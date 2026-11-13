package br.unicesumar.chat.service;

import java.util.Objects;

/** Recusa de uma operação de conversa; mensagem pública, sem detalhes internos. */
public final class ConversaException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Codigo {
        DADOS_INVALIDOS,
        /** Inexistente ou sem acesso: indistinguíveis para não revelar metadados. */
        NAO_ENCONTRADA,
        CONTA_NECESSARIA,
        /** Aguarde: resposta da IA em andamento ou limite de uso atingido. */
        LIMITE,
        INDISPONIVEL,
        /** Participante sem o papel exigido (por exemplo, fixar num grupo sem ser ADMIN). */
        SEM_PERMISSAO
    }

    private final Codigo codigo;

    public ConversaException(Codigo codigo, String mensagemPublica) {
        super(mensagemPublica, null, false, false);
        this.codigo = Objects.requireNonNull(codigo);
    }

    public Codigo getCodigo() {
        return codigo;
    }
}
