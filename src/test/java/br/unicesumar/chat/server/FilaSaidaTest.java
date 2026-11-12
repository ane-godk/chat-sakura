package br.unicesumar.chat.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Fila de saída limitada com escritor único (S1). */
class FilaSaidaTest {

    /** Escritor que trava na primeira gravação até ser liberado (cliente que não lê). */
    private static final class EscritorTravado extends Writer {
        final CountDownLatch entrou = new CountDownLatch(1);
        final CountDownLatch liberar = new CountDownLatch(1);
        final StringWriter gravado = new StringWriter();

        @Override
        public void write(char[] buf, int off, int len) throws IOException {
            entrou.countDown();
            try {
                if (!liberar.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Não liberado.");
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            gravado.write(buf, off, len);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void filaCheiaDeveRecusarSemBloquearEAvisarUmaUnicaVez() throws Exception {
        EscritorTravado destino = new EscritorTravado();
        AtomicInteger transbordos = new AtomicInteger();
        FilaSaida fila = new FilaSaida(3, destino, "teste-saida", transbordos::incrementAndGet, () -> { });
        fila.iniciar();

        assertTrue(fila.enfileirar("primeira"));
        assertTrue(destino.entrou.await(5, TimeUnit.SECONDS), "O escritor está preso no socket");

        // Com o escritor travado, cabem exatamente 3 linhas pendentes.
        assertTrue(fila.enfileirar("a"));
        assertTrue(fila.enfileirar("b"));
        assertTrue(fila.enfileirar("c"));
        assertFalse(fila.enfileirar("d"), "Fila cheia: recusa imediata, sem esperar");
        assertFalse(fila.enfileirar("e"));

        assertEquals(1, transbordos.get(), "Transbordo sinalizado uma única vez");
        assertTrue(fila.transbordou());

        destino.liberar.countDown();
        assertTrue(fila.aguardar(5_000));
    }

    @Test
    void deveGravarNaOrdemMesmoComVariosProdutores() throws Exception {
        StringWriter destino = new StringWriter();
        FilaSaida fila = new FilaSaida(10_000, destino, "teste-ordem", () -> fail("não deve transbordar"), () -> { });
        fila.iniciar();

        ExecutorService produtores = Executors.newFixedThreadPool(4);
        for (int p = 0; p < 4; p++) {
            int produtor = p;
            produtores.execute(() -> {
                for (int i = 0; i < 500; i++) {
                    assertTrue(fila.enfileirar(produtor + ":" + i));
                }
            });
        }
        produtores.shutdown();
        assertTrue(produtores.awaitTermination(10, TimeUnit.SECONDS));

        fila.encerrar();
        assertTrue(fila.aguardar(5_000));

        String[] linhas = destino.toString().split(System.lineSeparator());
        assertEquals(2000, linhas.length);
        int[] ultimo = {-1, -1, -1, -1};
        for (String linha : linhas) {
            String[] partes = linha.split(":");
            int produtor = Integer.parseInt(partes[0]);
            int i = Integer.parseInt(partes[1]);
            assertEquals(ultimo[produtor] + 1, i, "Ordem de cada produtor preservada");
            ultimo[produtor] = i;
        }
    }

    @Test
    void encerrarDeveEntregarOQueJaEstavaNaFilaERecusarNovas() throws Exception {
        StringWriter destino = new StringWriter();
        FilaSaida fila = new FilaSaida(10, destino, "teste-fim", () -> { }, () -> { });
        assertTrue(fila.enfileirar("um"));
        assertTrue(fila.enfileirar("dois"));
        fila.encerrar();
        assertFalse(fila.enfileirar("depois"));

        fila.iniciar();
        assertTrue(fila.aguardar(5_000));
        assertEquals("um" + System.lineSeparator() + "dois" + System.lineSeparator(), destino.toString());
    }

    @Test
    void falhaDeEscritaDeveEncerrarAFila() throws Exception {
        CountDownLatch falhou = new CountDownLatch(1);
        Writer quebrado = new Writer() {
            @Override public void write(char[] b, int o, int l) throws IOException { throw new IOException("quebrou"); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        FilaSaida fila = new FilaSaida(10, quebrado, "teste-falha", () -> { }, falhou::countDown);
        fila.iniciar();
        fila.enfileirar("x");

        assertTrue(falhou.await(5, TimeUnit.SECONDS));
        assertTrue(fila.aguardar(5_000));
        assertFalse(fila.enfileirar("y"));
    }
}
