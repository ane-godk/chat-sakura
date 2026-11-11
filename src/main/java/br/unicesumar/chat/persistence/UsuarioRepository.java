package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.PerfilUsuario;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/** Contas de usuário persistentes. Falhas internas: {@link IllegalStateException}. */
public interface UsuarioRepository {

    /**
     * Cadastra uma conta com login e apelido já normalizados.
     *
     * @throws ContaDuplicadaException se o login ou o apelido já estiverem
     *                                 em uso (como login ou apelido de outra conta)
     */
    PerfilUsuario cadastrar(
            String login,
            String apelido,
            String senhaHash,
            String senhaSal,
            String avatarId,
            LocalDateTime criadoEm
    );

    Optional<ContaArmazenada> buscarPorLogin(String login);

    Optional<PerfilUsuario> buscarPorId(long id);

    /** {@code true} se o nome é login ou apelido de alguma conta. */
    boolean nomeEmUso(String nome);

    /** Grava o avatar (já validado) da conta; {@code false} se a conta não existe. */
    default boolean atualizarAvatar(long usuarioId, String avatarId) {
        throw new UnsupportedOperationException("Atualização de avatar não suportada.");
    }

    /** Perfis públicos das contas, por apelido (no máximo {@code limite}). */
    default java.util.List<PerfilUsuario> listar(int limite) {
        throw new UnsupportedOperationException("Listagem não suportada.");
    }

    /** Conta com as credenciais derivadas; uso exclusivo do servidor. */
    final class ContaArmazenada {

        private final PerfilUsuario perfil;
        private final String senhaHash;
        private final String senhaSal;

        public ContaArmazenada(PerfilUsuario perfil, String senhaHash, String senhaSal) {
            this.perfil = Objects.requireNonNull(perfil);
            this.senhaHash = Objects.requireNonNull(senhaHash);
            this.senhaSal = Objects.requireNonNull(senhaSal);
        }

        public PerfilUsuario perfil() {
            return perfil;
        }

        public String senhaHash() {
            return senhaHash;
        }

        public String senhaSal() {
            return senhaSal;
        }

        /** Nunca expõe hash nem sal. */
        @Override
        public String toString() {
            return "ContaArmazenada[" + perfil + "]";
        }
    }

    /** Login ou apelido já utilizados por outra conta. */
    final class ContaDuplicadaException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public enum Campo { LOGIN, APELIDO }

        private final Campo campo;

        public ContaDuplicadaException(Campo campo) {
            super(campo == Campo.LOGIN ? "Login em uso." : "Apelido em uso.");
            this.campo = campo;
        }

        public Campo getCampo() {
            return campo;
        }
    }
}
