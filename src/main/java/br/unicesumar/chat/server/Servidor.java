
package br.unicesumar.chat.server;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.util.Objects;

public final class Servidor {

    public static final int PORTA_PADRAO = 12345;

    private final int porta;
    private final ChatService chatService;

    public Servidor(int porta, ChatService chatService) {

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
                        chatService
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

        ConnectionFactory fabrica =
                new ConnectionFactory("chat-sakura.db");

        MensagemRepository repositorio =
                new JdbcMensagemRepository(fabrica);

        ClientRegistry registro = new ClientRegistry();

        ChatService servico = new ChatService(
                repositorio,
                registro,
                Clock.systemDefaultZone()
        );

        Servidor servidor = new Servidor(
                PORTA_PADRAO,
                servico
        );

        servidor.iniciar();
    }
}
