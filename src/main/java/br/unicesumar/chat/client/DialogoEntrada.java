package br.unicesumar.chat.client;

import br.unicesumar.chat.model.RegrasConta;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diálogo temático de entrada: login, criação de conta ou visitante.
 *
 * Só monta e valida os dados (na EDT); a autenticação acontece no servidor,
 * em segundo plano. As senhas são lidas como {@code char[]}, os campos são
 * limpos ao fechar e nenhum valor de senha é exibido ou registrado.
 */
final class DialogoEntrada {

    enum Modo {
        ENTRAR("Entrar", "Entrar com sua conta"),
        CADASTRAR("Criar conta", "Criar uma conta Sakura"),
        VISITANTE("Visitante", "Entrar como visitante");

        final String rotulo;
        final String titulo;

        Modo(String rotulo, String titulo) {
            this.rotulo = rotulo;
            this.titulo = titulo;
        }
    }

    /** Dados para iniciar a conexão ({@code seguro}: TLS, o padrão). */
    record Escolha(String endereco, int porta, ConexaoServidor.PedidoEntrada pedido, boolean seguro) {
    }

    // TLS é o padrão; sem TLS só marcando explicitamente (desenvolvimento).
    private final JCheckBox campoSemTls = new JCheckBox("Sem TLS (somente desenvolvimento local)");
    private final JLabel avisoSemTls = new JLabel("<html>Sem TLS, a senha e as mensagens trafegam legíveis"
            + " na rede. Use apenas com um servidor em modo de desenvolvimento.</html>");

    private final CampoTextoSakura campoLogin = new CampoTextoSakura(18, "Ex.: ana.souza", null);
    private final CampoTextoSakura campoApelido = new CampoTextoSakura(18, "Nome exibido no chat", null);
    private final CampoSenhaSakura campoSenha = new CampoSenhaSakura(18, "Sua senha");
    private final CampoSenhaSakura campoConfirmacao = new CampoSenhaSakura(18, "Repita a senha");
    private final CampoTextoSakura campoNome = new CampoTextoSakura(18, "Ex.: Ana", null);
    private final CampoTextoSakura campoServidor = new CampoTextoSakura(18, null, null);
    private final CampoTextoSakura campoPorta = new CampoTextoSakura(18, null, null);

    private final JLabel titulo = new JLabel();
    private final Map<Modo, BotaoSakura> botoesModo = new EnumMap<>(Modo.class);

    private JComponent linhaLogin;
    private JComponent linhaApelido;
    private JComponent linhaSenha;
    private JComponent linhaConfirmacao;
    private JComponent linhaNome;

    private Modo modo = Modo.ENTRAR;
    private JDialog dialogo;

    /** Exibe o diálogo (modal, na EDT). */
    static Optional<Escolha> exibir(Component pai) {
        return new DialogoEntrada().mostrar(pai, "localhost", 12345, null, false);
    }

    /**
     * Novo login após a reconexão: servidor, porta e login já preenchidos;
     * só a senha é pedida (ela não fica guardada entre as sessões). Se a
     * sessão anterior usava TLS, a opção "sem TLS" fica bloqueada.
     */
    static Optional<Escolha> exibirReconexao(Component pai, String endereco, int porta, String login,
                                            boolean exigirTls) {
        return new DialogoEntrada().mostrar(pai, endereco, porta, login, exigirTls);
    }

    private Optional<Escolha> mostrar(Component pai, String endereco, int porta, String login, boolean exigirTls) {

        campoServidor.setText(endereco);
        campoPorta.setText(String.valueOf(porta));
        if (login != null) {
            campoLogin.setText(login);
        }
        campoSemTls.setSelected(false);
        campoSemTls.setEnabled(!exigirTls);
        if (exigirTls) {
            campoSemTls.setToolTipText("A sessão anterior usava TLS: a reconexão também precisa usar.");
        }

        AtomicReference<Escolha> escolha = new AtomicReference<>();

        BotaoSakura botaoOk = new BotaoSakura("Conectar", BotaoSakura.Estilo.PRINCIPAL);
        BotaoSakura botaoCancelar = new BotaoSakura("Cancelar", BotaoSakura.Estilo.SECUNDARIO);

        JOptionPane painel = new JOptionPane(
                criarFormulario(),
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                new Object[] {botaoOk, botaoCancelar},
                botaoOk
        );

        // Dados inválidos mantêm o diálogo aberto para correção.
        botaoOk.addActionListener(e -> {
            try {
                escolha.set(montarEscolha());
                painel.setValue(JOptionPane.OK_OPTION);
            } catch (IllegalArgumentException erro) {
                JOptionPane.showMessageDialog(
                        dialogo, erro.getMessage(), "Chat Sakura", JOptionPane.ERROR_MESSAGE);
            }
        });
        botaoCancelar.addActionListener(e -> painel.setValue(JOptionPane.CANCEL_OPTION));

        aplicarFundo(painel, TemaSakura.cores().fundo());

        dialogo = painel.createDialog(pai, login == null
                ? "Conectar ao Chat Sakura"
                : "O servidor voltou: entre de novo para continuar");
        dialogo.getContentPane().setBackground(TemaSakura.cores().fundo());
        dialogo.getRootPane().setDefaultButton((JButton) painel.getOptions()[0]);

        selecionar(Modo.ENTRAR);

        try {
            dialogo.setVisible(true);
        } finally {
            dialogo.dispose();
            campoSenha.limpar();
            campoConfirmacao.limpar();
        }

        return Optional.ofNullable(escolha.get());
    }

    private JPanel criarFormulario() {

        titulo.setFont(TemaSakura.FONTE_TITULO_DIALOGO);
        titulo.setForeground(TemaSakura.cores().rosaEscuro());

        JPanel topo = new JPanel(new BorderLayout(TemaSakura.ESPACO_M, 0));
        topo.setOpaque(false);
        topo.add(new IconeSakura(34), BorderLayout.WEST);
        topo.add(titulo, BorderLayout.CENTER);

        // O modo ativo fica desabilitado, indicando a seleção.
        JPanel modos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modos.setOpaque(false);
        for (Modo opcao : Modo.values()) {
            BotaoSakura botao = new BotaoSakura(opcao.rotulo, BotaoSakura.Estilo.SECUNDARIO);
            botao.addActionListener(e -> selecionar(opcao));
            botoesModo.put(opcao, botao);
            modos.add(botao);
        }

        linhaLogin = linha("Login:", campoLogin);
        linhaApelido = linha("Apelido:", campoApelido);
        linhaSenha = linha("Senha:", campoSenha);
        linhaConfirmacao = linha("Confirmar senha:", campoConfirmacao);
        linhaNome = linha("Nome de usuário:", campoNome);

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setOpaque(false);

        GridBagConstraints posicao = new GridBagConstraints();
        posicao.gridx = 0;
        posicao.gridy = 0;
        posicao.weightx = 1;
        posicao.fill = GridBagConstraints.HORIZONTAL;
        posicao.insets = new Insets(0, 0, TemaSakura.ESPACO_M, 0);

        campoSemTls.setOpaque(false);
        campoSemTls.setFont(TemaSakura.FONTE_PEQUENA);
        campoSemTls.setForeground(TemaSakura.cores().texto());
        avisoSemTls.setFont(TemaSakura.FONTE_PEQUENA);
        avisoSemTls.setForeground(TemaSakura.cores().coralTexto());
        avisoSemTls.setVisible(false);
        campoSemTls.addActionListener(e -> {
            avisoSemTls.setVisible(campoSemTls.isSelected());
            if (dialogo != null) {
                dialogo.pack();
            }
        });

        for (JComponent componente : new JComponent[] {
                topo, modos, linhaLogin, linhaApelido, linhaSenha, linhaConfirmacao, linhaNome,
                linha("Endereço do servidor:", campoServidor), linha("Porta:", campoPorta),
                campoSemTls, avisoSemTls}) {
            formulario.add(componente, posicao);
            posicao.gridy++;
        }

        // Foco no primeiro campo do modo quando o diálogo aparece.
        formulario.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                SwingUtilities.invokeLater(() -> primeiroCampo().requestFocusInWindow());
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {
            }
        });

        return formulario;
    }

    private void selecionar(Modo novo) {

        modo = novo;
        titulo.setText(novo.titulo);
        botoesModo.forEach((opcao, botao) -> botao.setEnabled(opcao != novo));

        linhaLogin.setVisible(novo != Modo.VISITANTE);
        linhaSenha.setVisible(novo != Modo.VISITANTE);
        linhaApelido.setVisible(novo == Modo.CADASTRAR);
        linhaConfirmacao.setVisible(novo == Modo.CADASTRAR);
        linhaNome.setVisible(novo == Modo.VISITANTE);

        if (dialogo != null) {
            dialogo.pack();
            primeiroCampo().requestFocusInWindow();
        }
    }

    private JTextField primeiroCampo() {
        if (modo == Modo.VISITANTE) {
            return campoNome;
        }
        // Login já preenchido (reconexão): o foco vai direto para a senha.
        return campoLogin.getText().isBlank() ? campoLogin : campoSenha;
    }

    /**
     * Valida os campos do modo ativo. As senhas só saem do campo após a
     * validação e vão direto para o pedido, que as apaga depois do envio.
     *
     * @throws IllegalArgumentException com mensagem para o usuário
     */
    private Escolha montarEscolha() {

        String endereco = campoServidor.getText().trim();

        if (endereco.isBlank()) {
            throw new IllegalArgumentException("Informe o endereço do servidor.");
        }

        int porta;

        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException e) {
            porta = -1;
        }

        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Informe uma porta válida.");
        }

        return new Escolha(endereco, porta, montarPedido(), !campoSemTls.isSelected());
    }

    private ConexaoServidor.PedidoEntrada montarPedido() {

        if (modo == Modo.VISITANTE) {
            String nome = campoNome.getText().trim();
            if (nome.isBlank() || nome.length() > 30 || nome.matches(".*\\s+.*")) {
                throw new IllegalArgumentException("O nome deve ter entre 1 e 30 caracteres, sem espaços.");
            }
            // Mesmas regras do servidor (invisíveis, espaços Unicode, alfabetos misturados).
            return new ConexaoServidor.Visitante(RegrasConta.normalizarNomeVisitante(nome));
        }

        String login = RegrasConta.normalizarLogin(campoLogin.getText());

        if (modo == Modo.ENTRAR) {
            char[] senha = campoSenha.getPassword();
            if (senha.length == 0) {
                throw new IllegalArgumentException("Informe a senha.");
            }
            campoSenha.limpar();
            return new ConexaoServidor.Entrar(login, senha);
        }

        String apelido = RegrasConta.normalizarApelido(campoApelido.getText());
        char[] senha = campoSenha.getPassword();
        char[] confirmacao = campoConfirmacao.getPassword();

        try {
            RegrasConta.validarSenha(senha);

            if (!Arrays.equals(senha, confirmacao)) {
                throw new IllegalArgumentException("As senhas não conferem.");
            }

        } catch (IllegalArgumentException e) {
            Arrays.fill(senha, '\0');
            throw e;

        } finally {
            Arrays.fill(confirmacao, '\0');
        }

        campoSenha.limpar();
        campoConfirmacao.limpar();

        return new ConexaoServidor.Cadastrar(login, apelido, senha);
    }

    private static JComponent linha(String rotulo, JTextField campo) {

        JLabel legenda = new JLabel(rotulo);
        legenda.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        legenda.setForeground(TemaSakura.cores().texto());
        legenda.setLabelFor(campo);

        JPanel linha = new JPanel(new BorderLayout(0, 4));
        linha.setOpaque(false);
        linha.add(legenda, BorderLayout.NORTH);
        linha.add(campo, BorderLayout.CENTER);
        return linha;
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
}
