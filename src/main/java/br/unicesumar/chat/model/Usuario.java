
package br.unicesumar.chat.model;

import java.util.Objects;

public final class Usuario {

    public static final int TAMANHO_MAXIMO_NOME = 30;

    private final Long id;
    private final String nome;
    private final String chave;

    /** Participante sem conta (cliente legado ou visitante). */
    public Usuario(String nome) {
        this(null, nome);
    }

    /**
     * @param id identificador da conta autenticada, atribuído pelo
     *           servidor; {@code null} para participantes sem conta
     */
    public Usuario(Long id, String nome) {

        if (id != null && id <= 0) {
            throw new IllegalArgumentException(
                    "O identificador deve ser positivo."
            );
        }

        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do usuário é obrigatório."
            );
        }

        String nomeTratado = nome.trim();

        if (nomeTratado.length() > TAMANHO_MAXIMO_NOME) {
            throw new IllegalArgumentException(
                    "O nome deve ter no máximo 30 caracteres."
            );
        }

        if (nomeTratado.matches(".*\\s+.*")) {
            throw new IllegalArgumentException(
                    "O nome não pode conter espaços."
            );
        }

        this.id = id;
        this.nome = nomeTratado;
        this.chave = NormalizacaoNome.chave(nomeTratado);
    }

    public String getNome() {
        return nome;
    }

    /** Conta vinculada, ou {@code null} para participantes sem conta. */
    public Long getId() {
        return id;
    }

    public boolean isAutenticado() {
        return id != null;
    }

    /*
     * A igualdade continua pelo nome, agora pela chave normalizada (NFKC +
     * minúsculas, ver NormalizacaoNome): é a regra que impede dois
     * participantes ativos com nomes equivalentes, tenham ou não conta.
     */

    @Override
    public boolean equals(Object objeto) {

        if (this == objeto) {
            return true;
        }

        if (!(objeto instanceof Usuario outro)) {
            return false;
        }

        return chave.equals(outro.chave);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chave);
    }

    /** Chave normalizada do nome (comparação, nunca exibida). */
    public String getChave() {
        return chave;
    }

    @Override
    public String toString() {
        return nome;
    }
}
