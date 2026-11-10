package br.unicesumar.chat.persistence.jdbc;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class ConnectionFactory {

    private final String caminhoBanco;
    private final String url;
    private final Properties propriedades;

    public ConnectionFactory(String caminhoBanco) {

        if (caminhoBanco == null || caminhoBanco.isBlank()) {
            throw new IllegalArgumentException(
                    "O caminho do banco é obrigatório."
            );
        }

        this.caminhoBanco = caminhoBanco;
        this.url = "jdbc:sqlite:" + caminhoBanco;

        // No SQLite as chaves estrangeiras valem por conexão: toda conexão
        // aberta por esta fábrica as aplica (PRAGMA foreign_keys = ON).
        SQLiteConfig configuracao = new SQLiteConfig();
        configuracao.enforceForeignKeys(true);
        this.propriedades = configuracao.toProperties();
    }

    /** Caminho do arquivo do banco (usado no nome do backup de migração). */
    public String caminhoBanco() {
        return caminhoBanco;
    }

    public Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(url, propriedades);
    }
}
