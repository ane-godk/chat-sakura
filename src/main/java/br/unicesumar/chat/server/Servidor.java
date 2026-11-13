
package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorIndisponivel;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.service.ServicoIA;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcConversaRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcMemoriaRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcAparenciaRepository;
import br.unicesumar.chat.service.ServicoPreferencias;
import br.unicesumar.chat.service.ServicoMensagens;
import br.unicesumar.chat.persistence.jdbc.JdbcFixacaoRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.service.ServicoAutenticacao;
import br.unicesumar.chat.service.ServicoConversas;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

public final class Servidor {

    public static final int PORTA_PADRAO = 12345;

    static final String MENSAGEM_LOTADO =
            "SISTEMA: Servidor lotado ou conexões demais deste endereço. Tente novamente mais tarde.";

    private final int porta;
    private final ChatService chatService;

    /** Nulos juntos: servidor sem contas (somente apelidos, como antes). */
    private final ServicoAutenticacao autenticacao;
    private final ServicoConversas conversas;

    private final Servicos servicos;
    private final LimitesServidor limites;
    private final ControleConexoes vagas;

    public Servidor(int porta, ChatService chatService) {
        this(porta, chatService, null, null);
    }

    public Servidor(
            int porta,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas
    ) {
        this(porta, chatService, autenticacao, conversas, null, LimitesServidor.padrao());
    }

    private Servidor(
            int porta,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas,
            Servicos servicos,
            LimitesServidor limites
    ) {
        if (porta < 0 || porta > 65535) {
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
        this.servicos = servicos;
        this.limites = Objects.requireNonNull(limites);
        this.vagas = new ControleConexoes(limites.maxConexoes(), limites.maxConexoesPorIp());
    }

    /** Serviços completos (chat, contas e conversas) sobre o mesmo banco. */
    public record Servicos(ChatService chat, ServicoAutenticacao autenticacao, ServicoConversas conversas,
                           ServicoIA ia, LimitesServidor limites, ServicoPreferencias preferencias,
                           ServicoMensagens mensagens) {

        /** Provedor de IA conforme as variáveis de ambiente (Qwen, SIMULADO ou indisponível). */
        public static Servicos montar(ConnectionFactory fabrica, HashSenha hashSenha, Clock relogio) {
            return montar(fabrica, hashSenha, relogio, ProvedorIndisponivel.doAmbiente(System.getenv()));
        }

        public static Servicos montar(ConnectionFactory fabrica, HashSenha hashSenha, Clock relogio,
                                      ProvedorIA provedor) {
            return montar(fabrica, hashSenha, relogio, provedor, LimitesServidor.padrao());
        }

        public static Servicos montar(ConnectionFactory fabrica, HashSenha hashSenha, Clock relogio,
                                      ProvedorIA provedor, LimitesServidor limites) {

            // Migração única, antes de qualquer repositório (os construtores
            // dos repositórios apenas confirmam que ela já ocorreu).
            fabrica.garantirEsquema();

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
                    new ServicoIA(repositorioConversas, mensagens, conversas, provedor,
                            new JdbcMemoriaRepository(fabrica), relogio),
                    limites,
                    new ServicoPreferencias(new JdbcAparenciaRepository(fabrica), relogio),
                    new ServicoMensagens(repositorioConversas, mensagens, new JdbcFixacaoRepository(fabrica),
                            registro, relogio)
            );
        }

        public ClienteHandler atender(Socket socket) {
            return atender(socket, () -> { });
        }

        /** @param aoEncerrar chamado uma vez ao fim da sessão (libera a vaga) */
        public ClienteHandler atender(Socket socket, Runnable aoEncerrar) {
            return new ClienteHandler(socket, chat, autenticacao, conversas, ia, limites, aoEncerrar, preferencias,
                    mensagens);
        }
    }

    /**
     * Servidor completo (contas, conversas, IA e histórico) sobre um banco
     * SQLite, com o transporte do ambiente (TLS por padrão).
     *
     * @throws IllegalStateException se o TLS não estiver configurado
     */
    public static Servidor comBanco(int porta, ConnectionFactory fabrica, HashSenha hashSenha) {
        return comBanco(porta, fabrica, hashSenha, ProvedorIndisponivel.doAmbiente(System.getenv()));
    }

    /** Servidor completo com o provedor de IA indicado e o transporte do ambiente. */
    public static Servidor comBanco(int porta, ConnectionFactory fabrica, HashSenha hashSenha, ProvedorIA provedor) {
        // O transporte é lido antes de abrir o banco: sem TLS válido, nada é migrado.
        TransporteServidor transporte = TransporteServidor.doAmbiente(System.getenv());
        return new Servidor(porta, Servicos.montar(fabrica, hashSenha, Clock.systemDefaultZone(), provedor,
                LimitesServidor.doAmbiente(System.getenv())), transporte);
    }

    /** Sem transporte definido: TCP de desenvolvimento no loopback (testes que abrem o próprio socket). */
    Servidor(int porta, Servicos servicos) {
        this(porta, servicos, TransporteServidor.tcpDesenvolvimento());
    }

    public Servidor(int porta, Servicos servicos, TransporteServidor transporte) {
        this(porta, servicos.chat(), servicos.autenticacao(), servicos.conversas(), servicos, servicos.limites());
        this.transporte = Objects.requireNonNull(transporte);
    }

    private TransporteServidor transporte = TransporteServidor.tcpDesenvolvimento();

    /** Socket de escuta conforme o transporte (TLS ou TCP de desenvolvimento). */
    public ServerSocket abrirSocket() throws IOException {
        return transporte.abrir(porta);
    }

    /** Vagas de conexão (diagnóstico e testes). */
    ControleConexoes vagas() {
        return vagas;
    }

    public void iniciar() {

        System.out.println("==================================");
        System.out.println("       CHAT SAKURA - SERVIDOR      ");
        System.out.println("==================================");

        try (ServerSocket servidorSocket = abrirSocket()) {

            System.out.println("Servidor iniciado na porta " + servidorSocket.getLocalPort());
            System.out.println("Transporte: " + transporte.descricao());
            if (servicos != null) {
                br.unicesumar.chat.ia.ParametrosIA p = servicos.ia().parametros();
                System.out.println("Sakura IA: " + servicos.ia().provedor().descricao() + " (contexto "
                        + p.janelaTokens() + " tokens, resposta " + p.tokensResposta() + ", "
                        + p.geracoesSimultaneas() + " geração(ões) por vez, fila " + p.filaMaxima() + ")");
                // Opcional (QWEN_AQUECER): carrega o modelo sem atrasar a inicialização.
                servicos.ia().aquecerEmSegundoPlano();
            }
            System.out.println("Limites: " + limites.maxConexoes() + " conexões ("
                    + limites.maxConexoesPorIp() + " por IP), entrada em "
                    + limites.prazoEntrada().toSeconds() + " s, PING v2 a cada "
                    + limites.intervaloPing().toSeconds() + " s.");
            System.out.println("Aguardando conexões...");

            atenderConexoes(servidorSocket);

        } catch (IOException e) {

            System.err.println(
                    "Erro no servidor: " + e.getMessage()
            );
        }
    }

    /**
     * Laço de aceitação até o {@code ServerSocket} ser fechado. Cada conexão
     * precisa de uma vaga (limite global e por IP); sem vaga, recebe um aviso
     * curto e é fechada na hora, sem criar thread.
     */
    public void atenderConexoes(ServerSocket servidorSocket) {

        while (!servidorSocket.isClosed()) {

            Socket socket;

            try {
                socket = servidorSocket.accept();
            } catch (IOException e) {
                if (!servidorSocket.isClosed()) {
                    System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                }
                continue;
            }

            InetAddress endereco = socket.getInetAddress();
            String ip = endereco == null ? "?" : endereco.getHostAddress();

            if (!vagas.reservar(ip)) {
                recusar(socket, ip);
                continue;
            }

            System.out.println(
                    "Nova conexão: "
                            + socket.getRemoteSocketAddress()
            );

            Runnable liberar = () -> vagas.liberar(ip);

            ClienteHandler handler = servicos != null
                    ? servicos.atender(socket, liberar)
                    : new ClienteHandler(socket, chatService, autenticacao, conversas, null, limites, liberar);

            try {
                new Thread(
                        handler,
                        "chat-cliente-" + socket.getPort()
                ).start();
            } catch (RuntimeException | OutOfMemoryError e) {
                vagas.liberar(ip);
                fechar(socket);
                System.err.println("Não foi possível atender a conexão: " + e.getMessage());
            }
        }
    }

    /** Aviso curto num socket recém-aceito (cabe no buffer: não bloqueia). */
    private static void recusar(Socket socket, String ip) {
        System.err.println("Conexão recusada (limite atingido): " + ip);
        try {
            OutputStream saida = socket.getOutputStream();
            saida.write((MENSAGEM_LOTADO + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            saida.flush();
        } catch (IOException e) {
            // Cliente já foi embora.
        }
        fechar(socket);
    }

    private static void fechar(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // Nada a fazer.
        }
    }

    public static void main(String[] args) {

        Servidor servidor;
        try {
            servidor = comBanco(
                    PORTA_PADRAO,
                    new ConnectionFactory("chat-sakura.db"),
                    new HashSenha()
            );
        } catch (IllegalStateException e) {
            // Configuração de TLS ausente ou inválida: não inicia (sem fallback para TCP).
            System.err.println("Chat Sakura: o servidor não foi iniciado. " + e.getMessage());
            System.exit(1);
            return;
        }
        servidor.iniciar();
    }
}
