package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Backoff exponencial, tentativa única por vez e cancelamento (B10). */
class ReconexaoAutomaticaTest {

    private static final class Registro implements ReconexaoAutomatica.Ouvinte {
        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();

        @Override
        public void tentativaFalhou(int numero, long proximaEmMs) {
            eventos.add("falhou " + numero + " " + proximaEmMs);
        }

        @Override
        public void servidorDisponivel() {
            eventos.add("disponivel");
        }

        String proximo() throws InterruptedException {
            String evento = eventos.poll(5, TimeUnit.SECONDS);
            assertNotNull(evento, "Evento esperado não chegou");
            return evento;
        }
    }

    @Test
    void atrasoDeveCrescerExponencialmenteAteOMaximo() {
        assertEquals(List.of(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
                List.of(1, 2, 3, 4, 5, 6).stream()
                        .map(n -> ReconexaoAutomatica.atraso(n, 1_000, 30_000)).toList());
        assertEquals(30_000L, ReconexaoAutomatica.atraso(1_000, 1_000, 30_000), "Sem estouro");
    }

    @Test
    void deveTentarComBackoffAteOServidorVoltar() throws Exception {
        AtomicInteger sondagens = new AtomicInteger();
        AtomicBoolean servidorNoAr = new AtomicBoolean(false);
        Registro ouvinte = new Registro();
        ReconexaoAutomatica reconexao = new ReconexaoAutomatica(ouvinte, (e, p) -> {
            sondagens.incrementAndGet();
            return servidorNoAr.get();
        }, 5, 40);

        assertTrue(reconexao.iniciar("localhost", 1));
        assertFalse(reconexao.iniciar("localhost", 1), "Nunca duas reconexões ao mesmo tempo");

        assertEquals("falhou 1 10", ouvinte.proximo());
        assertEquals("falhou 2 20", ouvinte.proximo());
        assertEquals("falhou 3 40", ouvinte.proximo());
        assertEquals("falhou 4 40", ouvinte.proximo(), "Limite máximo");

        servidorNoAr.set(true);
        assertEquals("disponivel", ouvinte.proximo());
        assertFalse(reconexao.ativa(), "Terminou: o novo login fica com a interface");
        int total = sondagens.get();
        assertTrue(total >= 5);

        // Depois de terminar, pode começar de novo (nova queda).
        assertTrue(reconexao.iniciar("localhost", 1));
        assertEquals("disponivel", ouvinte.proximo());
        reconexao.fechar();
    }

    @Test
    void cancelarDeveInterromperAsTentativas() throws Exception {
        CountDownLatch primeira = new CountDownLatch(1);
        AtomicInteger sondagens = new AtomicInteger();
        Registro ouvinte = new Registro();
        ReconexaoAutomatica reconexao = new ReconexaoAutomatica(ouvinte, (e, p) -> {
            sondagens.incrementAndGet();
            primeira.countDown();
            return false;
        }, 5, 10);

        reconexao.iniciar("localhost", 1);
        assertTrue(primeira.await(5, TimeUnit.SECONDS));
        ouvinte.proximo();

        reconexao.cancelar();
        assertFalse(reconexao.ativa());
        int aposCancelar = sondagens.get();

        // Uma nova reconexão comprova que o agendador segue vivo e que a
        // antiga não voltou a sondar.
        CountDownLatch nova = new CountDownLatch(1);
        ReconexaoAutomatica outra = new ReconexaoAutomatica(ouvinte, (e, p) -> {
            nova.countDown();
            return true;
        }, 50, 50);
        outra.iniciar("localhost", 1);
        assertTrue(nova.await(5, TimeUnit.SECONDS));
        assertTrue(sondagens.get() <= aposCancelar + 1, "No máximo a sondagem que já estava em curso");
        assertFalse(reconexao.ativa());
        reconexao.fechar();
        outra.fechar();
    }
}
