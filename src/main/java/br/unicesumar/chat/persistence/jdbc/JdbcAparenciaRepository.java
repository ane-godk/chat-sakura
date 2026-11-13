package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.persistence.AparenciaRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/** Tabela {@code preferencia_aparencia} (esquema v6). */
public final class JdbcAparenciaRepository implements AparenciaRepository {

    private final ConnectionFactory connectionFactory;

    public JdbcAparenciaRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "A fábrica de conexões é obrigatória.");
        connectionFactory.garantirEsquema();
    }

    @Override
    public Optional<Aparencia> buscar(long usuarioId) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT paleta, modo, fundo FROM preferencia_aparencia WHERE usuario_id = ?")) {

            comando.setLong(1, usuarioId);

            try (ResultSet r = comando.executeQuery()) {
                return r.next()
                        ? Optional.of(Aparencia.ler(r.getString("paleta"), r.getString("modo"), r.getString("fundo")))
                        : Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar a aparência da conta.", e);
        }
    }

    @Override
    public void salvar(long usuarioId, Aparencia aparencia, LocalDateTime quando) {

        try (Connection conexao = connectionFactory.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement("""
                     INSERT INTO preferencia_aparencia (usuario_id, paleta, modo, fundo, atualizada_em)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT (usuario_id) DO UPDATE SET
                         paleta = excluded.paleta,
                         modo = excluded.modo,
                         fundo = excluded.fundo,
                         atualizada_em = excluded.atualizada_em
                     """)) {

            comando.setLong(1, usuarioId);
            comando.setString(2, aparencia.paleta().name());
            comando.setString(3, aparencia.modo().name());
            comando.setString(4, aparencia.fundo().name());
            comando.setString(5, quando.toString());
            comando.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao gravar a aparência da conta.", e);
        }
    }
}
