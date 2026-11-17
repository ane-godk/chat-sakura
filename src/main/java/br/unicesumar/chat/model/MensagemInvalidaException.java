
package br.unicesumar.chat.model;

public class MensagemInvalidaException
        extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}
