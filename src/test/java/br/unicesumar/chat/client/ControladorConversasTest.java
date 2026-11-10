package br.unicesumar.chat.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static br.unicesumar.chat.client.ControladorConversas.EstadoConexao.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Controlador das conversas: encaminhamento dos eventos de rede à
 * interface (sempre na EDT), estado da conversa, sons e integração
 * com o servidor real em memória.
 */
class ControladorConversasTest {

    private static final long ESPERA_S = 5;

    private static final class ReprodutorFalso implements ReprodutorSom {
        final List<SomSakura> tocados = new CopyOnWriteArrayList<>();

        @Override
        public void tocar(SomSakura som) {
            tocados.add(som);
        }

        @Override
        public void fechar() {
        }
    }

    /** Visão sem componentes: registra as chamadas e verifica a EDT. */
    private static final class VisaoFalsa implements ControladorConversas.Visao {

        final List<String> linhas = new CopyOnWriteArrayList<>();
        final List<ControladorConversas.EstadoConexao> estados = new CopyOnWriteArrayList<>();
        final AtomicBoolean foraDaEdt = new AtomicBoolean(false);
        volatile int reinicios;
        volatile int envios;
        volatile boolean historicoConcluido;

        private void verificarEdt() {
            if (!SwingUtilities.isEventDispatchThread()) {
                foraDaEdt.set(true);
            }
        }

        @Override
        public void reiniciarConversa(String idConversa, String usuarioAtual) {
            verificarEdt();
            reinicios++;
            linhas.clear();
            historicoConcluido = false;
        }

        @Override
        public void exibirLinha(String idConversa, String linha) {
            verificarEdt();
            assertEquals(ControladorConversas.ID_GERAL, idConversa);
            linhas.add(linha);
            if (FormatoLinhaChat.FIM_HISTORICO.equals(linha)) {
                historicoConcluido = true;
            }
        }

        @Override
        public boolean historicoConcluido(String idConversa) {
            verificarEdt();
            return historicoConcluido;
        }

        @Override
        public void estadoAlterado(ControladorConversas.EstadoConexao estado) {
            verificarEdt();
            estados.add(estado);
        }

        @Override
        public void mensagemEnviada() {
            verificarEdt();
            envios++;
        }
    }

    private final ReprodutorFalso reprodutor = new ReprodutorFalso();
    private final List<ControladorConversas> controladores = new ArrayList<>();
    private ServidorTeste servidor;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorTeste();
    }

    @AfterEach
    void encerrar() throws Exception {
        naEdt(() -> controladores.forEach(ControladorConversas::desconectar));
        servidor.close();
    }

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable t) {
                erro.set(t);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    /** Espera até que a condição, avaliada na EDT, seja verdadeira. */
    private static void esperarNaEdt(java.util.function.BooleanSupplier condicao, String descricao)
            throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
        AtomicBoolean ok = new AtomicBoolean(false);
        while (System.nanoTime() < limite) {
            naEdt(() -> ok.set(condicao.getAsBoolean()));
            if (ok.get()) {
                return;
            }
            new CountDownLatch(1).await(50, TimeUnit.MILLISECONDS);
        }
        fail("Tempo esgotado: " + descricao);
    }

    private ControladorConversas novo(VisaoFalsa visao) {
        ControladorConversas controlador = new ControladorConversas(
                visao, new NotificacoesSonoras(reprodutor, System::currentTimeMillis));
        controladores.add(controlador);
        return controlador;
    }

    private ControladorConversas conectado(VisaoFalsa visao, String nome) throws Exception {
        ControladorConversas controlador = novo(visao);
        naEdt(() -> assertTrue(controlador.conectar(ServidorTeste.ENDERECO, servidor.porta(), nome)));
        esperarNaEdt(() -> visao.historicoConcluido && controlador.estado() == CONECTADO,
                nome + " conectado com histórico");
        return controlador;
    }

    @Test
    void eventosDeRedeDevemChegarNaEdtNaOrdemDeEnvio() throws Exception {
        VisaoFalsa visao = new VisaoFalsa();
        AtomicReference<ConexaoServidor.Ouvinte> ouvinte = new AtomicReference<>();

        ControladorConversas controlador = new ControladorConversas(
                visao,
                new NotificacoesSonoras(reprodutor, System::currentTimeMillis),
                SwingUtilities::invokeLater,
                o -> {
                    ouvinte.set(o);
                    return new ConexaoServidor(o);
                }
        );

        // Eventos disparados por uma thread de rede simulada.
        ExecutorService rede = Executors.newSingleThreadExecutor();
        rede.execute(() -> {
            for (int i = 1; i <= 50; i++) {
                ouvinte.get().linhaRecebida("[10/11 10:00] Bia: linha " + i);
            }
            ouvinte.get().mensagemEnviada();
            ouvinte.get().estadoAlterado();
        });
        rede.shutdown();
        assertTrue(rede.awaitTermination(ESPERA_S, TimeUnit.SECONDS));

        naEdt(() -> {
            // Ainda na fila da EDT antes deste ponto: tudo já foi processado.
        });

        assertFalse(visao.foraDaEdt.get(), "A visão só pode ser chamada na EDT");
        assertEquals(50, visao.linhas.size());
        for (int i = 1; i <= 50; i++) {
            assertEquals("[10/11 10:00] Bia: linha " + i, visao.linhas.get(i - 1));
        }
        assertEquals(1, visao.envios);
        assertEquals(List.of(DESCONECTADO), visao.estados);

        naEdt(() -> {
            assertEquals(visao.linhas, controlador.conversaAtiva().linhas());
            assertEquals(String.join("\n", visao.linhas) + "\n", controlador.conversaAtiva().texto());
        });
    }

    @Test
    void deveTerSomenteOChatGeralNestaEtapa() throws Exception {
        ControladorConversas controlador = novo(new VisaoFalsa());

        naEdt(() -> {
            assertEquals(1, controlador.conversas().size());
            assertEquals(ControladorConversas.ID_GERAL, controlador.conversaAtiva().id());
            assertEquals(ControladorConversas.TITULO_GERAL, controlador.conversaAtiva().titulo());
            assertEquals(DESCONECTADO, controlador.estado());
            assertFalse(controlador.enviar("sem conexão"));
        });
    }

    @Test
    void conectarNaEdtNaoDeveBloquearEnquantoOServidorNaoResponde() throws Exception {
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {

            VisaoFalsa visao = new VisaoFalsa();
            ControladorConversas controlador = novo(visao);

            // Se a rede rodasse na EDT, este invokeAndWait nunca terminaria,
            // pois o servidor não responde.
            naEdt(() -> {
                assertTrue(controlador.conectar(ServidorTeste.ENDERECO, mudo.getLocalPort(), "Ana"));
                assertEquals(CONECTANDO, controlador.estado());
                assertFalse(controlador.conectar(ServidorTeste.ENDERECO, mudo.getLocalPort(), "Ana"));
            });

            try (Socket aceito = mudo.accept()) {
                naEdt(controlador::desconectar);
                esperarNaEdt(() -> controlador.estado() == DESCONECTADO, "desconexão");
            }

            assertEquals(1, visao.reinicios);
            assertFalse(visao.foraDaEdt.get());
            assertFalse(reprodutor.tocados.contains(SomSakura.CONECTADO));
            assertFalse(reprodutor.tocados.contains(SomSakura.DESCONECTADO),
                    "Sessão nunca confirmada não toca desconexão");
        }
    }

    @Test
    void tresClientesDevemConversarComHistoricoSonsEReconexao() throws Exception {
        VisaoFalsa visaoAna = new VisaoFalsa();
        VisaoFalsa visaoBia = new VisaoFalsa();
        VisaoFalsa visaoCaio = new VisaoFalsa();

        ControladorConversas ana = conectado(visaoAna, "Ana");
        ControladorConversas bia = conectado(visaoBia, "Bia");
        ControladorConversas caio = conectado(visaoCaio, "Caio");

        esperarNaEdt(() -> visaoAna.linhas.stream().anyMatch(l -> l.endsWith("Caio se conectou")),
                "aviso de presença de Caio");

        naEdt(() -> assertTrue(ana.enviar("Oi, pessoal!")));
        esperarNaEdt(() -> visaoAna.envios == 1, "animação de envio");
        naEdt(() -> assertTrue(bia.enviar("Oi, Ana!")));
        esperarNaEdt(() -> visaoCaio.linhas.stream().anyMatch(l -> l.endsWith(" Bia: Oi, Ana!")),
                "mensagem de Bia em Caio");
        naEdt(() -> assertTrue(caio.enviar("Olá!")));

        for (VisaoFalsa visao : List.of(visaoAna, visaoBia, visaoCaio)) {
            esperarNaEdt(() -> visao.linhas.stream().anyMatch(l -> l.endsWith(" Caio: Olá!")),
                    "mensagem de Caio");
        }

        // Mesma ordem de mensagens nos três clientes.
        List<String> ordemAna = mensagens(visaoAna);
        assertEquals(ordemAna, mensagens(visaoBia));
        assertEquals(ordemAna, mensagens(visaoCaio));
        assertEquals(3, ordemAna.size());

        // Sons: conexão confirmada e recebimento de mensagens de outros.
        assertTrue(reprodutor.tocados.contains(SomSakura.CONECTADO));
        assertTrue(reprodutor.tocados.contains(SomSakura.MENSAGEM_ENVIADA));
        assertTrue(reprodutor.tocados.contains(SomSakura.MENSAGEM_RECEBIDA));

        // Exportação: o texto da conversa ativa reflete as linhas exibidas.
        naEdt(() -> assertEquals(String.join("\n", visaoAna.linhas) + "\n",
                ana.conversaAtiva().texto()));

        // Reconexão: Caio sai e volta; recebe o histórico com as mensagens.
        naEdt(caio::desconectar);
        esperarNaEdt(() -> caio.estado() == DESCONECTADO, "Caio desconectado");
        esperarNaEdt(() -> visaoAna.linhas.stream().anyMatch(l -> l.endsWith("Caio se desconectou")),
                "aviso de saída");
        assertTrue(reprodutor.tocados.contains(SomSakura.DESCONECTADO));

        naEdt(() -> assertTrue(caio.conectar(ServidorTeste.ENDERECO, servidor.porta(), "Caio")));
        esperarNaEdt(() -> visaoCaio.historicoConcluido && caio.estado() == CONECTADO,
                "Caio reconectado");

        assertEquals(2, visaoCaio.reinicios);
        assertEquals("SISTEMA: Bem-vindo ao Chat Sakura!", visaoCaio.linhas.get(0));
        assertTrue(mensagens(visaoCaio).containsAll(ordemAna), "Histórico após reconexão");
        naEdt(() -> assertEquals(visaoCaio.linhas, caio.conversaAtiva().linhas()));

        assertFalse(visaoAna.foraDaEdt.get() || visaoBia.foraDaEdt.get() || visaoCaio.foraDaEdt.get());
    }

    private static List<String> mensagens(VisaoFalsa visao) {
        return visao.linhas.stream()
                .filter(l -> FormatoLinhaChat.analisar(l).tipo() == FormatoLinhaChat.Tipo.MENSAGEM)
                .distinct()
                .toList();
    }
}
