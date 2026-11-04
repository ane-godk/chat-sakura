package br.unicesumar.chat.client;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Cartão da conversa com fundo em degradê branco-rosado e detalhes
 * florais discretos nos cantos (flores parcialmente cortadas pela borda
 * e pétalas soltas). O fundo fica fixo durante a rolagem; as mensagens
 * passam por cima dele.
 *
 * A composição é guardada em cache e refeita só quando o tamanho ou a
 * escala da tela mudam.
 */
final class FundoConversa extends JPanel {

    private static final long serialVersionUID = 1L;

    static final Color DEGRADE_TOPO = Color.WHITE;
    static final Color DEGRADE_BASE = new Color(255, 241, 247);

    private static final float OPACIDADE_FLORES = 0.30f;
    private static final float OPACIDADE_PETALAS = 0.30f;

    private transient BufferedImage composicao;
    private int larguraComposta;
    private int alturaComposta;
    private double escalaComposta;

    FundoConversa(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        int largura = getWidth();
        int altura = getHeight();

        if (largura <= 0 || altura <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            double escala = g2.getTransform().getScaleX();

            if (composicao == null
                    || largura != larguraComposta
                    || altura != alturaComposta
                    || escala != escalaComposta) {

                composicao = compor(largura, altura, escala);
                larguraComposta = largura;
                alturaComposta = altura;
                escalaComposta = escala;
            }

            g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            g2.drawImage(composicao, 0, 0, largura, altura, null);

        } finally {
            g2.dispose();
        }
    }

    private static BufferedImage compor(int largura, int altura, double escala) {

        BufferedImage imagem = new BufferedImage(
                (int) Math.ceil(largura * escala),
                (int) Math.ceil(altura * escala),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = imagem.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.scale(escala, escala);

            float raio = TemaSakura.RAIO_CARTAO;
            RoundRectangle2D forma = new RoundRectangle2D.Float(
                    0.5f, 0.5f, largura - 1.5f, altura - 1.5f, raio, raio
            );

            // Máscara arredondada e degradê.
            g.setColor(Color.WHITE);
            g.fill(forma);
            g.setComposite(AlphaComposite.SrcIn);
            g.setPaint(new GradientPaint(0, 0, DEGRADE_TOPO, 0, altura, DEGRADE_BASE));
            g.fillRect(0, 0, largura, altura);

            // Decoração somente dentro do cartão.
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_ATOP, OPACIDADE_PETALAS));
            desenharPetalas(g, largura, altura);

            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_ATOP, OPACIDADE_FLORES));
            desenharFlores(g, largura, altura, escala);

            // Borda.
            g.setComposite(AlphaComposite.SrcOver);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(TemaSakura.ROSA_BORDA);
            g.draw(forma);

        } finally {
            g.dispose();
        }

        return imagem;
    }

    /**
     * Ramalhete no canto inferior direito, parcialmente cortado pela borda,
     * e uma flor pequena no canto superior direito. O tamanho acompanha a
     * altura da área, para não pesar em janelas pequenas.
     */
    private static void desenharFlores(
            Graphics2D g, int largura, int altura, double escala) {

        double fator = Math.max(0.5, Math.min(1.0, altura / 360.0));

        int grande = (int) Math.round(72 * fator);
        int media = (int) Math.round(40 * fator);
        int pequena = (int) Math.round(26 * fator);

        flor(g, largura - grande * 2 / 3, altura - grande * 3 / 5, grande, escala);
        flor(g, largura - grande - media / 2, altura - media * 2 / 3, media, escala);
        flor(g, largura - pequena, altura - grande - pequena / 2, pequena, escala);
        flor(g, largura - pequena - 14, 8, pequena, escala);
    }

    private static void flor(Graphics2D g, int x, int y, int tamanho, double escala) {

        BufferedImage imagem = RecursosSakura
                .florEmPixels((int) Math.ceil(tamanho * escala))
                .orElse(null);

        if (imagem != null) {
            g.drawImage(imagem, x, y, tamanho, tamanho, null);
        } else {
            IconeSakura.desenhar(g, x + tamanho / 2.0, y + tamanho / 2.0, tamanho / 2.0 - 1);
        }
    }

    /** Pétalas soltas perto das bordas, longe do centro da leitura. */
    private static void desenharPetalas(Graphics2D g, int largura, int altura) {

        g.setColor(TemaSakura.ROSA_FLOR);

        petala(g, 20, altura - 34, 13, 25);
        petala(g, 44, altura - 18, 9, -35);
        petala(g, 14, 28, 10, 60);
        petala(g, largura - 24, altura * 0.42, 9, 40);
    }

    private static void petala(Graphics2D g, double x, double y, double tamanho, double angulo) {

        AffineTransform original = g.getTransform();

        g.translate(x, y);
        g.rotate(Math.toRadians(angulo));
        g.fill(new Ellipse2D.Double(-tamanho / 2, -tamanho * 0.32, tamanho, tamanho * 0.64));

        g.setTransform(original);
    }
}
