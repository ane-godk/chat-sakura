
package br.unicesumar.chat;

import br.unicesumar.chat.domain.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConcorrenciaTest {

    @Test
    void deveSalvarMilMensagensSemPerdas()
            throws Exception {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        int quantidadeThreads = 10;
        int mensagensPorThread = 100;

        int totalEsperado =
                quantidadeThreads * mensagensPorThread;

        ExecutorService executor =
                Executors.newFixedThreadPool(quantidadeThreads);

        List<Callable<Void>> tarefas = new ArrayList<>();

        for (int i = 0; i < quantidadeThreads; i++) {

            final int numeroThread = i;

            tarefas.add(() -> {

                for (int j = 0; j < mensagensPorThread; j++) {

                    Mensagem mensagem = new Mensagem(
                            "Usuario-" + numeroThread,
                            "Mensagem-" + j
                    );

                    repositorio.salvar(mensagem);
                }

                return null;
            });
        }

        List<Future<Void>> resultados;

        try {

            resultados = new ArrayList<>();

            for (Callable<Void> tarefa : tarefas) {
                resultados.add(executor.submit(tarefa));
            }

        } finally {
            executor.shutdown();
        }

        boolean terminou = executor.awaitTermination(
                30,
                TimeUnit.SECONDS
        );

        if (!terminou) {
            executor.shutdownNow();
        }

        assertTrue(
                terminou,
                "As threads não terminaram no tempo esperado."
        );

        for (Future<Void> resultado : resultados) {
            resultado.get();
        }

        assertEquals(
                totalEsperado,
                repositorio.contarMensagens(),
                "Nenhuma mensagem pode ser perdida."
        );
    }
}
