package br.unicesumar.chat.client;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Function;

/**
 * Painel com cantos arredondados, fundo liso ou em degradê horizontal
 * e borda opcional.
 *
 * As cores são papéis da paleta, lidos a cada pintura: o painel acompanha
 * a troca de tema sem precisar ser recriado.
 */
final class PainelArredondado extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Function<PaletaSakura, Color> corInicio;
    private final transient Function<PaletaSakura, Color> corFim;
    private final transient Function<PaletaSakura, Color> corBorda;
    private final int raio;

    /**
     * @param corFim   cor final do degradê, ou {@code null} para fundo liso
     * @param corBorda cor da borda, ou {@code null} para nenhuma
     */
    PainelArredondado(
            LayoutManager layout,
            Function<PaletaSakura, Color> corInicio,
            Function<PaletaSakura, Color> corFim,
            Function<PaletaSakura, Color> corBorda,
            int raio
    ) {
        super(layout);
        this.corInicio = corInicio;
        this.corFim = corFim;
        this.corBorda = corBorda;
        this.raio = raio;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        PaletaSakura cores = TemaSakura.cores();
        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            RoundRectangle2D forma = new RoundRectangle2D.Float(
                    0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f,
                    raio, raio
            );

            g2.setPaint(corFim == null
                    ? corInicio.apply(cores)
                    : new GradientPaint(0, 0, corInicio.apply(cores),
                            getWidth(), 0, corFim.apply(cores)));
            g2.fill(forma);

            if (corBorda != null) {
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(corBorda.apply(cores));
                g2.draw(forma);
            }
        } finally {
            g2.dispose();
        }
    }
}
