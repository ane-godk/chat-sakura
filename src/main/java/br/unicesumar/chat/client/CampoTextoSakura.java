package br.unicesumar.chat.client;

import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Campo de texto arredondado do tema Sakura, com borda destacada
 * quando em foco e texto de orientação (placeholder) quando vazio.
 */
final class CampoTextoSakura extends JTextField {

    private static final long serialVersionUID = 1L;

    private final String placeholder;
    private final String placeholderDesabilitado;

    CampoTextoSakura(
            int colunas,
            String placeholder,
            String placeholderDesabilitado
    ) {
        super(colunas);
        this.placeholder = placeholder;
        this.placeholderDesabilitado = placeholderDesabilitado;

        setOpaque(false);
        setFont(TemaSakura.FONTE_TEXTO);
        setForeground(TemaSakura.TEXTO);
        setDisabledTextColor(TemaSakura.TEXTO_SUAVE);
        setCaretColor(TemaSakura.ROSA_ESCURO);
        setSelectionColor(TemaSakura.ROSA_PETALA);
        setSelectedTextColor(TemaSakura.TEXTO);
        setBorder(new EmptyBorder(9, 14, 9, 14));

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
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            g2.setColor(isEnabled()
                    ? TemaSakura.CARTAO
                    : TemaSakura.CAMPO_DESABILITADO);

            g2.fill(contorno());
        } finally {
            g2.dispose();
        }

        super.paintComponent(g);

        String orientacao = isEnabled() ? placeholder : placeholderDesabilitado;

        if (orientacao != null && getDocument().getLength() == 0) {

            Graphics2D gt = TemaSakura.suavizar(g);

            try {
                Insets margens = getInsets();
                FontMetrics metricas = gt.getFontMetrics(getFont());

                int y = (getHeight() - metricas.getHeight()) / 2
                        + metricas.getAscent();

                gt.setFont(getFont());
                gt.setColor(TemaSakura.TEXTO_SUAVE);
                gt.drawString(orientacao, margens.left, y);
            } finally {
                gt.dispose();
            }
        }
    }

    @Override
    protected void paintBorder(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            boolean emFoco = isFocusOwner() && isEnabled();

            g2.setStroke(new BasicStroke(emFoco ? 2f : 1.5f));
            g2.setColor(emFoco ? TemaSakura.ROSA : TemaSakura.ROSA_BORDA);
            g2.draw(contorno());
        } finally {
            g2.dispose();
        }
    }

    private RoundRectangle2D contorno() {
        float raio = TemaSakura.RAIO_CONTROLE;
        return new RoundRectangle2D.Float(
                1f, 1f, getWidth() - 3f, getHeight() - 3f, raio, raio
        );
    }
}
