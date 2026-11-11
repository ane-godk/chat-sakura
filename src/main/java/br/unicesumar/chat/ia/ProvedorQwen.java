package br.unicesumar.chat.ia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Qwen por API compatível com OpenAI ({@code POST /chat/completions}):
 * Alibaba Cloud Model Studio (DashScope), outros hosts do Qwen ou o
 * Ollama local ({@code http://localhost:11434/v1}).
 *
 * Usa {@link HttpClient} do Java 17 com tempo limite, uma única tentativa
 * (sem repetições automáticas) e limite de tokens da resposta. A chave vai
 * somente no cabeçalho {@code Authorization} e nunca aparece em logs nem
 * em mensagens de erro.
 */
public final class ProvedorQwen implements ProvedorIA {

    static final double TEMPERATURA = 0.8;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final ConfiguracaoQwen configuracao;
    private final HttpClient cliente;

    public ProvedorQwen(ConfiguracaoQwen configuracao) {
        this.configuracao = Objects.requireNonNull(configuracao);
        this.cliente = HttpClient.newBuilder()
                .connectTimeout(configuracao.tempoLimite())
                .build();
    }

    @Override
    public String descricao() {
        return "Qwen " + configuracao.modelo();
    }

    /** Corpo JSON da requisição (visível para testes). */
    String corpo(PedidoIA pedido) {

        JsonArray mensagens = new JsonArray();
        mensagens.add(mensagem("system", pedido.instrucoes()));
        for (Turno turno : pedido.historico()) {
            mensagens.add(mensagem(turno.papel() == Papel.USUARIO ? "user" : "assistant", turno.texto()));
        }

        JsonObject corpo = new JsonObject();
        corpo.addProperty("model", configuracao.modelo());
        corpo.add("messages", mensagens);
        corpo.addProperty("max_tokens", pedido.maximoTokens());
        corpo.addProperty("temperature", TEMPERATURA);
        corpo.addProperty("stream", false);
        return GSON.toJson(corpo);
    }

    private static JsonObject mensagem(String papel, String conteudo) {
        JsonObject mensagem = new JsonObject();
        mensagem.addProperty("role", papel);
        mensagem.addProperty("content", conteudo);
        return mensagem;
    }

    @Override
    public String gerar(PedidoIA pedido) throws FalhaIA {

        HttpRequest.Builder requisicao = HttpRequest.newBuilder(configuracao.endpoint())
                .timeout(configuracao.tempoLimite())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo(pedido), StandardCharsets.UTF_8));

        if (configuracao.chave() != null) {
            requisicao.header("Authorization", "Bearer " + configuracao.chave());
        }

        HttpResponse<String> resposta;

        try {
            resposta = cliente.send(requisicao.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new FalhaIA(FalhaIA.Codigo.TEMPO_ESGOTADO,
                    "O Qwen demorou demais para responder. Tente novamente.");
        } catch (ConnectException e) {
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, ConfiguracaoQwen.local(configuracao.baseUrl())
                    ? "O serviço local do Qwen (Ollama) não está em execução."
                    : "Não foi possível conectar ao serviço do Qwen.");
        } catch (IOException e) {
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, "Falha de rede ao acessar o Qwen.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, "Geração interrompida.");
        }

        int status = resposta.statusCode();

        if (status == 401 || status == 403) {
            throw new FalhaIA(FalhaIA.Codigo.AUTENTICACAO, "Chave do Qwen inválida ou sem permissão (QWEN_API_KEY).");
        }
        if (status == 429) {
            throw new FalhaIA(FalhaIA.Codigo.LIMITE, "Limite de uso do Qwen atingido. Aguarde e tente novamente.");
        }
        if (status == 404) {
            throw new FalhaIA(FalhaIA.Codigo.NAO_CONFIGURADO,
                    "Modelo ou endereço do Qwen não encontrado (QWEN_MODEL / QWEN_BASE_URL).");
        }
        if (status < 200 || status >= 300) {
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, "O serviço do Qwen respondeu com erro (" + status + ").");
        }

        return interpretar(resposta.body());
    }

    /** Extrai {@code choices[0].message.content}, sem raciocínio interno ({@code <think>}). */
    static String interpretar(String corpo) throws FalhaIA {

        try {
            JsonObject objeto = JsonParser.parseString(corpo).getAsJsonObject();
            JsonElement conteudo = objeto.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content");

            String texto = conteudo.getAsString()
                    .replaceAll("(?s)<think>.*?</think>", "")
                    .strip();

            if (texto.isEmpty()) {
                throw new FalhaIA(FalhaIA.Codigo.RESPOSTA_INVALIDA, "O Qwen retornou uma resposta vazia.");
            }
            return texto;

        } catch (FalhaIA e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FalhaIA(FalhaIA.Codigo.RESPOSTA_INVALIDA, "Resposta do Qwen em formato inesperado.");
        }
    }
}
