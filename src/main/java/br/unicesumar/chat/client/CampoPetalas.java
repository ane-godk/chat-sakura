package br.unicesumar.chat.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Movimento das pétalas que caem no fundo da conversa: queda lenta com
 * leve balanço lateral e rotação. Ao sair por baixo, a pétala volta ao
 * topo em uma posição aleatória. Lógica pura, sem Swing.
 */
final class CampoPetalas {

    record Petala(double x, double y, double tamanho, double angulo) {
    }

    private final Random aleatorio;
    private final int quantidade;

    private final double[] base;
    private final double[] y;
    private final double[] velocidade;
    private final double[] amplitude;
    private final double[] fase;
    private final double[] frequencia;
    private final double[] angulo;
    private final double[] giro;
    private final double[] tamanho;

    private int largura;
    private int altura;

    CampoPetalas(int quantidade, long semente) {
        this.quantidade = quantidade;
        this.aleatorio = new Random(semente);
        base = new double[quantidade];
        y = new double[quantidade];
        velocidade = new double[quantidade];
        amplitude = new double[quantidade];
        fase = new double[quantidade];
        frequencia = new double[quantidade];
        angulo = new double[quantidade];
        giro = new double[quantidade];
        tamanho = new double[quantidade];
    }

    /** Ajusta a área; na primeira vez, espalha as pétalas por ela. */
    void ajustarArea(int novaLargura, int novaAltura) {

        boolean primeira = largura == 0 || altura == 0;

        largura = Math.max(1, novaLargura);
        altura = Math.max(1, novaAltura);

        if (primeira) {
            for (int i = 0; i < quantidade; i++) {
                sortear(i, aleatorio.nextDouble() * altura);
            }
        }
    }

    private void sortear(int i, double inicioY) {
        tamanho[i] = 9 + aleatorio.nextDouble() * 6;
        base[i] = aleatorio.nextDouble() * largura;
        y[i] = inicioY;
        velocidade[i] = 12 + aleatorio.nextDouble() * 10;     // px por segundo
        amplitude[i] = 8 + aleatorio.nextDouble() * 16;
        fase[i] = aleatorio.nextDouble() * Math.PI * 2;
        frequencia[i] = 0.8 + aleatorio.nextDouble() * 0.8;   // radianos por segundo
        angulo[i] = aleatorio.nextDouble() * 360;
        giro[i] = (aleatorio.nextBoolean() ? 1 : -1) * (10 + aleatorio.nextDouble() * 20);
    }

    void avancar(long passoMs) {

        if (largura == 0) {
            return;
        }

        double segundos = Math.max(0, passoMs) / 1000.0;

        for (int i = 0; i < quantidade; i++) {
            y[i] += velocidade[i] * segundos;
            fase[i] += frequencia[i] * segundos;
            angulo[i] += giro[i] * segundos;

            if (y[i] - tamanho[i] > altura) {
                sortear(i, -tamanho[i]);
            }
        }
    }

    List<Petala> petalas() {

        List<Petala> lista = new ArrayList<>(quantidade);

        for (int i = 0; i < quantidade; i++) {
            double x = base[i] + Math.sin(fase[i]) * amplitude[i];
            lista.add(new Petala(x, y[i], tamanho[i], angulo[i]));
        }

        return lista;
    }

    int quantidade() {
        return quantidade;
    }
}
