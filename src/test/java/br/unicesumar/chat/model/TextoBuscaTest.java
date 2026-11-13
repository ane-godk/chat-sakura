package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextoBuscaTest {

    @Test
    void ignoraAcentosMaiusculasLarguraEEspacosRepetidos() {
        assertEquals("acao rapida", TextoBusca.normalizar("AÇÃO   Rápida"));
        assertEquals("acao", TextoBusca.normalizar("ａçãｏ"), "largura total");
        assertEquals("sao paulo", TextoBusca.normalizar("São\tPaulo"));
        assertEquals("lamen de misso", TextoBusca.normalizar("Lámen de Missô"));
    }

    @Test
    void preservaEmojisEAlfabetosNaoLatinos() {
        assertEquals("oi 🌸 さくら", TextoBusca.normalizar("Oi 🌸 さくら"));
        assertTrue(TextoBusca.normalizar("Привет, мир").contains("привет"));
    }

    @Test
    void termoTemTamanhoMinimoEMaximo() {
        assertEquals("ca", TextoBusca.termo("  Cá "));
        assertThrows(IllegalArgumentException.class, () -> TextoBusca.termo("a"));
        assertThrows(IllegalArgumentException.class, () -> TextoBusca.termo("   "));
        assertThrows(IllegalArgumentException.class, () -> TextoBusca.termo(null));
        assertThrows(IllegalArgumentException.class, () -> TextoBusca.termo("x".repeat(101)));
        assertEquals(100, TextoBusca.termo("x".repeat(100)).length());
    }

    @Test
    void trechoMarcaATermoNoTextoOriginal() {
        TextoBusca.Trecho t = TextoBusca.trecho("Reunião às 18h no Jardim", "jardim");
        assertEquals("Reunião às 18h no Jardim", t.texto());
        assertEquals("Jardim", t.texto().substring(t.inicio(), t.fim()));

        TextoBusca.Trecho acento = TextoBusca.trecho("Vamos à REUNIÃO amanhã", "reuniao");
        assertEquals("REUNIÃO", acento.texto().substring(acento.inicio(), acento.fim()));
    }

    @Test
    void trechoLongoECortadoComReticenciasSemQuebrarEmojis() {
        String longo = "🌸".repeat(30) + " começo " + "palavra ".repeat(10) + "alvo" + " fim".repeat(60);
        TextoBusca.Trecho t = TextoBusca.trecho(longo, "alvo");
        assertTrue(t.texto().startsWith("…"));
        assertTrue(t.texto().endsWith("…"));
        assertEquals("alvo", t.texto().substring(t.inicio(), t.fim()));
        assertTrue(t.texto().length() <= TextoBusca.TRECHO_MAXIMO + 2);
        assertFalse(Character.isLowSurrogate(t.texto().charAt(1)), "Não corta um emoji ao meio");
    }

    @Test
    void semOcorrenciaDevolveOInicioDoTexto() {
        TextoBusca.Trecho t = TextoBusca.trecho("Olá, mundo", "xyz");
        assertEquals(-1, t.inicio());
        assertEquals("Olá, mundo", t.texto());
    }
}
