package br.unicesumar.chat.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Cadastro e login pelo cliente, contra o servidor real com contas em SQLite temporário. */
class ConexaoContasTest {

    private static final long ESPERA_S = 5;

    @TempDir
    Path pasta;

    private ServidorTeste servidor;
    private final List<ConexaoServidor> conexoes = new ArrayList<>();

    /** Gravador da Etapa 0 com os eventos de autenticação. */
    private static final class OuvinteContas extends ConexaoServidorTest.OuvinteGravador {

        final LinkedBlockingQueue<String> identidades = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String> recusas = new LinkedBlockingQueue<>();

        @Override
        public void identidadeConfirmada(String apelido) {
            identidades.add(apelido);
        }

        @Override
        public void autenticacaoRecusada(String mensagem) {
            recusas.add(mensagem);
        }

        final LinkedBlockingQueue<br.unicesumar.chat.protocolo.ProtocoloJson.Evento> eventos =
                new LinkedBlockingQueue<>();

        @Override
        public void eventoRecebido(br.unicesumar.chat.protocolo.ProtocoloJson.Evento evento) {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                eventoNaEdt.set(true);
            }
            eventos.add(evento);
        }

        /** Consome eventos até o tipo indicado. */
        br.unicesumar.chat.protocolo.ProtocoloJson.Evento esperarEvento(
                br.unicesumar.chat.protocolo.ProtocoloJson.TipoEvento tipo) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
            while (System.nanoTime() < limite) {
                var evento = eventos.poll(100, TimeUnit.MILLISECONDS);
                if (evento != null && evento.tipo() == tipo) {
                    return evento;
                }
            }
            throw new AssertionError("Evento não recebido: " + tipo);
        }
    }

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorTeste(pasta.resolve("contas.db"));
    }

    @AfterEach
    void encerrar() throws Exception {
        conexoes.forEach(ConexaoServidor::desconectar);
        servidor.close();
    }

    private ConexaoServidor iniciar(OuvinteContas ouvinte, ConexaoServidor.PedidoEntrada pedido) {
        ConexaoServidor conexao = new ConexaoServidor(ouvinte);
        conexoes.add(conexao);
        assertTrue(conexao.iniciar(ServidorTeste.ENDERECO, servidor.porta(), pedido));
        return conexao;
    }

    private ConexaoServidor cadastrarAna(OuvinteContas ouvinte) throws Exception {
        char[] senha = "cerejeira em flor".toCharArray();
        ConexaoServidor conexao = iniciar(ouvinte, new ConexaoServidor.Cadastrar("Ana.Souza", "Ana", senha));

        assertEquals("Ana", ouvinte.identidades.poll(ESPERA_S, TimeUnit.SECONDS));
        assertTrue(ouvinte.confirmada.await(ESPERA_S, TimeUnit.SECONDS));
        ouvinte.esperarEvento(br.unicesumar.chat.protocolo.ProtocoloJson.TipoEvento.HISTORICO);
        assertArrayEquals(new char[senha.length], senha, "A senha é apagada após o envio");
        return conexao;
    }

    @Test
    void cadastroELoginDevemConfirmarAIdentidadeDoServidor() throws Exception {
        OuvinteContas primeira = new OuvinteContas();
        ConexaoServidor conexao = cadastrarAna(primeira);
        assertFalse(primeira.eventoNaEdt.get());

        conexao.desconectar();
        assertTrue(primeira.encerrada.await(ESPERA_S, TimeUnit.SECONDS));

        // Login pelo login (sem diferenciar maiúsculas): o nome exibido é o apelido.
        OuvinteContas segunda = new OuvinteContas();
        iniciar(segunda, new ConexaoServidor.Entrar("ANA.SOUZA", "cerejeira em flor".toCharArray()));
        assertEquals("Ana", segunda.identidades.poll(ESPERA_S, TimeUnit.SECONDS));
        assertTrue(segunda.confirmada.await(ESPERA_S, TimeUnit.SECONDS));
    }

    @Test
    void senhaIncorretaDeveSerInformadaSemConfirmarAConexao() throws Exception {
        cadastrarAna(new OuvinteContas()).desconectar();

        OuvinteContas ouvinte = new OuvinteContas();
        ConexaoServidor conexao = iniciar(ouvinte, new ConexaoServidor.Entrar("ana.souza", "senha errada".toCharArray()));

        assertEquals("Login ou senha incorretos.", ouvinte.recusas.poll(ESPERA_S, TimeUnit.SECONDS));
        assertTrue(ouvinte.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
        assertEquals(1, ouvinte.confirmada.getCount());
        assertFalse(conexao.ocupada());
        assertTrue(ouvinte.identidades.isEmpty());
    }

    @Test
    void visitanteComNomeDeContaDeveSerRecusado() throws Exception {
        cadastrarAna(new OuvinteContas()).desconectar();

        OuvinteContas ouvinte = new OuvinteContas();
        iniciar(ouvinte, new ConexaoServidor.Visitante("ana"));

        ouvinte.esperar(l -> l.startsWith("SISTEMA: Não foi possível entrar: este nome pertence a uma conta"));
        assertTrue(ouvinte.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
        assertEquals(1, ouvinte.confirmada.getCount());
    }

    @Test
    void servidorSemContasDeveRecusarOLoginComMensagemClara() throws Exception {
        try (ServidorTeste semContas = new ServidorTeste()) {
            OuvinteContas ouvinte = new OuvinteContas();
            ConexaoServidor conexao = new ConexaoServidor(ouvinte);
            conexoes.add(conexao);
            assertTrue(conexao.iniciar(ServidorTeste.ENDERECO, semContas.porta(),
                    new ConexaoServidor.Entrar("ana", "qualquer senha".toCharArray())));

            assertTrue(ouvinte.recusas.poll(ESPERA_S, TimeUnit.SECONDS).contains("não oferece contas"));
            assertTrue(ouvinte.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
        }
    }

    @Test
    void loginNaEdtNaoDeveBloquearEAtualizaONomeExibidoNaEdt() throws Exception {
        List<String> identificados = new CopyOnWriteArrayList<>();
        AtomicBoolean foraDaEdt = new AtomicBoolean(false);
        AtomicBoolean historicoConcluido = new AtomicBoolean(false);

        ControladorConversas.Visao visao = new ControladorConversas.Visao() {
            private void verificar() {
                if (!SwingUtilities.isEventDispatchThread()) {
                    foraDaEdt.set(true);
                }
            }

            @Override public void reiniciarConversa(long id, String usuario) { verificar(); }
            @Override public void exibirLinha(long id, String linha) {
                verificar();
                if (FormatoLinhaChat.FIM_HISTORICO.equals(linha)) {
                    historicoConcluido.set(true);
                }
            }
            @Override public boolean historicoConcluido(long id) { verificar(); return historicoConcluido.get(); }
            @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { verificar(); }
            @Override public void mensagemEnviada() { verificar(); }
            @Override public void usuarioIdentificado(String apelido) { verificar(); identificados.add(apelido); }
            @Override public void autenticacaoRecusada(String mensagem) { verificar(); }
        };

        cadastrarAna(new OuvinteContas()).desconectar();

        ControladorConversas controlador = new ControladorConversas(
                visao, new NotificacoesSonoras(new ReprodutorSomMudo(), System::currentTimeMillis));

        // Servidor mudo: se a autenticação rodasse na EDT, invokeAndWait travaria.
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicReference<ControladorConversas.EstadoConexao> estado = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                controlador.conectar(ServidorTeste.ENDERECO, mudo.getLocalPort(),
                        new ConexaoServidor.Entrar("ana.souza", "cerejeira em flor".toCharArray()));
                estado.set(controlador.estado());
            });
            assertEquals(ControladorConversas.EstadoConexao.CONECTANDO, estado.get());
            try (Socket aceito = mudo.accept()) {
                SwingUtilities.invokeAndWait(controlador::desconectar);
            }
        }

        // Aguarda a primeira sessão terminar antes de entrar de verdade.
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
        AtomicReference<Boolean> livre = new AtomicReference<>(false);
        while (!livre.get() && System.nanoTime() < limite) {
            SwingUtilities.invokeAndWait(() -> livre.set(
                    controlador.estado() == ControladorConversas.EstadoConexao.DESCONECTADO));
        }

        SwingUtilities.invokeAndWait(() -> assertTrue(controlador.conectar(ServidorTeste.ENDERECO, servidor.porta(),
                new ConexaoServidor.Entrar("ana.souza", "cerejeira em flor".toCharArray()))));

        limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
        AtomicReference<String> nome = new AtomicReference<>("");
        while (!"Ana".equals(nome.get()) && System.nanoTime() < limite) {
            SwingUtilities.invokeAndWait(() -> nome.set(
                    controlador.estado() == ControladorConversas.EstadoConexao.CONECTADO
                            ? controlador.nomeUsuario() : ""));
        }

        assertEquals("Ana", nome.get(), "O nome exibido vem da conta, não do login digitado");
        assertEquals(List.of("Ana"), identificados);
        assertFalse(foraDaEdt.get());
        SwingUtilities.invokeAndWait(controlador::desconectar);
    }

    private static final class ReprodutorSomMudo implements ReprodutorSom {
        @Override
        public void tocar(SomSakura som) {
        }

        @Override
        public void fechar() {
        }
    }
}
