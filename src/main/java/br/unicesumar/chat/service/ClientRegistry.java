package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClientRegistry {

    private final List<ConexaoCliente> clientes = new ArrayList<>();

    public synchronized void registrar(ConexaoCliente cliente) {

        Objects.requireNonNull(cliente, "Cliente obrigatório.");

        Usuario usuario = Objects.requireNonNull(
                cliente.getUsuario(),
                "Usuário obrigatório."
        );

        for (ConexaoCliente existente : clientes) {

            if (existente.getUsuario().equals(usuario)) {
                throw new IllegalArgumentException(
                        "Já existe um usuário conectado com esse nome."
                );
            }
        }

        clientes.add(cliente);
    }

    public synchronized void remover(ConexaoCliente cliente) {
        clientes.remove(cliente);
    }

    public synchronized int quantidade() {
        return clientes.size();
    }

    public synchronized List<ConexaoCliente> listarClientes() {
        return new ArrayList<>(clientes);
    }
}