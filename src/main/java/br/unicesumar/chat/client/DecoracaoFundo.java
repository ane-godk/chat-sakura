package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Fundo do cartão da conversa, para qualquer paleta e qualquer um dos três
 * tipos:
 *
 * <ul>
 *   <li>{@code FLORAL}: degradê da paleta e ilustrações originais da flor,
 *       desenhadas em código, somente nos cantos e bordas (o centro, onde
 *       ficam as mensagens, permanece limpo);</li>
 *   <li>{@code LISO}: um único tom pastel da paleta;</li>
 *   <li>{@code NEUTRO}: tom neutro, quase sem cor, sem decoração.</li>
 * </ul>
 *
 * A Sakura usa a flor original ({@code flor.png}) e as pétalas de sempre;
 * Myosotis, Fuji e Suzuran são ilustrações vetoriais próprias. Nenhuma
 * imagem externa é usada. Quem chama guarda o resultado em cache.
 */
final class DecoracaoFundo {

    static final float OPACIDADE_FLORES = 0.30f;
    static final float OPACIDADE_PETALAS = 0.30f;
    static final float OPACIDADE_ILUSTRACAO = 0.40f;

    private DecoracaoFundo() {
    }

    /** Cor lisa (pastel) da paleta. */
    static Color corLisa(PaletaSakura c) {
        return TemaSakura.misturar(c.degradeConversaTopo(), c.rosaPetala(), c.noturno() ? 0.18 : 0.40);
    }

    /** Cor neutra: o cartão da paleta sem saturação (mesma luminosidade aproximada). */
    static Color corNeutra(PaletaSakura c) {
        Color base = c.degradeConversaTopo();
        int cinza = (int) Math.round(base.getRed() * 0.299 + base.getGreen() * 0.587 + base.getBlue() * 0.114);
        int ajuste = c.noturno() ? 2 : -3;
        int v = Math.max(0, Math.min(255, cinza + ajuste));
        return new Color(v, v, Math.min(255, v + 2));
    }

    /** Cor de fundo atrás do texto em cada ponto do centro do cartão (topo e base). */
    static Color[] coresDoCentro(PaletaSakura c, Aparencia.Fundo fundo) {
        return switch (fundo) {
            case FLORAL -> new Color[] {c.degradeConversaTopo(), c.degradeConversaBase()};
            case LISO -> new Color[] {corLisa(c)};
            case NEUTRO -> new Color[] {corNeutra(c)};
        };
    }

    /**
     * Área central sempre livre de decoração (as ilustrações ficam fora
     * dela), em coordenadas lógicas.
     */
    static Rectangle areaLivre(int largura, int altura) {
        return new Rectangle(largura / 4, altura / 4, largura / 2, altura / 2);
    }

    static BufferedImage compor(PaletaSakura c, Aparencia.Paleta flor, Aparencia.Fundo fundo,
                                int largura, int altura, double escala) {

        BufferedImage imagem = new BufferedImage(
                Math.max(1, (int) Math.ceil(largura * escala)),
                Math.max(1, (int) Math.ceil(altura * escala)),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = imagem.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.scale(escala, escala);

            float raio = TemaSakura.RAIO_CARTAO;
            RoundRectangle2D forma = new RoundRectangle2D.Float(
                    0.5f, 0.5f, largura - 1.5f, altura - 1.5f, raio, raio);

            // Máscara arredondada (branco aqui é só máscara, não cor do tema).
            g.setColor(Color.WHITE);
            g.fill(forma);
            g.setComposite(AlphaComposite.SrcIn);

            switch (fundo) {
                case FLORAL -> {
                    g.setPaint(new GradientPaint(0, 0, c.degradeConversaTopo(), 0, altura, c.degradeConversaBase()));
                    g.fillRect(0, 0, largura, altura);
                    decorar(g, c, flor, largura, altura, escala);
                }
                case LISO -> {
                    g.setColor(corLisa(c));
                    g.fillRect(0, 0, largura, altura);
                }
                case NEUTRO -> {
                    g.setColor(corNeutra(c));
                    g.fillRect(0, 0, largura, altura);
                }
            }

            g.setComposite(AlphaComposite.SrcOver);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(fundo == Aparencia.Fundo.NEUTRO
                    ? TemaSakura.misturar(corNeutra(c), c.textoSuave(), 0.25)
                    : c.rosaBorda());
            g.draw(forma);

        } finally {
            g.dispose();
        }

        return imagem;
    }

    /** Fator de tamanho das ilustrações: acompanha a altura, sem pesar em janelas pequenas. */
    private static double fator(int altura) {
        return Math.max(0.5, Math.min(1.0, altura / 360.0));
    }

    private static void decorar(Graphics2D g, PaletaSakura c, Aparencia.Paleta flor,
                                int largura, int altura, double escala) {
        switch (flor) {
            case SAKURA -> {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, OPACIDADE_PETALAS));
                petalasSoltas(g, c, largura, altura);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, OPACIDADE_FLORES));
                ramalheteSakura(g, c, largura, altura, escala);
            }
            case MYOSOTIS -> {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, OPACIDADE_ILUSTRACAO));
                myosotis(g, c, largura, altura);
            }
            case FUJI -> {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, OPACIDADE_ILUSTRACAO));
                fuji(g, c, largura, altura);
            }
            case SUZURAN -> {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, OPACIDADE_ILUSTRACAO));
                suzuran(g, c, largura, altura);
            }
        }
    }

    /** Tom de folha discreto, comum às ilustrações. */
    private static Color folha(PaletaSakura c) {
        return TemaSakura.misturar(c.verde(), c.textoSuave(), 0.35);
    }

    // ------------------------------------------------------------------
    // Sakura: a composição original (flor.png e pétalas soltas)
    // ------------------------------------------------------------------

    private static void ramalheteSakura(Graphics2D g, PaletaSakura c, int largura, int altura, double escala) {
        double f = fator(altura);
        int grande = (int) Math.round(72 * f);
        int media = (int) Math.round(40 * f);
        int pequena = (int) Math.round(26 * f);

        florSakura(g, c, largura - grande * 2 / 3, altura - grande * 3 / 5, grande, escala);
        florSakura(g, c, largura - grande - media / 2, altura - media * 2 / 3, media, escala);
        florSakura(g, c, largura - pequena, altura - grande - pequena / 2, pequena, escala);
        florSakura(g, c, largura - pequena - 14, 8, pequena, escala);
    }

    private static void florSakura(Graphics2D g, PaletaSakura c, int x, int y, int tamanho, double escala) {
        BufferedImage imagem = RecursosSakura
                .florEmPixels((int) Math.ceil(tamanho * escala), Aparencia.Paleta.SAKURA, c.noturno())
                .orElse(null);
        if (imagem != null) {
            g.drawImage(imagem, x, y, tamanho, tamanho, null);
        } else {
            IconeSakura.desenhar(g, x + tamanho / 2.0, y + tamanho / 2.0, tamanho / 2.0 - 1);
        }
    }

    private static void petalasSoltas(Graphics2D g, PaletaSakura c, int largura, int altura) {
        g.setColor(c.rosaFlor());
        petala(g, 20, altura - 34, 13, 25);
        petala(g, 44, altura - 18, 9, -35);
        petala(g, 14, 28, 10, 60);
        petala(g, largura - 24, altura * 0.42, 9, 40);
    }

    static void petala(Graphics2D g, double x, double y, double tamanho, double angulo) {
        AffineTransform original = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(angulo));
        g.fill(new Ellipse2D.Double(-tamanho / 2, -tamanho * 0.32, tamanho, tamanho * 0.64));
        g.setTransform(original);
    }

    // ------------------------------------------------------------------
    // Myosotis: buquês de florzinhas de cinco pétalas com miolo amarelo
    // ------------------------------------------------------------------

    private static void myosotis(Graphics2D g, PaletaSakura c, int largura, int altura) {
        double f = fator(altura);
        Color petala = c.rosaFlor();
        Color escura = c.rosa();
        Color miolo = c.ambar();

        // Ramos finos saindo do canto inferior direito.
        g.setColor(folha(c));
        g.setStroke(new BasicStroke((float) (2.2 * f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(largura + 4, altura - 10 * f, largura - 60 * f, altura - 40 * f,
                largura - 92 * f, altura - 92 * f));
        g.draw(new QuadCurve2D.Double(largura - 30 * f, altura + 4, largura - 52 * f, altura - 30 * f,
                largura - 110 * f, altura - 36 * f));
        folhaOval(g, largura - 70 * f, altura - 58 * f, 26 * f, 10 * f, -35);
        folhaOval(g, largura - 82 * f, altura - 30 * f, 22 * f, 9 * f, 15);

        double[][] buque = {
                {-92, -96, 13}, {-76, -106, 10}, {-104, -80, 10}, {-60, -82, 12}, {-112, -40, 11},
                {-96, -30, 9}, {-36, -58, 14}, {-20, -30, 12}, {-56, -22, 10}, {-14, -88, 9},
        };
        for (double[] b : buque) {
            florMyosotis(g, largura + b[0] * f, altura + b[1] * f, b[2] * f, petala, escura, miolo);
        }

        // Pequeno buquê no canto superior esquerdo.
        g.setColor(folha(c));
        g.draw(new QuadCurve2D.Double(-4, 22 * f, 30 * f, 18 * f, 54 * f, 40 * f));
        double[][] alto = {{20, 16, 10}, {40, 30, 8}, {12, 40, 7}, {56, 46, 6}};
        for (double[] b : alto) {
            florMyosotis(g, b[0] * f, b[1] * f, b[2] * f, petala, escura, miolo);
        }

        // Botões soltos na borda esquerda inferior.
        g.setColor(petala);
        for (double[] p : new double[][] {{16, -40, 4}, {30, -24, 3}, {10, -18, 3}}) {
            g.fill(new Ellipse2D.Double(p[0] * f - p[2] * f, altura + p[1] * f - p[2] * f,
                    2 * p[2] * f, 2 * p[2] * f));
        }
    }

    private static void florMyosotis(Graphics2D g, double cx, double cy, double r,
                                     Color petala, Color contorno, Color miolo) {
        double rp = r * 0.48;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 72);
            double px = cx + Math.cos(a) * r * 0.55;
            double py = cy + Math.sin(a) * r * 0.55;
            Ellipse2D p = new Ellipse2D.Double(px - rp, py - rp, 2 * rp, 2 * rp);
            g.setColor(petala);
            g.fill(p);
            g.setColor(contorno);
            g.setStroke(new BasicStroke((float) Math.max(0.6, r * 0.06)));
            g.draw(p);
        }
        g.setColor(miolo);
        double rm = r * 0.26;
        g.fill(new Ellipse2D.Double(cx - rm, cy - rm, 2 * rm, 2 * rm));
    }

    // ------------------------------------------------------------------
    // Fuji: cachos pendentes de glicínia nos cantos superiores
    // ------------------------------------------------------------------

    private static void fuji(Graphics2D g, PaletaSakura c, int largura, int altura) {
        double f = fator(altura);

        // Ramo ao longo do topo, nos dois cantos.
        g.setColor(folha(c));
        g.setStroke(new BasicStroke((float) (2.6 * f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(-6, 10 * f, 50 * f, 2 * f, 110 * f, 14 * f));
        g.draw(new QuadCurve2D.Double(largura + 6, 12 * f, largura - 56 * f, 0, largura - 120 * f, 16 * f));

        folhaOval(g, 70 * f, 12 * f, 20 * f, 7 * f, 20);
        folhaOval(g, 100 * f, 22 * f, 16 * f, 6 * f, 60);
        folhaOval(g, largura - 76 * f, 10 * f, 20 * f, 7 * f, -20);
        folhaOval(g, largura - 108 * f, 22 * f, 16 * f, 6 * f, -60);

        Color topo = c.rosa();
        Color ponta = c.rosaFlor();
        cacho(g, 20 * f, 8 * f, 104 * f, 26 * f, topo, ponta, 1);
        cacho(g, 52 * f, 10 * f, 76 * f, 22 * f, topo, ponta, 2);
        cacho(g, 84 * f, 14 * f, 54 * f, 18 * f, topo, ponta, 3);
        cacho(g, largura - 24 * f, 8 * f, 96 * f, 24 * f, topo, ponta, 4);
        cacho(g, largura - 58 * f, 10 * f, 70 * f, 20 * f, topo, ponta, 5);
        cacho(g, largura - 92 * f, 14 * f, 46 * f, 16 * f, topo, ponta, 6);
    }

    /** Cacho pendente: florzinhas cada vez menores e mais claras rumo à ponta. */
    private static void cacho(Graphics2D g, double x, double yTopo, double comprimento, double largura,
                              Color topo, Color ponta, int semente) {
        int n = 11;
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double y = yTopo + t * comprimento;
            double w = largura * (1 - t * 0.72);
            double desvio = Math.sin(semente * 1.7 + i * 1.3) * w * 0.12;
            g.setColor(TemaSakura.misturar(topo, ponta, t));
            for (int lado = -1; lado <= 1; lado += 2) {
                double px = x + desvio + lado * w * 0.22;
                double rw = w * 0.56;
                double rh = w * 0.40;
                g.fill(new Ellipse2D.Double(px - rw / 2, y - rh / 2, rw, rh));
            }
        }
    }

    // ------------------------------------------------------------------
    // Suzuran: hastes arqueadas com sininhos e folhas largas
    // ------------------------------------------------------------------

    private static void suzuran(Graphics2D g, PaletaSakura c, int largura, int altura) {
        double f = fator(altura);
        Color folha = c.rosa();
        Color sino = c.noturno() ? TemaSakura.misturar(c.texto(), c.rosaFlor(), 0.25) : c.cartao();
        Color contorno = c.rosaEscuro();

        // Duas folhas largas no canto inferior esquerdo.
        g.setColor(TemaSakura.misturar(folha, c.rosaFlor(), 0.35));
        folhaLanca(g, 10 * f, altura + 6, 54 * f, altura - 120 * f, 22 * f);
        g.setColor(folha);
        folhaLanca(g, 34 * f, altura + 6, 104 * f, altura - 92 * f, 20 * f);

        // Haste arqueada com sininhos pendurados.
        double x0 = 24 * f;
        double y0 = altura + 2;
        double cx = 44 * f;
        double cy = altura - 150 * f;
        double x1 = 112 * f;
        double y1 = altura - 120 * f;
        g.setColor(contorno);
        g.setStroke(new BasicStroke((float) (1.8 * f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(x0, y0, cx, cy, x1, y1));
        for (int i = 0; i < 6; i++) {
            double t = 0.42 + i * 0.105;
            double px = quad(x0, cx, x1, t);
            double py = quad(y0, cy, y1, t);
            double tamanho = (12 - i * 1.1) * f;
            g.setColor(contorno);
            g.setStroke(new BasicStroke((float) Math.max(0.8, f)));
            g.draw(new QuadCurve2D.Double(px, py, px + 3 * f, py + 4 * f, px + 2 * f, py + 9 * f));
            sininho(g, px + 2 * f, py + 9 * f + tamanho * 0.5, tamanho, sino, contorno);
        }

        // Raminho menor no canto superior direito.
        double rx0 = largura + 2;
        double ry0 = 8 * f;
        double rcx = largura - 30 * f;
        double rcy = -6 * f;
        double rx1 = largura - 70 * f;
        double ry1 = 26 * f;
        g.setColor(contorno);
        g.setStroke(new BasicStroke((float) (1.6 * f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(rx0, ry0, rcx, rcy, rx1, ry1));
        for (int i = 0; i < 4; i++) {
            double t = 0.35 + i * 0.18;
            double px = quad(rx0, rcx, rx1, t);
            double py = quad(ry0, rcy, ry1, t);
            sininho(g, px, py + 9 * f, (9 - i) * f, sino, contorno);
        }
    }

    private static double quad(double a, double controle, double b, double t) {
        double u = 1 - t;
        return u * u * a + 2 * u * t * controle + t * t * b;
    }

    /** Sininho: cúpula arredondada com a borda inferior recortada em três pontas. */
    private static void sininho(Graphics2D g, double cx, double cy, double tamanho, Color corpo, Color contorno) {
        double r = tamanho / 2;
        Path2D.Double sino = new Path2D.Double();
        sino.moveTo(cx - r, cy + r * 0.35);
        sino.curveTo(cx - r, cy - r * 1.1, cx + r, cy - r * 1.1, cx + r, cy + r * 0.35);
        sino.quadTo(cx + r * 0.66, cy + r * 0.15, cx + r * 0.33, cy + r * 0.55);
        sino.quadTo(cx, cy + r * 0.2, cx - r * 0.33, cy + r * 0.55);
        sino.quadTo(cx - r * 0.66, cy + r * 0.15, cx - r, cy + r * 0.35);
        sino.closePath();
        g.setColor(corpo);
        g.fill(sino);
        g.setColor(contorno);
        g.setStroke(new BasicStroke((float) Math.max(0.7, tamanho * 0.08)));
        g.draw(sino);
    }

    /** Folha longa em forma de lança, da base até a ponta. */
    private static void folhaLanca(Graphics2D g, double xBase, double yBase, double xPonta, double yPonta,
                                   double largura) {
        double mx = (xBase + xPonta) / 2;
        double my = (yBase + yPonta) / 2;
        double dx = xPonta - xBase;
        double dy = yPonta - yBase;
        double comprimento = Math.hypot(dx, dy);
        double nx = -dy / comprimento * largura;
        double ny = dx / comprimento * largura;
        Path2D.Double f = new Path2D.Double();
        f.moveTo(xBase, yBase);
        f.quadTo(mx + nx, my + ny, xPonta, yPonta);
        f.quadTo(mx - nx * 0.6, my - ny * 0.6, xBase, yBase);
        f.closePath();
        g.fill(f);
    }

    private static void folhaOval(Graphics2D g, double cx, double cy, double comprimento, double largura,
                                  double angulo) {
        AffineTransform original = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(angulo));
        g.fill(new Ellipse2D.Double(-comprimento / 2, -largura / 2, comprimento, largura));
        g.setTransform(original);
    }
}
