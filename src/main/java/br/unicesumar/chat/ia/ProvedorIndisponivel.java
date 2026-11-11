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

    /**
     * Escolhe o provedor do servidor: {@code CHAT_SAKURA_IA=simulado} usa o
     * SIMULADO; caso contrário, o Qwen das variáveis de ambiente, ou este
     * provedor indisponível se nada estiver configurado.
     */
    public static ProvedorIA doAmbiente(Map<String, String> ambiente) {

        if ("simulado".equalsIgnoreCase(ambiente.getOrDefault("CHAT_SAKURA_IA", "").strip())) {
            return new ProvedorIAFalso();
        }

        try {
            return ConfiguracaoQwen.doAmbiente(ambiente)
                    .<ProvedorIA>map(ProvedorQwen::new)
                    .orElseGet(() -> new ProvedorIndisponivel(null));
        } catch (IllegalArgumentException e) {
            return new ProvedorIndisponivel("Configuração do Qwen inválida: " + e.getMessage());
        }
    }
}
