
package br.unicesumar.chat.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClienteChat extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color ROSA = new Color(232, 122, 158);
    private static final Color FUNDO = new Color(255, 245, 249);
    private static final Color TEXTO = new Color(75, 55, 65);

    private final JTextArea areaMensagens = new JTextArea();
    private final JTextField campoMensagem = new JTextField();
    private final JButton botaoEnviar = new JButton("Enviar");
    private final JLabel status = new JLabel("Desconectado");

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter saida;

    private String nomeUsuario;

    public ClienteChat() {
        configurarJanela();
        montarInterface();
        conectar();
    }

    private void configurarJanela() {
        setTitle("Chat Sakura");
        setSize(650, 500);
        setMinimumSize(new Dimension(450, 350));
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
        JPanel principal = new JPanel(new BorderLayout(12, 12));
        principal.setBackground(FUNDO);
        principal.setBorder(
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        );

        JLabel titulo = new JLabel("Chat Sakura");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 24));
        titulo.setForeground(ROSA);

        status.setForeground(TEXTO);

        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(titulo, BorderLayout.WEST);
        topo.add(status, BorderLayout.EAST);

        areaMensagens.setEditable(false);
        areaMensagens.setLineWrap(true);
        areaMensagens.setWrapStyleWord(true);
        areaMensagens.setFont(
                new Font("SansSerif", Font.PLAIN, 14)
        );
        areaMensagens.setBackground(Color.WHITE);
        areaMensagens.setForeground(TEXTO);
        areaMensagens.setMargin(new Insets(12, 12, 12, 12));

        JScrollPane rolagem = new JScrollPane(areaMensagens);
        rolagem.setBorder(
                BorderFactory.createLineBorder(
                        new Color(245, 210, 223)
                )
        );

        campoMensagem.setFont(
                new Font("SansSerif", Font.PLAIN, 14)
        );

        botaoEnviar.setBackground(ROSA);
        botaoEnviar.setForeground(Color.WHITE);
        botaoEnviar.setFocusPainted(false);
        botaoEnviar.setEnabled(false);

        JPanel rodape = new JPanel(new BorderLayout(10, 0));
        rodape.setOpaque(false);
        rodape.add(campoMensagem, BorderLayout.CENTER);
        rodape.add(botaoEnviar, BorderLayout.EAST);

        botaoEnviar.addActionListener(e -> enviarMensagem());
        campoMensagem.addActionListener(e -> enviarMensagem());

        principal.add(topo, BorderLayout.NORTH);
        principal.add(rolagem, BorderLayout.CENTER);
        principal.add(rodape, BorderLayout.SOUTH);

        setContentPane(principal);
    }

    private void conectar() {
        String nome = JOptionPane.showInputDialog(
                this,
                "Digite seu nome de usuário:",
                "Entrar no Chat Sakura",
                JOptionPane.QUESTION_MESSAGE
        );

        if (nome == null || nome.isBlank()) {
            dispose();
            return;
        }

        nomeUsuario = nome.trim();

        String endereco = JOptionPane.showInputDialog(
                this,
                "Endereço do servidor:",
                "localhost"
        );

        if (endereco == null || endereco.isBlank()) {
            dispose();
            return;
        }

        String portaTexto = JOptionPane.showInputDialog(
                this,
                "Porta do servidor:",
                "12345"
        );

        if (portaTexto == null) {
            dispose();
            return;
        }

        final int porta;

        try {
            porta = Integer.parseInt(portaTexto.trim());

            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Porta inválida.",
                    "Erro",
                    JOptionPane.ERROR_MESSAGE
            );
            dispose();
            return;
        }

        status.setText("Conectando...");

        Thread threadConexao = new Thread(() -> {
            try {
                socket = new Socket(endereco.trim(), porta);

                entrada = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                saida = new PrintWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8
                        ),
                        true
                );

                // O servidor envia duas linhas iniciais.
                // Depois espera receber o nome do usuário.
                String primeiraLinha = entrada.readLine();
                String segundaLinha = entrada.readLine();

                if (primeiraLinha == null || segundaLinha == null) {
                    throw new IOException(
                            "O servidor encerrou a conexão."
                    );
                }

                adicionarMensagem(primeiraLinha);
                adicionarMensagem(segundaLinha);

                saida.println(nomeUsuario);

                SwingUtilities.invokeLater(() -> {
                    status.setText("Conectado: " + nomeUsuario);
                    botaoEnviar.setEnabled(true);
                    campoMensagem.setEnabled(true);
                    campoMensagem.requestFocusInWindow();
                });

                String linha;

                while ((linha = entrada.readLine()) != null) {
                    adicionarMensagem(linha);
                }

            } catch (IOException e) {
                adicionarMensagem(
                        "SISTEMA: Erro de conexão: " + e.getMessage()
                );

            } finally {
                desconectar();

                SwingUtilities.invokeLater(() -> {
                    status.setText("Desconectado");
                    botaoEnviar.setEnabled(false);
                    campoMensagem.setEnabled(false);
                });
            }
        }, "chat-sakura-rede");

        threadConexao.setDaemon(true);
        threadConexao.start();
    }

    private void enviarMensagem() {
        String texto = campoMensagem.getText().trim();

        if (texto.isEmpty() || saida == null) {
            return;
        }

        saida.println(texto);

        if (saida.checkError()) {
            adicionarMensagem(
                    "SISTEMA: Não foi possível enviar a mensagem."
            );
            return;
        }

        campoMensagem.setText("");
    }

    private void adicionarMensagem(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            areaMensagens.append(mensagem + "\n");
            areaMensagens.setCaretPosition(
                    areaMensagens.getDocument().getLength()
            );
        });
    }

    private void desconectar() {
        try {
            if (saida != null) {
                saida.println("/sair");
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

        } catch (IOException e) {
            System.err.println(
                    "Erro ao desconectar: " + e.getMessage()
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClienteChat cliente = new ClienteChat();

            if (cliente.isDisplayable()) {
                cliente.setVisible(true);
            }
        });
    }
}
