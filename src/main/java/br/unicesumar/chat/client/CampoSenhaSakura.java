package br.unicesumar.chat.client;

import javax.swing.JPasswordField;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Campo de senha com a mesma aparência do {@link CampoTextoSakura}. A
 * senha é lida como {@code char[]} ({@link #getPassword()}) e o campo deve
 * ser limpo logo após o uso; ela nunca é exibida.
 */
final class CampoSenhaSakura extends JPasswordField {

    private static final long serialVersionUID = 1L;

    private static final float RECUO = 3f;

    private final String placeholder;

    CampoSenhaSakura(int colunas, String placeholder) {
        super(colunas);
        this.placeholder = placeholder;

        setOpaque(false);
        setFont(TemaSakura.FONTE_TEXTO);
        setEchoChar('•');
        setBorder(new EmptyBorder(11, 16, 11, 16));

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
    public Color getForeground() {
        return TemaSakura.cores().texto();
    }

    @Override
    public Color getDisabledTextColor() {
        return TemaSakura.cores().textoSuave();
    }

    @Override
    public Color getCaretColor() {
        return TemaSakura.cores().rosaEscuro();
    }

    @Override
    public Color getSelectionColor() {
        return TemaSakura.cores().rosaPetala();
    }

    @Override
    public Color getSelectedTextColor() {
        return TemaSakura.cores().texto();
    }

    /** Apaga o conteúdo do campo. */
    void limpar() {
        setText("");
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            g2.setColor(isEnabled()
                    ? TemaSakura.cores().cartao()
                    : TemaSakura.cores().campoDesabilitado());
            g2.fill(contorno());
        } finally {
            g2.dispose();
        }

        super.paintComponent(g);

        if (placeholder != null && getDocument().getLength() == 0) {

            Graphics2D gt = TemaSakura.suavizar(g);

            try {
                Insets margens = getInsets();
                FontMetrics metricas = gt.getFontMetrics(getFont());
                int y = (getHeight() - metricas.getHeight()) / 2 + metricas.getAscent();

                gt.setFont(getFont());
                gt.setColor(TemaSakura.cores().textoSuave());
                gt.drawString(placeholder, margens.left, y);
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

            if (emFoco) {
                g2.setStroke(new BasicStroke(5f));
                g2.setColor(TemaSakura.cores().brilhoFocoCampo());
                g2.draw(contorno());
            }

            g2.setStroke(new BasicStroke(emFoco ? 2f : 1.5f));
            g2.setColor(emFoco ? TemaSakura.cores().rosa() : TemaSakura.cores().rosaBorda());
            g2.draw(contorno());
        } finally {
            g2.dispose();
        }
    }

    private RoundRectangle2D contorno() {
        float raio = TemaSakura.RAIO_CONTROLE + 4f;
        return new RoundRectangle2D.Float(
                RECUO, RECUO,
                getWidth() - 2 * RECUO - 1f,
                getHeight() - 2 * RECUO - 1f,
                raio, raio
        );
    }
}
