
package br.unicesumar.chat.model;

public class MensagemInvalidaException
        extends IllegalArgumentException {

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}
