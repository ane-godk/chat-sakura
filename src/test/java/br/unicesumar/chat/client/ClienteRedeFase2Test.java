package br.unicesumar.chat.client;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.server.LimitesServidor;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.service.HashSenha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cliente contra o servidor real (SQLite temporário): ordem de envio (B1),
 * nova tentativa de histórico (B2) e reconexão com restauração (B10).
 */
class ClienteRedeFase2Test {

    private static final String SENHA = "senha-segura-1";
    private static final String ENDERECO = InetAddress.getLoopbackAddress().getHostAddress();

    @TempDir
    Path pasta;

    /** Servidor real que pode cair e voltar na mesma porta (como um reinício). */
    private final class ServidorReiniciavel {

        private int porta;
        private ServerSocket socket;
        private ExecutorService threads;
        private final List<Socket> abertos = new CopyOnWriteArrayList<>();
        Servidor.Servicos servicos;

        void iniciar() throws IOException {
            LimitesServidor limites = LimitesServidor.padrao().comTaxas(
                    new LimitesServidor.Taxa(1_000, 60_000), new LimitesServidor.Taxa(100, 6_000),
                    new LimitesServidor.Taxa(1_000, 60_000), System::nanoTime);
            servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("cliente.db").toString()),
                    new HashSenha(1_000), Clock.systemDefaultZone(), new ProvedorIAFalso(), limites);
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), 50);
            porta = socket.getLocalPort();
            threads = Executors.newCachedThreadPool();
            ServerSocket atual = socket;
            threads.execute(() -> {
                while (!atual.isClosed()) {
                    try {
                        Socket s = atual.accept();
                        abertos.add(s);
                        threads.execute(servicos.atender(s));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        void parar() throws Exception {
            socket.close();
            for (Socket s : abertos) {
                s.close();
            }
            abertos.clear();
            threads.shutdown();
            assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /** Visão que registra tudo numa fila (chamada só na "EDT" do teste). */
    private static final class Visao implements ControladorConversas.Visao {
        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
        volatile List<String> exibida = List.of();
        volatile long ativa = 1;

        @Override public void reiniciarConversa(long id, String usuario) { eventos.add("reiniciar"); }
        @Override public void exibirLinha(long id, String linha) { eventos.add("linha " + id + " " + linha); }
        @Override public boolean historicoConcluido(long id) { return true; }
        @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { eventos.add("estado " + e); }
        @Override public void mensagemEnviada() { }
        @Override public void usuarioIdentificado(String apelido) { eventos.add("identificado " + apelido); }
        @Override public void autenticacaoRecusada(String mensagem) { eventos.add("recusada " + mensagem); }
        @Override public void erroConversa(String mensagem) { eventos.add("erro " + mensagem); }

        @Override
        public void exibirConversa(ControladorConversas.Conversa conversa) {
            exibida = List.copyOf(conversa.linhas());
            eventos.add("exibir " + conversa.id() + " " + exibida);
        }

        @Override
        public void conversasAtualizadas(List<ControladorConversas.ItemLateral> itens, long idAtiva) {
            ativa = idAtiva;
        }

        @Override
        public void reconexaoEmAndamento(int tentativa, long proximaEmMs) {
            eventos.add("reconectando " + tentativa);
        }

        @Override
        public void reconexaoDisponivel(String endereco, int porta, String login) {
            eventos.add("disponivel " + login);
        }

        @Override
        public void reconexaoEncerrada() {
            eventos.add("encerrada");
        }

        String esperar(Predicate<String> criterio) throws InterruptedException {
            List<String> vistos = new ArrayList<>();
            while (true) {
                String evento = eventos.poll(10, TimeUnit.SECONDS);
                assertNotNull(evento, "Evento esperado não chegou. Vistos: " + vistos);
                vistos.add(evento);
                if (criterio.test(evento)) {
                    return evento;
                }
            }
        }
    }

    /** Sons tocados: DESCONECTADO marca o fim de uma sessão confirmada. */
    private static final class Sons implements ReprodutorSom {
        final BlockingQueue<SomSakura> tocados = new LinkedBlockingQueue<>();
        @Override public void tocar(SomSakura som) { tocados.add(som); }
        @Override public void fechar() { }

        void esperar(SomSakura esperado) throws InterruptedException {
            while (true) {
                SomSakura som = tocados.poll(10, TimeUnit.SECONDS);
                assertNotNull(som, "Som " + esperado + " não tocou");
                if (som == esperado) {
                    return;
                }
            }
        }
    }

    private final ServidorReiniciavel servidor = new ServidorReiniciavel();
    private final ExecutorService edt = Executors.newSingleThreadExecutor();
    private final List<ControladorConversas> controladores = new ArrayList<>();
    private long idAna;
    private long idBia;

    @BeforeEach
    void iniciar() throws Exception {
        servidor.iniciar();
        idAna = servidor.servicos.autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray()).getId();
        idBia = servidor.servicos.autenticacao().cadastrar("bia", "Bia", SENHA.toCharArray()).getId();
    }

    @AfterEach
    void encerrar() throws Exception {
        for (ControladorConversas c : controladores) {
            naEdt(() -> {
                c.encerrar();
                return null;
            });
        }
        servidor.parar();
        edt.shutdown();
        assertTrue(edt.awaitTermination(5, TimeUnit.SECONDS));
    }

    private <T> T naEdt(Callable<T> acao) throws Exception {
        return edt.submit(acao).get(10, TimeUnit.SECONDS);
    }

    private final class Cliente {
        final Visao visao = new Visao();
        final Sons sons = new Sons();
        final AtomicReference<ConexaoServidor.Ouvinte> ouvinte = new AtomicReference<>();
        final ControladorConversas controlador;

        Cliente() throws Exception {
            controlador = naEdt(() -> new ControladorConversas(visao,
                    new NotificacoesSonoras(sons, System::currentTimeMillis), edt::execute,
                    o -> {
                        ouvinte.set(o);
                        return new ConexaoServidor(o);
                    },
                    o -> new ReconexaoAutomatica(o, ReconexaoAutomatica.SONDA_TCP, 50, 200)));
            controladores.add(controlador);
        }

        void entrar(String login, String apelido) throws Exception {
            naEdt(() -> controlador.conectar(ENDERECO, servidor.porta,
                    new ConexaoServidor.Entrar(login, SENHA.toCharArray())));
            visao.esperar(("identificado " + apelido)::equals);
            visao.esperar(e -> e.startsWith("linha 1 SISTEMA: Conectado como"));
        }

        ControladorConversas.EstadoConexao estado() throws Exception {
            return naEdt(controlador::estado);
        }

        /** Espera o fim da sessão confirmada e a reação do controlador a ele. */
        void esperarFimDaSessao() throws Exception {
            sons.esperar(SomSakura.DESCONECTADO);
            naEdt(() -> null);
        }
    }

    // ------------------------------------------------------------------
    // B1 — ordem de envio
    // ------------------------------------------------------------------

    @Test
    void enviosRapidosEConcorrentesDevemSairNaOrdemSemThreadPorEnvio() throws Exception {
        int remetentes = 5;
        int porRemetente = 10;
        CountDownLatch confirmada = new CountDownLatch(1);
        CountDownLatch recebidas = new CountDownLatch(remetentes * porRemetente);
        List<String> ecos = Collections.synchronizedList(new ArrayList<>());

        ConexaoServidor conexao = new ConexaoServidor(new ConexaoServidor.Ouvinte() {
            @Override public void linhaRecebida(String linha) {
                if (linha.contains("Caio: t")) {
                    ecos.add(linha.substring(linha.indexOf("Caio: ") + 6));
                    recebidas.countDown();
                }
            }
            @Override public void conexaoConfirmada() { confirmada.countDown(); }
            @Override public void mensagemEnviada() { }
            @Override public void estadoAlterado() { }
            @Override public void sessaoEncerrada() { }
        });
        assertTrue(conexao.iniciar(ENDERECO, servidor.porta, "Caio"));
        assertTrue(confirmada.await(10, TimeUnit.SECONDS));

        ExecutorService threads = Executors.newFixedThreadPool(remetentes);
        for (int t = 0; t < remetentes; t++) {
            int remetente = t;
            threads.execute(() -> {
                for (int i = 0; i < porRemetente; i++) {
                    assertTrue(conexao.enviar("t" + remetente + "-" + i));
                }
            });
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(recebidas.await(10, TimeUnit.SECONDS), "Ecos: " + ecos.size());

        long escritores = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().equals("sakura-envio")).count();
        assertTrue(escritores <= 1, "Um único escritor por sessão: " + escritores);

        for (int t = 0; t < remetentes; t++) {
            String prefixo = "t" + t + "-";
            List<String> doRemetente = ecos.stream().filter(e -> e.startsWith(prefixo)).toList();
            List<String> esperado = new ArrayList<>();
            for (int i = 0; i < porRemetente; i++) {
                esperado.add(prefixo + i);
            }
            assertEquals(esperado, doRemetente, "Ordem de cada remetente preservada");
        }

        // A ordem recebida é a ordem gravada no servidor.
        List<String> gravadas = servidor.servicos.chat().buscarHistorico(100).stream()
                .map(Mensagem::getConteudo).filter(c -> c.startsWith("t")).toList();
        assertEquals(gravadas, ecos);

        conexao.desconectar();
        assertFalse(conexao.enviar("depois do fim"), "Nada é enviado após o encerramento");
    }

    // ------------------------------------------------------------------
    // B2 — histórico
    // ------------------------------------------------------------------

    @Test
    void erroDeHistoricoDeveLiberarNovaTentativaSemDuplicar() throws Exception {
        Cliente ana = new Cliente();
        ana.entrar("ana", "Ana");

        // Uma conversa que o servidor não reconhece para a Ana: o HISTORICO falha.
        Conversa fantasma = new Conversa(999, Conversa.Tipo.GRUPO, "Fantasma", List.of());
        ProtocoloJson.Evento evento = ProtocoloJson.lerEvento(
                ProtocoloJson.eventoConversa(null, ProtocoloJson.ConversaJson.de(fantasma, null, 0)));
        ana.ouvinte.get().eventoRecebido(evento);
        naEdt(() -> null);

        naEdt(() -> {
            ana.controlador.selecionar(999);
            return null;
        });
        ana.visao.esperar(e -> e.startsWith("erro Conversa não encontrada"));

        naEdt(() -> {
            ana.controlador.selecionar(1);
            ana.controlador.selecionar(999);
            return null;
        });
        ana.visao.esperar(e -> e.startsWith("erro Conversa não encontrada"));

        // Conversa real: o histórico chega uma vez; uma resposta repetida não duplica.
        naEdt(() -> ana.controlador.abrirPrivada(idBia));
        String exibida = ana.visao.esperar(e -> e.startsWith("exibir ") && !e.startsWith("exibir 999")
                && !e.startsWith("exibir 1 "));
        long privada = Long.parseLong(exibida.split(" ")[1]);
        servidor.servicos.conversas().enviar(new Usuario(idBia, "Bia"), privada, "Oi, Ana");
        ana.visao.esperar(e -> e.startsWith("linha " + privada) && e.endsWith("Bia: Oi, Ana"));

        List<ProtocoloJson.MensagemJson> historico = servidor.servicos.conversas()
                .historico(new Usuario(idAna, "Ana"), privada, null, 50).stream()
                .map(ProtocoloJson.MensagemJson::de).toList();
        ana.ouvinte.get().eventoRecebido(ProtocoloJson.lerEvento(
                ProtocoloJson.eventoHistorico(null, privada, historico)));
        naEdt(() -> null);

        List<String> linhas = naEdt(() -> ana.controlador.conversaAtiva().linhas());
        assertEquals(1, linhas.stream().filter(l -> l.endsWith("Bia: Oi, Ana")).count(), linhas.toString());
        assertTrue(linhas.stream().filter(FormatoLinhaChat.INICIO_HISTORICO::equals).count() <= 1, linhas.toString());
        assertNotEquals(999, ana.visao.ativa);
    }

    // ------------------------------------------------------------------
    // B10 — reconexão
    // ------------------------------------------------------------------

    @Test
    void quedaDoServidorDeveReconectarComNovoLoginERestaurarAConversa() throws Exception {
        Cliente ana = new Cliente();
        ana.entrar("ana", "Ana");

        naEdt(() -> ana.controlador.abrirPrivada(idBia));
        String exibida = ana.visao.esperar(e -> e.startsWith("exibir ") && !e.startsWith("exibir 1 "));
        long privada = Long.parseLong(exibida.split(" ")[1]);
        naEdt(() -> ana.controlador.enviar("antes da queda"));
        ana.visao.esperar(e -> e.startsWith("linha " + privada) && e.endsWith("Ana: antes da queda"));

        // Queda do servidor: a sessão cai e a reconexão começa.
        servidor.parar();
        ana.esperarFimDaSessao();
        ana.visao.esperar("reconectando 0"::equals);
        assertEquals(ControladorConversas.EstadoConexao.RECONECTANDO, ana.estado());
        ana.visao.esperar("reconectando 1"::equals);
        assertFalse(naEdt(() -> ana.controlador.enviar("durante a queda")), "Nada é enfileirado sem conexão");

        // O servidor volta: o controlador pede novo login (a senha não ficou guardada).
        servidor.iniciar();
        ana.visao.esperar("disponivel ana"::equals);
        naEdt(() -> ana.controlador.conectar(ENDERECO, servidor.porta,
                new ConexaoServidor.Entrar("ana", SENHA.toCharArray())));
        ana.visao.esperar("identificado Ana"::equals);

        // A conversa ativa é restaurada, com o histórico persistido, sem duplicatas.
        String restaurada = ana.visao.esperar(e -> e.startsWith("exibir " + privada + " ")
                && e.contains("antes da queda") && e.contains(FormatoLinhaChat.FIM_HISTORICO));
        assertEquals(1, ana.visao.exibida.stream().filter(l -> l.endsWith("Ana: antes da queda")).count(),
                restaurada);
        assertEquals(privada, ana.visao.ativa);
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, ana.estado());
        assertTrue(servidor.servicos.conversas().historico(new Usuario(idAna, "Ana"), privada, null, 50).stream()
                .noneMatch(m -> m.getConteudo().equals("durante a queda")), "Sem reenvio automático");
    }

    @Test
    void reconexaoDeveSerCancelavel() throws Exception {
        Cliente ana = new Cliente();
        ana.entrar("ana", "Ana");

        servidor.parar();
        ana.esperarFimDaSessao();
        ana.visao.esperar("reconectando 0"::equals);

        naEdt(() -> {
            ana.controlador.cancelarReconexao();
            return null;
        });
        ana.visao.esperar("encerrada"::equals);
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, ana.estado());

        servidor.iniciar();
        // Sem reconexão ativa, o servidor de volta não gera pedido de login.
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, ana.estado());
        assertTrue(ana.visao.eventos.stream().noneMatch(e -> e.startsWith("disponivel")));
    }

    @Test
    void saidaVoluntariaNaoDeveReconectar() throws Exception {
        Cliente ana = new Cliente();
        ana.entrar("ana", "Ana");

        naEdt(() -> {
            ana.controlador.desconectar();
            return null;
        });
        ana.esperarFimDaSessao();
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, ana.estado());
        assertTrue(ana.visao.eventos.stream().noneMatch(e -> e.startsWith("reconectando")));
    }

    @Test
    void sessaoSubstituidaPorOutroLoginNaoDeveReconectar() throws Exception {
        Cliente primeira = new Cliente();
        primeira.entrar("ana", "Ana");
        Cliente segunda = new Cliente();
        segunda.entrar("ana", "Ana");

        primeira.visao.esperar(e -> e.startsWith("erro Sua conta entrou em outra janela"));
        primeira.esperarFimDaSessao();
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, primeira.estado());
        assertTrue(primeira.visao.eventos.stream().noneMatch(e -> e.startsWith("reconectando")));
        assertEquals(ControladorConversas.EstadoConexao.CONECTADO, segunda.estado());
    }

    @Test
    void visitanteNaoTemReconexaoAutomatica() throws Exception {
        Cliente caio = new Cliente();
        naEdt(() -> caio.controlador.conectar(ENDERECO, servidor.porta, "Caio"));
        caio.visao.esperar(e -> e.equals("linha 1 SISTEMA: Conectado como Caio"));

        servidor.parar();
        caio.esperarFimDaSessao();
        assertEquals(ControladorConversas.EstadoConexao.DESCONECTADO, caio.estado(),
                "Visitantes e legado mantêm o comportamento anterior");
        servidor.iniciar();
    }
}
