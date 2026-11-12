package br.unicesumar.chat.client;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.server.LimitesServidor;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.tls.CertificadosTeste;
import br.unicesumar.chat.tls.ContextoTls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Cliente TLS: validação do certificado, ausência de rebaixamento e reconexão segura. */
class ClienteTlsTest {

    private static final String SENHA = "senha-segura-1";

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private ServerSocket servidor;
    private int porta;
    private ExecutorService threads;
    private final List<Socket> abertos = new CopyOnWriteArrayList<>();
    private final AtomicInteger aceitas = new AtomicInteger();
    private final ExecutorService edt = Executors.newSingleThreadExecutor();
    private ControladorConversas controlador;

    private TransporteCliente confiando() {
        return TransporteCliente.tls(CertificadosTeste.arquivo("confianca.p12"), CertificadosTeste.SENHA.toCharArray());
    }

    /** Servidor real (serviços completos) com o keystore indicado; {@code null} = TCP sem TLS. */
    private void iniciar(String keystore) throws Exception {
        if (servicos == null) {
            servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("cliente-tls.db").toString()),
                    new HashSenha(1_000), Clock.systemDefaultZone(), new ProvedorIAFalso(), LimitesServidor.padrao());
        }
        if (keystore == null) {
            servidor = new ServerSocket();
        } else {
            SSLContext contexto = CertificadosTeste.servidor(keystore);
            servidor = contexto.getServerSocketFactory().createServerSocket();
            ContextoTls.configurar((SSLServerSocket) servidor, contexto);
        }
        servidor.setReuseAddress(true);   // somente no teste: reabrir a mesma porta
        servidor.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), 50);
        porta = servidor.getLocalPort();
        threads = Executors.newCachedThreadPool();
        ServerSocket atual = servidor;
        threads.execute(() -> {
            while (!atual.isClosed()) {
                try {
                    Socket s = atual.accept();
                    aceitas.incrementAndGet();
                    abertos.add(s);
                    threads.execute(servicos.atender(s));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    private void parar() throws Exception {
        servidor.close();
        for (Socket s : abertos) {
            s.close();
        }
        abertos.clear();
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
    }

    @AfterEach
    void encerrar() throws Exception {
        if (controlador != null) {
            edt.submit(() -> controlador.encerrar()).get(10, TimeUnit.SECONDS);
        }
        if (servidor != null && !servidor.isClosed()) {
            parar();
        }
        edt.shutdown();
        assertTrue(edt.awaitTermination(5, TimeUnit.SECONDS));
    }

    private String falhaAoAbrir(TransporteCliente transporte, String endereco) {
        TransporteCliente.FalhaConexaoSegura falha = assertThrows(TransporteCliente.FalhaConexaoSegura.class,
                () -> transporte.abrir(new Socket(), endereco, porta, 5_000).close());
        return falha.getMessage();
    }

    @Test
    void certificadoValidoPorNomeEIpEProtocoloModerno() throws Exception {
        iniciar("servidor.p12");
        for (String endereco : new String[] {"localhost", "127.0.0.1"}) {
            try (Socket socket = confiando().abrir(new Socket(), endereco, porta, 5_000)) {
                SSLSocket tls = assertInstanceOf(SSLSocket.class, socket);
                assertTrue(Set.of("TLSv1.3", "TLSv1.2").contains(tls.getSession().getProtocol()));
                assertEquals("HTTPS", tls.getSSLParameters().getEndpointIdentificationAlgorithm(),
                        "Verificação do nome sempre ligada");
            }
        }
    }

    @Test
    void certificadoNaoConfiavelEhRecusado() throws Exception {
        iniciar("estranho.p12");
        assertTrue(falhaAoAbrir(confiando(), "localhost").contains("não é confiável"));
        // Sem truststore configurado, vale o do JDK: um certificado próprio também é recusado.
        assertTrue(falhaAoAbrir(TransporteCliente.tls(null, null), "localhost").contains("não é confiável"));
    }

    @Test
    void certificadoExpiradoEhRecusado() throws Exception {
        iniciar("expirado.p12");
        assertTrue(falhaAoAbrir(confiando(), "localhost").contains("expirou"));
    }

    @Test
    void nomeDoServidorIncorretoEhRecusado() throws Exception {
        iniciar("nome-errado.p12");
        String mensagem = falhaAoAbrir(confiando(), "localhost");
        assertTrue(mensagem.contains("não corresponde ao endereço \"localhost\""), mensagem);
    }

    @Test
    void credenciaisNaoSaemQuandoOCertificadoFalha() throws Exception {
        iniciar("estranho.p12");
        AtomicReference<String> falha = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        ConexaoServidor conexao = new ConexaoServidor(new Ouvinte() {
            @Override public void falhaConexaoSegura(String mensagem) { falha.set(mensagem); }
            @Override public void sessaoEncerrada() { fim.countDown(); }
        });

        assertTrue(conexao.iniciar("localhost", porta,
                new ConexaoServidor.Cadastrar("ana", "Ana", SENHA.toCharArray()), confiando()));
        assertTrue(fim.await(10, TimeUnit.SECONDS));

        assertTrue(falha.get().contains("não é confiável"), falha.get());
        assertFalse(servicos.autenticacao().nomeReservado("ana"), "O cadastro nunca chegou ao servidor");
        assertEquals(1, aceitas.get(), "Uma única tentativa (TLS), sem nova tentativa em TCP");
    }

    @Test
    void clienteTlsNaoRebaixaParaTcpComServidorSemTls() throws Exception {
        iniciar(null);   // servidor em TCP puro
        AtomicReference<String> falha = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        ConexaoServidor conexao = new ConexaoServidor(new Ouvinte() {
            @Override public void falhaConexaoSegura(String mensagem) { falha.set(mensagem); }
            @Override public void sessaoEncerrada() { fim.countDown(); }
        });

        conexao.iniciar("localhost", porta, new ConexaoServidor.Entrar("ana", SENHA.toCharArray()), confiando());
        assertTrue(fim.await(10, TimeUnit.SECONDS));

        assertTrue(falha.get().contains("não respondeu com TLS"), falha.get());
        assertEquals(1, aceitas.get(), "Sem segunda tentativa em texto claro");
        assertEquals(0, servicos.chat().contarClientes(), "Nada de login nem de visitante no servidor");
    }

    // ------------------------------------------------------------------
    // Reconexão
    // ------------------------------------------------------------------

    private final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();

    private <T> T naEdt(Callable<T> acao) throws Exception {
        return edt.submit(acao).get(10, TimeUnit.SECONDS);
    }

    private final List<String> vistos = new CopyOnWriteArrayList<>();

    private String esperar(String prefixo) throws InterruptedException {
        while (true) {
            String evento = eventos.poll(15, TimeUnit.SECONDS);
            assertNotNull(evento, "Evento não chegou: " + prefixo + ". Vistos: " + vistos);
            vistos.add(evento);
            if (evento.startsWith(prefixo)) {
                return evento;
            }
        }
    }

    @Test
    void reconexaoUsaTlsDeNovoRevalidaOCertificadoENaoAceitaRebaixamento() throws Exception {
        iniciar("servidor.p12");
        servicos.autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray());

        ControladorConversas.Visao visao = new ControladorConversas.Visao() {
            @Override public void reiniciarConversa(long id, String usuario) { }
            @Override public void exibirLinha(long id, String linha) { eventos.add("linha " + linha); }
            @Override public boolean historicoConcluido(long id) { return true; }
            @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { eventos.add("estado " + e); }
            @Override public void mensagemEnviada() { }
            @Override public void usuarioIdentificado(String apelido) { eventos.add("identificado " + apelido); }
            @Override public void autenticacaoRecusada(String mensagem) { eventos.add("recusada " + mensagem); }
            @Override public void reconexaoEmAndamento(int n, long ms) { eventos.add("reconectando " + n); }
            @Override public void reconexaoDisponivel(String e, int p, String login) { eventos.add("disponivel " + login); }
        };
        controlador = naEdt(() -> new ControladorConversas(visao,
                new NotificacoesSonoras(new ReprodutorSom() {
                    @Override public void tocar(SomSakura som) { }
                    @Override public void fechar() { }
                }, System::currentTimeMillis),
                edt::execute, ConexaoServidor::new,
                o -> new ReconexaoAutomatica(o, ReconexaoAutomatica.SONDA_TCP, 50, 200)));

        naEdt(() -> controlador.conectar("localhost", porta,
                new ConexaoServidor.Entrar("ana", SENHA.toCharArray()), confiando()));
        esperar("identificado Ana");
        esperar("linha SISTEMA: Conectado como");   // sessão confirmada (lista de conversas recebida)
        assertTrue(naEdt(controlador::ultimaConexaoSegura));

        // Queda; o servidor volta com um certificado não confiável.
        parar();
        esperar("reconectando 0");
        iniciar("estranho.p12");
        esperar("disponivel ana");

        // Rebaixar para TCP na reconexão é recusado.
        assertFalse(naEdt(() -> controlador.conectar("localhost", porta,
                new ConexaoServidor.Entrar("ana", SENHA.toCharArray()), TransporteCliente.TCP_DESENVOLVIMENTO)));
        assertTrue(esperar("recusada ").contains("precisa usar TLS"));

        // A nova conexão TLS valida o certificado de novo: este é recusado.
        naEdt(() -> controlador.conectar("localhost", porta,
                new ConexaoServidor.Entrar("ana", SENHA.toCharArray()), confiando()));
        assertTrue(esperar("recusada ").contains("não é confiável"));

        // Com o certificado correto, a conexão TLS volta normalmente.
        parar();
        iniciar("servidor.p12");
        naEdt(() -> controlador.conectar("localhost", porta,
                new ConexaoServidor.Entrar("ana", SENHA.toCharArray()), confiando()));
        esperar("identificado Ana");
        esperar("linha SISTEMA: Conectado como");
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, naEdt(controlador::estado));
    }

    /** Ouvinte mínimo para os testes de conexão. */
    private abstract static class Ouvinte implements ConexaoServidor.Ouvinte {
        @Override public void linhaRecebida(String linha) { }
        @Override public void conexaoConfirmada() { }
        @Override public void mensagemEnviada() { }
        @Override public void estadoAlterado() { }
    }
}
