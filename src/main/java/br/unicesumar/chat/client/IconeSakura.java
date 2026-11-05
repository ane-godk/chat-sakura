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
 * Flor de cerejeira (sakura) usada no cabeçalho, no diálogo de conexão
 * e como ícone da janela. Usa a imagem interna {@code flor.png}; se ela
 * não estiver disponível, desenha a flor em código.
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
            int lado = Math.min(getWidth(), getHeight());
            int x = (getWidth() - lado) / 2;
            int y = (getHeight() - lado) / 2;

            BufferedImage flor = florNoTamanho(
                    lado, g2.getTransform().getScaleX()
            );

            if (flor != null) {
                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                g2.drawImage(flor, x, y, lado, lado, null);
            } else {
                desenhar(g2, getWidth() / 2.0, getHeight() / 2.0, lado / 2.0 - 1);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Flor do tema ativo, já no tamanho em pixels da tela (em cache). */
    private static BufferedImage florNoTamanho(int lado, double escala) {
        if (lado <= 0) {
            return null;
        }
        return RecursosSakura.florEmPixels((int) Math.ceil(lado * escala)).orElse(null);
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

            g2.setColor(TemaSakura.cores().rosaFlor());
            g2.fill(petala);

            g2.setColor(TemaSakura.cores().rosa());
            g2.draw(petala);
        }

        g2.setTransform(original);

        double miolo = raio * 0.22;
        g2.setColor(TemaSakura.cores().rosaEscuro());
        g2.fill(new Ellipse2D.Double(
                centroX - miolo, centroY - miolo, miolo * 2, miolo * 2
        ));

        double polen = raio * 0.09;
        g2.setColor(TemaSakura.cores().ambar());
        g2.fill(new Ellipse2D.Double(
                centroX - polen, centroY - polen, polen * 2, polen * 2
        ));
    }

    /** Ícones da janela em vários tamanhos, para a barra de tarefas. */
    static List<Image> imagensJanela() {

        List<Image> imagens = new ArrayList<>();

        BufferedImage flor = RecursosSakura.flor().orElse(null);

        for (int tamanho : new int[] {16, 24, 32, 48, 64, 128}) {

            if (flor != null) {
                imagens.add(RecursosSakura.redimensionar(flor, tamanho, tamanho));
                continue;
            }

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
