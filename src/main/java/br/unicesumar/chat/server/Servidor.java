
package br.unicesumar.chat.server;

import br.unicesumar.chat.domain.Mensagem;
import br.unicesumar.chat.domain.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Servidor {

    public static final int PORTA_PADRAO = 12345;

    private final int porta;
    private final MensagemRepository repositorio;

    private final List<ClienteHandler> clientes =
            Collections.synchronizedList(new ArrayList<>());

    public Servidor(int porta, MensagemRepository repositorio) {

        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException(
                    "A porta deve estar entre 1 e 65535."
            );
        }

        if (repositorio == null) {
            throw new IllegalArgumentException(
                    "O repositório não pode ser nulo."
            );
        }

        this.porta = porta;
        this.repositorio = repositorio;
    }

    public void iniciar() {

        System.out.println("==================================");
        System.out.println("       CHAT SAKURA - SERVIDOR      ");
        System.out.println("==================================");

        try (ServerSocket servidorSocket = new ServerSocket(porta)) {

            System.out.println(
                    "Servidor iniciado na porta " + porta
            );

            System.out.println(
                    "Aguardando conexões..."
            );

            while (true) {

                Socket socket = servidorSocket.accept();

                System.out.println(
                        "Nova conexão: "
                                + socket.getRemoteSocketAddress()
                );

                ClienteHandler clienteHandler =
                        new ClienteHandler(
                                socket,
                                this,
                                repositorio
                        );

                Thread thread = new Thread(clienteHandler);

                thread.start();
            }

        } catch (IOException e) {

            System.err.println(
                    "Erro no servidor: " + e.getMessage()
            );
        }
    }

    public void adicionarCliente(ClienteHandler cliente) {

        clientes.add(cliente);

        System.out.println(
                "Clientes conectados: " + contarClientes()
        );
    }

    public void removerCliente(ClienteHandler cliente) {

        clientes.remove(cliente);

        System.out.println(
                "Clientes conectados: " + contarClientes()
        );
    }

    public int contarClientes() {

        synchronized (clientes) {
            return clientes.size();
        }
    }

    public void broadcast(Mensagem mensagem) {

        synchronized (clientes) {

            for (ClienteHandler cliente : clientes) {

                try {

                    cliente.enviarMensagem(
                            mensagem.toString()
                    );

                } catch (IOException e) {

                    System.err.println(
                            "Falha ao enviar mensagem para "
                                    + cliente.getNomeUsuario()
                                    + ": "
                                    + e.getMessage()
                    );
                }
            }
        }
    }

    public static void main(String[] args) {

        MensagemRepository repositorio =
                new MensagemRepositoryEmMemoria();

        Servidor servidor = new Servidor(
                PORTA_PADRAO,
                repositorio
        );

        servidor.iniciar();
    }
}
