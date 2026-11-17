package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa.Tipo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Barra lateral de conversas: Chat Geral, privadas e grupos, com avatar,
 * prévia da última mensagem e contador de não lidas. As cores são lidas da
 * paleta ativa a cada pintura (Sakura Claro e Noturno). Usada só na EDT.
 */
final class BarraConversas extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int LARGURA = 262;
    private static final int ALTURA_ITEM = 58;
    private static final int AVATAR = 36;

    private final DefaultListModel<ControladorConversas.ItemLateral> modelo = new DefaultListModel<>();
    private final JList<ControladorConversas.ItemLateral> lista = new JList<>(modelo);

    final BotaoSakura botaoPrivada = new BotaoSakura("Privada", BotaoSakura.Estilo.SECUNDARIO);
    final BotaoSakura botaoGrupo = new BotaoSakura("Grupo", BotaoSakura.Estilo.SECUNDARIO);
    final BotaoSakura botaoIA = new BotaoSakura("IA", BotaoSakura.Estilo.SECUNDARIO);

    private boolean atualizando;

    BarraConversas(LongConsumer aoSelecionar) {
        super(new BorderLayout(0, TemaSakura.ESPACO_P));
        Objects.requireNonNull(aoSelecionar);
        setOpaque(false);
        setPreferredSize(new Dimension(LARGURA, 0));

        JLabel titulo = new JLabel("Conversas") {
            private static final long serialVersionUID = 1L;

            @Override
            public Color getForeground() {
                return TemaSakura.cores().rosaEscuro();
            }
        };
        titulo.setFont(TemaSakura.FONTE_NOME);

        botaoPrivada.setToolTipText("Nova conversa privada");
        botaoGrupo.setToolTipText("Novo grupo");
        botaoIA.setToolTipText("Sakura IA: criar personagem (Qwen)");

        // Três colunas iguais: os botões sempre cabem na largura da barra.
        JPanel acoes = new JPanel(new java.awt.GridLayout(1, 3, 4, 0));
        acoes.setOpaque(false);
        acoes.add(botaoPrivada);
        acoes.add(botaoGrupo);
        acoes.add(botaoIA);

        // Título acima dos botões: a barra é estreita demais para os dois lado a lado.
        JPanel topo = new JPanel(new BorderLayout(0, 6));
        topo.setOpaque(false);
        topo.setBorder(new EmptyBorder(6, 8, 0, 6));
        topo.add(titulo, BorderLayout.NORTH);
        topo.add(acoes, BorderLayout.CENTER);

        lista.setOpaque(false);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setFixedCellHeight(ALTURA_ITEM);
        lista.setCellRenderer(new Renderizador());
        lista.getAccessibleContext().setAccessibleName("Lista de conversas");
        lista.addListSelectionListener(e -> {
            ControladorConversas.ItemLateral item = lista.getSelectedValue();
            if (!atualizando && !e.getValueIsAdjusting() && item != null) {
                aoSelecionar.accept(item.id());
            }
        });

        JScrollPane rolagem = new JScrollPane(lista);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        rolagem.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.getVerticalScrollBar().setUI(new BarraRolagemSakura());
        rolagem.getVerticalScrollBar().setOpaque(false);

        PainelArredondado cartao = new PainelArredondado(
                new BorderLayout(0, 4),
                PaletaSakura::cartao,
                PaletaSakura::cartao,
                PaletaSakura::rosaBorda,
                TemaSakura.RAIO_CARTAO
        );
        cartao.setBorder(new EmptyBorder(6, 4, 6, 4));
        cartao.add(topo, BorderLayout.NORTH);
        cartao.add(rolagem, BorderLayout.CENTER);

        add(cartao, BorderLayout.CENTER);
    }

    /** Substitui os itens mantendo a seleção na conversa ativa. */
    void atualizar(List<ControladorConversas.ItemLateral> itens, long idAtiva) {
        atualizando = true;
        try {
            modelo.clear();
            int selecionado = -1;
            for (int i = 0; i < itens.size(); i++) {
                modelo.addElement(itens.get(i));
                if (itens.get(i).id() == idAtiva) {
                    selecionado = i;
                }
            }
            lista.setSelectedIndex(selecionado);
        } finally {
            atualizando = false;
        }
        repaint();
    }

    /** Botões de nova conversa: somente em sessões com conta. */
    void definirContas(boolean habilitado) {
        botaoPrivada.setEnabled(habilitado);
        botaoGrupo.setEnabled(habilitado);
        botaoIA.setEnabled(habilitado);
    }

    int quantidade() {
        return modelo.size();
    }

    ControladorConversas.ItemLateral item(int indice) {
        return modelo.get(indice);
    }

    /** Item pintado com a paleta ativa. */
    private static final class Renderizador extends JPanel
            implements ListCellRenderer<ControladorConversas.ItemLateral> {

        private static final long serialVersionUID = 1L;

        Renderizador() {
            setOpaque(false);
        }

        private ControladorConversas.ItemLateral item;
        private boolean selecionado;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ControladorConversas.ItemLateral> lista,
                ControladorConversas.ItemLateral valor, int indice,
                boolean selecionado, boolean foco) {
            this.item = valor;
            this.selecionado = selecionado;
            getAccessibleContext().setAccessibleName(valor.titulo()
                    + (valor.naoLidas() > 0 ? ", " + valor.naoLidas() + " não lidas" : ""));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = TemaSakura.suavizar(g);
            PaletaSakura cores = TemaSakura.cores();

            try {
                int largura = getWidth();
                int altura = getHeight();

                if (selecionado) {
                    g2.setColor(cores.rosaPetala());
                    g2.fill(new RoundRectangle2D.Float(4, 3, largura - 8, altura - 6, 14, 14));
                }

                // Avatar: flor para o Geral; inicial para privadas e grupos.
                int xAvatar = 10;
                int yAvatar = (altura - AVATAR) / 2;
                if (item.tipo() == Tipo.GERAL) {
                    IconeSakura.desenhar(g2, xAvatar + AVATAR / 2.0, yAvatar + AVATAR / 2.0, AVATAR / 2.0 - 1);
                } else if (item.avatarId() != null) {
                    // Privada: avatar da outra pessoa; IA: marcador do personagem.
                    AvatarSakura.desenhar(g2, item.avatarId(), xAvatar, yAvatar, AVATAR);
                } else {
                    g2.setColor(item.tipo() == Tipo.GRUPO ? cores.rosaHover() : cores.rosa());
                    g2.fill(new Ellipse2D.Float(xAvatar, yAvatar, AVATAR, AVATAR));
                    g2.setColor(cores.textoBotaoPrincipal());
                    g2.setFont(TemaSakura.FONTE_NOME);
                    String inicial = item.tipo() == Tipo.GRUPO ? "#" : inicial(item.titulo());
                    FontMetrics m = g2.getFontMetrics();
                    g2.drawString(inicial, xAvatar + (AVATAR - m.stringWidth(inicial)) / 2,
                            yAvatar + (AVATAR - m.getHeight()) / 2 + m.getAscent());
                }

                int xTexto = xAvatar + AVATAR + 10;
                int larguraBadge = 0;

                if (item.naoLidas() > 0) {
                    String quantidade = item.naoLidas() > 99 ? "99+" : String.valueOf(item.naoLidas());
                    g2.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
                    FontMetrics m = g2.getFontMetrics();
                    larguraBadge = Math.max(20, m.stringWidth(quantidade) + 12);
                    int xBadge = largura - larguraBadge - 12;
                    int yBadge = (altura - 20) / 2;
                    g2.setColor(cores.rosaEscuro());
                    g2.fill(new RoundRectangle2D.Float(xBadge, yBadge, larguraBadge, 20, 20, 20));
                    g2.setColor(cores.textoBotaoPrincipal());
                    g2.drawString(quantidade, xBadge + (larguraBadge - m.stringWidth(quantidade)) / 2,
                            yBadge + (20 - m.getHeight()) / 2 + m.getAscent());
                    larguraBadge += 8;
                }

                int disponivel = largura - xTexto - 12 - larguraBadge;

                g2.setFont(TemaSakura.FONTE_NOME);
                g2.setColor(cores.texto());
                g2.drawString(cortar(item.titulo(), g2.getFontMetrics(), disponivel), xTexto, altura / 2 - 3);

                g2.setFont(TemaSakura.FONTE_PEQUENA);
                g2.setColor(cores.textoSuave());
                String previa = item.previa().isBlank() ? "Sem mensagens" : item.previa();
                g2.drawString(cortar(previa, g2.getFontMetrics(), disponivel), xTexto, altura / 2 + 15);

            } finally {
                g2.dispose();
            }
        }

        private static String inicial(String titulo) {
            return titulo.isBlank() ? "?" : titulo.substring(0, titulo.offsetByCodePoints(0, 1)).toUpperCase();
        }

        private static String cortar(String texto, FontMetrics metricas, int largura) {
            if (metricas.stringWidth(texto) <= largura) {
                return texto;
            }
            String reticencias = "…";
            int fim = texto.length();
            while (fim > 0 && metricas.stringWidth(texto.substring(0, fim) + reticencias) > largura) {
                fim--;
            }
            return texto.substring(0, fim) + reticencias;
        }
    }
}
