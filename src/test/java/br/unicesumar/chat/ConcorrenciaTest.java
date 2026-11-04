
package br.unicesumar.chat;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConcorrenciaTest {

    @Test
    void deveSalvarTodasAsMensagensConcorrentemente()
            throws Exception {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        int quantidadeThreads = 10;
        int mensagensPorThread = 100;
        int totalEsperado = quantidadeThreads * mensagensPorThread;

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

                        Mensagem mensagem = new Mensagem(
                                new Usuario("Usuario" + numeroThread),
                                "Mensagem " + j,
                                LocalDateTime.of(2026, 11, 3, 15, 0)
                        );

                        repositorio.salvar(mensagem);
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

            // Propaga possíveis erros ocorridos nas threads.
            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }

            List<Mensagem> mensagens =
                    repositorio.buscarUltimas(totalEsperado);

            assertEquals(totalEsperado, mensagens.size());

            // Os IDs devem ser únicos e sequenciais.
            for (int i = 0; i < totalEsperado; i++) {

                assertEquals(
                        (long) i + 1,
                        mensagens.get(i).getId()
                );
            }

        } finally {
            executor.shutdownNow();
        }
    }
}
