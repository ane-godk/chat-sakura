package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Sessões identificadas pelo id da conta: substituição segura (S5). */
class SubstituicaoSessaoTest {

    private static final class Sessao implements ConexaoCliente {
        final Usuario usuario;
        final List<Mensagem> recebidas = Collections.synchronizedList(new ArrayList<>());
        final List<String> encerramentos = Collections.synchronizedList(new ArrayList<>());

        Sessao(Usuario usuario) {
            this.usuario = usuario;
        }

        @Override public Usuario getUsuario() { return usuario; }
        @Override public void enviar(Mensagem m) { recebidas.add(m); }
        @Override public void encerrar(String motivo) { encerramentos.add(motivo); }
    }

    private final ClientRegistry registro = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repositorio = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repositorio, registro, Clock.systemDefaultZone());

    private static final Usuario ANA = new Usuario(1L, "Ana");
    private static final Usuario BIA = new Usuario(2L, "Bia");

    private long avisos(Sessao sessao, TipoMensagem tipo) {
        return sessao.recebidas.stream().filter(m -> m.getTipo() == tipo).count();
    }

    @Test
    void novoLoginDeveSubstituirASessaoAntigaSemAvisosDuplicados() {
        Sessao bia = new Sessao(BIA);
        chat.conectarCliente(bia, 10);

        Sessao antiga = new Sessao(ANA);
        chat.conectarCliente(antiga, 10);
        assertEquals(1, avisos(bia, TipoMensagem.ENTRADA));

        Sessao nova = new Sessao(new Usuario(1L, "Ana"));
        chat.conectarCliente(nova, 10);

        assertEquals(List.of(ChatService.MOTIVO_SUBSTITUICAO), antiga.encerramentos, "A antiga é avisada e encerrada");
        assertTrue(nova.encerramentos.isEmpty());
        assertEquals(2, chat.contarClientes(), "Uma sessão por conta");
        assertTrue(registro.contem(nova));
        assertFalse(registro.contem(antiga));

        assertEquals(1, avisos(bia, TipoMensagem.ENTRADA), "Sem nova entrada: Ana continuou presente");
        assertEquals(0, avisos(bia, TipoMensagem.SAIDA), "Sem saída na substituição");

        // O fim da sessão antiga não remove a nova nem gera aviso.
        chat.desconectarCliente(antiga);
        assertTrue(registro.contem(nova), "Registro da sessão nova preservado");
        assertEquals(0, avisos(bia, TipoMensagem.SAIDA));

        chat.desconectarCliente(nova);
        assertEquals(1, avisos(bia, TipoMensagem.SAIDA), "Saída real: um único aviso");
    }

    @Test
    void sessaoSubstituidaNaoDeveConseguirEnviar() throws Exception {
        Sessao bia = new Sessao(BIA);
        chat.conectarCliente(bia, 10);
        Sessao antiga = new Sessao(ANA);
        chat.conectarCliente(antiga, 10);
        chat.enviarMensagem(ANA, "antes", antiga);

        Sessao nova = new Sessao(new Usuario(1L, "Ana"));
        chat.conectarCliente(nova, 10);

        assertThrows(SessaoEncerradaException.class, () -> chat.enviarMensagem(ANA, "depois", antiga));
        chat.enviarMensagem(ANA, "pela nova", nova);

        List<String> salvas = repositorio.buscarUltimas(50).stream()
                .filter(m -> m.getTipo() == TipoMensagem.MENSAGEM).map(Mensagem::getConteudo).toList();
        assertEquals(List.of("antes", "pela nova"), salvas, "Nada da sessão antiga após a substituição");
    }

    @Test
    void visitantesContinuamComARegraDoNomeUnico() {
        chat.conectarCliente(new Sessao(new Usuario("Caio")), 10);
        assertThrows(IllegalArgumentException.class, () -> chat.conectarCliente(new Sessao(new Usuario("caio")), 10),
                "Visitante não substitui: o nome continua único");

        // Uma conta com o mesmo nome de outra sessão também não substitui.
        assertThrows(IllegalArgumentException.class,
                () -> chat.conectarCliente(new Sessao(new Usuario(9L, "Caio")), 10));
        assertEquals(1, chat.contarClientes());
    }

    @Test
    void reconexoesRapidasDevemTerminarComUmaUnicaSessao() throws Exception {
        Sessao bia = new Sessao(BIA);
        chat.conectarCliente(bia, 10);

        ExecutorService threads = Executors.newFixedThreadPool(8);
        List<Sessao> sessoes = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 40; i++) {
            threads.execute(() -> {
                Sessao s = new Sessao(new Usuario(1L, "Ana"));
                sessoes.add(s);
                chat.conectarCliente(s, 10);
            });
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(2, chat.contarClientes(), "Bia e uma única sessão da Ana");
        long ativas = sessoes.stream().filter(registro::contem).count();
        long encerradas = sessoes.stream().filter(s -> !s.encerramentos.isEmpty()).count();
        assertEquals(1, ativas);
        assertEquals(39, encerradas, "Todas as anteriores foram encerradas");
        assertEquals(1, avisos(bia, TipoMensagem.ENTRADA), "Uma entrada, sem duplicatas");
    }
}
