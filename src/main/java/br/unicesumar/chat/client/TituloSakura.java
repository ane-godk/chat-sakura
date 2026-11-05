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

    private final transient java.util.function.Supplier<Color> cor;

    /** @param cor cor lida da paleta ativa a cada pintura */
    TituloSakura(String texto, java.awt.Font fonte, java.util.function.Supplier<Color> cor) {
        super(texto);
        this.cor = cor;
        setFont(fonte);
        setOpaque(false);
    }

    @Override
    public Color getForeground() {
        // Durante a construção do JLabel, o fornecedor ainda não existe.
        return cor == null ? super.getForeground() : cor.get();
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

            g2.setColor(TemaSakura.cores().haloTitulo());
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
