package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuebraTextoTest {

    private static FontMetrics metricas() {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return g.getFontMetrics(TemaSakura.FONTE_TEXTO);
        } finally {
            g.dispose();
        }
    }

    private static String semEspacos(String texto) {
        return texto.replace(" ", "");
    }

    @Test
    void textoCurtoDeveFicarEmUmaLinha() {

        assertEquals(List.of("Oi, Ana!"), QuebraTexto.quebrar("Oi, Ana!", metricas(), 300));
        assertEquals(List.of(""), QuebraTexto.quebrar("", metricas(), 300));
    }

    @Test
    void nenhumaLinhaDeveUltrapassarALargura() {

        FontMetrics fm = metricas();
        String texto = "Uma mensagem longa com várias palavras para quebrar ".repeat(8);

        List<String> linhas = QuebraTexto.quebrar(texto, fm, 180);

        assertTrue(linhas.size() > 3);
        for (String linha : linhas) {
            assertTrue(fm.stringWidth(linha) <= 180, "Linha larga demais: " + linha);
        }
        assertEquals(semEspacos(texto), semEspacos(String.join("", linhas)));
    }

    @Test
    void palavraSemEspacosDeveSerDivididaSemPerderCaracteres() {

        FontMetrics fm = metricas();
        String palavra = "https://exemplo.com/" + "abcdefghij".repeat(12);

        List<String> linhas = QuebraTexto.quebrar(palavra, fm, 120);

        assertTrue(linhas.size() > 1);
        for (String linha : linhas) {
            assertTrue(fm.stringWidth(linha) <= 120);
        }
        assertEquals(palavra, String.join("", linhas));
    }

    @Test
    void naoDeveSepararEmojisNemAcentos() {

        String texto = "🌸ção".repeat(40);

        List<String> linhas = QuebraTexto.quebrar(texto, metricas(), 60);

        assertEquals(texto, String.join("", linhas));
        for (String linha : linhas) {
            assertFalse(Character.isLowSurrogate(linha.charAt(0)), "Emoji partido.");
            assertFalse(Character.isHighSurrogate(linha.charAt(linha.length() - 1)), "Emoji partido.");
        }
    }
}
