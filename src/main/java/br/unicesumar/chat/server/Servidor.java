
package br.unicesumar.chat.server;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcConversaRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.service.ServicoAutenticacao;
import br.unicesumar.chat.service.ServicoConversas;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.util.Objects;

public final class Servidor {

    public static final int PORTA_PADRAO = 12345;

    private final int porta;
    private final ChatService chatService;

    /** Nulos juntos: servidor sem contas (somente apelidos, como antes). */
    private final ServicoAutenticacao autenticacao;
    private final ServicoConversas conversas;

    public Servidor(int porta, ChatService chatService) {
        this(porta, chatService, null, null);
    }

    public Servidor(
            int porta,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas
    ) {

        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException(
                    "A porta deve estar entre 1 e 65535."
            );
        }

        this.porta = porta;
        this.chatService = Objects.requireNonNull(
                chatService,
                "O serviço do chat é obrigatório."
        );
        this.autenticacao = autenticacao;
        this.conversas = conversas;
    }

    /** Serviços completos (chat, contas e conversas) sobre o mesmo banco. */
    public record Servicos(ChatService chat, ServicoAutenticacao autenticacao, ServicoConversas conversas) {

        public static Servicos montar(ConnectionFactory fabrica, HashSenha hashSenha, Clock relogio) {

            MensagemRepository mensagens = new JdbcMensagemRepository(fabrica);
            JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(fabrica);
            ClientRegistry registro = new ClientRegistry();
            ChatService chat = new ChatService(mensagens, registro, relogio);

            return new Servicos(
                    chat,
                    new ServicoAutenticacao(usuarios, hashSenha, relogio),
                    new ServicoConversas(new JdbcConversaRepository(fabrica), mensagens, usuarios,
                            chat, registro, relogio)
            );
        }

        public ClienteHandler atender(Socket socket) {
            return new ClienteHandler(socket, chat, autenticacao, conversas);
        }
    }

    /** Servidor completo (contas, conversas e histórico) sobre um banco SQLite. */
    public static Servidor comBanco(int porta, ConnectionFactory fabrica, HashSenha hashSenha) {
        Servicos servicos = Servicos.montar(fabrica, hashSenha, Clock.systemDefaultZone());
        return new Servidor(porta, servicos.chat(), servicos.autenticacao(), servicos.conversas());
    }

    public void iniciar() {

        System.out.println("==================================");
        System.out.println("       CHAT SAKURA - SERVIDOR      ");
        System.out.println("==================================");

        try (ServerSocket servidorSocket = new ServerSocket(porta)) {

            System.out.println("Servidor iniciado na porta " + porta);
            System.out.println("Aguardando conexões...");

            while (true) {

                Socket socket = servidorSocket.accept();

                System.out.println(
                        "Nova conexão: "
                                + socket.getRemoteSocketAddress()
                );

                ClienteHandler handler = new ClienteHandler(
                        socket,
                        chatService,
                        autenticacao,
                        conversas
                );

                new Thread(
                        handler,
                        "chat-cliente-" + socket.getPort()
                ).start();
            }

        } catch (IOException e) {

            System.err.println(
                    "Erro no servidor: " + e.getMessage()
            );
        }
    }

    public static void main(String[] args) {

        comBanco(
                PORTA_PADRAO,
                new ConnectionFactory("chat-sakura.db"),
                new HashSenha()
        ).iniciar();
    }
}
