package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Registra a sessão. Contas autenticadas são identificadas pelo id: um
     * novo login da mesma conta substitui a sessão anterior, que sai do
     * registro aqui mesmo (de forma atômica) e é devolvida para ser
     * encerrada. Sessões sem conta seguem a regra do nome único.
     *
     * @return a sessão substituída, se houver
     * @throws IllegalArgumentException se outra sessão usa o mesmo nome
     */
    public Optional<ConexaoCliente> registrar(ConexaoCliente cliente) {

        Objects.requireNonNull(cliente, "Cliente obrigatório.");

        Usuario usuario = Objects.requireNonNull(
                cliente.getUsuario(),
                "Usuário obrigatório."
        );

        // Verificação e inclusão atômicas: impede nomes duplicados.
        synchronized (clientes) {

            ConexaoCliente substituida = null;

            for (ConexaoCliente existente : clientes) {

                if (existente == cliente) {
                    throw new IllegalArgumentException(
                            "Já existe um usuário conectado com esse nome."
                    );
                }

                Long id = usuario.getId();

                if (id != null && id.equals(existente.getUsuario().getId())) {
                    substituida = existente;
                    continue;
                }

                if (existente.getUsuario().equals(usuario)) {
                    throw new IllegalArgumentException(
                            "Já existe um usuário conectado com esse nome."
                    );
                }
            }

            if (substituida != null) {
                removerPorIdentidade(substituida);
            }

            clientes.add(cliente);
            return Optional.ofNullable(substituida);
        }
    }

    /** {@code true} se esta mesma sessão (por identidade) está registrada. */
    public boolean contem(ConexaoCliente cliente) {
        synchronized (clientes) {
            for (ConexaoCliente existente : clientes) {
                if (existente == cliente) {
                    return true;
                }
            }
            return false;
        }
    }

    // Por identidade: duas sessões nunca são "iguais" entre si.
    private boolean removerPorIdentidade(ConexaoCliente cliente) {
        for (int i = 0; i < clientes.size(); i++) {
            if (clientes.get(i) == cliente) {
                clientes.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} se o cliente estava registrado (permite
     *         emitir um único aviso de saída por cliente)
     */
    public boolean remover(ConexaoCliente cliente) {
        synchronized (clientes) {
            return removerPorIdentidade(cliente);
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
        transmitirExceto(mensagem, null);
    }

    /**
     * Entrega somente às sessões autenticadas cujas contas estão em
     * {@code usuarioIds} (participantes de uma conversa privada ou grupo).
     * Sessões sem conta (visitantes e clientes legados) nunca recebem.
     */
    public void transmitirPara(Mensagem mensagem, Set<Long> usuarioIds) {

        Objects.requireNonNull(mensagem, "Mensagem obrigatória.");
        paraUsuarios(usuarioIds, cliente -> cliente.enviar(mensagem));
    }

    /** Ação de envio a uma sessão; falhas são registradas e não interrompem as demais. */
    @FunctionalInterface
    public interface Entrega {
        void executar(ConexaoCliente cliente) throws Exception;
    }

    /** Executa a entrega para cada sessão autenticada das contas indicadas. */
    public void paraUsuarios(Set<Long> usuarioIds, Entrega entrega) {

        Objects.requireNonNull(usuarioIds, "Destinatários obrigatórios.");

        synchronized (clientes) {

            for (ConexaoCliente cliente : clientes) {

                Long id = cliente.getUsuario().getId();

                if (id == null || !usuarioIds.contains(id)) {
                    continue;
                }

                try {
                    entrega.executar(cliente);
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

    /** Contas com sessão ativa. */
    public Set<Long> contasConectadas() {
        synchronized (clientes) {
            Set<Long> ids = new HashSet<>();
            for (ConexaoCliente cliente : clientes) {
                if (cliente.getUsuario().getId() != null) {
                    ids.add(cliente.getUsuario().getId());
                }
            }
            return ids;
        }
    }

    /**
     * Igual a {@link #transmitir}, sem enviar ao cliente indicado
     * ({@code null} para enviar a todos).
     */
    public void transmitirExceto(Mensagem mensagem, ConexaoCliente excluido) {

        Objects.requireNonNull(mensagem, "Mensagem obrigatória.");

        synchronized (clientes) {

            for (ConexaoCliente cliente : clientes) {

                if (cliente == excluido) {
                    continue;
                }

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
