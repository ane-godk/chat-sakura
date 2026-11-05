
package br.unicesumar.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClienteChat extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final int LIMITE_MENSAGEM = 500;

    /** Distância do fim (px) em que a rolagem automática continua ativa. */
    private static final int TOLERANCIA_ROLAGEM = 24;

    private final PainelConversa areaMensagens = new PainelConversa();
    private final JScrollPane rolagem = new JScrollPane(areaMensagens);
    private final FundoConversa fundoConversa = new FundoConversa(new BorderLayout());

    // Sons de notificação (reprodução em thread própria, sem bloquear a EDT).
    private final NotificacoesSonoras notificacoes =
            new NotificacoesSonoras(new ReprodutorJavaSound(), Transicao::agora);

    private final BotaoSakura botaoSom =
            new BotaoSakura("", BotaoSakura.Estilo.SECUNDARIO);

    private final BotaoSakura botaoTema =
            new BotaoSakura("", BotaoSakura.Estilo.SECUNDARIO);

    // Estado da rolagem automática; acessado somente na EDT.
    private boolean acompanhandoFim = true;
    private int extensaoAnterior = -1;
    private int maximoAnterior = -1;

    private final CampoTextoSakura campoMensagem = new CampoTextoSakura(
            20,
            "Digite sua mensagem...",
            "Conecte-se para enviar mensagens",
            true
    );

    private final JLabel contador = new JLabel();

    private final BotaoSakura botaoEnviar =
            new BotaoSakura("Enviar", BotaoSakura.Estilo.PRINCIPAL);
    private final BotaoSakura botaoConectar =
            new BotaoSakura("Conectar", BotaoSakura.Estilo.PRINCIPAL);
    private final BotaoSakura botaoDesconectar =
            new BotaoSakura("Sair", BotaoSakura.Estilo.SECUNDARIO);
    private final BotaoSakura botaoExportar =
            new BotaoSakura("Exportar TXT", BotaoSakura.Estilo.SECUNDARIO);

    private final IndicadorConexao indicador = new IndicadorConexao();

    private final AtomicBoolean conectado = new AtomicBoolean(false);
    private final AtomicBoolean conectando = new AtomicBoolean(false);

    private final Object bloqueioEscrita = new Object();

    private volatile Socket socket;
    private volatile BufferedWriter saida;

    private volatile String nomeUsuario = "";

    public ClienteChat() {
        this(PreferenciasSakura.padrao());
    }

    /** @param preferencias arquivo de preferências (substituível nos testes) */
    ClienteChat(PreferenciasSakura preferencias) {
        this.preferencias = preferencias;
        this.aoMudarAnimacoes = () -> preferencias.salvarAnimacoes(ConfiguracaoAnimacoes.modo());

        // 1. Preferências aplicadas antes de montar a interface, para a janela
        //    já aparecer no tema certo (-D válida > arquivo > padrão).
        aplicarPreferenciasIniciais();

        // 2. Só depois as mudanças passam a ser salvas: assim uma -D não
        //    sobrescreve o arquivo; apenas escolhas do usuário são gravadas.
        GerenciadorTema.aoMudar(aoMudarTema);
        ConfiguracaoAnimacoes.aoMudar(aoMudarAnimacoes);

        configurarJanela();
        montarInterface();
        atualizarEstado();
    }

    private final transient PreferenciasSakura preferencias;
    private final transient Runnable aoMudarTema = this::aplicarTema;
    private final transient Runnable aoMudarAnimacoes;

    private void aplicarPreferenciasIniciais() {
        PreferenciasSakura.Valores valores = preferencias.valoresIniciais();
        GerenciadorTema.definir(valores.tema());
        ConfiguracaoAnimacoes.definirModo(valores.animacoes());
        notificacoes.definirSilenciado(valores.silenciado());
    }

    /**
     * Troca de tema (na EDT): os componentes leem a paleta ao pintar, então
     * basta recalcular o que depende de estado e repintar. A conversa, a
     * rolagem e a conexão não são tocadas.
     */
    private void aplicarTema() {
        atualizarContador();
        atualizarBotaoTema();
        preferencias.salvarTema(GerenciadorTema.atual());
        getRootPane().repaint();
    }

    /**
     * Libera áudio e ouvintes globais e conclui as gravações pendentes.
     * Feito aqui (e não em windowClosed) porque uma janela nunca exibida
     * não recebe esse evento. Chamadas repetidas são inofensivas.
     */
    @Override
    public void dispose() {
        super.dispose();
        notificacoes.fechar();
        GerenciadorTema.removerOuvinte(aoMudarTema);
        ConfiguracaoAnimacoes.removerOuvinte(aoMudarAnimacoes);
        preferencias.aguardarGravacoes(1000);
    }

    private void configurarJanela() {
        setTitle("Chat Sakura");
        setSize(800, 580);
        setMinimumSize(new Dimension(600, 420));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setIconImages(IconeSakura.imagensJanela());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }

            // Pétalas pausadas com a janela inativa ou minimizada.
            @Override
            public void windowActivated(WindowEvent e) {
                fundoConversa.definirJanelaAtiva(true);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                fundoConversa.definirJanelaAtiva(false);
            }

            @Override
            public void windowIconified(WindowEvent e) {
                fundoConversa.definirJanelaAtiva(false);
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                fundoConversa.definirJanelaAtiva(isActive());
            }
        });
    }

    private void montarInterface() {

        int espaco = TemaSakura.ESPACO_M;

        // Fundo da janela lido da paleta ativa a cada pintura.
        JPanel principal = new JPanel(new BorderLayout(0, espaco)) {
            private static final long serialVersionUID = 1L;

            @Override
            public Color getBackground() {
                return TemaSakura.cores().fundo();
            }
        };
        principal.setBorder(
                new EmptyBorder(espaco, espaco, espaco, espaco)
        );

        principal.add(criarCabecalho(), BorderLayout.NORTH);
        principal.add(criarAreaConversa(), BorderLayout.CENTER);
        principal.add(criarBarraEnvio(), BorderLayout.SOUTH);

        setContentPane(principal);

        campoMensagem.getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        atualizarContador();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        atualizarContador();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        atualizarContador();
                    }
                }
        );

        atualizarContador();

        botaoConectar.addActionListener(
                e -> abrirDialogoConexao()
        );

        botaoDesconectar.addActionListener(
                e -> desconectar()
        );

        botaoExportar.addActionListener(
                e -> exportarHistorico()
        );

        botaoEnviar.addActionListener(
                e -> enviarMensagem()
        );

        campoMensagem.addActionListener(
                e -> enviarMensagem()
        );
    }

    private JPanel criarCabecalho() {

        PainelCabecalho cabecalho = new PainelCabecalho(
                new BorderLayout(TemaSakura.ESPACO_M, 0)
        );
        cabecalho.setBorder(new EmptyBorder(12, 14, 12, 8));

        TituloSakura titulo = new TituloSakura(
                "Chat Sakura",
                TemaSakura.FONTE_TITULO,
                () -> TemaSakura.cores().rosaEscuro()
        );

        JLabel subtitulo = new JLabel("Converse em tempo real") {
            private static final long serialVersionUID = 1L;

            @Override
            public Color getForeground() {
                return TemaSakura.cores().textoCabecalho();
            }
        };
        subtitulo.setFont(TemaSakura.FONTE_SUBTITULO);

        JPanel titulos = new JPanel(new BorderLayout(0, 0));
        titulos.setOpaque(false);
        titulos.add(titulo, BorderLayout.CENTER);
        titulos.add(subtitulo, BorderLayout.SOUTH);

        JPanel blocoTitulos = new JPanel(new GridBagLayout());
        blocoTitulos.setOpaque(false);
        blocoTitulos.add(titulos);

        JPanel marca = new JPanel(
                new BorderLayout(TemaSakura.ESPACO_M, 0)
        );
        marca.setOpaque(false);
        marca.add(new IconeSakura(60), BorderLayout.WEST);
        marca.add(blocoTitulos, BorderLayout.CENTER);

        JPanel linhaStatus = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 6, 0)
        );
        linhaStatus.setOpaque(false);
        linhaStatus.add(criarBotaoTema());
        linhaStatus.add(indicador);

        JPanel acoes = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 6, 0)
        );
        acoes.setOpaque(false);
        acoes.add(botaoConectar);
        acoes.add(botaoDesconectar);
        acoes.add(botaoExportar);

        // O selo nunca fica mais largo que a fileira de botões,
        // para não cobrir o título em janelas estreitas.
        // Descontado também o botão de tema, ao lado do selo.
        indicador.definirLarguraMaxima(
                acoes.getPreferredSize().width - 12
                        - botaoTema.getPreferredSize().width - 6
        );

        JPanel lateral = new JPanel(new BorderLayout(0, 6));
        lateral.setOpaque(false);
        lateral.add(linhaStatus, BorderLayout.NORTH);
        lateral.add(acoes, BorderLayout.SOUTH);

        cabecalho.add(marca, BorderLayout.WEST);
        cabecalho.add(lateral, BorderLayout.EAST);

        return cabecalho;
    }

    private JPanel criarAreaConversa() {

        // Área rolável transparente sobre o fundo floral fixo. O modo
        // simples redesenha a área visível a cada rolagem, mantendo a
        // decoração parada enquanto as mensagens se movem.
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        rolagem.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        rolagem.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );

        JScrollBar barraVertical = rolagem.getVerticalScrollBar();
        barraVertical.setUI(new BarraRolagemSakura());
        barraVertical.setUnitIncrement(16);
        barraVertical.setOpaque(false);
        barraVertical.getModel().addChangeListener(
                e -> atualizarAcompanhamentoDoFim()
        );

        fundoConversa.setBorder(new EmptyBorder(6, 6, 6, 6));
        fundoConversa.add(rolagem, BorderLayout.CENTER);

        return fundoConversa;
    }

    /**
     * Rolagem automática sem atrapalhar a leitura (executado na EDT).
     *
     * Quando só a posição muda, foi o usuário quem rolou: passa a
     * acompanhar o fim apenas se ele estiver no fim. Quando muda o
     * tamanho do conteúdo (nova mensagem) ou da janela, mantém a
     * última linha visível se o usuário estava acompanhando o fim.
     */
    private void atualizarAcompanhamentoDoFim() {

        BoundedRangeModel modelo = rolagem.getVerticalScrollBar().getModel();

        boolean noFim = modelo.getValue() + modelo.getExtent()
                >= modelo.getMaximum() - TOLERANCIA_ROLAGEM;

        boolean mudouTamanho = modelo.getExtent() != extensaoAnterior
                || modelo.getMaximum() != maximoAnterior;

        if (!mudouTamanho) {
            acompanhandoFim = noFim;
            return;
        }

        extensaoAnterior = modelo.getExtent();
        maximoAnterior = modelo.getMaximum();

        if (modelo.getMaximum() <= modelo.getExtent()) {
            acompanhandoFim = true;
        }

        boolean fimExato = modelo.getValue() + modelo.getExtent()
                >= modelo.getMaximum();

        if (acompanhandoFim && !fimExato) {
            SwingUtilities.invokeLater(
                    () -> modelo.setValue(modelo.getMaximum())
            );
        }
    }

    private JPanel criarBarraEnvio() {

        contador.setFont(TemaSakura.FONTE_PEQUENA);
        contador.setHorizontalAlignment(SwingConstants.RIGHT);

        // Pequena flor ao lado do contador, como detalhe decorativo.
        // O ícone desenha a flor do tema ativo a cada pintura.
        int iconeContador = 0;
        if (RecursosSakura.flor().isPresent()) {
            iconeContador = 13;
            int lado = iconeContador;
            contador.setIcon(new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    RecursosSakura.florEmPixels(lado).ifPresent(
                            imagem -> g.drawImage(imagem, x, y, lado, lado, null));
                }

                @Override
                public int getIconWidth() {
                    return lado;
                }

                @Override
                public int getIconHeight() {
                    return lado;
                }
            });
            contador.setIconTextGap(4);
        }

        // Largura fixa para o campo não "pular" ao digitar.
        FontMetrics metricas = contador.getFontMetrics(contador.getFont());
        contador.setPreferredSize(new Dimension(
                metricas.stringWidth("000/" + LIMITE_MENSAGEM)
                        + iconeContador + 8,
                metricas.getHeight()
        ));

        botaoEnviar.definirIcone(IconesDesenhados::aviaoDePapel);

        JPanel direita = new JPanel(
                new BorderLayout(TemaSakura.ESPACO_P, 0)
        );
        direita.setOpaque(false);
        direita.add(contador, BorderLayout.WEST);
        direita.add(criarBotaoSom(), BorderLayout.CENTER);
        direita.add(botaoEnviar, BorderLayout.EAST);

        PainelArredondado cartao = new PainelArredondado(
                new BorderLayout(TemaSakura.ESPACO_P, 0),
                PaletaSakura::cartao,
                PaletaSakura::rosaHoverClaro,
                PaletaSakura::rosaBorda,
                TemaSakura.RAIO_CARTAO
        );
        cartao.setBorder(new EmptyBorder(5, 6, 5, 6));
        cartao.add(campoMensagem, BorderLayout.CENTER);
        cartao.add(direita, BorderLayout.EAST);

        return cartao;
    }

    /** Botão de tema no cabeçalho: lua (ativar Noturno) ou sol (voltar ao Claro). */
    private BotaoSakura criarBotaoTema() {
        botaoTema.setBorder(new EmptyBorder(4, 7, 4, 7));
        // Sem texto, a altura viria só das margens: alinha com o selo de conexão.
        int altura = indicador.getPreferredSize().height;
        botaoTema.setPreferredSize(new Dimension(altura + 8, altura));
        botaoTema.addActionListener(e -> GerenciadorTema.alternar());
        atualizarBotaoTema();
        return botaoTema;
    }

    /** Ícone, dica e nome acessível conforme o tema ativo. */
    private void atualizarBotaoTema() {
        boolean noturno = GerenciadorTema.atual() == GerenciadorTema.Tema.NOTURNO;
        String acao = noturno ? ROTULO_VOLTAR_CLARO : ROTULO_ATIVAR_NOTURNO;
        botaoTema.definirIcone(noturno ? IconesDesenhados::sol : IconesDesenhados::lua);
        botaoTema.setToolTipText(acao);
        botaoTema.getAccessibleContext().setAccessibleName(acao);
    }

    static final String ROTULO_ATIVAR_NOTURNO = "Ativar Sakura Noturno";
    static final String ROTULO_VOLTAR_CLARO = "Voltar ao Sakura Claro";

    /** Botão "Sons: Ligados / Silenciados" (alterna durante o uso). */
    private BotaoSakura criarBotaoSom() {
        botaoSom.setBorder(new EmptyBorder(9, 11, 9, 11));
        botaoSom.addActionListener(e -> {
            notificacoes.definirSilenciado(!notificacoes.silenciado());
            preferencias.salvarSons(notificacoes.silenciado());
            atualizarBotaoSom();
        });
        atualizarBotaoSom();
        return botaoSom;
    }

    private void atualizarBotaoSom() {
        boolean silenciado = notificacoes.silenciado();
        String estado = silenciado ? "Sons: Silenciados" : "Sons: Ligados";
        botaoSom.definirIcone(silenciado
                ? IconesDesenhados::somSilenciado
                : IconesDesenhados::somLigado);
        botaoSom.setToolTipText(estado + (silenciado
                ? " (clique para ligar)" : " (clique para silenciar)"));
        botaoSom.getAccessibleContext().setAccessibleName(estado);
    }

    private void atualizarContador() {

        int tamanho = campoMensagem.getDocument().getLength();

        contador.setText(tamanho + "/" + LIMITE_MENSAGEM);

        if (tamanho > LIMITE_MENSAGEM) {
            contador.setForeground(TemaSakura.cores().coralTexto());
        } else if (tamanho >= LIMITE_MENSAGEM * 9 / 10) {
            contador.setForeground(TemaSakura.cores().ambarTexto());
        } else {
            contador.setForeground(TemaSakura.cores().textoSuave());
        }
    }

    private void abrirDialogoConexao() {

        if (conectado.get() || conectando.get()) {
            return;
        }

        CampoTextoSakura campoNome =
                new CampoTextoSakura(18, "Ex.: Ana", null);
        CampoTextoSakura campoServidor =
                new CampoTextoSakura(18, null, null);
        CampoTextoSakura campoPorta =
                new CampoTextoSakura(18, null, null);

        campoServidor.setText("localhost");
        campoPorta.setText("12345");

        JOptionPane painel = criarPainelConexao(
                campoNome,
                campoServidor,
                campoPorta
        );

        JDialog dialogo = painel.createDialog(
                this,
                "Conectar ao Chat Sakura"
        );
        dialogo.getContentPane().setBackground(TemaSakura.cores().fundo());

        // Enter confirma, como no diálogo padrão; Esc continua cancelando.
        dialogo.getRootPane().setDefaultButton(
                (JButton) painel.getOptions()[0]
        );

        dialogo.setVisible(true);
        dialogo.dispose();

        Object valor = painel.getValue();

        int opcao = valor instanceof Integer escolha
                ? escolha
                : JOptionPane.CLOSED_OPTION;

        if (opcao != JOptionPane.OK_OPTION) {
            return;
        }

        String nome = campoNome.getText().trim();
        String endereco = campoServidor.getText().trim();

        if (nome.isBlank()
                || nome.length() > 30
                || nome.matches(".*\\s+.*")) {

            mostrarErro(
                    "O nome deve ter entre 1 e 30 caracteres, sem espaços."
            );
            return;
        }

        if (endereco.isBlank()) {
            mostrarErro("Informe o endereço do servidor.");
            return;
        }

        final int porta;

        try {
            porta = Integer.parseInt(
                    campoPorta.getText().trim()
            );

            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            mostrarErro("Informe uma porta válida.");
            return;
        }

        if (!conectando.compareAndSet(false, true)) {
            return;
        }

        nomeUsuario = nome;
        areaMensagens.limpar();
        areaMensagens.definirUsuarioAtual(nome);
        acompanhandoFim = true;
        atualizarEstado();

        Thread thread = new Thread(
                () -> conectar(endereco, porta, nome),
                "sakura-conexao"
        );

        thread.setDaemon(true);
        thread.start();
    }

    private JOptionPane criarPainelConexao(
            CampoTextoSakura campoNome,
            CampoTextoSakura campoServidor,
            CampoTextoSakura campoPorta
    ) {

        JLabel titulo = new JLabel("Entrar no Chat Sakura");
        titulo.setFont(TemaSakura.FONTE_TITULO_DIALOGO);
        titulo.setForeground(TemaSakura.cores().rosaEscuro());

        JPanel topo = new JPanel(new BorderLayout(TemaSakura.ESPACO_M, 0));
        topo.setOpaque(false);
        topo.add(new IconeSakura(34), BorderLayout.WEST);
        topo.add(titulo, BorderLayout.CENTER);

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setOpaque(false);

        GridBagConstraints posicao = new GridBagConstraints();
        posicao.gridx = 0;
        posicao.gridy = 0;
        posicao.weightx = 1;
        posicao.fill = GridBagConstraints.HORIZONTAL;
        posicao.insets = new Insets(0, 0, TemaSakura.ESPACO_G, 0);
        formulario.add(topo, posicao);

        adicionarCampo(formulario, posicao, "Nome de usuário:", campoNome);
        adicionarCampo(formulario, posicao, "Endereço do servidor:", campoServidor);
        adicionarCampo(formulario, posicao, "Porta:", campoPorta);

        // Foco inicial no nome quando o diálogo é exibido.
        campoNome.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                SwingUtilities.invokeLater(campoNome::requestFocusInWindow);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {
            }
        });

        BotaoSakura botaoOk =
                new BotaoSakura("Conectar", BotaoSakura.Estilo.PRINCIPAL);
        BotaoSakura botaoCancelar =
                new BotaoSakura("Cancelar", BotaoSakura.Estilo.SECUNDARIO);

        JOptionPane painel = new JOptionPane(
                formulario,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                new Object[] {botaoOk, botaoCancelar},
                botaoOk
        );

        botaoOk.addActionListener(
                e -> painel.setValue(JOptionPane.OK_OPTION)
        );
        botaoCancelar.addActionListener(
                e -> painel.setValue(JOptionPane.CANCEL_OPTION)
        );

        aplicarFundo(painel, TemaSakura.cores().fundo());

        return painel;
    }

    private static void adicionarCampo(
            JPanel formulario,
            GridBagConstraints posicao,
            String rotulo,
            JTextField campo
    ) {

        JLabel legenda = new JLabel(rotulo);
        legenda.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        legenda.setForeground(TemaSakura.cores().texto());
        legenda.setLabelFor(campo);

        posicao.gridy++;
        posicao.insets = new Insets(0, 0, 4, 0);
        formulario.add(legenda, posicao);

        posicao.gridy++;
        posicao.insets = new Insets(0, 0, TemaSakura.ESPACO_M, 0);
        formulario.add(campo, posicao);
    }

    /** Aplica a cor de fundo ao painel e aos seus subpainéis. */
    private static void aplicarFundo(Container container, Color cor) {

        container.setBackground(cor);

        for (Component filho : container.getComponents()) {
            if (filho instanceof JPanel subpainel) {
                aplicarFundo(subpainel, cor);
            }
        }
    }

    private void conectar(
            String endereco,
            int porta,
            String nome
    ) {

        Socket novaConexao = new Socket();

        try {
            socket = novaConexao;

            novaConexao.connect(
                    new InetSocketAddress(endereco, porta),
                    5000
            );

            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                            novaConexao.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            BufferedWriter escritor = new BufferedWriter(
                    new OutputStreamWriter(
                            novaConexao.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            synchronized (bloqueioEscrita) {
                saida = escritor;
            }

            // O servidor atual envia duas linhas iniciais.
            String saudacao = entrada.readLine();
            String pedidoNome = entrada.readLine();

            if (saudacao == null || pedidoNome == null) {
                throw new IOException(
                        "O servidor encerrou a conexão."
                );
            }

            adicionarMensagem(saudacao);
            adicionarMensagem(pedidoNome);

            escreverLinha(nome);

            String confirmacao = entrada.readLine();

            if (confirmacao == null
                    || !confirmacao.startsWith(
                            "SISTEMA: Conectado como "
                    )) {

                if (confirmacao != null) {
                    adicionarMensagem(confirmacao);
                }

                throw new IOException(
                        "O servidor recusou a conexão."
                );
            }

            conectado.set(true);
            conectando.set(false);

            // Conexão confirmada pelo servidor.
            notificacoes.conexaoConfirmada();

            adicionarMensagem(confirmacao);
            atualizarEstado();

            String linha;

            while (conectado.get()
                    && (linha = entrada.readLine()) != null) {

                adicionarMensagem(linha);
            }

        } catch (IOException e) {

            adicionarMensagem(
                    "SISTEMA: " + e.getMessage()
            );

        } finally {

            conectado.set(false);
            conectando.set(false);

            // Fim da sessão (Sair ou queda): toca uma única vez, e só se
            // a conexão chegou a ser confirmada.
            notificacoes.sessaoEncerrada();

            try {
                novaConexao.close();
            } catch (IOException e) {
                System.err.println(
                        "Erro ao fechar conexão: "
                                + e.getMessage()
                );
            }

            synchronized (bloqueioEscrita) {
                if (socket == novaConexao) {
                    socket = null;
                    saida = null;
                }
            }

            atualizarEstado();
        }
    }

    private void escreverLinha(String texto)
            throws IOException {

        synchronized (bloqueioEscrita) {

            if (saida == null) {
                throw new IOException(
                        "Não existe conexão ativa."
                );
            }

            saida.write(texto);
            saida.newLine();
            saida.flush();
        }
    }

    private void enviarMensagem() {

        if (!conectado.get()) {
            return;
        }

        String texto = campoMensagem.getText().trim();

        if (texto.isEmpty()) {
            return;
        }

        if (texto.length() > LIMITE_MENSAGEM) {
            mostrarErro(
                    "A mensagem deve ter no máximo 500 caracteres."
            );
            return;
        }

        campoMensagem.setText("");

        Thread thread = new Thread(() -> {

            try {
                escreverLinha(texto);

                // Envio bem-sucedido: pequeno voo do avião de papel e som.
                SwingUtilities.invokeLater(botaoEnviar::animarEnvio);
                notificacoes.mensagemEnviada();

            } catch (IOException e) {

                adicionarMensagem(
                        "SISTEMA: Falha ao enviar: "
                                + e.getMessage()
                );
            }

        }, "sakura-envio");

        thread.setDaemon(true);
        thread.start();
    }

    private void desconectar() {

        conectado.set(false);

        Socket conexaoAtual = socket;

        if (conexaoAtual != null) {
            try {
                conexaoAtual.close();
            } catch (IOException e) {
                System.err.println(
                        "Erro ao desconectar: "
                                + e.getMessage()
                );
            }
        }

        atualizarEstado();
    }

    private void atualizarEstado() {

        SwingUtilities.invokeLater(() -> {

            boolean ativo = conectado.get();
            boolean emConexao = conectando.get();

            botaoConectar.setEnabled(
                    !ativo && !emConexao
            );

            botaoDesconectar.setEnabled(
                    ativo || emConexao
            );

            botaoEnviar.setEnabled(ativo);
            campoMensagem.setEnabled(ativo);

            botaoExportar.setEnabled(
                    !areaMensagens.texto().isBlank()
            );

            if (ativo) {

                indicador.mostrar(
                        IndicadorConexao.Estado.CONECTADO,
                        "Conectado como " + nomeUsuario
                );

            } else if (emConexao) {

                indicador.mostrar(
                        IndicadorConexao.Estado.CONECTANDO,
                        "Conectando..."
                );

            } else {

                indicador.mostrar(
                        IndicadorConexao.Estado.DESCONECTADO,
                        "Desconectado"
                );
            }
        });
    }

    private void adicionarMensagem(String mensagem) {

        SwingUtilities.invokeLater(() -> {

            // A rolagem até a nova linha é feita por
            // atualizarAcompanhamentoDoFim, se o usuário estiver no fim.
            areaMensagens.adicionarLinha(mensagem);

            // Som apenas para mensagens novas de outros participantes.
            notificacoes.linhaRecebida(
                    mensagem,
                    nomeUsuario,
                    areaMensagens.historicoConcluido()
            );

            botaoExportar.setEnabled(true);
        });
    }

    private void exportarHistorico() {

        String conteudo = areaMensagens.texto();

        if (conteudo.isBlank()) {
            mostrarErro(
                    "Não existem mensagens para exportar."
            );
            return;
        }

        JFileChooser seletor = new JFileChooser();

        String data = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd_HH-mm-ss"
                )
        );

        seletor.setSelectedFile(
                new File("chat-sakura-" + data + ".txt")
        );

        int resultado = seletor.showSaveDialog(this);

        if (resultado != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File arquivo = seletor.getSelectedFile();

        if (!arquivo.getName().toLowerCase().endsWith(".txt")) {
            arquivo = new File(
                    arquivo.getParentFile(),
                    arquivo.getName() + ".txt"
            );
        }

        if (arquivo.exists()) {

            int confirmar = JOptionPane.showConfirmDialog(
                    this,
                    "O arquivo já existe. Deseja substituí-lo?",
                    "Confirmar substituição",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirmar != JOptionPane.YES_OPTION) {
                return;
            }
        }

        Path destino = arquivo.toPath();

        try {

            Files.writeString(
                    destino,
                    conteudo,
                    StandardCharsets.UTF_8
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Histórico exportado com sucesso!\n"
                            + destino.toAbsolutePath(),
                    "Chat Sakura",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (IOException e) {

            mostrarErro(
                    "Não foi possível exportar o histórico:\n"
                            + e.getMessage()
            );
        }
    }

    private void mostrarErro(String mensagem) {

        JOptionPane.showMessageDialog(
                this,
                mensagem,
                "Chat Sakura",
                JOptionPane.ERROR_MESSAGE
        );
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            ClienteChat janela = new ClienteChat();
            janela.setVisible(true);
        });
    }
}
