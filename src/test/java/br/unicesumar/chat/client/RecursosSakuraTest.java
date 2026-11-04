package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class RecursosSakuraTest {

    /** Caracteres do português usados na interface. */
    private static final int[] CARACTERES_PORTUGUES = {
            0xE7, 0xE3, 0xF5, 0xE9, 0xED, 0xF3, 0xFA, 0xE2, 0xEA, 0xF4, 0xE0,
            0xC7, 0xC3, 0xD5, 0xC9, 0xCD, 0xD3, 0xDA, 0xC2, 0xCA, 0xD4, 0xC0
    };

    @Test
    void deveCarregarBannerEFlor() {

        BufferedImage banner = RecursosSakura.banner().orElseThrow();
        BufferedImage flor = RecursosSakura.flor().orElseThrow();

        assertEquals(1500, banner.getWidth());
        assertEquals(500, banner.getHeight());

        assertEquals(256, flor.getWidth());
        assertEquals(256, flor.getHeight());
        assertTrue(flor.getColorModel().hasAlpha(), "A flor deve ter transparência.");
    }

    @Test
    void deveRetornarVazioParaRecursoInexistente() {

        assertTrue(RecursosSakura.carregarImagem("/sakura/nao-existe.png").isEmpty());
        assertTrue(RecursosSakura.carregarFonte("/sakura/nao-existe.ttf").isEmpty());
    }

    @Test
    void deveCarregarFontesNunitoComCaracteresDoPortugues() {

        assertTrue(TemaSakura.fontesDestaqueCarregadas());

        for (String caminho : new String[] {
                RecursosSakura.FONTE_SEMIBOLD,
                RecursosSakura.FONTE_BOLD,
                RecursosSakura.FONTE_EXTRABOLD
        }) {
            Font fonte = RecursosSakura.carregarFonte(caminho).orElseThrow();

            assertTrue(fonte.getFamily().startsWith("Nunito"), caminho);

            for (int caractere : CARACTERES_PORTUGUES) {
                assertTrue(
                        fonte.canDisplay(caractere),
                        caminho + " sem U+" + Integer.toHexString(caractere)
                );
            }
        }
    }

    @Test
    void deveIncluirLicencaDasFontes() {

        assertNotNull(
                RecursosSakura.class.getResource("/sakura/fontes/OFL.txt"),
                "A licença SIL OFL deve acompanhar as fontes."
        );
    }

    @Test
    void deveRedimensionarParaOTamanhoPedido() {

        BufferedImage flor = RecursosSakura.flor().orElseThrow();

        BufferedImage pequena = RecursosSakura.redimensionar(flor, 37, 37);
        BufferedImage grande = RecursosSakura.redimensionar(flor, 300, 300);

        assertEquals(37, pequena.getWidth());
        assertEquals(37, pequena.getHeight());
        assertEquals(300, grande.getWidth());

        assertThrows(
                IllegalArgumentException.class,
                () -> RecursosSakura.redimensionar(flor, 0, 10)
        );
    }

    @Test
    void iconesDaJanelaDevemTerVariosTamanhos() {

        assertEquals(6, IconeSakura.imagensJanela().size());
    }
}
