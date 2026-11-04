package br.unicesumar.chat.client;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Selo de estado da conexão: pílula colorida com um ponto indicador
 * (verde, âmbar ou coral) seguido do texto do estado.
 */
final class IndicadorConexao extends JLabel {

    private static final long serialVersionUID = 1L;

    private static final int DIAMETRO_PONTO = 8;
    private static final int MARGEM = 12;

    enum Estado {
        CONECTADO(TemaSakura.VERDE, TemaSakura.VERDE_CLARO),
        CONECTANDO(TemaSakura.AMBAR, TemaSakura.AMBAR_CLARO),
        DESCONECTADO(TemaSakura.CORAL, TemaSakura.CORAL_CLARO);

        private final Color ponto;
        private final Color fundo;

        Estado(Color ponto, Color fundo) {
            this.ponto = ponto;
            this.fundo = fundo;
        }
    }

    private Estado estado = Estado.DESCONECTADO;

    IndicadorConexao() {
        super("Desconectado");
        setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        setForeground(TemaSakura.TEXTO);
        setOpaque(false);
        setBorder(new EmptyBorder(
                5, MARGEM + DIAMETRO_PONTO + 7, 5, MARGEM
        ));
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
            float altura = getHeight() - 1f;

            g2.setColor(estado.fundo);
            g2.fill(new RoundRectangle2D.Float(
                    0, 0, getWidth() - 1f, altura, altura, altura
            ));

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
