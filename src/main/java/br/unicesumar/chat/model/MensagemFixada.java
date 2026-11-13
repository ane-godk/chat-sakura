package br.unicesumar.chat.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Mensagem fixada numa conversa. Fixar não copia nem altera a mensagem:
 * apenas a referencia.
 *
 * @param fixadaPor apelido de quem fixou, ou nulo se a conta não existe mais
 */
public record MensagemFixada(Mensagem mensagem, String fixadaPor, LocalDateTime fixadaEm) {

    /** Fixações por conversa. */
    public static final int LIMITE_POR_CONVERSA = 10;

    public MensagemFixada {
        Objects.requireNonNull(mensagem);
        Objects.requireNonNull(fixadaEm);
    }

    /** Resultado de um pedido para fixar. */
    public enum Resultado { FIXADA, JA_FIXADA, LIMITE, MENSAGEM_INEXISTENTE }
}
