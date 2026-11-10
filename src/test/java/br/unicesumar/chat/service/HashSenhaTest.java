package br.unicesumar.chat.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** PBKDF2: parâmetros, sal, comparação e dados armazenados inválidos. */
class HashSenhaTest {

    private final HashSenha rapido = new HashSenha(1_000);

    @Test
    void parametrosPadraoDevemSerOsDocumentados() {
        assertEquals("PBKDF2WithHmacSHA256", HashSenha.ALGORITMO);
        assertEquals(600_000, HashSenha.ITERACOES_PADRAO);
        assertEquals(16, HashSenha.BYTES_SAL);
        assertEquals(32, HashSenha.BYTES_HASH);

        HashSenha padrao = new HashSenha();
        assertEquals(600_000, padrao.iteracoes());

        HashSenha.Credenciais credenciais = padrao.gerar("senha-forte-1".toCharArray());
        String[] partes = credenciais.hash().split("\\$");

        assertEquals("pbkdf2-sha256", partes[0]);
        assertEquals("600000", partes[1]);
        assertEquals(32, Base64.getDecoder().decode(partes[2]).length);
        assertEquals(16, Base64.getDecoder().decode(credenciais.sal()).length);
        assertTrue(padrao.verificar("senha-forte-1".toCharArray(), credenciais.hash(), credenciais.sal()));
    }

    @Test
    void mesmaSenhaDeveGerarSaisEHashesDistintos() {
        char[] senha = "igual-para-todos".toCharArray();
        HashSenha.Credenciais a = rapido.gerar(senha);
        HashSenha.Credenciais b = rapido.gerar(senha);

        assertNotEquals(a.sal(), b.sal());
        assertNotEquals(a.hash(), b.hash());
        assertTrue(rapido.verificar(senha, a.hash(), a.sal()));
        assertTrue(rapido.verificar(senha, b.hash(), b.sal()));
        assertFalse(rapido.verificar(senha, a.hash(), b.sal()), "Sal trocado não confere");
    }

    @Test
    void senhaIncorretaDeveSerRecusada() {
        HashSenha.Credenciais c = rapido.gerar("correta-123".toCharArray());
        assertFalse(rapido.verificar("correta-124".toCharArray(), c.hash(), c.sal()));
        assertFalse(rapido.verificar("Correta-123".toCharArray(), c.hash(), c.sal()));
        assertFalse(rapido.verificar(new char[0], c.hash(), c.sal()));
    }

    @Test
    void dadosArmazenadosInvalidosDevemSerRecusadosSemExcecao() {
        char[] senha = "correta-123".toCharArray();
        HashSenha.Credenciais c = rapido.gerar(senha);
        String derivado = c.hash().split("\\$")[2];

        String[] hashes = {
                null, "", "texto-puro", "pbkdf2-sha1$1000$" + derivado,
                "pbkdf2-sha256$abc$" + derivado, "pbkdf2-sha256$999$" + derivado,
                "pbkdf2-sha256$99999999$" + derivado, "pbkdf2-sha256$1000$***",
                "pbkdf2-sha256$1000$" + Base64.getEncoder().encodeToString(new byte[8]),
                "pbkdf2-sha256$1000$" + derivado + "$extra"};

        for (String hash : hashes) {
            assertFalse(rapido.verificar(senha, hash, c.sal()), String.valueOf(hash));
        }

        for (String sal : new String[] {null, "", "%%%", Base64.getEncoder().encodeToString(new byte[4])}) {
            assertFalse(rapido.verificar(senha, c.hash(), sal), String.valueOf(sal));
        }

        assertFalse(rapido.verificar(null, c.hash(), c.sal()));
    }

    @Test
    void credenciaisNaoDevemConterNemExporASenha() {
        char[] senha = "nao-pode-vazar".toCharArray();
        HashSenha.Credenciais c = rapido.gerar(senha);

        assertFalse(c.hash().contains("nao-pode-vazar"));
        assertFalse(c.sal().contains("nao-pode-vazar"));
        assertFalse(c.toString().contains(c.hash()));
        assertFalse(c.toString().contains(c.sal()));
        assertArrayEquals("nao-pode-vazar".toCharArray(), senha, "gerar() não altera a senha recebida");
    }

    @Test
    void iteracoesForaDoIntervaloDevemSerRecusadas() {
        assertThrows(IllegalArgumentException.class, () -> new HashSenha(999));
        assertThrows(IllegalArgumentException.class, () -> new HashSenha(10_000_001));
    }
}
