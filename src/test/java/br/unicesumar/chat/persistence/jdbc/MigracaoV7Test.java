package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.FiltroPesquisa;
import br.unicesumar.chat.model.TextoBusca;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Migração v7: forma de pesquisa das mensagens existentes e tabela de fixadas. */
class MigracaoV7Test {

    @TempDir
    Path pasta;

    private static List<String> coluna(Path arquivo, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo);
             Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            List<String> valores = new ArrayList<>();
            while (r.next()) {
                valores.add(r.getString(1));
            }
            return valores;
        }
    }

    @Test
    void bancoV6DeveIrParaV7PreenchendoAPesquisaSemAlterarOTexto() throws Exception {
        Path arquivo = pasta.resolve("v6.db");
        new MigradorBanco(new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES.subList(0, 6),
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"))).migrar();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo); Statement s = c.createStatement()) {
            s.execute("INSERT INTO mensagem (conversa_id, remetente, texto, horario) VALUES "
                    + "(1, 'Ana', 'Reunião às 18h no JARDIM', '2026-11-10T10:00'),"
                    + "(1, 'Bia', 'Olá 🌸', '2026-11-10T10:01')");
        }

        MigradorBanco.Resultado resultado = new MigradorBanco(new ConnectionFactory(arquivo.toString())).migrar();

        assertEquals(6, resultado.versaoAnterior());
        assertEquals(List.of("7"), coluna(arquivo, "PRAGMA user_version"));
        assertEquals(List.of("Reunião às 18h no JARDIM", "Olá 🌸"), coluna(arquivo, "SELECT texto FROM mensagem ORDER BY id"),
                "Texto original intacto");
        assertEquals(List.of("reuniao as 18h no jardim", "ola 🌸"),
                coluna(arquivo, "SELECT texto_busca FROM mensagem ORDER BY id"));
        assertEquals(List.of("mensagem_fixada"),
                coluna(arquivo, "SELECT name FROM sqlite_master WHERE name = 'mensagem_fixada'"));
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertTrue(arquivos.anyMatch(p -> p.getFileName().toString().contains(".backup-v6-")));
        }

        // As mensagens migradas já aparecem na pesquisa.
        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString()));
        assertEquals(1, repositorio.pesquisar(new FiltroPesquisa(1, TextoBusca.termo("reuniao"), null, null, null,
                null, 20)).size());
    }

    @Test
    void mensagemNovaGravaAFormaDePesquisa() throws Exception {
        Path arquivo = pasta.resolve("novo.db");
        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString()));
        repositorio.salvar(new br.unicesumar.chat.model.Mensagem(null, new br.unicesumar.chat.model.Usuario("Ana"),
                "AÇÃO já!", java.time.LocalDateTime.of(2026, 11, 13, 9, 0),
                br.unicesumar.chat.model.TipoMensagem.MENSAGEM, 1));
        assertEquals(List.of("acao ja!"), coluna(arquivo, "SELECT texto_busca FROM mensagem"));
        assertEquals(List.of("AÇÃO já!"), coluna(arquivo, "SELECT texto FROM mensagem"));
    }
}
