package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Migração do esquema SQLite, sempre em bancos temporários. */
class MigradorBancoTest {

    @TempDir
    Path pasta;

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private static final String TEXTO_ESPECIAL =
            "Ação, coração e pão 🌸👩‍💻 'aspas' \"duplas\"\tTAB; DROP TABLE mensagem;--";

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Path banco(String nome) {
        return pasta.resolve(nome);
    }

    private static Connection bruta(Path arquivo) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + arquivo);
    }

    private static void executar(Path arquivo, String... comandos) throws SQLException {
        try (Connection conexao = bruta(arquivo); Statement comando = conexao.createStatement()) {
            for (String sql : comandos) {
                comando.executeUpdate(sql);
            }
        }
    }

    private static List<List<String>> consultar(Path arquivo, String sql) throws SQLException {
        try (Connection conexao = bruta(arquivo);
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(sql)) {

            int colunas = resultado.getMetaData().getColumnCount();
            List<List<String>> linhas = new ArrayList<>();
            while (resultado.next()) {
                List<String> linha = new ArrayList<>();
                for (int i = 1; i <= colunas; i++) {
                    linha.add(resultado.getString(i));
                }
                linhas.add(linha);
            }
            return linhas;
        }
    }

    private static String valor(Path arquivo, String sql) throws SQLException {
        List<List<String>> linhas = consultar(arquivo, sql);
        return linhas.isEmpty() ? null : linhas.get(0).get(0);
    }

    private static int versao(Path arquivo) throws SQLException {
        return Integer.parseInt(valor(arquivo, "PRAGMA user_version"));
    }

    private static boolean existeTabela(Path arquivo, String tabela) throws SQLException {
        return valor(arquivo, "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '"
                + tabela + "'") != null;
    }

    private static List<String> colunas(Path arquivo, String tabela) throws SQLException {
        return consultar(arquivo, "PRAGMA table_info(" + tabela + ")")
                .stream().map(l -> l.get(1)).toList();
    }

    private List<Path> backups() throws Exception {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            return arquivos.filter(p -> p.getFileName().toString().contains(".backup-v")).toList();
        }
    }

    private MigradorBanco migrador(Path arquivo) {
        return new MigradorBanco(new ConnectionFactory(arquivo.toString()), MigradorBanco.MIGRACOES, RELOGIO);
    }

    /** Banco anterior aos avisos de presença: sem a coluna tipo. */
    private static void criarLegadoSemTipo(Path arquivo) throws SQLException {
        executar(arquivo,
                """
                CREATE TABLE mensagem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    remetente TEXT NOT NULL,
                    texto TEXT NOT NULL,
                    horario TEXT NOT NULL
                )
                """,
                "INSERT INTO mensagem (id, remetente, texto, horario) VALUES "
                        + "(1, 'Bia', '" + TEXTO_ESPECIAL.replace("'", "''") + "', '2026-11-03T15:00')",
                "INSERT INTO mensagem (id, remetente, texto, horario) VALUES "
                        + "(2, 'Caio', 'Segunda', '2026-11-03T15:05:59.123456789')",
                "INSERT INTO mensagem (id, remetente, texto, horario) VALUES "
                        + "(5, 'Ana', 'Depois de uma lacuna', '2026-11-03T15:10')",
                // Id 7 já foi usado e apagado: não pode ser reutilizado.
                "INSERT INTO mensagem (id, remetente, texto, horario) VALUES "
                        + "(7, 'Ana', 'Apagada', '2026-11-03T15:11')",
                "DELETE FROM mensagem WHERE id = 7");
    }

    /** Banco com avisos de presença (coluna tipo), ainda sem versão. */
    private static void criarLegadoComTipo(Path arquivo) throws SQLException {
        executar(arquivo,
                """
                CREATE TABLE mensagem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    remetente TEXT NOT NULL,
                    texto TEXT NOT NULL,
                    horario TEXT NOT NULL,
                    tipo TEXT NOT NULL DEFAULT 'MENSAGEM'
                )
                """,
                "INSERT INTO mensagem (remetente, texto, horario, tipo) VALUES "
                        + "('Ana', 'se conectou', '2026-11-04T20:34', 'ENTRADA')",
                "INSERT INTO mensagem (remetente, texto, horario, tipo) VALUES "
                        + "('Ana', 'Olá, mundo! 🌸', '2026-11-04T20:35', 'MENSAGEM')",
                "INSERT INTO mensagem (remetente, texto, horario, tipo) VALUES "
                        + "('Ana', 'se desconectou', '2026-11-04T20:38', 'SAIDA')");
    }

    private static final String DADOS_LEGADOS =
            "SELECT id, remetente, texto, horario FROM mensagem ORDER BY id";

    // ------------------------------------------------------------------
    // Testes
    // ------------------------------------------------------------------

    @Test
    void bancoVazioDeveSerCriadoNaVersaoAtualSemBackup() throws Exception {
        Path arquivo = banco("vazio.db");

        MigradorBanco.Resultado resultado = migrador(arquivo).migrar();

        assertEquals(0, resultado.versaoAnterior());
        assertEquals(MigradorBanco.VERSAO_ATUAL, resultado.versaoAtual());
        assertNull(resultado.backup());
        assertTrue(backups().isEmpty());
        assertEquals(MigradorBanco.VERSAO_ATUAL, versao(arquivo));

        for (String tabela : List.of("usuario", "conversa", "conversa_participante",
                "conversa_privada", "personagem", "mensagem")) {
            assertTrue(existeTabela(arquivo, tabela), tabela);
        }

        assertEquals(List.of(List.of("1", "GERAL", "Chat Geral")),
                consultar(arquivo, "SELECT id, tipo, nome FROM conversa"));
        assertEquals("0", valor(arquivo, "SELECT count(*) FROM mensagem"));
        assertEquals(List.of("id", "conversa_id", "remetente_id", "remetente", "texto", "horario", "tipo"),
                colunas(arquivo, "mensagem"));
    }

    @Test
    void legadoSemTipoDeveMigrarPreservandoTudo() throws Exception {
        Path arquivo = banco("legado-sem-tipo.db");
        criarLegadoSemTipo(arquivo);
        List<List<String>> antes = consultar(arquivo, DADOS_LEGADOS);

        MigradorBanco.Resultado resultado = migrador(arquivo).migrar();

        assertTrue(resultado.migrou());
        assertEquals(0, resultado.versaoAnterior());
        assertEquals(MigradorBanco.VERSAO_ATUAL, versao(arquivo));

        // Ids, remetentes, textos (acentos, emojis, aspas) e horários idênticos.
        assertEquals(antes, consultar(arquivo, DADOS_LEGADOS));
        assertEquals(TEXTO_ESPECIAL, valor(arquivo, "SELECT texto FROM mensagem WHERE id = 1"));
        assertEquals("2026-11-03T15:05:59.123456789",
                valor(arquivo, "SELECT horario FROM mensagem WHERE id = 2"));

        // Todas na Geral, como mensagens, sem conta associada.
        assertEquals(List.of(List.of("3", "3", "0")), consultar(arquivo,
                "SELECT sum(conversa_id = 1), sum(tipo = 'MENSAGEM'), count(remetente_id) FROM mensagem"));
        assertEquals("0", valor(arquivo, "SELECT count(*) FROM usuario"));

        // O repositório atual continua funcionando e não reutiliza o id 7.
        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString()));
        List<Mensagem> historico = repositorio.buscarUltimas(10);
        assertEquals(List.of(1L, 2L, 5L), historico.stream().map(Mensagem::getId).toList());
        assertEquals(TEXTO_ESPECIAL, historico.get(0).getConteudo());
        assertEquals("[03/11 15:00] Bia: " + TEXTO_ESPECIAL, historico.get(0).formatar());

        Mensagem nova = repositorio.salvar(new Mensagem(new Usuario("Dani"), "Nova", LocalDateTime.of(2026, 11, 10, 9, 0)));
        assertEquals(8L, nova.getId());
        assertEquals("1", valor(arquivo, "SELECT conversa_id FROM mensagem WHERE id = 8"));
    }

    @Test
    void legadoComTipoDeveMigrarPreservandoAvisosDePresenca() throws Exception {
        Path arquivo = banco("legado-com-tipo.db");
        criarLegadoComTipo(arquivo);
        List<List<String>> antes = consultar(arquivo, "SELECT id, remetente, texto, horario, tipo FROM mensagem ORDER BY id");

        migrador(arquivo).migrar();

        assertEquals(antes, consultar(arquivo, "SELECT id, remetente, texto, horario, tipo FROM mensagem ORDER BY id"));

        List<Mensagem> historico = new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString())).buscarUltimas(10);
        assertEquals(List.of(TipoMensagem.ENTRADA, TipoMensagem.MENSAGEM, TipoMensagem.SAIDA),
                historico.stream().map(Mensagem::getTipo).toList());
        assertEquals("SISTEMA: [04/11 20:34] Ana se conectou", historico.get(0).formatar());
        assertEquals("[04/11 20:35] Ana: Olá, mundo! 🌸", historico.get(1).formatar());
    }

    @Test
    void backupDeveSerConsistenteMesmoEmModoWal() throws Exception {
        Path arquivo = banco("wal.db");

        // Conexão aberta mantém os dados no arquivo -wal, sem checkpoint.
        try (Connection aberta = bruta(arquivo); Statement comando = aberta.createStatement()) {
            comando.executeQuery("PRAGMA journal_mode = WAL").close();
            comando.executeUpdate("PRAGMA wal_autocheckpoint = 0");
            comando.executeUpdate("CREATE TABLE mensagem (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "remetente TEXT NOT NULL, texto TEXT NOT NULL, horario TEXT NOT NULL)");
            comando.executeUpdate("INSERT INTO mensagem (remetente, texto, horario) "
                    + "VALUES ('Bia', 'Só no WAL 🌸', '2026-11-03T15:00')");
            assertTrue(Files.size(banco("wal.db-wal")) > 0, "Os dados devem estar no WAL");

            MigradorBanco.Resultado resultado = migrador(arquivo).migrar();
            Path backup = resultado.backup();

            assertNotNull(backup);
            assertEquals(List.of(backup), backups());
            assertTrue(backup.getFileName().toString().startsWith("wal.backup-v0-20261110-120000"));

            // O backup é o banco anterior, íntegro e completo.
            assertEquals(0, versao(backup));
            assertEquals("ok", valor(backup, "PRAGMA quick_check"));
            assertEquals(List.of("id", "remetente", "texto", "horario"), colunas(backup, "mensagem"));
            assertEquals("Só no WAL 🌸", valor(backup, "SELECT texto FROM mensagem"));
        }

        assertEquals("Só no WAL 🌸", valor(arquivo, "SELECT texto FROM mensagem WHERE conversa_id = 1"));
    }

    @Test
    void execucaoRepetidaNaoDeveDuplicarNemAlterarDados() throws Exception {
        Path arquivo = banco("repetido.db");
        criarLegadoComTipo(arquivo);

        migrador(arquivo).migrar();
        String consultaCompleta = "SELECT * FROM mensagem ORDER BY id";
        List<List<String>> mensagens = consultar(arquivo, consultaCompleta);
        List<List<String>> conversas = consultar(arquivo, "SELECT * FROM conversa");

        for (int i = 0; i < 3; i++) {
            MigradorBanco.Resultado resultado = migrador(arquivo).migrar();
            assertFalse(resultado.migrou());
            assertNull(resultado.backup());
            new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString()));
        }

        assertEquals(mensagens, consultar(arquivo, consultaCompleta));
        assertEquals(conversas, consultar(arquivo, "SELECT * FROM conversa"));
        assertEquals(1, backups().size(), "Somente a migração real gera backup");
        assertEquals(MigradorBanco.VERSAO_ATUAL, versao(arquivo));
    }

    @Test
    void falhaDuranteAMigracaoDeveDesfazerTudo() throws Exception {
        Path arquivo = banco("falha.db");
        criarLegadoSemTipo(arquivo);
        List<List<String>> antes = consultar(arquivo, DADOS_LEGADOS);
        List<List<String>> esquemaAntes = consultar(arquivo, "SELECT type, name, sql FROM sqlite_master ORDER BY name");

        // A v2 é aplicada por inteiro (inclusive a reconstrução de mensagem)
        // e só então a falha acontece.
        List<MigradorBanco.Migracao> comFalha = List.of(
                MigradorBanco::migrarParaV1,
                conexao -> {
                    MigradorBanco.migrarParaV2(conexao);
                    throw new SQLException("falha simulada");
                });

        MigracaoBancoException erro = assertThrows(MigracaoBancoException.class,
                () -> new MigradorBanco(new ConnectionFactory(arquivo.toString()), comFalha, RELOGIO).migrar());

        assertTrue(erro.getMessage().contains("falha simulada"));
        assertTrue(erro.getMessage().contains("permanece na versão 0"));
        assertTrue(erro.getMessage().contains("Backup:"));

        // Versão, esquema (inclusive a v1) e dados exatamente como antes.
        assertEquals(0, versao(arquivo));
        assertEquals(esquemaAntes, consultar(arquivo, "SELECT type, name, sql FROM sqlite_master ORDER BY name"));
        assertEquals(antes, consultar(arquivo, DADOS_LEGADOS));
        assertFalse(existeTabela(arquivo, "conversa"));
        assertFalse(colunas(arquivo, "mensagem").contains("tipo"));
        assertEquals(1, backups().size());

        // Corrigido o problema, a migração normal funciona.
        migrador(arquivo).migrar();
        assertEquals(MigradorBanco.VERSAO_ATUAL, versao(arquivo));
        assertEquals(antes, consultar(arquivo, DADOS_LEGADOS));
    }

    @Test
    void bancoDeVersaoFuturaDeveSerRecusadoSemAlteracao() throws Exception {
        Path arquivo = banco("futuro.db");
        executar(arquivo,
                "CREATE TABLE mensagem (id INTEGER PRIMARY KEY, texto TEXT)",
                "INSERT INTO mensagem (texto) VALUES ('do futuro')",
                "PRAGMA user_version = 99");
        byte[] antes = Files.readAllBytes(arquivo);

        MigracaoBancoException erro = assertThrows(MigracaoBancoException.class,
                () -> migrador(arquivo).migrar());
        assertTrue(erro.getMessage().contains("versão 99"));

        // O repositório (e portanto o servidor) não inicia.
        assertThrows(MigracaoBancoException.class,
                () -> new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString())));

        assertArrayEquals(antes, Files.readAllBytes(arquivo));
        assertTrue(backups().isEmpty());
    }

    @Test
    void chavesEstrangeirasERestricoesDevemSerAplicadas() throws Exception {
        Path arquivo = banco("integridade.db");
        ConnectionFactory fabrica = new ConnectionFactory(arquivo.toString());
        migrador(arquivo).migrar();

        try (Connection conexao = fabrica.abrirConexao(); Statement comando = conexao.createStatement()) {

            try (ResultSet ativo = comando.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(ativo.next());
                assertEquals(1, ativo.getInt(1), "Toda conexão da fábrica ativa as chaves estrangeiras");
            }

            String agora = "'2026-11-10T12:00'";
            comando.executeUpdate("INSERT INTO usuario (id, login, apelido, senha_hash, senha_sal, criado_em) "
                    + "VALUES (1, 'ana', 'Ana', 'h', 's', " + agora + "), (2, 'bia', 'Bia', 'h', 's', " + agora + ")");
            comando.executeUpdate("INSERT INTO conversa (id, tipo, criado_em) VALUES (10, 'PRIVADA', " + agora + ")");
            comando.executeUpdate("INSERT INTO conversa_privada VALUES (10, 1, 2)");
            comando.executeUpdate("INSERT INTO conversa_participante (conversa_id, usuario_id, entrou_em) "
                    + "VALUES (10, 1, " + agora + "), (10, 2, " + agora + ")");

            List<String> recusados = List.of(
                    // Conversa inexistente.
                    "INSERT INTO mensagem (conversa_id, remetente, texto, horario) VALUES (999, 'Ana', 'x', " + agora + ")",
                    // Usuário inexistente.
                    "INSERT INTO mensagem (conversa_id, remetente_id, remetente, texto, horario) VALUES (1, 999, 'X', 'x', " + agora + ")",
                    // Login repetido (sem diferenciar maiúsculas).
                    "INSERT INTO usuario (login, apelido, senha_hash, senha_sal, criado_em) VALUES ('ANA', 'Outra', 'h', 's', " + agora + ")",
                    // Segunda conversa Geral.
                    "INSERT INTO conversa (tipo, criado_em) VALUES ('GERAL', " + agora + ")",
                    // Tipo inválido.
                    "INSERT INTO conversa (tipo, criado_em) VALUES ('CANAL', " + agora + ")",
                    // IA sem personagem e grupo sem nome.
                    "INSERT INTO conversa (tipo, criado_em) VALUES ('IA', " + agora + ")",
                    "INSERT INTO conversa (tipo, nome, criado_em) VALUES ('GRUPO', '  ', " + agora + ")",
                    // Par privado fora da ordem canônica e par repetido.
                    "INSERT INTO conversa (id, tipo, criado_em) VALUES (11, 'PRIVADA', " + agora + ")",
                    "INSERT INTO conversa_privada VALUES (11, 2, 1)",
                    "INSERT INTO conversa_privada VALUES (11, 1, 2)",
                    // Participante repetido e papel inválido.
                    "INSERT INTO conversa_participante (conversa_id, usuario_id, entrou_em) VALUES (10, 1, " + agora + ")",
                    "INSERT INTO conversa_participante (conversa_id, usuario_id, papel, entrou_em) VALUES (1, 1, 'DONO', " + agora + ")",
                    // Personagem de dono inexistente.
                    "INSERT INTO personagem (dono_id, nome, personalidade, criado_em) VALUES (999, 'Sakura', 'gentil', " + agora + ")"
            );

            for (String sql : recusados) {
                // A conversa 11 em si é válida; ela só prepara os pares.
                if (sql.contains("VALUES (11, 'PRIVADA'")) {
                    comando.executeUpdate(sql);
                    continue;
                }
                assertThrows(SQLException.class, () -> comando.executeUpdate(sql), sql);
            }

            try (ResultSet violacoes = comando.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(violacoes.next());
            }
        }

        // Reabrir verifica a integridade e mantém tudo.
        migrador(arquivo).migrar();
        assertEquals("2", valor(arquivo, "SELECT count(*) FROM conversa_participante"));
    }

    @Test
    void historicoGeralNaoDeveIncluirMensagensDeOutrasConversas() throws Exception {
        Path arquivo = banco("isolamento.db");
        ConnectionFactory fabrica = new ConnectionFactory(arquivo.toString());
        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(fabrica);

        repositorio.salvar(new Mensagem(new Usuario("Ana"), "Pública", LocalDateTime.of(2026, 11, 10, 9, 0)));
        executar(arquivo,
                "INSERT INTO conversa (id, tipo, nome, criado_em) VALUES (2, 'GRUPO', 'Turma', '2026-11-10T09:00')",
                "INSERT INTO mensagem (conversa_id, remetente, texto, horario) "
                        + "VALUES (2, 'Bia', 'Secreta', '2026-11-10T09:01')");

        assertEquals(List.of("Pública"),
                repositorio.buscarUltimas(10).stream().map(Mensagem::getConteudo).toList());
    }

    /** Esquema normalizado (sem aspas e espaços extras) para comparação. */
    private static List<List<String>> esquema(Path arquivo) throws SQLException {
        return consultar(arquivo, "SELECT type, name, sql FROM sqlite_master "
                + "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name").stream()
                .map(l -> List.of(l.get(0), l.get(1),
                        l.get(2).replace("\"", "").replaceAll("\\s+", " ")
                                .replaceAll("\\( ", "(").replaceAll(" \\)", ")").trim()))
                .toList();
    }

    @Test
    void scriptDocumentadoDeveCorresponderAoEsquemaMigrado() throws Exception {
        Path migrado = banco("migrado.db");
        migrador(migrado).migrar();

        Path documentado = banco("documentado.db");
        String script = Files.readString(Path.of("sql", "criar_tabelas.sql"))
                .lines().filter(l -> !l.trim().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");
        executar(documentado, Stream.of(script.split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new));

        assertEquals(esquema(migrado), esquema(documentado));
        assertEquals(versao(migrado), versao(documentado));
    }

    @Test
    void bancoV2DeveIrParaV3PreservandoContasConversasEMensagens() throws Exception {
        Path arquivo = banco("v2.db");

        // Banco no esquema v2 (como ao fim das Etapas 1 e 2), com dados.
        new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES.subList(0, 2), RELOGIO).migrar();
        executar(arquivo,
                "INSERT INTO usuario (id, login, apelido, senha_hash, senha_sal, criado_em) "
                        + "VALUES (1, 'ana', 'Ana', 'h', 's', '2026-11-10T12:00')",
                "INSERT INTO conversa (id, tipo, nome, criador_id, criado_em) "
                        + "VALUES (2, 'GRUPO', 'Turma', 1, '2026-11-10T12:00')",
                "INSERT INTO conversa_participante (conversa_id, usuario_id, papel, entrou_em) "
                        + "VALUES (2, 1, 'ADMIN', '2026-11-10T12:00')",
                "INSERT INTO mensagem (conversa_id, remetente_id, remetente, texto, horario) "
                        + "VALUES (2, 1, 'Ana', 'Antes da v3', '2026-11-10T12:01')");
        assertEquals(2, versao(arquivo));
        List<List<String>> mensagensAntes = consultar(arquivo, "SELECT * FROM mensagem ORDER BY id");

        // Só a etapa v2→v3 (a v4 tem testes próprios).
        MigradorBanco.Resultado resultado = new MigradorBanco(new ConnectionFactory(arquivo.toString()),
                MigradorBanco.MIGRACOES.subList(0, 3), RELOGIO).migrar();

        assertEquals(2, resultado.versaoAnterior());
        assertEquals(3, versao(arquivo));
        assertTrue(resultado.backup().getFileName().toString().contains(".backup-v2-"));
        assertEquals(mensagensAntes, consultar(arquivo, "SELECT * FROM mensagem ORDER BY id"));
        assertEquals(List.of(List.of("2", "1", "ADMIN", "0")), consultar(arquivo,
                "SELECT conversa_id, usuario_id, papel, ultima_lida_id FROM conversa_participante"));
    }

    @Test
    void historicoDevePersistirAposReiniciarOServidor() throws Exception {
        Path arquivo = banco("reinicio.db");
        Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("UTC"));

        ChatService primeiro = new ChatService(
                new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString())),
                new ClientRegistry(), relogio);
        primeiro.enviarMensagem(new Usuario("Ana"), "Antes de reiniciar 🌸");
        primeiro.enviarMensagem(new Usuario("Bia"), "Também persiste");

        // "Reinício": nova fábrica, novo repositório e novo serviço.
        ChatService segundo = new ChatService(
                new JdbcMensagemRepository(new ConnectionFactory(arquivo.toString())),
                new ClientRegistry(), relogio);

        assertEquals(
                List.of("[10/11 12:00] Ana: Antes de reiniciar 🌸", "[10/11 12:00] Bia: Também persiste"),
                segundo.buscarHistorico(20).stream().map(Mensagem::formatar).toList());
        assertTrue(backups().isEmpty(), "Banco já atualizado não gera backup");
    }
}
