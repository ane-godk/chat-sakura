package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(
            Instant.parse("2026-11-03T15:00:00Z"),
            ZoneOffset.UTC
    );

    private static class RepositorioFalso
            implements MensagemRepository {

        private final List<Mensagem> mensagens = new ArrayList<>();

        @Override
        public synchronized Mensagem salvar(Mensagem mensagem) {

            Mensagem salva = mensagem.comId(
                    (long) mensagens.size() + 1
            );

            mensagens.add(salva);

            return salva;
        }

        @Override
        public synchronized List<Mensagem> buscarUltimas(int quantidade) {

            if (quantidade < 0) {
                throw new IllegalArgumentException();
            }

            int inicio = Math.max(
                    0,
                    mensagens.size() - quantidade
            );

            return new ArrayList<>(
                    mensagens.subList(inicio, mensagens.size())
            );
        }
    }

    private static class ClienteFalso
            implements ConexaoCliente {

        private final Usuario usuario;
        private final List<Mensagem> recebidas = new ArrayList<>();

        private boolean falhar;

        ClienteFalso(String nome) {
            this.usuario = new Usuario(nome);
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public void enviar(Mensagem mensagem) {

            if (falhar) {
                throw new IllegalStateException(
                        "Falha simulada."
                );
            }

            recebidas.add(mensagem);
        }
    }

    private ChatService criarServico() {

        return new ChatService(
                new RepositorioFalso(),
                new ClientRegistry(),
                RELOGIO_FIXO
        );
    }

    @Test
    void deveDistribuirMensagemParaTodosOsClientes() {

        ChatService servico = criarServico();

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso caio = new ClienteFalso("Caio");

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);
        servico.registrarCliente(caio);

        servico.enviarMensagem(
                ana.getUsuario(),
                "Olá, pessoal!"
        );

        assertEquals(1, ana.recebidas.size());
        assertEquals(1, bia.recebidas.size());
        assertEquals(1, caio.recebidas.size());
    }

    @Test
    void deveContinuarBroadcastQuandoUmClienteFalhar() {

        ChatService servico = criarServico();

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso caio = new ClienteFalso("Caio");

        bia.falhar = true;

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);
        servico.registrarCliente(caio);

        assertDoesNotThrow(() ->
                servico.enviarMensagem(
                        ana.getUsuario(),
                        "Mensagem de teste"
                )
        );

        assertEquals(1, ana.recebidas.size());
        assertEquals(0, bia.recebidas.size());
        assertEquals(1, caio.recebidas.size());
    }

    @Test
    void deveRejeitarUsuarioDuplicado() {

        ChatService servico = criarServico();

        servico.registrarCliente(
                new ClienteFalso("Ana")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> servico.registrarCliente(
                        new ClienteFalso("ana")
                )
        );
    }

    @Test
    void deveSalvarMensagemAntesDeDistribuir() {

        RepositorioFalso repositorio =
                new RepositorioFalso();

        ChatService servico = new ChatService(
                repositorio,
                new ClientRegistry(),
                RELOGIO_FIXO
        );

        ClienteFalso ana = new ClienteFalso("Ana");

        servico.registrarCliente(ana);

        Mensagem salva = servico.enviarMensagem(
                ana.getUsuario(),
                "Mensagem persistida"
        );

        assertNotNull(salva.getId());
        assertEquals(1, repositorio.mensagens.size());

        assertEquals(
                salva,
                ana.recebidas.get(0)
        );
    }

    @Test
    void deveRemoverClienteDoRegistro() {

        ChatService servico = criarServico();

        ClienteFalso ana = new ClienteFalso("Ana");

        servico.registrarCliente(ana);

        assertEquals(1, servico.contarClientes());

        servico.removerCliente(ana);

        assertEquals(0, servico.contarClientes());
    }

    @Test
    void naoDeveDistribuirMensagemQuandoPersistenciaFalhar() {

        MensagemRepository repositorioComFalha =
                new RepositorioFalso() {
                    @Override
                    public synchronized Mensagem salvar(Mensagem mensagem) {
                        throw new IllegalStateException(
                                "Banco indisponível."
                        );
                    }
                };

        ChatService servico = new ChatService(
                repositorioComFalha,
                new ClientRegistry(),
                RELOGIO_FIXO
        );

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);

        assertThrows(
                IllegalStateException.class,
                () -> servico.enviarMensagem(
                        ana.getUsuario(),
                        "Não deve ser distribuída"
                )
        );

        assertTrue(ana.recebidas.isEmpty());
        assertTrue(bia.recebidas.isEmpty());
    }

    @Test
    void naoDevePersistirNemDistribuirMensagemInvalida() {

        RepositorioFalso repositorio = new RepositorioFalso();

        ChatService servico = new ChatService(
                repositorio,
                new ClientRegistry(),
                RELOGIO_FIXO
        );

        ClienteFalso ana = new ClienteFalso("Ana");
        servico.registrarCliente(ana);

        assertThrows(
                IllegalArgumentException.class,
                () -> servico.enviarMensagem(ana.getUsuario(), "   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> servico.enviarMensagem(
                        ana.getUsuario(),
                        "a".repeat(501)
                )
        );

        assertTrue(repositorio.mensagens.isEmpty());
        assertTrue(ana.recebidas.isEmpty());
    }

    @Test
    void deveRespeitarLimiteDoHistorico() {

        RepositorioFalso repositorio = new RepositorioFalso();

        ChatService servico = new ChatService(
                repositorio,
                new ClientRegistry(),
                RELOGIO_FIXO
        );

        Usuario ana = new Usuario("Ana");

        for (int i = 1; i <= 30; i++) {
            servico.enviarMensagem(ana, "Mensagem " + i);
        }

        List<Mensagem> ultimas = servico.buscarHistorico(20);

        assertEquals(20, ultimas.size());
        assertEquals("Mensagem 11", ultimas.get(0).getConteudo());
        assertEquals("Mensagem 30", ultimas.get(19).getConteudo());

        List<Mensagem> naConexao = servico.registrarClienteComHistorico(
                new ClienteFalso("Bia"),
                5
        );

        assertEquals(5, naConexao.size());
        assertEquals("Mensagem 26", naConexao.get(0).getConteudo());
        assertEquals("Mensagem 30", naConexao.get(4).getConteudo());

        assertTrue(servico.buscarHistorico(0).isEmpty());
        assertEquals(30, servico.buscarHistorico(100).size());
    }
}