
package br.unicesumar.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    // Avisos não modais (um por vez, sem empilhar diálogos).
    private final AvisoDiscreto aviso = new AvisoDiscreto();

    // Rede e estado das conversas; a janela apenas monta e coordena a interface.
    private final transient ControladorConversas controlador =
            new ControladorConversas(new VisaoJanela(), notificacoes);

    // Avatar do perfil no cabeçalho (com conta).
    private final AvatarSakura.Icone avatarPerfil =
            new AvatarSakura.Icone(30, br.unicesumar.chat.model.CatalogoAvatares.PADRAO);

    // Barra lateral e nome da conversa ativa.
    private final BarraConversas barraConversas = new BarraConversas(id -> controlador.selecionar(id));

    private final JLabel tituloConversa = new JLabel(ControladorConversas.TITULO_GERAL) {
        private static final long serialVersionUID = 1L;

        @Override
        public Color getForeground() {
            return TemaSakura.cores().rosaEscuro();
        }
    };

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
        controlador.encerrar();
        notificacoes.fechar();
        GerenciadorTema.removerOuvinte(aoMudarTema);
        ConfiguracaoAnimacoes.removerOuvinte(aoMudarAnimacoes);
        preferencias.aguardarGravacoes(1000);
    }

    private void configurarJanela() {
        setTitle("Chat Sakura");
        setSize(1020, 620);
        setMinimumSize(new Dimension(820, 460));
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

        // Faixa de aviso logo acima da conversa (oculta quando não há aviso).
        JPanel centro = new JPanel(new BorderLayout(0, TemaSakura.ESPACO_M));
        centro.setOpaque(false);
        centro.add(aviso, BorderLayout.NORTH);
        centro.add(criarAreaConversa(), BorderLayout.CENTER);
        principal.add(centro, BorderLayout.CENTER);
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

        // Título centralizado verticalmente ao lado da flor.
        JPanel blocoTitulos = new JPanel(new GridBagLayout());
        blocoTitulos.setOpaque(false);
        blocoTitulos.add(titulo);

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
        linhaStatus.add(avatarPerfil);
        linhaStatus.add(criarBotaoTema());
        linhaStatus.add(indicador);

        // Avatar do perfil: clique para abrir a galeria (somente com conta).
        avatarPerfil.setVisible(false);
        avatarPerfil.setToolTipText("Escolher avatar");
        avatarPerfil.getAccessibleContext().setAccessibleName("Escolher avatar");
        avatarPerfil.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatarPerfil.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                escolherAvatar();
            }
        });

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

        // Nome da conversa ativa acima das mensagens.
        tituloConversa.setFont(TemaSakura.FONTE_NOME);
        tituloConversa.setBorder(new EmptyBorder(2, 8, 2, 8));
        fundoConversa.add(tituloConversa, BorderLayout.NORTH);

        barraConversas.botaoPrivada.addActionListener(e -> novaConversaPrivada());
        barraConversas.botaoGrupo.addActionListener(e -> novoGrupo());
        barraConversas.botaoIA.addActionListener(e -> novoPersonagem());
        barraConversas.definirContas(false);
        barraConversas.atualizar(controlador.itens(), ControladorConversas.ID_GERAL);

        JPanel area = new JPanel(new BorderLayout(TemaSakura.ESPACO_M, 0));
        area.setOpaque(false);
        area.add(barraConversas, BorderLayout.WEST);
        area.add(fundoConversa, BorderLayout.CENTER);

        return area;
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

    /** Entrar com conta, criar conta ou entrar como visitante. */
    private void abrirDialogoConexao() {

        if (controlador.estado() != ControladorConversas.EstadoConexao.DESCONECTADO) {
            return;
        }

        // Retorna imediatamente: a conexão e a autenticação ocorrem em
        // segundo plano.
        DialogoEntrada.exibir(this).ifPresent(escolha -> controlador.conectar(
                escolha.endereco(), escolha.porta(), escolha.pedido()));
    }

    private void enviarMensagem() {

        if (controlador.estado() != ControladorConversas.EstadoConexao.CONECTADO) {
            return;
        }

        String texto = campoMensagem.getText().trim();

        if (texto.isEmpty()) {
            return;
        }

        if (texto.length() > LIMITE_MENSAGEM) {
            aviso.mostrar("A mensagem deve ter no máximo 500 caracteres.", AvisoDiscreto.Tipo.ERRO);
            return;
        }

        campoMensagem.setText("");

        // A escrita no socket ocorre em segundo plano.
        controlador.enviar(texto);
    }

    private void desconectar() {
        controlador.desconectar();
    }

    /** Habilita os controles e o selo conforme o estado (na EDT). */
    private void atualizarEstado() {

        ControladorConversas.EstadoConexao estado = controlador.estado();

        boolean ativo = estado == ControladorConversas.EstadoConexao.CONECTADO;
        boolean emConexao = estado == ControladorConversas.EstadoConexao.CONECTANDO;
        boolean reconectando = estado == ControladorConversas.EstadoConexao.RECONECTANDO;

        botaoConectar.setEnabled(!ativo && !emConexao && !reconectando);
        // Durante a reconexão, "Sair" cancela a espera.
        botaoDesconectar.setEnabled(ativo || emConexao || reconectando);
        botaoEnviar.setEnabled(ativo);
        campoMensagem.setEnabled(ativo);
        botaoExportar.setEnabled(!controlador.conversaAtiva().texto().isBlank());
        avatarPerfil.setVisible(controlador.modoContas());
        barraConversas.definirContas(controlador.modoContas());

        if (ativo) {
            indicador.mostrar(
                    IndicadorConexao.Estado.CONECTADO,
                    "Conectado como " + controlador.nomeUsuario()
            );
        } else if (emConexao) {
            indicador.mostrar(
                    IndicadorConexao.Estado.CONECTANDO,
                    "Conectando..."
            );
        } else if (reconectando) {
            indicador.mostrar(
                    IndicadorConexao.Estado.CONECTANDO,
                    "Reconectando..."
            );
        } else {
            indicador.mostrar(
                    IndicadorConexao.Estado.DESCONECTADO,
                    "Desconectado"
            );
        }
    }

    /** Ligação entre o controlador e os componentes da janela (EDT). */
    private final class VisaoJanela implements ControladorConversas.Visao {

        @Override
        public void reiniciarConversa(long idConversa, String usuarioAtual) {
            areaMensagens.limpar();
            areaMensagens.definirUsuarioAtual(usuarioAtual);
            acompanhandoFim = true;
            tituloConversa.setText(ControladorConversas.TITULO_GERAL);
        }

        @Override
        public void exibirLinha(long idConversa, String linha) {
            // A rolagem até a nova linha é feita por
            // atualizarAcompanhamentoDoFim, se o usuário estiver no fim.
            areaMensagens.adicionarLinha(linha);
            botaoExportar.setEnabled(true);
        }

        @Override
        public boolean historicoConcluido(long idConversa) {
            return areaMensagens.historicoConcluido();
        }

        @Override
        public void exibirConversa(ControladorConversas.Conversa conversa) {
            acompanhandoFim = true;
            areaMensagens.carregar(conversa.linhas());
            tituloConversa.setText(tituloDe(conversa));
            botaoExportar.setEnabled(!conversa.texto().isBlank());
        }

        @Override
        public void perfilAtualizado(String apelido, String avatarId) {
            avatarPerfil.definir(avatarId);
            avatarPerfil.setVisible(true);
        }

        @Override
        public void estadoIA(long idConversa, boolean gerando, String descricao) {
            ControladorConversas.Conversa ativa = controlador.conversaAtiva();
            if (ativa.id() == idConversa) {
                tituloConversa.setText(tituloDe(ativa));
            }
        }

        @Override
        public void conversasAtualizadas(java.util.List<ControladorConversas.ItemLateral> itens, long idAtiva) {
            barraConversas.atualizar(itens, idAtiva);
            barraConversas.definirContas(controlador.modoContas());
        }

        // Recuperáveis: faixa discreta, sem diálogo modal.
        @Override
        public void erroConversa(String mensagem) {
            aviso.mostrar(mensagem, AvisoDiscreto.Tipo.ERRO);
        }

        private boolean aguardandoNovoLogin;

        @Override
        public void reconexaoEmAndamento(int tentativa, long proximaEmMs) {
            String texto = tentativa == 0
                    ? "Conexão perdida. Tentando reconectar..."
                    : "Servidor indisponível (tentativa " + tentativa + "). Nova tentativa em "
                            + Math.max(1, Math.round(proximaEmMs / 1000.0)) + " s.";
            aviso.mostrar(texto, AvisoDiscreto.Tipo.INFORMACAO, true, "Cancelar",
                    controlador::cancelarReconexao);
        }

        @Override
        public void reconexaoDisponivel(String endereco, int porta, String login) {
            aviso.mostrar("O servidor voltou. Entre de novo para continuar.", AvisoDiscreto.Tipo.INFORMACAO,
                    true, "Cancelar", controlador::cancelarReconexao);
            aguardandoNovoLogin = true;
            // Depois de repintar o aviso; a senha nunca foi guardada.
            SwingUtilities.invokeLater(() -> DialogoEntrada.exibirReconexao(ClienteChat.this, endereco, porta, login)
                    .ifPresentOrElse(
                            escolha -> controlador.conectar(escolha.endereco(), escolha.porta(), escolha.pedido()),
                            controlador::cancelarReconexao));
        }

        @Override
        public void reconexaoEncerrada() {
            aguardandoNovoLogin = false;
            aviso.mostrar("Reconexão cancelada. Use \"Conectar\" quando quiser voltar.",
                    AvisoDiscreto.Tipo.INFORMACAO);
        }

        @Override
        public void estadoAlterado(ControladorConversas.EstadoConexao estado) {
            atualizarEstado();
        }

        @Override
        public void mensagemEnviada() {
            // Envio bem-sucedido: pequeno voo do avião de papel.
            botaoEnviar.animarEnvio();
        }

        @Override
        public void usuarioIdentificado(String apelido) {
            areaMensagens.definirUsuarioAtual(apelido);
            if (aguardandoNovoLogin) {
                aguardandoNovoLogin = false;
                aviso.mostrar("Reconectado. Suas conversas foram restauradas.", AvisoDiscreto.Tipo.INFORMACAO);
            } else if (aviso.isVisible() && aviso.tipo() == AvisoDiscreto.Tipo.INFORMACAO) {
                aviso.ocultar();
            }
        }

        @Override
        public void autenticacaoRecusada(String mensagem) {
            mostrarErro(mensagem);
        }
    }

    /** Nome sugerido: {@code chat-sakura-<conversa>-<data>.txt}, só com caracteres seguros. */
    static String nomeArquivoExportacao(String tituloConversa, String data) {
        String base = java.text.Normalizer.normalize(tituloConversa, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return "chat-sakura-" + (base.isEmpty() ? "conversa" : base) + "-" + data + ".txt";
    }

    /** Nova conversa privada: lista de usuários pedida sem bloquear a EDT. */
    private void novaConversaPrivada() {
        controlador.solicitarUsuarios(usuarios -> DialogoNovaConversa.escolherContato(this, usuarios)
                .ifPresent(controlador::abrirPrivada));
    }

    /** Personagem de IA: a resposta do Qwen é gerada no servidor, sem bloquear a EDT. */
    private void novoPersonagem() {
        DialogoPersonagem.exibir(this).ifPresent(dados ->
                controlador.criarPersonagem(dados.nome(), dados.personalidade(), dados.contexto()));
    }

    private void escolherAvatar() {
        if (controlador.modoContas()) {
            DialogoAvatar.escolher(this, controlador.avatarProprio()).ifPresent(controlador::definirAvatar);
        }
    }

    /** Título da área de mensagens; personagens são identificados como IA. */
    static String tituloDe(ControladorConversas.Conversa conversa) {
        if (conversa.tipo() != br.unicesumar.chat.model.Conversa.Tipo.IA) {
            return conversa.titulo();
        }
        return conversa.titulo() + " · personagem de IA" + (conversa.gerando() ? " · digitando…" : "");
    }

    private void novoGrupo() {
        controlador.solicitarUsuarios(usuarios -> DialogoNovaConversa.criarGrupo(this, usuarios)
                .ifPresent(grupo -> controlador.criarGrupo(grupo.nome(), grupo.participantes())));
    }

    private void exportarHistorico() {

        String conteudo = controlador.conversaAtiva().texto();

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
                new File(nomeArquivoExportacao(controlador.conversaAtiva().titulo(), data))
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
