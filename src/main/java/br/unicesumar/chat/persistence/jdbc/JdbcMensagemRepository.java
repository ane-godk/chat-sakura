
package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class JdbcMensagemRepository
        implements MensagemRepository {

    private final ConnectionFactory connectionFactory;

    public JdbcMensagemRepository(
            ConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "A fábrica de conexões é obrigatória."
        );

        inicializarBanco();
    }

    private void inicializarBanco() {

        String sql = """
                CREATE TABLE IF NOT EXISTS mensagem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    remetente TEXT NOT NULL,
                    texto TEXT NOT NULL,
                    horario TEXT NOT NULL
                )
                """;

        try (
                Connection conexao =
                        connectionFactory.abrirConexao();

                Statement comando =
                        conexao.createStatement()
        ) {

            comando.executeUpdate(sql);

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Erro ao inicializar o banco de dados.",
                    e
            );
        }
    }

    @Override
    public Mensagem salvar(Mensagem mensagem) {

        Objects.requireNonNull(
                mensagem,
                "A mensagem é obrigatória."
        );

        if (mensagem.getId() != null) {
            throw new IllegalArgumentException(
                    "Somente mensagens sem ID podem ser salvas."
            );
        }

        String sql = """
                INSERT INTO mensagem (
                    remetente,
                    texto,
                    horario
                )
                VALUES (?, ?, ?)
                """;

        try (
                Connection conexao =
                        connectionFactory.abrirConexao();

                PreparedStatement comando =
                        conexao.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {

            comando.setString(
                    1,
                    mensagem.getRemetente().getNome()
            );

            comando.setString(
                    2,
                    mensagem.getConteudo()
            );

            comando.setString(
                    3,
                    mensagem.getHorario().toString()
            );

            int linhasAfetadas = comando.executeUpdate();

            if (linhasAfetadas != 1) {
                throw new IllegalStateException(
                        "A mensagem não foi inserida."
                );
            }

            try (ResultSet chaves = comando.getGeneratedKeys()) {

                if (chaves.next()) {

                    long id = chaves.getLong(1);

                    return mensagem.comId(id);
                }
            }

            // Alternativa compatível com SQLite:
            // recupera o ID gerado na mesma conexão.
            try (
                    Statement consulta =
                            conexao.createStatement();

                    ResultSet resultado =
                            consulta.executeQuery(
                                    "SELECT last_insert_rowid()"
                            )
            ) {

                if (resultado.next()) {
                    return mensagem.comId(
                            resultado.getLong(1)
                    );
                }
            }

            throw new IllegalStateException(
                    "Não foi possível recuperar o ID gerado."
            );

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Erro ao salvar mensagem no SQLite.",
                    e
            );
        }
    }

    @Override
    public List<Mensagem> buscarUltimas(int quantidade) {

        if (quantidade < 0) {
            throw new IllegalArgumentException(
                    "A quantidade não pode ser negativa."
            );
        }

        if (quantidade == 0) {
            return List.of();
        }

        String sql = """
                SELECT id, remetente, texto, horario
                FROM mensagem
                ORDER BY id DESC
                LIMIT ?
                """;

        List<Mensagem> mensagens = new ArrayList<>();

        try (
                Connection conexao =
                        connectionFactory.abrirConexao();

                PreparedStatement comando =
                        conexao.prepareStatement(sql)
        ) {

            comando.setInt(1, quantidade);

            try (
                    ResultSet resultado =
                            comando.executeQuery()
            ) {

                while (resultado.next()) {

                    Long id = resultado.getLong("id");

                    Usuario remetente = new Usuario(
                            resultado.getString("remetente")
                    );

                    String conteudo =
                            resultado.getString("texto");

                    LocalDateTime horario =
                            LocalDateTime.parse(
                                    resultado.getString("horario")
                            );

                    mensagens.add(
                            new Mensagem(
                                    id,
                                    remetente,
                                    conteudo,
                                    horario
                            )
                    );
                }
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Erro ao consultar histórico no SQLite.",
                    e
            );
        }

        Collections.reverse(mensagens);

        return mensagens;
    }
}
