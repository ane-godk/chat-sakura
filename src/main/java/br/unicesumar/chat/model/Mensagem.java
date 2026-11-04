
package br.unicesumar.chat.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class Mensagem {

    public static final int TAMANHO_MAXIMO_CONTEUDO = 500;

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final Long id;
    private final Usuario remetente;
    private final String conteudo;
    private final LocalDateTime horario;

    public Mensagem(
            Usuario remetente,
            String conteudo,
            LocalDateTime horario
    ) {
        this(null, remetente, conteudo, horario);
    }

    public Mensagem(
            Long id,
            Usuario remetente,
            String conteudo,
            LocalDateTime horario
    ) {

        if (id != null && id <= 0) {
            throw new MensagemInvalidaException(
                    "O identificador deve ser positivo."
            );
        }

        if (remetente == null) {
            throw new MensagemInvalidaException(
                    "O remetente é obrigatório."
            );
        }

        if (conteudo == null || conteudo.isBlank()) {
            throw new MensagemInvalidaException(
                    "O conteúdo da mensagem é obrigatório."
            );
        }

        String conteudoTratado = conteudo.trim();

        if (conteudoTratado.length() > TAMANHO_MAXIMO_CONTEUDO) {
            throw new MensagemInvalidaException(
                    "A mensagem deve ter no máximo 500 caracteres."
            );
        }

        if (horario == null) {
            throw new MensagemInvalidaException(
                    "O horário é obrigatório."
            );
        }

        this.id = id;
        this.remetente = remetente;
        this.conteudo = conteudoTratado;
        this.horario = horario;
    }

    public Mensagem comId(Long novoId) {
        return new Mensagem(
                novoId,
                remetente,
                conteudo,
                horario
        );
    }

    public Long getId() {
        return id;
    }

    public Usuario getRemetente() {
        return remetente;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    public String formatar() {
        return "[" + horario.format(FORMATO) + "] "
                + remetente.getNome()
                + ": "
                + conteudo;
    }

    @Override
    public String toString() {
        return formatar();
    }

    @Override
    public boolean equals(Object objeto) {

        if (this == objeto) {
            return true;
        }

        if (!(objeto instanceof Mensagem outra)) {
            return false;
        }

        return Objects.equals(id, outra.id)
                && remetente.equals(outra.remetente)
                && conteudo.equals(outra.conteudo)
                && horario.equals(outra.horario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                remetente,
                conteudo,
                horario
        );
    }
}
