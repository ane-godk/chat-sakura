package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Miniatura da janela com uma aparência candidata: fundo, cabeçalho,
 * fundo da conversa, balões, campo e botão. Pinta com a paleta da
 * candidata (não com a ativa), por isso nada muda na janela até "Aplicar".
 */
final class PreviaAparencia extends JPanel {

    private static final long serialVersionUID = 1L;

    private Aparencia aparencia;

    // Fundo da conversa em cache (refeito só se tamanho, escala ou aparência mudarem).
    private transient BufferedImage fundo;
    private transient Aparencia aparenciaDoFundo;
    private int larguraDoFundo;
    private int alturaDoFundo;
    private double escalaDoFundo;

    PreviaAparencia(Aparencia inicial) {
        this.aparencia = inicial;
        setOpaque(false);
        setPreferredSize(new Dimension(320, 240));
        atualizarDescricao();
    }

    Aparencia aparencia() {
        return aparencia;
    }

    /** Troca a candidata e repinta (na EDT). */
    void mostrar(Aparencia nova) {
        if (!nova.equals(aparencia)) {
            aparencia = nova;
            atualizarDescricao();
            repaint();
        }
    }

    private void atualizarDescricao() {
        getAccessibleContext().setAccessibleName("Prévia: " + DialogoAparencia.descrever(aparencia));
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w < 40 || h < 40) {
            return;
        }

        PaletaSakura c = PaletasFlorais.de(aparencia);
        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            // Janela.
            RoundRectangle2D janela = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, 16, 16);
            g2.setColor(c.fundo());
            g2.fill(janela);
            g2.setColor(c.rosaBorda());
            g2.draw(janela);

            // Cabeçalho com o nome do app.
            int margem = 10;
            RoundRectangle2D cabecalho = new RoundRectangle2D.Float(margem, margem, w - 2f * margem, 30, 12, 12);
            g2.setColor(c.cartao());
            g2.fill(cabecalho);
            g2.setColor(c.rosaBorda());
            g2.draw(cabecalho);
            g2.setFont(TemaSakura.FONTE_NOME);
            g2.setColor(c.rosaEscuro());
            g2.drawString("Chat Sakura", margem + 12, margem + 20);

            // Conversa.
            int topo = margem + 38;
            int alturaCampo = 30;
            int alturaConversa = h - topo - alturaCampo - margem - 8;
            int larguraConversa = w - 2 * margem;
            desenharFundo(g2, c, margem, topo, larguraConversa, alturaConversa);

            g2.setFont(TemaSakura.FONTE_PEQUENA);
            balao(g2, c, false, "Bia", "Olá! Tudo bem?", margem + 10, topo + 12, larguraConversa);
            balao(g2, c, true, "Você", "Tudo ótimo 🌸", margem + 10, topo + 58, larguraConversa);

            // Campo e botão de enviar.
            int yCampo = h - margem - alturaCampo;
            RoundRectangle2D campo = new RoundRectangle2D.Float(margem, yCampo, w - 2f * margem, alturaCampo, 14, 14);
            g2.setColor(c.cartao());
            g2.fill(campo);
            g2.setColor(c.rosaBorda());
            g2.draw(campo);
            g2.setColor(c.textoSuave());
            g2.drawString("Digite sua mensagem...", margem + 10, yCampo + 19);

            int larguraBotao = 64;
            int xBotao = w - margem - larguraBotao - 4;
            RoundRectangle2D botao = new RoundRectangle2D.Float(xBotao, yCampo + 4, larguraBotao, alturaCampo - 8, 12, 12);
            g2.setPaint(new GradientPaint(0, yCampo, c.botaoPrincipalTopo(), 0, yCampo + alturaCampo,
                    c.botaoPrincipalBase()));
            g2.fill(botao);
            g2.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
            g2.setColor(c.textoBotaoPrincipal());
            FontMetrics m = g2.getFontMetrics();
            g2.drawString("Enviar", xBotao + (larguraBotao - m.stringWidth("Enviar")) / 2, yCampo + 19);
        } finally {
            g2.dispose();
        }
    }

    private void desenharFundo(Graphics2D g2, PaletaSakura c, int x, int y, int largura, int altura) {
        double escala = g2.getTransform().getScaleX();
        if (fundo == null || !aparencia.equals(aparenciaDoFundo) || largura != larguraDoFundo
                || altura != alturaDoFundo || escala != escalaDoFundo) {
            fundo = DecoracaoFundo.compor(c, aparencia.paleta(), aparencia.fundo(), largura, altura, escala);
            aparenciaDoFundo = aparencia;
            larguraDoFundo = largura;
            alturaDoFundo = altura;
            escalaDoFundo = escala;
        }
        g2.drawImage(fundo, x, y, largura, altura, null);
    }

    private static void balao(Graphics2D g2, PaletaSakura c, boolean proprio, String nome, String texto,
                              int x, int y, int larguraConversa) {
        FontMetrics m = g2.getFontMetrics();
        int largura = Math.max(m.stringWidth(texto), m.stringWidth(nome)) + 22;
        int altura = 40;
        int bx = proprio ? x + larguraConversa - largura - 20 : x;

        RoundRectangle2D forma = new RoundRectangle2D.Float(bx, y, largura, altura, 14, 14);
        g2.setPaint(new GradientPaint(0, y, proprio ? c.balaoProprioTopo() : c.balaoOutroTopo(),
                0, y + altura, proprio ? c.balaoProprioBase() : c.balaoOutroBase()));
        g2.fill(forma);
        if (proprio) {
            g2.setColor(c.balaoProprioBorda());
            g2.setStroke(new BasicStroke(1f));
            g2.draw(forma);
        }

        g2.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        g2.setColor(proprio ? c.nomeProprio() : c.corParticipante(nome));
        g2.drawString(nome, bx + 11, y + 15);
        g2.setFont(TemaSakura.fonteQueExibe(TemaSakura.FONTE_PEQUENA, texto));
        g2.setColor(c.texto());
        g2.drawString(texto, bx + 11, y + 32);
    }
}
