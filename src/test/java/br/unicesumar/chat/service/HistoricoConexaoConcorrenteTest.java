package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
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
 * Verifica que clientes que entram durante broadcasts simultâneos
 * recebem histórico + mensagens ao vivo sem duplicatas, perdas
 * ou inversões de ordem.
 */
class HistoricoConexaoConcorrenteTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-11-03T15:00:00Z"),
            ZoneOffset.UTC
    );

    private static final int QUANTIDADE_HISTORICO = 20;

    private static final class ClienteFalso
            implements ConexaoCliente {

        private final Usuario usuario;
        private final List<Mensagem> recebidas = new ArrayList<>();

        ClienteFalso(String nome) {
            this.usuario = new Usuario(nome);
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public synchronized void enviar(Mensagem mensagem) {
            recebidas.add(mensagem);
        }

        synchronized List<Mensagem> mensagensRecebidas() {
            return new ArrayList<>(recebidas);
        }
    }

    private record Entrada(
            ClienteFalso cliente,
            List<Mensagem> historico
    ) {
    }

    @Test
    void clientesQueEntramDuranteBroadcastNaoPerdemNemDuplicamMensagens()
            throws Exception {

        ChatService servico = new ChatService(
                new MensagemRepositoryEmMemoria(),
                new ClientRegistry(),
                RELOGIO
        );

        Usuario remetente = new Usuario("Remetente");

        int remetentes = 4;
        int mensagensPorRemetente = 250;
        int totalMensagens = remetentes * mensagensPorRemetente;
        int clientesEntrando = 30;

        ExecutorService executor =
                Executors.newFixedThreadPool(remetentes + clientesEntrando);

        CountDownLatch inicio = new CountDownLatch(1);

        List<Future<?>> envios = new ArrayList<>();
        List<Future<Entrada>> entradas = new ArrayList<>();

        try {

            for (int i = 0; i < remetentes; i++) {

                final int numero = i;

                envios.add(executor.submit(() -> {
                    inicio.await();

                    for (int j = 0; j < mensagensPorRemetente; j++) {
                        servico.enviarMensagem(
                                remetente,
                                "R" + numero + " M" + j
                        );
                    }
                    return null;
                }));
            }

            for (int i = 0; i < clientesEntrando; i++) {

                ClienteFalso cliente = new ClienteFalso("Cliente" + i);

                entradas.add(executor.submit(() -> {
                    inicio.await();

                    List<Mensagem> historico =
                            servico.registrarClienteComHistorico(
                                    cliente,
                                    QUANTIDADE_HISTORICO
                            );

                    return new Entrada(cliente, historico);
                }));
            }

            inicio.countDown();
            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(30, TimeUnit.SECONDS),
                    "As tarefas não terminaram no prazo."
            );

            for (Future<?> envio : envios) {
                envio.get();
            }

            for (Future<Entrada> futuro : entradas) {

                Entrada entrada = futuro.get();

                List<Mensagem> visao = new ArrayList<>(entrada.historico());
                visao.addAll(entrada.cliente().mensagensRecebidas());

                assertTrue(
                        entrada.historico().size() <= QUANTIDADE_HISTORICO
                );

                if (visao.isEmpty()) {
                    continue;
                }

                // IDs consecutivos: sem lacunas (perdas), sem repetição
                // (duplicatas) e em ordem crescente.
                for (int k = 1; k < visao.size(); k++) {
                    assertEquals(
                            visao.get(k - 1).getId() + 1,
                            visao.get(k).getId(),
                            "Sequência quebrada para "
                                    + entrada.cliente().getUsuario()
                    );
                }

                // A visão do cliente termina na última mensagem enviada.
                assertEquals(
                        (long) totalMensagens,
                        visao.get(visao.size() - 1).getId()
                );

                // Se houve mensagens antes da entrada, o histórico
                // traz as mais recentes até o limite.
                long primeiraAoVivo = entrada.cliente()
                        .mensagensRecebidas()
                        .stream()
                        .findFirst()
                        .map(Mensagem::getId)
                        .orElse((long) totalMensagens + 1);

                long esperadoNoHistorico = Math.min(
                        QUANTIDADE_HISTORICO,
                        primeiraAoVivo - 1
                );

                assertEquals(
                        esperadoNoHistorico,
                        entrada.historico().size()
                );
            }

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void naoDeveRegistrarClienteDuplicadoNemRetornarHistorico() {

        ChatService servico = new ChatService(
                new MensagemRepositoryEmMemoria(),
                new ClientRegistry(),
                RELOGIO
        );

        servico.registrarClienteComHistorico(
                new ClienteFalso("Ana"),
                QUANTIDADE_HISTORICO
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> servico.registrarClienteComHistorico(
                        new ClienteFalso("ana"),
                        QUANTIDADE_HISTORICO
                )
        );

        assertEquals(1, servico.contarClientes());
    }
}
