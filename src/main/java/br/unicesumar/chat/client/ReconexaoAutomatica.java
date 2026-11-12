package br.unicesumar.chat.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Espera o servidor voltar depois de uma queda, com backoff exponencial
 * (1 s, 2 s, 4 s... até {@value #MAXIMO_PADRAO_MS} ms entre tentativas).
 *
 * Só verifica se o servidor aceita conexões (uma sonda TCP que abre e fecha
 * o socket); não autentica e não guarda senha. Quando o servidor responde,
 * avisa o ouvinte, que pede o login de novo ao usuário. Uma única
 * reconexão por vez; pode ser cancelada a qualquer momento. As tentativas
 * rodam numa thread própria, nunca na EDT.
 */
final class ReconexaoAutomatica {

    static final long INICIAL_PADRAO_MS = 1_000;
    static final long MAXIMO_PADRAO_MS = 30_000;
    static final int TEMPO_SONDA_MS = 2_000;

    /** Verifica se o servidor aceita conexões. */
    @FunctionalInterface
    interface Sonda {
        boolean alcancavel(String endereco, int porta);
    }

    /** Chamado na thread de reconexão (quem atualiza a interface repassa à EDT). */
    interface Ouvinte {

        /** Tentativa {@code numero} falhou; a próxima ocorre em {@code proximaEmMs}. */
        void tentativaFalhou(int numero, long proximaEmMs);

        /** O servidor voltou a aceitar conexões (a reconexão terminou). */
        void servidorDisponivel();
    }

    static final Sonda SONDA_TCP = (endereco, porta) -> {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endereco, porta), TEMPO_SONDA_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    };

    private final ScheduledExecutorService agendador;
    private final Sonda sonda;
    private final Ouvinte ouvinte;
    private final long inicialMs;
    private final long maximoMs;

    // Protegidos por this.
    private boolean ativa;
    private int tentativas;
    private long geracao;
    private ScheduledFuture<?> agendada;

    ReconexaoAutomatica(Ouvinte ouvinte) {
        this(ouvinte, SONDA_TCP, INICIAL_PADRAO_MS, MAXIMO_PADRAO_MS);
    }

    ReconexaoAutomatica(Ouvinte ouvinte, Sonda sonda, long inicialMs, long maximoMs) {
        if (inicialMs < 1 || maximoMs < inicialMs) {
            throw new IllegalArgumentException("Intervalos inválidos.");
        }
        this.ouvinte = Objects.requireNonNull(ouvinte);
        this.sonda = Objects.requireNonNull(sonda);
        this.inicialMs = inicialMs;
        this.maximoMs = maximoMs;
        this.agendador = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread thread = new Thread(tarefa, "sakura-reconexao");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Intervalo antes da tentativa seguinte à {@code tentativa}-ésima falha. */
    static long atraso(int tentativa, long inicialMs, long maximoMs) {
        int expoente = Math.max(0, Math.min(tentativa, 20));
        return Math.min(maximoMs, inicialMs << expoente);
    }

    /**
     * Começa a esperar pelo servidor; a primeira tentativa ocorre após o
     * intervalo inicial.
     *
     * @return {@code false} se já há uma reconexão em andamento
     */
    synchronized boolean iniciar(String endereco, int porta) {
        if (ativa) {
            return false;
        }
        ativa = true;
        tentativas = 0;
        long minha = ++geracao;
        agendada = agendador.schedule(() -> tentar(minha, endereco, porta), inicialMs, TimeUnit.MILLISECONDS);
        return true;
    }

    synchronized void cancelar() {
        ativa = false;
        geracao++;
        if (agendada != null) {
            agendada.cancel(false);
            agendada = null;
        }
    }

    synchronized boolean ativa() {
        return ativa;
    }

    synchronized int tentativas() {
        return tentativas;
    }

    void fechar() {
        cancelar();
        agendador.shutdownNow();
    }

    private void tentar(long minha, String endereco, int porta) {

        synchronized (this) {
            if (!ativa || minha != geracao) {
                return;
            }
        }

        boolean disponivel = sonda.alcancavel(endereco, porta);

        long proxima;
        int numero;

        synchronized (this) {
            // Cancelada (ou reiniciada) durante a sonda: nada a fazer.
            if (!ativa || minha != geracao) {
                return;
            }
            numero = ++tentativas;
            if (disponivel) {
                ativa = false;
                agendada = null;
                proxima = -1;
            } else {
                proxima = atraso(numero, inicialMs, maximoMs);
                agendada = agendador.schedule(() -> tentar(minha, endereco, porta), proxima, TimeUnit.MILLISECONDS);
            }
        }

        if (disponivel) {
            ouvinte.servidorDisponivel();
        } else {
            ouvinte.tentativaFalhou(numero, proxima);
        }
    }
}
