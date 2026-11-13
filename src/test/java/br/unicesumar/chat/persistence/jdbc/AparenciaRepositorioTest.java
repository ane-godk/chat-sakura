package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Aparencia;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Tabela preferencia_aparencia (v6): migração, gravação por conta, valores antigos e cascata. */
class AparenciaRepositorioTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 13, 12, 0);
    private static final Aparencia FUJI_ESCURO =
            new Aparencia(Aparencia.Paleta.FUJI, Aparencia.Modo.ESCURO, Aparencia.Fundo.FLORAL);
    private static final Aparencia MYOSOTIS_LISO =
            new Aparencia(Aparencia.Paleta.MYOSOTIS, Aparencia.Modo.CLARO, Aparencia.Fundo.LISO);

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

    private ConnectionFactory bancoComContas(String nome) throws Exception {
        ConnectionFactory fabrica = new ConnectionFactory(pasta.resolve(nome).toString());
        fabrica.garantirEsquema();
        try (Connection c = fabrica.abrirConexao(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO usuario (id, login, apelido, apelido_chave, senha_hash, senha_sal, criado_em)"
                    + " VALUES (1, 'ana', 'Ana', 'ana', 'h', 's', '2026-11-10'),"
                    + " (2, 'bia', 'Bia', 'bia', 'h', 's', '2026-11-10')");
        }
        return fabrica;
    }

    @Test
    void bancoV5DeveIrParaV6PreservandoDados() throws Exception {
        Path arquivo = pasta.resolve("v5.db");
        new MigradorBanco(new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES.subList(0, 5),
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"))).migrar();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo); Statement s = c.createStatement()) {
            s.execute("INSERT INTO usuario (id, login, apelido, apelido_chave, senha_hash, senha_sal, criado_em)"
                    + " VALUES (1, 'ana', 'Ana', 'ana', 'h', 's', '2026-11-10')");
            s.execute("INSERT INTO mensagem (conversa_id, remetente, texto, horario) VALUES (1, 'Ana', 'oi', '2026-11-10')");
        }

        MigradorBanco.Resultado resultado = new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES.subList(0, 6),
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"))).migrar();

        assertEquals(5, resultado.versaoAnterior());
        assertEquals(List.of("6"), coluna(arquivo, "PRAGMA user_version"));
        assertEquals(List.of("preferencia_aparencia"),
                coluna(arquivo, "SELECT name FROM sqlite_master WHERE name = 'preferencia_aparencia'"));
        assertEquals(List.of("oi"), coluna(arquivo, "SELECT texto FROM mensagem"));
        assertEquals(List.of("ana"), coluna(arquivo, "SELECT login FROM usuario"));
        assertEquals(List.of("0"), coluna(arquivo, "SELECT count(*) FROM preferencia_aparencia"),
                "Contas existentes continuam sem preferência (usam o padrão)");
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertTrue(arquivos.anyMatch(p -> p.getFileName().toString().contains(".backup-v5-")));
        }
    }

    @Test
    void contaSemPreferenciaNaoTemLinha() throws Exception {
        JdbcAparenciaRepository repositorio = new JdbcAparenciaRepository(bancoComContas("vazio.db"));
        assertEquals(Optional.empty(), repositorio.buscar(1));
        assertEquals(Optional.empty(), repositorio.buscar(999));
    }

    @Test
    void cadaContaGuardaASuaPreferenciaEAGravacaoSubstitui() throws Exception {
        ConnectionFactory fabrica = bancoComContas("contas.db");
        JdbcAparenciaRepository repositorio = new JdbcAparenciaRepository(fabrica);

        repositorio.salvar(1, FUJI_ESCURO, AGORA);
        repositorio.salvar(2, MYOSOTIS_LISO, AGORA);
        assertEquals(Optional.of(FUJI_ESCURO), repositorio.buscar(1));
        assertEquals(Optional.of(MYOSOTIS_LISO), repositorio.buscar(2));

        Aparencia suzuran = FUJI_ESCURO.comPaleta(Aparencia.Paleta.SUZURAN).comFundo(Aparencia.Fundo.NEUTRO);
        repositorio.salvar(1, suzuran, AGORA.plusMinutes(1));
        assertEquals(Optional.of(suzuran), repositorio.buscar(1));
        assertEquals(Optional.of(MYOSOTIS_LISO), repositorio.buscar(2), "A outra conta não muda");

        // Novo repositório sobre o mesmo arquivo (reinício do servidor).
        assertEquals(Optional.of(suzuran), new JdbcAparenciaRepository(fabrica).buscar(1));
        assertEquals(List.of("1|SUZURAN|ESCURO|NEUTRO|2026-11-13T12:01", "2|MYOSOTIS|CLARO|LISO|2026-11-13T12:00"),
                coluna(pasta.resolve("contas.db"), "SELECT usuario_id || '|' || paleta || '|' || modo || '|' || fundo"
                        + " || '|' || atualizada_em FROM preferencia_aparencia ORDER BY usuario_id"));
    }

    @Test
    void valoresGravadosDesconhecidosSaoLidosComoPadraoDoCampo() throws Exception {
        ConnectionFactory fabrica = bancoComContas("antigo.db");
        try (Connection c = fabrica.abrirConexao(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO preferencia_aparencia VALUES (1, 'LAVANDA', 'escuro', 'GRADE', '2026-11-13T12:00')");
        }
        assertEquals(Optional.of(new Aparencia(Aparencia.Paleta.SAKURA, Aparencia.Modo.ESCURO, Aparencia.Fundo.FLORAL)),
                new JdbcAparenciaRepository(fabrica).buscar(1));
    }

    @Test
    void apagarAContaApagaAPreferencia() throws Exception {
        ConnectionFactory fabrica = bancoComContas("cascata.db");
        JdbcAparenciaRepository repositorio = new JdbcAparenciaRepository(fabrica);
        repositorio.salvar(1, FUJI_ESCURO, AGORA);
        repositorio.salvar(2, MYOSOTIS_LISO, AGORA);

        try (Connection c = fabrica.abrirConexao(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM usuario WHERE id = 1");
        }

        assertEquals(Optional.empty(), repositorio.buscar(1));
        assertEquals(Optional.of(MYOSOTIS_LISO), repositorio.buscar(2));
    }

    @Test
    void contaInexistenteNaoPodeTerPreferencia() throws Exception {
        JdbcAparenciaRepository repositorio = new JdbcAparenciaRepository(bancoComContas("fk.db"));
        assertThrows(IllegalStateException.class, () -> repositorio.salvar(42, FUJI_ESCURO, AGORA));
    }
}
