package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.MemoriaPersonagem;
import br.unicesumar.chat.persistence.MemoriaRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/** Memória dos personagens na tabela {@code memoria_personagem} (esquema v5). */
public final class JdbcMemoriaRepository implements MemoriaRepository {

    private final ConnectionFactory connectionFactory;

    public JdbcMemoriaRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "A fábrica de conexões é obrigatória.");
        connectionFactory.garantirEsquema();
    }

    @Override
    public Optional<MemoriaPersonagem> buscar(long conversaId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT conversa_id, dono_id, personagem_id, resumo, ate_mensagem_id, esquecer_ate_id,"
                             + " atualizada_em FROM memoria_personagem WHERE conversa_id = ?")) {

            comando.setLong(1, conversaId);

            try (ResultSet r = comando.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MemoriaPersonagem(
                        r.getLong("conversa_id"), r.getLong("dono_id"), r.getLong("personagem_id"),
                        r.getString("resumo"), r.getLong("ate_mensagem_id"), r.getLong("esquecer_ate_id"),
                        LocalDateTime.parse(r.getString("atualizada_em"))));
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar a memória do personagem.", e);
        }
    }

    @Override
    public boolean salvarResumo(long conversaId, long donoId, long personagemId, String resumo,
                                long ateMensagemId, long esquecerAteEsperado, LocalDateTime quando) {

        // Novo registro, ou atualização somente se a memória não foi apagada nesse meio-tempo.
        String sql = """
                INSERT INTO memoria_personagem
                    (conversa_id, dono_id, personagem_id, resumo, ate_mensagem_id, esquecer_ate_id, atualizada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (conversa_id) DO UPDATE SET
                    resumo = excluded.resumo,
                    ate_mensagem_id = excluded.ate_mensagem_id,
                    atualizada_em = excluded.atualizada_em
                WHERE memoria_personagem.esquecer_ate_id = excluded.esquecer_ate_id
                  AND memoria_personagem.dono_id = excluded.dono_id
                  AND memoria_personagem.personagem_id = excluded.personagem_id
                """;

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setLong(1, conversaId);
            comando.setLong(2, donoId);
            comando.setLong(3, personagemId);
            comando.setString(4, resumo);
            comando.setLong(5, ateMensagemId);
            comando.setLong(6, esquecerAteEsperado);
            comando.setString(7, quando.toString());
            return comando.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao gravar a memória do personagem.", e);
        }
    }

    @Override
    public void apagar(long conversaId, long donoId, long personagemId, long ultimaMensagemId,
                       LocalDateTime quando) {

        String sql = """
                INSERT INTO memoria_personagem
                    (conversa_id, dono_id, personagem_id, resumo, ate_mensagem_id, esquecer_ate_id, atualizada_em)
                VALUES (?, ?, ?, '', ?, ?, ?)
                ON CONFLICT (conversa_id) DO UPDATE SET
                    resumo = '',
                    ate_mensagem_id = excluded.ate_mensagem_id,
                    esquecer_ate_id = excluded.esquecer_ate_id,
                    atualizada_em = excluded.atualizada_em
                WHERE memoria_personagem.dono_id = excluded.dono_id
                  AND memoria_personagem.personagem_id = excluded.personagem_id
                """;

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setLong(1, conversaId);
            comando.setLong(2, donoId);
            comando.setLong(3, personagemId);
            comando.setLong(4, ultimaMensagemId);
            comando.setLong(5, ultimaMensagemId);
            comando.setString(6, quando.toString());
            comando.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao apagar a memória do personagem.", e);
        }
    }
}
