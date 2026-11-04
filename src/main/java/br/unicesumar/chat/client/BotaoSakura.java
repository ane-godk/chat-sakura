package br.unicesumar.chat.client;

import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Botão arredondado do tema Sakura: degradê vertical, brilho interno,
 * sombra suave e ícone opcional. Possui estados normal, hover,
 * pressionado, desabilitado e anel de foco para navegação por teclado.
 *
 * Sombra e anel de foco ficam nas margens do próprio componente,
 * sem invadir textos ou áreas clicáveis vizinhas.
 */
final class BotaoSakura extends JButton {

    private static final long serialVersionUID = 1L;

    /** Espaço ao redor do corpo para o anel de foco. */
    private static final int MARGEM = 3;

    /** Espaço extra abaixo do corpo para a sombra. */
    private static final int SOMBRA = 2;

    private static final float RAIO = 16f;

    private static final int TAMANHO_ICONE = 16;
    private static final int ESPACO_ICONE = 7;

    enum Estilo {
        PRINCIPAL,
        SECUNDARIO
    }

    /** Desenho vetorial de um ícone, na cor e no tamanho indicados. */
    @FunctionalInterface
    interface DesenhoIcone {
        void desenhar(Graphics2D g2, double x, double y, double tamanho, Color cor);
    }

    private final Estilo estilo;
    private transient DesenhoIcone icone;

    BotaoSakura(String texto, Estilo estilo) {
        super(texto);
        this.estilo = estilo;

        setFont(TemaSakura.FONTE_BOTAO);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(9, 18, 9, 18));
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

    void definirIcone(DesenhoIcone novoIcone) {
        this.icone = novoIcone;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension tamanho = super.getPreferredSize();
        int extraIcone = icone == null ? 0 : TAMANHO_ICONE + ESPACO_ICONE;
        return new Dimension(
                tamanho.width + 2 * MARGEM + extraIcone,
                tamanho.height + 2 * MARGEM + SOMBRA
        );
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            ButtonModel modelo = getModel();
            boolean pressionado = isEnabled()
                    && modelo.isPressed() && modelo.isArmed();

            Aparencia aparencia = aparenciaDoEstado(modelo, pressionado);

            float x = MARGEM;
            float y = MARGEM + (pressionado ? 1f : 0f);
            float largura = getWidth() - 2f * MARGEM - 1f;
            float altura = getHeight() - 2f * MARGEM - SOMBRA - 1f;

            RoundRectangle2D corpo = new RoundRectangle2D.Float(
                    x, y, largura, altura, RAIO, RAIO
            );

            // Sombra suave em duas camadas (omitida ao pressionar).
            if (isEnabled() && !pressionado) {
                g2.setColor(new Color(142, 40, 84, 22));
                g2.fill(new RoundRectangle2D.Float(
                        x, y + 2.5f, largura, altura, RAIO, RAIO));
                g2.setColor(new Color(142, 40, 84, 30));
                g2.fill(new RoundRectangle2D.Float(
                        x, y + 1.2f, largura, altura, RAIO, RAIO));
            }

            g2.setPaint(new GradientPaint(
                    0, y, aparencia.topo(),
                    0, y + altura, aparencia.base()
            ));
            g2.fill(corpo);

            // Brilho interno no topo, para dar volume.
            if (isEnabled()) {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(255, 255, 255,
                        estilo == Estilo.PRINCIPAL ? 60 : 200));
                g2.draw(new RoundRectangle2D.Float(
                        x + 1f, y + 1f, largura - 2f, altura - 2f,
                        RAIO - 2f, RAIO - 2f));
            }

            if (aparencia.borda() != null) {
                g2.setStroke(new BasicStroke(1.4f));
                g2.setColor(aparencia.borda());
                g2.draw(corpo);
            }

            if (isFocusOwner() && isEnabled()) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(TemaSakura.ROSA);
                g2.draw(new RoundRectangle2D.Float(
                        x - 2f, y - 2f, largura + 4f, altura + 4f,
                        RAIO + 4f, RAIO + 4f));
            }

            desenharConteudo(g2, aparencia.texto(), x, y, largura, altura);

        } finally {
            g2.dispose();
        }
    }

    private void desenharConteudo(
            Graphics2D g2,
            Color cor,
            float x,
            float y,
            float largura,
            float altura
    ) {

        g2.setFont(getFont());
        FontMetrics metricas = g2.getFontMetrics();

        String texto = getText();
        int larguraTexto = metricas.stringWidth(texto);
        int larguraIcone = icone == null ? 0 : TAMANHO_ICONE + ESPACO_ICONE;

        float inicio = x + (largura - larguraTexto - larguraIcone) / 2f;

        if (icone != null) {
            icone.desenhar(
                    g2,
                    inicio,
                    y + (altura - TAMANHO_ICONE) / 2f,
                    TAMANHO_ICONE,
                    cor
            );
        }

        float linhaBase = y + (altura - metricas.getHeight()) / 2f
                + metricas.getAscent();

        g2.setColor(cor);
        g2.drawString(texto, inicio + larguraIcone, linhaBase);
    }

    private Aparencia aparenciaDoEstado(ButtonModel modelo, boolean pressionado) {

        boolean principal = estilo == Estilo.PRINCIPAL;

        if (!isEnabled()) {
            return principal
                    ? new Aparencia(TemaSakura.ROSA_PETALA, TemaSakura.ROSA_PETALA,
                            TemaSakura.ROSA_BORDA, TemaSakura.TEXTO_SUAVE)
                    : new Aparencia(TemaSakura.CARTAO, TemaSakura.CARTAO,
                            TemaSakura.ROSA_BORDA, TemaSakura.TEXTO_SUAVE);
        }

        if (pressionado) {
            return principal
                    ? new Aparencia(new Color(150, 42, 88), new Color(132, 36, 78),
                            null, Color.WHITE)
                    : new Aparencia(new Color(252, 222, 234), new Color(250, 214, 228),
                            TemaSakura.ROSA_ESCURO, TemaSakura.ROSA_PRESSIONADO);
        }

        if (modelo.isRollover()) {
            return principal
                    ? new Aparencia(new Color(180, 58, 110), new Color(155, 44, 92),
                            null, Color.WHITE)
                    : new Aparencia(new Color(255, 240, 246), new Color(253, 226, 237),
                            TemaSakura.ROSA_ESCURO, TemaSakura.ROSA_PRESSIONADO);
        }

        return principal
                ? new Aparencia(new Color(198, 70, 122), new Color(170, 52, 102),
                        null, Color.WHITE)
                : new Aparencia(Color.WHITE, new Color(255, 240, 246),
                        TemaSakura.ROSA, TemaSakura.ROSA_HOVER);
    }

    private record Aparencia(Color topo, Color base, Color borda, Color texto) {
    }
}
