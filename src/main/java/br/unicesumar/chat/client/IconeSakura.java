package br.unicesumar.chat.client;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Flor de cerejeira (sakura) desenhada em código, sem arquivos de imagem.
 * Usada no cabeçalho, no diálogo de conexão e como ícone da janela.
 */
final class IconeSakura extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final int PETALAS = 5;

    IconeSakura(int tamanho) {
        Dimension dimensao = new Dimension(tamanho, tamanho);
        setPreferredSize(dimensao);
        setMinimumSize(dimensao);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            double raio = Math.min(getWidth(), getHeight()) / 2.0 - 1;
            desenhar(g2, getWidth() / 2.0, getHeight() / 2.0, raio);
        } finally {
            g2.dispose();
        }
    }

    static void desenhar(
            Graphics2D g2,
            double centroX,
            double centroY,
            double raio
    ) {

        double comprimento = raio * 0.95;
        double largura = raio * 0.66;

        // Pétala apontando para cima, com o entalhe típico da sakura.
        Area petala = new Area(new Ellipse2D.Double(
                -largura / 2, -comprimento, largura, comprimento
        ));

        double entalhe = largura * 0.24;
        petala.subtract(new Area(new Ellipse2D.Double(
                -entalhe, -comprimento - entalhe * 0.9,
                entalhe * 2, entalhe * 2
        )));

        AffineTransform original = g2.getTransform();

        g2.setStroke(new BasicStroke((float) Math.max(1.0, raio * 0.07)));

        for (int i = 0; i < PETALAS; i++) {

            g2.setTransform(original);
            g2.translate(centroX, centroY);
            g2.rotate(Math.toRadians(360.0 / PETALAS * i));

            g2.setColor(TemaSakura.ROSA_FLOR);
            g2.fill(petala);

            g2.setColor(TemaSakura.ROSA);
            g2.draw(petala);
        }

        g2.setTransform(original);

        double miolo = raio * 0.22;
        g2.setColor(TemaSakura.ROSA_ESCURO);
        g2.fill(new Ellipse2D.Double(
                centroX - miolo, centroY - miolo, miolo * 2, miolo * 2
        ));

        double polen = raio * 0.09;
        g2.setColor(TemaSakura.AMBAR);
        g2.fill(new Ellipse2D.Double(
                centroX - polen, centroY - polen, polen * 2, polen * 2
        ));
    }

    /** Ícones da janela em vários tamanhos, para a barra de tarefas. */
    static List<Image> imagensJanela() {

        List<Image> imagens = new ArrayList<>();

        for (int tamanho : new int[] {16, 24, 32, 48, 64}) {

            BufferedImage imagem = new BufferedImage(
                    tamanho, tamanho, BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D g2 = imagem.createGraphics();

            try {
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );
                desenhar(g2, tamanho / 2.0, tamanho / 2.0, tamanho / 2.0 - 1);
            } finally {
                g2.dispose();
            }

            imagens.add(imagem);
        }

        return imagens;
    }
}
