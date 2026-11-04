package br.unicesumar.chat.client;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

/**
 * Rótulo de título com um leve halo branco ao redor das letras,
 * que destaca o texto sobre a ilustração do cabeçalho.
 */
final class TituloSakura extends JLabel {

    private static final long serialVersionUID = 1L;

    private static final Color HALO = new Color(255, 255, 255, 170);

    TituloSakura(String texto, java.awt.Font fonte, Color cor) {
        super(texto);
        setFont(fonte);
        setForeground(cor);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            g2.setFont(getFont());

            FontMetrics metricas = g2.getFontMetrics();
            Insets margens = getInsets();

            int x = margens.left;
            int y = margens.top
                    + (getHeight() - margens.top - margens.bottom
                    - metricas.getHeight()) / 2
                    + metricas.getAscent();

            String texto = getText();

            g2.setColor(HALO);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx != 0 || dy != 0) {
                        g2.drawString(texto, x + dx, y + dy);
                    }
                }
            }

            g2.setColor(getForeground());
            g2.drawString(texto, x, y);

        } finally {
            g2.dispose();
        }
    }
}
