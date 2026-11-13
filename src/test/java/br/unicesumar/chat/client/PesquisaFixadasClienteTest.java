package br.unicesumar.chat.client;

import br.unicesumar.chat.ia.ProvedorIAFalso;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controlador real contra o servidor real (SQLite temporário): pesquisa,
 * abertura de uma mensagem antiga (fora do histórico carregado) com
 * destaque, fixação e aviso ao outro participante.
 */
class PesquisaFixadasClienteTest {

    private static final String SENHA = "senha-segura-1";
    private static final String ENDERECO = InetAddress.getLoopbackAddress().getHostAddress();

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private ServerSocket socket;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> abertos = new CopyOnWriteArrayList<>();
    private final ExecutorService edt = Executors.newSingleThreadExecutor();
    private final List<ControladorConversas> controladores = new ArrayList<>();
    private Usuario ana;
    private Usuario bia;

    private static final class Visao implements ControladorConversas.Visao {
        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
        @Override public void reiniciarConversa(long id, String u) { }
        @Override public void exibirLinha(long id, String linha) { eventos.add("linha " + id + " " + linha); }
        @Override public boolean historicoConcluido(long id) { return true; }
        @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { }
        @Override public void mensagemEnviada() { }
        @Override public void usuarioIdentificado(String apelido) { eventos.add("identificado " + apelido); }
        @Override public void autenticacaoRecusada(String m) { eventos.add("recusada " + m); }
        @Override public void erroConversa(String m) { eventos.add("erro " + m); }
        @Override public void exibirConversa(ControladorConversas.Conversa c) { eventos.add("exibir " + c.id()); }
        @Override public void destacarMensagem(long id, long mensagemId) {
            eventos.add("destacar " + id + " " + mensagemId);
        }
        @Override public void fixadasAtualizadas(long id, List<ProtocoloJson.FixadaJson> fixadas) {
            eventos.add("fixadas " + id + " " + fixadas.stream().map(f -> f.mensagem().id()).toList());
        }

        String esperar(Predicate<String> criterio) throws InterruptedException {
            List<String> vistos = new ArrayList<>();
            while (true) {
                String e = eventos.poll(10, TimeUnit.SECONDS);
                assertNotNull(e, "Evento não chegou. Vistos: " + vistos);
                vistos.add(e);
                if (criterio.test(e)) {
                    return e;
                }
            }
        }
    }

    private final class Cliente {
        final Visao visao = new Visao();
        final ControladorConversas controlador;

        Cliente(String login, String apelido) throws Exception {
            controlador = naEdt(() -> new ControladorConversas(visao,
                    new NotificacoesSonoras(new ReprodutorSom() {
                        @Override public void tocar(SomSakura s) { }
                        @Override public void fechar() { }
                    }, System::currentTimeMillis), edt::execute, ConexaoServidor::new));
            controladores.add(controlador);
            naEdt(() -> controlador.conectar(ENDERECO, socket.getLocalPort(),
                    new ConexaoServidor.Entrar(login, SENHA.toCharArray())));
            visao.esperar(("identificado " + apelido)::equals);
            visao.esperar(e -> e.startsWith("linha 1 SISTEMA: Conectado como"));
        }

        void abrir(long conversa) throws Exception {
            naEdt(() -> {
                controlador.selecionar(conversa);
                return null;
            });
            visao.esperar(("fixadas " + conversa + " []")::equals);
        }
    }

    private <T> T naEdt(Callable<T> acao) throws Exception {
        return edt.submit(acao).get(10, TimeUnit.SECONDS);
    }

    @BeforeEach
    void iniciar() throws Exception {
        LimitesServidor limites = LimitesServidor.padrao().comTaxas(new LimitesServidor.Taxa(1_000, 60_000),
                new LimitesServidor.Taxa(100, 6_000), new LimitesServidor.Taxa(1_000, 60_000), System::nanoTime);
        servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("cliente-pesquisa.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), new ProvedorIAFalso(), limites);
        ana = new Usuario(servicos.autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray()).getId(), "Ana");
        bia = new Usuario(servicos.autenticacao().cadastrar("bia", "Bia", SENHA.toCharArray()).getId(), "Bia");
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(() -> {
            while (!socket.isClosed()) {
                try {
                    Socket s = socket.accept();
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
        for (ControladorConversas c : controladores) {
            naEdt(() -> {
                c.encerrar();
                return null;
            });
        }
        socket.close();
        for (Socket s : abertos) {
            s.close();
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        edt.shutdown();
        assertTrue(edt.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void pesquisaEAberturaDeMensagemAntigaComDestaque() throws Exception {
        long privada = servicos.conversas().abrirPrivada(ana, bia.getId()).conversa().getId();
        long antiga = servicos.conversas().enviar(bia, privada, "Senha do Wi-Fi: flor-de-cerejeira").getId();
        for (int i = 0; i < 130; i++) {
            servicos.conversas().enviar(i % 2 == 0 ? ana : bia, privada, "conversa comum " + i);
        }

        Cliente cliente = new Cliente("ana", "Ana");
        cliente.abrir(privada);
        assertFalse(naEdt(() -> cliente.controlador.conversaAtiva().contem(antiga)),
                "Fora das 50 mensagens carregadas");

        BlockingQueue<ControladorConversas.PaginaPesquisa> paginas = new LinkedBlockingQueue<>();
        assertTrue(naEdt(() -> cliente.controlador.pesquisar("wi-fi", null, null, null, null, paginas::add, null)));
        ControladorConversas.PaginaPesquisa pagina = paginas.poll(10, TimeUnit.SECONDS);
        assertNotNull(pagina);
        assertEquals(List.of(antiga), pagina.resultados().stream().map(r -> r.mensagem().id()).toList());

        naEdt(() -> {
            cliente.controlador.abrirMensagem(privada, antiga);
            return null;
        });
        cliente.visao.esperar(("destacar " + privada + " " + antiga)::equals);

        // Sem lacunas nem duplicatas: todas as 131 mensagens, em ordem.
        List<Long> ids = naEdt(() -> cliente.controlador.conversaAtiva().idsLinhas().stream()
                .filter(java.util.Objects::nonNull).toList());
        assertEquals(131, ids.size());
        assertEquals(131, new java.util.HashSet<>(ids).size());
        assertEquals(antiga, ids.get(0));
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1), "Ordem cronológica");
        }
    }

    @Test
    void fixarAvisaOOutroParticipanteENaoPermiteAoMembroDoGrupo() throws Exception {
        long grupo = servicos.conversas().criarGrupo(ana, "Jardim", List.of(bia.getId())).conversa().getId();
        long regra = servicos.conversas().enviar(ana, grupo, "Regras do jardim").getId();

        Cliente clienteAna = new Cliente("ana", "Ana");
        Cliente clienteBia = new Cliente("bia", "Bia");
        clienteAna.abrir(grupo);
        clienteBia.abrir(grupo);

        assertTrue(naEdt(() -> clienteAna.controlador.conversaAtiva().podeFixar()), "Ana é ADMIN");
        assertFalse(naEdt(() -> clienteBia.controlador.conversaAtiva().podeFixar()), "Bia é MEMBRO");

        naEdt(() -> clienteAna.controlador.fixar(regra));
        clienteAna.visao.esperar(("fixadas " + grupo + " [" + regra + "]")::equals);
        clienteBia.visao.esperar(("fixadas " + grupo + " [" + regra + "]")::equals);
        assertTrue(naEdt(() -> clienteBia.controlador.conversaAtiva().fixada(regra)));

        // Mesmo forçando o pedido, o servidor recusa o membro.
        naEdt(() -> clienteBia.controlador.desafixar(regra));
        clienteBia.visao.esperar(e -> e.startsWith("erro Somente a administração"));
        assertEquals(1, servicos.mensagens().fixadas(ana, grupo).size());

        // Abrir a fixada destaca a mensagem.
        naEdt(() -> {
            clienteBia.controlador.abrirMensagem(grupo, regra);
            return null;
        });
        clienteBia.visao.esperar(("destacar " + grupo + " " + regra)::equals);

        naEdt(() -> clienteAna.controlador.desafixar(regra));
        clienteBia.visao.esperar(("fixadas " + grupo + " []")::equals);
    }
}
