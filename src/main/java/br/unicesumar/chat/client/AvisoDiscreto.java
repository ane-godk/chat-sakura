package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;

/**
 * Faixa de aviso não modal sobre a conversa, para erros recuperáveis
 * (limite de envio, histórico indisponível, conversa não encontrada...) e
 * para o estado da reconexão.
 *
 * Há uma única faixa por janela: um aviso novo substitui o anterior, então
 * avisos nunca se empilham. Avisos comuns somem sozinhos; os persistentes
 * (reconexão) ficam até serem trocados ou fechados. As cores são lidas da
 * paleta ativa a cada pintura (temas Claro e Noturno). Use na EDT.
 */
final class AvisoDiscreto extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int DURACAO_PADRAO_MS = 7_000;

    enum Tipo {
        /** Erro recuperável: tom coral. */
        ERRO,
        /** Informação ou andamento (reconexão): tom âmbar. */
        INFORMACAO
    }

    private final JLabel texto = new JLabel();
    private final BotaoSakura acao = new BotaoSakura("", BotaoSakura.Estilo.SECUNDARIO);
    private final BotaoSakura fechar = new BotaoSakura("Fechar", BotaoSakura.Estilo.SECUNDARIO);
    private final Timer temporizador;

    private Tipo tipo = Tipo.ERRO;
    private ActionListener acaoAtual;
    private int exibicoes;

    AvisoDiscreto() {
        this(DURACAO_PADRAO_MS);
    }

    AvisoDiscreto(int duracaoMs) {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 8));

        texto.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botoes.setOpaque(false);
        botoes.add(acao);
        botoes.add(fechar);

        add(texto, BorderLayout.CENTER);
        add(botoes, BorderLayout.EAST);

        temporizador = new Timer(duracaoMs, e -> ocultar());
        temporizador.setRepeats(false);

        fechar.addActionListener(e -> ocultar());
        fechar.getAccessibleContext().setAccessibleName("Fechar aviso");
        acao.setVisible(false);

        setVisible(false);
    }

    /** Aviso temporário (some após alguns segundos). */
    void mostrar(String mensagem, Tipo novoTipo) {
        mostrar(mensagem, novoTipo, false, null, null);
    }

    /**
     * @param persistente {@code true}: fica até ser trocado ou fechado
     * @param rotuloAcao  botão opcional (por exemplo "Cancelar"); nulo para nenhum
     */
    void mostrar(String mensagem, Tipo novoTipo, boolean persistente, String rotuloAcao, Runnable aoAcionar) {

        tipo = novoTipo;
        exibicoes++;
        texto.setText(mensagem);
        texto.setToolTipText(mensagem);
        getAccessibleContext().setAccessibleName("Aviso: " + mensagem);

        if (acaoAtual != null) {
            acao.removeActionListener(acaoAtual);
            acaoAtual = null;
        }
        if (rotuloAcao != null && aoAcionar != null) {
            acao.setText(rotuloAcao);
            acaoAtual = e -> {
                ocultar();
                aoAcionar.run();
            };
            acao.addActionListener(acaoAtual);
            acao.setVisible(true);
        } else {
            acao.setVisible(false);
        }

        temporizador.stop();
        if (!persistente) {
            temporizador.restart();
        }

        setVisible(true);
        revalidate();
        repaint();
    }

    void ocultar() {
        temporizador.stop();
        setVisible(false);
        revalidate();
    }

    String mensagem() {
        return texto.getText();
    }

    Tipo tipo() {
        return tipo;
    }

    /** Quantas vezes um aviso foi mostrado (diagnóstico e testes). */
    int exibicoes() {
        return exibicoes;
    }

    boolean temporario() {
        return temporizador.isRunning();
    }

    boolean temAcao() {
        return acao.isVisible();
    }

    void acionar() {
        if (acaoAtual != null) {
            acao.doClick();
        }
    }

    private Color fundo() {
        PaletaSakura cores = TemaSakura.cores();
        Color base = tipo == Tipo.ERRO ? cores.coral() : cores.ambar();
        Color cartao = cores.cartao();
        // Mistura suave com o cartão: legível nos dois temas.
        return new Color(
                (base.getRed() + cartao.getRed() * 3) / 4,
                (base.getGreen() + cartao.getGreen() * 3) / 4,
                (base.getBlue() + cartao.getBlue() * 3) / 4);
    }

    @Override
    protected void paintComponent(Graphics g) {
        PaletaSakura cores = TemaSakura.cores();
        texto.setForeground(tipo == Tipo.ERRO ? cores.coralTexto() : cores.ambarTexto());

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fundo());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.setColor(tipo == Tipo.ERRO ? cores.coral() : cores.ambar());
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
