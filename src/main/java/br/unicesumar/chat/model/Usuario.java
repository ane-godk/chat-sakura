
package br.unicesumar.chat.model;

import java.util.Locale;
import java.util.Objects;

public final class Usuario {

    public static final int TAMANHO_MAXIMO_NOME = 30;

    private final String nome;

    public Usuario(String nome) {

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

        this.nome = nomeTratado;
    }

    public String getNome() {
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

        return nome.equalsIgnoreCase(outro.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                nome.toLowerCase(Locale.ROOT)
        );
    }

    @Override
    public String toString() {
        return nome;
    }
}
