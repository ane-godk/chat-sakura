
package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

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

        // Cria ou migra o esquema (MigradorBanco). Em caso de falha lança
        // MigracaoBancoException e o servidor não inicia.
        new MigradorBanco(connectionFactory).migrar();
    }

    private static TipoMensagem lerTipo(String valor) {
        try {
            return valor == null
                    ? TipoMensagem.MENSAGEM
                    : TipoMensagem.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return TipoMensagem.MENSAGEM;
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
                    conversa_id,
                    remetente,
                    texto,
                    horario,
                    tipo,
                    remetente_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
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

            comando.setLong(
                    1,
                    mensagem.getConversaId()
            );

            comando.setString(
                    2,
                    mensagem.getRemetente().getNome()
            );

            comando.setString(
                    3,
                    mensagem.getConteudo()
            );

            comando.setString(
                    4,
                    mensagem.getHorario().toString()
            );

            comando.setString(
                    5,
                    mensagem.getTipo().name()
            );

            // Conta autenticada vinculada pelo servidor; NULL para
            // visitantes, clientes legados e mensagens históricas.
            Long remetenteId = mensagem.getRemetente().getId();

            if (remetenteId == null) {
                comando.setNull(6, Types.INTEGER);
            } else {
                comando.setLong(6, remetenteId);
            }

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

    /** Somente o Chat Geral: mensagens de outras conversas nunca entram nele. */
    @Override
    public List<Mensagem> buscarUltimas(int quantidade) {
        return buscarHistorico(MigradorBanco.ID_CONVERSA_GERAL, null, quantidade);
    }

    @Override
    public List<Mensagem> buscarHistorico(long conversaId, Long antesDeId, int quantidade) {

        if (quantidade < 0) {
            throw new IllegalArgumentException(
                    "A quantidade não pode ser negativa."
            );
        }

        if (quantidade == 0) {
            return List.of();
        }

        String sql = """
                SELECT id, remetente_id, remetente, texto, horario, tipo
                FROM mensagem
                WHERE conversa_id = ? AND id < ?
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

            comando.setLong(1, conversaId);
            comando.setLong(2, antesDeId == null ? Long.MAX_VALUE : antesDeId);
            comando.setInt(3, quantidade);

            try (
                    ResultSet resultado =
                            comando.executeQuery()
            ) {

                while (resultado.next()) {
                    mensagens.add(lerMensagem(resultado, conversaId));
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

    static Mensagem lerMensagem(ResultSet resultado, long conversaId) throws SQLException {

        long remetenteId = resultado.getLong("remetente_id");

        Usuario remetente = new Usuario(
                resultado.wasNull() ? null : remetenteId,
                resultado.getString("remetente")
        );

        return new Mensagem(
                resultado.getLong("id"),
                remetente,
                resultado.getString("texto"),
                LocalDateTime.parse(resultado.getString("horario")),
                lerTipo(resultado.getString("tipo")),
                conversaId
        );
    }
}
