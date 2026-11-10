package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfilUsuarioTest {

    @Test
    void perfilDeveNormalizarEVincularOParticipanteAConta() {
        PerfilUsuario perfil = new PerfilUsuario(3, " Ana.Souza ", "Ágata", "sakura");

        assertEquals("ana.souza", perfil.getLogin());
        Usuario usuario = perfil.comoUsuario();
        assertEquals(3L, usuario.getId());
        assertEquals("Ágata", usuario.getNome());
        assertTrue(usuario.isAutenticado());
        assertFalse(new Usuario("Ágata").isAutenticado());
    }

    @Test
    void igualdadeDoParticipanteContinuaPeloNomeVisivel() {
        // Impede um visitante e uma conta ativos com o mesmo nome exibido.
        assertEquals(new Usuario(1L, "Ana"), new Usuario("ANA"));
        assertEquals(new Usuario(1L, "Ana").hashCode(), new Usuario("ana").hashCode());
    }

    @Test
    void dadosInvalidosDevemSerRecusados() {
        assertThrows(IllegalArgumentException.class, () -> new PerfilUsuario(0, "ana", "Ana", "sakura"));
        assertThrows(IllegalArgumentException.class, () -> new PerfilUsuario(1, "ana", "Ana", "../x"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario(-1L, "Ana"));
        assertFalse(new PerfilUsuario(1, "ana", "Ana", "sakura").toString().contains("ana"),
                "toString mostra apelido e id, sem o login");
    }
}
