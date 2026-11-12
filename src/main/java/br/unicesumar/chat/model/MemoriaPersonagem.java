package br.unicesumar.chat.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Memória de um personagem numa conversa: um resumo curto, em notas, da
 * conversa entre o dono e aquele personagem. Pertence à conversa (e,
 * portanto, a uma única conta e a um único personagem); nunca é global.
 *
 * @param ateMensagemId  última mensagem já considerada no resumo
 * @param esquecerAteId  mensagens com id até este valor não são mais
 *                       enviadas ao modelo (memória apagada pelo usuário);
 *                       o histórico continua visível na conversa
 */
public record MemoriaPersonagem(long conversaId, long donoId, long personagemId, String resumo,
                                long ateMensagemId, long esquecerAteId, LocalDateTime atualizadaEm) {

    /** Tamanho máximo do resumo guardado (e enviado ao modelo). */
    public static final int RESUMO_MAXIMO = 1_200;

    public MemoriaPersonagem {
        Objects.requireNonNull(resumo, "Resumo obrigatório.");
        Objects.requireNonNull(atualizadaEm, "Data obrigatória.");
        if (conversaId <= 0 || donoId <= 0 || personagemId <= 0) {
            throw new IllegalArgumentException("Identificadores inválidos.");
        }
        if (resumo.length() > RESUMO_MAXIMO) {
            throw new IllegalArgumentException("Resumo longo demais.");
        }
    }

    /** Memória vazia de uma conversa ainda sem resumo. */
    public static MemoriaPersonagem vazia(long conversaId, long donoId, long personagemId) {
        return new MemoriaPersonagem(conversaId, donoId, personagemId, "", 0, 0, LocalDateTime.MIN);
    }

    public boolean vazio() {
        return resumo.isBlank();
    }
}
