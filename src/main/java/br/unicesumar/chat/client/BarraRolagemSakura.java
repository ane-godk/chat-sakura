package br.unicesumar.chat.client;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

/** Barra de rolagem fina e arredondada, sem setas, no tema Sakura. */
final class BarraRolagemSakura extends BasicScrollBarUI {

    static final int LARGURA = 10;

    @Override
    protected void configureScrollBarColors() {
        thumbColor = TemaSakura.cores().rosaBorda();
        trackColor = TemaSakura.cores().cartao();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return botaoInvisivel();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return botaoInvisivel();
    }

    private static JButton botaoInvisivel() {
        JButton botao = new JButton();
        Dimension zero = new Dimension(0, 0);
        botao.setPreferredSize(zero);
        botao.setMinimumSize(zero);
        botao.setMaximumSize(zero);
        botao.setFocusable(false);
        return botao;
    }

    @Override
    public Dimension getPreferredSize(JComponent componente) {
        return new Dimension(LARGURA, LARGURA);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle area) {
        // Trilho transparente: o fundo da conversa aparece por trás.
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle area) {

        if (area.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            g2.setColor(isThumbRollover() || isDragging
                    ? TemaSakura.cores().rosaFlor()
                    : thumbColor);

            float margem = 2f;
            float largura = area.width - 2 * margem;

            g2.fill(new RoundRectangle2D.Float(
                    area.x + margem,
                    area.y + margem,
                    largura,
                    area.height - 2 * margem,
                    largura,
                    largura
            ));
        } finally {
            g2.dispose();
        }
    }
}
