package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.MemoriaPersonagem;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Tabela memoria_personagem (v5): migração, gravação, exclusão e cascata. */
class MemoriaRepositorioTest {

    @TempDir
    Path pasta;

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 12, 12, 0);

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
    void bancoV4DeveIrParaV5PreservandoDados() throws Exception {
        Path arquivo = pasta.resolve("v4.db");
        new MigradorBanco(new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES.subList(0, 4),
                Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("UTC"))).migrar();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo); Statement s = c.createStatement()) {
            s.execute("INSERT INTO usuario (id, login, apelido, apelido_chave, senha_hash, senha_sal, criado_em)"
                    + " VALUES (1, 'ana', 'Ana', 'ana', 'h', 's', '2026-11-10')");
            s.execute("INSERT INTO mensagem (conversa_id, remetente, texto, horario) VALUES (1, 'Ana', 'oi', '2026-11-10')");
        }

        MigradorBanco.Resultado resultado = new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES.subList(0, 5),
                Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("UTC"))).migrar();

        assertEquals(4, resultado.versaoAnterior());
        assertEquals(List.of("5"), coluna(arquivo, "PRAGMA user_version"));
        assertEquals(List.of("memoria_personagem"),
                coluna(arquivo, "SELECT name FROM sqlite_master WHERE name = 'memoria_personagem'"));
        assertEquals(List.of("oi"), coluna(arquivo, "SELECT texto FROM mensagem"));
        assertEquals(List.of("ana"), coluna(arquivo, "SELECT apelido_chave FROM usuario"));
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertTrue(arquivos.anyMatch(p -> p.getFileName().toString().contains(".backup-v4-")));
        }
    }

    @Test
    void memoriaDeveSerPorConversaPersistenteENaoRessuscitarDepoisDeApagada() throws Exception {
        Path arquivo = pasta.resolve("memoria.db");
        ConnectionFactory fabrica = new ConnectionFactory(arquivo.toString());
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(fabrica);
        JdbcConversaRepository conversas = new JdbcConversaRepository(fabrica);
        JdbcMemoriaRepository memorias = new JdbcMemoriaRepository(fabrica);

        long ana = usuarios.cadastrar("ana", "Ana", "h", "s", "sakura", AGORA).getId();
        long bia = usuarios.cadastrar("bia", "Bia", "h", "s", "sakura", AGORA).getId();
        long conversaAna = conversas.criarConversaIA(ana, "Naruto", "animado", "", AGORA);
        long conversaBia = conversas.criarConversaIA(bia, "Naruto", "animado", "", AGORA);
        long personagemAna = conversas.personagemDaConversa(conversaAna).orElseThrow().getId();
        long personagemBia = conversas.personagemDaConversa(conversaBia).orElseThrow().getId();

        assertTrue(memorias.buscar(conversaAna).isEmpty());
        assertTrue(memorias.salvarResumo(conversaAna, ana, personagemAna, "- Ana gosta de lámen.", 10, 0, AGORA));
        assertTrue(memorias.salvarResumo(conversaBia, bia, personagemBia, "- Bia estuda Java.", 4, 0, AGORA));

        // Reinício: nova fábrica sobre o mesmo arquivo.
        JdbcMemoriaRepository depois = new JdbcMemoriaRepository(new ConnectionFactory(arquivo.toString()));
        MemoriaPersonagem daAna = depois.buscar(conversaAna).orElseThrow();
        assertEquals("- Ana gosta de lámen.", daAna.resumo());
        assertEquals(ana, daAna.donoId());
        assertEquals(10, daAna.ateMensagemId());
        assertEquals("- Bia estuda Java.", depois.buscar(conversaBia).orElseThrow().resumo(), "Cada conversa a sua");

        // Uma conta não regrava a memória de outra conversa com o próprio id.
        assertFalse(depois.salvarResumo(conversaAna, bia, personagemBia, "- invasão", 11, 0, AGORA));
        assertEquals("- Ana gosta de lámen.", depois.buscar(conversaAna).orElseThrow().resumo());

        // Apagar: resumo vazio; um resumo antigo (iniciado antes) não é gravado.
        depois.apagar(conversaAna, ana, personagemAna, 12, AGORA);
        MemoriaPersonagem apagada = depois.buscar(conversaAna).orElseThrow();
        assertTrue(apagada.vazio());
        assertEquals(12, apagada.esquecerAteId());
        assertFalse(depois.salvarResumo(conversaAna, ana, personagemAna, "- resumo atrasado", 11, 0, AGORA),
                "Resumo iniciado antes da exclusão não ressuscita a memória");
        assertTrue(depois.buscar(conversaAna).orElseThrow().vazio());
        assertTrue(depois.salvarResumo(conversaAna, ana, personagemAna, "- nova nota", 20, 12, AGORA));

        // Excluir o personagem apaga a memória (ON DELETE CASCADE).
        try (Connection c = fabrica.abrirConexao(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM personagem WHERE id = " + personagemBia);
        }
        assertTrue(depois.buscar(conversaBia).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> depois.salvarResumo(conversaAna, ana, personagemAna, "x".repeat(1_201), 30, 12, AGORA),
                "Tamanho máximo garantido pelo banco");
    }
}
