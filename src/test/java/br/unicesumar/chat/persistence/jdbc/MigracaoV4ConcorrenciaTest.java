package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.service.HashSenha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Migração v3→v4 (apelido_chave), colisões, migração única e concorrência no SQLite (S7, B4, B7). */
class MigracaoV4ConcorrenciaTest {

    @TempDir
    Path pasta;

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("UTC"));

    private Path banco(String nome) {
        return pasta.resolve(nome);
    }

    private static void executar(Path arquivo, String... comandos) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo);
             Statement s = c.createStatement()) {
            for (String comando : comandos) {
                s.execute(comando);
            }
        }
    }

    private static List<String> coluna(Path arquivo, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo);
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            List<String> valores = new ArrayList<>();
            while (r.next()) {
                valores.add(r.getString(1));
            }
            return valores;
        }
    }

    private static int versao(Path arquivo) throws SQLException {
        return Integer.parseInt(coluna(arquivo, "PRAGMA user_version").get(0));
    }

    /** Banco já na v3, com as contas indicadas (id, login, apelido). */
    private Path bancoV3(String nome, String[]... contas) throws SQLException {
        Path arquivo = banco(nome);
        new MigradorBanco(new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES.subList(0, 3), RELOGIO)
                .migrar();
        for (String[] conta : contas) {
            executar(arquivo, "INSERT INTO usuario (id, login, apelido, senha_hash, senha_sal, criado_em) VALUES ("
                    + conta[0] + ", '" + conta[1] + "', '" + conta[2] + "', 'h', 's', '2026-11-10T12:00')");
        }
        executar(arquivo, "INSERT INTO mensagem (conversa_id, remetente_id, remetente, texto, horario) "
                + "VALUES (1, 1, 'Élia', 'antes da v4', '2026-11-10T12:01')");
        return arquivo;
    }

    private List<Path> backups() throws Exception {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            return arquivos.filter(p -> p.getFileName().toString().contains(".backup-v")).toList();
        }
    }

    @Test
    void v3ParaV4DevePreencherChavesUnicodeEPreservarDados() throws Exception {
        Path arquivo = bancoV3("v3.db",
                new String[] {"1", "elia", "Élia"},
                new String[] {"2", "ana", "Ａｎａ"},
                new String[] {"3", "joao", "João"});

        MigradorBanco.Resultado resultado = new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES.subList(0, 4), RELOGIO).migrar();

        assertEquals(3, resultado.versaoAnterior());
        assertEquals(4, versao(arquivo));
        assertTrue(resultado.backup().getFileName().toString().contains(".backup-v3-"));
        assertEquals(3, versao(resultado.backup()), "O backup é o banco v3");

        assertEquals(List.of("Élia", "Ａｎａ", "João"), coluna(arquivo, "SELECT apelido FROM usuario ORDER BY id"),
                "A apresentação original é preservada");
        assertEquals(List.of("élia", "ana", "joão"), coluna(arquivo, "SELECT apelido_chave FROM usuario ORDER BY id"));
        assertEquals(List.of("antes da v4"), coluna(arquivo, "SELECT texto FROM mensagem"));

        // Índice único como última defesa.
        assertThrows(SQLException.class, () -> executar(arquivo,
                "INSERT INTO usuario (login, apelido, apelido_chave, senha_hash, senha_sal, criado_em) "
                        + "VALUES ('outra', 'ANA', 'ana', 'h', 's', '2026-11-10')"));

        // Idempotente: rodar de novo não altera nada nem cria backup.
        MigradorBanco.Resultado denovo = new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES.subList(0, 4), RELOGIO).migrar();
        assertFalse(denovo.migrou());
        assertEquals(1, backups().size());
    }

    @Test
    void colisaoDeApelidosDeveInterromperSemAlterarContas() throws Exception {
        Path arquivo = bancoV3("colisao.db",
                new String[] {"1", "elia", "Élia"},
                new String[] {"2", "elia2", "ÉLIA"},
                new String[] {"3", "ana", "Ana"},
                new String[] {"4", "ana2", "Ａｎａ"},
                new String[] {"5", "bia", "Bia"});
        List<String> antes = coluna(arquivo, "SELECT id || ':' || login || ':' || apelido FROM usuario ORDER BY id");

        MigracaoBancoException erro = assertThrows(MigracaoBancoException.class, () -> new MigradorBanco(
                new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES.subList(0, 4), RELOGIO).migrar());

        String mensagem = erro.getMessage();
        assertTrue(mensagem.contains("id 1 (\"Élia\"), id 2 (\"ÉLIA\")"), mensagem);
        assertTrue(mensagem.contains("id 3 (\"Ana\"), id 4 (\"Ａｎａ\")"), mensagem);
        assertTrue(mensagem.contains("Nenhuma conta foi alterada"), mensagem);
        assertTrue(mensagem.contains("permanece na versão 3"), mensagem);
        assertFalse(mensagem.contains("Bia"), "Só as contas em colisão são citadas");

        assertEquals(3, versao(arquivo), "Rollback: o banco continua na v3");
        assertEquals(antes, coluna(arquivo, "SELECT id || ':' || login || ':' || apelido FROM usuario ORDER BY id"),
                "Nenhuma conta renomeada ou excluída");
        assertFalse(coluna(arquivo, "PRAGMA table_info(usuario)").isEmpty());
        assertTrue(coluna(arquivo, "SELECT name FROM pragma_table_info('usuario') WHERE name = 'apelido_chave'")
                .isEmpty(), "Nem a coluna nova sobrou");
        assertEquals(1, backups().size(), "Backup consistente criado antes da tentativa");
        assertEquals(3, versao(backups().get(0)));

        // Após renomear manualmente, a migração segue.
        executar(arquivo, "UPDATE usuario SET apelido = 'Élia2' WHERE id = 2",
                "UPDATE usuario SET apelido = 'Ana4' WHERE id = 4");
        new MigradorBanco(new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES.subList(0, 4), RELOGIO).migrar();
        assertEquals(4, versao(arquivo));
    }

    @Test
    void bancoLegadoV0DeveChegarAVersaoAtual() throws Exception {
        Path arquivo = banco("legado.db");
        executar(arquivo,
                "CREATE TABLE mensagem (id INTEGER PRIMARY KEY AUTOINCREMENT, remetente TEXT NOT NULL,"
                        + " texto TEXT NOT NULL, horario TEXT NOT NULL)",
                "INSERT INTO mensagem (remetente, texto, horario) VALUES ('Ana', 'legado', '2026-11-01T10:00')");

        MigradorBanco.Resultado resultado = new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES, RELOGIO).migrar();

        assertEquals(0, resultado.versaoAnterior());
        assertEquals(5, MigradorBanco.VERSAO_ATUAL);
        assertEquals(MigradorBanco.VERSAO_ATUAL, versao(arquivo));
        assertEquals(List.of("legado"), coluna(arquivo, "SELECT texto FROM mensagem"));
        assertEquals(List.of("ux_usuario_apelido_chave"),
                coluna(arquivo, "SELECT name FROM sqlite_master WHERE name = 'ux_usuario_apelido_chave'"));
    }

    @Test
    void repositorioDeveRecusarApelidoEquivalenteEReservarNomes() throws Exception {
        JdbcUsuarioRepository repositorio = new JdbcUsuarioRepository(new ConnectionFactory(banco("contas.db").toString()));
        repositorio.cadastrar("elia", "Élia", "h", "s", "sakura", LocalDateTime.now());

        UsuarioRepository.ContaDuplicadaException duplicada = assertThrows(
                UsuarioRepository.ContaDuplicadaException.class,
                () -> repositorio.cadastrar("outra", "ÉLIA", "h", "s", "sakura", LocalDateTime.now()));
        assertEquals(UsuarioRepository.ContaDuplicadaException.Campo.APELIDO, duplicada.getCampo());

        assertTrue(repositorio.nomeEmUso("élia"));
        assertTrue(repositorio.nomeEmUso("ＥＬＩＡ"), "Largura total");
        assertTrue(repositorio.nomeEmUso("ELIA"), "Equivale ao login");
        assertFalse(repositorio.nomeEmUso("Elio"));
    }

    @Test
    void servidorDeveMigrarUmaUnicaVezEAtivarWal() throws Exception {
        Path arquivo = banco("servidor.db");
        ConnectionFactory fabrica = new ConnectionFactory(arquivo.toString());

        Servidor.Servicos.montar(fabrica, new HashSenha(1_000), RELOGIO, new ProvedorIAFalso());

        assertEquals(1, fabrica.migracoesExecutadas(), "Três repositórios, uma migração");
        assertEquals(List.of("wal"), coluna(arquivo, "PRAGMA journal_mode"));

        // Uma nova inicialização (nova fábrica) migra/verifica de novo, uma vez.
        ConnectionFactory reinicio = new ConnectionFactory(arquivo.toString());
        new JdbcMensagemRepository(reinicio);
        new JdbcConversaRepository(reinicio);
        assertEquals(1, reinicio.migracoesExecutadas());
    }

    @Test
    void gravacoesSimultaneasEmConversasDiferentesNaoDevemPerderMensagens() throws Exception {
        ConnectionFactory fabrica = new ConnectionFactory(banco("concorrencia.db").toString());
        JdbcMensagemRepository mensagens = new JdbcMensagemRepository(fabrica);
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(fabrica);
        JdbcConversaRepository conversas = new JdbcConversaRepository(fabrica);

        long ana = usuarios.cadastrar("ana", "Ana", "h", "s", "sakura", LocalDateTime.now()).getId();
        long bia = usuarios.cadastrar("bia", "Bia", "h", "s", "sakura", LocalDateTime.now()).getId();
        List<Long> ids = new ArrayList<>();
        ids.add(1L);
        ids.add(conversas.criarPrivada(ana, bia, LocalDateTime.now()));
        for (int g = 0; g < 2; g++) {
            ids.add(conversas.criarGrupo("Grupo" + g, ana, java.util.Set.of(bia), LocalDateTime.now()));
        }

        int threads = 8;
        int porThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> tarefas = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            tarefas.add(executor.submit(() -> {
                long conversa = ids.get(thread % ids.size());
                for (int i = 0; i < porThread; i++) {
                    mensagens.salvar(new Mensagem(null, new Usuario(ana, "Ana"), "t" + thread + "-" + i,
                            LocalDateTime.now(), TipoMensagem.MENSAGEM, conversa));
                    // Leituras concorrentes com as gravações (WAL: não bloqueiam).
                    mensagens.buscarHistorico(conversa, null, 5);
                }
            }));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        for (Future<?> tarefa : tarefas) {
            tarefa.get(); // nenhuma exceção (SQLITE_BUSY) em nenhuma thread
        }

        int total = 0;
        for (long conversa : ids) {
            List<Mensagem> todas = mensagens.buscarHistorico(conversa, null, 1_000);
            total += todas.size();
            for (int t = 0; t < threads; t++) {
                if (ids.get(t % ids.size()) == conversa) {
                    int thread = t;
                    List<String> daThread = todas.stream().map(Mensagem::getConteudo)
                            .filter(c -> c.startsWith("t" + thread + "-")).toList();
                    assertEquals(porThread, daThread.size());
                    for (int i = 0; i < porThread; i++) {
                        assertEquals("t" + thread + "-" + i, daThread.get(i), "Ordem de cada thread preservada");
                    }
                }
            }
        }
        assertEquals(threads * porThread, total, "Nenhuma mensagem perdida");
    }
}
