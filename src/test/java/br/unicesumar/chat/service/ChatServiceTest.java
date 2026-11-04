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
}