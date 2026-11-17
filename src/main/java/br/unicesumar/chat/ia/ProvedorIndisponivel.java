package br.unicesumar.chat.ia;

import java.util.Locale;
import java.util.Map;

/** Usado quando o Qwen não está configurado: responde com orientação clara, sem simular. */
public final class ProvedorIndisponivel implements ProvedorIA {

    static final String ORIENTACAO = "A Sakura IA (Qwen) não está configurada no servidor."
            + " Defina GROQ_API_KEY (Qwen pela Groq), QWEN_API_KEY ou QWEN_BASE_URL do Ollama"
            + " e reinicie o servidor.";

    static final String ESCOLHA_INVALIDA = "Valor de CHAT_SAKURA_IA não reconhecido. Use groq, qwen,"
            + " simulado ou auto e reinicie o servidor.";

    private final String motivo;

    public ProvedorIndisponivel(String motivo) {
        this.motivo = motivo == null ? ORIENTACAO : motivo;
    }

    @Override
    public String gerar(PedidoIA pedido) throws FalhaIA {
        throw new FalhaIA(FalhaIA.Codigo.NAO_CONFIGURADO, motivo);
    }

    @Override
    public String descricao() {
        return "Qwen (não configurado)";
    }

    /** O que falta configurar (sem valores das variáveis, nunca a chave). */
    public String motivo() {
        return motivo;
    }

    /**
     * Escolhe o provedor do servidor sem consultas de rede (ver
     * {@link #doAmbiente(Map, DeteccaoOllama.Consulta)}).
     */
    public static ProvedorIA doAmbiente(Map<String, String> ambiente) {
        return doAmbiente(ambiente, null);
    }

    /**
     * Como {@link #doAmbiente(Map)}, procurando também, sem Groq nem
     * variáveis do Qwen, o {@code qwen2.5:3b} no Ollama local
     * ({@link DeteccaoOllama}). Variáveis definidas pelo usuário nunca são
     * substituídas.
     */
    public static ProvedorIA doAmbienteComOllama(Map<String, String> ambiente) {
        return doAmbiente(ambiente, DeteccaoOllama.local());
    }

    /**
     * Escolha do provedor por {@code CHAT_SAKURA_IA}:
     *
     * <ul>
     *   <li>{@code simulado}: provedor SIMULADO;</li>
     *   <li>{@code groq}: somente a Groq ({@code GROQ_*});</li>
     *   <li>{@code qwen} (ou {@code ollama}/{@code local}): somente as
     *       variáveis {@code QWEN_*} ou o Ollama detectado;</li>
     *   <li>vazio ou {@code auto}: Groq com {@code GROQ_API_KEY}; senão
     *       {@code QWEN_*} ou o Ollama detectado.</li>
     * </ul>
     *
     * Um provedor escolhido explicitamente nunca é trocado por outro: se
     * faltar algo, a IA fica indisponível com a explicação (o chat continua).
     *
     * @param ollama consulta ao Ollama local ({@code null}: sem detecção)
     */
    public static ProvedorIA doAmbiente(Map<String, String> ambiente, DeteccaoOllama.Consulta ollama) {

        String escolha = ambiente.getOrDefault("CHAT_SAKURA_IA", "").strip().toLowerCase(Locale.ROOT);

        switch (escolha) {
            case "simulado":
                return new ProvedorIAFalso();
            case "groq":
                return ConfiguracaoGroq.provedor(ambiente);
            case "qwen", "ollama", "local":
                return qwenOuOllama(ambiente, ollama);
            case "", "auto":
                break;
            default:
                return new ProvedorIndisponivel(ESCOLHA_INVALIDA);
        }

        if (ConfiguracaoGroq.configurado(ambiente)) {
            return ConfiguracaoGroq.provedor(ambiente);
        }

        return qwenOuOllama(ambiente, ollama);
    }

    /** Variáveis {@code QWEN_*}; sem elas, o Ollama detectado (se houver consulta). */
    private static ProvedorIA qwenOuOllama(Map<String, String> ambiente, DeteccaoOllama.Consulta ollama) {

        Map<String, String> efetivo = ambiente;

        if (ollama != null && semConfiguracaoQwen(ambiente)) {
            DeteccaoOllama.Resultado resultado = DeteccaoOllama.detectar(ambiente, ollama);
            if (resultado.ambiente().isEmpty()) {
                return new ProvedorIndisponivel(resultado.motivo());
            }
            efetivo = resultado.ambiente().get();
        }

        try {
            return ConfiguracaoQwen.doAmbiente(efetivo)
                    .<ProvedorIA>map(ProvedorQwen::new)
                    .orElseGet(() -> new ProvedorIndisponivel(null));
        } catch (IllegalArgumentException e) {
            return new ProvedorIndisponivel("Configuração do Qwen inválida: " + e.getMessage());
        }
    }

    private static boolean semConfiguracaoQwen(Map<String, String> ambiente) {
        for (String nome : new String[] {"QWEN_API_KEY", "QWEN_BASE_URL", "QWEN_MODEL"}) {
            String valor = ambiente.get(nome);
            if (valor != null && !valor.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
