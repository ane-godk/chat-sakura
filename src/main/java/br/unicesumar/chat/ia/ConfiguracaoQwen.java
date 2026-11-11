package br.unicesumar.chat.ia;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Configuração do Qwen lida de variáveis de ambiente (nunca do código):
 *
 * <ul>
 *   <li>{@code QWEN_API_KEY}: chave da API (opcional para Ollama local);</li>
 *   <li>{@code QWEN_BASE_URL}: base compatível com OpenAI (padrão:
 *       Alibaba Cloud Model Studio, {@link #URL_PADRAO});</li>
 *   <li>{@code QWEN_MODEL}: modelo da família Qwen (padrão {@link #MODELO_PADRAO});</li>
 *   <li>{@code QWEN_TIMEOUT_SEGUNDOS}: tempo limite da resposta (padrão 45).</li>
 * </ul>
 *
 * {@link #toString()} nunca mostra a chave.
 */
public record ConfiguracaoQwen(URI baseUrl, String modelo, String chave, Duration tempoLimite) {

    public static final String URL_PADRAO = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
    public static final String MODELO_PADRAO = "qwen-plus";
    public static final int TEMPO_PADRAO_S = 45;

    public ConfiguracaoQwen {
        if (modelo == null || !modelo.toLowerCase(Locale.ROOT).contains("qwen")) {
            throw new IllegalArgumentException("QWEN_MODEL deve ser um modelo da família Qwen.");
        }
        if (baseUrl == null || baseUrl.getScheme() == null
                || !(baseUrl.getScheme().equals("https") || baseUrl.getScheme().equals("http"))) {
            throw new IllegalArgumentException("QWEN_BASE_URL deve ser uma URL http(s).");
        }
        if (chave != null && chave.isBlank()) {
            chave = null;
        }
        if (chave == null && !local(baseUrl)) {
            throw new IllegalArgumentException("Defina QWEN_API_KEY para usar a API do Qwen.");
        }
    }

    /** Endereço de chat completions (OpenAI compatível). */
    public URI endpoint() {
        String base = baseUrl.toString().replaceAll("/+$", "");
        return URI.create(base + "/chat/completions");
    }

    /** Servidor local (Ollama): dispensa chave. */
    public static boolean local(URI url) {
        String host = url.getHost();
        return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    }

    /**
     * @return a configuração, ou vazio se nenhuma variável do Qwen foi definida
     * @throws IllegalArgumentException se as variáveis definidas forem inválidas
     */
    public static Optional<ConfiguracaoQwen> doAmbiente(Map<String, String> ambiente) {

        String chave = ambiente.get("QWEN_API_KEY");
        String url = ambiente.get("QWEN_BASE_URL");
        String modelo = ambiente.get("QWEN_MODEL");

        if (isVazio(chave) && isVazio(url) && isVazio(modelo)) {
            return Optional.empty();
        }

        int segundos = TEMPO_PADRAO_S;
        String tempo = ambiente.get("QWEN_TIMEOUT_SEGUNDOS");
        if (!isVazio(tempo)) {
            try {
                segundos = Math.max(5, Math.min(120, Integer.parseInt(tempo.strip())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("QWEN_TIMEOUT_SEGUNDOS deve ser um número.");
            }
        }

        URI base;
        try {
            base = URI.create(isVazio(url) ? URL_PADRAO : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("QWEN_BASE_URL inválida.");
        }

        return Optional.of(new ConfiguracaoQwen(
                base,
                isVazio(modelo) ? MODELO_PADRAO : modelo.strip(),
                isVazio(chave) ? null : chave.strip(),
                Duration.ofSeconds(segundos)));
    }

    private static boolean isVazio(String valor) {
        return valor == null || valor.isBlank();
    }

    @Override
    public String toString() {
        return "ConfiguracaoQwen[" + baseUrl + ", " + modelo + ", chave="
                + (chave == null ? "ausente" : "****") + ", " + tempoLimite.toSeconds() + "s]";
    }
}
