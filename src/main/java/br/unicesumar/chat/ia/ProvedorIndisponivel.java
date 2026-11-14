package br.unicesumar.chat.ia;

import java.util.Map;

/** Usado quando o Qwen não está configurado: responde com orientação clara, sem simular. */
public final class ProvedorIndisponivel implements ProvedorIA {

    static final String ORIENTACAO = "A Sakura IA (Qwen) não está configurada no servidor."
            + " Defina QWEN_API_KEY (ou QWEN_BASE_URL do Ollama) e reinicie o servidor.";

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
     * Escolhe o provedor do servidor: {@code CHAT_SAKURA_IA=simulado} usa o
     * SIMULADO; caso contrário, o Qwen das variáveis de ambiente, ou este
     * provedor indisponível se nada estiver configurado.
     */
    public static ProvedorIA doAmbiente(Map<String, String> ambiente) {
        return doAmbiente(ambiente, null);
    }

    /**
     * Como {@link #doAmbiente(Map)}, mas, sem nenhuma variável do Qwen, procura
     * o {@code qwen2.5:3b} no Ollama local ({@link DeteccaoOllama}). Variáveis
     * definidas pelo usuário nunca são substituídas.
     */
    public static ProvedorIA doAmbienteComOllama(Map<String, String> ambiente) {
        return doAmbiente(ambiente, DeteccaoOllama.local());
    }

    /** @param ollama consulta ao Ollama local ({@code null}: sem detecção) */
    public static ProvedorIA doAmbiente(Map<String, String> ambiente, DeteccaoOllama.Consulta ollama) {

        if ("simulado".equalsIgnoreCase(ambiente.getOrDefault("CHAT_SAKURA_IA", "").strip())) {
            return new ProvedorIAFalso();
        }

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
