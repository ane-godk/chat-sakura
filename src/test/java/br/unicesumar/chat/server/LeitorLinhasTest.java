package br.unicesumar.chat.server;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class LeitorLinhasTest {

    @Test
    void deveAceitarOsMesmosTerminadoresDoBufferedReader() throws Exception {
        LeitorLinhas leitor = new LeitorLinhas(new StringReader("a\nb\r\nc\rd\n\ne"), 10);

        for (String esperada : new String[] {"a", "b", "c", "d", "", "e"}) {
            assertEquals(esperada, leitor.lerLinha());
        }
        assertNull(leitor.lerLinha());
    }

    @Test
    void linhaLongaDeveSerDescartadaSemInterromperAsProximas() throws Exception {
        LeitorLinhas leitor = new LeitorLinhas(new StringReader("1234\n" + "x".repeat(50) + "\nok\n12345"), 4);

        assertEquals("1234", leitor.lerLinha());
        assertThrows(LeitorLinhas.LinhaMuitoLongaException.class, leitor::lerLinha);
        assertEquals("ok", leitor.lerLinha());
        assertThrows(LeitorLinhas.LinhaMuitoLongaException.class, leitor::lerLinha);
        assertNull(leitor.lerLinha());
    }
}
