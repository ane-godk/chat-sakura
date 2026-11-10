package br.unicesumar.chat.service;

import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static br.unicesumar.chat.service.AutenticacaoException.Codigo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cadastro e login sobre SQLite temporário. */
class ServicoAutenticacaoTest {

    @TempDir
    Path pasta;

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private static final String SENHA = "flor de cerejeira 🌸";

    private Path banco() {
        return pasta.resolve("contas.db");
    }

    private ServicoAutenticacao servico() {
        return new ServicoAutenticacao(
                new JdbcUsuarioRepository(new ConnectionFactory(banco().toString())),
                new HashSenha(1_000),
                RELOGIO);
    }

    private static char[] senha() {
        return SENHA.toCharArray();
    }

    private static AutenticacaoException.Codigo codigo(Executavel acao) {
        AutenticacaoException e = assertThrows(AutenticacaoException.class, acao::executar);
        return e.getCodigo();
    }

    @FunctionalInterface
    private interface Executavel {
        void executar() throws Exception;
    }

    private List<List<String>> usuariosBrutos() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + banco());
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM usuario ORDER BY id")) {
            List<List<String>> linhas = new ArrayList<>();
            int colunas = r.getMetaData().getColumnCount();
            while (r.next()) {
                List<String> linha = new ArrayList<>();
                for (int i = 1; i <= colunas; i++) {
                    linha.add(r.getString(i));
                }
                linhas.add(linha);
            }
            return linhas;
        }
    }

    @Test
    void cadastroValidoDeveRetornarSomenteDadosPublicosEApagarASenha() throws Exception {
        char[] senha = senha();

        PerfilUsuario perfil = servico().cadastrar("  Ana.Souza ", "Ágata", senha);

        assertTrue(perfil.getId() > 0);
        assertEquals("ana.souza", perfil.getLogin(), "Login normalizado em minúsculas");
        assertEquals("Ágata", perfil.getApelido());
        assertEquals("sakura", perfil.getAvatarId());
        assertArrayEquals(new char[SENHA.length()], senha, "A senha é apagada após o uso");

        List<String> linha = usuariosBrutos().get(0);
        assertEquals("ana.souza", linha.get(1));
        assertEquals("2026-11-10T12:00", linha.get(6));
    }

    @Test
    void loginComSenhaCorretaDeveAutenticarSemDiferenciarMaiusculas() throws Exception {
        ServicoAutenticacao servico = servico();
        PerfilUsuario cadastrado = servico.cadastrar("ana.souza", "Ana", senha());

        assertEquals(cadastrado, servico.autenticar("ana.souza", senha()));
        assertEquals(cadastrado, servico.autenticar("ANA.Souza", senha()));
    }

    @Test
    void senhaIncorretaELoginInexistenteDevemTerAMesmaResposta() throws Exception {
        ServicoAutenticacao servico = servico();
        servico.cadastrar("ana.souza", "Ana", senha());

        AutenticacaoException errada = assertThrows(AutenticacaoException.class,
                () -> servico.autenticar("ana.souza", "outra senha 123".toCharArray()));
        AutenticacaoException inexistente = assertThrows(AutenticacaoException.class,
                () -> servico.autenticar("bia.lima", senha()));
        AutenticacaoException invalida = assertThrows(AutenticacaoException.class,
                () -> servico.autenticar("ana.souza", "curta".toCharArray()));

        for (AutenticacaoException e : List.of(errada, inexistente, invalida)) {
            assertEquals(CREDENCIAIS_INVALIDAS, e.getCodigo());
            assertEquals("Login ou senha incorretos.", e.getMessage());
        }
    }

    @Test
    void loginOuApelidoDuplicadosDevemSerRecusados() throws Exception {
        ServicoAutenticacao servico = servico();
        servico.cadastrar("ana.souza", "Ana", senha());

        assertEquals(LOGIN_EM_USO, codigo(() -> servico.cadastrar("ANA.SOUZA", "Outra", senha())));
        assertEquals(APELIDO_EM_USO, codigo(() -> servico.cadastrar("outra.ana", "ANA", senha())));
        // Login de uma conta não pode virar apelido de outra, e vice-versa.
        assertEquals(APELIDO_EM_USO, codigo(() -> servico.cadastrar("bia.lima", "Ana.Souza", senha())));
        assertEquals(LOGIN_EM_USO, codigo(() -> servico.cadastrar("ana", "Bia", senha())));

        assertEquals(1, usuariosBrutos().size());
    }

    @Test
    void camposInvalidosDevemSerRecusadosComMensagemSegura() throws Exception {
        ServicoAutenticacao servico = servico();
        String controle = "senha\u0007longa";

        Object[][] casos = {
                {null, "Ana", SENHA}, {"", "Ana", SENHA}, {"ab", "Ana", SENHA},
                {"a".repeat(31), "Ana", SENHA}, {"ana souza", "Ana", SENHA}, {"ána", "Ana", SENHA},
                {"ana", null, SENHA}, {"ana", " ", SENHA}, {"ana", "Ana Souza", SENHA},
                {"ana", "A".repeat(31), SENHA}, {"ana", "Ana<script>", SENHA},
                {"ana", "Ana", "1234567"}, {"ana", "Ana", "x".repeat(129)}, {"ana", "Ana", controle},
                {"ana", "Ana", null}};

        for (Object[] caso : casos) {
            char[] senha = caso[2] == null ? null : ((String) caso[2]).toCharArray();
            AutenticacaoException e = assertThrows(AutenticacaoException.class,
                    () -> servico.cadastrar((String) caso[0], (String) caso[1], senha));
            assertEquals(DADOS_INVALIDOS, e.getCodigo(), java.util.Arrays.toString(caso));
            if (caso[2] != null) {
                assertFalse(e.getMessage().contains((String) caso[2]), "A mensagem não contém a senha");
            }
        }

        assertTrue(usuariosBrutos().isEmpty());
    }

    @Test
    void contaDevePersistirAposReabrirOBanco() throws Exception {
        PerfilUsuario cadastrado = servico().cadastrar("ana.souza", "Ana", senha());

        // Nova fábrica, novo repositório e novo serviço: como após reiniciar.
        JdbcUsuarioRepository reaberto = new JdbcUsuarioRepository(new ConnectionFactory(banco().toString()));
        ServicoAutenticacao servico = new ServicoAutenticacao(reaberto, new HashSenha(1_000), RELOGIO);

        assertEquals(cadastrado, servico.autenticar("ana.souza", senha()));
        assertEquals(Optional.of(cadastrado), reaberto.buscarPorId(cadastrado.getId()));
        assertEquals(Optional.empty(), reaberto.buscarPorId(999));
        assertTrue(servico.nomeReservado("ANA"));
        assertTrue(servico.nomeReservado("Ana.Souza"));
        assertFalse(servico.nomeReservado("Caio"));
    }

    @Test
    void contasComAMesmaSenhaDevemTerSaisEHashesDistintos() throws Exception {
        ServicoAutenticacao servico = servico();
        servico.cadastrar("ana.souza", "Ana", senha());
        servico.cadastrar("bia.lima", "Bia", senha());

        List<List<String>> linhas = usuariosBrutos();
        assertNotEquals(linhas.get(0).get(3), linhas.get(1).get(3), "hash");
        assertNotEquals(linhas.get(0).get(4), linhas.get(1).get(4), "sal");
    }

    @Test
    void senhaNuncaDeveSerArmazenadaEmTextoPuro() throws Exception {
        servico().cadastrar("ana.souza", "Ana", senha());

        for (List<String> linha : usuariosBrutos()) {
            for (String valor : linha) {
                assertFalse(valor != null && valor.contains(SENHA), "Coluna com a senha");
            }
            assertTrue(linha.get(3).startsWith("pbkdf2-sha256$1000$"));
        }

        // Nem no arquivo do banco (inclusive como UTF-8 bruto).
        String bruto = new String(Files.readAllBytes(banco()), StandardCharsets.ISO_8859_1);
        assertFalse(bruto.contains(new String(SENHA.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
    }

    @Test
    void cadastrosSimultaneosDevemRespeitarALoginUnico() throws Exception {
        ServicoAutenticacao servico = servico();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<PerfilUsuario>> mesmoLogin = new ArrayList<>();
        List<Future<PerfilUsuario>> distintos = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            String variante = i % 2 == 0 ? "disputado" : "DISPUTADO";
            int n = i;
            mesmoLogin.add(executor.submit(() -> {
                largada.await();
                return servico.cadastrar(variante, "Disputa" + n, senha());
            }));
            distintos.add(executor.submit(() -> {
                largada.await();
                return servico.cadastrar("pessoa" + n, "Pessoa" + n, senha());
            }));
        }

        largada.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        int sucessos = 0;
        for (Future<PerfilUsuario> f : mesmoLogin) {
            try {
                f.get();
                sucessos++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertEquals(LOGIN_EM_USO, ((AutenticacaoException) e.getCause()).getCodigo());
            }
        }
        assertEquals(1, sucessos, "Somente um cadastro do login disputado");

        for (Future<PerfilUsuario> f : distintos) {
            assertNotNull(f.get());
        }

        assertEquals(9, usuariosBrutos().size());
    }

    /** Repositório que falha com dados sensíveis na mensagem da exceção. */
    private static final class RepositorioQuebrado implements UsuarioRepository {

        private static IllegalStateException falha() {
            return new IllegalStateException("falha interna com " + SENHA + " e hash pbkdf2-sha256$1$AAAA");
        }

        @Override
        public PerfilUsuario cadastrar(String l, String a, String h, String s, String av, LocalDateTime c) {
            throw falha();
        }

        @Override
        public Optional<ContaArmazenada> buscarPorLogin(String login) {
            throw falha();
        }

        @Override
        public Optional<PerfilUsuario> buscarPorId(long id) {
            throw falha();
        }

        @Override
        public boolean nomeEmUso(String nome) {
            throw falha();
        }
    }

    @Test
    void falhasInternasNaoDevemExporCredenciaisEmLogsNemMensagens() throws Exception {
        ServicoAutenticacao servico = new ServicoAutenticacao(new RepositorioQuebrado(), new HashSenha(1_000), RELOGIO);

        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        PrintStream erroOriginal = System.err;
        PrintStream saidaOriginal = System.out;
        PrintStream captura = new PrintStream(capturado, true, StandardCharsets.UTF_8);
        List<AutenticacaoException> erros = new ArrayList<>();

        try {
            System.setErr(captura);
            System.setOut(captura);
            erros.add(assertThrows(AutenticacaoException.class, () -> servico.cadastrar("ana", "Ana", senha())));
            erros.add(assertThrows(AutenticacaoException.class, () -> servico.autenticar("ana", senha())));
            assertTrue(servico.nomeReservado("Ana"), "Na falha, o nome não é liberado");
        } finally {
            System.setErr(erroOriginal);
            System.setOut(saidaOriginal);
        }

        String log = capturado.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("erro interno"), log);
        assertFalse(log.contains(SENHA));
        assertFalse(log.contains("pbkdf2"));

        for (AutenticacaoException e : erros) {
            assertEquals(INDISPONIVEL, e.getCodigo());
            assertEquals(ServicoAutenticacao.MENSAGEM_INDISPONIVEL, e.getMessage());
            assertNull(e.getCause(), "Nenhuma causa interna chega ao cliente");
        }
    }
}
