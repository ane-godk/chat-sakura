package br.unicesumar.chat.client;

import java.util.ArrayDeque;

/**
 * Limita quantas entradas podem ser animadas em uma janela de tempo.
 * Em rajadas de mensagens, as excedentes aparecem sem animação.
 */
final class LimitadorRajada {

    private final int maximo;
    private final long janelaMs;
    private final ArrayDeque<Long> recentes = new ArrayDeque<>();

    LimitadorRajada(int maximo, long janelaMs) {
        this.maximo = maximo;
        this.janelaMs = janelaMs;
    }

    boolean permitir(long agora) {

        while (!recentes.isEmpty() && agora - recentes.peekFirst() >= janelaMs) {
            recentes.pollFirst();
        }

        if (recentes.size() >= maximo) {
            return false;
        }

        recentes.addLast(agora);
        return true;
    }

    void limpar() {
        recentes.clear();
    }
}
