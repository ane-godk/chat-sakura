package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Persistência dos avisos de presença e migração de bancos antigos (SQLite temporário). */
class JdbcAvisoPresencaTest {

    @TempDir
    Path pasta;

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 11, 4, 20, 34);

    @Test
    void deveGravarERecuperarOTipoNaOrdemCorreta() {

        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(
                new ConnectionFactory(pasta.resolve("tipos.db").toString()));

        Usuario ana = new Usuario("Ana");

        repositorio.salvar(Mensagem.entrada(ana, HORARIO));
        repositorio.salvar(new Mensagem(ana, "Olá!", HORARIO.plusMinutes(1)));
        repositorio.salvar(Mensagem.saida(ana, HORARIO.plusMinutes(4)));

        List<Mensagem> historico = repositorio.buscarUltimas(10);

        assertEquals(
                List.of(TipoMensagem.ENTRADA, TipoMensagem.MENSAGEM, TipoMensagem.SAIDA),
                historico.stream().map(Mensagem::getTipo).toList()
        );
        assertEquals("SISTEMA: [04/11 20:38] Ana se desconectou", historico.get(2).formatar());
        assertTrue(historico.get(0).getId() < historico.get(2).getId());
    }

    @Test
    void deveMigrarBancoComEsquemaAntigoPreservandoAsMensagens() throws Exception {

        Path arquivo = pasta.resolve("esquema-antigo.db");
        String url = "jdbc:sqlite:" + arquivo;

        // Banco no formato anterior à implementação, com mensagens reais.
        try (Connection conexao = DriverManager.getConnection(url);
             Statement comando = conexao.createStatement()) {

            comando.executeUpdate("""
                    CREATE TABLE mensagem (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        remetente TEXT NOT NULL,
                        texto TEXT NOT NULL,
                        horario TEXT NOT NULL
                    )
                    """);
            comando.executeUpdate("INSERT INTO mensagem (remetente, texto, horario) "
                    + "VALUES ('Bia', 'Mensagem antiga com ação 🌸', '2026-11-03T15:00')");
            comando.executeUpdate("INSERT INTO mensagem (remetente, texto, horario) "
                    + "VALUES ('Caio', 'Outra mensagem antiga', '2026-11-03T15:05')");
        }

        JdbcMensagemRepository repositorio =
                new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString()));

        // Coluna criada; registros antigos intactos e sem eventos inventados.
        List<Mensagem> antigas = repositorio.buscarUltimas(10);

        assertEquals(2, antigas.size());
        assertEquals("Mensagem antiga com ação 🌸", antigas.get(0).getConteudo());
        assertEquals("Bia", antigas.get(0).getRemetente().getNome());
        assertEquals(1L, antigas.get(0).getId());
        assertTrue(antigas.stream().allMatch(m -> m.getTipo() == TipoMensagem.MENSAGEM));

        try (Connection conexao = DriverManager.getConnection(url);
             Statement comando = conexao.createStatement();
             ResultSet colunas = comando.executeQuery("PRAGMA table_info(mensagem)")) {

            boolean possuiTipo = false;
            while (colunas.next()) {
                possuiTipo |= "tipo".equals(colunas.getString("name"));
            }
            assertTrue(possuiTipo, "A coluna tipo deve existir após a migração.");
        }

        // Novos avisos passam a ser gravados após os antigos.
        Mensagem entrada = repositorio.salvar(Mensagem.entrada(new Usuario("Ana"), HORARIO));
        assertEquals(3L, entrada.getId());

        // Reabrir não repete a migração nem altera os dados.
        JdbcMensagemRepository reaberto =
                new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString()));

        List<Mensagem> todas = reaberto.buscarUltimas(10);
        assertEquals(3, todas.size());
        assertEquals(TipoMensagem.ENTRADA, todas.get(2).getTipo());
        assertEquals(antigas, todas.subList(0, 2));
    }
}
