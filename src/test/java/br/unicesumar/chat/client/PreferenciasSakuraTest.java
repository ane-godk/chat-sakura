package br.unicesumar.chat.client;

import br.unicesumar.chat.client.ConfiguracaoAnimacoes.Modo;
import br.unicesumar.chat.client.GerenciadorTema.Tema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Persistência local das preferências, sempre em diretório temporário. */
class PreferenciasSakuraTest {

    @TempDir
    Path pasta;

    private PreferenciasSakura preferencias(String nome) {
        return new PreferenciasSakura(pasta.resolve(nome).resolve("preferencias.properties"));
    }

    private static PreferenciasSakura.Valores semPropriedades(PreferenciasSakura p) {
        return PreferenciasSakura.resolver(p.ler(), null, null, null);
    }

    private static void escrever(PreferenciasSakura p, String conteudo) throws Exception {
        Files.createDirectories(p.arquivo().getParent());
        Files.writeString(p.arquivo(), conteudo, StandardCharsets.UTF_8);
    }

    @Test
    void arquivoInexistenteDeveUsarOsPadroes() {
        PreferenciasSakura p = preferencias("nova");

        assertTrue(p.ler().isEmpty());
        assertEquals(PreferenciasSakura.Valores.PADRAO, semPropriedades(p));
        assertFalse(Files.exists(p.arquivo()), "Ler não cria o arquivo.");
    }

    @Test
    void preferenciasDevemSerSalvasERestauradas() {
        PreferenciasSakura p = preferencias("salvar");
        p.salvarTema(Tema.NOTURNO);
        p.salvarSons(true);
        p.salvarAnimacoes(Modo.REDUZIDAS);
        assertTrue(p.aguardarGravacoes(5000));

        PreferenciasSakura.Valores lidos = semPropriedades(new PreferenciasSakura(p.arquivo()));

        assertEquals(Tema.NOTURNO, lidos.tema());
        assertTrue(lidos.silenciado());
        assertEquals(Modo.REDUZIDAS, lidos.animacoes());
    }

    @Test
    void valoresInvalidosDevemUsarOPadraoSomenteNaChaveInvalida() throws Exception {
        PreferenciasSakura p = preferencias("invalido");
        escrever(p, "tema=azul\nsons=talvez\nanimacoes=reduzidas\n");

        PreferenciasSakura.Valores v = semPropriedades(p);

        assertEquals(Tema.CLARO, v.tema());
        assertFalse(v.silenciado());
        assertEquals(Modo.REDUZIDAS, v.animacoes());
    }

    @Test
    void arquivoParcialmenteCorrompidoDeveRecuperarOsValoresValidos() throws Exception {
        PreferenciasSakura p = preferencias("corrompido");
        // "\\uZZZZ" é um escape inválido: Properties.load falha no arquivo inteiro.
        escrever(p, "tema=noturno\nlixo\\uZZZZ=1\nsons=silenciados\n\u0000\u0001 bytes estranhos\n");

        PreferenciasSakura.Valores v = semPropriedades(p);

        assertEquals(Tema.NOTURNO, v.tema());
        assertTrue(v.silenciado());
        assertEquals(Modo.COMPLETAS, v.animacoes());
    }

    @Test
    void propriedadesDesconhecidasDevemSerPreservadas() throws Exception {
        PreferenciasSakura p = preferencias("desconhecidas");
        escrever(p, "outra.chave=valor importante\nanimacoes=desativadas\n");

        p.salvarTema(Tema.NOTURNO);
        assertTrue(p.aguardarGravacoes(5000));

        Properties lidas = p.ler();
        assertEquals("valor importante", lidas.getProperty("outra.chave"));
        assertEquals("desativadas", lidas.getProperty("animacoes"));
        assertEquals("noturno", lidas.getProperty("tema"));
    }

    @Test
    void alteracoesSucessivasNaoDevemPerderDados() throws Exception {
        PreferenciasSakura p = preferencias("sucessivas");

        for (int i = 0; i < 30; i++) {
            p.salvarTema(i % 2 == 0 ? Tema.NOTURNO : Tema.CLARO);
            p.salvarSons(i % 3 == 0);
            p.salvarAnimacoes(Modo.values()[i % 3]);
        }
        assertTrue(p.aguardarGravacoes(10000));

        Properties lidas = p.ler();
        assertEquals("claro", lidas.getProperty("tema"));          // i = 29
        assertEquals("ligados", lidas.getProperty("sons"));        // 29 % 3 != 0
        assertEquals("desativadas", lidas.getProperty("animacoes"));  // 29 % 3 == 2

        try (Stream<Path> arquivos = Files.list(p.arquivo().getParent())) {
            assertEquals(1, arquivos.count(), "Nenhum arquivo temporário deixado para trás.");
        }
    }

    @Test
    void propriedadesValidasDeInicializacaoTemPrioridadeSemGravarNoArquivo() throws Exception {
        PreferenciasSakura p = preferencias("precedencia");
        escrever(p, "tema=noturno\nsons=silenciados\nanimacoes=reduzidas\n");
        byte[] antes = Files.readAllBytes(p.arquivo());

        PreferenciasSakura.Valores comD = PreferenciasSakura.resolver(p.ler(), "claro", "ligados", "desativadas");
        assertEquals(Tema.CLARO, comD.tema());
        assertFalse(comD.silenciado());
        assertEquals(Modo.DESATIVADAS, comD.animacoes());

        // -D inválida: vale o arquivo.
        PreferenciasSakura.Valores invalidas = PreferenciasSakura.resolver(p.ler(), "azul", "??", "x");
        assertEquals(Tema.NOTURNO, invalidas.tema());
        assertTrue(invalidas.silenciado());
        assertEquals(Modo.REDUZIDAS, invalidas.animacoes());

        assertArrayEquals(antes, Files.readAllBytes(p.arquivo()), "A resolução não grava o arquivo.");
    }

    @Test
    void falhasDeLeituraEGravacaoNaoDevemInterromperOChat() throws Exception {
        // Caminho do arquivo é uma pasta: leitura e gravação falham.
        Path pastaNoLugar = pasta.resolve("pasta-no-lugar").resolve("preferencias.properties");
        Files.createDirectories(pastaNoLugar);
        PreferenciasSakura comPasta = new PreferenciasSakura(pastaNoLugar);

        assertTrue(comPasta.ler().isEmpty());
        assertDoesNotThrow(() -> comPasta.salvarTema(Tema.NOTURNO));
        assertTrue(comPasta.aguardarGravacoes(5000));

        // A "pasta" de destino é um arquivo comum: não dá para criar o diretório.
        Path arquivoComum = pasta.resolve("sou-um-arquivo");
        Files.writeString(arquivoComum, "x");
        PreferenciasSakura semPasta = new PreferenciasSakura(arquivoComum.resolve("preferencias.properties"));

        assertDoesNotThrow(() -> semPasta.salvarSons(true));
        assertTrue(semPasta.aguardarGravacoes(5000));
        assertEquals(PreferenciasSakura.Valores.PADRAO, semPropriedades(semPasta));
    }

    @Test
    void deveInterpretarOsValoresAceitos() {
        assertEquals(Tema.NOTURNO, PreferenciasSakura.tema(" Noturno ").orElseThrow());
        assertTrue(PreferenciasSakura.tema("azul").isEmpty());
        assertTrue(PreferenciasSakura.silenciado("silenciados").orElseThrow());
        assertFalse(PreferenciasSakura.silenciado("ligados").orElseThrow());
        assertEquals(Modo.REDUZIDAS, PreferenciasSakura.animacoes("reduzidas").orElseThrow());
        assertTrue(PreferenciasSakura.animacoes(null).isEmpty());
        assertTrue(PreferenciasSakura.arquivoPadrao().endsWith(Path.of(".chat-sakura", "preferencias.properties")));
    }
}
