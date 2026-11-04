package br.unicesumar.chat.client;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

/**
 * Separador decorativo do histórico: linhas finas, flores e o rótulo
 * "Histórico recente" ou "Fim do histórico". A linha original do
 * servidor continua intacta na exportação.
 */
final class SeparadorHistorico extends JComponent implements ItemConversa {

    private static final long serialVersionUID = 1L;

    static final String ROTULO_INICIO = "Histórico recente";
    static final String ROTULO_FIM = "Fim do histórico";

    private static final int ALTURA = 34;
    private static final int TAMANHO_FLOR = 16;
    private static final int ESPACO = 8;

    private final String rotulo;

    SeparadorHistorico(String linhaOriginal) {
        this.rotulo = FormatoLinhaChat.FIM_HISTORICO.equals(linhaOriginal)
                ? ROTULO_FIM
                : ROTULO_INICIO;
        setOpaque(false);
    }

    String rotulo() {
        return rotulo;
    }

    @Override
    public Alinhamento alinhamento() {
        return Alinhamento.LARGURA_TOTAL;
    }

    @Override
    public Dimension tamanhoPara(int larguraDisponivel) {
        return new Dimension(larguraDisponivel, ALTURA);
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            g2.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
            FontMetrics metricas = g2.getFontMetrics();

            int largura = getWidth();
            int centroY = getHeight() / 2;

            // O mesmo TextLayout mede e desenha o rótulo: o espaço das
            // flores fica igual dos dois lados em qualquer destino de
            // desenho (tela ou imagem).
            TextLayout layout = new TextLayout(
                    rotulo, g2.getFont(), g2.getFontRenderContext()
            );
            int larguraTexto = (int) Math.ceil(layout.getAdvance());

            int inicioTexto = (largura - larguraTexto) / 2;
            int florEsquerda = inicioTexto - ESPACO - TAMANHO_FLOR;
            int florDireita = inicioTexto + larguraTexto + ESPACO;

            // Linhas finas que se estendem a partir das flores.
            int margem = (int) (largura * 0.08);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(TemaSakura.ROSA_BORDA);

            if (florEsquerda - ESPACO > margem) {
                g2.draw(new Line2D.Float(margem, centroY, florEsquerda - ESPACO, centroY));
            }
            if (florDireita + TAMANHO_FLOR + ESPACO < largura - margem) {
                g2.draw(new Line2D.Float(
                        florDireita + TAMANHO_FLOR + ESPACO, centroY,
                        largura - margem, centroY));
            }

            desenharFlor(g2, florEsquerda, centroY - TAMANHO_FLOR / 2);
            desenharFlor(g2, florDireita, centroY - TAMANHO_FLOR / 2);

            g2.setColor(TemaSakura.ROSA_ESCURO);
            layout.draw(
                    g2,
                    inicioTexto,
                    centroY - metricas.getHeight() / 2f + metricas.getAscent()
            );

        } finally {
            g2.dispose();
        }
    }

    private static void desenharFlor(Graphics2D g2, int x, int y) {

        int pixels = (int) Math.ceil(TAMANHO_FLOR * g2.getTransform().getScaleX());
        BufferedImage flor = RecursosSakura.florEmPixels(pixels).orElse(null);

        if (flor == null) {
            IconeSakura.desenhar(
                    g2,
                    x + TAMANHO_FLOR / 2.0,
                    y + TAMANHO_FLOR / 2.0,
                    TAMANHO_FLOR / 2.0 - 1
            );
            return;
        }

        g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        g2.drawImage(flor, x, y, TAMANHO_FLOR, TAMANHO_FLOR, null);
    }
}
