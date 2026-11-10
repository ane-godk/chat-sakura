package br.unicesumar.chat.service;

import java.util.Objects;

/**
 * Recusa de cadastro ou login. A mensagem é pública (pode ser exibida ao
 * usuário) e nunca contém senha, hash, sal ou detalhes internos.
 */
public final class AutenticacaoException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Codigo {
        DADOS_INVALIDOS,
        LOGIN_EM_USO,
        APELIDO_EM_USO,
        /** Login inexistente ou senha incorreta (indistinguíveis de propósito). */
        CREDENCIAIS_INVALIDAS,
        /** Falha interna; o detalhe fica apenas no log do servidor. */
        INDISPONIVEL
    }

    private final Codigo codigo;

    public AutenticacaoException(Codigo codigo, String mensagemPublica) {
        super(mensagemPublica, null, false, false);
        this.codigo = Objects.requireNonNull(codigo);
    }

    public Codigo getCodigo() {
        return codigo;
    }
}
