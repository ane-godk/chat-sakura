package br.unicesumar.chat.service;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static br.unicesumar.chat.service.AutenticacaoException.Codigo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Limitação de tentativas por IP e login, bloqueio temporário e PBKDF2 concorrente (S4). */
class ProtecaoAutenticacaoTest {

    @TempDir
    Path pasta;

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-11T12:00:00Z"));

    private ServicoAutenticacao servico(ProtecaoAutenticacao protecao) throws Exception {
        ServicoAutenticacao servico = new ServicoAutenticacao(
                new JdbcUsuarioRepository(new ConnectionFactory(pasta.resolve("contas.db").toString())),
                new HashSenha(1_000), relogio, protecao);
        servico.cadastrar("ana.souza", "Ana", "senha-correta".toCharArray());
        servico.cadastrar("bia.lima", "Bia", "senha-da-bia".toCharArray());
        return servico;
    }

    private static AutenticacaoException.Codigo codigo(Executavel acao) {
        return assertThrows(AutenticacaoException.class, acao::executar).getCodigo();
    }

    @FunctionalInterface
    private interface Executavel {
        void executar() throws Exception;
    }

    @Test
    void limitadorDeveBloquearTemporariamenteEExpirar() {
        LimitadorTentativas limitador = new LimitadorTentativas(3, Duration.ofMinutes(5), Duration.ofMinutes(10), relogio);

        limitador.registrarFalha("x");
        limitador.registrarFalha("x");
        assertFalse(limitador.bloqueado("x"));
        limitador.registrarFalha("x");
        assertTrue(limitador.bloqueado("x"), "Terceira falha bloqueia");
        assertFalse(limitador.bloqueado("y"), "Outras chaves não são afetadas");

        relogio.avancar(Duration.ofMinutes(9));
        assertTrue(limitador.bloqueado("x"));
        relogio.avancar(Duration.ofMinutes(1));
        assertFalse(limitador.bloqueado("x"), "Bloqueio expira: nunca é permanente");

        // Falhas antigas saem da janela.
        limitador.registrarFalha("z");
        limitador.registrarFalha("z");
        relogio.avancar(Duration.ofMinutes(6));
        limitador.registrarFalha("z");
        assertFalse(limitador.bloqueado("z"));

        // Sucesso esquece as falhas.
        limitador.registrarFalha("w");
        limitador.registrarFalha("w");
        limitador.registrarSucesso("w");
        limitador.registrarFalha("w");
        assertFalse(limitador.bloqueado("w"));
    }

    @Test
    void limitadorNaoDeveCrescerSemLimite() {
        LimitadorTentativas limitador = new LimitadorTentativas(5, Duration.ofMinutes(1), Duration.ofMinutes(1),
                100, relogio);
        for (int i = 0; i < 5_000; i++) {
            limitador.registrarFalha("ip-" + i);
        }
        assertTrue(limitador.chavesAcompanhadas() <= 100, "Chaves: " + limitador.chavesAcompanhadas());

        relogio.avancar(Duration.ofMinutes(2));
        limitador.limpar();
        assertEquals(0, limitador.chavesAcompanhadas(), "Entradas vencidas são removidas");
    }

    @Test
    void ipComMuitasFalhasDeveSerBloqueadoMesmoComSenhaCorreta() throws Exception {
        ServicoAutenticacao servico = servico(new ProtecaoAutenticacao(relogio));

        for (int i = 0; i < ProtecaoAutenticacao.FALHAS_POR_IP; i++) {
            assertEquals(CREDENCIAIS_INVALIDAS,
                    codigo(() -> servico.autenticar("conta" + 1, "errada-123".toCharArray(), "10.0.0.9")));
        }

        assertEquals(MUITAS_TENTATIVAS,
                codigo(() -> servico.autenticar("ana.souza", "senha-correta".toCharArray(), "10.0.0.9")));
        // Outro endereço não é afetado.
        assertEquals("Ana", servico.autenticar("ana.souza", "senha-correta".toCharArray(), "10.0.0.8").getApelido());

        relogio.avancar(Duration.ofMinutes(5));
        assertEquals("Ana", servico.autenticar("ana.souza", "senha-correta".toCharArray(), "10.0.0.9").getApelido(),
                "Bloqueio por IP expira");
    }

    @Test
    void loginComMuitasFalhasDeveSerBloqueadoSemRevelarSeExiste() throws Exception {
        ServicoAutenticacao servico = servico(new ProtecaoAutenticacao(relogio));

        // Ataque distribuído (um IP por tentativa) contra uma conta existente e uma inexistente.
        for (int i = 0; i < ProtecaoAutenticacao.FALHAS_POR_LOGIN; i++) {
            String ip = "10.1.0." + i;
            assertEquals(CREDENCIAIS_INVALIDAS, codigo(() -> servico.autenticar("bia.lima", "errada-123".toCharArray(), ip)));
            assertEquals(CREDENCIAIS_INVALIDAS, codigo(() -> servico.autenticar("fantasma", "errada-123".toCharArray(), ip)));
        }

        AutenticacaoException existente = assertThrows(AutenticacaoException.class,
                () -> servico.autenticar("bia.lima", "senha-da-bia".toCharArray(), "10.2.0.1"));
        AutenticacaoException inexistente = assertThrows(AutenticacaoException.class,
                () -> servico.autenticar("fantasma", "senha-da-bia".toCharArray(), "10.2.0.1"));

        assertEquals(MUITAS_TENTATIVAS, existente.getCodigo());
        assertEquals(existente.getCodigo(), inexistente.getCodigo(), "Mesma resposta: não revela se o login existe");
        assertEquals(existente.getMessage(), inexistente.getMessage());

        // Outra conta segue funcionando; o bloqueio do login expira.
        assertEquals("Ana", servico.autenticar("ana.souza", "senha-correta".toCharArray(), "10.2.0.1").getApelido());
        relogio.avancar(Duration.ofMinutes(15));
        assertEquals("Bia", servico.autenticar("bia.lima", "senha-da-bia".toCharArray(), "10.2.0.1").getApelido());
    }

    @Test
    void umUnicoIpNaoConsegueBloquearAContaDeOutraPessoa() throws Exception {
        ServicoAutenticacao servico = servico(new ProtecaoAutenticacao(relogio));

        // O atacante esgota o próprio limite de IP antes de atingir o do login.
        for (int i = 0; i < ProtecaoAutenticacao.FALHAS_POR_LOGIN; i++) {
            servicoFalha(servico, "bia.lima", "10.9.9.9");
        }

        assertEquals("Bia", servico.autenticar("bia.lima", "senha-da-bia".toCharArray(), "10.0.0.2").getApelido(),
                "A dona da conta entra normalmente de outro endereço");
    }

    private static void servicoFalha(ServicoAutenticacao servico, String login, String ip) {
        assertThrows(AutenticacaoException.class, () -> servico.autenticar(login, "errada-123".toCharArray(), ip));
    }

    @Test
    void cadastrosDevemSerLimitadosPorIp() throws Exception {
        ServicoAutenticacao servico = servico(new ProtecaoAutenticacao(relogio));

        for (int i = 0; i < ProtecaoAutenticacao.CADASTROS_POR_IP; i++) {
            servico.cadastrar("nova" + i, "Nova" + i, "senha-segura".toCharArray(), "10.3.0.1");
        }
        assertEquals(MUITAS_TENTATIVAS,
                codigo(() -> servico.cadastrar("outra", "Outra", "senha-segura".toCharArray(), "10.3.0.1")));
        assertEquals("Outra", servico.cadastrar("outra", "Outra", "senha-segura".toCharArray(), "10.3.0.2").getApelido());
    }

    @Test
    void calculosPbkdf2SimultaneosDevemSerLimitados() throws Exception {
        LimitadorTentativas livre = new LimitadorTentativas(1_000, Duration.ofMinutes(1), Duration.ofMinutes(1), relogio);
        ProtecaoAutenticacao protecao = new ProtecaoAutenticacao(livre, livre, livre, 2, Duration.ofSeconds(10));

        int tarefas = 8;
        CountDownLatch dentro = new CountDownLatch(2);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicInteger concluidas = new AtomicInteger();

        ExecutorService threads = Executors.newFixedThreadPool(tarefas);
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < tarefas; i++) {
            futuros.add(threads.submit(() -> protecao.calcular(() -> {
                dentro.countDown();
                try {
                    assertTrue(liberar.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return concluidas.incrementAndGet();
            })));
        }

        assertTrue(dentro.await(5, TimeUnit.SECONDS), "Dois cálculos em andamento");
        assertEquals(0, concluidas.get());
        liberar.countDown();

        for (Future<?> futuro : futuros) {
            futuro.get(10, TimeUnit.SECONDS);
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(tarefas, concluidas.get(), "Todos terminam, em fila");
        assertEquals(2, protecao.picoDeCalculos(), "Nunca mais que o limite ao mesmo tempo");
    }

    @Test
    void semVagaParaCalcularDeveResponderIndisponivelSemTravar() throws Exception {
        LimitadorTentativas livre = new LimitadorTentativas(1_000, Duration.ofMinutes(1), Duration.ofMinutes(1), relogio);
        ProtecaoAutenticacao protecao = new ProtecaoAutenticacao(livre, livre, livre, 1, Duration.ofMillis(50));
        ServicoAutenticacao servico = servico(protecao);

        CountDownLatch ocupado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService thread = Executors.newSingleThreadExecutor();
        Future<?> ocupante = thread.submit(() -> protecao.calcular(() -> {
            ocupado.countDown();
            try {
                return liberar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }));
        assertTrue(ocupado.await(5, TimeUnit.SECONDS));

        char[] senha = "senha-correta".toCharArray();
        AutenticacaoException e = assertThrows(AutenticacaoException.class,
                () -> servico.autenticar("ana.souza", senha, "10.0.0.1"));
        assertEquals(INDISPONIVEL, e.getCodigo());
        assertArrayEquals(new char[senha.length], senha, "A senha é apagada mesmo sem vaga");

        liberar.countDown();
        ocupante.get(5, TimeUnit.SECONDS);
        thread.shutdown();
        assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS));
    }
}
