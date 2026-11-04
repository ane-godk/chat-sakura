
package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdbcMensagemRepositoryTest {

    @TempDir
    Path pastaTemporaria;

    @Test
    void deveSalvarMensagemComIdGerado() {

        Path arquivoBanco =
                pastaTemporaria.resolve("teste.db");

        JdbcMensagemRepository repositorio =
                new JdbcMensagemRepository(
                        new ConnectionFactory(
                                arquivoBanco.toString()
                        )
                );

        Mensagem mensagem = new Mensagem(
                new Usuario("Ana"),
                "Olá, Sakura!",
                LocalDateTime.of(2026, 11, 3, 15, 0)
        );

        Mensagem salva = repositorio.salvar(mensagem);

        assertNull(mensagem.getId());
        assertNotNull(salva.getId());
        assertTrue(salva.getId() > 0);
        assertEquals(
                mensagem.getConteudo(),
                salva.getConteudo()
        );
    }

    @Test
    void deveRecuperarUltimasMensagensEmOrdem() {

        Path arquivoBanco =
                pastaTemporaria.resolve("historico.db");

        JdbcMensagemRepository repositorio =
                new JdbcMensagemRepository(
                        new ConnectionFactory(
                                arquivoBanco.toString()
                        )
                );

        Usuario usuario = new Usuario("Ana");

        for (int i = 1; i <= 5; i++) {

            repositorio.salvar(
                    new Mensagem(
                            usuario,
                            "Mensagem " + i,
                            LocalDateTime.of(
                                    2026, 11, 3, 15, i
                            )
                    )
            );
        }

        List<Mensagem> historico =
                repositorio.buscarUltimas(3);

        assertEquals(3, historico.size());

        assertEquals(
                "Mensagem 3",
                historico.get(0).getConteudo()
        );

        assertEquals(
                "Mensagem 4",
                historico.get(1).getConteudo()
        );

        assertEquals(
                "Mensagem 5",
                historico.get(2).getConteudo()
        );
    }

    @Test
    void devePreservarMensagensAposReabrirRepositorio() {

        Path arquivoBanco =
                pastaTemporaria.resolve("persistencia.db");

        ConnectionFactory fabrica =
                new ConnectionFactory(
                        arquivoBanco.toString()
                );

        JdbcMensagemRepository primeiro =
                new JdbcMensagemRepository(fabrica);

        primeiro.salvar(
                new Mensagem(
                        new Usuario("Bia"),
                        "Mensagem persistente",
                        LocalDateTime.of(2026, 11, 3, 16, 0)
                )
        );

        JdbcMensagemRepository segundo =
                new JdbcMensagemRepository(fabrica);

        List<Mensagem> historico =
                segundo.buscarUltimas(10);

        assertEquals(1, historico.size());

        assertEquals(
                "Mensagem persistente",
                historico.get(0).getConteudo()
        );

        assertEquals(
                "Bia",
                historico.get(0)
                        .getRemetente()
                        .getNome()
        );
    }

    @Test
    void deveRejeitarQuantidadeNegativa() {

        Path arquivoBanco =
                pastaTemporaria.resolve("validacao.db");

        JdbcMensagemRepository repositorio =
                new JdbcMensagemRepository(
                        new ConnectionFactory(
                                arquivoBanco.toString()
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> repositorio.buscarUltimas(-1)
        );
    }
}
