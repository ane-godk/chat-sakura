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
 *   <li>{@code QWEN_TIMEOUT_SEGUNDOS}: tempo limite da resposta (padrão 45);</li>
 *   <li>ajustes de geração e de contexto: ver {@link Ajustes#doAmbiente}.</li>
 * </ul>
 *
 * {@link #toString()} nunca mostra a chave.
 */
public record ConfiguracaoQwen(URI baseUrl, String modelo, String chave, Duration tempoLimite, Ajustes ajustes) {

    public static final String URL_PADRAO = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
    public static final String MODELO_PADRAO = "qwen-plus";
    public static final int TEMPO_PADRAO_S = 45;
    public static final int PORTA_OLLAMA = 11434;

    /** Forma de chamar o serviço. */
    public enum Api {
        /** {@code POST /chat/completions} (Model Studio, outros hosts e o {@code /v1} do Ollama). */
        OPENAI,
        /**
         * {@code POST /api/chat} nativo do Ollama: aceita {@code num_ctx},
         * {@code keep_alive} e {@code repeat_penalty}, que o {@code /v1} ignora.
         */
        OLLAMA
    }

    /**
     * Ajustes de geração e de uso.
     *
     * @param keepAlive tempo que o Ollama mantém o modelo carregado depois do
     *                  último pedido (só na API nativa; vale por pedido e não
     *                  altera a configuração global do Ollama)
     * @param aquecer   carregar o modelo em segundo plano ao iniciar o servidor
     */
    public record Ajustes(Api api, ParametrosIA parametros, double temperatura, double topP,
                          double penalidadeRepeticao, String keepAlive, boolean aquecer) {

        public Ajustes {
            if (api == null || parametros == null || keepAlive == null || keepAlive.isBlank()) {
                throw new IllegalArgumentException("Ajustes do Qwen incompletos.");
            }
        }

        /** Padrões: local (Ollama) mais conservador; remoto como antes, com amostragem ajustada. */
        public static Ajustes padrao(Api api, boolean local) {
            // penalidadeRepeticao na escala do repeat_penalty do Ollama (1.0 = nenhuma).
            return new Ajustes(api, local ? ParametrosIA.LOCAL : ParametrosIA.REMOTO,
                    0.65, 0.9, 1.1, "30m", false);
        }

        /**
         * Variáveis opcionais (valores fora do intervalo são ajustados ao limite):
         * {@code QWEN_API} (auto, openai ou ollama), {@code QWEN_CONTEXTO_TOKENS},
         * {@code QWEN_RESPOSTA_TOKENS}, {@code QWEN_HISTORICO_MENSAGENS},
         * {@code QWEN_GERACOES_SIMULTANEAS}, {@code QWEN_FILA_MAXIMA},
         * {@code QWEN_TEMPERATURA}, {@code QWEN_TOP_P}, {@code QWEN_KEEP_ALIVE}
         * e {@code QWEN_AQUECER} (true/1 para ativar).
         */
        static Ajustes doAmbiente(Map<String, String> ambiente, URI base) {

            Api api = apiDe(ambiente.get("QWEN_API"), base);
            boolean local = local(base);
            Ajustes padrao = padrao(api, local);
            ParametrosIA p = padrao.parametros();

            int janela = inteiro(ambiente, "QWEN_CONTEXTO_TOKENS", p.janelaTokens(), ParametrosIA.JANELA_MINIMA,
                    131_072);
            int resposta = inteiro(ambiente, "QWEN_RESPOSTA_TOKENS", p.tokensResposta(), 64, Math.min(2_048, janela / 2 - 1));

            ParametrosIA parametros = new ParametrosIA(
                    janela,
                    resposta,
                    inteiro(ambiente, "QWEN_HISTORICO_MENSAGENS", p.historicoMensagens(), 2, 60),
                    inteiro(ambiente, "QWEN_GERACOES_SIMULTANEAS", p.geracoesSimultaneas(), 1, 8),
                    inteiro(ambiente, "QWEN_FILA_MAXIMA", p.filaMaxima(), 1, 64));

            String keepAlive = ambiente.getOrDefault("QWEN_KEEP_ALIVE", padrao.keepAlive()).strip();
            if (!keepAlive.matches("-?\\d+[smh]?")) {
                keepAlive = padrao.keepAlive();
            }

            String aquecer = ambiente.getOrDefault("QWEN_AQUECER", "").strip().toLowerCase(Locale.ROOT);

            return new Ajustes(api, parametros,
                    decimal(ambiente, "QWEN_TEMPERATURA", padrao.temperatura(), 0.0, 1.5),
                    decimal(ambiente, "QWEN_TOP_P", padrao.topP(), 0.1, 1.0),
                    padrao.penalidadeRepeticao(),
                    keepAlive,
                    aquecer.equals("true") || aquecer.equals("1") || aquecer.equals("sim"));
        }
    }

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
        if (ajustes == null) {
            ajustes = Ajustes.padrao(apiDe(null, baseUrl), local(baseUrl));
        }
    }

    /** Ajustes padrão conforme o endereço (Ollama local detectado pela porta 11434). */
    public ConfiguracaoQwen(URI baseUrl, String modelo, String chave, Duration tempoLimite) {
        this(baseUrl, modelo, chave, tempoLimite, null);
    }

    /** Endereço de chat completions (OpenAI compatível). */
    public URI endpoint() {
        String base = baseUrl.toString().replaceAll("/+$", "");
        return URI.create(base + "/chat/completions");
    }

    /** Endereço nativo do Ollama: a base sem o sufixo {@code /v1}, mais {@code /api/chat}. */
    public URI endpointOllama() {
        String base = baseUrl.toString().replaceAll("/+$", "").replaceAll("/v1$", "");
        return URI.create(base + "/api/chat");
    }

    /** Servidor local (Ollama): dispensa chave. */
    public static boolean local(URI url) {
        String host = url.getHost();
        return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    }

    /** {@code auto} (padrão): API nativa quando a base é o Ollama local na porta 11434. */
    static Api apiDe(String valor, URI base) {
        String api = valor == null ? "auto" : valor.strip().toLowerCase(Locale.ROOT);
        return switch (api) {
            case "openai" -> Api.OPENAI;
            case "ollama" -> Api.OLLAMA;
            default -> local(base) && base.getPort() == PORTA_OLLAMA ? Api.OLLAMA : Api.OPENAI;
        };
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
                Duration.ofSeconds(segundos),
                base.getScheme() == null ? null : Ajustes.doAmbiente(ambiente, base)));
    }

    private static boolean isVazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static int inteiro(Map<String, String> ambiente, String nome, int padrao, int minimo, int maximo) {
        String valor = ambiente.get(nome);
        if (isVazio(valor)) {
            return padrao;
        }
        try {
            return Math.max(minimo, Math.min(maximo, Integer.parseInt(valor.strip())));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static double decimal(Map<String, String> ambiente, String nome, double padrao, double minimo,
                                  double maximo) {
        String valor = ambiente.get(nome);
        if (isVazio(valor)) {
            return padrao;
        }
        try {
            return Math.max(minimo, Math.min(maximo, Double.parseDouble(valor.strip().replace(',', '.'))));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    @Override
    public String toString() {
        return "ConfiguracaoQwen[" + baseUrl + ", " + modelo + ", chave="
                + (chave == null ? "ausente" : "****") + ", " + tempoLimite.toSeconds() + "s, "
                + ajustes.api() + "]";
    }
}
