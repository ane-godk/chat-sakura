package br.unicesumar.chat.client;

import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Botão arredondado do tema Sakura, com estados normal, hover,
 * pressionado, desabilitado e anel de foco para navegação por teclado.
 */
final class BotaoSakura extends JButton {

    private static final long serialVersionUID = 1L;

    /** Espaço reservado ao redor do botão para o anel de foco. */
    private static final int MARGEM_FOCO = 3;

    enum Estilo {
        PRINCIPAL,
        SECUNDARIO
    }

    private final Estilo estilo;

    BotaoSakura(String texto, Estilo estilo) {
        super(texto);
        this.estilo = estilo;

        setFont(TemaSakura.FONTE_BOTAO);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(8, 16, 8, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension tamanho = super.getPreferredSize();
        return new Dimension(
                tamanho.width + 2 * MARGEM_FOCO,
                tamanho.height + 2 * MARGEM_FOCO
        );
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            Cores cores = coresDoEstado();

            float raio = TemaSakura.RAIO_CONTROLE;
            float largura = getWidth() - 2f * MARGEM_FOCO - 1f;
            float altura = getHeight() - 2f * MARGEM_FOCO - 1f;

            RoundRectangle2D corpo = new RoundRectangle2D.Float(
                    MARGEM_FOCO, MARGEM_FOCO, largura, altura, raio, raio
            );

            g2.setColor(cores.fundo());
            g2.fill(corpo);

            if (cores.borda() != null) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(cores.borda());
                g2.draw(corpo);
            }

            if (isFocusOwner() && isEnabled()) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(TemaSakura.ROSA);
                g2.draw(new RoundRectangle2D.Float(
                        1f,
                        1f,
                        getWidth() - 3f,
                        getHeight() - 3f,
                        raio + 4f,
                        raio + 4f
                ));
            }

            g2.setFont(getFont());
            g2.setColor(cores.texto());

            FontMetrics metricas = g2.getFontMetrics();
            String texto = getText();

            int x = (getWidth() - metricas.stringWidth(texto)) / 2;
            int y = (getHeight() - metricas.getHeight()) / 2
                    + metricas.getAscent();

            g2.drawString(texto, x, y);

        } finally {
            g2.dispose();
        }
    }

    private Cores coresDoEstado() {

        ButtonModel modelo = getModel();

        boolean principal = estilo == Estilo.PRINCIPAL;

        if (!isEnabled()) {
            return principal
                    ? new Cores(TemaSakura.ROSA_PETALA, null,
                            TemaSakura.TEXTO_SUAVE)
                    : new Cores(TemaSakura.CARTAO, TemaSakura.ROSA_BORDA,
                            TemaSakura.TEXTO_SUAVE);
        }

        if (modelo.isPressed() && modelo.isArmed()) {
            return principal
                    ? new Cores(TemaSakura.ROSA_PRESSIONADO, null, Color.WHITE)
                    : new Cores(TemaSakura.ROSA_PETALA, TemaSakura.ROSA_ESCURO,
                            TemaSakura.ROSA_PRESSIONADO);
        }

        if (modelo.isRollover()) {
            return principal
                    ? new Cores(TemaSakura.ROSA_HOVER, null, Color.WHITE)
                    : new Cores(TemaSakura.ROSA_HOVER_CLARO,
                            TemaSakura.ROSA_ESCURO, TemaSakura.ROSA_HOVER);
        }

        return principal
                ? new Cores(TemaSakura.ROSA_ESCURO, null, Color.WHITE)
                : new Cores(TemaSakura.CARTAO, TemaSakura.ROSA,
                        TemaSakura.ROSA_ESCURO);
    }

    private record Cores(Color fundo, Color borda, Color texto) {
    }
}
