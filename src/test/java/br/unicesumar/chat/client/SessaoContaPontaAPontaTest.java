package br.unicesumar.chat.client;

import br.unicesumar.chat.ia.ProvedorIndisponivel;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.server.LimitesServidor;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.server.TransporteServidor;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.tls.CertificadosTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Cenário relatado em outro computador: banco novo, IA não configurada,
 * cadastro, saída, novo login, eventos iniciais, conversa privada e envio.
 * Servidor e cliente reais, em TCP de desenvolvimento e em TLS.
 */
class SessaoContaPontaAPontaTest {

    private static final String SENHA = "senha-segura-1";

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private ServerSocket servidor;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> abertos = new CopyOnWriteArrayList<>();
    private final ExecutorService edt = Executors.newSingleThreadExecutor();
    private ControladorConversas controlador;

    private final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
    private final List<String> vistos = new CopyOnWriteArrayList<>();

    /** Servidor como o do usuário: banco vazio e Qwen sem nenhuma variável definida. */
    private void iniciar(TransporteServidor transporte) throws IOException {
        servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("novo.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), ProvedorIndisponivel.doAmbiente(Map.of()),
                LimitesServidor.padrao());
        servidor = transporte.abrir(0);
        threads.execute(() -> {
            while (!servidor.isClosed()) {
                try {
                    Socket s = servidor.accept();
                    abertos.add(s);
                    threads.execute(servicos.atender(s));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    @AfterEach
    void encerrar() throws Exception {
        if (controlador != null) {
            edt.submit(() -> controlador.encerrar()).get(10, TimeUnit.SECONDS);
        }
        edt.shutdown();
        assertTrue(edt.awaitTermination(5, TimeUnit.SECONDS));
        if (servidor != null) {
            servidor.close();
        }
        for (Socket s : abertos) {
            s.close();
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
    }

    private <T> T naEdt(Callable<T> acao) throws Exception {
        return edt.submit(acao).get(10, TimeUnit.SECONDS);
    }

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

    private void criarControlador() throws Exception {
        ControladorConversas.Visao visao = new ControladorConversas.Visao() {
            @Override public void reiniciarConversa(long id, String usuario) { }
            @Override public void exibirLinha(long id, String linha) { eventos.add("linha " + id + " " + linha); }
            @Override public boolean historicoConcluido(long id) { return true; }
            @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { eventos.add("estado " + e); }
            @Override public void mensagemEnviada() { eventos.add("enviada"); }
            @Override public void usuarioIdentificado(String apelido) { eventos.add("identificado " + apelido); }
            @Override public void autenticacaoRecusada(String mensagem) { eventos.add("recusada " + mensagem); }
            @Override public void erroConversa(String mensagem) { eventos.add("erro " + mensagem); }
            @Override public void exibirConversa(ControladorConversas.Conversa c) { eventos.add("conversa " + c.titulo()); }
            @Override public void aparenciaRecebida(br.unicesumar.chat.model.Aparencia a) { eventos.add("aparencia"); }
            @Override public void exibirMensagem(long id, String linha, Long mensagemId) {
                eventos.add("mensagem " + id + " " + linha);
            }
        };
        controlador = naEdt(() -> new ControladorConversas(visao,
                new NotificacoesSonoras(new ReprodutorSom() {
                    @Override public void tocar(SomSakura som) { }
                    @Override public void fechar() { }
                }, System::currentTimeMillis),
                this::naEdtSeAtiva, ConexaoServidor::new));
    }

    /** Eventos tardios da thread de rede depois do fim do teste são descartados. */
    private void naEdtSeAtiva(Runnable acao) {
        try {
            edt.execute(acao);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Teste já encerrado.
        }
    }

    /** Cadastro, saída, login, eventos iniciais, privada e envio; a sessão continua ativa. */
    private void percorrerCenario(TransporteCliente transporte) throws Exception {
        long amigaId = servicos.autenticacao().cadastrar("amiga", "Amiga", SENHA.toCharArray()).getId();
        criarControlador();
        String endereco = "localhost";
        int porta = servidor.getLocalPort();

        // 1. Cadastro (já entra na conta).
        assertTrue(naEdt(() -> controlador.conectar(endereco, porta,
                new ConexaoServidor.Cadastrar("zesk", "zesk", SENHA.toCharArray()), transporte)));
        esperar("identificado zesk");
        esperar("linha " + ControladorConversas.ID_GERAL + " SISTEMA: Conectado como zesk");
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, naEdt(controlador::estado));

        // 2. Sai da primeira conexão.
        naEdt(() -> {
            controlador.desconectar();
            return null;
        });
        esperar("estado DESCONECTADO");
        aguardarClientesNoServidor(0);

        // 3. Login com a conta criada.
        assertTrue(naEdt(() -> controlador.conectar(endereco, porta,
                new ConexaoServidor.Entrar("zesk", SENHA.toCharArray()), transporte)));
        esperar("identificado zesk");
        esperar("linha " + ControladorConversas.ID_GERAL + " SISTEMA: Conectado como zesk");
        esperar("aparencia");   // último evento do estado inicial
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, naEdt(controlador::estado));
        assertTrue(naEdt(controlador::modoContas));
        assertEquals(1, servicos.chat().contarClientes());

        // 4. Abre uma conversa privada e envia uma mensagem.
        assertTrue(naEdt(() -> controlador.abrirPrivada(amigaId)));
        esperar("conversa Amiga");
        long privada = naEdt(() -> controlador.conversaAtiva().id());
        assertNotEquals(ControladorConversas.ID_GERAL, privada);
        assertTrue(naEdt(() -> controlador.enviar("oi, Amiga")));
        assertTrue(esperar("mensagem " + privada + " ").contains("oi, Amiga"));

        // 5. A sessão continua autenticada e conectada.
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, naEdt(controlador::estado));
        assertTrue(naEdt(controlador::modoContas));
        assertEquals(1, servicos.chat().contarClientes());
        assertFalse(vistos.stream().anyMatch(e -> e.startsWith("recusada") || e.startsWith("erro")),
                "Nenhuma recusa ou erro: " + vistos);
    }

    @Test
    void cadastroSaidaLoginEEnvioEmTcpDeDesenvolvimento() throws Exception {
        iniciar(TransporteServidor.tcpDesenvolvimento());
        percorrerCenario(TransporteCliente.TCP_DESENVOLVIMENTO);
    }

    @Test
    void cadastroSaidaLoginEEnvioEmTls() throws Exception {
        iniciar(TransporteServidor.tls(CertificadosTeste.servidor("servidor.p12"), InetAddress.getLoopbackAddress()));
        percorrerCenario(TransporteCliente.tls(CertificadosTeste.arquivo("confianca.p12"),
                CertificadosTeste.SENHA.toCharArray()));
    }

    // ------------------------------------------------------------------
    // Mensagens quando a sessão termina por um motivo local
    // ------------------------------------------------------------------

    /** Ouvinte que registra as linhas exibidas e o fim da sessão. */
    private class OuvinteRegistro implements ConexaoServidor.Ouvinte {
        @Override public void linhaRecebida(String linha) { eventos.add("linha " + linha); }
        @Override public void conexaoConfirmada() { eventos.add("confirmada"); }
        @Override public void mensagemEnviada() { }
        @Override public void estadoAlterado() { }
        @Override public void sessaoEncerrada() { eventos.add("encerrada"); }
    }

    /**
     * Uma falha inesperada na thread de rede (por exemplo, uma classe que não
     * pode mais ser carregada) encerra a sessão, mas nunca em silêncio: o
     * usuário vê o motivo na conversa, sem senha nem dados da conta.
     */
    @Test
    void falhaInesperadaNoClienteEncerraASessaoComAvisoAoUsuario() throws Exception {
        iniciar(TransporteServidor.tcpDesenvolvimento());
        servicos.autenticacao().cadastrar("zesk", "zesk", SENHA.toCharArray());
        ConexaoServidor conexao = new ConexaoServidor(new OuvinteRegistro() {
            @Override
            public void eventoRecebido(br.unicesumar.chat.protocolo.ProtocoloJson.Evento evento) {
                throw new IllegalStateException("falha simulada ao tratar " + evento.tipo());
            }
        });

        assertTrue(conexao.iniciar("localhost", servidor.getLocalPort(),
                new ConexaoServidor.Entrar("zesk", SENHA.toCharArray())));
        esperar("confirmada");
        String aviso = esperar("linha SISTEMA: Falha inesperada no cliente");
        assertTrue(aviso.contains("IllegalStateException"), aviso);
        assertFalse(aviso.contains(SENHA), "A senha nunca aparece no aviso");
        esperar("encerrada");
        assertFalse(conexao.ocupada());
    }

    /** Servidor fora do ar (ou noutro endereço/porta): mensagem compreensível, não o texto do Java. */
    @Test
    void servidorInacessivelMostraOrientacaoCompreensivel() throws Exception {
        int portaLivre;
        try (ServerSocket temporario = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaLivre = temporario.getLocalPort();
        }
        ConexaoServidor conexao = new ConexaoServidor(new OuvinteRegistro());

        assertTrue(conexao.iniciar("localhost", portaLivre, new ConexaoServidor.Entrar("zesk", SENHA.toCharArray())));
        String aviso = esperar("linha SISTEMA: ");
        assertTrue(aviso.contains("Não foi possível conectar ao servidor localhost:" + portaLivre), aviso);
        assertTrue(aviso.contains("servidor está em execução"), aviso);
        esperar("encerrada");
    }

    /** Mesmo cenário na janela real (não exibida), com preferências temporárias. */
    @Test
    void janelaRealPermaneceConectadaAposCadastroSaidaELogin() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        iniciar(TransporteServidor.tcpDesenvolvimento());
        int porta = servidor.getLocalPort();
        PreferenciasSakura preferencias = new PreferenciasSakura(pasta.resolve("prefs").resolve("p.properties"));
        ClienteChat[] janela = new ClienteChat[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> janela[0] = new ClienteChat(preferencias));
        try {
            ControladorConversas c = janela[0].controlador();
            javax.swing.SwingUtilities.invokeAndWait(() -> c.conectar("localhost", porta,
                    new ConexaoServidor.Cadastrar("zesk", "zesk", SENHA.toCharArray())));
            aguardarEstado(c, ControladorConversas.EstadoConexao.CONECTADO);
            javax.swing.SwingUtilities.invokeAndWait(c::desconectar);
            aguardarEstado(c, ControladorConversas.EstadoConexao.DESCONECTADO);
            aguardarClientesNoServidor(0);

            javax.swing.SwingUtilities.invokeAndWait(() -> c.conectar("localhost", porta,
                    new ConexaoServidor.Entrar("zesk", SENHA.toCharArray())));
            aguardarEstado(c, ControladorConversas.EstadoConexao.CONECTADO);
            // Estado inicial completo (aparência da conta aplicada) e sessão ainda ativa.
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!naEdtSwing(janela[0]::aparenciaDaConta) && System.nanoTime() < limite) {
                Thread.onSpinWait();
            }
            assertTrue(naEdtSwing(janela[0]::aparenciaDaConta));
            assertTrue(naEdtSwing(() -> c.enviar("olá, Geral")));
            assertTrue(naEdtSwing(c::modoContas));
            assertEquals(ControladorConversas.EstadoConexao.CONECTADO, naEdtSwing(c::estado));
            assertEquals(1, servicos.chat().contarClientes());
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(janela[0]::dispose);
            javax.swing.SwingUtilities.invokeAndWait(
                    () -> GerenciadorTema.definir(br.unicesumar.chat.model.Aparencia.PADRAO));
        }
    }

    /** O servidor percebe a saída do cliente de forma assíncrona. */
    private void aguardarClientesNoServidor(int esperado) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (servicos.chat().contarClientes() != esperado && System.nanoTime() < limite) {
            Thread.onSpinWait();
        }
        assertEquals(esperado, servicos.chat().contarClientes());
    }

    private static <T> T naEdtSwing(java.util.function.Supplier<T> acao) throws Exception {
        Object[] valor = new Object[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> valor[0] = acao.get());
        @SuppressWarnings("unchecked")
        T t = (T) valor[0];
        return t;
    }

    private static void aguardarEstado(ControladorConversas c, ControladorConversas.EstadoConexao esperado)
            throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (naEdtSwing(c::estado) != esperado && System.nanoTime() < limite) {
            Thread.onSpinWait();
        }
        assertEquals(esperado, naEdtSwing(c::estado));
    }
}
