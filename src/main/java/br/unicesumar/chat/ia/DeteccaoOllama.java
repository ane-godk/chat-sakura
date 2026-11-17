package br.unicesumar.chat.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Detecção opcional do Qwen no Ollama local, usada somente quando nenhuma
 * variável {@code GROQ_API_KEY}, {@code OPENROUTER_API_KEY}, {@code QWEN_API_KEY},
 * {@code QWEN_BASE_URL} ou {@code QWEN_MODEL} foi definida (configurações
 * explícitas sempre prevalecem).
 *
 * Apenas lê a lista de modelos ({@code GET /api/tags}) no próprio computador
 * (127.0.0.1): não baixa modelos, não altera a configuração do Ollama e não
 * acessa a rede externa.
 */
public final class DeteccaoOllama {

    public static final String MODELO = "qwen2.5:3b";
    public static final URI ENDERECO = URI.create("http://127.0.0.1:" + ConfiguracaoQwen.PORTA_OLLAMA);
    static final Duration PRAZO = Duration.ofSeconds(2);

    static final String SEM_OLLAMA = "Ollama não encontrado em " + ENDERECO + ". Para usar os personagens de IA,"
            + " instale o Ollama, rode \"ollama pull " + MODELO + "\" e reinicie o servidor"
            + " (ou use o INICIAR-COM-GROQ.bat para usar o Qwen pela internet, via Groq).";
    static final String SEM_MODELO = "O Ollama está em execução, mas o modelo " + MODELO
            + " não está instalado. Rode \"ollama pull " + MODELO + "\" e reinicie o servidor.";

    /** Lê o corpo de {@code GET /api/tags} (substituível nos testes). */
    public interface Consulta {
        String modelosInstalados() throws IOException;
    }

    /** Resultado: as variáveis do Qwen a usar, ou o motivo de não haver IA. */
    public record Resultado(Optional<Map<String, String>> ambiente, String motivo) {
        public Resultado {
            Objects.requireNonNull(ambiente);
        }
    }

    private DeteccaoOllama() {
    }

    /** Consulta HTTP real ao Ollama local, com prazo curto. */
    public static Consulta local() {
        return http(ENDERECO);
    }

    /** {@code GET <base>/api/tags} (base substituível nos testes). */
    static Consulta http(URI base) {
        return () -> {
            HttpClient cliente = HttpClient.newBuilder().connectTimeout(PRAZO).build();
            HttpRequest pedido = HttpRequest.newBuilder(base.resolve("/api/tags")).timeout(PRAZO).GET().build();
            try {
                HttpResponse<String> resposta = cliente.send(pedido,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resposta.statusCode() != 200) {
                    throw new IOException("HTTP " + resposta.statusCode());
                }
                return resposta.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Consulta interrompida.");
            }
        };
    }

    /**
     * @param ambiente variáveis atuais (ajustes opcionais como
     *                 {@code QWEN_KEEP_ALIVE} continuam valendo)
     */
    public static Resultado detectar(Map<String, String> ambiente, Consulta consulta) {
        String corpo;
        try {
            corpo = consulta.modelosInstalados();
        } catch (IOException | RuntimeException e) {
            return new Resultado(Optional.empty(), SEM_OLLAMA);
        }

        if (!possuiModelo(corpo)) {
            return new Resultado(Optional.empty(), corpo == null ? SEM_OLLAMA : SEM_MODELO);
        }

        Map<String, String> configurado = new HashMap<>(ambiente);
        configurado.put("QWEN_BASE_URL", ENDERECO + "/v1");
        configurado.put("QWEN_MODEL", MODELO);
        return new Resultado(Optional.of(Map.copyOf(configurado)), null);
    }

    /** {@code true} se a lista do Ollama contém {@code qwen2.5:3b} (com ou sem sufixo de variante). */
    static boolean possuiModelo(String corpo) {
        if (corpo == null) {
            return false;
        }
        try {
            JsonElement raiz = JsonParser.parseString(corpo);
            if (!raiz.isJsonObject() || !raiz.getAsJsonObject().has("models")) {
                return false;
            }
            JsonArray modelos = raiz.getAsJsonObject().getAsJsonArray("models");
            for (JsonElement item : modelos) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject modelo = item.getAsJsonObject();
                for (String campo : new String[] {"name", "model"}) {
                    if (modelo.has(campo) && modelo.get(campo).isJsonPrimitive()) {
                        String nome = modelo.get(campo).getAsString();
                        if (nome.equals(MODELO) || nome.startsWith(MODELO + "-") || nome.equals(MODELO + ":latest")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
