package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;

import java.util.Objects;

/**
 * Conversa vista por um participante: última mensagem (ou {@code null}) e
 * quantidade de mensagens de outros ainda não lidas.
 */
public record ResumoConversa(Conversa conversa, Mensagem ultima, int naoLidas) {

    public ResumoConversa {
        Objects.requireNonNull(conversa);
    }
}
