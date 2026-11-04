
package br.unicesumar.chat;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.MensagemInvalidaException;
import br.unicesumar.chat.model.Usuario;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MensagemTest {

    private static final LocalDateTime HORARIO =
            LocalDateTime.of(2026, 11, 3, 15, 30);

    @Test
    void deveCriarMensagemValida() {

        Mensagem mensagem = new Mensagem(
                new Usuario("Ana"),
                "Olá, Sakura!",
                HORARIO
        );

        assertEquals("Ana", mensagem.getRemetente().getNome());
        assertEquals("Olá, Sakura!", mensagem.getConteudo());
        assertEquals(HORARIO, mensagem.getHorario());
        assertNull(mensagem.getId());
    }

    @Test
    void deveRejeitarConteudoNulo() {

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(
                        new Usuario("Ana"),
                        null,
                        HORARIO
                )
        );
    }

    @Test
    void deveRejeitarConteudoEmBranco() {

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(
                        new Usuario("Ana"),
                        "   ",
                        HORARIO
                )
        );
    }

    @Test
    void deveRejeitarRemetenteNulo() {

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(
                        null,
                        "Mensagem de teste",
                        HORARIO
                )
        );
    }

    @Test
    void deveRejeitarHorarioNulo() {

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(
                        new Usuario("Ana"),
                        "Mensagem de teste",
                        null
                )
        );
    }

    @Test
    void deveCompararMensagensComMesmoConteudo() {

        Mensagem primeira = new Mensagem(
                new Usuario("Ana"),
                "Olá!",
                HORARIO
        );

        Mensagem segunda = new Mensagem(
                new Usuario("Ana"),
                "Olá!",
                HORARIO
        );

        assertEquals(primeira, segunda);
        assertEquals(primeira.hashCode(), segunda.hashCode());
    }

    @Test
    void deveDiferenciarMensagensComIdsDistintos() {

        Mensagem primeira = new Mensagem(
                1L,
                new Usuario("Ana"),
                "Olá!",
                HORARIO
        );

        Mensagem segunda = new Mensagem(
                2L,
                new Usuario("Ana"),
                "Olá!",
                HORARIO
        );

        assertNotEquals(primeira, segunda);
    }

    @Test
    void deveFormatarMensagemCorretamente() {

        Mensagem mensagem = new Mensagem(
                new Usuario("Bia"),
                "Bom dia!",
                HORARIO
        );

        assertEquals(
                "[03/11 15:30] Bia: Bom dia!",
                mensagem.formatar()
        );
    }
}
