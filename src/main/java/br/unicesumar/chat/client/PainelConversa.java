package br.unicesumar.chat.client;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conversa exibida em componentes individuais (balões, linhas do sistema
 * e separadores), empilhados verticalmente e ajustados à largura da área
 * rolável, sem rolagem horizontal.
 *
 * As linhas recebidas são guardadas exatamente como chegaram: {@link #texto()}
 * devolve cada linha seguida de "\n", o mesmo conteúdo exportado antes.
 * Deve ser usado somente na Event Dispatch Thread.
 */
final class PainelConversa extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    static final int MARGEM_H = 14;
    static final int MARGEM_V = 12;
    static final int ESPACO = 7;

    private final List<String> linhas = new ArrayList<>();
    private String usuarioAtual = "";

    PainelConversa() {
        setLayout(new LayoutConversa());
        // Transparente: o fundo floral fica no cartão da conversa.
        setOpaque(false);
    }

    void definirUsuarioAtual(String nome) {
        usuarioAtual = nome == null ? "" : nome;
    }

    void limpar() {
        linhas.clear();
        removeAll();
        revalidate();
        repaint();
    }

    /** Texto puro da conversa (base da exportação TXT). */
    String texto() {
        StringBuilder texto = new StringBuilder();
        for (String linha : linhas) {
            texto.append(linha).append('\n');
        }
        return texto.toString();
    }

    void adicionarLinha(String linha) {

        String original = Objects.requireNonNullElse(linha, "");
        linhas.add(original);

        add(criarItem(FormatoLinhaChat.analisar(original)));

        revalidate();
        repaint();
    }

    private JComponent criarItem(FormatoLinhaChat.Linha linha) {
        return switch (linha.tipo()) {
            case MENSAGEM -> new BalaoMensagem(linha, linha.enviadaPor(usuarioAtual));
            case SISTEMA -> new LinhaTexto(linha.texto(), LinhaTexto.Estilo.SISTEMA);
            case SEPARADOR_HISTORICO -> new SeparadorHistorico(linha.texto());
            default -> new LinhaTexto(linha.texto(), LinhaTexto.Estilo.PADRAO);
        };
    }

    // Scrollable: acompanha a largura da área visível (sem rolagem horizontal).

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visivel, int orientacao, int direcao) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visivel, int orientacao, int direcao) {
        return Math.max(16, visivel.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport area
                && area.getHeight() > getPreferredSize().height;
    }

    /** Empilha os itens, alinhando cada um conforme o seu tipo. */
    private static final class LayoutConversa implements LayoutManager {

        @Override
        public void addLayoutComponent(String nome, Component componente) {
        }

        @Override
        public void removeLayoutComponent(Component componente) {
        }

        /** Largura de referência: a da área visível, quando houver. */
        private static int largura(Container painel) {
            if (painel.getParent() instanceof JViewport area && area.getWidth() > 0) {
                return area.getWidth();
            }
            return painel.getWidth();
        }

        @Override
        public Dimension preferredLayoutSize(Container painel) {

            int largura = largura(painel);
            int disponivel = Math.max(1, largura - 2 * MARGEM_H);
            int altura = 2 * MARGEM_V;

            Component[] itens = painel.getComponents();

            for (int i = 0; i < itens.length; i++) {
                if (itens[i] instanceof ItemConversa item) {
                    altura += item.tamanhoPara(disponivel).height;
                    if (i < itens.length - 1) {
                        altura += ESPACO;
                    }
                }
            }

            return new Dimension(largura, altura);
        }

        @Override
        public Dimension minimumLayoutSize(Container painel) {
            return new Dimension(0, 0);
        }

        @Override
        public void layoutContainer(Container painel) {

            int largura = painel.getWidth();
            int disponivel = Math.max(1, largura - 2 * MARGEM_H);
            int y = MARGEM_V;

            for (Component componente : painel.getComponents()) {

                if (!(componente instanceof ItemConversa item)) {
                    continue;
                }

                Dimension tamanho = item.tamanhoPara(disponivel);

                int x = switch (item.alinhamento()) {
                    case ESQUERDA, LARGURA_TOTAL -> MARGEM_H;
                    case DIREITA -> largura - MARGEM_H - tamanho.width;
                };

                componente.setBounds(x, y, tamanho.width, tamanho.height);
                y += tamanho.height + ESPACO;
            }
        }
    }
}
