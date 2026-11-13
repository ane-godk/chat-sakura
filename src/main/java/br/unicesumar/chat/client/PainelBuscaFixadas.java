package br.unicesumar.chat.client;

import br.unicesumar.chat.protocolo.ProtocoloJson;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;

/**
 * Painel lateral (não modal) da conversa ativa, com duas vistas:
 *
 * <ul>
 *   <li><b>Pesquisa:</b> termo, filtros opcionais de remetente e período,
 *       resultados paginados (remetente, data e trecho com o termo em
 *       destaque). Clicar num resultado abre e destaca a mensagem.</li>
 *   <li><b>Fixadas:</b> as mensagens fixadas da conversa; clicar localiza a
 *       mensagem; quem pode fixar também pode desafixar daqui.</li>
 * </ul>
 *
 * Tudo roda na EDT; os pedidos vão ao servidor pelo controlador, sem
 * bloquear. Respostas de uma conversa anterior ou de uma pesquisa já
 * substituída são descartadas. As cores vêm da paleta ativa.
 */
final class PainelBuscaFixadas extends JPanel {

    private static final long serialVersionUID = 1L;

    enum Vista { PESQUISA, FIXADAS }

    static final int LARGURA = 300;
    static final String NENHUM_RESULTADO = "Nenhuma mensagem encontrada.";
    static final String SEM_FIXADAS = "Nenhuma mensagem fixada nesta conversa.";
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");

    /** Opção do filtro de remetente. */
    record Remetente(Long id, String nome) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private final transient ControladorConversas controlador;
    private final Runnable aoFechar;

    private final CardLayout cartoes = new CardLayout();
    private final JPanel conteudo = new JPanel(cartoes);
    private final JLabel titulo = new JLabel();
    private Vista vista = Vista.PESQUISA;
    private long conversaExibida = -1;

    // Pesquisa
    private final CampoTextoSakura campoTermo = new CampoTextoSakura(14, "Palavra ou trecho", null);
    private final JComboBox<Remetente> campoRemetente = new JComboBox<>();
    private final CampoTextoSakura campoDe = new CampoTextoSakura(7, "De dd/mm/aaaa", null);
    private final CampoTextoSakura campoAte = new CampoTextoSakura(7, "Até dd/mm/aaaa", null);
    private final BotaoSakura botaoBuscar = new BotaoSakura("Buscar", BotaoSakura.Estilo.PRINCIPAL);
    private final BotaoSakura botaoMais = new BotaoSakura("Mais resultados", BotaoSakura.Estilo.SECUNDARIO);
    private final JLabel estadoPesquisa = new JLabel(" ");
    private final DefaultListModel<ProtocoloJson.ResultadoJson> resultados = new DefaultListModel<>();
    private final JList<ProtocoloJson.ResultadoJson> listaResultados = new JList<>(resultados);
    private long numeroPesquisa;
    private Consulta ultimaConsulta;
    private Long proximaPagina;

    // Fixadas
    private final JLabel estadoFixadas = new JLabel(" ");
    private final DefaultListModel<ProtocoloJson.FixadaJson> fixadas = new DefaultListModel<>();
    private final JList<ProtocoloJson.FixadaJson> listaFixadas = new JList<>(fixadas);
    private final BotaoSakura botaoDesafixar = new BotaoSakura("Desafixar", BotaoSakura.Estilo.SECUNDARIO);

    /** Pesquisa feita (repetida para a próxima página). */
    private record Consulta(long conversaId, String termo, Long remetenteId, LocalDate de, LocalDate ate) {
    }

    PainelBuscaFixadas(ControladorConversas controlador, Runnable aoFechar) {
        super(new BorderLayout(0, TemaSakura.ESPACO_P));
        this.controlador = Objects.requireNonNull(controlador);
        this.aoFechar = Objects.requireNonNull(aoFechar);
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(LARGURA, 10));

        add(criarTopo(), BorderLayout.NORTH);
        conteudo.setOpaque(false);
        conteudo.add(criarPesquisa(), Vista.PESQUISA.name());
        conteudo.add(criarFixadas(), Vista.FIXADAS.name());
        add(conteudo, BorderLayout.CENTER);
        aplicarTema();
    }

    // ------------------------------------------------------------------ montagem

    private JComponent criarTopo() {
        titulo.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        BotaoSakura fechar = new BotaoSakura("×", BotaoSakura.Estilo.SECUNDARIO);
        fechar.setBorder(new EmptyBorder(2, 8, 2, 8));
        fechar.setToolTipText("Fechar painel");
        fechar.getAccessibleContext().setAccessibleName("Fechar painel");
        fechar.addActionListener(e -> aoFechar.run());
        JPanel topo = new JPanel(new BorderLayout(6, 0));
        topo.setOpaque(false);
        topo.add(titulo, BorderLayout.CENTER);
        topo.add(fechar, BorderLayout.EAST);
        return topo;
    }

    private JComponent criarPesquisa() {
        campoTermo.addActionListener(e -> pesquisar());
        campoTermo.getAccessibleContext().setAccessibleName("Termo da pesquisa");
        botaoBuscar.definirIcone(IconesDesenhados::lupa);
        botaoBuscar.setBorder(new EmptyBorder(6, 10, 6, 10));
        botaoBuscar.addActionListener(e -> pesquisar());

        JPanel linhaTermo = new JPanel(new BorderLayout(6, 0));
        linhaTermo.setOpaque(false);
        linhaTermo.add(campoTermo, BorderLayout.CENTER);
        linhaTermo.add(botaoBuscar, BorderLayout.EAST);

        campoRemetente.setFont(TemaSakura.FONTE_PEQUENA);
        // Seta e fundo na paleta ativa (lidos na pintura), em vez das cores do sistema.
        campoRemetente.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected javax.swing.JButton createArrowButton() {
                javax.swing.JButton seta = new javax.swing.plaf.basic.BasicArrowButton(
                        javax.swing.SwingConstants.SOUTH) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Color getBackground() {
                        return TemaSakura.cores().cartao();
                    }

                    @Override
                    public void paintTriangle(Graphics g, int x, int y, int tamanho, int direcao, boolean ativo) {
                        Color anterior = g.getColor();
                        g.setColor(TemaSakura.cores().textoSuave());
                        int t = Math.max(3, tamanho / 2);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        g.fillPolygon(new int[] {cx - t, cx + t, cx}, new int[] {cy - t / 2, cy - t / 2, cy + t / 2}, 3);
                        g.setColor(anterior);
                    }
                };
                seta.setBorder(BorderFactory.createEmptyBorder());
                return seta;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle limites, boolean comFoco) {
                g.setColor(TemaSakura.cores().cartao());
                g.fillRect(limites.x, limites.y, limites.width, limites.height);
            }

            /** Valor atual na paleta, inclusive desabilitado (sem as cores do sistema). */
            @Override
            public void paintCurrentValue(Graphics g, java.awt.Rectangle limites, boolean comFoco) {
                PaletaSakura c = TemaSakura.cores();
                Object valor = comboBox.getSelectedItem();
                g.setColor(c.cartao());
                g.fillRect(limites.x, limites.y, limites.width, limites.height);
                if (valor != null) {
                    g.setFont(comboBox.getFont());
                    g.setColor(comboBox.isEnabled() ? c.texto() : c.textoSuave());
                    java.awt.FontMetrics m = g.getFontMetrics();
                    ((Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.drawString(valor.toString(), limites.x + 4,
                            limites.y + (limites.height - m.getHeight()) / 2 + m.getAscent());
                }
            }
        });
        campoRemetente.getAccessibleContext().setAccessibleName("Filtrar por remetente");
        campoRemetente.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                                                          boolean selecionado, boolean foco) {
                super.getListCellRendererComponent(lista, valor, indice, selecionado, foco);
                PaletaSakura c = TemaSakura.cores();
                setBackground(selecionado ? c.rosaPetala() : c.cartao());
                setForeground(c.texto());
                setBorder(new EmptyBorder(3, 6, 3, 6));
                return this;
            }
        });
        campoDe.getAccessibleContext().setAccessibleName("Data inicial (dd/mm/aaaa)");
        campoAte.getAccessibleContext().setAccessibleName("Data final (dd/mm/aaaa)");
        campoDe.addActionListener(e -> pesquisar());
        campoAte.addActionListener(e -> pesquisar());

        JPanel periodo = new JPanel(new GridLayout(1, 2, 6, 0));
        periodo.setOpaque(false);
        periodo.add(campoDe);
        periodo.add(campoAte);

        JPanel filtros = new JPanel(new GridLayout(2, 1, 0, 6));
        filtros.setOpaque(false);
        filtros.add(campoRemetente);
        filtros.add(periodo);

        estadoPesquisa.setFont(TemaSakura.FONTE_PEQUENA);

        JPanel cabecalho = new JPanel(new BorderLayout(0, 6));
        cabecalho.setOpaque(false);
        cabecalho.add(linhaTermo, BorderLayout.NORTH);
        cabecalho.add(filtros, BorderLayout.CENTER);
        cabecalho.add(estadoPesquisa, BorderLayout.SOUTH);

        configurarLista(listaResultados, "Resultados da pesquisa");
        listaResultados.setCellRenderer(new Cartao<>(this::htmlResultado));
        aoEscolher(listaResultados, r -> controlador.abrirMensagem(r.mensagem().conversaId(), r.mensagem().id()));

        botaoMais.setVisible(false);
        botaoMais.addActionListener(e -> proximaPagina());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        rodape.setOpaque(false);
        rodape.add(botaoMais);

        JPanel painel = new JPanel(new BorderLayout(0, 6));
        painel.setOpaque(false);
        painel.add(cabecalho, BorderLayout.NORTH);
        painel.add(rolagem(listaResultados), BorderLayout.CENTER);
        painel.add(rodape, BorderLayout.SOUTH);
        return painel;
    }

    private JComponent criarFixadas() {
        estadoFixadas.setFont(TemaSakura.FONTE_PEQUENA);
        configurarLista(listaFixadas, "Mensagens fixadas");
        listaFixadas.setCellRenderer(new Cartao<>(this::htmlFixada));
        aoEscolher(listaFixadas, f -> controlador.abrirMensagem(f.mensagem().conversaId(), f.mensagem().id()));
        listaFixadas.addListSelectionListener(e -> atualizarBotaoDesafixar());

        botaoDesafixar.definirIcone(IconesDesenhados::alfinete);
        botaoDesafixar.setToolTipText("Desafixar a mensagem selecionada (ela continua na conversa)");
        botaoDesafixar.addActionListener(e -> {
            ProtocoloJson.FixadaJson escolhida = listaFixadas.getSelectedValue();
            if (escolhida != null) {
                controlador.desafixar(escolhida.mensagem().id());
            }
        });
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        rodape.setOpaque(false);
        rodape.add(botaoDesafixar);

        JPanel painel = new JPanel(new BorderLayout(0, 6));
        painel.setOpaque(false);
        painel.add(estadoFixadas, BorderLayout.NORTH);
        painel.add(rolagem(listaFixadas), BorderLayout.CENTER);
        painel.add(rodape, BorderLayout.SOUTH);
        return painel;
    }

    private static void configurarLista(JList<?> lista, String nome) {
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setOpaque(false);
        lista.getAccessibleContext().setAccessibleName(nome);
        lista.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    /** Clique ou Enter num item. */
    private static <T> void aoEscolher(JList<T> lista, java.util.function.Consumer<T> acao) {
        lista.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int indice = lista.locationToIndex(e.getPoint());
                if (indice >= 0 && lista.getCellBounds(indice, indice).contains(e.getPoint())) {
                    lista.setSelectedIndex(indice);
                    acao.accept(lista.getModel().getElementAt(indice));
                }
            }
        });
        lista.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && lista.getSelectedValue() != null) {
                    acao.accept(lista.getSelectedValue());
                }
            }
        });
    }

    private static JScrollPane rolagem(JList<?> lista) {
        JScrollPane rolagem = new JScrollPane(lista);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        rolagem.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.getVerticalScrollBar().setUI(new BarraRolagemSakura());
        rolagem.getVerticalScrollBar().setOpaque(false);
        rolagem.getVerticalScrollBar().setUnitIncrement(16);
        return rolagem;
    }

    // ------------------------------------------------------------------ vistas

    Vista vista() {
        return vista;
    }

    void mostrar(Vista nova) {
        vista = nova;
        cartoes.show(conteudo, nova.name());
        atualizarTitulo();
        if (nova == Vista.PESQUISA) {
            campoTermo.requestFocusInWindow();
        } else {
            atualizarFixadas();
        }
    }

    /** A conversa ativa mudou (ou foi recarregada): limpa o que era da anterior. */
    void conversaAtiva(ControladorConversas.Conversa conversa) {
        if (conversa.id() == conversaExibida) {
            atualizarTitulo();
            return;
        }
        conversaExibida = conversa.id();
        numeroPesquisa++;                    // descarta respostas pendentes
        ultimaConsulta = null;
        proximaPagina = null;
        resultados.clear();
        botaoMais.setVisible(false);
        estado(estadoPesquisa, " ");

        campoRemetente.removeAllItems();
        campoRemetente.addItem(new Remetente(null, "Todos os remetentes"));
        conversa.participantes().forEach(p -> campoRemetente.addItem(new Remetente(p.id(), p.apelido())));
        campoRemetente.setEnabled(campoRemetente.getItemCount() > 1);

        atualizarTitulo();
        atualizarFixadas();
    }

    private void atualizarTitulo() {
        ControladorConversas.Conversa ativa = controlador.conversaAtiva();
        String nome = ativa == null ? "" : ativa.titulo();
        titulo.setText(vista == Vista.PESQUISA ? "Pesquisar em " + nome : "Fixadas em " + nome);
    }

    // ------------------------------------------------------------------ pesquisa

    /** Lê os campos e faz uma nova pesquisa (primeira página). */
    void pesquisar() {
        String termo = campoTermo.getText().strip();
        if (termo.length() < 2) {
            estado(estadoPesquisa, "Digite pelo menos 2 caracteres.");
            return;
        }
        LocalDate de;
        LocalDate ate;
        try {
            de = data(campoDe.getText());
            ate = data(campoAte.getText());
        } catch (DateTimeParseException e) {
            estado(estadoPesquisa, "Use datas no formato dd/mm/aaaa.");
            return;
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            estado(estadoPesquisa, "A data inicial deve ser anterior à final.");
            return;
        }
        Remetente remetente = (Remetente) campoRemetente.getSelectedItem();
        ultimaConsulta = new Consulta(controlador.conversaAtiva().id(), termo,
                remetente == null ? null : remetente.id(), de, ate);
        resultados.clear();
        enviar(ultimaConsulta, null);
    }

    private void proximaPagina() {
        if (ultimaConsulta != null && proximaPagina != null) {
            enviar(ultimaConsulta, proximaPagina);
        }
    }

    private void enviar(Consulta consulta, Long antesDe) {
        long numero = ++numeroPesquisa;
        estado(estadoPesquisa, "Pesquisando…");
        botaoMais.setVisible(false);
        boolean enviado = controlador.pesquisar(consulta.termo(), consulta.remetenteId(), consulta.de(),
                consulta.ate(), antesDe,
                pagina -> {
                    if (numero == numeroPesquisa && pagina.conversaId() == conversaExibida) {
                        mostrarResultados(pagina);
                    }
                },
                () -> {
                    if (numero == numeroPesquisa) {
                        estado(estadoPesquisa, "A pesquisa não pôde ser feita.");
                    }
                });
        if (!enviado) {
            estado(estadoPesquisa, "Conecte-se com uma conta para pesquisar.");
        }
    }

    /** Acrescenta uma página de resultados (usado também nos testes). */
    void mostrarResultados(ControladorConversas.PaginaPesquisa pagina) {
        java.util.Set<Long> presentes = new java.util.HashSet<>();
        for (int i = 0; i < resultados.size(); i++) {
            presentes.add(resultados.get(i).mensagem().id());
        }
        for (ProtocoloJson.ResultadoJson r : pagina.resultados()) {
            if (presentes.add(r.mensagem().id())) {     // sem duplicatas
                resultados.addElement(r);
            }
        }
        proximaPagina = pagina.temMais() && !pagina.resultados().isEmpty()
                ? pagina.resultados().get(pagina.resultados().size() - 1).mensagem().id() : null;
        botaoMais.setVisible(proximaPagina != null);
        if (resultados.isEmpty()) {
            estado(estadoPesquisa, NENHUM_RESULTADO);
        } else {
            estado(estadoPesquisa, resultados.size() + (resultados.size() == 1 ? " mensagem encontrada"
                    : " mensagens encontradas") + (proximaPagina != null ? " (há mais)" : "") + ".");
        }
    }

    static LocalDate data(String texto) {
        String limpo = texto == null ? "" : texto.strip();
        return limpo.isEmpty() ? null : LocalDate.parse(limpo, DATA);
    }

    // ------------------------------------------------------------------ fixadas

    void atualizarFixadas() {
        ControladorConversas.Conversa ativa = controlador.conversaAtiva();
        Long selecionada = listaFixadas.getSelectedValue() == null ? null
                : listaFixadas.getSelectedValue().mensagem().id();
        fixadas.clear();
        if (ativa != null) {
            ativa.fixadas().forEach(fixadas::addElement);
        }
        for (int i = 0; i < fixadas.size(); i++) {
            if (Objects.equals(fixadas.get(i).mensagem().id(), selecionada)) {
                listaFixadas.setSelectedIndex(i);
            }
        }
        if (ativa != null && ativa.tipo() == br.unicesumar.chat.model.Conversa.Tipo.GERAL) {
            estado(estadoFixadas, "O Chat Geral não tem mensagens fixadas.");
        } else {
            estado(estadoFixadas, fixadas.isEmpty() ? SEM_FIXADAS
                    : fixadas.size() + " de " + br.unicesumar.chat.model.MensagemFixada.LIMITE_POR_CONVERSA
                    + " fixadas. Clique para localizar.");
        }
        botaoDesafixar.setVisible(ativa != null && ativa.podeFixar());
        atualizarBotaoDesafixar();
    }

    private void atualizarBotaoDesafixar() {
        botaoDesafixar.setEnabled(listaFixadas.getSelectedValue() != null);
    }

    // ------------------------------------------------------------------ apresentação

    /** Cores da paleta ativa nos componentes que não as leem sozinhos. */
    void aplicarTema() {
        PaletaSakura c = TemaSakura.cores();
        titulo.setForeground(c.rosaHover());
        campoRemetente.setBackground(c.cartao());
        campoRemetente.setForeground(c.texto());
        campoRemetente.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.rosaBorda(), 1, true),
                BorderFactory.createEmptyBorder(2, 4, 2, 2)));
        estado(estadoPesquisa, estadoPesquisa.getText());
        estado(estadoFixadas, estadoFixadas.getText());
        repaint();
    }

    private static void estado(JLabel rotulo, String texto) {
        rotulo.setText(texto);
        rotulo.setForeground(TemaSakura.cores().textoSuave());
    }

    String estadoPesquisa() {
        return estadoPesquisa.getText();
    }

    String estadoFixadas() {
        return estadoFixadas.getText();
    }

    DefaultListModel<ProtocoloJson.ResultadoJson> resultados() {
        return resultados;
    }

    DefaultListModel<ProtocoloJson.FixadaJson> fixadas() {
        return fixadas;
    }

    CampoTextoSakura campoTermo() {
        return campoTermo;
    }

    CampoTextoSakura campoDe() {
        return campoDe;
    }

    BotaoSakura botaoMais() {
        return botaoMais;
    }

    BotaoSakura botaoDesafixar() {
        return botaoDesafixar;
    }

    JList<ProtocoloJson.ResultadoJson> listaResultados() {
        return listaResultados;
    }

    JList<ProtocoloJson.FixadaJson> listaFixadas() {
        return listaFixadas;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = TemaSakura.suavizar(g);
        try {
            PaletaSakura c = TemaSakura.cores();
            RoundRectangle2D forma = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f,
                    TemaSakura.RAIO_CARTAO, TemaSakura.RAIO_CARTAO);
            g2.setColor(c.cartao());
            g2.fill(forma);
            g2.setColor(c.rosaBorda());
            g2.draw(forma);
        } finally {
            g2.dispose();
        }
    }

    // HTML dos cartões: o texto vem do servidor e é sempre escapado.

    private String htmlResultado(ProtocoloJson.ResultadoJson r) {
        String trecho = r.trecho();
        String marcado;
        if (r.inicio() >= 0 && r.fim() <= trecho.length() && r.inicio() < r.fim()) {
            marcado = escapar(trecho.substring(0, r.inicio()))
                    + "<b style='color:" + hex(TemaSakura.cores().rosaEscuro()) + "'>"
                    + escapar(trecho.substring(r.inicio(), r.fim())) + "</b>"
                    + escapar(trecho.substring(r.fim()));
        } else {
            marcado = escapar(trecho);
        }
        return cabecalho(r.mensagem()) + "<br>" + marcado;
    }

    private String htmlFixada(ProtocoloJson.FixadaJson f) {
        String texto = f.mensagem().texto();
        if (texto.length() > 160) {
            texto = texto.substring(0, Character.isHighSurrogate(texto.charAt(159)) ? 159 : 160) + "…";
        }
        String autor = f.fixadaPor() == null ? "" : "<br><span style='color:" + hex(TemaSakura.cores().textoSuave())
                + "'>Fixada por " + escapar(f.fixadaPor()) + "</span>";
        return cabecalho(f.mensagem()) + "<br>" + escapar(texto) + autor;
    }

    private static String cabecalho(ProtocoloJson.MensagemJson m) {
        PaletaSakura c = TemaSakura.cores();
        String quando;
        try {
            quando = LocalDateTime.parse(m.horario()).format(DATA_HORA);
        } catch (DateTimeParseException e) {
            quando = m.horario();
        }
        return "<b style='color:" + hex(c.corParticipante(m.remetente())) + "'>" + escapar(m.remetente()) + "</b>"
                + " <span style='color:" + hex(c.textoHorario()) + "'>" + escapar(quando) + "</span>";
    }

    static String escapar(String texto) {
        StringBuilder saida = new StringBuilder(texto.length());
        for (char ch : texto.toCharArray()) {
            switch (ch) {
                case '&' -> saida.append("&amp;");
                case '<' -> saida.append("&lt;");
                case '>' -> saida.append("&gt;");
                case '"' -> saida.append("&quot;");
                case '\'' -> saida.append("&#39;");
                default -> saida.append(ch);
            }
        }
        return saida.toString();
    }

    private static String hex(Color cor) {
        return String.format("#%02x%02x%02x", cor.getRed(), cor.getGreen(), cor.getBlue());
    }

    /** Cartão de um item da lista, com as cores da paleta ativa. */
    private static final class Cartao<T> extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        private final transient java.util.function.Function<T, String> html;

        Cartao(java.util.function.Function<T, String> html) {
            this.html = html;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                                                      boolean selecionado, boolean foco) {
            super.getListCellRendererComponent(lista, "", indice, selecionado, foco);
            PaletaSakura c = TemaSakura.cores();
            int largura = Math.max(120, lista.getWidth() - 34);
            setText("<html><div style='width:" + largura + "px; color:" + hex(c.texto()) + "'>"
                    + html.apply((T) valor) + "</div></html>");
            setFont(TemaSakura.FONTE_PEQUENA);
            setOpaque(true);
            setBackground(selecionado ? c.rosaPetala() : c.cartao());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, c.rosaBorda()),
                    new EmptyBorder(7, 8, 7, 8)));
            return this;
        }
    }

    List<String> textosResultados() {
        java.util.ArrayList<String> textos = new java.util.ArrayList<>();
        for (int i = 0; i < resultados.size(); i++) {
            textos.add(resultados.get(i).mensagem().texto());
        }
        return textos;
    }
}
