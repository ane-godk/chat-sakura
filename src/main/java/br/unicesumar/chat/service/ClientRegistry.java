package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registro thread-safe dos clientes conectados.
 *
 * Todo acesso à lista é feito dentro de {@code synchronized (clientes)},
 * que é o mesmo monitor usado internamente por
 * {@link Collections#synchronizedList}.
 */
public final class ClientRegistry {

    private final List<ConexaoCliente> clientes =
            Collections.synchronizedList(new ArrayList<>());

    public void registrar(ConexaoCliente cliente) {

        Objects.requireNonNull(cliente, "Cliente obrigatório.");

        Usuario usuario = Objects.requireNonNull(
                cliente.getUsuario(),
                "Usuário obrigatório."
        );

        // Verificação e inclusão atômicas: impede nomes duplicados.
        synchronized (clientes) {

            for (ConexaoCliente existente : clientes) {

                if (existente.getUsuario().equals(usuario)) {
                    throw new IllegalArgumentException(
                            "Já existe um usuário conectado com esse nome."
                    );
                }
            }

            clientes.add(cliente);
        }
    }

    public void remover(ConexaoCliente cliente) {
        synchronized (clientes) {
            clientes.remove(cliente);
        }
    }

    public int quantidade() {
        synchronized (clientes) {
            return clientes.size();
        }
    }

    public List<ConexaoCliente> listarClientes() {
        synchronized (clientes) {
            return new ArrayList<>(clientes);
        }
    }

    /**
     * Envia a mensagem a todos os clientes, percorrendo a lista dentro
     * de {@code synchronized (clientes)}. A falha de um cliente é
     * registrada e não interrompe o envio aos demais.
     *
     * Implementações de {@link ConexaoCliente#enviar} não devem
     * registrar nem remover clientes, pois isso alteraria a lista
     * durante a iteração.
     */
    public void transmitir(Mensagem mensagem) {

        Objects.requireNonNull(mensagem, "Mensagem obrigatória.");

        synchronized (clientes) {

            for (ConexaoCliente cliente : clientes) {

                try {
                    cliente.enviar(mensagem);
                } catch (Exception e) {
                    System.err.println(
                            "Falha no envio para "
                                    + cliente.getUsuario().getNome()
                                    + ": "
                                    + e.getMessage()
                    );
                }
            }
        }
    }
}
