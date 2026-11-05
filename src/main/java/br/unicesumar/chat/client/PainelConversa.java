package br.unicesumar.chat.client;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

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

    static final int DURACAO_ENTRADA_MS = 180;
    static final int DESLOCAMENTO_ENTRADA = 8;

    /** Em rajadas, no máximo 3 entradas animadas a cada 600 ms. */
    static final int MAXIMO_ANIMADAS = 3;
    static final long JANELA_RAJADA_MS = 600;

    private final List<String> linhas = new ArrayList<>();
    private String usuarioAtual = "";

    // Animação de entrada: somente para itens que chegam após o histórico.
    private boolean historicoConcluido;
    private final transient Map<Component, Long> entradas = new IdentityHashMap<>();
    private final transient LimitadorRajada limitador =
            new LimitadorRajada(MAXIMO_ANIMADAS, JANELA_RAJADA_MS);
    private transient LongSupplier relogio = Transicao::agora;
    private final transient AnimadorSwing animador =
            new AnimadorSwing(15, this::passoEntradas);

    PainelConversa() {
        setLayout(new LayoutConversa());
        // Transparente: o fundo floral fica no cartão da conversa.
        setOpaque(false);

        // Clique direito em área vazia: opções de animação.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mostrarOpcoes(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mostrarOpcoes(e);
            }
        });
    }

    private void mostrarOpcoes(MouseEvent evento) {
        if (evento.isPopupTrigger() && isShowing()) {
            MenuConversa.criar().show(this, evento.getX(), evento.getY());
            evento.consume();
        }
    }

    void definirRelogio(LongSupplier novoRelogio) {
        this.relogio = novoRelogio;
    }

    boolean historicoConcluido() {
        return historicoConcluido;
    }

    boolean animandoEntrada(Component item) {
        return entradas.containsKey(item);
    }

    /** Progresso da entrada (0 a 1); 1 para itens sem animação. */
    double progressoEntrada(Component item, long agora) {
        Long inicio = entradas.get(item);
        if (inicio == null) {
            return 1;
        }
        double t = (agora - inicio) / (double) DURACAO_ENTRADA_MS;
        return t >= 1 ? 1 : Transicao.suavizar(Math.max(0, t));
    }

    private void registrarEntrada(JComponent item, FormatoLinhaChat.Linha linha) {

        if (linha.tipo() == FormatoLinhaChat.Tipo.SEPARADOR_HISTORICO
                && FormatoLinhaChat.FIM_HISTORICO.equals(linha.texto())) {
            historicoConcluido = true;
            return;
        }

        boolean animavel = item instanceof BalaoMensagem || item instanceof AvisoPresenca;
        long agora = relogio.getAsLong();

        if (historicoConcluido
                && animavel
                && ConfiguracaoAnimacoes.transicoesAtivas()
                && limitador.permitir(agora)) {
            entradas.put(item, agora);
            animador.iniciar();
        }
    }

    private boolean passoEntradas() {

        long agora = relogio.getAsLong();

        entradas.entrySet().removeIf(entrada -> {
            Component item = entrada.getKey();
            repaint(item.getX(), item.getY() - 2,
                    item.getWidth(), item.getHeight() + DESLOCAMENTO_ENTRADA + 4);
            return agora - entrada.getValue() >= DURACAO_ENTRADA_MS;
        });

        return !entradas.isEmpty();
    }

    /**
     * Itens em animação de entrada são pintados com opacidade e um pequeno
     * deslocamento vertical. O layout não muda: a rolagem não é afetada.
     */
    @Override
    protected void paintChildren(Graphics g) {

        if (entradas.isEmpty()) {
            super.paintChildren(g);
            return;
        }

        long agora = relogio.getAsLong();
        Rectangle recorte = g.getClipBounds();

        for (Component item : getComponents()) {

            Rectangle area = item.getBounds();

            if (!item.isVisible()
                    || (recorte != null && !recorte.intersects(
                    area.x, area.y, area.width, area.height + DESLOCAMENTO_ENTRADA))) {
                continue;
            }

            double progresso = progressoEntrada(item, agora);
            int deslocamento = (int) Math.round((1 - progresso) * DESLOCAMENTO_ENTRADA);

            Graphics2D gi = (Graphics2D) g.create(
                    area.x, area.y + deslocamento, area.width, area.height);

            try {
                if (progresso < 1) {
                    gi.setComposite(AlphaComposite.SrcOver.derive((float) progresso));
                }
                item.paint(gi);
            } finally {
                gi.dispose();
            }
        }
    }

    void definirUsuarioAtual(String nome) {
        usuarioAtual = nome == null ? "" : nome;
    }

    void limpar() {
        linhas.clear();
        entradas.clear();
        limitador.limpar();
        historicoConcluido = false;
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

        FormatoLinhaChat.Linha analisada = FormatoLinhaChat.analisar(original);
        JComponent item = criarItem(analisada);

        add(item);
        registrarEntrada(item, analisada);

        revalidate();
        repaint();
    }

    private JComponent criarItem(FormatoLinhaChat.Linha linha) {
        return switch (linha.tipo()) {
            case MENSAGEM -> new BalaoMensagem(linha, linha.enviadaPor(usuarioAtual));
            case SISTEMA -> new LinhaTexto(linha.texto(), LinhaTexto.Estilo.SISTEMA);
            case SEPARADOR_HISTORICO -> new SeparadorHistorico(linha.texto());
            case AVISO_PRESENCA -> new AvisoPresenca(linha);
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
