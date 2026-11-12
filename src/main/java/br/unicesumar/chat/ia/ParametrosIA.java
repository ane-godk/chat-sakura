package br.unicesumar.chat.ia;

/**
 * Limites de uso do provedor, aplicados pelo servidor ao montar o contexto
 * e ao agendar gerações.
 *
 * @param janelaTokens        janela de contexto considerada no orçamento
 *                            (instruções + memória + histórico + resposta)
 * @param tokensResposta      limite de tokens da resposta
 * @param historicoMensagens  máximo de mensagens recentes enviadas
 * @param geracoesSimultaneas gerações ao mesmo tempo (Ollama local: 1)
 * @param filaMaxima          gerações aguardando vaga; além disso, recusa
 */
public record ParametrosIA(int janelaTokens, int tokensResposta, int historicoMensagens,
                           int geracoesSimultaneas, int filaMaxima) {

    /** API remota (padrão anterior: duas gerações em paralelo). */
    public static final ParametrosIA REMOTO = new ParametrosIA(16_384, 400, 20, 2, 8);

    /** Ollama local com 16 GB de RAM e GPU de 2 GB: uma geração por vez. */
    public static final ParametrosIA LOCAL = new ParametrosIA(4_096, 320, 16, 1, 4);

    /**
     * Janela mínima aceita: as partes fixas das instruções (identidade,
     * estilo e regras) custam cerca de 600 tokens; abaixo disso não sobraria
     * espaço útil para memória e histórico.
     */
    public static final int JANELA_MINIMA = 2_048;

    public ParametrosIA {
        if (janelaTokens < JANELA_MINIMA || tokensResposta < 32 || tokensResposta >= janelaTokens / 2
                || historicoMensagens < 1 || geracoesSimultaneas < 1 || filaMaxima < 1) {
            throw new IllegalArgumentException("Parâmetros de IA inválidos.");
        }
    }
}
