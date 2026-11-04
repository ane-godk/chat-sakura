package br.unicesumar.chat.client;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Cabeçalho ilustrado: banner de cerejeiras ajustado ao tamanho do painel
 * (preenchendo sem distorcer), com cantos arredondados suaves, véu claro
 * à esquerda para a legibilidade do título e borda rosa.
 *
 * A composição é guardada em cache e refeita só quando o tamanho ou a
 * escala da tela mudam. Sem o banner, usa um degradê rosa.
 */
final class PainelCabecalho extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int ALTURA_MINIMA = 112;

    /** Fração da largura em que a ilustração começa. */
    private static final double DESLOCAMENTO_BANNER = 0.15;

    private transient BufferedImage composicao;
    private int larguraComposta;
    private int alturaComposta;
    private double escalaComposta;

    PainelCabecalho(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension tamanho = super.getPreferredSize();
        return new Dimension(
                tamanho.width,
                Math.max(tamanho.height, ALTURA_MINIMA)
        );
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

        int larguraPixels = (int) Math.ceil(largura * escala);
        int alturaPixels = (int) Math.ceil(altura * escala);

        BufferedImage imagem = new BufferedImage(
                larguraPixels, alturaPixels, BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = imagem.createGraphics();

        try {
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            g.scale(escala, escala);

            float raio = TemaSakura.RAIO_CARTAO;
            RoundRectangle2D forma = new RoundRectangle2D.Float(
                    0.5f, 0.5f, largura - 1.5f, altura - 1.5f, raio, raio
            );

            // 1. Máscara arredondada (bordas suavizadas).
            g.setColor(Color.WHITE);
            g.fill(forma);

            // 2. Ilustração somente dentro da máscara.
            g.setComposite(AlphaComposite.SrcIn);
            desenharFundo(g, largura, altura, escala);

            // 3. Véu claro à esquerda, atrás do título.
            g.setComposite(AlphaComposite.SrcAtop);
            g.setPaint(new GradientPaint(
                    0, 0, new Color(255, 255, 255, 150),
                    largura * 0.5f, 0, new Color(255, 255, 255, 0)
            ));
            g.fillRect(0, 0, largura, altura);

            // 4. Borda.
            g.setComposite(AlphaComposite.SrcOver);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(TemaSakura.ROSA_BORDA);
            g.draw(forma);

        } finally {
            g.dispose();
        }

        return imagem;
    }

    private static void desenharFundo(
            Graphics2D g,
            int largura,
            int altura,
            double escala
    ) {

        BufferedImage banner = RecursosSakura.banner().orElse(null);

        if (banner == null) {
            g.setPaint(new GradientPaint(
                    0, 0, TemaSakura.ROSA_PETALA,
                    largura, 0, TemaSakura.CARTAO
            ));
            g.fillRect(0, 0, largura, altura);
            return;
        }

        // A ilustração é deslocada para a direita: o título fica sobre a
        // área lisa, o galho logo depois e o pagode atrás dos controles.
        // Ancorada no topo, onde estão o galho e o pagode.
        int deslocamento = (int) Math.round(largura * DESLOCAMENTO_BANNER);

        double fator = Math.max(
                (double) largura / banner.getWidth(),
                (double) altura / banner.getHeight()
        );

        int larguraDesenho = (int) Math.ceil(banner.getWidth() * fator);
        int alturaDesenho = (int) Math.ceil(banner.getHeight() * fator);

        BufferedImage ajustado = RecursosSakura.redimensionar(
                banner,
                (int) Math.ceil(larguraDesenho * escala),
                (int) Math.ceil(alturaDesenho * escala)
        );

        g.drawImage(
                ajustado,
                deslocamento, 0,
                larguraDesenho, alturaDesenho,
                null
        );

        // A faixa à esquerda recebe o espelho da borda esquerda da
        // ilustração (área lisa de nuvens): contínua na junção, sem emenda.
        int larguraFonte = (int) Math.ceil(deslocamento * escala);

        g.drawImage(
                ajustado,
                deslocamento, 0, 0, alturaDesenho,
                0, 0, larguraFonte, ajustado.getHeight(),
                null
        );
    }
}
