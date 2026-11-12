package br.unicesumar.chat.ia;

import br.unicesumar.chat.ia.ProvedorIA.FalhaIA;
import br.unicesumar.chat.ia.ProvedorIA.Finalidade;
import br.unicesumar.chat.ia.ProvedorIA.Papel;
import br.unicesumar.chat.ia.ProvedorIA.PedidoIA;
import br.unicesumar.chat.ia.ProvedorIA.Resposta;
import br.unicesumar.chat.ia.ProvedorIA.Turno;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API nativa do Ollama ({@code /api/chat}), parâmetros de amostragem,
 * respostas truncadas nos dois formatos, configuração por ambiente e
 * aquecimento opcional. Tudo contra um servidor HTTP local de teste.
 */
class ProvedorQwenOllamaTest {

    private HttpServer servidor;
    private final List<String> corpos = new CopyOnWriteArrayList<>();
    private final List<String> caminhos = new CopyOnWriteArrayList<>();

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** Responde em qualquer caminho com o status e o corpo dados. */
    private URI servidor(int status, String resposta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", troca -> {
            caminhos.add(troca.getRequestURI().getPath());
            corpos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1");
    }

    private static ConfiguracaoQwen configuracao(URI base, ConfiguracaoQwen.Api api, boolean aquecer) {
        ConfiguracaoQwen.Ajustes padrao = ConfiguracaoQwen.Ajustes.padrao(api, true);
        return new ConfiguracaoQwen(base, "qwen2.5:3b", null, Duration.ofSeconds(5),
                new ConfiguracaoQwen.Ajustes(api, padrao.parametros(), padrao.temperatura(), padrao.topP(),
                        padrao.penalidadeRepeticao(), "45m", aquecer));
    }

    private static PedidoIA pedido(Finalidade finalidade) {
        return new PedidoIA("Você é Naruto.", List.of(new Turno(Papel.USUARIO, "Oi!")), 320, finalidade);
    }

    private static String ollama(String conteudo, String motivo) {
        JsonObject mensagem = new JsonObject();
        mensagem.addProperty("role", "assistant");
        mensagem.addProperty("content", conteudo);
        JsonObject raiz = new JsonObject();
        raiz.add("message", mensagem);
        raiz.addProperty("done", true);
        if (motivo != null) {
            raiz.addProperty("done_reason", motivo);
        }
        return raiz.toString();
    }

    @Test
    void apiNativaDeveEnviarJanelaKeepAliveEPenalidade() throws Exception {
        ProvedorQwen qwen = new ProvedorQwen(configuracao(servidor(200, ollama("Dattebayo!", "stop")),
                ConfiguracaoQwen.Api.OLLAMA, false));

        Resposta resposta = qwen.gerarResposta(pedido(Finalidade.RESPOSTA));

        assertEquals("Dattebayo!", resposta.texto());
        assertFalse(resposta.truncada());
        assertEquals(List.of("/api/chat"), caminhos, "Base /v1 vira /api/chat");

        JsonObject corpo = JsonParser.parseString(corpos.get(0)).getAsJsonObject();
        assertEquals("qwen2.5:3b", corpo.get("model").getAsString());
        assertFalse(corpo.get("stream").getAsBoolean());
        assertEquals("45m", corpo.get("keep_alive").getAsString(), "keep_alive por pedido");
        JsonObject opcoes = corpo.getAsJsonObject("options");
        assertEquals(ParametrosIA.LOCAL.janelaTokens(), opcoes.get("num_ctx").getAsInt(), "Janela explícita");
        assertEquals(320, opcoes.get("num_predict").getAsInt());
        assertEquals(0.65, opcoes.get("temperature").getAsDouble(), 1e-9);
        assertEquals(0.9, opcoes.get("top_p").getAsDouble(), 1e-9);
        assertEquals(1.1, opcoes.get("repeat_penalty").getAsDouble(), 1e-9);
        assertEquals(List.of("system", "user"), corpo.getAsJsonArray("messages").asList().stream()
                .map(m -> m.getAsJsonObject().get("role").getAsString()).toList());
    }

    @Test
    void respostaCortadaDeveSerSinalizadaNosDoisFormatos() throws Exception {
        assertTrue(ProvedorQwen.interpretarOllama(ollama("Era uma vez", "length")).truncada());
        assertFalse(ProvedorQwen.interpretarOllama(ollama("Fim.", null)).truncada(), "Versões sem done_reason");

        String openai = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Era uma\"},"
                + "\"finish_reason\":\"length\"}]}";
        Resposta cortada = ProvedorQwen.interpretarResposta(openai);
        assertTrue(cortada.truncada());
        assertEquals("Era uma", cortada.texto(), "O texto não é alterado pelo provedor");

        assertThrows(FalhaIA.class, () -> ProvedorQwen.interpretarOllama("{\"done\":true}"));
    }

    @Test
    void modeloAusenteNoOllamaDeveOrientarSemBaixarNada() throws Exception {
        ProvedorQwen qwen = new ProvedorQwen(configuracao(servidor(404, "{\"error\":\"model not found\"}"),
                ConfiguracaoQwen.Api.OLLAMA, false));
        FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido(Finalidade.RESPOSTA)));
        assertEquals(FalhaIA.Codigo.NAO_CONFIGURADO, falha.getCodigo());
        assertTrue(falha.getMessage().contains("ollama pull"));
        assertEquals(1, corpos.size(), "Nenhuma outra chamada (nada de download)");
    }

    @Test
    void apiCompativelComOpenAIDeveEnviarAmostragemEResumoComTemperaturaBaixa() throws Exception {
        String resposta = "{\"choices\":[{\"message\":{\"content\":\"- nota\"},\"finish_reason\":\"stop\"}]}";
        ProvedorQwen qwen = new ProvedorQwen(configuracao(servidor(200, resposta), ConfiguracaoQwen.Api.OPENAI, false));

        qwen.gerarResposta(pedido(Finalidade.RESPOSTA));
        qwen.gerarResposta(pedido(Finalidade.RESUMO));

        assertEquals(List.of("/v1/chat/completions", "/v1/chat/completions"), caminhos);
        JsonObject normal = JsonParser.parseString(corpos.get(0)).getAsJsonObject();
        assertEquals(0.65, normal.get("temperature").getAsDouble(), 1e-9);
        assertEquals(0.9, normal.get("top_p").getAsDouble(), 1e-9);
        assertEquals(0.3, normal.get("presence_penalty").getAsDouble(), 1e-9);
        assertEquals(320, normal.get("max_tokens").getAsInt());
        assertNull(normal.get("options"), "Parâmetros nativos não vão para o /v1");
        assertNull(normal.get("keep_alive"));

        JsonObject resumo = JsonParser.parseString(corpos.get(1)).getAsJsonObject();
        assertEquals(ProvedorQwen.TEMPERATURA_RESUMO, resumo.get("temperature").getAsDouble(), 1e-9);
    }

    @Test
    void ambienteDeveEscolherApiEParametrosComPadroesSeguros() {
        ConfiguracaoQwen ollama = ConfiguracaoQwen.doAmbiente(Map.of(
                "QWEN_BASE_URL", "http://localhost:11434/v1", "QWEN_MODEL", "qwen2.5:3b")).orElseThrow();
        assertEquals(ConfiguracaoQwen.Api.OLLAMA, ollama.ajustes().api(), "Ollama local detectado (porta 11434)");
        assertEquals(ParametrosIA.LOCAL, ollama.ajustes().parametros());
        assertEquals(1, ollama.ajustes().parametros().geracoesSimultaneas());
        assertFalse(ollama.ajustes().aquecer(), "Aquecimento é opcional (desligado por padrão)");
        assertEquals(URI.create("http://localhost:11434/api/chat"), ollama.endpointOllama());

        ConfiguracaoQwen forcado = ConfiguracaoQwen.doAmbiente(Map.of(
                "QWEN_BASE_URL", "http://localhost:11434/v1", "QWEN_MODEL", "qwen2.5:3b", "QWEN_API", "openai",
                "QWEN_CONTEXTO_TOKENS", "8192", "QWEN_RESPOSTA_TOKENS", "999999", "QWEN_TEMPERATURA", "0,6",
                "QWEN_KEEP_ALIVE", "10m; rm -rf", "QWEN_AQUECER", "true", "QWEN_FILA_MAXIMA", "0")).orElseThrow();
        assertEquals(ConfiguracaoQwen.Api.OPENAI, forcado.ajustes().api());
        assertEquals(8192, forcado.ajustes().parametros().janelaTokens());
        assertEquals(2048, forcado.ajustes().parametros().tokensResposta(), "Limitado ao máximo");
        assertEquals(1, forcado.ajustes().parametros().filaMaxima(), "Limitado ao mínimo");
        assertEquals(0.6, forcado.ajustes().temperatura(), 1e-9, "Aceita vírgula decimal");
        assertEquals("30m", forcado.ajustes().keepAlive(), "Valor inválido volta ao padrão");
        assertTrue(forcado.ajustes().aquecer());

        ConfiguracaoQwen remoto = ConfiguracaoQwen.doAmbiente(Map.of("QWEN_API_KEY", "sk-teste")).orElseThrow();
        assertEquals(ConfiguracaoQwen.Api.OPENAI, remoto.ajustes().api(), "Remoto continua no formato OpenAI");
        assertEquals(ParametrosIA.REMOTO, remoto.ajustes().parametros());
        assertEquals(2, remoto.ajustes().parametros().geracoesSimultaneas(), "Remoto configurado à parte");
    }

    @Test
    void aquecimentoSoOcorreQuandoAtivadoENaApiNativa() throws Exception {
        URI base = servidor(200, ollama("", "load"));

        new ProvedorQwen(configuracao(base, ConfiguracaoQwen.Api.OLLAMA, false)).aquecer();
        new ProvedorQwen(configuracao(base, ConfiguracaoQwen.Api.OPENAI, true)).aquecer();
        assertTrue(corpos.isEmpty(), "Desligado ou fora da API nativa: nenhuma chamada");

        new ProvedorQwen(configuracao(base, ConfiguracaoQwen.Api.OLLAMA, true)).aquecer();
        assertEquals(1, corpos.size());
        JsonObject corpo = JsonParser.parseString(corpos.get(0)).getAsJsonObject();
        assertEquals(0, corpo.getAsJsonArray("messages").size(), "Só carrega o modelo, sem gerar texto");
        assertEquals("45m", corpo.get("keep_alive").getAsString());
    }

    @Test
    void ollamaParadoNoAquecimentoNaoLancaExcecao() {
        ProvedorQwen qwen = new ProvedorQwen(configuracao(URI.create("http://localhost:1/v1"),
                ConfiguracaoQwen.Api.OLLAMA, true));
        assertDoesNotThrow(qwen::aquecer, "Falha no aquecimento só é registrada");
    }
}
