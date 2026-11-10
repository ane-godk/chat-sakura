package br.unicesumar.chat.model;

import java.util.List;
import java.util.Objects;

/**
 * Conversa do chat: Geral (aberta), privada (dois participantes) ou grupo.
 * Imutável. A Geral tem id estável {@link #ID_GERAL} e não lista participantes.
 */
public final class Conversa {

    public static final long ID_GERAL = 1;
    public static final int NOME_GRUPO_MAXIMO = 40;
    public static final int PARTICIPANTES_MAXIMOS = 50;

    public enum Tipo { GERAL, PRIVADA, GRUPO, IA }

    public enum Papel { ADMIN, MEMBRO }

    /** Participante com os dados públicos da conta. */
    public record Participante(long id, String apelido, String avatarId, Papel papel) {
        public Participante {
            Objects.requireNonNull(apelido);
            Objects.requireNonNull(avatarId);
            Objects.requireNonNull(papel);
        }
    }

    private final long id;
    private final Tipo tipo;
    private final String nome;
    private final List<Participante> participantes;

    public Conversa(long id, Tipo tipo, String nome, List<Participante> participantes) {
        if (id <= 0) {
            throw new IllegalArgumentException("O identificador deve ser positivo.");
        }
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo);
        this.nome = nome;
        this.participantes = List.copyOf(participantes);
    }

    public long getId() {
        return id;
    }

    public Tipo getTipo() {
        return tipo;
    }

    /** Nome do grupo ou da Geral; {@code null} em conversas privadas. */
    public String getNome() {
        return nome;
    }

    public List<Participante> getParticipantes() {
        return participantes;
    }

    public boolean participa(long usuarioId) {
        return tipo == Tipo.GERAL
                || participantes.stream().anyMatch(p -> p.id() == usuarioId);
    }

    /** Valida e normaliza o nome de um grupo. */
    public static String normalizarNomeGrupo(String nome) {
        String tratado = nome == null ? "" : nome.strip();
        if (tratado.isEmpty() || tratado.length() > NOME_GRUPO_MAXIMO
                || tratado.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "O nome do grupo deve ter de 1 a " + NOME_GRUPO_MAXIMO + " caracteres.");
        }
        return tratado;
    }

    @Override
    public boolean equals(Object objeto) {
        return objeto instanceof Conversa outra
                && id == outra.id && tipo == outra.tipo
                && Objects.equals(nome, outra.nome)
                && participantes.equals(outra.participantes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tipo, nome, participantes);
    }
}
