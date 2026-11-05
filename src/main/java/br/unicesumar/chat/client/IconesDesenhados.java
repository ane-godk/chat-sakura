package br.unicesumar.chat.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/** Ícones vetoriais simples, desenhados em código. */
final class IconesDesenhados {

    private IconesDesenhados() {
    }

    /** Lua crescente (ativar o Sakura Noturno). */
    static void lua(Graphics2D g2, double x, double y, double tamanho, Color cor) {
        java.awt.geom.Area lua = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
                x + tamanho * 0.12, y + tamanho * 0.10, tamanho * 0.76, tamanho * 0.76));
        lua.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
                x + tamanho * 0.36, y + tamanho * 0.00, tamanho * 0.64, tamanho * 0.64)));
        g2.setColor(cor);
        g2.fill(lua);
    }

    /** Sol com raios (voltar ao Sakura Claro). */
    static void sol(Graphics2D g2, double x, double y, double tamanho, Color cor) {
        double centroX = x + tamanho / 2;
        double centroY = y + tamanho / 2;
        double raio = tamanho * 0.22;

        g2.setColor(cor);
        g2.fill(new java.awt.geom.Ellipse2D.Double(centroX - raio, centroY - raio, raio * 2, raio * 2));

        g2.setStroke(new BasicStroke((float) Math.max(1.2, tamanho * 0.09),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double angulo = Math.PI / 4 * i;
            double interno = tamanho * 0.33;
            double externo = tamanho * 0.47;
            g2.draw(new java.awt.geom.Line2D.Double(
                    centroX + Math.cos(angulo) * interno, centroY + Math.sin(angulo) * interno,
                    centroX + Math.cos(angulo) * externo, centroY + Math.sin(angulo) * externo));
        }
    }

    /** Alto-falante com ondas (sons ligados). */
    static void somLigado(Graphics2D g2, double x, double y, double tamanho, Color cor) {
        altoFalante(g2, x, y, tamanho, cor);
        g2.setStroke(new BasicStroke((float) Math.max(1.2, tamanho * 0.09),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new java.awt.geom.Arc2D.Double(
                x + tamanho * 0.40, y + tamanho * 0.30, tamanho * 0.30, tamanho * 0.40,
                -50, 100, java.awt.geom.Arc2D.OPEN));
        g2.draw(new java.awt.geom.Arc2D.Double(
                x + tamanho * 0.36, y + tamanho * 0.14, tamanho * 0.58, tamanho * 0.72,
                -50, 100, java.awt.geom.Arc2D.OPEN));
    }

    /** Alto-falante com um "x" (sons silenciados). */
    static void somSilenciado(Graphics2D g2, double x, double y, double tamanho, Color cor) {
        altoFalante(g2, x, y, tamanho, cor);
        g2.setStroke(new BasicStroke((float) Math.max(1.2, tamanho * 0.09),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new java.awt.geom.Line2D.Double(
                x + tamanho * 0.66, y + tamanho * 0.36, x + tamanho * 0.94, y + tamanho * 0.64));
        g2.draw(new java.awt.geom.Line2D.Double(
                x + tamanho * 0.94, y + tamanho * 0.36, x + tamanho * 0.66, y + tamanho * 0.64));
    }

    private static void altoFalante(Graphics2D g2, double x, double y, double tamanho, Color cor) {
        Path2D.Double corpo = new Path2D.Double();
        corpo.moveTo(x + tamanho * 0.06, y + tamanho * 0.36);
        corpo.lineTo(x + tamanho * 0.24, y + tamanho * 0.36);
        corpo.lineTo(x + tamanho * 0.48, y + tamanho * 0.14);
        corpo.lineTo(x + tamanho * 0.48, y + tamanho * 0.86);
        corpo.lineTo(x + tamanho * 0.24, y + tamanho * 0.64);
        corpo.lineTo(x + tamanho * 0.06, y + tamanho * 0.64);
        corpo.closePath();
        g2.setColor(cor);
        g2.fill(corpo);
    }

    /** Avião de papel apontando para cima e para a direita. */
    static void aviaoDePapel(
            Graphics2D g2,
            double x,
            double y,
            double tamanho,
            Color cor
    ) {

        Path2D.Double corpo = new Path2D.Double();
        corpo.moveTo(x + tamanho * 0.02, y + tamanho * 0.48);
        corpo.lineTo(x + tamanho * 0.98, y + tamanho * 0.04);
        corpo.lineTo(x + tamanho * 0.62, y + tamanho * 0.96);
        corpo.lineTo(x + tamanho * 0.44, y + tamanho * 0.62);
        corpo.closePath();

        g2.setColor(cor);
        g2.fill(corpo);

        // Dobra do papel, em um tom mais escuro e translúcido da própria cor.
        Path2D.Double dobra = new Path2D.Double();
        dobra.moveTo(x + tamanho * 0.44, y + tamanho * 0.62);
        dobra.lineTo(x + tamanho * 0.98, y + tamanho * 0.04);

        g2.setStroke(new BasicStroke(
                (float) Math.max(1.0, tamanho * 0.09),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));
        g2.setColor(TemaSakura.cores().dobraAviao());
        g2.draw(dobra);
    }
}
