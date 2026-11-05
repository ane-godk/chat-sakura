package br.unicesumar.chat.client;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
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

    private static final float OPACIDADE_FLORES = 0.30f;
    private static final float OPACIDADE_PETALAS = 0.30f;

    private transient BufferedImage composicao;
    private int larguraComposta;
    private int alturaComposta;
    private double escalaComposta;

    // Pétalas caindo (animação contínua e lenta, 10 quadros por segundo).
    static final int QUANTIDADE_PETALAS = 5;
    static final int INTERVALO_PETALAS_MS = 100;
    private static final float OPACIDADE_PETALAS_CAINDO = 0.32f;

    private final transient CampoPetalas petalas =
            new CampoPetalas(QUANTIDADE_PETALAS, System.nanoTime());
    private final transient AnimadorSwing animadorPetalas =
            new AnimadorSwing(INTERVALO_PETALAS_MS, this::passoPetalas);
    private final transient Runnable aoMudarAnimacoes = this::atualizarPetalas;

    private boolean janelaAtiva = true;
    private long ultimoPasso;

    FundoConversa(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    /**
     * Regra de execução das pétalas: somente no modo completo, com a
     * janela ativa (não minimizada nem em segundo plano) e visível.
     */
    static boolean petalasDevemRodar(
            ConfiguracaoAnimacoes.Modo modo,
            boolean janelaAtiva,
            boolean visivel
    ) {
        return modo == ConfiguracaoAnimacoes.Modo.COMPLETAS && janelaAtiva && visivel;
    }

    /** Informado pela janela: ativa, inativa ou minimizada. */
    void definirJanelaAtiva(boolean ativa) {
        this.janelaAtiva = ativa;
        atualizarPetalas();
    }

    boolean petalasRodando() {
        return animadorPetalas.rodando();
    }

    private void atualizarPetalas() {
        if (petalasDevemRodar(ConfiguracaoAnimacoes.modo(), janelaAtiva, isShowing())) {
            ultimoPasso = 0;
            animadorPetalas.iniciar();
        } else {
            animadorPetalas.parar();
        }
        repaint();
    }

    private boolean passoPetalas() {

        if (!petalasDevemRodar(ConfiguracaoAnimacoes.modo(), janelaAtiva, isShowing())) {
            return false;
        }

        long agora = Transicao.agora();
        long passo = ultimoPasso == 0 ? 0 : Math.min(agora - ultimoPasso, 200);
        ultimoPasso = agora;

        petalas.ajustarArea(getWidth(), getHeight());

        List<CampoPetalas.Petala> antes = petalas.petalas();
        petalas.avancar(passo);
        List<CampoPetalas.Petala> depois = petalas.petalas();

        // Redesenha só a área de cada pétala (posição antiga e nova),
        // em vez do cartão inteiro com todas as mensagens.
        Rectangle limites = new Rectangle(0, 0, getWidth(), getHeight());

        for (int i = 0; i < depois.size(); i++) {
            Rectangle area = areaDa(antes.get(i)).union(areaDa(depois.get(i)));
            Rectangle visivel = area.intersection(limites);
            if (!visivel.isEmpty()) {
                paintImmediately(visivel);
            }
        }
        return true;
    }

    private static Rectangle areaDa(CampoPetalas.Petala p) {
        int margem = (int) Math.ceil(p.tamanho() / 2) + 2;
        return new Rectangle(
                (int) Math.floor(p.x()) - margem,
                (int) Math.floor(p.y()) - margem,
                2 * margem + 1,
                2 * margem + 1
        );
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ConfiguracaoAnimacoes.aoMudar(aoMudarAnimacoes);
        atualizarPetalas();
    }

    @Override
    public void removeNotify() {
        ConfiguracaoAnimacoes.removerOuvinte(aoMudarAnimacoes);
        animadorPetalas.parar();
        super.removeNotify();
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

                composicao = compatibilizar(compor(largura, altura, escala));
                larguraComposta = largura;
                alturaComposta = altura;
                escalaComposta = escala;
            }

            if (composicao.getWidth() == largura && composicao.getHeight() == altura) {
                // Sem escala: cópia direta, a forma mais rápida.
                g2.drawImage(composicao, 0, 0, null);
            } else {
                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                g2.drawImage(composicao, 0, 0, largura, altura, null);
            }

            if (animadorPetalas.rodando()) {
                desenharPetalasCaindo(g2, largura, altura);
            }

        } finally {
            g2.dispose();
        }
    }

    /**
     * Copia a composição para uma imagem compatível com a tela, que o
     * Java2D pode acelerar ao desenhar (importante com as pétalas).
     */
    private BufferedImage compatibilizar(BufferedImage imagem) {

        java.awt.GraphicsConfiguration configuracao = getGraphicsConfiguration();

        if (configuracao == null) {
            return imagem;
        }

        BufferedImage compativel = configuracao.createCompatibleImage(
                imagem.getWidth(), imagem.getHeight(), java.awt.Transparency.TRANSLUCENT);

        Graphics2D g = compativel.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.drawImage(imagem, 0, 0, null);
        } finally {
            g.dispose();
        }
        return compativel;
    }

    /** Pétalas em movimento, por trás das mensagens e dentro do cartão. */
    private void desenharPetalasCaindo(Graphics2D g2, int largura, int altura) {

        Graphics2D gp = (Graphics2D) g2.create();

        try {
            gp.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            // Recorte retangular (rápido); pétalas nos cantos arredondados
            // são omitidas para não ultrapassar a borda do cartão.
            gp.clipRect(2, 2, largura - 4, altura - 4);
            gp.setComposite(AlphaComposite.SrcOver.derive(OPACIDADE_PETALAS_CAINDO));
            gp.setColor(TemaSakura.cores().rosaFlor());

            int canto = TemaSakura.RAIO_CARTAO;

            for (CampoPetalas.Petala p : petalas.petalas()) {
                boolean noCanto = (p.x() < canto || p.x() > largura - canto)
                        && (p.y() < canto || p.y() > altura - canto);
                if (!noCanto) {
                    petala(gp, p.x(), p.y(), p.tamanho(), p.angulo());
                }
            }
        } finally {
            gp.dispose();
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
            g.setColor(Color.WHITE);   // máscara de recorte (não é cor do tema)
            g.fill(forma);
            g.setComposite(AlphaComposite.SrcIn);
            g.setPaint(new GradientPaint(0, 0, TemaSakura.cores().degradeConversaTopo(), 0, altura, TemaSakura.cores().degradeConversaBase()));
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
            g.setColor(TemaSakura.cores().rosaBorda());
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

        g.setColor(TemaSakura.cores().rosaFlor());

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
