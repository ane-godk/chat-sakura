package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o broadcast feito dentro de synchronized (clientes) em conjunto
 * com registros e remoções simultâneos.
 */
class BroadcastSincronizacaoTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-11-03T15:00:00Z"),
            ZoneOffset.UTC
    );

    private static final class ClienteFalso
            implements ConexaoCliente {

        private final Usuario usuario;
        private final List<Mensagem> recebidas = new ArrayList<>();

        private boolean removido;
        private boolean recebeuAposRemocao;

        ClienteFalso(String nome) {
            this.usuario = new Usuario(nome);
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public synchronized void enviar(Mensagem mensagem) {
            if (removido) {
                recebeuAposRemocao = true;
            }
            recebidas.add(mensagem);
        }

        synchronized void marcarRemovido() {
            removido = true;
        }

        synchronized boolean recebeuAposRemocao() {
            return recebeuAposRemocao;
        }

        synchronized List<Mensagem> mensagensRecebidas() {
            return new ArrayList<>(recebidas);
        }
    }

    private static ChatService criarServico() {
        return new ChatService(
                new MensagemRepositoryEmMemoria(),
                new ClientRegistry(),
                RELOGIO
        );
    }

    private static void assertIdsCrescentes(List<Mensagem> mensagens) {
        for (int i = 1; i < mensagens.size(); i++) {
            assertTrue(
                    mensagens.get(i).getId() > mensagens.get(i - 1).getId(),
                    "Mensagens fora de ordem ou duplicadas."
            );
        }
    }

    @Test
    void clienteRemovidoNaoDeveReceberNovasMensagens() {

        ChatService servico = criarServico();

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);

        servico.enviarMensagem(ana.getUsuario(), "Antes");
        servico.removerCliente(bia);
        servico.enviarMensagem(ana.getUsuario(), "Depois");

        assertEquals(2, ana.mensagensRecebidas().size());
        assertEquals(1, bia.mensagensRecebidas().size());
        assertEquals("Antes", bia.mensagensRecebidas().get(0).getConteudo());
    }

    @Test
    void transmitirDeveIsolarFalhaDeUmCliente() {

        ClientRegistry registro = new ClientRegistry();

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso caio = new ClienteFalso("Caio");

        registro.registrar(ana);
        registro.registrar(new ConexaoCliente() {
            @Override
            public Usuario getUsuario() {
                return new Usuario("Bia");
            }

            @Override
            public void enviar(Mensagem mensagem) throws Exception {
                throw new java.io.IOException("Conexão encerrada.");
            }
        });
        registro.registrar(caio);

        Mensagem mensagem = new Mensagem(
                1L,
                new Usuario("Ana"),
                "Olá",
                LocalDateTime.of(2026, 11, 3, 15, 0)
        );

        assertDoesNotThrow(() -> registro.transmitir(mensagem));

        assertEquals(List.of(mensagem), ana.mensagensRecebidas());
        assertEquals(List.of(mensagem), caio.mensagensRecebidas());
    }

    @Test
    void deveSuportarRegistroRemocaoEEnvioSimultaneos()
            throws Exception {

        ChatService servico = criarServico();

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);

        int remetentes = 4;
        int mensagensPorRemetente = 200;
        int totalMensagens = remetentes * mensagensPorRemetente;

        int threadsEntradaSaida = 6;
        int ciclosPorThread = 50;

        ExecutorService executor = Executors.newFixedThreadPool(
                remetentes + threadsEntradaSaida
        );

        CountDownLatch inicio = new CountDownLatch(1);

        List<Future<?>> tarefas = new ArrayList<>();
        List<ClienteFalso> temporarios =
                java.util.Collections.synchronizedList(new ArrayList<>());

        try {

            for (int i = 0; i < remetentes; i++) {

                final int numero = i;

                tarefas.add(executor.submit(() -> {
                    inicio.await();

                    for (int j = 0; j < mensagensPorRemetente; j++) {
                        servico.enviarMensagem(
                                ana.getUsuario(),
                                "R" + numero + " M" + j
                        );
                    }
                    return null;
                }));
            }

            for (int i = 0; i < threadsEntradaSaida; i++) {

                final int numero = i;

                tarefas.add(executor.submit(() -> {
                    inicio.await();

                    for (int c = 0; c < ciclosPorThread; c++) {

                        ClienteFalso temporario = new ClienteFalso(
                                "T" + numero + "_" + c
                        );

                        servico.registrarClienteComHistorico(temporario, 20);
                        servico.removerCliente(temporario);
                        temporario.marcarRemovido();

                        temporarios.add(temporario);
                    }
                    return null;
                }));
            }

            inicio.countDown();
            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(30, TimeUnit.SECONDS),
                    "As tarefas não terminaram no prazo."
            );

            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }

            // Clientes permanentes recebem tudo, em ordem.
            assertEquals(totalMensagens, ana.mensagensRecebidas().size());
            assertEquals(totalMensagens, bia.mensagensRecebidas().size());
            assertIdsCrescentes(ana.mensagensRecebidas());
            assertEquals(
                    ana.mensagensRecebidas(),
                    bia.mensagensRecebidas()
            );

            // Clientes temporários: ordem preservada e nenhuma
            // mensagem entregue após a remoção.
            assertEquals(
                    threadsEntradaSaida * ciclosPorThread,
                    temporarios.size()
            );

            for (ClienteFalso temporario : temporarios) {
                assertFalse(temporario.recebeuAposRemocao());
                assertIdsCrescentes(temporario.mensagensRecebidas());
            }

            assertEquals(2, servico.contarClientes());

        } finally {
            executor.shutdownNow();
        }
    }
}
