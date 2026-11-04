
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChatConcorrenciaTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-11-03T15:00:00Z"),
            ZoneOffset.UTC
    );

    private static final class RepositorioFalso
            implements MensagemRepository {

        private final List<Mensagem> mensagens = new ArrayList<>();

        @Override
        public synchronized Mensagem salvar(Mensagem mensagem) {

            Mensagem persistida = mensagem.comId(
                    (long) mensagens.size() + 1
            );

            mensagens.add(persistida);
            return persistida;
        }

        @Override
        public synchronized List<Mensagem> buscarUltimas(int quantidade) {

            if (quantidade < 0) {
                throw new IllegalArgumentException(
                        "Quantidade inválida."
                );
            }

            int inicio = Math.max(
                    0,
                    mensagens.size() - quantidade
            );

            return new ArrayList<>(
                    mensagens.subList(inicio, mensagens.size())
            );
        }

        public synchronized int quantidade() {
            return mensagens.size();
        }
    }

    private static final class ClienteFalso
            implements ConexaoCliente {

        private final Usuario usuario;

        private final List<Mensagem> recebidas =
                new ArrayList<>();

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

        public synchronized List<Mensagem> mensagensRecebidas() {
            return new ArrayList<>(recebidas);
        }
    }

    @Test
    void devePersistirTodasAsMensagensSimultaneas()
            throws Exception {

        RepositorioFalso repositorio =
                new RepositorioFalso();

        ChatService servico = new ChatService(
                repositorio,
                new ClientRegistry(),
                RELOGIO
        );

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso caio = new ClienteFalso("Caio");

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);
        servico.registrarCliente(caio);

        int quantidadeThreads = 10;
        int mensagensPorThread = 100;
        int totalEsperado =
                quantidadeThreads * mensagensPorThread;

        ExecutorService executor =
                Executors.newFixedThreadPool(quantidadeThreads);

        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();

        try {

            for (int i = 0; i < quantidadeThreads; i++) {

                final int numeroThread = i;

                tarefas.add(executor.submit(() -> {

                    inicio.await();

                    for (int j = 0; j < mensagensPorThread; j++) {

                        servico.enviarMensagem(
                                ana.getUsuario(),
                                "Thread " + numeroThread
                                        + " mensagem " + j
                        );
                    }

                    return null;
                }));
            }

            inicio.countDown();
            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(
                            30,
                            TimeUnit.SECONDS
                    ),
                    "As tarefas não terminaram no prazo."
            );

            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }

            assertEquals(
                    totalEsperado,
                    repositorio.quantidade()
            );

            assertEquals(
                    totalEsperado,
                    ana.mensagensRecebidas().size()
            );

            assertEquals(
                    totalEsperado,
                    bia.mensagensRecebidas().size()
            );

            assertEquals(
                    totalEsperado,
                    caio.mensagensRecebidas().size()
            );

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void devePreservarOrdemDoBroadcast()
            throws Exception {

        RepositorioFalso repositorio =
                new RepositorioFalso();

        ChatService servico = new ChatService(
                repositorio,
                new ClientRegistry(),
                RELOGIO
        );

        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");

        servico.registrarCliente(ana);
        servico.registrarCliente(bia);

        int total = 200;

        ExecutorService executor =
                Executors.newFixedThreadPool(8);

        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();

        try {

            for (int i = 0; i < total; i++) {

                final int numero = i;

                tarefas.add(executor.submit(() -> {
                    inicio.await();

                    servico.enviarMensagem(
                            ana.getUsuario(),
                            "Mensagem " + numero
                    );

                    return null;
                }));
            }

            inicio.countDown();
            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(
                            30,
                            TimeUnit.SECONDS
                    )
            );

            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }

            List<Mensagem> mensagensAna =
                    ana.mensagensRecebidas();

            List<Mensagem> mensagensBia =
                    bia.mensagensRecebidas();

            assertEquals(total, mensagensAna.size());
            assertEquals(total, mensagensBia.size());

            for (int i = 0; i < total; i++) {

                assertEquals(
                        mensagensAna.get(i).getId(),
                        mensagensBia.get(i).getId()
                );

                if (i > 0) {
                    assertTrue(
                            mensagensAna.get(i).getId()
                                    > mensagensAna.get(i - 1).getId()
                    );
                }
            }

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deveRegistrarApenasUmUsuarioComNomeDuplicado()
            throws Exception {

        ClientRegistry registro = new ClientRegistry();

        int quantidadeThreads = 20;

        ExecutorService executor =
                Executors.newFixedThreadPool(quantidadeThreads);

        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Boolean>> tarefas = new ArrayList<>();

        try {

            for (int i = 0; i < quantidadeThreads; i++) {

                tarefas.add(executor.submit(() -> {

                    inicio.await();

                    try {
                        registro.registrar(
                                new ClienteFalso("Ana")
                        );
                        return true;

                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                }));
            }

            inicio.countDown();
            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(
                            30,
                            TimeUnit.SECONDS
                    )
            );

            int registrosAceitos = 0;

            for (Future<Boolean> tarefa : tarefas) {

                if (tarefa.get()) {
                    registrosAceitos++;
                }
            }

            assertEquals(1, registrosAceitos);
            assertEquals(1, registro.quantidade());

        } finally {
            executor.shutdownNow();
        }
    }
}
