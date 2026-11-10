package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Comunicação TCP do cliente contra o servidor real, sem Swing e sem banco. */
class ConexaoServidorTest {

    private static final long ESPERA_S = 5;

    /** Registra os eventos e se algum chegou na EDT (não deveria). */
    static final class OuvinteGravador implements ConexaoServidor.Ouvinte {

        final LinkedBlockingQueue<String> linhas = new LinkedBlockingQueue<>();
        final List<String> todas = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CountDownLatch confirmada = new CountDownLatch(1);
        final CountDownLatch encerrada = new CountDownLatch(1);
        final LinkedBlockingQueue<Boolean> enviadas = new LinkedBlockingQueue<>();
        final AtomicBoolean eventoNaEdt = new AtomicBoolean(false);

        private void registrarThread() {
            if (SwingUtilities.isEventDispatchThread()) {
                eventoNaEdt.set(true);
            }
        }

        @Override
        public void linhaRecebida(String linha) {
            registrarThread();
            todas.add(linha);
            linhas.add(linha);
        }

        @Override
        public void conexaoConfirmada() {
            registrarThread();
            confirmada.countDown();
        }

        @Override
        public void mensagemEnviada() {
            registrarThread();
            enviadas.add(true);
        }

        @Override
        public void estadoAlterado() {
            registrarThread();
        }

        @Override
        public void sessaoEncerrada() {
            registrarThread();
            encerrada.countDown();
        }

        /** Consome linhas até encontrar uma que satisfaça o critério. */
        String esperar(Predicate<String> criterio) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
            while (System.nanoTime() < limite) {
                String linha = linhas.poll(100, TimeUnit.MILLISECONDS);
                if (linha != null && criterio.test(linha)) {
                    return linha;
                }
            }
            throw new AssertionError("Linha esperada não chegou. Recebidas: " + todas);
        }
    }

    private ServidorTeste servidor;
    private final List<ConexaoServidor> conexoes = new ArrayList<>();

    @BeforeEach
    void iniciarServidor() throws Exception {
        servidor = new ServidorTeste();
    }

    @AfterEach
    void encerrar() throws Exception {
        conexoes.forEach(ConexaoServidor::desconectar);
        servidor.close();
    }

    private ConexaoServidor nova(OuvinteGravador ouvinte) {
        ConexaoServidor conexao = new ConexaoServidor(ouvinte);
        conexoes.add(conexao);
        return conexao;
    }

    private ConexaoServidor conectar(OuvinteGravador ouvinte, String nome) throws Exception {
        ConexaoServidor conexao = nova(ouvinte);
        assertTrue(conexao.iniciar(ServidorTeste.ENDERECO, servidor.porta(), nome));
        assertTrue(ouvinte.confirmada.await(ESPERA_S, TimeUnit.SECONDS), "Conexão não confirmada");
        ouvinte.esperar(FormatoLinhaChat.FIM_HISTORICO::equals);
        return conexao;
    }

    @Test
    void deveConectarEReceberHistoricoNaOrdemDoProtocolo() throws Exception {
        servidor.repositorio.salvar(new Mensagem(
                new Usuario("Bia"), "Mensagem antiga", LocalDateTime.of(2026, 11, 1, 10, 0)));

        OuvinteGravador ouvinte = new OuvinteGravador();
        ConexaoServidor conexao = conectar(ouvinte, "Ana");

        assertTrue(conexao.conectado());
        assertEquals(List.of(
                "SISTEMA: Bem-vindo ao Chat Sakura!",
                "SISTEMA: Informe seu nome de usuário:",
                "SISTEMA: Conectado como Ana",
                FormatoLinhaChat.INICIO_HISTORICO,
                "[01/11 10:00] Bia: Mensagem antiga",
                FormatoLinhaChat.FIM_HISTORICO
        ), ouvinte.todas);
        assertFalse(ouvinte.eventoNaEdt.get(), "Eventos de rede não devem rodar na EDT");
    }

    @Test
    void deveEnviarEReceberMensagensEAvisosDePresenca() throws Exception {
        OuvinteGravador ana = new OuvinteGravador();
        conectar(ana, "Ana");

        OuvinteGravador bia = new OuvinteGravador();
        ConexaoServidor conexaoBia = conectar(bia, "Bia");

        ana.esperar(l -> l.startsWith("SISTEMA: [") && l.endsWith("Bia se conectou"));

        assertTrue(conexaoBia.enviar("Oi, Ana!"));
        assertNotNull(bia.enviadas.poll(ESPERA_S, TimeUnit.SECONDS), "Envio não confirmado");

        ana.esperar(l -> l.endsWith(" Bia: Oi, Ana!"));
        bia.esperar(l -> l.endsWith(" Bia: Oi, Ana!"));

        conexaoBia.desconectar();
        assertTrue(bia.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
        ana.esperar(l -> l.startsWith("SISTEMA: [") && l.endsWith("Bia se desconectou"));
        assertFalse(ana.eventoNaEdt.get() || bia.eventoNaEdt.get());
    }

    @Test
    void deveReconectarAposDesconectar() throws Exception {
        OuvinteGravador primeira = new OuvinteGravador();
        ConexaoServidor conexao = conectar(primeira, "Ana");

        conexao.desconectar();
        assertTrue(primeira.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
        assertFalse(conexao.ocupada());

        OuvinteGravador segunda = new OuvinteGravador();
        ConexaoServidor nova = conectar(segunda, "Ana");
        assertTrue(nova.conectado());
    }

    @Test
    void deveInformarRecusaDeNomeDuplicado() throws Exception {
        conectar(new OuvinteGravador(), "Ana");

        OuvinteGravador repetido = new OuvinteGravador();
        ConexaoServidor conexao = nova(repetido);
        assertTrue(conexao.iniciar(ServidorTeste.ENDERECO, servidor.porta(), "ana"));

        repetido.esperar(l -> l.startsWith("SISTEMA: Não foi possível entrar:"));
        repetido.esperar("SISTEMA: O servidor recusou a conexão."::equals);
        assertTrue(repetido.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
        assertEquals(1, repetido.confirmada.getCount(), "Recusa não confirma a conexão");
        assertFalse(conexao.ocupada());
    }

    @Test
    void iniciarNaoDeveBloquearEnquantoOServidorNaoResponde() throws Exception {
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {

            OuvinteGravador ouvinte = new OuvinteGravador();
            ConexaoServidor conexao = nova(ouvinte);

            // O servidor aceita, mas nunca envia nada: iniciar precisa retornar.
            assertTrue(conexao.iniciar(ServidorTeste.ENDERECO, mudo.getLocalPort(), "Ana"));
            assertTrue(conexao.conectando());
            assertFalse(conexao.iniciar(ServidorTeste.ENDERECO, mudo.getLocalPort(), "Ana"),
                    "Uma segunda conexão simultânea deve ser recusada");

            try (Socket aceito = mudo.accept()) {
                assertTrue(conexao.conectando());
                conexao.desconectar();
                assertTrue(ouvinte.encerrada.await(ESPERA_S, TimeUnit.SECONDS));
            }

            assertFalse(conexao.ocupada());
            assertEquals(1, ouvinte.confirmada.getCount());
        }
    }

    @Test
    void enviarSemConexaoDeveSerIgnorado() {
        OuvinteGravador ouvinte = new OuvinteGravador();
        ConexaoServidor conexao = nova(ouvinte);

        assertFalse(conexao.enviar("Oi"));
        assertTrue(ouvinte.enviadas.isEmpty());
    }
}
