package br.unicesumar.chat.server;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Fila de saída limitada de uma sessão, com um único escritor.
 *
 * Quem produz (broadcasts, a própria sessão) apenas enfileira, sem I/O e sem
 * esperar: nenhum bloqueio compartilhado fica preso a um socket lento. A
 * thread escritora grava na ordem de chegada. Se a fila encher (cliente que
 * não lê), {@code aoTransbordar} é chamado uma única vez para derrubar a
 * sessão. Falha de escrita chama {@code aoFalhar}.
 */
final class FilaSaida {

    /** Marcador de fim (comparado por identidade). */
    private static final String FIM = new String("FIM");

    private final int capacidade;
    // Uma posição extra garante espaço para o marcador de fim.
    private final BlockingQueue<String> fila;
    private final Writer destino;
    private final Runnable aoTransbordar;
    private final Runnable aoFalhar;
    private final Thread escritor;
    private volatile Runnable aoConcluir = () -> { };

    private boolean encerrada;
    private boolean transbordou;

    /** Executado pelo escritor depois de gravar tudo e encontrar o fim. */
    void aoConcluir(Runnable acao) {
        this.aoConcluir = Objects.requireNonNull(acao);
    }

    FilaSaida(int capacidade, Writer destino, String nome, Runnable aoTransbordar, Runnable aoFalhar) {
        if (capacidade < 1) {
            throw new IllegalArgumentException("Capacidade inválida.");
        }
        this.capacidade = capacidade;
        this.fila = new ArrayBlockingQueue<>(capacidade + 1);
        this.destino = Objects.requireNonNull(destino);
        this.aoTransbordar = Objects.requireNonNull(aoTransbordar);
        this.aoFalhar = Objects.requireNonNull(aoFalhar);
        this.escritor = new Thread(this::escrever, nome);
        this.escritor.setDaemon(true);
    }

    void iniciar() {
        escritor.start();
    }

    /**
     * Enfileira sem bloquear.
     *
     * @return {@code false} se a fila está encerrada ou cheia (neste caso a
     *         sessão é derrubada por {@code aoTransbordar})
     */
    boolean enfileirar(String linha) {
        Objects.requireNonNull(linha);
        boolean chamarTransbordo;
        synchronized (this) {
            if (encerrada) {
                return false;
            }
            if (fila.size() < capacidade && fila.offer(linha)) {
                return true;
            }
            encerrada = true;
            chamarTransbordo = !transbordou;
            transbordou = true;
            fila.offer(FIM);
        }
        if (chamarTransbordo) {
            aoTransbordar.run();
        }
        return false;
    }

    /** Não aceita novas linhas; as já enfileiradas ainda são gravadas. */
    synchronized void encerrar() {
        if (!encerrada) {
            encerrada = true;
            fila.offer(FIM);
        }
    }

    synchronized boolean transbordou() {
        return transbordou;
    }

    int pendentes() {
        return fila.size();
    }

    /** @return {@code true} se o escritor terminou dentro do prazo */
    boolean aguardar(long milissegundos) throws InterruptedException {
        if (escritor.getState() == Thread.State.NEW) {
            return true;
        }
        escritor.join(Math.max(1, milissegundos));
        return !escritor.isAlive();
    }

    private void escrever() {
        try {
            while (true) {
                String linha = fila.take();
                if (linha == FIM) {
                    break;
                }
                destino.write(linha);
                destino.write(System.lineSeparator());
                // Agrupa gravações: só descarrega quando não há mais linhas.
                if (fila.isEmpty()) {
                    destino.flush();
                }
            }
            destino.flush();
            aoConcluir.run();
        } catch (IOException e) {
            synchronized (this) {
                encerrada = true;
            }
            fila.clear();
            aoFalhar.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
