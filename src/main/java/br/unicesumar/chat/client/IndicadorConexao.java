package br.unicesumar.chat.client;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Selo de estado da conexão: pílula branca translúcida com borda rosa,
 * um ponto indicador (verde, âmbar ou coral) e o texto do estado.
 */
final class IndicadorConexao extends JLabel {

    private static final long serialVersionUID = 1L;

    private static final int DIAMETRO_PONTO = 10;
    private static final int MARGEM = 14;

    enum Estado {
        CONECTADO(TemaSakura.VERDE),
        CONECTANDO(TemaSakura.AMBAR),
        DESCONECTADO(TemaSakura.CORAL);

        private final Color ponto;

        Estado(Color ponto) {
            this.ponto = ponto;
        }
    }

    private Estado estado = Estado.DESCONECTADO;

    private int larguraMaxima = Integer.MAX_VALUE;

    IndicadorConexao() {
        super("Desconectado");
        setFont(TemaSakura.FONTE_STATUS);
        setForeground(TemaSakura.TEXTO);
        setOpaque(false);
        setBorder(new EmptyBorder(
                6, MARGEM + DIAMETRO_PONTO + 8, 6, MARGEM
        ));
    }

    /**
     * Limita a largura do selo; textos maiores são abreviados com "…"
     * e aparecem completos na dica (tooltip).
     */
    void definirLarguraMaxima(int largura) {
        this.larguraMaxima = largura;
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension tamanho = super.getPreferredSize();
        return new Dimension(
                Math.min(tamanho.width, larguraMaxima),
                tamanho.height
        );
    }

    void mostrar(Estado novoEstado, String texto) {
        this.estado = novoEstado;
        setText(texto);
        setToolTipText(texto);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            float altura = getHeight() - 1.5f;
            RoundRectangle2D pilula = new RoundRectangle2D.Float(
                    0.75f, 0.75f, getWidth() - 1.5f, altura, altura, altura
            );

            g2.setColor(TemaSakura.BRANCO_TRANSLUCIDO);
            g2.fill(pilula);

            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(TemaSakura.ROSA_BORDA);
            g2.draw(pilula);

            double y = (getHeight() - DIAMETRO_PONTO) / 2.0;
            g2.setColor(estado.ponto);
            g2.fill(new Ellipse2D.Double(
                    MARGEM, y, DIAMETRO_PONTO, DIAMETRO_PONTO
            ));
        } finally {
            g2.dispose();
        }

        super.paintComponent(g);
    }
}
