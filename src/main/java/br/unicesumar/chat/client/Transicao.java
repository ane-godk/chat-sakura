package br.unicesumar.chat.client;

import java.util.function.BooleanSupplier;

/**
 * Valor que se aproxima suavemente de um alvo em um tempo fixo
 * (aceleração e desaceleração suaves). O tempo é informado pelo chamador,
 * em milissegundos, o que permite testar sem relógio real.
 *
 * Com as animações desativadas, o valor vai direto para o alvo.
 */
final class Transicao {

    private final double duracaoMs;
    private final BooleanSupplier ativa;

    private double valorInicial;
    private double alvo;
    private long inicio;
    private boolean animando;

    Transicao(double valor, double duracaoMs) {
        this(valor, duracaoMs, ConfiguracaoAnimacoes::transicoesAtivas);
    }

    Transicao(double valor, double duracaoMs, BooleanSupplier ativa) {
        this.valorInicial = valor;
        this.alvo = valor;
        this.duracaoMs = duracaoMs;
        this.ativa = ativa;
    }

    /** Inicia a transição a partir do valor atual (sem saltos). */
    void irPara(double novoAlvo, long agora) {

        if (novoAlvo == alvo) {
            return;
        }

        valorInicial = valor(agora);
        alvo = novoAlvo;
        inicio = agora;
        animando = duracaoMs > 0 && ativa.getAsBoolean();
    }

    /** Define o valor imediatamente, sem animação. */
    void definir(double valor) {
        valorInicial = valor;
        alvo = valor;
        animando = false;
    }

    double valor(long agora) {

        if (!animando) {
            return alvo;
        }

        double t = (agora - inicio) / duracaoMs;

        if (t >= 1) {
            animando = false;
            return alvo;
        }

        return valorInicial + (alvo - valorInicial) * suavizar(Math.max(0, t));
    }

    boolean emAndamento(long agora) {
        valor(agora);
        return animando;
    }

    double alvo() {
        return alvo;
    }

    /** Curva suave entre 0 e 1 (smoothstep). */
    static double suavizar(double t) {
        return t * t * (3 - 2 * t);
    }

    static long agora() {
        return System.nanoTime() / 1_000_000L;
    }
}
