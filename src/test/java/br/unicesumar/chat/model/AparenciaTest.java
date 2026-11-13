package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AparenciaTest {

    @Test
    void padraoESakuraClaroFloral() {
        assertEquals(new Aparencia(Aparencia.Paleta.SAKURA, Aparencia.Modo.CLARO, Aparencia.Fundo.FLORAL),
                Aparencia.PADRAO);
        assertFalse(Aparencia.PADRAO.escuro());
    }

    @Test
    void cadaPaletaModoEFundoPodeSerEscolhido() {
        for (Aparencia.Paleta paleta : Aparencia.Paleta.values()) {
            for (Aparencia.Modo modo : Aparencia.Modo.values()) {
                for (Aparencia.Fundo fundo : Aparencia.Fundo.values()) {
                    Aparencia a = Aparencia.exigir(paleta.name(), modo.name(), fundo.name());
                    assertEquals(new Aparencia(paleta, modo, fundo), a);
                    assertEquals(modo == Aparencia.Modo.ESCURO, a.escuro());
                }
            }
        }
        assertEquals(4, Aparencia.Paleta.values().length);
        assertEquals(3, Aparencia.Fundo.values().length);
    }

    @Test
    void comAlteraSoUmCampo() {
        Aparencia fuji = Aparencia.PADRAO.comPaleta(Aparencia.Paleta.FUJI);
        assertEquals(new Aparencia(Aparencia.Paleta.FUJI, Aparencia.Modo.CLARO, Aparencia.Fundo.FLORAL), fuji);
        assertEquals(Aparencia.Modo.ESCURO, fuji.comModo(Aparencia.Modo.ESCURO).modo());
        assertEquals(Aparencia.Fundo.NEUTRO, fuji.comFundo(Aparencia.Fundo.NEUTRO).fundo());
        assertEquals(Aparencia.PADRAO, new Aparencia(Aparencia.Paleta.SAKURA, Aparencia.Modo.CLARO,
                Aparencia.Fundo.FLORAL), "Imutável: o original não muda");
    }

    @Test
    void leituraTolerantetrocaSoOCampoInvalidoPeloPadrao() {
        assertEquals(new Aparencia(Aparencia.Paleta.SAKURA, Aparencia.Modo.ESCURO, Aparencia.Fundo.LISO),
                Aparencia.ler("ROSA_ANTIGA", "escuro", " liso "));
        assertEquals(new Aparencia(Aparencia.Paleta.FUJI, Aparencia.Modo.CLARO, Aparencia.Fundo.FLORAL),
                Aparencia.ler("fuji", "NOTURNO", null));
        assertEquals(Aparencia.PADRAO, Aparencia.ler(null, null, null));
        assertEquals(Aparencia.PADRAO, Aparencia.ler("", "  ", "123"));
    }

    @Test
    void leituraEstritaRecusaQualquerCampoInvalido() {
        assertEquals("Paleta inválida.", assertThrows(IllegalArgumentException.class,
                () -> Aparencia.exigir("ROSA", "CLARO", "FLORAL")).getMessage());
        assertEquals("Modo inválido.", assertThrows(IllegalArgumentException.class,
                () -> Aparencia.exigir("SAKURA", null, "FLORAL")).getMessage());
        assertEquals("Fundo inválido.", assertThrows(IllegalArgumentException.class,
                () -> Aparencia.exigir("SAKURA", "CLARO", "")).getMessage());
        assertThrows(NullPointerException.class, () -> new Aparencia(null, Aparencia.Modo.CLARO, Aparencia.Fundo.LISO));
    }
}
