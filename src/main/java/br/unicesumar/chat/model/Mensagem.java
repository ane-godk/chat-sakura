
package br.unicesumar.chat.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class Mensagem {

    public static final int TAMANHO_MAXIMO_CONTEUDO = 500;

    /** Conteúdo registrado para os avisos de presença. */
    public static final String TEXTO_ENTRADA = "se conectou";
    public static final String TEXTO_SAIDA = "se desconectou";

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final Long id;
    private final Usuario remetente;
    private final String conteudo;
    private final LocalDateTime horario;
    private final TipoMensagem tipo;

    public Mensagem(
            Usuario remetente,
            String conteudo,
            LocalDateTime horario
    ) {
        this(null, remetente, conteudo, horario, TipoMensagem.MENSAGEM);
    }

    public Mensagem(
            Long id,
            Usuario remetente,
            String conteudo,
            LocalDateTime horario
    ) {
        this(id, remetente, conteudo, horario, TipoMensagem.MENSAGEM);
    }

    public Mensagem(
            Long id,
            Usuario remetente,
            String conteudo,
            LocalDateTime horario,
            TipoMensagem tipo
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

        if (tipo == null) {
            throw new MensagemInvalidaException(
                    "O tipo da mensagem é obrigatório."
            );
        }

        this.id = id;
        this.remetente = remetente;
        this.conteudo = conteudoTratado;
        this.horario = horario;
        this.tipo = tipo;
    }

    /** Aviso de que o participante se conectou. */
    public static Mensagem entrada(Usuario participante, LocalDateTime horario) {
        return new Mensagem(
                null, participante, TEXTO_ENTRADA, horario, TipoMensagem.ENTRADA
        );
    }

    /** Aviso de que o participante se desconectou. */
    public static Mensagem saida(Usuario participante, LocalDateTime horario) {
        return new Mensagem(
                null, participante, TEXTO_SAIDA, horario, TipoMensagem.SAIDA
        );
    }

    public Mensagem comId(Long novoId) {
        return new Mensagem(
                novoId,
                remetente,
                conteudo,
                horario,
                tipo
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

    public TipoMensagem getTipo() {
        return tipo;
    }

    public boolean isAvisoDePresenca() {
        return tipo != TipoMensagem.MENSAGEM;
    }

    /**
     * Linha enviada pelo protocolo.
     *
     * Mensagens: {@code [dd/MM HH:mm] Nome: conteúdo}.
     * Avisos: {@code SISTEMA: [dd/MM HH:mm] Nome se conectou}. Como toda
     * linha de usuário começa com o horário, um participante não consegue
     * produzir uma linha iniciada por "SISTEMA: ".
     */
    public String formatar() {

        String quando = "[" + horario.format(FORMATO) + "]";

        if (isAvisoDePresenca()) {
            return "SISTEMA: " + quando + " "
                    + remetente.getNome() + " " + conteudo;
        }

        return quando + " " + remetente.getNome() + ": " + conteudo;
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
                && horario.equals(outra.horario)
                && tipo == outra.tipo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                remetente,
                conteudo,
                horario,
                tipo
        );
    }
}
