package br.unicesumar.chat.ia;

import br.unicesumar.chat.ia.ProvedorIA.FalhaIA;
import br.unicesumar.chat.ia.ProvedorIA.Finalidade;
import br.unicesumar.chat.ia.ProvedorIA.Papel;
import br.unicesumar.chat.ia.ProvedorIA.PedidoIA;
import br.unicesumar.chat.ia.ProvedorIA.Turno;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
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
 * Qwen pela Groq contra um servidor HTTP local que imita a API
 * ({@code /openai/v1/chat/completions}). Nenhum pedido sai para a internet
 * e nenhuma chave real é usada.
 */
class ProvedorGroqTest {

    private static final String CHAVE = "gsk_teste_NAO_REAL_0000";

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

    /** Sobe a Groq simulada e devolve a base ({@code http://127.0.0.1:porta/openai/v1}). */
    private URI simular(int status, String corpo, boolean espera) throws IOException {
        if (servidor != null) {
            servidor.stop(0);
        }
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/openai/v1/", troca -> {
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
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/openai/v1");
    }

    private static Map<String, String> ambiente(URI base, String... extras) {
        Map<String, String> ambiente = new HashMap<>();
        ambiente.put("CHAT_SAKURA_IA", "groq");
        ambiente.put("GROQ_API_KEY", CHAVE);
        ambiente.put("GROQ_BASE_URL", base.toString());
        for (int i = 0; i + 1 < extras.length; i += 2) {
            ambiente.put(extras[i], extras[i + 1]);
        }
        return ambiente;
    }

    private ProvedorQwen provedor(int status, String corpo) throws IOException {
        return provedor(simular(status, corpo, false));
    }

    private static ProvedorQwen provedor(URI base, String... extras) {
        ProvedorIA provedor = ProvedorIndisponivel.doAmbiente(ambiente(base, extras), null);
        return assertInstanceOf(ProvedorQwen.class, provedor);
    }

    private static String respostaOpenAI(String conteudo, String motivo) {
        return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"model\":\"" + ConfiguracaoGroq.MODELO_PADRAO
                + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
                + JsonParser.parseString("\"" + conteudo + "\"") + "},\"finish_reason\":\"" + motivo + "\"}],"
                + "\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":5,\"total_tokens\":25}}";
    }

    private static PedidoIA pedido() {
        return new PedidoIA("Você é a Sakura, gentil e curiosa.", List.of(
                new Turno(Papel.USUARIO, "Oi! Lembra do meu gato?"),
                new Turno(Papel.ASSISTENTE, "Lembro! O Mochi."),
                new Turno(Papel.USUARIO, "Ele fez aniversário.")), 400);
    }

    @Test
    void deveChamarAGroqComBearerEOFormatoCompativel() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("Parabéns ao Mochi! 🌸", "stop"));

        assertEquals("Parabéns ao Mochi! 🌸", qwen.gerar(pedido()));
        assertEquals("/openai/v1/chat/completions", caminho.get());
        assertEquals("Bearer " + CHAVE, autorizacao.get());

        JsonObject corpo = JsonParser.parseString(corpoRecebido.get()).getAsJsonObject();
        assertEquals(ConfiguracaoGroq.MODELO_PADRAO, corpo.get("model").getAsString());
        assertEquals(400, corpo.get("max_completion_tokens").getAsInt());
        assertEquals("none", corpo.get("reasoning_effort").getAsString(), "Sem raciocínio interno");
        assertFalse(corpo.get("stream").getAsBoolean());
        assertEquals(0.65, corpo.get("temperature").getAsDouble(), 1e-9);
        assertEquals(0.9, corpo.get("top_p").getAsDouble(), 1e-9);
        for (String ausente : new String[] {"max_tokens", "presence_penalty", "frequency_penalty", "reasoning",
                "reasoning_format", "include_reasoning", "options", "keep_alive", "num_ctx", "n", "logprobs"}) {
            assertFalse(corpo.has(ausente), "Parâmetro não aceito ou exclusivo de outro serviço: " + ausente);
        }
        assertFalse(corpoRecebido.get().contains(CHAVE), "A chave vai só no cabeçalho");

        assertEquals("Qwen " + ConfiguracaoGroq.MODELO_PADRAO + " (Groq)", qwen.descricao());
        assertEquals(ParametrosIA.REMOTO, qwen.parametros(), "Perfil remoto, mesmo em servidor de teste local");
        assertFalse(qwen.configuracao().toString().contains(CHAVE));
    }

    @Test
    void personalidadeEHistoricoVaoNaOrdemEComOsPapeisCorretos() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("ok", "stop"));

        qwen.gerar(pedido());

        JsonArray mensagens = JsonParser.parseString(corpoRecebido.get()).getAsJsonObject()
                .getAsJsonArray("messages");
        assertEquals(4, mensagens.size());
        String[][] esperado = {
                {"system", "Você é a Sakura, gentil e curiosa."},
                {"user", "Oi! Lembra do meu gato?"},
                {"assistant", "Lembro! O Mochi."},
                {"user", "Ele fez aniversário."},
        };
        for (int i = 0; i < esperado.length; i++) {
            JsonObject mensagem = mensagens.get(i).getAsJsonObject();
            assertEquals(esperado[i][0], mensagem.get("role").getAsString());
            assertEquals(esperado[i][1], mensagem.get("content").getAsString());
            assertEquals(2, mensagem.size(), "Só role e content (messages[].name não é aceito)");
        }
    }

    @Test
    void resumoDeMemoriaUsaTemperaturaBaixaERespostaCortadaEInformada() throws Exception {
        ProvedorQwen qwen = provedor(200, respostaOpenAI("Resumo parcial <think>x</think>", "length"));

        ProvedorIA.Resposta resposta = qwen.gerarResposta(new PedidoIA("Resuma a conversa.",
                List.of(new Turno(Papel.USUARIO, "texto")), 200, Finalidade.RESUMO));

        assertTrue(resposta.truncada(), "finish_reason=length");
        assertEquals("Resumo parcial", resposta.texto(), "Raciocínio eventual nunca aparece");
        JsonObject corpo = JsonParser.parseString(corpoRecebido.get()).getAsJsonObject();
        assertEquals(ProvedorQwen.TEMPERATURA_RESUMO, corpo.get("temperature").getAsDouble(), 1e-9);
    }

    @Test
    void respostaVaziaOuForaDoFormatoEFalhaClara() throws Exception {
        FalhaIA vazia = assertThrows(FalhaIA.class,
                () -> provedor(200, respostaOpenAI("  ", "stop")).gerar(pedido()));
        assertEquals(FalhaIA.Codigo.RESPOSTA_INVALIDA, vazia.getCodigo());

        FalhaIA estranha = assertThrows(FalhaIA.class, () -> provedor(200, "<html>erro</html>").gerar(pedido()));
        assertEquals(FalhaIA.Codigo.RESPOSTA_INVALIDA, estranha.getCodigo());
    }

    @Test
    void errosHttpDaGroqViramMensagensClarasSemExporAChave() throws Exception {
        Object[][] casos = {
                {400, FalhaIA.Codigo.NAO_CONFIGURADO, "GROQ_MODEL"},
                {401, FalhaIA.Codigo.AUTENTICACAO, "GROQ_API_KEY"},
                {402, FalhaIA.Codigo.CREDITOS, "saldo"},
                {403, FalhaIA.Codigo.AUTENTICACAO, "sem permissão"},
                {404, FalhaIA.Codigo.NAO_CONFIGURADO, "não foi encontrado"},
                {413, FalhaIA.Codigo.LIMITE, "grande demais"},
                {422, FalhaIA.Codigo.NAO_CONFIGURADO, "GROQ_MODEL"},
                {429, FalhaIA.Codigo.LIMITE, "Limite de uso gratuito"},
                {498, FalhaIA.Codigo.INDISPONIVEL, "temporariamente indisponível"},
                {500, FalhaIA.Codigo.INDISPONIVEL, "temporariamente indisponível"},
                {502, FalhaIA.Codigo.INDISPONIVEL, "temporariamente indisponível"},
                {503, FalhaIA.Codigo.INDISPONIVEL, "temporariamente indisponível"},
                {418, FalhaIA.Codigo.INDISPONIVEL, "(418)"},
        };
        for (Object[] caso : casos) {
            ProvedorQwen qwen = provedor((Integer) caso[0],
                    "{\"error\":{\"message\":\"detalhe com " + CHAVE + "\",\"type\":\"invalid_request_error\"}}");
            FalhaIA falha = assertThrows(FalhaIA.class, () -> qwen.gerar(pedido()), "HTTP " + caso[0]);
            assertEquals(caso[1], falha.getCodigo(), "HTTP " + caso[0]);
            assertTrue(falha.getMessage().contains((String) caso[2]), falha.getMessage());
            assertTrue(falha.getMessage().contains("Groq") || falha.getMessage().contains("GROQ"), falha.getMessage());
            assertFalse(falha.getMessage().contains(CHAVE), "Nunca repete o corpo nem a chave");
        }
    }

    @Test
    void semInternetOuSemRespostaAFalhaEClaraENaoTrava() throws Exception {
        int portaFechada;
        try (ServerSocket livre = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaFechada = livre.getLocalPort();
        }
        ProvedorQwen semRede = provedor(URI.create("http://127.0.0.1:" + portaFechada + "/openai/v1"));
        FalhaIA rede = assertThrows(FalhaIA.class, () -> semRede.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.INDISPONIVEL, rede.getCodigo());
        assertTrue(rede.getMessage().contains("Groq"), rede.getMessage());

        ProvedorQwen lento = provedor(simular(200, respostaOpenAI("tarde", "stop"), true),
                "GROQ_TIMEOUT_SEGUNDOS", "1");
        long inicio = System.nanoTime();
        FalhaIA tempo = assertThrows(FalhaIA.class, () -> lento.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.TEMPO_ESGOTADO, tempo.getCodigo());
        assertTrue(tempo.getMessage().contains("Groq"), tempo.getMessage());
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - inicio) < 8, "Respeita o tempo limite");
    }

    @Test
    void comAChaveDaGroqOOllamaNaoEConsultado() {
        AtomicInteger consultasOllama = new AtomicInteger();
        DeteccaoOllama.Consulta ollama = () -> {
            consultasOllama.incrementAndGet();
            return "{\"models\":[{\"name\":\"qwen2.5:3b\"}]}";
        };

        ProvedorIA escolhido = ProvedorIndisponivel.doAmbiente(Map.of("CHAT_SAKURA_IA", "groq",
                "GROQ_API_KEY", CHAVE, "QWEN_API_KEY", "outra"), ollama);

        ProvedorQwen qwen = assertInstanceOf(ProvedorQwen.class, escolhido);
        assertTrue(qwen.configuracao().groq());
        assertEquals(CHAVE, qwen.configuracao().chave());
        assertEquals(URI.create("https://api.groq.com/openai/v1/chat/completions"), qwen.configuracao().endpoint());
        assertEquals(ConfiguracaoGroq.MODELO_PADRAO, qwen.configuracao().modelo());
        assertEquals(0, consultasOllama.get());

        ProvedorIA semEscolha = ProvedorIndisponivel.doAmbiente(Map.of("GROQ_API_KEY", CHAVE,
                "QWEN_API_KEY", "outra"), ollama);
        assertTrue(assertInstanceOf(ProvedorQwen.class, semEscolha).configuracao().groq(),
                "Sem CHAT_SAKURA_IA (auto), a Groq vem antes de QWEN_* e do Ollama");
        assertEquals(0, consultasOllama.get());
    }

    @Test
    void outrasEscolhasExplicitasContinuamFuncionando() {
        DeteccaoOllama.Consulta ollama = () -> "{\"models\":[{\"name\":\"qwen2.5:3b\"}]}";
        Map<String, String> ambiente = new HashMap<>(Map.of("GROQ_API_KEY", CHAVE));

        ambiente.put("CHAT_SAKURA_IA", "qwen");
        ProvedorQwen local = assertInstanceOf(ProvedorQwen.class, ProvedorIndisponivel.doAmbiente(ambiente, ollama));
        assertEquals(ConfiguracaoQwen.Servico.QWEN, local.configuracao().servico());
        assertEquals(DeteccaoOllama.MODELO, local.configuracao().modelo());

        ambiente.put("CHAT_SAKURA_IA", "simulado");
        assertTrue(ProvedorIndisponivel.doAmbiente(ambiente, ollama).simulado());

        ambiente.put("CHAT_SAKURA_IA", "auto");
        assertTrue(assertInstanceOf(ProvedorQwen.class,
                ProvedorIndisponivel.doAmbiente(ambiente, ollama)).configuracao().groq());

        ambiente.put("CHAT_SAKURA_IA", "grok");
        assertEquals(ProvedorIndisponivel.ESCOLHA_INVALIDA, assertInstanceOf(ProvedorIndisponivel.class,
                ProvedorIndisponivel.doAmbiente(ambiente, ollama)).motivo(), "Erro de digitação não troca de provedor");

        ProvedorQwen semGroq = assertInstanceOf(ProvedorQwen.class,
                ProvedorIndisponivel.doAmbiente(Map.of(), ollama));
        assertEquals(DeteccaoOllama.MODELO, semGroq.configuracao().modelo(), "Auto sem chave: Ollama detectado");
    }

    @Test
    void groqEscolhidaSemChaveNaoCaiParaOutroProvedor() throws Exception {
        AtomicInteger consultasOllama = new AtomicInteger();
        DeteccaoOllama.Consulta ollama = () -> {
            consultasOllama.incrementAndGet();
            return "{\"models\":[{\"name\":\"qwen2.5:3b\"}]}";
        };

        ProvedorIA semChave = ProvedorIndisponivel.doAmbiente(Map.of("CHAT_SAKURA_IA", "groq",
                "GROQ_API_KEY", "   ", "QWEN_API_KEY", "outra"), ollama);

        ProvedorIndisponivel indisponivel = assertInstanceOf(ProvedorIndisponivel.class, semChave);
        assertEquals(ConfiguracaoGroq.SEM_CHAVE, indisponivel.motivo());
        assertEquals(0, consultasOllama.get());
        FalhaIA falha = assertThrows(FalhaIA.class, () -> semChave.gerar(pedido()));
        assertEquals(FalhaIA.Codigo.NAO_CONFIGURADO, falha.getCodigo());
        assertFalse(falha.getMessage().contains("outra"));
    }

    @Test
    void modeloConfiguravelEConfiguracoesInsegurasOuForaDoQwenSaoRecusadas() {
        ProvedorQwen outro = assertInstanceOf(ProvedorQwen.class, ProvedorIndisponivel.doAmbiente(Map.of(
                "CHAT_SAKURA_IA", "groq", "GROQ_API_KEY", CHAVE, "GROQ_MODEL", " qwen/qwen3-32b ")));
        assertEquals("qwen/qwen3-32b", outro.configuracao().modelo());
        assertEquals(URI.create(ConfiguracaoGroq.URL_PADRAO), outro.configuracao().baseUrl());

        String motivo = assertInstanceOf(ProvedorIndisponivel.class, ProvedorIndisponivel.doAmbiente(Map.of(
                "CHAT_SAKURA_IA", "groq", "GROQ_API_KEY", CHAVE, "GROQ_BASE_URL", "http://api.groq.com/openai/v1")))
                .motivo();
        assertTrue(motivo.contains("GROQ_BASE_URL") && motivo.contains("https"), motivo);
        assertFalse(motivo.contains(CHAVE));

        motivo = assertInstanceOf(ProvedorIndisponivel.class, ProvedorIndisponivel.doAmbiente(Map.of(
                "CHAT_SAKURA_IA", "groq", "GROQ_API_KEY", CHAVE, "GROQ_MODEL", "llama-3.3-70b-versatile")))
                .motivo();
        assertTrue(motivo.contains("GROQ_MODEL") && motivo.contains("Qwen"), motivo);
        assertFalse(motivo.contains(CHAVE));

        motivo = assertInstanceOf(ProvedorIndisponivel.class, ProvedorIndisponivel.doAmbiente(Map.of(
                "CHAT_SAKURA_IA", "groq", "GROQ_API_KEY", CHAVE, "GROQ_TIMEOUT_SEGUNDOS", "abc"))).motivo();
        assertTrue(motivo.contains("GROQ_TIMEOUT_SEGUNDOS"), motivo);
    }
}
