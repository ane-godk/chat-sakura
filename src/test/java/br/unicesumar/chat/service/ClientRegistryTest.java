
package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientRegistryTest {

    private static final class ClienteFalso
            implements ConexaoCliente {

        private final Usuario usuario;

        ClienteFalso(String nome) {
            this.usuario = new Usuario(nome);
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public void enviar(Mensagem mensagem) {
            // Cliente simulado: não utiliza rede.
        }
    }

    @Test
    void deveRegistrarClientesDiferentes() {

        ClientRegistry registro = new ClientRegistry();

        registro.registrar(new ClienteFalso("Ana"));
        registro.registrar(new ClienteFalso("Bia"));

        assertEquals(2, registro.quantidade());
    }

    @Test
    void deveRejeitarNomesDuplicados() {

        ClientRegistry registro = new ClientRegistry();

        registro.registrar(new ClienteFalso("Ana"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registro.registrar(
                        new ClienteFalso("ana")
                )
        );

        assertEquals(1, registro.quantidade());
    }

    @Test
    void deveRemoverCliente() {

        ClientRegistry registro = new ClientRegistry();

        ClienteFalso cliente = new ClienteFalso("Ana");

        registro.registrar(cliente);
        registro.remover(cliente);

        assertEquals(0, registro.quantidade());
    }

    @Test
    void deveRetornarCopiaDaLista() {

        ClientRegistry registro = new ClientRegistry();

        registro.registrar(new ClienteFalso("Ana"));

        List<ConexaoCliente> copia =
                registro.listarClientes();

        copia.clear();

        assertEquals(1, registro.quantidade());
    }
}
