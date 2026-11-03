
package br.unicesumar.chat;

import br.unicesumar.chat.domain.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MensagemRepositoryEmMemoriaTest {

    @Test
    void deveSalvarMensagem() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        repositorio.salvar(
                new Mensagem("Ana", "Olá!")
        );

        assertEquals(1, repositorio.contarMensagens());
    }

    @Test
    void deveBuscarSomenteAsUltimasMensagens() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        repositorio.salvar(new Mensagem("Ana", "Primeira"));
        repositorio.salvar(new Mensagem("Bia", "Segunda"));
        repositorio.salvar(new Mensagem("Carlos", "Terceira"));

        List<Mensagem> resultado =
                repositorio.buscarUltimas(2);

        assertEquals(2, resultado.size());

        assertEquals(
                "Segunda",
                resultado.get(0).getTexto()
        );

        assertEquals(
                "Terceira",
                resultado.get(1).getTexto()
        );
    }
}
