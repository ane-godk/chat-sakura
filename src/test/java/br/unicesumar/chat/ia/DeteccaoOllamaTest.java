package br.unicesumar.chat.ia;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Detecção opcional do qwen2.5:3b no Ollama local: só leitura da lista de
 * modelos, sem baixar nada, e sem substituir configurações explícitas.
 */
class DeteccaoOllamaTest {

    private static final String COM_QWEN =
            "{\"models\":[{\"name\":\"llama3:8b\"},{\"name\":\"qwen2.5:3b\",\"model\":\"qwen2.5:3b\"}]}";
    private static final String SEM_QWEN = "{\"models\":[{\"name\":\"llama3:8b\"},{\"name\":\"qwen2.5:7b\"}]}";

    @Test
    void reconheceOModeloComOuSemVariante() {
        assertTrue(DeteccaoOllama.possuiModelo(COM_QWEN));
        assertTrue(DeteccaoOllama.possuiModelo("{\"models\":[{\"name\":\"qwen2.5:3b-instruct-q4_K_M\"}]}"));
        assertFalse(DeteccaoOllama.possuiModelo(SEM_QWEN), "Outro tamanho do Qwen não serve");
        assertFalse(DeteccaoOllama.possuiModelo("{\"models\":[]}"));
        assertFalse(DeteccaoOllama.possuiModelo("não é json"));
        assertFalse(DeteccaoOllama.possuiModelo(null));
    }

    @Test
    void comOModeloInstaladoUsaOOllamaLocalComOsAjustesLocais() {
        ProvedorIA provedor = ProvedorIndisponivel.doAmbiente(Map.of("QWEN_KEEP_ALIVE", "5m"), () -> COM_QWEN);

        ProvedorQwen qwen = assertInstanceOf(ProvedorQwen.class, provedor);
        assertEquals("Qwen qwen2.5:3b", qwen.descricao());
        assertEquals(URI.create("http://127.0.0.1:11434/v1"), qwen.configuracao().baseUrl());
        assertEquals(ConfiguracaoQwen.Api.OLLAMA, qwen.configuracao().ajustes().api());
        assertEquals(ParametrosIA.LOCAL, qwen.parametros(), "Perfil do Ollama local");
        assertEquals("5m", qwen.configuracao().ajustes().keepAlive(), "Ajustes opcionais continuam valendo");
    }

    @Test
    void semOllamaOuSemOModeloOServidorSegueSemIAEExplicaOQueFazer() {
        ProvedorIA sem = ProvedorIndisponivel.doAmbiente(Map.of(), () -> {
            throw new IOException("Connection refused");
        });
        ProvedorIndisponivel semOllama = assertInstanceOf(ProvedorIndisponivel.class, sem);
        assertTrue(semOllama.motivo().contains("Ollama não encontrado"), semOllama.motivo());
        assertTrue(semOllama.motivo().contains("ollama pull qwen2.5:3b"), semOllama.motivo());

        ProvedorIndisponivel semModelo = assertInstanceOf(ProvedorIndisponivel.class,
                ProvedorIndisponivel.doAmbiente(Map.of(), () -> SEM_QWEN));
        assertTrue(semModelo.motivo().contains("não está instalado"), semModelo.motivo());
        assertTrue(semModelo.motivo().contains("ollama pull qwen2.5:3b"), semModelo.motivo());
    }

    @Test
    void configuracaoExplicitaNuncaESubstituidaNemConsultaOOllama() {
        AtomicInteger consultas = new AtomicInteger();
        DeteccaoOllama.Consulta contar = () -> {
            consultas.incrementAndGet();
            return COM_QWEN;
        };

        ProvedorQwen remoto = assertInstanceOf(ProvedorQwen.class,
                ProvedorIndisponivel.doAmbiente(Map.of("QWEN_API_KEY", "chave-ficticia"), contar));
        assertEquals("Qwen qwen-plus", remoto.descricao(), "Provedor remoto preservado");

        ProvedorQwen outroModelo = assertInstanceOf(ProvedorQwen.class, ProvedorIndisponivel.doAmbiente(
                Map.of("QWEN_BASE_URL", "http://localhost:11434/v1", "QWEN_MODEL", "qwen2.5:1.5b"), contar));
        assertEquals("Qwen qwen2.5:1.5b", outroModelo.descricao());

        assertInstanceOf(ProvedorIAFalso.class,
                ProvedorIndisponivel.doAmbiente(Map.of("CHAT_SAKURA_IA", "simulado"), contar));

        assertEquals(0, consultas.get(), "Com configuração explícita, o Ollama nem é consultado");
    }

    @Test
    void semDeteccaoOComportamentoAnteriorEMantido() {
        assertEquals("Qwen (não configurado)", ProvedorIndisponivel.doAmbiente(Map.of()).descricao());
    }

    /** Consulta HTTP real (GET /api/tags) a um servidor falso no loopback. */
    @Test
    void consultaHttpLeAListaDeModelos() throws Exception {
        HttpServer falso = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        falso.createContext("/api/tags", troca -> {
            byte[] corpo = COM_QWEN.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(200, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        falso.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + falso.getAddress().getPort());
            assertTrue(DeteccaoOllama.possuiModelo(DeteccaoOllama.http(base).modelosInstalados()));
        } finally {
            falso.stop(0);
        }

        // Nada escutando: falha rápida, tratada como "Ollama ausente".
        URI fechado = URI.create("http://127.0.0.1:" + falso.getAddress().getPort());
        assertTrue(DeteccaoOllama.detectar(Map.of(), DeteccaoOllama.http(fechado)).ambiente().isEmpty());
    }
}
