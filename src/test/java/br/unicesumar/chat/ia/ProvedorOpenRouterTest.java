package br.unicesumar.chat.ia;

import br.unicesumar.chat.ia.ProvedorIA.FalhaIA;
import br.unicesumar.chat.ia.ProvedorIA.Papel;
import br.unicesumar.chat.ia.ProvedorIA.PedidoIA;
import br.unicesumar.chat.ia.ProvedorIA.Turno;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Qwen pelo OpenRouter contra um servidor HTTP local que imita a API
 * ({@code /api/v1/chat/completions} e {@code /api/v1/models}). Nenhum
 * pedido sai para a internet e nenhuma chave real é usada.
 */
class ProvedorOpenRouterTest {

    private static final String CHAVE = "sk-or-v1-teste-NAO-REAL-0000";
    private static final String MODELO = "qwen/qwen-teste:free";

    private HttpServer servidor;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicReference<String> autorizacao = new AtomicReference<>();
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final CountDownLatch liberar = new CountDownLatch(1);

    @AfterEach
    void parar() {
        liberar.countDown();
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** Sobe o OpenRouter simulado e devolve a base ({@code http://127.0.0.1:porta/api/v1}). */
    private URI simular(int status, String corpo, boolean espera) throws IOException {
        if (servidor != null) {
            servidor.stop(0);
        }
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/api/v1/", troca -> {
            caminho.set(troca.getRequestURI().getPath());
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
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/api/v1");
    }

    private static Map<String, String> ambiente(URI base, String... extras) {
        Map<String, String> ambiente = new HashMap<>();
        ambiente.put("OPENROUTER_API_KEY", CHAVE);
        ambiente.put("OPENROUTER_BASE_URL", base.toString());
        for (int i = 0; i + 1 < extras.length; i += 2) {
            ambiente.put(extras[i], extras[i + 1]);
        }
        return ambiente;
    }

    private ProvedorQwen provedor(int status, String corpo) throws IOException {
        return provedor(simular(status, corpo, false));
    }

    private static ProvedorQwen provedor(URI base, String... extras) {
        String[] comModelo = new String[extras.length + 2];
        comModelo[0] = "OPENROUTER_MODEL";
        comModelo[1] = MODELO;
        System.arraycopy(extras, 0, comModelo, 2, extras.length);
        ProvedorIA provedor = ProvedorIndisponivel.doAmbiente(ambiente(base, comModelo), null, null);
        return assertInstanceOf(ProvedorQwen.class, provedor);
    }

    private static String respostaOpenAI(String conteudo) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + JsonParser.parseString("\"" + conteudo + "\"") + "},\"finish_reason\":\"stop\"}]}";
    }

    private static PedidoIA pedido() {
        return new PedidoIA("Você é a Sakura.", List.of(new Turno(Papel.USUARIO, "Oi!")), 400);
    }

    private static String catalogo(String... modelos) {
        StringBuilder json = new StringBuilder("{\"data\":[");
        for (int i = 0; i < modelos.length; i++) {
            String id = modelos[i];
            String preco = id.endsWith(":free") ? "0" : "0.0000003";
            json.append(i == 0 ? "" : ",").append("{\"id\":\"").append(id)
                    .append("\",\"pricing\":{\"prompt\":\"").append(preco)
                    .append("\",\"completion\":\"").append(preco).append("\"}}");
        }
        return json.append("]}").toString();
    }

    @Test
    void deveChamarOOpenRouterComBearerEInterpretarAResposta() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("Olá! 🌸"));

        assertEquals("Olá! 🌸", qwen.gerar(pedido()));
        assertEquals("/api/v1/chat/completions", caminho.get());
        assertEquals("Bearer " + CHAVE, autorizacao.get());

        JsonObject corpo = JsonParser.parseString(corpoRecebido.get()).getAsJsonObject();
        assertEquals(MODELO, corpo.get("model").getAsString());
        assertEquals(400, corpo.get("max_tokens").getAsInt());
        assertTrue(corpo.getAsJsonObject("reasoning").get("exclude").getAsBoolean(),
                "Raciocínio interno fora do texto");
        assertFalse(corpoRecebido.get().contains(CHAVE), "A chave vai só no cabeçalho");

        assertEquals("Qwen " + MODELO + " (OpenRouter)", qwen.descricao());
        assertEquals(ParametrosIA.REMOTO, qwen.parametros(), "Perfil remoto, mesmo em servidor de teste local");
        assertFalse(qwen.configuracao().toString().contains(CHAVE));
    }

    @Test
    void errosHttpDoOpenRouterViramMensagensClarasSemExporAChave() throws Exception {
        Object[][] casos = {
                {401, FalhaIA.Codigo.AUTENTICACAO, "OPENROUTER_API_KEY"},
                {402, FalhaIA.Codigo.CREDITOS, "créditos"},
                {403, FalhaIA.Codigo.AUTENTICACAO, "moderação"},
                {404, FalhaIA.Codigo.NAO_CONFIGURADO, "OPENROUTER_MODEL"},
                {400, FalhaIA.Codigo.NAO_CONFIGURADO, "OPENROUTER_MODEL"},
                {408, FalhaIA.Codigo.TEMPO_ESGOTADO, "demorou"},
                {429, FalhaIA.Codigo.LIMITE, "Limite"},
                {503, FalhaIA.Codigo.INDISPONIVEL, "temporariamente indisponível"},
                {500, FalhaIA.Codigo.INDISPONIVEL, "(500)"},
        };
        for (Object[] caso : casos) {
            ProvedorQwen qwen = provedor((Integer) caso[0],
                    "{\"error\":{\"code\":" + caso[0] + ",\"message\":\"detalhe com " + CHAVE + "\"}}");
            FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()), "HTTP " + caso[0]);
            assertEquals(caso[1], falha.getCodigo(), "HTTP " + caso[0]);
            assertTrue(falha.getMessage().contains((String) caso[2]), falha.getMessage());
            assertFalse(falha.getMessage().contains(CHAVE), "Nunca repete o corpo nem a chave");
        }
    }

    @Test
    void erroDentroDeUmaResposta200TambemEInformado() throws Exception {
        ProvedorQwen qwen = provedor(200, "{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}");

        FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.LIMITE, falha.getCodigo());
    }

    @Test
    void semInternetOuSemRespostaAFalhaEClaraENaoTrava() throws Exception {
        int portaFechada;
        try (ServerSocket livre = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaFechada = livre.getLocalPort();
        }
        ProvedorQwen semRede = provedor(URI.create("http://127.0.0.1:" + portaFechada + "/api/v1"));
        FalhaIA rede = assertThrows(FalhaIA.class, () -> semRede.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.INDISPONIVEL, rede.getCodigo());
        assertTrue(rede.getMessage().contains("OpenRouter"), rede.getMessage());

        ProvedorQwen lento = provedor(simular(200, respostaOpenAI("tarde"), true),
                "OPENROUTER_TIMEOUT_SEGUNDOS", "1");
        long inicio = System.nanoTime();
        FalhaIA tempo = assertThrows(FalhaIA.class, () -> lento.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.TEMPO_ESGOTADO, tempo.getCodigo());
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - inicio) < 8, "Respeita o tempo limite");
    }

    @Test
    void comAChaveDoOpenRouterOOllamaNaoEConsultado() {
        AtomicInteger consultasOllama = new AtomicInteger();
        DeteccaoOllama.Consulta ollama = () -> {
            consultasOllama.incrementAndGet();
            return "{\"models\":[{\"name\":\"qwen2.5:3b\"}]}";
        };

        ProvedorIA comChave = ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", CHAVE,
                "OPENROUTER_MODEL", MODELO, "QWEN_API_KEY", "outra"), ollama, null);
        ProvedorQwen qwen = assertInstanceOf(ProvedorQwen.class, comChave);
        assertTrue(qwen.configuracao().openRouter());
        assertEquals(URI.create(ConfiguracaoOpenRouter.URL_PADRAO), qwen.configuracao().baseUrl());
        assertEquals(URI.create("https://openrouter.ai/api/v1/chat/completions"), qwen.configuracao().endpoint());
        assertEquals(0, consultasOllama.get(), "OpenRouter tem prioridade");

        ProvedorIA semChave = ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", "  "), ollama, null);
        ProvedorQwen local = assertInstanceOf(ProvedorQwen.class, semChave);
        assertFalse(local.configuracao().openRouter(), "Chave vazia: segue a detecção do Ollama");
        assertEquals(1, consultasOllama.get());
    }

    @Test
    void semOpenROUTER_MODELProcuraUmQwenGratuitoNaListaReal() {
        AtomicReference<URI> consultado = new AtomicReference<>();
        ConfiguracaoOpenRouter.Catalogo lista = base -> {
            consultado.set(base);
            return catalogo("qwen/qwen3-pago", "google/gemma-livre:free", "qwen/qwen3-vl-30b:free",
                    "qwen/qwen3-30b-a3b:free");
        };

        ProvedorIA escolhido = ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", CHAVE), null, lista);

        ProvedorQwen qwen = assertInstanceOf(ProvedorQwen.class, escolhido);
        assertEquals("qwen/qwen3-30b-a3b:free", qwen.configuracao().modelo(), "Qwen gratuito de conversa");
        assertEquals(URI.create(ConfiguracaoOpenRouter.URL_PADRAO), consultado.get());
    }

    @Test
    void semQwenGratuitoSemListaOuSemModeloOChatSegueComMensagemClara() {
        Map<String, String> soChave = Map.of("OPENROUTER_API_KEY", CHAVE);

        ProvedorIA semGratuito = ProvedorIndisponivel.doAmbiente(soChave, null,
                base -> catalogo("qwen/qwen3-pago", "qwen/qwen-falso:free-nao", "outro/modelo:free"));
        assertEquals(ConfiguracaoOpenRouter.SEM_GRATUITO,
                assertInstanceOf(ProvedorIndisponivel.class, semGratuito).motivo());

        ProvedorIA semLista = ProvedorIndisponivel.doAmbiente(soChave, null, base -> {
            throw new IOException("sem internet");
        });
        assertEquals(ConfiguracaoOpenRouter.SEM_CATALOGO,
                assertInstanceOf(ProvedorIndisponivel.class, semLista).motivo());

        ProvedorIA semConsulta = ProvedorIndisponivel.doAmbiente(soChave);
        assertEquals(ConfiguracaoOpenRouter.SEM_MODELO,
                assertInstanceOf(ProvedorIndisponivel.class, semConsulta).motivo());

        FalhaIA falha = assertThrows(FalhaIA.class, () -> semConsulta.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.NAO_CONFIGURADO, falha.getCodigo());
        assertFalse(falha.getMessage().contains(CHAVE));
    }

    @Test
    void configuracoesInseguraOuForaDoQwenSaoRecusadas() {
        ProvedorIA http = ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", CHAVE,
                "OPENROUTER_MODEL", MODELO, "OPENROUTER_BASE_URL", "http://openrouter.ai/api/v1"));
        String motivo = assertInstanceOf(ProvedorIndisponivel.class, http).motivo();
        assertTrue(motivo.contains("https"), motivo);

        ProvedorIA outroModelo = ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", CHAVE,
                "OPENROUTER_MODEL", "google/gemma-livre:free"));
        motivo = assertInstanceOf(ProvedorIndisponivel.class, outroModelo).motivo();
        assertTrue(motivo.contains("OPENROUTER_MODEL") && motivo.contains("Qwen"), motivo);
        assertFalse(motivo.contains(CHAVE));
    }

    @Test
    void modeloPagoOuAusenteDaListaGeraAvisoNoLogSemAChave() {
        PrintStream original = System.err;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        System.setErr(new PrintStream(saida, true, StandardCharsets.UTF_8));
        try {
            ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", CHAVE,
                    "OPENROUTER_MODEL", "qwen/qwen3-pago"), null, base -> catalogo("qwen/qwen3-pago"));
            ProvedorIndisponivel.doAmbiente(Map.of("OPENROUTER_API_KEY", CHAVE,
                    "OPENROUTER_MODEL", "qwen/inexistente:free"), null, base -> catalogo("qwen/qwen3-pago"));
        } finally {
            System.setErr(original);
        }
        String log = saida.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("qwen/qwen3-pago não é gratuito"), log);
        assertTrue(log.contains("qwen/inexistente:free não aparece na lista"), log);
        assertFalse(log.contains(CHAVE));
    }

    @Test
    void consultaDaListaDeModelosNaoEnviaAChave() throws Exception {
        URI base = simular(200, catalogo("qwen/qwen3-30b-a3b:free"), false);

        String corpo = ConfiguracaoOpenRouter.http().modelos(base);

        assertEquals("/api/v1/models", caminho.get());
        assertNull(autorizacao.get(), "A lista é pública: a chave não sai do servidor");
        assertEquals(List.of(new ConfiguracaoOpenRouter.Modelo("qwen/qwen3-30b-a3b:free", true)),
                ConfiguracaoOpenRouter.lerModelos(corpo));
    }
}
