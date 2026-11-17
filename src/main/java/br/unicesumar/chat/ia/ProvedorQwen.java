package br.unicesumar.chat.ia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Qwen por duas formas de chamada:
 *
 * <ul>
 *   <li>{@link ConfiguracaoQwen.Api#OPENAI}: {@code POST /chat/completions}
 *       (Alibaba Cloud Model Studio, outros hosts ou o {@code /v1} do Ollama),
 *       com {@code temperature}, {@code top_p}, {@code presence_penalty} e
 *       {@code max_tokens} (na Groq: {@code max_completion_tokens} e
 *       {@code reasoning_effort}, sem {@code presence_penalty});</li>
 *   <li>{@link ConfiguracaoQwen.Api#OLLAMA}: {@code POST /api/chat} nativo,
 *       que aceita também {@code num_ctx} (janela de contexto explícita, pois
 *       o padrão muda entre versões do Ollama), {@code keep_alive} (por
 *       pedido, sem alterar a configuração global) e {@code repeat_penalty}.</li>
 * </ul>
 *
 * Usa {@link HttpClient} do Java 17 com tempo limite e uma única tentativa.
 * A chave vai somente no cabeçalho {@code Authorization} e nunca aparece em
 * logs nem em mensagens de erro. Respostas cortadas pelo limite de tokens
 * são sinalizadas ({@link Resposta#truncada()}), nunca escondidas.
 */
public final class ProvedorQwen implements ProvedorIA {

    /** Resumos de memória: baixa criatividade, para não inventar fatos. */
    static final double TEMPERATURA_RESUMO = 0.2;

    /** {@code reasoning_effort} enviado à Groq: {@code none} desliga o raciocínio do Qwen3. */
    static final String ESFORCO_RACIOCINIO_GROQ = "none";

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
        return "Qwen " + configuracao.modelo()
                + (configuracao.servico() == ConfiguracaoQwen.Servico.QWEN ? "" : " (" + configuracao.servico().nome() + ")");
    }

    @Override
    public ParametrosIA parametros() {
        return configuracao.ajustes().parametros();
    }

    ConfiguracaoQwen configuracao() {
        return configuracao;
    }

    private boolean nativo() {
        return configuracao.ajustes().api() == ConfiguracaoQwen.Api.OLLAMA;
    }

    private double temperatura(PedidoIA pedido) {
        return pedido.finalidade() == Finalidade.RESUMO ? TEMPERATURA_RESUMO : configuracao.ajustes().temperatura();
    }

    private static JsonArray mensagens(PedidoIA pedido) {
        JsonArray mensagens = new JsonArray();
        mensagens.add(mensagem("system", pedido.instrucoes()));
        for (Turno turno : pedido.historico()) {
            mensagens.add(mensagem(turno.papel() == Papel.USUARIO ? "user" : "assistant", turno.texto()));
        }
        return mensagens;
    }

    /** Corpo JSON no formato OpenAI (visível para testes). */
    String corpo(PedidoIA pedido) {
        ConfiguracaoQwen.Ajustes ajustes = configuracao.ajustes();
        JsonObject corpo = new JsonObject();
        corpo.addProperty("model", configuracao.modelo());
        corpo.add("messages", mensagens(pedido));
        if (configuracao.groq()) {
            // Groq: max_tokens está obsoleto e presence_penalty não é aceito por nenhum modelo.
            corpo.addProperty("max_completion_tokens", pedido.maximoTokens());
            corpo.addProperty("temperature", temperatura(pedido));
            corpo.addProperty("top_p", ajustes.topP());
            corpo.addProperty("stream", false);
            // Sem raciocínio interno: a resposta inteira cabe em max_completion_tokens e chega mais rápido.
            corpo.addProperty("reasoning_effort", ESFORCO_RACIOCINIO_GROQ);
            return GSON.toJson(corpo);
        }
        corpo.addProperty("max_tokens", pedido.maximoTokens());
        corpo.addProperty("temperature", temperatura(pedido));
        corpo.addProperty("top_p", ajustes.topP());
        // repeat_penalty 1.1 (escala do Ollama) equivale a presence_penalty 0.3.
        corpo.addProperty("presence_penalty",
                Math.min(2.0, Math.max(0.0, (ajustes.penalidadeRepeticao() - 1.0) * 3)));
        corpo.addProperty("stream", false);
        return GSON.toJson(corpo);
    }

    /** Corpo JSON da API nativa do Ollama (visível para testes). */
    String corpoOllama(PedidoIA pedido) {
        ConfiguracaoQwen.Ajustes ajustes = configuracao.ajustes();
        JsonObject opcoes = new JsonObject();
        opcoes.addProperty("num_ctx", ajustes.parametros().janelaTokens());
        opcoes.addProperty("num_predict", pedido.maximoTokens());
        opcoes.addProperty("temperature", temperatura(pedido));
        opcoes.addProperty("top_p", ajustes.topP());
        opcoes.addProperty("repeat_penalty", ajustes.penalidadeRepeticao());

        JsonObject corpo = new JsonObject();
        corpo.addProperty("model", configuracao.modelo());
        corpo.add("messages", mensagens(pedido));
        corpo.addProperty("stream", false);
        corpo.addProperty("keep_alive", ajustes.keepAlive());
        corpo.add("options", opcoes);
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
        return gerarResposta(pedido).texto();
    }

    @Override
    public Resposta gerarResposta(PedidoIA pedido) throws FalhaIA {
        if (nativo()) {
            return interpretarOllama(enviar(configuracao.endpointOllama(), corpoOllama(pedido)));
        }
        return interpretarResposta(enviar(configuracao.endpoint(), corpo(pedido)));
    }

    /**
     * Carrega o modelo no Ollama sem gerar texto (pedido com lista de
     * mensagens vazia), usando o mesmo {@code keep_alive}. Só na API nativa
     * e com {@code QWEN_AQUECER} ativo; nunca baixa modelos.
     */
    @Override
    public void aquecer() {
        if (!nativo() || !configuracao.ajustes().aquecer()) {
            return;
        }
        JsonObject corpo = new JsonObject();
        corpo.addProperty("model", configuracao.modelo());
        corpo.add("messages", new JsonArray());
        corpo.addProperty("stream", false);
        corpo.addProperty("keep_alive", configuracao.ajustes().keepAlive());
        long inicio = System.nanoTime();
        try {
            enviar(configuracao.endpointOllama(), GSON.toJson(corpo));
            System.out.printf("Sakura IA: modelo %s carregado em %.1f s (keep_alive %s).%n",
                    configuracao.modelo(), (System.nanoTime() - inicio) / 1e9, configuracao.ajustes().keepAlive());
        } catch (FalhaIA e) {
            System.err.println("Sakura IA: aquecimento não concluído (" + e.getCodigo() + "): " + e.getMessage());
        }
    }

    private String enviar(URI endereco, String corpo) throws FalhaIA {

        HttpRequest.Builder requisicao = HttpRequest.newBuilder(endereco)
                .timeout(configuracao.tempoLimite())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));

        if (configuracao.chave() != null) {
            requisicao.header("Authorization", "Bearer " + configuracao.chave());
        }

        HttpResponse<String> resposta;

        try {
            resposta = cliente.send(requisicao.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new FalhaIA(FalhaIA.Codigo.TEMPO_ESGOTADO, configuracao.groq()
                    ? "A Groq demorou demais para responder. Tente novamente."
                    : "O Qwen demorou demais para responder. Tente novamente.");
        } catch (ConnectException e) {
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, configuracao.groq()
                    ? "Não foi possível conectar à Groq. Confira a internet deste computador."
                    : ConfiguracaoQwen.local(configuracao.baseUrl())
                    ? "O serviço local do Qwen (Ollama) não está em execução."
                    : "Não foi possível conectar ao serviço do Qwen.");
        } catch (IOException e) {
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, configuracao.groq()
                    ? "Falha de rede ao acessar a Groq. Confira a internet e tente novamente."
                    : "Falha de rede ao acessar o Qwen.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, "Geração interrompida.");
        }

        int status = resposta.statusCode();

        if (configuracao.groq()) {
            if (status < 200 || status >= 300) {
                throw falhaGroq(status);
            }
            return resposta.body();
        }

        if (status == 401 || status == 403) {
            throw new FalhaIA(FalhaIA.Codigo.AUTENTICACAO, "Chave do Qwen inválida ou sem permissão (QWEN_API_KEY).");
        }
        if (status == 429) {
            throw new FalhaIA(FalhaIA.Codigo.LIMITE, "Limite de uso do Qwen atingido. Aguarde e tente novamente.");
        }
        if (status == 404) {
            throw new FalhaIA(FalhaIA.Codigo.NAO_CONFIGURADO, nativo()
                    ? "Modelo não encontrado no Ollama (" + configuracao.modelo()
                            + "). Baixe-o com 'ollama pull' antes de usar."
                    : "Modelo ou endereço do Qwen não encontrado (QWEN_MODEL / QWEN_BASE_URL).");
        }
        if (status < 200 || status >= 300) {
            throw new FalhaIA(FalhaIA.Codigo.INDISPONIVEL, "O serviço do Qwen respondeu com erro (" + status + ").");
        }

        return resposta.body();
    }

    /**
     * Erros documentados da Groq, em mensagens públicas (sem a chave e sem
     * repetir o corpo da resposta).
     */
    private FalhaIA falhaGroq(int status) {
        String modelo = configuracao.modelo();
        return switch (status) {
            case 401 -> new FalhaIA(FalhaIA.Codigo.AUTENTICACAO,
                    "Chave da Groq inválida, expirada ou revogada (GROQ_API_KEY). Crie outra em "
                            + ConfiguracaoGroq.PAGINA_CHAVES + ".");
            case 402 -> new FalhaIA(FalhaIA.Codigo.CREDITOS,
                    "A Groq recusou o pedido por falta de saldo ou de plano para o modelo " + modelo
                            + ". Confira a conta em console.groq.com.");
            case 403 -> new FalhaIA(FalhaIA.Codigo.AUTENTICACAO,
                    "A Groq recusou o pedido (chave sem permissão para o modelo " + modelo
                            + " ou acesso bloqueado nesta rede).");
            case 404 -> new FalhaIA(FalhaIA.Codigo.NAO_CONFIGURADO,
                    "O modelo " + modelo + " não foi encontrado na Groq. Confira GROQ_MODEL.");
            case 400, 422 -> new FalhaIA(FalhaIA.Codigo.NAO_CONFIGURADO,
                    "A Groq recusou o pedido para o modelo " + modelo
                            + " (modelo indisponível ou opções incompatíveis). Confira GROQ_MODEL.");
            case 408 -> new FalhaIA(FalhaIA.Codigo.TEMPO_ESGOTADO,
                    "A Groq demorou demais para responder. Tente novamente.");
            case 413 -> new FalhaIA(FalhaIA.Codigo.LIMITE,
                    "A conversa ficou grande demais para o limite da Groq. Tente uma mensagem mais curta.");
            case 429 -> new FalhaIA(FalhaIA.Codigo.LIMITE,
                    "Limite de uso gratuito da Groq atingido (pedidos ou tokens por minuto)."
                            + " Aguarde um pouco e tente novamente.");
            case 498, 500, 502, 503, 504 -> new FalhaIA(FalhaIA.Codigo.INDISPONIVEL,
                    "A Groq está temporariamente indisponível para o modelo " + modelo + ". Tente em instantes.");
            default -> new FalhaIA(FalhaIA.Codigo.INDISPONIVEL,
                    "A Groq respondeu com erro (" + status + ").");
        };
    }

    /** Extrai {@code choices[0].message.content}, sem raciocínio interno ({@code <think>}). */
    static String interpretar(String corpo) throws FalhaIA {
        return interpretarResposta(corpo).texto();
    }

    /** Formato OpenAI; {@code finish_reason = "length"} marca resposta cortada. */
    static Resposta interpretarResposta(String corpo) throws FalhaIA {
        try {
            JsonObject escolha = JsonParser.parseString(corpo).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonElement conteudo = escolha.getAsJsonObject("message").get("content");
            JsonElement motivo = escolha.get("finish_reason");
            boolean truncada = motivo != null && !motivo.isJsonNull() && "length".equals(motivo.getAsString());
            return new Resposta(limpar(conteudo.getAsString()), truncada);
        } catch (FalhaIA e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FalhaIA(FalhaIA.Codigo.RESPOSTA_INVALIDA, "Resposta do Qwen em formato inesperado.");
        }
    }

    /** Formato nativo do Ollama; {@code done_reason = "length"} marca resposta cortada. */
    static Resposta interpretarOllama(String corpo) throws FalhaIA {
        try {
            JsonObject objeto = JsonParser.parseString(corpo).getAsJsonObject();
            JsonElement conteudo = objeto.getAsJsonObject("message").get("content");
            JsonElement motivo = objeto.get("done_reason");
            boolean truncada = motivo != null && !motivo.isJsonNull() && "length".equals(motivo.getAsString());
            return new Resposta(limpar(conteudo.getAsString()), truncada);
        } catch (FalhaIA e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FalhaIA(FalhaIA.Codigo.RESPOSTA_INVALIDA, "Resposta do Ollama em formato inesperado.");
        }
    }

    private static String limpar(String bruto) throws FalhaIA {
        String texto = bruto.replaceAll("(?s)<think>.*?</think>", "").strip();
        if (texto.isEmpty()) {
            throw new FalhaIA(FalhaIA.Codigo.RESPOSTA_INVALIDA, "O Qwen retornou uma resposta vazia.");
        }
        return texto;
    }
}
