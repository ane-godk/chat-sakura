package br.unicesumar.chat.ia;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Qwen pela API da Groq (compatível com OpenAI), lida somente de variáveis
 * de ambiente:
 *
 * <ul>
 *   <li>{@code GROQ_API_KEY}: chave (obrigatória; criada em
 *       {@link #PAGINA_CHAVES});</li>
 *   <li>{@code GROQ_MODEL}: identificador do modelo (padrão
 *       {@link #MODELO_PADRAO}); precisa ser da família Qwen;</li>
 *   <li>{@code GROQ_BASE_URL}: base da API (padrão {@link #URL_PADRAO};
 *       precisa ser https, exceto {@code localhost} nos testes);</li>
 *   <li>{@code GROQ_TIMEOUT_SEGUNDOS}: tempo limite da resposta (padrão 60).</li>
 * </ul>
 *
 * Os ajustes opcionais de geração ({@code QWEN_TEMPERATURA},
 * {@code QWEN_RESPOSTA_TOKENS} etc.) continuam valendo, com o perfil remoto.
 * A chave vai somente no cabeçalho {@code Authorization: Bearer}: nunca no
 * código, no banco, nos logs ou nas mensagens de erro.
 */
public final class ConfiguracaoGroq {

    public static final String URL_PADRAO = "https://api.groq.com/openai/v1";
    public static final String MODELO_PADRAO = "qwen/qwen3.8-27b";
    public static final int TEMPO_PADRAO_S = 60;
    static final String PAGINA_CHAVES = "https://console.groq.com/keys";

    static final String SEM_CHAVE = "Defina GROQ_API_KEY (crie a chave em " + PAGINA_CHAVES + ") para usar o"
            + " Qwen pela Groq e reinicie o servidor. O INICIAR-COM-GROQ.bat pede a chave sem gravá-la.";

    private ConfiguracaoGroq() {
    }

    /** {@code true} se {@code GROQ_API_KEY} foi definida (e não está em branco). */
    public static boolean configurado(Map<String, String> ambiente) {
        return !isVazio(ambiente.get("GROQ_API_KEY"));
    }

    /** Provedor da Groq, ou {@link ProvedorIndisponivel} com o motivo (sem a chave). */
    static ProvedorIA provedor(Map<String, String> ambiente) {
        if (!configurado(ambiente)) {
            return new ProvedorIndisponivel(SEM_CHAVE);
        }
        try {
            return new ProvedorQwen(criar(ambiente));
        } catch (IllegalArgumentException e) {
            return new ProvedorIndisponivel("Configuração da Groq inválida: " + e.getMessage());
        }
    }

    /** @throws IllegalArgumentException se alguma variável for inválida (a mensagem nunca contém a chave) */
    static ConfiguracaoQwen criar(Map<String, String> ambiente) {
        int segundos = TEMPO_PADRAO_S;
        String tempo = ambiente.get("GROQ_TIMEOUT_SEGUNDOS");
        if (!isVazio(tempo)) {
            try {
                segundos = Math.max(5, Math.min(180, Integer.parseInt(tempo.strip())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("GROQ_TIMEOUT_SEGUNDOS deve ser um número.");
            }
        }

        String url = ambiente.get("GROQ_BASE_URL");
        URI base;
        try {
            base = URI.create(isVazio(url) ? URL_PADRAO : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("GROQ_BASE_URL inválida.");
        }

        String modelo = ambiente.get("GROQ_MODEL");
        return new ConfiguracaoQwen(base,
                isVazio(modelo) ? MODELO_PADRAO : modelo.strip(),
                ambiente.get("GROQ_API_KEY").strip(),
                Duration.ofSeconds(segundos),
                ConfiguracaoQwen.Ajustes.doAmbiente(ambiente, ConfiguracaoQwen.Api.OPENAI, false),
                ConfiguracaoQwen.Servico.GROQ);
    }

    private static boolean isVazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
