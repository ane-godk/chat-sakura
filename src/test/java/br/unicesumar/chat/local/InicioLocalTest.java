package br.unicesumar.chat.local;

import br.unicesumar.chat.ia.ProvedorIndisponivel;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.net.BindException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Inicializador local: porta, pasta de dados e servidor somente no loopback (sem certificados). */
class InicioLocalTest {

    @TempDir
    Path pasta;

    private final ExecutorService threads = Executors.newCachedThreadPool();

    @AfterEach
    void encerrar() throws InterruptedException {
        threads.shutdownNow();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        // Atendimentos do Servidor real (threads próprias) soltam o banco antes da limpeza.
        for (Thread atendimento : Thread.getAllStackTraces().keySet()) {
            if (atendimento.getName().startsWith("chat-cliente-")) {
                atendimento.join(10_000);
            }
        }
    }

    private static int portaLivre() throws IOException {
        try (ServerSocket temporario = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return temporario.getLocalPort();
        }
    }

    /** Servidor falso que envia {@code primeiraLinha} (ou nada) a cada conexão. */
    private ServerSocket falso(String primeiraLinha) throws IOException {
        ServerSocket socket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        threads.execute(() -> {
            while (!socket.isClosed()) {
                try (Socket cliente = socket.accept()) {
                    if (primeiraLinha != null) {
                        Writer saida = new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8);
                        saida.write(primeiraLinha + "\r\n");
                        saida.flush();
                    }
                    cliente.getInputStream().read();   // espera o outro lado fechar
                } catch (IOException e) {
                    return;
                }
            }
        });
        return socket;
    }

    @Test
    void identificaPortaLivreServidorSakuraEOutroPrograma() throws Exception {
        assertEquals(InicioLocal.EstadoPorta.LIVRE, InicioLocal.sondar(portaLivre(), 1_000));

        try (ServerSocket sakura = falso(InicioLocal.SAUDACAO);
             ServerSocket outro = falso("HTTP/1.1 400 Bad Request");
             ServerSocket mudo = falso(null)) {
            assertEquals(InicioLocal.EstadoPorta.CHAT_SAKURA, InicioLocal.sondar(sakura.getLocalPort(), 1_000));
            assertEquals(InicioLocal.EstadoPorta.OUTRO_PROGRAMA, InicioLocal.sondar(outro.getLocalPort(), 1_000));
            // Conecta mas não fala (como um servidor TLS esperando a negociação).
            assertEquals(InicioLocal.EstadoPorta.OUTRO_PROGRAMA, InicioLocal.sondar(mudo.getLocalPort(), 500));

            assertEquals(InicioLocal.SAKURA_ATIVO, InicioLocal.verificar(sakura.getLocalPort()));
            assertEquals(InicioLocal.PORTA_OCUPADA, InicioLocal.verificar(outro.getLocalPort()));
        }
        assertEquals(InicioLocal.PORTA_LIVRE, InicioLocal.verificar(portaLivre()));
    }

    @Test
    void aguardarTerminaQuandoOServidorRespondeOuQuandoEleRegistraFalha() throws Exception {
        try (ServerSocket sakura = falso(InicioLocal.SAUDACAO)) {
            assertEquals(InicioLocal.SAKURA_ATIVO, InicioLocal.aguardar(sakura.getLocalPort(), 5, null));
        }

        Path marcador = InicioLocal.marcadorFalha(pasta);
        Files.createDirectories(marcador.getParent());
        Files.writeString(marcador, "falhou");
        long inicio = System.nanoTime();
        assertEquals(InicioLocal.FALHA, InicioLocal.aguardar(portaLivre(), 60, marcador));
        assertTrue(System.nanoTime() - inicio < TimeUnit.SECONDS.toNanos(10), "Não espera o prazo inteiro");

        assertEquals(InicioLocal.SEM_RESPOSTA, InicioLocal.aguardar(portaLivre(), 1, null));
    }

    @Test
    void pastaDeDadosFicaAoLadoDoProgramaMesmoComEspacosEAcentos() throws Exception {
        Path projeto = Files.createDirectories(pasta.resolve("Área de Trabalho").resolve("chat sakura-main"));
        Path alvo = Files.createDirectories(projeto.resolve("target"));
        Path jar = Files.writeString(alvo.resolve("chat-sakura.jar"), "");
        assertEquals(projeto.resolve("dados").toAbsolutePath(), InicioLocal.pastaDados(jar));
        assertEquals(projeto.resolve("dados").toAbsolutePath(),
                InicioLocal.pastaDados(Files.createDirectories(alvo.resolve("classes"))));

        Path portatil = Files.createDirectories(pasta.resolve("Pen drive").resolve("ChatSakura-Portátil"));
        Path jarPortatil = Files.writeString(portatil.resolve("chat-sakura.jar"), "");
        assertEquals(portatil.resolve("dados").toAbsolutePath(), InicioLocal.pastaDados(jarPortatil));
    }

    /**
     * O servidor do modo local nasce sem keystore nem senha técnica e escuta
     * somente no loopback, mesmo que o ambiente peça TLS ou outra interface.
     */
    @Test
    void servidorLocalSemCertificadosEscutaSomenteNoLoopback() throws Exception {
        Servidor servidor = InicioLocal.criarServidor(pasta.resolve("dados"), 0, new ProvedorIndisponivel(null));
        try (ServerSocket escuta = servidor.abrirSocket()) {
            assertTrue(escuta.getInetAddress().isLoopbackAddress(), "Somente 127.0.0.1");
            assertFalse(escuta instanceof javax.net.ssl.SSLServerSocket, "Modo local é TCP de desenvolvimento");
            threads.execute(() -> servidor.atenderConexoes(escuta));

            assertEquals(InicioLocal.EstadoPorta.CHAT_SAKURA, InicioLocal.sondar(escuta.getLocalPort(), 2_000));

            // Por um endereço da rede (não loopback), a conexão é recusada.
            Optional<InetAddress> externo = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(i -> {
                        try {
                            return i.isUp() && !i.isLoopback();
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
                    .filter(a -> a instanceof java.net.Inet4Address && !a.isLoopbackAddress())
                    .findFirst();
            if (externo.isPresent()) {
                assertThrows(IOException.class, () -> {
                    try (Socket s = new Socket()) {
                        s.connect(new java.net.InetSocketAddress(externo.get(), escuta.getLocalPort()), 1_000);
                    }
                });
            }
        }
        assertTrue(Files.exists(pasta.resolve("dados").resolve("chat-sakura.db")), "Banco criado na pasta dados");
    }

    @Test
    void portaOcupadaGeraMensagemCompreensivel() throws Exception {
        try (ServerSocket ocupada = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Servidor servidor = InicioLocal.criarServidor(pasta.resolve("dados"), ocupada.getLocalPort(),
                    new ProvedorIndisponivel(null));
            assertThrows(BindException.class, () -> servidor.abrirSocket().close());

            PrintStream original = System.err;
            ByteArrayOutputStream capturado = new ByteArrayOutputStream();
            System.setErr(new PrintStream(capturado, true, StandardCharsets.UTF_8));
            try {
                servidor.iniciar();   // retorna: não conseguiu abrir a porta
            } finally {
                System.setErr(original);
            }
            String erro = capturado.toString(StandardCharsets.UTF_8);
            assertTrue(erro.contains("a porta " + ocupada.getLocalPort() + " já está em uso"), erro);
        }
    }

    @Test
    void saidaDuplicadaMantemAcentosNoConsoleENoArquivo() throws Exception {
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        ByteArrayOutputStream arquivo = new ByteArrayOutputStream();
        PrintStream saida = new PrintStream(new SaidaDuplicada(
                new PrintStream(console, true, StandardCharsets.UTF_8), arquivo), true, StandardCharsets.UTF_8);

        saida.println("Conexão encerrada: ação ç ã 🌸");
        // Um caractere de vários bytes dividido entre duas escritas.
        byte[] flor = "ã".getBytes(StandardCharsets.UTF_8);
        saida.write(flor, 0, 1);
        saida.write(flor, 1, 1);
        saida.flush();

        String esperado = "Conexão encerrada: ação ç ã 🌸" + System.lineSeparator() + "ã";
        assertEquals(esperado, console.toString(StandardCharsets.UTF_8));
        assertEquals(esperado, arquivo.toString(StandardCharsets.UTF_8));
    }
}
