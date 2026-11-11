
package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorIndisponivel;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.service.ServicoIA;
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
        this(porta, chatService, autenticacao, conversas, null);
    }

    private Servidor(
            int porta,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas,
            Servicos servicos
    ) {
        this.servicos = servicos;

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
    public record Servicos(ChatService chat, ServicoAutenticacao autenticacao, ServicoConversas conversas,
                           ServicoIA ia) {

        /** Provedor de IA conforme as variáveis de ambiente (Qwen, SIMULADO ou indisponível). */
        public static Servicos montar(ConnectionFactory fabrica, HashSenha hashSenha, Clock relogio) {
            return montar(fabrica, hashSenha, relogio, ProvedorIndisponivel.doAmbiente(System.getenv()));
        }

        public static Servicos montar(ConnectionFactory fabrica, HashSenha hashSenha, Clock relogio,
                                      ProvedorIA provedor) {

            MensagemRepository mensagens = new JdbcMensagemRepository(fabrica);
            JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(fabrica);
            JdbcConversaRepository repositorioConversas = new JdbcConversaRepository(fabrica);
            ClientRegistry registro = new ClientRegistry();
            ChatService chat = new ChatService(mensagens, registro, relogio);
            ServicoConversas conversas = new ServicoConversas(repositorioConversas, mensagens, usuarios,
                    chat, registro, relogio);

            return new Servicos(
                    chat,
                    new ServicoAutenticacao(usuarios, hashSenha, relogio),
                    conversas,
                    new ServicoIA(repositorioConversas, mensagens, conversas, provedor, relogio)
            );
        }

        public ClienteHandler atender(Socket socket) {
            return new ClienteHandler(socket, chat, autenticacao, conversas, ia);
        }
    }

    /** Servidor completo (contas, conversas, IA e histórico) sobre um banco SQLite. */
    public static Servidor comBanco(int porta, ConnectionFactory fabrica, HashSenha hashSenha) {
        return new Servidor(porta, Servicos.montar(fabrica, hashSenha, Clock.systemDefaultZone()));
    }

    /** Servidor completo com o provedor de IA indicado. */
    public static Servidor comBanco(int porta, ConnectionFactory fabrica, HashSenha hashSenha, ProvedorIA provedor) {
        return new Servidor(porta, Servicos.montar(fabrica, hashSenha, Clock.systemDefaultZone(), provedor));
    }

    private final Servicos servicos;

    private Servidor(int porta, Servicos servicos) {
        this(porta, servicos.chat(), servicos.autenticacao(), servicos.conversas(), servicos);
    }

    public void iniciar() {

        System.out.println("==================================");
        System.out.println("       CHAT SAKURA - SERVIDOR      ");
        System.out.println("==================================");

        try (ServerSocket servidorSocket = new ServerSocket(porta)) {

            System.out.println("Servidor iniciado na porta " + porta);
            if (servicos != null) {
                System.out.println("Sakura IA: " + servicos.ia().provedor().descricao());
            }
            System.out.println("Aguardando conexões...");

            while (true) {

                Socket socket = servidorSocket.accept();

                System.out.println(
                        "Nova conexão: "
                                + socket.getRemoteSocketAddress()
                );

                ClienteHandler handler = servicos != null
                        ? servicos.atender(socket)
                        : new ClienteHandler(socket, chatService, autenticacao, conversas);

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
