package br.unicesumar.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Proteções da autenticação contra força bruta e esgotamento de CPU.
 *
 * <ul>
 *   <li>Falhas de login por IP: {@value #FALHAS_POR_IP} em 5 min bloqueiam o IP por 5 min.</li>
 *   <li>Falhas por login normalizado (de qualquer IP): {@value #FALHAS_POR_LOGIN} em
 *       15 min bloqueiam aquele login por 15 min. O limite é maior que o do IP, então
 *       um único endereço não consegue bloquear a conta de outra pessoa.</li>
 *   <li>Cadastros por IP: {@value #CADASTROS_POR_IP} em 10 min.</li>
 *   <li>Cálculos PBKDF2 simultâneos limitados ao número de processadores
 *       (mínimo 2); quem não consegue vaga em 10 s recebe "indisponível".</li>
 * </ul>
 *
 * Todos os bloqueios são temporários. Nada aqui é registrado em log com senha.
 */
public final class ProtecaoAutenticacao {

    public static final int FALHAS_POR_IP = 10;
    public static final int FALHAS_POR_LOGIN = 20;
    public static final int CADASTROS_POR_IP = 5;

    private final LimitadorTentativas porIp;
    private final LimitadorTentativas porLogin;
    private final LimitadorTentativas cadastrosPorIp;

    private final Semaphore calculos;
    private final long esperaCalculoMs;
    private final AtomicInteger simultaneos = new AtomicInteger();
    private final AtomicInteger pico = new AtomicInteger();

    public ProtecaoAutenticacao(Clock relogio) {
        this(
                new LimitadorTentativas(FALHAS_POR_IP, Duration.ofMinutes(5), Duration.ofMinutes(5), relogio),
                new LimitadorTentativas(FALHAS_POR_LOGIN, Duration.ofMinutes(15), Duration.ofMinutes(15), relogio),
                new LimitadorTentativas(CADASTROS_POR_IP, Duration.ofMinutes(10), Duration.ofMinutes(10), relogio),
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                Duration.ofSeconds(10)
        );
    }

    ProtecaoAutenticacao(LimitadorTentativas porIp, LimitadorTentativas porLogin,
                         LimitadorTentativas cadastrosPorIp, int calculosSimultaneos, Duration esperaCalculo) {
        this.porIp = Objects.requireNonNull(porIp);
        this.porLogin = Objects.requireNonNull(porLogin);
        this.cadastrosPorIp = Objects.requireNonNull(cadastrosPorIp);
        if (calculosSimultaneos < 1) {
            throw new IllegalArgumentException("Limite de cálculos inválido.");
        }
        this.calculos = new Semaphore(calculosSimultaneos, true);
        this.esperaCalculoMs = esperaCalculo.toMillis();
    }

    /** Sinaliza que não há vaga para calcular o hash agora. */
    static final class SemVagaException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SemVagaException() {
            super("Sem vaga para cálculo de senha.", null, false, false);
        }
    }

    boolean loginBloqueado(String ip, String loginNormalizado) {
        return porIp.bloqueado(ip) || porLogin.bloqueado(loginNormalizado);
    }

    void falhaDeLogin(String ip, String loginNormalizado) {
        porIp.registrarFalha(ip);
        porLogin.registrarFalha(loginNormalizado);
    }

    void sucessoDeLogin(String loginNormalizado) {
        porLogin.registrarSucesso(loginNormalizado);
    }

    /** Conta a tentativa de cadastro do IP; {@code false} se o limite foi atingido. */
    boolean permitirCadastro(String ip) {
        if (ip == null) {
            return true;
        }
        if (cadastrosPorIp.bloqueado(ip) || porIp.bloqueado(ip)) {
            return false;
        }
        cadastrosPorIp.registrarFalha(ip);
        return true;
    }

    /**
     * Executa um cálculo PBKDF2 dentro do limite de simultâneos.
     *
     * @throws SemVagaException se não houver vaga no prazo
     */
    <T> T calcular(Supplier<T> calculo) {
        boolean obtido;
        try {
            obtido = calculos.tryAcquire(esperaCalculoMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SemVagaException();
        }
        if (!obtido) {
            throw new SemVagaException();
        }
        try {
            int agora = simultaneos.incrementAndGet();
            pico.accumulateAndGet(agora, Math::max);
            return calculo.get();
        } finally {
            simultaneos.decrementAndGet();
            calculos.release();
        }
    }

    /** Maior número de cálculos simultâneos já observado (diagnóstico e testes). */
    int picoDeCalculos() {
        return pico.get();
    }

    int chavesAcompanhadas() {
        return porIp.chavesAcompanhadas() + porLogin.chavesAcompanhadas() + cadastrosPorIp.chavesAcompanhadas();
    }
}
