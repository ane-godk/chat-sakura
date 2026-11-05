package br.unicesumar.chat.client;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedString;

/**
 * Aviso de presença ("Ana se conectou · 20:34"): pequena pílula
 * centralizada com a flor Sakura. Na saída, a flor fica esmaecida.
 *
 * Apenas apresentação: a linha original do servidor é mantida pelo
 * PainelConversa para a exportação TXT.
 */
final class AvisoPresenca extends JComponent implements ItemConversa {

    private static final long serialVersionUID = 1L;

    private static final int ALTURA = 28;
    private static final int TAMANHO_FLOR = 14;
    private static final int PREENCHIMENTO_H = 12;
    private static final int ESPACO_FLOR = 6;

    private static final Font FONTE_ACAO = TemaSakura.fonte(Font.PLAIN, 12.5f);

    private final String participante;
    private final String acao;
    private final String horario;
    private final boolean entrada;
    private final Font fonteNome;

    AvisoPresenca(FormatoLinhaChat.Linha linha) {

        if (linha.tipo() != FormatoLinhaChat.Tipo.AVISO_PRESENCA) {
            throw new IllegalArgumentException("A linha não é um aviso de presença.");
        }

        this.participante = linha.remetente();
        this.acao = linha.conteudo();
        this.horario = horaMinuto(linha.horario());
        this.entrada = FormatoLinhaChat.ACAO_ENTRADA.equals(acao);
        this.fonteNome = TemaSakura.fonteQueExibe(
                TemaSakura.FONTE_PEQUENA_NEGRITO, participante);

        setOpaque(false);
        setToolTipText(BalaoMensagem.semColchetes(linha.horario()));
    }

    /** "[04/11 20:34]" é exibido como "20:34". */
    static String horaMinuto(String horario) {
        String semColchetes = BalaoMensagem.semColchetes(horario);
        int espaco = semColchetes.lastIndexOf(' ');
        return espaco >= 0 ? semColchetes.substring(espaco + 1) : semColchetes;
    }

    String participante() {
        return participante;
    }

    boolean entrada() {
        return entrada;
    }

    String horarioExibido() {
        return horario;
    }

    /** Texto exibido na pílula, sem a flor. */
    String textoExibido() {
        return participante + " " + acao + " · " + horario;
    }

    @Override
    public Alinhamento alinhamento() {
        return Alinhamento.LARGURA_TOTAL;
    }

    @Override
    public Dimension tamanhoPara(int larguraDisponivel) {
        return new Dimension(larguraDisponivel, ALTURA);
    }

    /** Largura total da pílula (flor + texto + margens). */
    int larguraPilula(FontRenderContext contexto) {
        return (int) Math.ceil(layout(contexto).getAdvance())
                + TAMANHO_FLOR + ESPACO_FLOR + 2 * PREENCHIMENTO_H;
    }

    private TextLayout layout(FontRenderContext contexto) {

        String texto = textoExibido();
        int fimNome = participante.length();
        int inicioHora = texto.length() - horario.length();

        AttributedString atribuido = new AttributedString(texto);
        atribuido.addAttribute(TextAttribute.FONT, FONTE_ACAO);
        atribuido.addAttribute(TextAttribute.FOREGROUND, TemaSakura.TEXTO_SUAVE);

        atribuido.addAttribute(TextAttribute.FONT, fonteNome, 0, fimNome);
        // ROSA_HOVER sobre a pílula: contraste ~5,8:1.
        atribuido.addAttribute(TextAttribute.FOREGROUND, TemaSakura.ROSA_HOVER, 0, fimNome);

        atribuido.addAttribute(TextAttribute.FOREGROUND, TemaSakura.TEXTO_HORARIO,
                inicioHora, texto.length());

        return new TextLayout(atribuido.getIterator(), contexto);
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            TextLayout texto = layout(g2.getFontRenderContext());

            int larguraTexto = (int) Math.ceil(texto.getAdvance());
            int largura = Math.min(
                    getWidth() - 2,
                    larguraTexto + TAMANHO_FLOR + ESPACO_FLOR + 2 * PREENCHIMENTO_H
            );

            float x = (getWidth() - largura) / 2f;
            float altura = getHeight() - 4f;
            float y = 2f;

            RoundRectangle2D pilula = new RoundRectangle2D.Float(
                    x, y, largura, altura, altura, altura);

            g2.setColor(TemaSakura.ROSA_HOVER_CLARO);
            g2.fill(pilula);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(TemaSakura.ROSA_BORDA);
            g2.draw(pilula);

            int xFlor = Math.round(x) + PREENCHIMENTO_H;
            int yFlor = (getHeight() - TAMANHO_FLOR) / 2;
            desenharFlor(g2, xFlor, yFlor);

            float linhaBase = (getHeight() - (texto.getAscent() + texto.getDescent())) / 2f
                    + texto.getAscent();

            texto.draw(g2, xFlor + TAMANHO_FLOR + ESPACO_FLOR, linhaBase);

        } finally {
            g2.dispose();
        }
    }

    private void desenharFlor(Graphics2D g2, int x, int y) {

        Graphics2D gf = (Graphics2D) g2.create();

        try {
            if (!entrada) {
                // Saída: flor esmaecida.
                gf.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            }

            int pixels = (int) Math.ceil(TAMANHO_FLOR * gf.getTransform().getScaleX());
            BufferedImage flor = RecursosSakura.florEmPixels(pixels).orElse(null);

            if (flor == null) {
                IconeSakura.desenhar(gf, x + TAMANHO_FLOR / 2.0, y + TAMANHO_FLOR / 2.0,
                        TAMANHO_FLOR / 2.0 - 1);
            } else {
                gf.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gf.drawImage(flor, x, y, TAMANHO_FLOR, TAMANHO_FLOR, null);
            }
        } finally {
            gf.dispose();
        }
    }
}
