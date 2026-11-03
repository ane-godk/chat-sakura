
package br.unicesumar.chat.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class Mensagem {

    private static final DateTimeFormatter FORMATO =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String remetente;
    private final String texto;
    private final LocalDateTime horario;

    public Mensagem(
            String remetente,
            String texto,
            LocalDateTime horario
    ) {
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalArgumentException(
                "O remetente não pode ser vazio."
            );
        }

        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(
                "O texto da mensagem não pode ser vazio."
            );
        }

        if (horario == null) {
            throw new IllegalArgumentException(
                "O horário não pode ser nulo."
            );
        }

        this.remetente = remetente.trim();
        this.texto = texto.trim();
        this.horario = horario;
    }

    public Mensagem(String remetente, String texto) {
        this(remetente, texto, LocalDateTime.now());
    }

    public String getRemetente() {
        return remetente;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    @Override
    public String toString() {
        return "[" + horario.format(FORMATO) + "] "
                + remetente + ": " + texto;
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }

        if (!(objeto instanceof Mensagem outra)) {
            return false;
        }

        return remetente.equals(outra.remetente)
                && texto.equals(outra.texto)
                && horario.equals(outra.horario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remetente, texto, horario);
    }
}
