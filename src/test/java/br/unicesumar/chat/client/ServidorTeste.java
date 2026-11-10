package br.unicesumar.chat.client;

import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.server.ClienteHandler;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.service.ServicoAutenticacao;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servidor real do chat (ClienteHandler + ChatService) em porta efêmera
 * do loopback, com histórico em memória: isolado do banco principal.
 */
final class ServidorTeste implements AutoCloseable {

    static final String ENDERECO = InetAddress.getLoopbackAddress().getHostAddress();

    final MensagemRepositoryEmMemoria repositorio = new MensagemRepositoryEmMemoria();

    private final ServerSocket servidor =
            new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> conexoes = new CopyOnWriteArrayList<>();

    ServidorTeste() throws IOException {
        this(null);
    }

    /** @param bancoContas SQLite temporário para contas; {@code null} = sem contas */
    ServidorTeste(Path bancoContas) throws IOException {

        ChatService servico = new ChatService(
                repositorio,
                new ClientRegistry(),
                Clock.systemDefaultZone()
        );

        ServicoAutenticacao contas = bancoContas == null
                ? null
                : new ServicoAutenticacao(
                        new JdbcUsuarioRepository(new ConnectionFactory(bancoContas.toString())),
                        new HashSenha(1_000),
                        Clock.systemDefaultZone());

        threads.execute(() -> {
            while (!servidor.isClosed()) {
                try {
                    Socket socket = servidor.accept();
                    conexoes.add(socket);
                    threads.execute(new ClienteHandler(socket, servico, contas));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    int porta() {
        return servidor.getLocalPort();
    }

    @Override
    public void close() throws Exception {
        servidor.close();
        for (Socket socket : conexoes) {
            socket.close();
        }
        threads.shutdown();
        if (!threads.awaitTermination(5, TimeUnit.SECONDS)) {
            threads.shutdownNow();
        }
    }
}
