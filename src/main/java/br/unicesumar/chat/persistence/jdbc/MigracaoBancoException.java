package br.unicesumar.chat.persistence.jdbc;

/**
 * Falha ao verificar ou migrar o esquema do banco. O servidor não deve
 * iniciar enquanto ela não for resolvida.
 */
public final class MigracaoBancoException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public MigracaoBancoException(String mensagem) {
        super(mensagem);
    }

    public MigracaoBancoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
