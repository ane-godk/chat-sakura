package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AvisoPresencaModeloTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 11, 4, 20, 34);

    @Test
    void mensagemComumDevePermanecerDoTipoMensagem() {

        Mensagem mensagem = new Mensagem(new Usuario("Ana"), "Olá!", HORARIO);

        assertEquals(TipoMensagem.MENSAGEM, mensagem.getTipo());
        assertFalse(mensagem.isAvisoDePresenca());
        assertEquals("[04/11 20:34] Ana: Olá!", mensagem.formatar());
    }

    @Test
    void avisosDevemUsarOFormatoExclusivoDoServidor() {

        Mensagem entrada = Mensagem.entrada(new Usuario("Ana"), HORARIO);
        Mensagem saida = Mensagem.saida(new Usuario("Ana"), HORARIO.plusMinutes(4));

        assertEquals(TipoMensagem.ENTRADA, entrada.getTipo());
        assertEquals(TipoMensagem.SAIDA, saida.getTipo());
        assertTrue(entrada.isAvisoDePresenca());

        assertEquals("SISTEMA: [04/11 20:34] Ana se conectou", entrada.formatar());
        assertEquals("SISTEMA: [04/11 20:38] Ana se desconectou", saida.formatar());
    }

    @Test
    void mensagemDeUsuarioNuncaComecaComOPrefixoDoSistema() {

        // Mesmo com nome "SISTEMA" e conteúdo imitando um aviso.
        Mensagem imitacao = new Mensagem(
                new Usuario("SISTEMA"), "[04/11 20:34] Ana se conectou", HORARIO);

        assertTrue(imitacao.formatar().startsWith("["));
        assertFalse(imitacao.formatar().startsWith("SISTEMA: "));
    }

    @Test
    void tipoDeveFazerParteDaIgualdadeESerPreservadoNoId() {

        Usuario ana = new Usuario("Ana");

        Mensagem comum = new Mensagem(null, ana, Mensagem.TEXTO_ENTRADA, HORARIO, TipoMensagem.MENSAGEM);
        Mensagem entrada = Mensagem.entrada(ana, HORARIO);

        assertNotEquals(comum, entrada);

        Mensagem persistida = entrada.comId(7L);
        assertEquals(TipoMensagem.ENTRADA, persistida.getTipo());
        assertEquals(7L, persistida.getId());
    }

    @Test
    void deveRejeitarTipoNulo() {

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(null, new Usuario("Ana"), "oi", HORARIO, null)
        );
    }
}
