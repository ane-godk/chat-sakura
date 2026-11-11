package br.unicesumar.chat.ia;

import br.unicesumar.chat.ia.ProvedorIA.FalhaIA;
import br.unicesumar.chat.ia.ProvedorIA.Papel;
import br.unicesumar.chat.ia.ProvedorIA.PedidoIA;
import br.unicesumar.chat.ia.ProvedorIA.Turno;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provedor Qwen (API compatível com OpenAI) contra um servidor HTTP local
 * que imita o serviço: requisição, resposta, erros, tempo limite e sigilo
 * da chave. Nenhuma chamada sai para a internet.
 */
class ProvedorQwenTest {

    private static final String CHAVE = "sk-teste-NAO-REAL-123";

    private HttpServer servidor;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicReference<String> autorizacao = new AtomicReference<>();
    private final CountDownLatch liberar = new CountDownLatch(1);

    @AfterEach
    void parar() {
        liberar.countDown();
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** Servidor que responde com o status e o corpo indicados (ou espera, se {@code espera}). */
    private ProvedorQwen provedor(int status, String corpo, boolean espera, Duration tempo, String chave)
            throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/v1/chat/completions", troca -> {
            corpoRecebido.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            if (espera) {
                try {
                    liberar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1");
        return new ProvedorQwen(new ConfiguracaoQwen(base, "qwen2.5:3b", chave, tempo));
    }

    private static String respostaOpenAI(String conteudo) {
        JsonObject mensagem = new JsonObject();
        mensagem.addProperty("role", "assistant");
        mensagem.addProperty("content", conteudo);
        JsonObject escolha = new JsonObject();
        escolha.add("message", mensagem);
        JsonArray escolhas = new JsonArray();
        escolhas.add(escolha);
        JsonObject raiz = new JsonObject();
        raiz.add("choices", escolhas);
        return raiz.toString();
    }

    private static PedidoIA pedido() {
        return new PedidoIA("Você é Naruto.", List.of(
                new Turno(Papel.USUARIO, "Oi, Naruto!"),
                new Turno(Papel.ASSISTENTE, "Fala, parceiro! Dattebayo!"),
                new Turno(Papel.USUARIO, "Vamos treinar?")), 400);
    }

    @Test
    void deveMontarARequisicaoNoFormatoDoQwenEInterpretarAResposta() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("<think>planejando</think>\n Bora treinar! 🍥"),
                false, Duration.ofSeconds(5), CHAVE);

        assertEquals("Bora treinar! 🍥", qwen.gerar(pedido()), "Sem o raciocínio interno <think>");
        assertEquals("Bearer " + CHAVE, autorizacao.get());

        JsonObject corpo = JsonParser.parseString(corpoRecebido.get()).getAsJsonObject();
        assertEquals("qwen2.5:3b", corpo.get("model").getAsString());
        assertEquals(400, corpo.get("max_tokens").getAsInt());
        assertFalse(corpo.get("stream").getAsBoolean());

        JsonArray mensagens = corpo.getAsJsonArray("messages");
        assertEquals(List.of("system", "user", "assistant", "user"), mensagens.asList().stream()
                .map(m -> m.getAsJsonObject().get("role").getAsString()).toList());
        assertEquals("Você é Naruto.", mensagens.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("Vamos treinar?", mensagens.get(3).getAsJsonObject().get("content").getAsString());
        assertFalse(corpoRecebido.get().contains(CHAVE), "A chave não vai no corpo");
        assertEquals("Qwen qwen2.5:3b", qwen.descricao());
    }

    @Test
    void ollamaLocalDispensaAChave() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("Olá!"), false, Duration.ofSeconds(5), null);
        assertEquals("Olá!", qwen.gerar(pedido()));
        assertNull(autorizacao.get());
    }

    @Test
    void errosDaApiDevemVirarFalhasClarasSemExporAChave() throws Exception {
        Object[][] casos = {
                {401, FalhaIA.Codigo.AUTENTICACAO}, {403, FalhaIA.Codigo.AUTENTICACAO},
                {429, FalhaIA.Codigo.LIMITE}, {404, FalhaIA.Codigo.NAO_CONFIGURADO},
                {500, FalhaIA.Codigo.INDISPONIVEL}, {503, FalhaIA.Codigo.INDISPONIVEL}};

        for (Object[] caso : casos) {
            ProvedorQwen qwen = provedor((int) caso[0], "{\"error\":{\"message\":\"chave " + CHAVE + "\"}}",
                    false, Duration.ofSeconds(5), CHAVE);
            FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()));
            assertEquals(caso[1], falha.getCodigo(), String.valueOf(caso[0]));
            assertFalse(falha.getMessage().contains(CHAVE), "A mensagem nunca repete o corpo nem a chave");
            servidor.stop(0);
        }
    }

    @Test
    void respostasMalformadasOuVaziasDevemSerRecusadas() throws Exception {
        for (String corpo : new String[] {"não é json", "{}", "{\"choices\":[]}", respostaOpenAI("<think>só isso</think>")}) {
            FalhaIA falha = assertThrows(FalhaIA.class, () -> ProvedorQwen.interpretar(corpo));
            assertEquals(FalhaIA.Codigo.RESPOSTA_INVALIDA, falha.getCodigo(), corpo);
        }
    }

    @Test
    void tempoLimiteDeveSerRespeitado() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("tarde demais"), true, Duration.ofMillis(400), CHAVE);
        long inicio = System.nanoTime();
        FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.TEMPO_ESGOTADO, falha.getCodigo());
        assertTrue(System.nanoTime() - inicio < TimeUnit.SECONDS.toNanos(5));
    }

    @Test
    void servicoLocalParadoDeveSerInformado() throws Exception {
        int porta;
        try (ServerSocket livre = new ServerSocket(0)) {
            porta = livre.getLocalPort();
        }
        ProvedorQwen qwen = new ProvedorQwen(new ConfiguracaoQwen(
                URI.create("http://localhost:" + porta + "/v1"), "qwen2.5:3b", null, Duration.ofSeconds(2)));
        FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.INDISPONIVEL, falha.getCodigo());
        assertTrue(falha.getMessage().contains("Ollama"));
    }

    @Test
    void configuracaoDeveVirDoAmbienteSomenteComModeloQwen() {
        assertTrue(ConfiguracaoQwen.doAmbiente(Map.of()).isEmpty(), "Nada configurado");

        ConfiguracaoQwen padrao = ConfiguracaoQwen.doAmbiente(Map.of("QWEN_API_KEY", CHAVE)).orElseThrow();
        assertEquals(ConfiguracaoQwen.MODELO_PADRAO, padrao.modelo());
        assertEquals(URI.create(ConfiguracaoQwen.URL_PADRAO + "/chat/completions"), padrao.endpoint());
        assertFalse(padrao.toString().contains(CHAVE), "toString não mostra a chave");

        ConfiguracaoQwen ollama = ConfiguracaoQwen.doAmbiente(Map.of(
                "QWEN_BASE_URL", "http://localhost:11434/v1/", "QWEN_MODEL", "qwen2.5:3b",
                "QWEN_TIMEOUT_SEGUNDOS", "1")).orElseThrow();
        assertNull(ollama.chave());
        assertEquals(URI.create("http://localhost:11434/v1/chat/completions"), ollama.endpoint());
        assertEquals(Duration.ofSeconds(5), ollama.tempoLimite(), "Tempo mínimo de 5 s");

        assertThrows(IllegalArgumentException.class, () -> ConfiguracaoQwen.doAmbiente(
                Map.of("QWEN_API_KEY", CHAVE, "QWEN_MODEL", "gemini-1.5-flash")), "Somente Qwen");
        assertThrows(IllegalArgumentException.class, () -> ConfiguracaoQwen.doAmbiente(
                Map.of("QWEN_MODEL", "qwen-plus")), "API remota exige chave");
        assertThrows(IllegalArgumentException.class, () -> ConfiguracaoQwen.doAmbiente(
                Map.of("QWEN_API_KEY", CHAVE, "QWEN_BASE_URL", "ftp://x")));
    }

    @Test
    void escolhaDoProvedorDeveSerExplicitaENuncaSimularEmSilencio() {
        assertTrue(ProvedorIndisponivel.doAmbiente(Map.of("CHAT_SAKURA_IA", "simulado")).simulado());
        assertEquals("SIMULADO", ProvedorIndisponivel.doAmbiente(Map.of("CHAT_SAKURA_IA", "simulado")).descricao());
        assertInstanceOf(ProvedorQwen.class, ProvedorIndisponivel.doAmbiente(Map.of("QWEN_API_KEY", CHAVE)));

        ProvedorIA nenhum = ProvedorIndisponivel.doAmbiente(Map.of());
        assertFalse(nenhum.simulado());
        FalhaIA falha = assertThrows(FalhaIA.class, () -> nenhum.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.NAO_CONFIGURADO, falha.getCodigo());
        assertTrue(falha.getMessage().contains("QWEN_API_KEY"));

        ProvedorIA invalido = ProvedorIndisponivel.doAmbiente(Map.of("QWEN_API_KEY", CHAVE, "QWEN_MODEL", "gpt-4o"));
        FalhaIA falhaInvalida = assertThrows(FalhaIA.class, () -> invalido.gerar(pedido()));
        assertFalse(falhaInvalida.getMessage().contains(CHAVE));
    }

    @Test
    void provedorNaoDeveRegistrarAChaveEmLogs() throws Exception {
        PrintStream erro = System.err;
        PrintStream saida = System.out;
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        PrintStream captura = new PrintStream(log, true, StandardCharsets.UTF_8);
        try {
            System.setErr(captura);
            System.setOut(captura);
            ProvedorQwen qwen = provedor(401, "{}", false, Duration.ofSeconds(5), CHAVE);
            assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()));
            provedor(200, respostaOpenAI("ok"), false, Duration.ofSeconds(5), CHAVE).gerar(pedido());
        } finally {
            System.setErr(erro);
            System.setOut(saida);
        }
        assertFalse(log.toString(StandardCharsets.UTF_8).contains(CHAVE));
    }
}
