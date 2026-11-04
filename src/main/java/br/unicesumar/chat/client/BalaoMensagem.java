package br.unicesumar.chat.client;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Balão de uma mensagem de participante: nome e horário no topo e o
 * conteúdo com quebra automática. Mensagens próprias ficam à direita,
 * em rosa-claro; as dos demais, à esquerda, em branco.
 *
 * Apenas apresentação: a linha original é mantida pelo PainelConversa.
 */
final class BalaoMensagem extends JComponent implements ItemConversa {

    private static final long serialVersionUID = 1L;

    /** Fração máxima da largura disponível ocupada por um balão. */
    static final double FRACAO_MAXIMA = 0.72;

    static final int PREENCHIMENTO_H = 14;
    private static final int PREENCHIMENTO_V = 8;
    private static final int ESPACO_CABECALHO = 3;
    private static final int ESPACO_NOME_HORARIO = 10;
    private static final int SOMBRA = 2;
    private static final float RAIO = 18f;

    private final String remetente;
    private final String horario;
    private final String conteudo;
    private final boolean propria;
    private final Color corNome;
    private final Font fonteNome;

    private transient MenuMensagem.Copiador copiador =
            MenuMensagem.AREA_DE_TRANSFERENCIA;
    private transient MenuMensagem.ExibidorMenu exibidorMenu =
            MenuMensagem.EXIBIR_NA_TELA;
    private transient JPopupMenu menu;

    BalaoMensagem(FormatoLinhaChat.Linha linha, boolean propria) {

        if (linha.tipo() != FormatoLinhaChat.Tipo.MENSAGEM) {
            throw new IllegalArgumentException("A linha não é uma mensagem.");
        }

        this.remetente = linha.remetente();
        this.horario = semColchetes(linha.horario());
        this.conteudo = linha.conteudo();
        this.propria = propria;

        Color[] cores = TemaSakura.CORES_PARTICIPANTES;
        this.corNome = propria
                ? TemaSakura.ROSA_ESCURO
                : cores[FormatoLinhaChat.indiceCor(remetente, cores.length)];

        // Nunito para o nome; fonte composta se o nome tiver, por
        // exemplo, um emoji que a Nunito não possui.
        this.fonteNome = TemaSakura.fonteQueExibe(TemaSakura.FONTE_NOME, remetente);

        setOpaque(false);

        // Menu de contexto em qualquer ponto do balão (nome, horário ou
        // texto). O gatilho varia por plataforma: no Windows ocorre ao
        // soltar o botão direito; em outras, ao pressionar.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                verificarMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                verificarMenu(e);
            }
        });
    }

    private void verificarMenu(MouseEvent evento) {
        if (evento.isPopupTrigger()) {
            exibidorMenu.exibir(menuDeContexto(), this, evento.getX(), evento.getY());
            evento.consume();
        }
    }

    JPopupMenu menuDeContexto() {
        if (menu == null) {
            menu = MenuMensagem.criar(this::copiarMensagem);
        }
        return menu;
    }

    /**
     * Copia somente o conteúdo da mensagem (sem nome, horário ou
     * prefixos do protocolo), exatamente como recebido.
     */
    boolean copiarMensagem() {
        return MenuMensagem.copiarComSeguranca(copiador, conteudo);
    }

    void definirCopiador(MenuMensagem.Copiador novoCopiador) {
        this.copiador = novoCopiador;
    }

    void definirExibidorMenu(MenuMensagem.ExibidorMenu novoExibidor) {
        this.exibidorMenu = novoExibidor;
    }

    Font fonteNome() {
        return fonteNome;
    }

    /** "[04/11 15:02]" é exibido como "04/11 15:02". */
    static String semColchetes(String horario) {
        if (horario.length() >= 2
                && horario.startsWith("[")
                && horario.endsWith("]")) {
            return horario.substring(1, horario.length() - 1);
        }
        return horario;
    }

    String remetente() {
        return remetente;
    }

    String horarioExibido() {
        return horario;
    }

    String conteudo() {
        return conteudo;
    }

    boolean propria() {
        return propria;
    }

    @Override
    public Alinhamento alinhamento() {
        return propria ? Alinhamento.DIREITA : Alinhamento.ESQUERDA;
    }

    @Override
    public Dimension tamanhoPara(int larguraDisponivel) {

        int interno = larguraInterna(larguraDisponivel);
        List<String> linhas = linhasPara(interno);

        return new Dimension(
                interno + 2 * PREENCHIMENTO_H,
                alturaPara(linhas.size())
        );
    }

    /** Largura do texto: o necessário, até o limite do balão. */
    private int larguraInterna(int larguraDisponivel) {

        FontMetrics texto = getFontMetrics(TemaSakura.FONTE_TEXTO);

        int maxima = Math.max(
                40,
                (int) (larguraDisponivel * FRACAO_MAXIMA) - 2 * PREENCHIMENTO_H
        );

        int natural = Math.max(larguraCabecalho(), texto.stringWidth(conteudo));

        return Math.min(maxima, natural);
    }

    private int larguraCabecalho() {
        return getFontMetrics(fonteNome).stringWidth(remetente)
                + ESPACO_NOME_HORARIO
                + getFontMetrics(TemaSakura.FONTE_PEQUENA).stringWidth(horario);
    }

    List<String> linhasPara(int larguraInterna) {
        return QuebraTexto.quebrar(
                conteudo,
                getFontMetrics(TemaSakura.FONTE_TEXTO),
                larguraInterna
        );
    }

    private int alturaPara(int quantidadeLinhas) {
        return PREENCHIMENTO_V
                + getFontMetrics(fonteNome).getHeight()
                + ESPACO_CABECALHO
                + quantidadeLinhas * getFontMetrics(TemaSakura.FONTE_TEXTO).getHeight()
                + PREENCHIMENTO_V
                + SOMBRA;
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = TemaSakura.suavizar(g);

        try {
            float largura = getWidth() - 1f;
            float altura = getHeight() - SOMBRA - 1f;

            RoundRectangle2D corpo = new RoundRectangle2D.Float(
                    0.5f, 0.5f, largura - 0.5f, altura, RAIO, RAIO
            );

            g2.setColor(TemaSakura.SOMBRA_SUAVE);
            g2.fill(new RoundRectangle2D.Float(
                    0.5f, 0.5f + SOMBRA, largura - 0.5f, altura, RAIO, RAIO
            ));

            g2.setPaint(propria
                    ? new GradientPaint(0, 0, TemaSakura.BALAO_PROPRIO_TOPO,
                            0, altura, TemaSakura.BALAO_PROPRIO_BASE)
                    : new GradientPaint(0, 0, Color.WHITE,
                            0, altura, TemaSakura.BALAO_OUTRO_BASE));
            g2.fill(corpo);

            g2.setStroke(new BasicStroke(1.1f));
            g2.setColor(propria
                    ? TemaSakura.BALAO_PROPRIO_BORDA
                    : TemaSakura.ROSA_BORDA);
            g2.draw(corpo);

            desenharCabecalho(g2);
            desenharConteudo(g2);

        } finally {
            g2.dispose();
        }
    }

    private void desenharCabecalho(Graphics2D g2) {

        FontMetrics nome = g2.getFontMetrics(fonteNome);
        FontMetrics hora = g2.getFontMetrics(TemaSakura.FONTE_PEQUENA);

        int esquerda = PREENCHIMENTO_H;
        int direita = getWidth() - PREENCHIMENTO_H;
        int linhaBase = PREENCHIMENTO_V + nome.getAscent();

        int larguraHora = hora.stringWidth(horario);
        int espacoNome = Math.max(0, direita - esquerda - larguraHora - ESPACO_NOME_HORARIO);

        g2.setFont(fonteNome);
        g2.setColor(corNome);
        g2.drawString(abreviar(remetente, nome, espacoNome), esquerda, linhaBase);

        g2.setFont(TemaSakura.FONTE_PEQUENA);
        g2.setColor(TemaSakura.TEXTO_HORARIO);
        g2.drawString(horario, direita - larguraHora, linhaBase);
    }

    private void desenharConteudo(Graphics2D g2) {

        FontMetrics nome = g2.getFontMetrics(fonteNome);
        FontMetrics texto = g2.getFontMetrics(TemaSakura.FONTE_TEXTO);

        int interno = getWidth() - 2 * PREENCHIMENTO_H;
        int y = PREENCHIMENTO_V + nome.getHeight() + ESPACO_CABECALHO + texto.getAscent();

        g2.setFont(TemaSakura.FONTE_TEXTO);
        g2.setColor(TemaSakura.TEXTO);

        for (String linha : linhasPara(interno)) {
            g2.drawString(linha, PREENCHIMENTO_H, y);
            y += texto.getHeight();
        }
    }

    private static String abreviar(String texto, FontMetrics metricas, int largura) {

        if (metricas.stringWidth(texto) <= largura) {
            return texto;
        }

        String reticencias = "…";
        int fim = texto.length();

        while (fim > 0
                && metricas.stringWidth(texto.substring(0, fim) + reticencias) > largura) {
            fim--;
            // Não separa pares substitutos (por exemplo, emojis).
            if (fim > 0 && Character.isHighSurrogate(texto.charAt(fim - 1))) {
                fim--;
            }
        }

        return texto.substring(0, fim) + reticencias;
    }
}
