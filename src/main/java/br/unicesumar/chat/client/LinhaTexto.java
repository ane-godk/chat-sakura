package br.unicesumar.chat.client;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Linha de texto da conversa que não é mensagem de participante.
 *
 * {@link Estilo#SISTEMA}: centralizada, em itálico e discreta, dentro de
 * uma pílula suave (o prefixo "SISTEMA: " não é exibido).
 * {@link Estilo#PADRAO}: linhas inesperadas, à esquerda, sem destaque.
 */
final class LinhaTexto extends JComponent implements ItemConversa {

    private static final long serialVersionUID = 1L;

    private static final int PREENCHIMENTO_H = 12;
    private static final int PREENCHIMENTO_V = 4;
    private static final double FRACAO_SISTEMA = 0.86;

    enum Estilo {
        SISTEMA,
        PADRAO
    }

    private final Estilo estilo;
    private final String textoExibido;

    LinhaTexto(String linhaOriginal, Estilo estilo) {
        this.estilo = estilo;

        String texto = linhaOriginal == null ? "" : linhaOriginal;

        this.textoExibido = estilo == Estilo.SISTEMA
                && texto.startsWith(FormatoLinhaChat.PREFIXO_SISTEMA)
                ? texto.substring(FormatoLinhaChat.PREFIXO_SISTEMA.length())
                : texto;

        setOpaque(false);
    }

    Estilo estilo() {
        return estilo;
    }

    String textoExibido() {
        return textoExibido;
    }

    @Override
    public Alinhamento alinhamento() {
        return Alinhamento.LARGURA_TOTAL;
    }

    private Font fonte() {
        return estilo == Estilo.SISTEMA
                ? TemaSakura.FONTE_SISTEMA
                : TemaSakura.FONTE_TEXTO;
    }

    private int larguraTexto(int larguraTotal) {
        return estilo == Estilo.SISTEMA
                ? (int) (larguraTotal * FRACAO_SISTEMA) - 2 * PREENCHIMENTO_H
                : larguraTotal;
    }

    // Cache da quebra: só é refeita quando a largura muda.
    private transient List<String> linhasEmCache;
    private int larguraEmCache = -1;

    List<String> linhasPara(int larguraTotal) {
        if (linhasEmCache == null || larguraTotal != larguraEmCache) {
            linhasEmCache = QuebraTexto.quebrar(
                    textoExibido,
                    getFontMetrics(fonte()),
                    larguraTexto(larguraTotal)
            );
            larguraEmCache = larguraTotal;
        }
        return linhasEmCache;
    }

    @Override
    public Dimension tamanhoPara(int larguraDisponivel) {

        int linhas = linhasPara(larguraDisponivel).size();
        int altura = linhas * getFontMetrics(fonte()).getHeight()
                + 2 * PREENCHIMENTO_V;

        return new Dimension(larguraDisponivel, altura);
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            g2.setFont(fonte());
            FontMetrics metricas = g2.getFontMetrics();
            List<String> linhas = linhasPara(getWidth());

            int y = PREENCHIMENTO_V + metricas.getAscent();

            if (estilo == Estilo.PADRAO) {
                g2.setColor(TemaSakura.TEXTO);
                for (String linha : linhas) {
                    g2.drawString(linha, 0, y);
                    y += metricas.getHeight();
                }
                return;
            }

            int maiorLinha = 0;
            for (String linha : linhas) {
                maiorLinha = Math.max(maiorLinha, metricas.stringWidth(linha));
            }

            float larguraPilula = maiorLinha + 2f * PREENCHIMENTO_H;
            float altura = getHeight() - 1f;

            g2.setColor(TemaSakura.ROSA_HOVER_CLARO);
            g2.fill(new RoundRectangle2D.Float(
                    (getWidth() - larguraPilula) / 2f, 0.5f,
                    larguraPilula, altura,
                    Math.min(altura, 22f), Math.min(altura, 22f)
            ));

            g2.setColor(TemaSakura.TEXTO_SUAVE);
            for (String linha : linhas) {
                int x = (getWidth() - metricas.stringWidth(linha)) / 2;
                g2.drawString(linha, x, y);
                y += metricas.getHeight();
            }

        } finally {
            g2.dispose();
        }
    }
}
