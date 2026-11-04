package br.unicesumar.chat.client;

import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Campo de texto arredondado do tema Sakura, com borda e brilho rosa
 * quando em foco, texto de orientação (placeholder) quando vazio e,
 * opcionalmente, uma flor à esquerda. A flor fica na margem interna,
 * fora da área de digitação.
 */
final class CampoTextoSakura extends JTextField {

    private static final long serialVersionUID = 1L;

    /** Espaço entre a borda do componente e o contorno (para o brilho). */
    private static final float RECUO = 3f;

    private static final int TAMANHO_FLOR = 20;
    private static final int MARGEM_ESQUERDA = 16;

    private static final Color BRILHO_FOCO = new Color(248, 187, 208, 150);

    private final String placeholder;
    private final String placeholderDesabilitado;
    private final boolean comFlor;

    private transient BufferedImage florAjustada;

    CampoTextoSakura(
            int colunas,
            String placeholder,
            String placeholderDesabilitado
    ) {
        this(colunas, placeholder, placeholderDesabilitado, false);
    }

    CampoTextoSakura(
            int colunas,
            String placeholder,
            String placeholderDesabilitado,
            boolean comFlor
    ) {
        super(colunas);
        this.placeholder = placeholder;
        this.placeholderDesabilitado = placeholderDesabilitado;
        this.comFlor = comFlor;

        setOpaque(false);
        setFont(TemaSakura.FONTE_TEXTO);
        setForeground(TemaSakura.TEXTO);
        setDisabledTextColor(TemaSakura.TEXTO_SUAVE);
        setCaretColor(TemaSakura.ROSA_ESCURO);
        setSelectionColor(TemaSakura.ROSA_PETALA);
        setSelectedTextColor(TemaSakura.TEXTO);

        int esquerda = comFlor
                ? MARGEM_ESQUERDA + TAMANHO_FLOR + 8
                : MARGEM_ESQUERDA;

        setBorder(new EmptyBorder(11, esquerda, 11, 16));

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

            if (comFlor) {
                desenharFlor(g2);
            }
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

    private void desenharFlor(Graphics2D g2) {

        int x = MARGEM_ESQUERDA - 2;
        int y = (getHeight() - TAMANHO_FLOR) / 2;

        BufferedImage flor = RecursosSakura.flor().orElse(null);

        if (flor == null) {
            IconeSakura.desenhar(
                    g2,
                    x + TAMANHO_FLOR / 2.0,
                    y + TAMANHO_FLOR / 2.0,
                    TAMANHO_FLOR / 2.0 - 1
            );
            return;
        }

        int pixels = (int) Math.ceil(
                TAMANHO_FLOR * g2.getTransform().getScaleX()
        );

        if (florAjustada == null || florAjustada.getWidth() != pixels) {
            florAjustada = RecursosSakura.redimensionar(flor, pixels, pixels);
        }

        if (!isEnabled()) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.5f));
        }

        g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        g2.drawImage(florAjustada, x, y, TAMANHO_FLOR, TAMANHO_FLOR, null);
    }

    @Override
    protected void paintBorder(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            boolean emFoco = isFocusOwner() && isEnabled();

            if (emFoco) {
                g2.setStroke(new BasicStroke(5f));
                g2.setColor(BRILHO_FOCO);
                g2.draw(contorno());
            }

            g2.setStroke(new BasicStroke(emFoco ? 2f : 1.5f));
            g2.setColor(emFoco ? TemaSakura.ROSA : TemaSakura.ROSA_BORDA);
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
