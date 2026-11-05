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
 * sombra suave e ícone opcional. Os estados normal, hover e pressionado
 * mudam com transições curtas; há anel de foco para navegação por teclado.
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

    static final int DURACAO_HOVER_MS = 120;
    static final int DURACAO_PRESSAO_MS = 90;
    static final int DURACAO_ENVIO_MS = 380;

    /** Deslocamento máximo do ícone na animação de envio. */
    private static final double VOO_ICONE = 5.0;

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

    private final transient Transicao hover = new Transicao(0, DURACAO_HOVER_MS);
    private final transient Transicao pressao = new Transicao(0, DURACAO_PRESSAO_MS);
    private final transient Transicao envio = new Transicao(1, DURACAO_ENVIO_MS);
    private final transient AnimadorSwing animador = new AnimadorSwing(15, this::passoAnimacao);

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

        getModel().addChangeListener(e -> atualizarTransicoes());
    }

    private void atualizarTransicoes() {

        ButtonModel modelo = getModel();
        long agora = Transicao.agora();

        hover.irPara(modelo.isRollover() ? 1 : 0, agora);
        pressao.irPara(modelo.isPressed() && modelo.isArmed() ? 1 : 0, agora);

        if (hover.emAndamento(agora) || pressao.emAndamento(agora)) {
            animador.iniciar();
        }
        repaint();
    }

    private boolean passoAnimacao() {
        long agora = Transicao.agora();
        repaint();
        return hover.emAndamento(agora)
                || pressao.emAndamento(agora)
                || envio.emAndamento(agora);
    }

    /** Pequeno voo do ícone (avião de papel) após um envio bem-sucedido. */
    void animarEnvio() {

        if (icone == null || !ConfiguracaoAnimacoes.transicoesAtivas()) {
            return;
        }

        envio.definir(0);
        envio.irPara(1, Transicao.agora());
        animador.iniciar();
    }

    double progressoHover() {
        return hover.valor(Transicao.agora());
    }

    double progressoEnvio() {
        return envio.valor(Transicao.agora());
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
            long agora = Transicao.agora();
            double h = hover.valor(agora);
            double p = isEnabled() ? pressao.valor(agora) : 0;

            Aparencia aparencia = isEnabled()
                    ? misturar(misturar(normal(), destacado(), h), pressionado(), p)
                    : desabilitado();

            float x = MARGEM;
            float y = MARGEM + (float) p;
            float largura = getWidth() - 2f * MARGEM - 1f;
            float altura = getHeight() - 2f * MARGEM - SOMBRA - 1f;

            RoundRectangle2D corpo = new RoundRectangle2D.Float(
                    x, y, largura, altura, RAIO, RAIO
            );

            // Sombra suave em duas camadas, que some ao pressionar.
            if (isEnabled() && p < 1) {
                double fator = 1 - p;
                g2.setColor(new Color(142, 40, 84, (int) (22 * fator)));
                g2.fill(new RoundRectangle2D.Float(
                        x, y + 2.5f, largura, altura, RAIO, RAIO));
                g2.setColor(new Color(142, 40, 84, (int) (30 * fator)));
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

            desenharConteudo(g2, aparencia.texto(), x, y, largura, altura, agora);

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
            float altura,
            long agora
    ) {

        g2.setFont(getFont());
        FontMetrics metricas = g2.getFontMetrics();

        String texto = getText();
        int larguraTexto = metricas.stringWidth(texto);
        int larguraIcone = icone == null ? 0 : TAMANHO_ICONE + ESPACO_ICONE;

        float inicio = x + (largura - larguraTexto - larguraIcone) / 2f;

        if (icone != null) {
            // Voo do avião: sobe e avança um pouco, depois volta ao lugar
            // (mais para cima do que para a frente, sem encostar no texto).
            double voo = Math.sin(Math.PI * envio.valor(agora)) * VOO_ICONE;

            icone.desenhar(
                    g2,
                    inicio + voo * 0.5,
                    y + (altura - TAMANHO_ICONE) / 2f - voo,
                    TAMANHO_ICONE,
                    cor
            );
        }

        float linhaBase = y + (altura - metricas.getHeight()) / 2f
                + metricas.getAscent();

        g2.setColor(cor);
        g2.drawString(texto, inicio + larguraIcone, linhaBase);
    }

    // --- Aparências por estado ---

    private Aparencia normal() {
        return estilo == Estilo.PRINCIPAL
                ? new Aparencia(new Color(198, 70, 122), new Color(170, 52, 102),
                        null, Color.WHITE)
                : new Aparencia(Color.WHITE, new Color(255, 240, 246),
                        TemaSakura.ROSA, TemaSakura.ROSA_HOVER);
    }

    private Aparencia destacado() {
        return estilo == Estilo.PRINCIPAL
                ? new Aparencia(new Color(180, 58, 110), new Color(155, 44, 92),
                        null, Color.WHITE)
                : new Aparencia(new Color(255, 240, 246), new Color(253, 226, 237),
                        TemaSakura.ROSA_ESCURO, TemaSakura.ROSA_PRESSIONADO);
    }

    private Aparencia pressionado() {
        return estilo == Estilo.PRINCIPAL
                ? new Aparencia(new Color(150, 42, 88), new Color(132, 36, 78),
                        null, Color.WHITE)
                : new Aparencia(new Color(252, 222, 234), new Color(250, 214, 228),
                        TemaSakura.ROSA_ESCURO, TemaSakura.ROSA_PRESSIONADO);
    }

    private Aparencia desabilitado() {
        return estilo == Estilo.PRINCIPAL
                ? new Aparencia(TemaSakura.ROSA_PETALA, TemaSakura.ROSA_PETALA,
                        TemaSakura.ROSA_BORDA, TemaSakura.TEXTO_SUAVE)
                : new Aparencia(TemaSakura.CARTAO, TemaSakura.CARTAO,
                        TemaSakura.ROSA_BORDA, TemaSakura.TEXTO_SUAVE);
    }

    private static Aparencia misturar(Aparencia a, Aparencia b, double t) {
        if (t <= 0) {
            return a;
        }
        if (t >= 1) {
            return b;
        }
        return new Aparencia(
                TemaSakura.misturar(a.topo(), b.topo(), t),
                TemaSakura.misturar(a.base(), b.base(), t),
                TemaSakura.misturar(a.borda(), b.borda(), t),
                TemaSakura.misturar(a.texto(), b.texto(), t)
        );
    }

    private record Aparencia(Color topo, Color base, Color borda, Color texto) {
    }
}
