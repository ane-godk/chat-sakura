
package br.unicesumar.chat.domain;

import java.util.Objects;

public final class Usuario {

    private final String nome;

    public Usuario(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(
                "O nome do usuário não pode ser vazio."
            );
        }

        this.nome = nome.trim();
    }

    public String getNome() {
        return nome;
    }

    @Override
    public String toString() {
        return nome;
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }

        if (!(objeto instanceof Usuario outro)) {
            return false;
        }

        return nome.equals(outro.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nome);
    }
}
