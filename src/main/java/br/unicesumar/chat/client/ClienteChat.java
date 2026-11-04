
package br.unicesumar.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
    private final JButton botaoExportar = new JButton("Exportar TXT");

    private final JLabel labelStatus = new JLabel("Desconectado");

    private final AtomicBoolean conectado = new AtomicBoolean(false);
    private final AtomicBoolean conectando = new AtomicBoolean(false);

    private final Object bloqueioEscrita = new Object();

    private volatile Socket socket;
    private volatile BufferedWriter saida;

    private volatile String nomeUsuario = "";

    public ClienteChat() {
        configurarJanela();
        montarInterface();
        atualizarEstado();
    }

    private void configurarJanela() {
        setTitle("Chat Sakura");
        setSize(800, 580);
        setMinimumSize(new Dimension(600, 420));
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

        JPanel acoes = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0)
        );
        acoes.setOpaque(false);

        estilizarBotao(botaoConectar, ROSA);
        estilizarBotao(
                botaoDesconectar,
                new Color(130, 110, 125)
        );
        estilizarBotao(botaoExportar, ROSA);

        acoes.add(botaoConectar);
        acoes.add(botaoDesconectar);
        acoes.add(botaoExportar);

        cabecalho.add(titulos, BorderLayout.WEST);
        cabecalho.add(acoes, BorderLayout.EAST);

        areaMensagens.setEditable(false);
        areaMensagens.setLineWrap(true);
        areaMensagens.setWrapStyleWord(true);
        areaMensagens.setFont(
                new Font("SansSerif", Font.PLAIN, 14)
        );
        areaMensagens.setBackground(Color.WHITE);
        areaMensagens.setForeground(TEXTO);
        areaMensagens.setMargin(
                new Insets(16, 16, 16, 16)
        );

        JScrollPane rolagem = new JScrollPane(areaMensagens);
        rolagem.setBorder(
                BorderFactory.createLineBorder(
                        ROSA_CLARO, 2
                )
        );

        JPanel inferior = new JPanel(
                new BorderLayout(0, 10)
        );
        inferior.setOpaque(false);

        labelStatus.setFont(
                new Font("SansSerif", Font.BOLD, 12)
        );

        JPanel envio = new JPanel(
                new BorderLayout(10, 0)
        );
        envio.setOpaque(false);

        campoMensagem.setFont(
                new Font("SansSerif", Font.PLAIN, 14)
        );

        campoMensagem.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                ROSA_CLARO, 2
                        ),
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

    private void estilizarBotao(
            JButton botao,
            Color cor
    ) {
        botao.setBackground(cor);
        botao.setForeground(Color.WHITE);
        botao.setFont(
                new Font("SansSerif", Font.BOLD, 13)
        );
        botao.setFocusPainted(false);
        botao.setBorder(
                new EmptyBorder(11, 18, 11, 18)
        );
        botao.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );
    }

    private void abrirDialogoConexao() {

        if (conectado.get() || conectando.get()) {
            return;
        }

        JTextField campoNome = new JTextField(15);
        JTextField campoServidor =
                new JTextField("localhost", 15);
        JTextField campoPorta =
                new JTextField("12345", 15);

        JPanel formulario = new JPanel(
                new GridLayout(0, 1, 5, 5)
        );

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
        areaMensagens.setText("");
        atualizarEstado();

        Thread thread = new Thread(
                () -> conectar(endereco, porta, nome),
                "sakura-conexao"
        );

        thread.setDaemon(true);
        thread.start();
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

        if (texto.length() > 500) {
            mostrarErro(
                    "A mensagem deve ter no máximo 500 caracteres."
            );
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
                    !areaMensagens.getText().isBlank()
            );

            if (ativo) {

                labelStatus.setText(
                        "Conectado como " + nomeUsuario
                );

                labelStatus.setForeground(VERDE);

            } else if (emConexao) {

                labelStatus.setText("Conectando...");
                labelStatus.setForeground(ROSA);

            } else {

                labelStatus.setText("Desconectado");
                labelStatus.setForeground(
                        new Color(160, 75, 90)
                );
            }
        });
    }

    private void adicionarMensagem(String mensagem) {

        SwingUtilities.invokeLater(() -> {

            areaMensagens.append(mensagem + "\n");

            areaMensagens.setCaretPosition(
                    areaMensagens.getDocument().getLength()
            );

            botaoExportar.setEnabled(true);
        });
    }

    private void exportarHistorico() {

        String conteudo = areaMensagens.getText();

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
