
package br.unicesumar.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClienteChat extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color ROSA = new Color(225, 105, 150);
    private static final Color ROSA_CLARO = new Color(255, 230, 240);
    private static final Color FUNDO = new Color(255, 247, 250);
    private static final Color TEXTO = new Color(75, 55, 68);
    private static final Color VERDE = new Color(45, 145, 95);

    private final JTextArea areaMensagens = new JTextArea();
    private final JTextField campoMensagem = new JTextField();
    private final JButton botaoEnviar = new JButton("Enviar");
    private final JButton botaoConectar = new JButton("Conectar");
    private final JButton botaoDesconectar = new JButton("Sair");
    private final JLabel labelStatus = new JLabel("Desconectado");

    private final AtomicBoolean conectado = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile BufferedWriter saida;

    private String nomeUsuario;

    public ClienteChat() {
        configurarJanela();
        montarInterface();
        atualizarEstado(false);
    }

    private void configurarJanela() {
        setTitle("Chat Sakura");
        setSize(760, 560);
        setMinimumSize(new Dimension(560, 400));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }
        });
    }

    private void montarInterface() {

        JPanel principal = new JPanel(new BorderLayout(0, 14));
        principal.setBackground(FUNDO);
        principal.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel cabecalho = new JPanel(new BorderLayout(10, 8));
        cabecalho.setOpaque(false);

        JLabel titulo = new JLabel("Chat Sakura");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 28));
        titulo.setForeground(ROSA);

        JLabel subtitulo = new JLabel("Converse em tempo real");
        subtitulo.setForeground(TEXTO);

        JPanel titulos = new JPanel(new GridLayout(2, 1));
        titulos.setOpaque(false);
        titulos.add(titulo);
        titulos.add(subtitulo);

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acoes.setOpaque(false);

        estilizarBotao(botaoConectar, ROSA);
        estilizarBotao(botaoDesconectar, new Color(130, 110, 125));

        acoes.add(botaoConectar);
        acoes.add(botaoDesconectar);

        cabecalho.add(titulos, BorderLayout.WEST);
        cabecalho.add(acoes, BorderLayout.EAST);

        areaMensagens.setEditable(false);
        areaMensagens.setLineWrap(true);
        areaMensagens.setWrapStyleWord(true);
        areaMensagens.setFont(new Font("SansSerif", Font.PLAIN, 14));
        areaMensagens.setBackground(Color.WHITE);
        areaMensagens.setForeground(TEXTO);
        areaMensagens.setMargin(new Insets(16, 16, 16, 16));

        JScrollPane rolagem = new JScrollPane(areaMensagens);
        rolagem.setBorder(
                BorderFactory.createLineBorder(ROSA_CLARO, 2)
        );

        JPanel inferior = new JPanel(new BorderLayout(0, 10));
        inferior.setOpaque(false);

        labelStatus.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel envio = new JPanel(new BorderLayout(10, 0));
        envio.setOpaque(false);

        campoMensagem.setFont(new Font("SansSerif", Font.PLAIN, 14));
        campoMensagem.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ROSA_CLARO, 2),
                        new EmptyBorder(10, 12, 10, 12)
                )
        );

        estilizarBotao(botaoEnviar, ROSA);

        envio.add(campoMensagem, BorderLayout.CENTER);
        envio.add(botaoEnviar, BorderLayout.EAST);

        inferior.add(labelStatus, BorderLayout.NORTH);
        inferior.add(envio, BorderLayout.CENTER);

        principal.add(cabecalho, BorderLayout.NORTH);
        principal.add(rolagem, BorderLayout.CENTER);
        principal.add(inferior, BorderLayout.SOUTH);

        setContentPane(principal);

        botaoConectar.addActionListener(e -> abrirDialogoConexao());
        botaoDesconectar.addActionListener(e -> desconectar());
        botaoEnviar.addActionListener(e -> enviarMensagem());
        campoMensagem.addActionListener(e -> enviarMensagem());
    }

    private void estilizarBotao(JButton botao, Color cor) {
        botao.setBackground(cor);
        botao.setForeground(Color.WHITE);
        botao.setFont(new Font("SansSerif", Font.BOLD, 13));
        botao.setFocusPainted(false);
        botao.setBorder(new EmptyBorder(11, 18, 11, 18));
        botao.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );
    }

    private void abrirDialogoConexao() {

        JTextField campoNome = new JTextField(15);
        JTextField campoServidor = new JTextField("localhost", 15);
        JTextField campoPorta = new JTextField("12345", 15);

        JPanel formulario = new JPanel(new GridLayout(0, 1, 5, 5));
        formulario.add(new JLabel("Nome de usuário:"));
        formulario.add(campoNome);
        formulario.add(new JLabel("Endereço do servidor:"));
        formulario.add(campoServidor);
        formulario.add(new JLabel("Porta:"));
        formulario.add(campoPorta);

        int opcao = JOptionPane.showConfirmDialog(
                this,
                formulario,
                "Conectar ao Chat Sakura",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (opcao != JOptionPane.OK_OPTION) {
            return;
        }

        String nome = campoNome.getText().trim();
        String endereco = campoServidor.getText().trim();

        if (nome.isBlank() || nome.length() > 30
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
            porta = Integer.parseInt(campoPorta.getText().trim());

            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            mostrarErro("Informe uma porta válida.");
            return;
        }

        nomeUsuario = nome;
        botaoConectar.setEnabled(false);
        labelStatus.setText("Conectando...");

        Thread thread = new Thread(
                () -> conectar(endereco, porta, nome),
                "sakura-conexao"
        );

        thread.setDaemon(true);
        thread.start();
    }

    private void conectar(String endereco, int porta, String nome) {

        Socket novaConexao = new Socket();

        try {
            novaConexao.connect(
                    new InetSocketAddress(endereco, porta),
                    5000
            );

            socket = novaConexao;

            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                            novaConexao.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            saida = new BufferedWriter(
                    new OutputStreamWriter(
                            novaConexao.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            // Protocolo atual do servidor:
            // duas linhas de boas-vindas antes do nickname.
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

            adicionarMensagem(confirmacao);

            SwingUtilities.invokeLater(() -> {
                atualizarEstado(true);
                campoMensagem.requestFocusInWindow();
            });

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

            try {
                novaConexao.close();
            } catch (IOException e) {
                System.err.println(
                        "Erro ao fechar conexão: " + e.getMessage()
                );
            }

            if (socket == novaConexao) {
                socket = null;
                saida = null;
            }

            SwingUtilities.invokeLater(() -> atualizarEstado(false));
        }
    }

    private synchronized void escreverLinha(String texto)
            throws IOException {

        if (saida == null) {
            throw new IOException("Não existe conexão ativa.");
        }

        saida.write(texto);
        saida.newLine();
        saida.flush();
    }

    private void enviarMensagem() {

        if (!conectado.get()) {
            return;
        }

        String texto = campoMensagem.getText().trim();

        if (texto.isEmpty()) {
            return;
        }

        campoMensagem.setText("");

        Thread thread = new Thread(() -> {

            try {
                escreverLinha(texto);

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
                        "Erro ao desconectar: " + e.getMessage()
                );
            }
        }

        atualizarEstado(false);
    }

    private void atualizarEstado(boolean ativo) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(
                    () -> atualizarEstado(ativo)
            );
            return;
        }

        botaoConectar.setEnabled(!ativo);
        botaoDesconectar.setEnabled(ativo);
        botaoEnviar.setEnabled(ativo);
        campoMensagem.setEnabled(ativo);

        labelStatus.setText(
                ativo
                        ? "Conectado como " + nomeUsuario
                        : "Desconectado"
        );

        labelStatus.setForeground(
                ativo ? VERDE : new Color(160, 75, 90)
        );
    }

    private void adicionarMensagem(String mensagem) {

        SwingUtilities.invokeLater(() -> {

            areaMensagens.append(mensagem + "\n");

            areaMensagens.setCaretPosition(
                    areaMensagens.getDocument().getLength()
            );
        });
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
