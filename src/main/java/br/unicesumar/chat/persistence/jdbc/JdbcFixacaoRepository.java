package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.MensagemFixada;
import br.unicesumar.chat.persistence.FixacaoRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tabela {@code mensagem_fixada} (esquema v7).
 *
 * Fixar roda numa transação {@code BEGIN IMMEDIATE} (configurada no
 * {@link ConnectionFactory}): quem fixa reserva a escrita antes de contar
 * as fixações, então dois pedidos simultâneos nunca passam do limite, nem
 * em processos diferentes.
 */
public final class JdbcFixacaoRepository implements FixacaoRepository {

    private final ConnectionFactory connectionFactory;

    public JdbcFixacaoRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "A fábrica de conexões é obrigatória.");
        connectionFactory.garantirEsquema();
    }

    @Override
    public MensagemFixada.Resultado fixar(long conversaId, long mensagemId, long usuarioId, LocalDateTime quando,
                                          int limite) {

        try (Connection conexao = connectionFactory.abrirConexao()) {
            conexao.setAutoCommit(false);
            try {
                MensagemFixada.Resultado resultado = fixarNaTransacao(conexao, conversaId, mensagemId, usuarioId,
                        quando, limite);
                conexao.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao fixar a mensagem.", e);
        }
    }

    private static MensagemFixada.Resultado fixarNaTransacao(Connection conexao, long conversaId, long mensagemId,
                                                             long usuarioId, LocalDateTime quando, int limite)
            throws SQLException {

        try (PreparedStatement existe = conexao.prepareStatement(
                "SELECT 1 FROM mensagem WHERE id = ? AND conversa_id = ? AND tipo IN ('MENSAGEM', 'IA')")) {
            existe.setLong(1, mensagemId);
            existe.setLong(2, conversaId);
            try (ResultSet r = existe.executeQuery()) {
                if (!r.next()) {
                    return MensagemFixada.Resultado.MENSAGEM_INEXISTENTE;
                }
            }
        }

        try (PreparedStatement ja = conexao.prepareStatement(
                "SELECT 1 FROM mensagem_fixada WHERE conversa_id = ? AND mensagem_id = ?")) {
            ja.setLong(1, conversaId);
            ja.setLong(2, mensagemId);
            try (ResultSet r = ja.executeQuery()) {
                if (r.next()) {
                    return MensagemFixada.Resultado.JA_FIXADA;
                }
            }
        }

        try (PreparedStatement contar = conexao.prepareStatement(
                "SELECT count(*) FROM mensagem_fixada WHERE conversa_id = ?")) {
            contar.setLong(1, conversaId);
            try (ResultSet r = contar.executeQuery()) {
                r.next();
                if (r.getInt(1) >= limite) {
                    return MensagemFixada.Resultado.LIMITE;
                }
            }
        }

        try (PreparedStatement inserir = conexao.prepareStatement(
                "INSERT INTO mensagem_fixada (conversa_id, mensagem_id, fixada_por, fixada_em) VALUES (?, ?, ?, ?)")) {
            inserir.setLong(1, conversaId);
            inserir.setLong(2, mensagemId);
            inserir.setLong(3, usuarioId);
            inserir.setString(4, quando.toString());
            inserir.executeUpdate();
        }
        return MensagemFixada.Resultado.FIXADA;
    }

    @Override
    public boolean desafixar(long conversaId, long mensagemId) {
        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "DELETE FROM mensagem_fixada WHERE conversa_id = ? AND mensagem_id = ?")) {
            comando.setLong(1, conversaId);
            comando.setLong(2, mensagemId);
            return comando.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao desafixar a mensagem.", e);
        }
    }

    @Override
    public List<MensagemFixada> listar(long conversaId) {
        String sql = """
                SELECT m.id, m.remetente_id, m.remetente, m.texto, m.horario, m.tipo,
                       u.apelido AS fixada_por_apelido, f.fixada_em
                FROM mensagem_fixada f
                JOIN mensagem m ON m.id = f.mensagem_id AND m.conversa_id = f.conversa_id
                LEFT JOIN usuario u ON u.id = f.fixada_por
                WHERE f.conversa_id = ?
                ORDER BY f.fixada_em, f.mensagem_id
                """;
        List<MensagemFixada> fixadas = new ArrayList<>();
        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, conversaId);
            try (ResultSet r = comando.executeQuery()) {
                while (r.next()) {
                    fixadas.add(new MensagemFixada(JdbcMensagemRepository.lerMensagem(r, conversaId),
                            r.getString("fixada_por_apelido"), LocalDateTime.parse(r.getString("fixada_em"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao listar as mensagens fixadas.", e);
        }
        return fixadas;
    }
}
