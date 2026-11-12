package br.unicesumar.chat.service;

/**
 * A sessão de origem não está mais registrada (foi substituída por um novo
 * login da mesma conta ou já se desconectou): o pedido é recusado.
 */
public final class SessaoEncerradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessaoEncerradaException() {
        super("Sessão encerrada.", null, false, false);
    }
}
