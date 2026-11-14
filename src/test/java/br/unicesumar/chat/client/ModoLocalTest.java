package br.unicesumar.chat.client;

import br.unicesumar.chat.ia.ProvedorIndisponivel;
import br.unicesumar.chat.local.InicioLocal;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Modo local do inicializador: destino fixo 127.0.0.1:12345 sem TLS, sem
 * certificados nem senhas técnicas, e o TLS preservado fora dele. Fluxo
 * completo com duas contas sobre o servidor do inicializador (banco temporário).
 */
class ModoLocalTest {

    private static final String SENHA = "senha-segura-1";

    @TempDir
    Path pasta;

    private final ExecutorService edt = Executors.newSingleThreadExecutor();
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<ControladorConversas> controladores = new CopyOnWriteArrayList<>();
    private ServerSocket escuta;

    @AfterEach
    void encerrar() throws Exception {
        for (ControladorConversas c : controladores) {
            edt.submit(c::encerrar).get(10, TimeUnit.SECONDS);
        }
        edt.shutdown();
        assertTrue(edt.awaitTermination(5, TimeUnit.SECONDS));
        if (escuta != null) {
            escuta.close();
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        // O Servidor real atende cada conexão numa thread própria: espera todas
        // terminarem (e soltarem o banco) antes de a pasta temporária ser apagada.
        for (Thread atendimento : Thread.getAllStackTraces().keySet()) {
            if (atendimento.getName().startsWith("chat-cliente-")) {
                atendimento.join(10_000);
                assertFalse(atendimento.isAlive(), "Atendimento ainda ativo: " + atendimento.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // Destino da conexão
    // ------------------------------------------------------------------

    @Test
    void modoLocalUsaSempreOProprioComputadorSemTls() {
        // Mesmo com outro texto nos campos (que ficam bloqueados), o destino não muda.
        DialogoEntrada.Escolha escolha = DialogoEntrada.destino("192.168.0.10", "9999", false, true, null);
        assertEquals("127.0.0.1", escolha.endereco());
        assertEquals(12345, escolha.porta());
        assertFalse(escolha.seguro());
        assertFalse(TransporteCliente.de(escolha.seguro()).seguro(), "Transporte TCP local");
    }

    @Test
    void foraDoModoLocalOTlsContinuaSendoOPadraoSemRebaixamento() {
        DialogoEntrada.Escolha padrao = DialogoEntrada.destino("localhost", "12345", false, false, null);
        assertTrue(padrao.seguro(), "Sem marcar \"Sem TLS\", a conexão é TLS");
        assertTrue(TransporteCliente.de(padrao.seguro()).seguro());

        assertFalse(DialogoEntrada.destino("localhost", "12345", true, false, null).seguro(),
                "Sem TLS só quando marcado explicitamente");
        assertThrows(IllegalArgumentException.class, () -> DialogoEntrada.destino(" ", "12345", false, false, null));
        assertThrows(IllegalArgumentException.class, () -> DialogoEntrada.destino("localhost", "0", false, false, null));

        // Reconexão de uma sessão TLS nunca vira local (sem TLS).
        assertFalse(DialogoEntrada.localNaReconexao(true, true));
        assertTrue(DialogoEntrada.localNaReconexao(true, false));
        assertFalse(DialogoEntrada.localNaReconexao(false, false));
    }

    @Test
    void janelaComumNaoEntraNoModoLocal() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        PreferenciasSakura preferencias = new PreferenciasSakura(pasta.resolve("prefs").resolve("p.properties"));
        ClienteChat[] janelas = new ClienteChat[2];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            janelas[0] = new ClienteChat(preferencias);
            janelas[1] = new ClienteChat(preferencias, true);
        });
        try {
            assertFalse(janelas[0].conexaoLocal());
            assertEquals("Chat Sakura", janelas[0].getTitle());
            assertTrue(janelas[1].conexaoLocal());
            assertTrue(janelas[1].getTitle().contains("modo local"));
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                janelas[0].dispose();
                janelas[1].dispose();
            });
        }
    }

    /** Depois de "Criar conta", a janela diz claramente que a pessoa já está conectada. */
    @Test
    void janelaAvisaQueACadastradaJaEstaConectada() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Servidor servidor = InicioLocal.criarServidor(pasta.resolve("dados"), 0, new ProvedorIndisponivel(null));
        escuta = servidor.abrirSocket();
        threads.execute(() -> servidor.atenderConexoes(escuta));
        PreferenciasSakura preferencias = new PreferenciasSakura(pasta.resolve("prefs").resolve("p.properties"));
        ClienteChat[] janela = new ClienteChat[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> janela[0] = new ClienteChat(preferencias, true));
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> janela[0].conectar(new DialogoEntrada.Escolha(
                    "127.0.0.1", escuta.getLocalPort(),
                    new ConexaoServidor.Cadastrar("zesk", "Zesk", SENHA.toCharArray()), false)));
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            String[] aviso = {""};
            while (!aviso[0].contains("Conta criada") && System.nanoTime() < limite) {
                javax.swing.SwingUtilities.invokeAndWait(() -> aviso[0] = janela[0].aviso().mensagem());
                Thread.onSpinWait();
            }
            assertTrue(aviso[0].contains("Conta criada! Você já está conectado como Zesk"), aviso[0]);
            ControladorConversas.EstadoConexao[] estado = new ControladorConversas.EstadoConexao[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> estado[0] = janela[0].controlador().estado());
            assertEquals(ControladorConversas.EstadoConexao.CONECTADO, estado[0]);
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(janela[0]::dispose);
            javax.swing.SwingUtilities.invokeAndWait(
                    () -> GerenciadorTema.definir(br.unicesumar.chat.model.Aparencia.PADRAO));
        }
    }

    /** Servidor fechado: a entrada falha com um aviso em diálogo (não só uma linha no chat). */
    @Test
    void servidorFechadoMostraErroComOrientacao() throws Exception {
        int portaLivre;
        try (ServerSocket temporario = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            portaLivre = temporario.getLocalPort();
        }
        Cliente ana = new Cliente("ana");
        DialogoEntrada.Escolha local = DialogoEntrada.destino(null, null, false, true,
                new ConexaoServidor.Entrar("ana", SENHA.toCharArray()));
        naEdt(() -> ana.controlador.conectar(local.endereco(), portaLivre, local.pedido(),
                TransporteCliente.de(local.seguro())));
        String recusa = ana.esperar("recusada ");
        assertTrue(recusa.contains("Não foi possível conectar ao servidor 127.0.0.1:" + portaLivre), recusa);
        assertTrue(recusa.contains("servidor está em execução"), recusa);
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, ana.estado());
    }

    // ------------------------------------------------------------------
    // Fluxo completo no servidor do inicializador
    // ------------------------------------------------------------------

    private final class Cliente {
        final String nome;
        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
        final List<String> vistos = new CopyOnWriteArrayList<>();
        final ControladorConversas controlador;

        Cliente(String nome) throws Exception {
            this.nome = nome;
            ControladorConversas.Visao visao = new ControladorConversas.Visao() {
                @Override public void reiniciarConversa(long id, String usuario) { }
                @Override public void exibirLinha(long id, String linha) { eventos.add("linha " + linha); }
                @Override public boolean historicoConcluido(long id) { return true; }
                @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { eventos.add("estado " + e); }
                @Override public void mensagemEnviada() { }
                @Override public void usuarioIdentificado(String apelido) { eventos.add("identificado " + apelido); }
                @Override public void autenticacaoRecusada(String mensagem) { eventos.add("recusada " + mensagem); }
                @Override public void erroConversa(String mensagem) { eventos.add("erro " + mensagem); }
                @Override public void exibirConversa(ControladorConversas.Conversa c) { eventos.add("conversa " + c.titulo()); }
                @Override public void exibirMensagem(long id, String linha, Long mensagemId) {
                    eventos.add("mensagem " + id + " " + linha);
                }
            };
            controlador = edt.submit(() -> new ControladorConversas(visao,
                    new NotificacoesSonoras(new ReprodutorSom() {
                        @Override public void tocar(SomSakura som) { }
                        @Override public void fechar() { }
                    }, System::currentTimeMillis),
                    acao -> {
                        try {
                            edt.execute(acao);
                        } catch (RejectedExecutionException e) {
                            // Teste já encerrado.
                        }
                    }, ConexaoServidor::new)).get(10, TimeUnit.SECONDS);
            controladores.add(controlador);
        }

        String esperar(String prefixo) throws InterruptedException {
            while (true) {
                String evento = eventos.poll(15, TimeUnit.SECONDS);
                assertNotNull(evento, nome + ": evento não chegou: " + prefixo + ". Vistos: " + vistos);
                vistos.add(evento);
                if (evento.startsWith(prefixo)) {
                    return evento;
                }
            }
        }

        /** Conecta como o diálogo do modo local faria (porta do servidor de teste). */
        void conectar(ConexaoServidor.PedidoEntrada pedido) throws Exception {
            DialogoEntrada.Escolha local = DialogoEntrada.destino(null, null, false, true, pedido);
            assertTrue(naEdt(() -> controlador.conectar(local.endereco(), escuta.getLocalPort(), local.pedido(),
                    TransporteCliente.de(local.seguro()))));
        }

        ControladorConversas.EstadoConexao estado() throws Exception {
            return naEdt(controlador::estado);
        }
    }

    private <T> T naEdt(Callable<T> acao) throws Exception {
        return edt.submit(acao).get(10, TimeUnit.SECONDS);
    }

    @Test
    void duasContasConversamSaemEEntramDeNovoSemCertificados() throws Exception {
        Servidor servidor = InicioLocal.criarServidor(pasta.resolve("dados"), 0, new ProvedorIndisponivel(null));
        escuta = servidor.abrirSocket();
        threads.execute(() -> servidor.atenderConexoes(escuta));

        Cliente ana = new Cliente("ana");
        Cliente bia = new Cliente("bia");

        // 1. Duas contas novas: cada cadastro já entra conectado.
        ana.conectar(new ConexaoServidor.Cadastrar("ana", "Ana", SENHA.toCharArray()));
        ana.esperar("identificado Ana");
        ana.esperar("linha SISTEMA: Conectado como Ana");
        bia.conectar(new ConexaoServidor.Cadastrar("bia", "Bia", SENHA.toCharArray()));
        bia.esperar("identificado Bia");
        bia.esperar("linha SISTEMA: Conectado como Bia");
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, ana.estado());
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, bia.estado());

        // 2. Ana abre uma conversa privada com Bia e envia; Bia recebe.
        long biaId = naEdt(() -> {
            java.util.concurrent.CompletableFuture<Long> id = new java.util.concurrent.CompletableFuture<>();
            ana.controlador.solicitarUsuarios(usuarios -> id.complete(usuarios.stream()
                    .filter(u -> u.apelido().equals("Bia")).findFirst().orElseThrow().id()));
            return id;
        }).get(10, TimeUnit.SECONDS);
        assertTrue(naEdt(() -> ana.controlador.abrirPrivada(biaId)));
        ana.esperar("conversa Bia");
        long privada = naEdt(() -> ana.controlador.conversaAtiva().id());
        assertTrue(naEdt(() -> ana.controlador.enviar("Oi, Bia! Tudo bem?")));
        assertTrue(ana.esperar("mensagem " + privada + " ").contains("Oi, Bia! Tudo bem?"));
        // Para Bia, a conversa nova aparece; ao abri-la, a mensagem está no histórico.
        naEdt(() -> {
            bia.controlador.selecionar(privada);
            return null;
        });
        bia.esperar("conversa Ana");
        assertTrue(naEdt(() -> bia.controlador.conversaAtiva().texto()).contains("Oi, Bia! Tudo bem?"));

        // 3. Bia responde; Ana recebe na conversa aberta.
        assertTrue(naEdt(() -> bia.controlador.enviar("Tudo ótimo!")));
        assertTrue(ana.esperar("mensagem " + privada + " ").contains("Tudo ótimo!"));

        // 4. Ana sai e entra de novo com a senha: a sessão volta e continua ativa.
        naEdt(() -> {
            ana.controlador.desconectar();
            return null;
        });
        ana.esperar("estado DESCONECTADO");
        ana.conectar(new ConexaoServidor.Entrar("ana", SENHA.toCharArray()));
        ana.esperar("identificado Ana");
        ana.esperar("linha SISTEMA: Conectado como Ana");
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, ana.estado());
        assertTrue(naEdt(ana.controlador::modoContas));
        assertTrue(naEdt(() -> ana.controlador.enviar("Voltei!")));
        assertTrue(ana.esperar("mensagem " + ControladorConversas.ID_GERAL + " ").contains("Voltei!"));
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, ana.estado());

        // 5. Senha errada continua sendo recusada (autenticação preservada).
        Cliente intrusa = new Cliente("intrusa");
        intrusa.conectar(new ConexaoServidor.Entrar("bia", "senha-errada-9".toCharArray()));
        intrusa.esperar("recusada ");
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, intrusa.estado());

        assertFalse(ana.vistos.stream().anyMatch(v -> v.startsWith("recusada") || v.startsWith("erro")), ana.vistos.toString());
        assertFalse(bia.vistos.stream().anyMatch(v -> v.startsWith("recusada") || v.startsWith("erro")), bia.vistos.toString());
    }
}
