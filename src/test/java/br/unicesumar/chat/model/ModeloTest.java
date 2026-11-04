
package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ModeloTest {

    private static final LocalDateTime HORARIO =
            LocalDateTime.of(2026, 11, 3, 14, 30);

    @Test
    void deveCriarUsuarioValido() {

        Usuario usuario = new Usuario("Ana");

        assertEquals("Ana", usuario.getNome());
    }

    @Test
    void deveRejeitarNomeVazio() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Usuario("   ")
        );
    }

    @Test
    void deveRejeitarNomeComEspacos() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Usuario("Ana Maria")
        );
    }

    @Test
    void deveConsiderarNomesIguaisSemDiferenciarMaiusculas() {

        Usuario primeiro = new Usuario("Ana");
        Usuario segundo = new Usuario("ana");

        assertEquals(primeiro, segundo);
        assertEquals(
                primeiro.hashCode(),
                segundo.hashCode()
        );
    }

    @Test
    void deveRejeitarMensagemVazia() {

        Usuario usuario = new Usuario("Ana");

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(usuario, "   ", HORARIO)
        );
    }

    @Test
    void deveRejeitarMensagemAcimaDoLimite() {

        Usuario usuario = new Usuario("Ana");

        String texto = "a".repeat(501);

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(usuario, texto, HORARIO)
        );
    }

    @Test
    void deveFormatarMensagem() {

        Mensagem mensagem = new Mensagem(
                new Usuario("Ana"),
                "Olá!",
                HORARIO
        );

        assertEquals(
                "[03/11 14:30] Ana: Olá!",
                mensagem.formatar()
        );
    }

    @Test
    void deveCriarNovaInstanciaComId() {

        Mensagem original = new Mensagem(
                new Usuario("Ana"),
                "Olá!",
                HORARIO
        );

        Mensagem persistida = original.comId(10L);

        assertNull(original.getId());
        assertEquals(10L, persistida.getId());
        assertNotSame(original, persistida);
        assertEquals(
                original.getConteudo(),
                persistida.getConteudo()
        );
    }
}
