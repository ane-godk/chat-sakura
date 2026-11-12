package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.persistence.ConversaRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Conversas, participantes e pares privados no SQLite (esquema v3). */
public final class JdbcConversaRepository implements ConversaRepository {

    private final ConnectionFactory connectionFactory;

    public JdbcConversaRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "A fábrica de conexões é obrigatória.");
        connectionFactory.garantirEsquema();
    }

    @Override
    public Optional<Long> buscarPrivada(long usuarioA, long usuarioB) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT conversa_id FROM conversa_privada "
                             + "WHERE usuario_menor_id = ? AND usuario_maior_id = ?")) {

            comando.setLong(1, Math.min(usuarioA, usuarioB));
            comando.setLong(2, Math.max(usuarioA, usuarioB));

            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(resultado.getLong(1)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar conversa privada.", e);
        }
    }

    @Override
    public long criarPrivada(long usuarioA, long usuarioB, LocalDateTime criadaEm) {

        return emTransacao(conexao -> {
            long id = inserirConversa(conexao, "PRIVADA", null, usuarioA, criadaEm);

            try (PreparedStatement par = conexao.prepareStatement(
                    "INSERT INTO conversa_privada VALUES (?, ?, ?)")) {
                par.setLong(1, id);
                par.setLong(2, Math.min(usuarioA, usuarioB));
                par.setLong(3, Math.max(usuarioA, usuarioB));
                par.executeUpdate();
            }

            inserirParticipante(conexao, id, usuarioA, Conversa.Papel.MEMBRO, criadaEm);
            inserirParticipante(conexao, id, usuarioB, Conversa.Papel.MEMBRO, criadaEm);
            return id;
        });
    }

    @Override
    public long criarGrupo(String nome, long criadorId, Set<Long> convidados, LocalDateTime criadaEm) {

        return emTransacao(conexao -> {
            long id = inserirConversa(conexao, "GRUPO", nome, criadorId, criadaEm);
            inserirParticipante(conexao, id, criadorId, Conversa.Papel.ADMIN, criadaEm);
            for (long convidado : convidados) {
                if (convidado != criadorId) {
                    inserirParticipante(conexao, id, convidado, Conversa.Papel.MEMBRO, criadaEm);
                }
            }
            return id;
        });
    }

    @Override
    public long criarConversaIA(long donoId, String nome, String personalidade, String contexto,
                                LocalDateTime criadaEm) {

        return emTransacao(conexao -> {
            long personagemId;

            try (PreparedStatement comando = conexao.prepareStatement(
                    "INSERT INTO personagem (dono_id, nome, personalidade, contexto, criado_em) "
                            + "VALUES (?, ?, ?, ?, ?) RETURNING id")) {
                comando.setLong(1, donoId);
                comando.setString(2, nome);
                comando.setString(3, personalidade);
                comando.setString(4, contexto);
                comando.setString(5, criadaEm.toString());
                try (ResultSet chave = comando.executeQuery()) {
                    chave.next();
                    personagemId = chave.getLong(1);
                }
            }

            long conversaId;

            try (PreparedStatement comando = conexao.prepareStatement(
                    "INSERT INTO conversa (tipo, nome, criador_id, personagem_id, criado_em) "
                            + "VALUES ('IA', ?, ?, ?, ?) RETURNING id")) {
                comando.setString(1, nome);
                comando.setLong(2, donoId);
                comando.setLong(3, personagemId);
                comando.setString(4, criadaEm.toString());
                try (ResultSet chave = comando.executeQuery()) {
                    chave.next();
                    conversaId = chave.getLong(1);
                }
            }

            inserirParticipante(conexao, conversaId, donoId, Conversa.Papel.ADMIN, criadaEm);
            return conversaId;
        });
    }

    @Override
    public Optional<br.unicesumar.chat.model.Personagem> personagemDaConversa(long conversaId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT p.id, p.dono_id, p.nome, p.personalidade, p.contexto FROM conversa c "
                             + "JOIN personagem p ON p.id = c.personagem_id "
                             + "WHERE c.id = ? AND c.tipo = 'IA'")) {

            comando.setLong(1, conversaId);

            try (ResultSet r = comando.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new br.unicesumar.chat.model.Personagem(
                        r.getLong("id"), r.getLong("dono_id"), r.getString("nome"),
                        r.getString("personalidade"), r.getString("contexto")));
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar personagem.", e);
        }
    }

    @Override
    public Optional<Conversa> buscar(long conversaId) {

        try (Connection conexao = connectionFactory.abrirConexao()) {
            return buscar(conexao, conversaId);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar conversa.", e);
        }
    }

    @Override
    public List<Conversa> conversasDoUsuario(long usuarioId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT c.id FROM conversa c "
                             + "JOIN conversa_participante p ON p.conversa_id = c.id "
                             + "WHERE p.usuario_id = ? AND c.tipo IN ('PRIVADA', 'GRUPO', 'IA') "
                             + "ORDER BY c.id")) {

            comando.setLong(1, usuarioId);
            List<Long> ids = new ArrayList<>();

            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    ids.add(resultado.getLong(1));
                }
            }

            List<Conversa> conversas = new ArrayList<>();
            for (long id : ids) {
                buscar(conexao, id).ifPresent(conversas::add);
            }
            return conversas;

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao listar conversas.", e);
        }
    }

    @Override
    public boolean participa(long conversaId, long usuarioId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT 1 FROM conversa_participante WHERE conversa_id = ? AND usuario_id = ?")) {

            comando.setLong(1, conversaId);
            comando.setLong(2, usuarioId);

            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao verificar participação.", e);
        }
    }

    @Override
    public Set<Long> participantes(long conversaId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT usuario_id FROM conversa_participante WHERE conversa_id = ?")) {

            comando.setLong(1, conversaId);
            Set<Long> ids = new LinkedHashSet<>();

            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    ids.add(resultado.getLong(1));
                }
            }
            return ids;

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar participantes.", e);
        }
    }

    @Override
    public void marcarLida(long conversaId, long usuarioId, long mensagemId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "UPDATE conversa_participante "
                             + "SET ultima_lida_id = max(ultima_lida_id, "
                             + "  (SELECT coalesce(max(id), 0) FROM mensagem "
                             + "   WHERE conversa_id = ? AND id <= ?)) "
                             + "WHERE conversa_id = ? AND usuario_id = ?")) {

            comando.setLong(1, conversaId);
            comando.setLong(2, mensagemId);
            comando.setLong(3, conversaId);
            comando.setLong(4, usuarioId);
            comando.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao marcar conversa como lida.", e);
        }
    }

    @Override
    public int naoLidas(long conversaId, long usuarioId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT count(*) FROM mensagem m "
                             + "JOIN conversa_participante p "
                             + "  ON p.conversa_id = m.conversa_id AND p.usuario_id = ? "
                             + "WHERE m.conversa_id = ? AND m.id > p.ultima_lida_id "
                             + "  AND (m.remetente_id IS NULL OR m.remetente_id <> ?)")) {

            comando.setLong(1, usuarioId);
            comando.setLong(2, conversaId);
            comando.setLong(3, usuarioId);

            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? resultado.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao contar mensagens não lidas.", e);
        }
    }

    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Operacao {
        long executar(Connection conexao) throws SQLException;
    }

    private long emTransacao(Operacao operacao) {

        try (Connection conexao = connectionFactory.abrirConexao()) {

            conexao.setAutoCommit(false);

            try {
                long resultado = operacao.executar(conexao);
                conexao.commit();
                return resultado;

            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                if (e instanceof SQLException sql && sql.getMessage() != null
                        && sql.getMessage().contains("UNIQUE")) {
                    throw new IllegalArgumentException("Conversa já existente.", e);
                }
                throw e;

            } finally {
                conexao.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao gravar conversa.", e);
        }
    }

    private static long inserirConversa(Connection conexao, String tipo, String nome,
                                        long criadorId, LocalDateTime criadaEm) throws SQLException {

        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO conversa (tipo, nome, criador_id, criado_em) VALUES (?, ?, ?, ?) RETURNING id")) {

            comando.setString(1, tipo);
            comando.setString(2, nome);
            comando.setLong(3, criadorId);
            comando.setString(4, criadaEm.toString());

            try (ResultSet chave = comando.executeQuery()) {
                if (!chave.next()) {
                    throw new SQLException("Id da conversa não retornado.");
                }
                return chave.getLong(1);
            }
        }
    }

    private static void inserirParticipante(Connection conexao, long conversaId, long usuarioId,
                                            Conversa.Papel papel, LocalDateTime entrada) throws SQLException {

        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO conversa_participante (conversa_id, usuario_id, papel, entrou_em) "
                        + "VALUES (?, ?, ?, ?)")) {

            comando.setLong(1, conversaId);
            comando.setLong(2, usuarioId);
            comando.setString(3, papel.name());
            comando.setString(4, entrada.toString());
            comando.executeUpdate();
        }
    }

    private static Optional<Conversa> buscar(Connection conexao, long conversaId) throws SQLException {

        Conversa.Tipo tipo;
        String nome;

        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT tipo, nome FROM conversa WHERE id = ?")) {
            comando.setLong(1, conversaId);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }
                tipo = Conversa.Tipo.valueOf(resultado.getString("tipo"));
                nome = resultado.getString("nome");
            }
        }

        List<Conversa.Participante> participantes = new ArrayList<>();

        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT u.id, u.apelido, u.avatar_id, p.papel FROM conversa_participante p "
                        + "JOIN usuario u ON u.id = p.usuario_id "
                        + "WHERE p.conversa_id = ? ORDER BY p.papel, u.apelido COLLATE NOCASE")) {
            comando.setLong(1, conversaId);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    participantes.add(new Conversa.Participante(
                            resultado.getLong("id"),
                            resultado.getString("apelido"),
                            resultado.getString("avatar_id"),
                            Conversa.Papel.valueOf(resultado.getString("papel"))
                    ));
                }
            }
        }

        return Optional.of(new Conversa(conversaId, tipo, nome, participantes));
    }
}
