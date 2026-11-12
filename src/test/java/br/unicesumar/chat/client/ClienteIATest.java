package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Cliente e IA: aviso de resposta truncada e memória do personagem (ver e apagar). */
class ClienteIATest {

    @TempDir
    Path pasta;

    private final ExecutorService edt = Executors.newSingleThreadExecutor();
    private final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
    private ServidorTeste servidor;
    private ControladorConversas controlador;
    private final AtomicReference<ConexaoServidor.Ouvinte> ouvinte = new AtomicReference<>();

    private final ControladorConversas.Visao visao = new ControladorConversas.Visao() {
        @Override public void reiniciarConversa(long id, String usuario) { }
        @Override public void exibirLinha(long id, String linha) { eventos.add("linha " + id + " " + linha); }
        @Override public boolean historicoConcluido(long id) { return true; }
        @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { }
        @Override public void mensagemEnviada() { }
        @Override public void usuarioIdentificado(String apelido) { eventos.add("identificado " + apelido); }
        @Override public void autenticacaoRecusada(String mensagem) { eventos.add("recusada " + mensagem); }
        @Override public void erroConversa(String mensagem) { eventos.add("aviso " + mensagem); }
        @Override public void exibirConversa(ControladorConversas.Conversa c) { eventos.add("exibir " + c.id() + " " + c.tipo()); }
        @Override public void estadoIA(long id, boolean gerando, String d) { eventos.add("ia " + id + " " + gerando); }
    };

    private <T> T naEdt(Callable<T> acao) throws Exception {
        return edt.submit(acao).get(10, TimeUnit.SECONDS);
    }

    private String esperar(String prefixo) throws InterruptedException {
        while (true) {
            String evento = eventos.poll(10, TimeUnit.SECONDS);
            assertNotNull(evento, "Evento não chegou: " + prefixo);
            if (evento.startsWith(prefixo)) {
                return evento;
            }
        }
    }

    private void criarControlador() throws Exception {
        controlador = naEdt(() -> new ControladorConversas(visao,
                new NotificacoesSonoras(new ReprodutorSom() {
                    @Override public void tocar(SomSakura som) { }
                    @Override public void fechar() { }
                }, System::currentTimeMillis),
                edt::execute, o -> {
                    ouvinte.set(o);
                    return new ConexaoServidor(o);
                }));
    }

    @AfterEach
    void encerrar() throws Exception {
        if (controlador != null) {
            naEdt(() -> {
                controlador.encerrar();
                return null;
            });
        }
        if (servidor != null) {
            servidor.close();
        }
        edt.shutdown();
        assertTrue(edt.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void respostaTruncadaGeraAvisoNaoModalEEncerraODigitando() throws Exception {
        criarControlador();
        ouvinte.get().identidadeConfirmada("Ana");
        Conversa naruto = new Conversa(9, Conversa.Tipo.IA, "Naruto", List.of(
                new Conversa.Participante(1, "Ana", "sakura", Conversa.Papel.ADMIN)));
        ouvinte.get().eventoRecebido(ProtocoloJson.lerEvento(ProtocoloJson.eventoConversas(null,
                List.of(ProtocoloJson.ConversaJson.de(naruto, null, 0)))));
        ouvinte.get().eventoRecebido(ProtocoloJson.lerEvento(ProtocoloJson.eventoEstadoIA(9, "GERANDO", "Qwen")));
        esperar("ia 9 true");

        ouvinte.get().eventoRecebido(ProtocoloJson.lerEvento(ProtocoloJson.eventoEstadoIA(9, "TRUNCADA",
                "A resposta foi interrompida pelo limite de tamanho (marcada com […]).")));
        assertEquals("aviso Naruto: A resposta foi interrompida pelo limite de tamanho (marcada com […]).",
                esperar("aviso "));
        esperar("ia 9 false");
    }

    @Test
    void memoriaDoPersonagemPodeSerVistaEApagadaPeloDono() throws Exception {
        servidor = new ServidorTeste(pasta.resolve("cliente-ia.db"));
        criarControlador();
        naEdt(() -> controlador.conectar(ServidorTeste.ENDERECO, servidor.porta(),
                new ConexaoServidor.Cadastrar("ana", "Ana", "senha-segura-1".toCharArray())));
        esperar("identificado Ana");
        esperar("linha 1 SISTEMA: Conectado como");

        assertFalse(naEdt(() -> controlador.verMemoria(m -> fail("Geral não tem memória"))),
                "Só em conversas de IA");

        naEdt(() -> controlador.criarPersonagem("Naruto", "animado", ""));
        String exibida;
        do {
            exibida = esperar("exibir ");
        } while (!exibida.endsWith(" IA"));
        long naruto = Long.parseLong(exibida.split(" ")[1]);
        assertEquals(Conversa.Tipo.IA, naEdt(() -> controlador.conversaAtiva().tipo()));

        BlockingQueue<ControladorConversas.MemoriaInfo> memorias = new LinkedBlockingQueue<>();
        assertTrue(naEdt(() -> controlador.verMemoria(memorias::add)));
        ControladorConversas.MemoriaInfo vista = memorias.poll(10, TimeUnit.SECONDS);
        assertNotNull(vista);
        assertEquals(naruto, vista.conversaId());
        assertEquals("", vista.resumo());
        assertNull(vista.atualizadaEm());
        assertEquals(DialogoMemoria.SEM_MEMORIA, DialogoMemoria.texto(vista));

        assertTrue(naEdt(() -> controlador.apagarMemoria(memorias::add)));
        ControladorConversas.MemoriaInfo apagada = memorias.poll(10, TimeUnit.SECONDS);
        assertNotNull(apagada);
        assertEquals("", apagada.resumo());
        assertNotNull(apagada.atualizadaEm(), "A exclusão fica registrada");
    }
}
