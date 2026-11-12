package br.unicesumar.chat.server;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Vagas de conexão: limite global e por endereço IP. Cada reserva aceita
 * deve ser liberada exatamente uma vez, quando a sessão termina.
 */
final class ControleConexoes {

    private final int maximo;
    private final int maximoPorIp;

    private final Map<String, Integer> porIp = new HashMap<>();
    private int ocupadas;

    ControleConexoes(int maximo, int maximoPorIp) {
        if (maximo < 1 || maximoPorIp < 1) {
            throw new IllegalArgumentException("Limites inválidos.");
        }
        this.maximo = maximo;
        this.maximoPorIp = maximoPorIp;
    }

    /** @return {@code true} se havia vaga (global e para o IP) */
    synchronized boolean reservar(String ip) {
        int doIp = porIp.getOrDefault(ip, 0);
        if (ocupadas >= maximo || doIp >= maximoPorIp) {
            return false;
        }
        ocupadas++;
        porIp.put(ip, doIp + 1);
        return true;
    }

    synchronized void liberar(String ip) {
        Integer doIp = porIp.get(ip);
        if (doIp == null) {
            return;
        }
        if (doIp <= 1) {
            porIp.remove(ip);
        } else {
            porIp.put(ip, doIp - 1);
        }
        ocupadas--;
        notifyAll();
    }

    synchronized int ocupadas() {
        return ocupadas;
    }

    synchronized int ocupadas(String ip) {
        return porIp.getOrDefault(ip, 0);
    }

    /** Espera (sem sondagem) até haver no máximo {@code limite} vagas ocupadas. */
    synchronized boolean aguardarOcupadas(int limite, Duration prazo) throws InterruptedException {
        long fim = System.nanoTime() + prazo.toNanos();
        while (ocupadas > limite) {
            long restante = fim - System.nanoTime();
            if (restante <= 0) {
                return false;
            }
            wait(Math.max(1, restante / 1_000_000));
        }
        return true;
    }
}
