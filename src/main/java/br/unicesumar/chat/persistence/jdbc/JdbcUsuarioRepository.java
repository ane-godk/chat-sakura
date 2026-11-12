package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.NormalizacaoNome;
import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.persistence.UsuarioRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Contas na tabela {@code usuario} (esquema v2). Login e apelido são
 * comparados sem diferenciar maiúsculas ({@code COLLATE NOCASE}, que no
 * SQLite cobre as letras ASCII).
 *
 * Os erros lançados nunca incluem senha, hash ou sal.
 */
public final class JdbcUsuarioRepository implements UsuarioRepository {

    private static final String COLUNAS_PERFIL = "id, login, apelido, avatar_id";

    private final ConnectionFactory connectionFactory;

    public JdbcUsuarioRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "A fábrica de conexões é obrigatória.");

        // Garante o esquema v2 (idempotente se já estiver atualizado).
        connectionFactory.garantirEsquema();
    }

    /**
     * Verificação e inserção na mesma transação. O serviço de autenticação
     * serializa os cadastros; a restrição UNIQUE do login é a última defesa.
     */
    @Override
    public PerfilUsuario cadastrar(
            String login,
            String apelido,
            String senhaHash,
            String senhaSal,
            String avatarId,
            LocalDateTime criadoEm
    ) {

        try (Connection conexao = connectionFactory.abrirConexao()) {

            conexao.setAutoCommit(false);

            try {
                if (nomeEmUso(conexao, login)) {
                    throw new ContaDuplicadaException(ContaDuplicadaException.Campo.LOGIN);
                }

                if (nomeEmUso(conexao, apelido)) {
                    throw new ContaDuplicadaException(ContaDuplicadaException.Campo.APELIDO);
                }

                long id;

                try (PreparedStatement comando = conexao.prepareStatement("""
                        INSERT INTO usuario
                            (login, apelido, senha_hash, senha_sal, avatar_id, criado_em, apelido_chave)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """)) {

                    comando.setString(1, login);
                    comando.setString(2, apelido);
                    comando.setString(3, senhaHash);
                    comando.setString(4, senhaSal);
                    comando.setString(5, avatarId);
                    comando.setString(6, criadoEm.toString());
                    comando.setString(7, NormalizacaoNome.chave(apelido));

                    try (ResultSet chave = comando.executeQuery()) {
                        if (!chave.next()) {
                            throw new SQLException("Id da conta não retornado.");
                        }
                        id = chave.getLong(1);
                    }
                }

                conexao.commit();

                return new PerfilUsuario(id, login, apelido, avatarId);

            } catch (SQLException | RuntimeException e) {
                conexao.rollback();

                // Última defesa: índices únicos de login e de apelido_chave.
                if (e instanceof SQLException sql && sql.getMessage() != null
                        && sql.getMessage().contains("UNIQUE")) {
                    throw new ContaDuplicadaException(sql.getMessage().contains("apelido_chave")
                            ? ContaDuplicadaException.Campo.APELIDO
                            : ContaDuplicadaException.Campo.LOGIN);
                }

                throw e;

            } finally {
                conexao.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao cadastrar conta no SQLite.", e);
        }
    }

    @Override
    public Optional<ContaArmazenada> buscarPorLogin(String login) {

        String sql = "SELECT " + COLUNAS_PERFIL + ", senha_hash, senha_sal "
                + "FROM usuario WHERE login = ? COLLATE NOCASE";

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setString(1, login);

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ContaArmazenada(
                        lerPerfil(resultado),
                        resultado.getString("senha_hash"),
                        resultado.getString("senha_sal")
                ));
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar conta no SQLite.", e);
        }
    }

    @Override
    public Optional<PerfilUsuario> buscarPorId(long id) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT " + COLUNAS_PERFIL + " FROM usuario WHERE id = ?")) {

            comando.setLong(1, id);

            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next()
                        ? Optional.of(lerPerfil(resultado))
                        : Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar conta no SQLite.", e);
        }
    }

    @Override
    public boolean nomeEmUso(String nome) {

        try (Connection conexao = connectionFactory.abrirConexao()) {
            return nomeEmUso(conexao, nome);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar contas no SQLite.", e);
        }
    }

    @Override
    public boolean atualizarAvatar(long usuarioId, String avatarId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "UPDATE usuario SET avatar_id = ? WHERE id = ?")) {

            comando.setString(1, avatarId);
            comando.setLong(2, usuarioId);
            return comando.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao atualizar avatar no SQLite.", e);
        }
    }

    @Override
    public java.util.List<PerfilUsuario> listar(int limite) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT " + COLUNAS_PERFIL + " FROM usuario "
                             + "ORDER BY apelido COLLATE NOCASE LIMIT ?")) {

            comando.setInt(1, limite);
            java.util.List<PerfilUsuario> perfis = new java.util.ArrayList<>();

            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    perfis.add(lerPerfil(resultado));
                }
            }
            return perfis;

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao listar contas no SQLite.", e);
        }
    }

    /**
     * {@code true} se o nome equivale (chave NFKC + minúsculas) ao login ou ao
     * apelido de alguma conta. Logins são ASCII em minúsculas, então a chave
     * também os compara corretamente.
     */
    private static boolean nomeEmUso(Connection conexao, String nome) throws SQLException {

        String chave = NormalizacaoNome.chave(nome);

        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT 1 FROM usuario "
                        + "WHERE login = ? COLLATE NOCASE OR apelido_chave = ? "
                        + "LIMIT 1")) {

            comando.setString(1, chave);
            comando.setString(2, chave);

            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next();
            }
        }
    }

    private static PerfilUsuario lerPerfil(ResultSet resultado) throws SQLException {
        return new PerfilUsuario(
                resultado.getLong("id"),
                resultado.getString("login"),
                resultado.getString("apelido"),
                // Valor desconhecido (dado antigo) vira o avatar padrão.
                br.unicesumar.chat.model.CatalogoAvatares.normalizar(resultado.getString("avatar_id"))
        );
    }
}
