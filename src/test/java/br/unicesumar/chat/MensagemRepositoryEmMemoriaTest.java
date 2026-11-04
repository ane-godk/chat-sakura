
package br.unicesumar.chat;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MensagemRepositoryEmMemoriaTest {

    private Mensagem criarMensagem(String texto) {
        return new Mensagem(
                new Usuario("Ana"),
                texto,
                LocalDateTime.of(2026, 11, 3, 15, 0)
        );
    }

    @Test
    void deveSalvarMensagemComId() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        Mensagem original = criarMensagem("Olá!");

        Mensagem salva = repositorio.salvar(original);

        assertNull(original.getId());
        assertEquals(1L, salva.getId());
        assertEquals(1, repositorio.buscarUltimas(10).size());
    }

    @Test
    void deveRetornarUltimasMensagensEmOrdem() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        repositorio.salvar(criarMensagem("Primeira"));
        repositorio.salvar(criarMensagem("Segunda"));
        repositorio.salvar(criarMensagem("Terceira"));

        List<Mensagem> mensagens =
                repositorio.buscarUltimas(2);

        assertEquals(2, mensagens.size());
        assertEquals("Segunda", mensagens.get(0).getConteudo());
        assertEquals("Terceira", mensagens.get(1).getConteudo());
    }

    @Test
    void deveRetornarListaIndependente() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        repositorio.salvar(criarMensagem("Teste"));

        List<Mensagem> copia = repositorio.buscarUltimas(10);

        copia.clear();

        assertEquals(1, repositorio.buscarUltimas(10).size());
    }

    @Test
    void deveRejeitarQuantidadeNegativa() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        assertThrows(
                IllegalArgumentException.class,
                () -> repositorio.buscarUltimas(-1)
        );
    }

    @Test
    void deveRetornarListaVaziaQuandoQuantidadeForZero() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        repositorio.salvar(criarMensagem("Teste"));

        assertTrue(repositorio.buscarUltimas(0).isEmpty());
    }
}
