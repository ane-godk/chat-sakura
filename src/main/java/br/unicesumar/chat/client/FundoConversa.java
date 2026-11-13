package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Cartão da conversa com o fundo escolhido na aparência (floral, liso ou
 * neutro; ver {@link DecoracaoFundo}). O fundo fica fixo durante a
 * rolagem; as mensagens passam por cima dele.
 *
 * A composição é guardada em cache e refeita só quando o tamanho, a
 * escala da tela, a paleta ou o fundo mudam. As pétalas caindo só
 * aparecem no fundo floral.
 */
final class FundoConversa extends JPanel {

    private static final long serialVersionUID = 1L;

    private transient BufferedImage composicao;
    private int larguraComposta;
    private int alturaComposta;
    private double escalaComposta;
    private transient PaletaSakura paletaComposta;
    private transient Aparencia aparenciaComposta;
    private final transient Runnable aoMudarTema = this::atualizarPetalas;

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

    /** A regra geral e, além dela, somente no fundo floral. */
    static boolean petalasDevemRodar(
            ConfiguracaoAnimacoes.Modo modo, boolean janelaAtiva, boolean visivel, Aparencia.Fundo fundo) {
        return fundo == Aparencia.Fundo.FLORAL && petalasDevemRodar(modo, janelaAtiva, visivel);
    }

    private boolean petalasNesteFundo() {
        return petalasDevemRodar(ConfiguracaoAnimacoes.modo(), janelaAtiva, isShowing(), GerenciadorTema.fundo());
    }

    /** Composição em cache (testes). */
    BufferedImage composicao() {
        return composicao;
    }

    private void atualizarPetalas() {
        if (petalasNesteFundo()) {
            ultimoPasso = 0;
            animadorPetalas.iniciar();
        } else {
            animadorPetalas.parar();
        }
        repaint();
    }

    private boolean passoPetalas() {

        if (!petalasNesteFundo()) {
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
        GerenciadorTema.aoMudar(aoMudarTema);
        atualizarPetalas();
    }

    @Override
    public void removeNotify() {
        ConfiguracaoAnimacoes.removerOuvinte(aoMudarAnimacoes);
        GerenciadorTema.removerOuvinte(aoMudarTema);
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

            PaletaSakura cores = TemaSakura.cores();
            Aparencia aparencia = GerenciadorTema.aparencia();

            if (composicao == null
                    || largura != larguraComposta
                    || altura != alturaComposta
                    || escala != escalaComposta
                    || cores != paletaComposta
                    || !aparencia.equals(aparenciaComposta)) {

                composicao = compatibilizar(DecoracaoFundo.compor(cores, aparencia.paleta(), aparencia.fundo(),
                        largura, altura, escala));
                larguraComposta = largura;
                alturaComposta = altura;
                escalaComposta = escala;
                paletaComposta = cores;
                aparenciaComposta = aparencia;
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
                    DecoracaoFundo.petala(gp, p.x(), p.y(), p.tamanho(), p.angulo());
                }
            }
        } finally {
            gp.dispose();
        }
    }
}
