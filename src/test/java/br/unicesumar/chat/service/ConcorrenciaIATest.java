package br.unicesumar.chat.service;

import br.unicesumar.chat.ia.ConfiguracaoQwen;
import br.unicesumar.chat.ia.ParametrosIA;
import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorQwen;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Fila limitada, uma geração por vez no modo local, falhas, tempo esgotado e respostas truncadas. */
class ConcorrenciaIATest {

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private final List<String> estados = Collections.synchronizedList(new ArrayList<>());

    /** Provedor controlável: bloqueia até liberar e devolve "resposta para: <pergunta>". */
    private static class Controlado implements ProvedorIA {
        final ParametrosIA parametros;
        final CountDownLatch liberar = new CountDownLatch(1);
        final CountDownLatch primeira = new CountDownLatch(1);
        final CountDownLatch duas = new CountDownLatch(2);
        final AtomicInteger simultaneas = new AtomicInteger();
        final AtomicInteger pico = new AtomicInteger();
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());

        Controlado(ParametrosIA parametros) {
            this.parametros = parametros;
        }

        @Override
        public String gerar(PedidoIA pedido) {
            pico.accumulateAndGet(simultaneas.incrementAndGet(), Math::max);
            threads.add(Thread.currentThread().getName());
            primeira.countDown();
            duas.countDown();
            try {
                liberar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                simultaneas.decrementAndGet();
            }
            return "resposta para: " + pedido.historico().get(pedido.historico().size() - 1).texto();
        }

        @Override public String descricao() { return "controlado"; }
        @Override public ParametrosIA parametros() { return parametros; }
    }

    private Usuario conta(String login) throws Exception {
        return servicos.autenticacao().cadastrar(login, login.toUpperCase(), ("senha-" + login).toCharArray())
                .comoUsuario();
    }

    private void montar(ProvedorIA provedor) {
        servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("ia.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), provedor);
    }

    /** Sessão registrada que guarda os estados da IA recebidos. */
    private void conectar(Usuario usuario) throws Exception {
        var campo = ServicoConversas.class.getDeclaredField("registro");
        campo.setAccessible(true);
        ((ClientRegistry) campo.get(servicos.conversas())).registrar(new ConexaoCliente() {
            @Override public Usuario getUsuario() { return usuario; }
            @Override public void enviar(Mensagem m) { }
            @Override public void estadoIA(long id, String estado, String msg) { estados.add(id + ":" + estado + ":" + msg); }
        });
    }

    private void enviar(Usuario usuario, long conversa, String texto) throws Exception {
        servicos.ia().verificarEnvio(usuario, conversa);
        servicos.ia().aposMensagem(usuario, servicos.conversas().enviar(usuario, conversa, texto));
    }

    private List<String> textos(Usuario usuario, long conversa) throws Exception {
        return servicos.conversas().historico(usuario, conversa, null, 50).stream().map(Mensagem::getConteudo).toList();
    }

    @Test
    void filaCheiaRecusaAntesDeGravarERespostasNaoSeMisturam() throws Exception {
        Controlado provedor = new Controlado(new ParametrosIA(4_096, 320, 16, 1, 1));
        montar(provedor);
        Usuario ana = conta("ana");
        Usuario bia = conta("bia");
        Usuario caio = conta("caio");
        long a = servicos.ia().criarPersonagem(ana, "Naruto", "animado", "").conversa().getId();
        long b = servicos.ia().criarPersonagem(bia, "Sakura", "gentil", "").conversa().getId();
        long c = servicos.ia().criarPersonagem(caio, "Kakashi", "calmo", "").conversa().getId();

        enviar(ana, a, "pergunta da Ana");
        assertTrue(provedor.primeira.await(5, TimeUnit.SECONDS), "Ana em geração");
        enviar(bia, b, "pergunta da Bia");                  // aguarda na fila (1 vaga)

        ConversaException cheia = assertThrows(ConversaException.class, () -> enviar(caio, c, "pergunta do Caio"));
        assertEquals(ConversaException.Codigo.LIMITE, cheia.getCodigo());
        assertEquals(ServicoIA.MENSAGEM_OCUPADA, cheia.getMessage());
        assertTrue(textos(caio, c).isEmpty(), "Recusada antes de gravar: nada perdido nem pendente");

        provedor.liberar.countDown();
        assertTrue(servicos.ia().aguardar(10_000));

        assertEquals(List.of("pergunta da Ana", "resposta para: pergunta da Ana"), textos(ana, a));
        assertEquals(List.of("pergunta da Bia", "resposta para: pergunta da Bia"), textos(bia, b));
        assertEquals(1, provedor.pico.get(), "Uma geração por vez no modo local");
        assertTrue(provedor.threads.stream().allMatch("sakura-ia-1"::equals));

        enviar(caio, c, "pergunta do Caio");               // com a fila livre, aceita
        assertTrue(servicos.ia().aguardar(10_000));
        assertEquals(List.of("pergunta do Caio", "resposta para: pergunta do Caio"), textos(caio, c));
    }

    @Test
    void provedorRemotoMantemDuasGeracoesSimultaneas() throws Exception {
        Controlado provedor = new Controlado(ParametrosIA.REMOTO);
        montar(provedor);
        Usuario ana = conta("ana");
        long a = servicos.ia().criarPersonagem(ana, "Naruto", "animado", "").conversa().getId();
        long b = servicos.ia().criarPersonagem(ana, "Sakura", "gentil", "").conversa().getId();

        enviar(ana, a, "um");
        enviar(ana, b, "dois");
        // A segunda geração começa sem esperar a primeira (duas threads no remoto).
        assertTrue(provedor.duas.await(5, TimeUnit.SECONDS), "As duas gerações em andamento ao mesmo tempo");
        provedor.liberar.countDown();
        assertTrue(servicos.ia().aguardar(10_000));
        assertEquals(2, provedor.pico.get());
    }

    @Test
    void tempoEsgotadoLiberaAConversaEInformaOErro() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        montar(new ProvedorIA() {
            @Override
            public String gerar(PedidoIA pedido) throws FalhaIA {
                if (chamadas.incrementAndGet() == 1) {
                    throw new FalhaIA(FalhaIA.Codigo.TEMPO_ESGOTADO, "O Qwen demorou demais para responder.");
                }
                return "agora foi";
            }

            @Override public String descricao() { return "teste"; }
        });
        Usuario ana = conta("ana");
        conectar(ana);
        long naruto = servicos.ia().criarPersonagem(ana, "Naruto", "animado", "").conversa().getId();

        enviar(ana, naruto, "primeira");
        assertTrue(servicos.ia().aguardar(5_000));
        assertTrue(estados.contains(naruto + ":ERRO:O Qwen demorou demais para responder."), estados.toString());
        assertFalse(servicos.ia().gerando(naruto), "Estado de geração liberado");

        enviar(ana, naruto, "segunda");
        assertTrue(servicos.ia().aguardar(5_000));
        assertEquals(List.of("primeira", "segunda", "agora foi"), textos(ana, naruto), "Mensagem não perdida");
    }

    @Test
    void respostaTruncadaGanhaMarcaEAvisoProprio() throws Exception {
        montar(new ProvedorIA() {
            @Override public String gerar(PedidoIA pedido) { return "não usado"; }

            @Override
            public Resposta gerarResposta(PedidoIA pedido) {
                return new Resposta("Primeira frase completa. Segunda frase também. E a terceira foi cor", true);
            }

            @Override public String descricao() { return "teste"; }
        });
        Usuario ana = conta("ana");
        conectar(ana);
        long naruto = servicos.ia().criarPersonagem(ana, "Naruto", "animado", "").conversa().getId();

        enviar(ana, naruto, "Conte uma história");
        assertTrue(servicos.ia().aguardar(5_000));

        assertEquals("Primeira frase completa. Segunda frase também. […]", textos(ana, naruto).get(1),
                "Corte visível, sem frase pela metade");
        assertTrue(estados.contains(naruto + ":" + ServicoIA.TRUNCADA + ":" + ServicoIA.MENSAGEM_TRUNCADA), estados.toString());
    }

    @Test
    void ollamaParadoInformaFalhaSemTravarAConversa() throws Exception {
        ConfiguracaoQwen.Ajustes ajustes = ConfiguracaoQwen.Ajustes.padrao(ConfiguracaoQwen.Api.OLLAMA, true);
        montar(new ProvedorQwen(new ConfiguracaoQwen(URI.create("http://localhost:1/v1"), "qwen2.5:3b", null,
                Duration.ofSeconds(2), ajustes)));
        Usuario ana = conta("ana");
        conectar(ana);
        long naruto = servicos.ia().criarPersonagem(ana, "Naruto", "animado", "").conversa().getId();

        enviar(ana, naruto, "Oi?");
        assertTrue(servicos.ia().aguardar(10_000));

        assertTrue(estados.stream().anyMatch(e -> e.startsWith(naruto + ":ERRO:") && e.contains("Ollama")), estados.toString());
        assertFalse(servicos.ia().gerando(naruto));
        servicos.conversas().enviar(ana, 1, "Chat Geral segue funcionando");
    }
}
