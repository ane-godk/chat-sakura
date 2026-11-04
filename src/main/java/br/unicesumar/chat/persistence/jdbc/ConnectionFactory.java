
package br.unicesumar.chat.persistence.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionFactory {

    private final String url;

    public ConnectionFactory(String caminhoBanco) {

        if (caminhoBanco == null || caminhoBanco.isBlank()) {
            throw new IllegalArgumentException(
                    "O caminho do banco é obrigatório."
            );
        }

        this.url = "jdbc:sqlite:" + caminhoBanco;
    }

    public Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
