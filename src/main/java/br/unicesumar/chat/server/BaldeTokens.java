package br.unicesumar.chat.server;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Token bucket simples e previsível: até {@code capacidade} fichas, repostas
 * continuamente a {@code fichasPorSegundo}. {@link #consumir()} nunca espera:
 * sem ficha, a operação é recusada na hora. Usado por uma única sessão.
 */
final class BaldeTokens {

    private final double capacidade;
    private final double fichasPorNano;
    private final LongSupplier relogioNanos;

    private double fichas;
    private long ultimaReposicao;

    BaldeTokens(int capacidade, double fichasPorSegundo, LongSupplier relogioNanos) {
        if (capacidade < 1 || fichasPorSegundo <= 0) {
            throw new IllegalArgumentException("Balde inválido.");
        }
        this.capacidade = capacidade;
        this.fichasPorNano = fichasPorSegundo / 1_000_000_000.0;
        this.relogioNanos = Objects.requireNonNull(relogioNanos);
        this.fichas = capacidade;
        this.ultimaReposicao = relogioNanos.getAsLong();
    }

    synchronized boolean consumir() {
        long agora = relogioNanos.getAsLong();
        long decorrido = agora - ultimaReposicao;
        if (decorrido > 0) {
            fichas = Math.min(capacidade, fichas + decorrido * fichasPorNano);
            ultimaReposicao = agora;
        }
        if (fichas >= 1) {
            fichas -= 1;
            return true;
        }
        return false;
    }
}
