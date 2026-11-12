package br.unicesumar.chat.persistence.jdbc;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Conexões SQLite do Chat Sakura.
 *
 * Toda conexão aberta aqui usa:
 * <ul>
 *   <li>{@code PRAGMA foreign_keys = ON} (no SQLite vale por conexão);</li>
 *   <li>{@code busy_timeout} de {@value #ESPERA_BLOQUEIO_MS} ms: em vez de
 *       falhar com SQLITE_BUSY na hora, espera o outro escritor terminar;</li>
 *   <li>transações {@code BEGIN IMMEDIATE}: quem vai escrever reserva o
 *       bloqueio de escrita no início, evitando o SQLITE_BUSY sem espera que
 *       ocorre quando duas transações tentam promover leitura para escrita.</li>
 * </ul>
 *
 * {@link #garantirEsquema()} migra o banco uma única vez por fábrica (os
 * repositórios chamam, mas só a primeira chamada trabalha) e ativa o modo
 * WAL, em que leitores não bloqueiam o escritor. O WAL cria os arquivos
 * auxiliares {@code -wal} e {@code -shm} ao lado do banco enquanto houver
 * conexões abertas (já ignorados no Git); o backup das migrações usa
 * {@code VACUUM INTO}, consistente também em WAL.
 */
public final class ConnectionFactory {

    static final int ESPERA_BLOQUEIO_MS = 5_000;

    private final String caminhoBanco;
    private final String url;
    private final Properties propriedades;

    private boolean esquemaGarantido;
    private int migracoesExecutadas;

    public ConnectionFactory(String caminhoBanco) {

        if (caminhoBanco == null || caminhoBanco.isBlank()) {
            throw new IllegalArgumentException(
                    "O caminho do banco é obrigatório."
            );
        }

        this.caminhoBanco = caminhoBanco;
        this.url = "jdbc:sqlite:" + caminhoBanco;

        SQLiteConfig configuracao = new SQLiteConfig();
        configuracao.enforceForeignKeys(true);
        configuracao.setBusyTimeout(ESPERA_BLOQUEIO_MS);
        configuracao.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        this.propriedades = configuracao.toProperties();
    }

    /** Caminho do arquivo do banco (usado no nome do backup de migração). */
    public String caminhoBanco() {
        return caminhoBanco;
    }

    public Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(url, propriedades);
    }

    /**
     * Migra (com backup e verificação de integridade) e ativa o WAL, uma
     * única vez para esta fábrica. Chamadas seguintes não fazem nada.
     *
     * @throws MigracaoBancoException se o banco não puder ser migrado; a
     *                                próxima chamada tenta de novo
     */
    public synchronized void garantirEsquema() {

        if (esquemaGarantido) {
            return;
        }

        migracoesExecutadas++;
        new MigradorBanco(this).migrar();
        ativarWal();
        esquemaGarantido = true;
    }

    /** Quantas vezes a migração foi executada por esta fábrica (diagnóstico e testes). */
    public synchronized int migracoesExecutadas() {
        return migracoesExecutadas;
    }

    private void ativarWal() {
        try (Connection conexao = abrirConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery("PRAGMA journal_mode = WAL")) {
            String modo = resultado.next() ? resultado.getString(1) : "?";
            if (!"wal".equalsIgnoreCase(modo)) {
                System.err.println("Chat Sakura: modo WAL indisponível; usando " + modo + ".");
            }
        } catch (SQLException e) {
            // O banco funciona no modo de diário padrão; apenas sem WAL.
            System.err.println("Chat Sakura: não foi possível ativar o WAL: " + e.getMessage());
        }
    }
}
