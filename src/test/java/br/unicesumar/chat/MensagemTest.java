
package br.unicesumar.chat;

import br.unicesumar.chat.domain.Mensagem;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MensagemTest {

    @Test
    void deveCriarMensagemValida() {

        LocalDateTime horario =
                LocalDateTime.of(2026, 11, 3, 14, 30, 0);

        Mensagem mensagem = new Mensagem(
                "Kelly",
                "Olá, mundo!",
                horario
        );

        assertEquals("Kelly", mensagem.getRemetente());
        assertEquals("Olá, mundo!", mensagem.getTexto());
        assertEquals(horario, mensagem.getHorario());
    }

    @Test
    void naoDeveAceitarRemetenteVazio() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem("", "Olá")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(null, "Olá")
        );
    }

    @Test
    void naoDeveAceitarTextoVazio() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem("Ana", "")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem("Ana", null)
        );
    }

    @Test
    void deveFormatarMensagemCorretamente() {

        LocalDateTime horario =
                LocalDateTime.of(2026, 11, 3, 15, 45, 12);

        Mensagem mensagem = new Mensagem(
                "Ana",
                "Bom dia!",
                horario
        );

        assertEquals(
                "[15:45:12] Ana: Bom dia!",
                mensagem.toString()
        );
    }

    @Test
    void mensagensComMesmoConteudoDevemSerIguais() {

        LocalDateTime horario =
                LocalDateTime.of(2026, 11, 3, 10, 0);

        Mensagem primeira = new Mensagem(
                "Ana", "Olá", horario
        );

        Mensagem segunda = new Mensagem(
                "Ana", "Olá", horario
        );

        assertEquals(primeira, segunda);
        assertEquals(
                primeira.hashCode(),
                segunda.hashCode()
        );
    }
}
