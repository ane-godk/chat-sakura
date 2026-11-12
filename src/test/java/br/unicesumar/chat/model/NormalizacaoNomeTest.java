package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Normalização Unicode centralizada dos nomes (S6). */
class NormalizacaoNomeTest {

    @Test
    void nomesEquivalentesDevemTerAMesmaChave() {
        assertEquals("ana", NormalizacaoNome.chave("Ana"));
        assertTrue(NormalizacaoNome.equivalentes("Ana", "ANA"));
        assertTrue(NormalizacaoNome.equivalentes("Ana", "Ａｎａ"), "Largura total (NFKC)");
        assertTrue(NormalizacaoNome.equivalentes("Élia", "ÉLIA"), "Maiúsculas fora do ASCII");
        assertTrue(NormalizacaoNome.equivalentes("Élia", "Élia"), "Acento combinado");
        assertTrue(NormalizacaoNome.equivalentes("ﬁo", "fio"), "Ligatura");
        assertTrue(NormalizacaoNome.equivalentes("Ana²", "Ana2"), "Sobrescrito");
        assertFalse(NormalizacaoNome.equivalentes("Ana", "Ane"));
    }

    @Test
    void chaveNaoDependeDaLocalidadePadrao() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
            assertEquals("ilma", NormalizacaoNome.chave("ILMA"), "Sem o 'ı' turco: usa Locale.ROOT");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void exibicaoPreservaAFormaOriginal() {
        assertEquals("Ａｎａ", NormalizacaoNome.exibicao(" Ａｎａ "), "Exibe como digitado (só NFC)");
        assertEquals("Élia", NormalizacaoNome.exibicao("Élia"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ana​",        // espaço de largura zero
            "A‍na",        // junção de largura zero
            "Ana⁠",        // word joiner
            "﻿Ana",        // BOM
            "An­a",        // hífen invisível
            "Ana‮",        // inversão de direção
            "Ana\u0007",        // controle
            "Ana",        // uso privado
    })
    void caracteresInvisiveisDevemSerRejeitados(String nome) {
        assertEquals("O nome contém caracteres invisíveis ou de controle.", NormalizacaoNome.problema(nome));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana Souza", "Ana B", "Ana B", "Ana　B", "Ana B"})
    void espacosNaoConvencionaisDevemSerRejeitados(String nome) {
        assertEquals("O nome não pode conter espaços.", NormalizacaoNome.problema(nome));
    }

    @Test
    void misturaDeAlfabetosDeveSerRejeitadaMasEscritasUnicasAceitas() {
        assertNotNull(NormalizacaoNome.problema("Аna"), "A cirílico + latim");
        assertNotNull(NormalizacaoNome.problema("Pαulo"), "alfa grego + latim");

        assertNull(NormalizacaoNome.problema("Ana_2026"));
        assertNull(NormalizacaoNome.problema("João"));
        assertNull(NormalizacaoNome.problema("Анна"), "Só cirílico");
        assertNull(NormalizacaoNome.problema("さくら桜サクラ"), "Japonês conta como uma escrita");
        assertNull(NormalizacaoNome.problema("벚꽃"), "Coreano");
    }

    @Test
    void regrasDeApelidoEVisitanteUsamAMesmaValidacao() {
        assertEquals("Ａｎａ", RegrasConta.normalizarApelido("Ａｎａ"));
        assertEquals("ana", RegrasConta.chaveApelido("Ａｎａ"));

        for (String invalido : new String[] {"Ana​", "Аna", "Ana B"}) {
            assertThrows(IllegalArgumentException.class, () -> RegrasConta.normalizarApelido(invalido), invalido);
            assertThrows(IllegalArgumentException.class, () -> RegrasConta.normalizarNomeVisitante(invalido), invalido);
        }
    }

    @Test
    void usuariosEquivalentesSaoIguaisNasSessoes() {
        assertEquals(new Usuario("Élia"), new Usuario("ÉLIA"));
        assertEquals(new Usuario("Élia").hashCode(), new Usuario("ÉLIA").hashCode());
        assertEquals(new Usuario("Ana"), new Usuario("Ａｎａ"));
        assertEquals("Ａｎａ", new Usuario("Ａｎａ").getNome(), "O nome exibido não muda");
    }
}
