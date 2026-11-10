package br.unicesumar.chat.model;

import java.util.Objects;

/**
 * Dados públicos de uma conta autenticada. Não contém senha, hash nem sal:
 * é o único formato de conta devolvido ao cliente.
 */
public final class PerfilUsuario {

    private final long id;
    private final String login;
    private final String apelido;
    private final String avatarId;

    public PerfilUsuario(long id, String login, String apelido, String avatarId) {

        if (id <= 0) {
            throw new IllegalArgumentException("O identificador deve ser positivo.");
        }

        this.id = id;
        this.login = RegrasConta.normalizarLogin(login);
        this.apelido = RegrasConta.normalizarApelido(apelido);
        this.avatarId = RegrasConta.validarAvatar(avatarId);
    }

    public long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getApelido() {
        return apelido;
    }

    public String getAvatarId() {
        return avatarId;
    }

    /** Participante do chat vinculado a esta conta. */
    public Usuario comoUsuario() {
        return new Usuario(id, apelido);
    }

    @Override
    public boolean equals(Object objeto) {
        return objeto instanceof PerfilUsuario outro
                && id == outro.id
                && login.equals(outro.login)
                && apelido.equals(outro.apelido)
                && avatarId.equals(outro.avatarId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, login, apelido, avatarId);
    }

    @Override
    public String toString() {
        return apelido + " (#" + id + ")";
    }
}
