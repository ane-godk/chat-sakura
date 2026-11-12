package br.unicesumar.chat.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Token bucket, vagas de conexão, configuração e leitura após timeout. */
class LimitesUnitariosTest {

    @Test
    void baldeDeveRecusarAoEsgotarERecarregarComOTempo() {
        AtomicLong agora = new AtomicLong(0);
        BaldeTokens balde = new BaldeTokens(3, 1.0, agora::get);

        assertTrue(balde.consumir());
        assertTrue(balde.consumir());
        assertTrue(balde.consumir());
        assertFalse(balde.consumir(), "Rajada esgotada");

        agora.addAndGet(500_000_000L);
        assertFalse(balde.consumir(), "Meio segundo: ainda sem ficha");
        agora.addAndGet(500_000_000L);
        assertTrue(balde.consumir(), "Um segundo: uma ficha");
        assertFalse(balde.consumir());

        agora.addAndGet(60_000_000_000L);
        for (int i = 0; i < 3; i++) {
            assertTrue(balde.consumir());
        }
        assertFalse(balde.consumir(), "Nunca acumula além da capacidade");
    }

    @Test
    void vagasDevemRespeitarLimiteGlobalEPorIpELiberar() throws Exception {
        ControleConexoes vagas = new ControleConexoes(3, 2);

        assertTrue(vagas.reservar("10.0.0.1"));
        assertTrue(vagas.reservar("10.0.0.1"));
        assertFalse(vagas.reservar("10.0.0.1"), "Limite por IP");
        assertTrue(vagas.reservar("10.0.0.2"));
        assertFalse(vagas.reservar("10.0.0.3"), "Limite global");

        vagas.liberar("10.0.0.1");
        assertEquals(2, vagas.ocupadas());
        assertTrue(vagas.reservar("10.0.0.3"), "Vaga liberada é reaproveitada");
        assertTrue(vagas.aguardarOcupadas(3, Duration.ofMillis(10)));

        vagas.liberar("desconhecido");
        assertEquals(3, vagas.ocupadas(), "Liberar IP sem reserva não altera nada");
        vagas.liberar("10.0.0.1");
        vagas.liberar("10.0.0.2");
        vagas.liberar("10.0.0.3");
        assertEquals(0, vagas.ocupadas());
        assertEquals(0, vagas.ocupadas("10.0.0.1"));
    }

    @Test
    void limitesDevemVirDoAmbienteComPadroesSeguros() {
        LimitesServidor padrao = LimitesServidor.padrao();
        assertEquals(200, padrao.maxConexoes());
        assertEquals(10, padrao.maxConexoesPorIp());
        assertEquals(Duration.ofSeconds(30), padrao.prazoEntrada());
        assertEquals(Duration.ofSeconds(30), padrao.intervaloPing());

        LimitesServidor ajustado = LimitesServidor.doAmbiente(Map.of(
                "CHAT_SAKURA_MAX_CONEXOES", "50",
                "CHAT_SAKURA_MAX_CONEXOES_IP", "3",
                "CHAT_SAKURA_PING_S", "15",
                "CHAT_SAKURA_FILA_SAIDA", "abc",
                "CHAT_SAKURA_PRAZO_ENTRADA_S", "-4"));
        assertEquals(50, ajustado.maxConexoes());
        assertEquals(3, ajustado.maxConexoesPorIp());
        assertEquals(Duration.ofSeconds(15), ajustado.intervaloPing());
        assertEquals(padrao.capacidadeFila(), ajustado.capacidadeFila(), "Valor inválido mantém o padrão");
        assertEquals(padrao.prazoEntrada(), ajustado.prazoEntrada());
    }

    @Test
    void leitorDevePreservarLinhaParcialAposTempoLimite() throws Exception {
        // Simula o socket: "ab", depois um tempo limite, depois "c\n".
        Reader comPausa = new Reader() {
            private final Reader antes = new StringReader("ab");
            private final Reader depois = new StringReader("c\nd\n");
            private boolean pausou;

            @Override
            public int read(char[] buf, int off, int len) throws IOException {
                int lido = antes.read(buf, off, len);
                if (lido != -1) {
                    return lido;
                }
                if (!pausou) {
                    pausou = true;
                    throw new SocketTimeoutException("tempo");
                }
                return depois.read(buf, off, len);
            }

            @Override
            public void close() {
            }
        };

        LeitorLinhas leitor = new LeitorLinhas(comPausa, 100);
        assertThrows(SocketTimeoutException.class, leitor::lerLinha);
        assertEquals("abc", leitor.lerLinha(), "O que chegou antes do tempo limite não se perde");
        assertEquals("d", leitor.lerLinha());
        assertNull(leitor.lerLinha());
    }
}
