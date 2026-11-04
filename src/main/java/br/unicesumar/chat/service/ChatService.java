package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class ChatService {

    private final MensagemRepository repositorio;
    private final ClientRegistry registro;
    private final Clock relogio;

    private final Object bloqueioMensagens = new Object();

    public ChatService(
            MensagemRepository repositorio,
            ClientRegistry registro,
            Clock relogio
    ) {

        this.repositorio = Objects.requireNonNull(
                repositorio,
                "Repositório obrigatório."
        );

        this.registro = Objects.requireNonNull(
                registro,
                "Registro de clientes obrigatório."
        );

        this.relogio = Objects.requireNonNull(
                relogio,
                "Relógio obrigatório."
        );
    }

    public Mensagem enviarMensagem(
            Usuario remetente,
            String conteudo
    ) {

        Objects.requireNonNull(
                remetente,
                "Remetente obrigatório."
        );

        Mensagem mensagem = new Mensagem(
                remetente,
                conteudo,
                LocalDateTime.now(relogio)
        );

        synchronized (bloqueioMensagens) {

            Mensagem persistida = repositorio.salvar(mensagem);

            List<ConexaoCliente> destinatarios =
                    registro.listarClientes();

            for (ConexaoCliente cliente : destinatarios) {

                try {
                    cliente.enviar(persistida);
                } catch (Exception e) {
                    System.err.println(
                            "Falha no envio para "
                                    + cliente.getUsuario().getNome()
                                    + ": "
                                    + e.getMessage()
                    );
                }
            }

            return persistida;
        }
    }

    public List<Mensagem> buscarHistorico(int quantidade) {
        return repositorio.buscarUltimas(quantidade);
    }

    public void registrarCliente(ConexaoCliente cliente) {
        registro.registrar(cliente);
    }

    public void removerCliente(ConexaoCliente cliente) {
        registro.remover(cliente);
    }

    public int contarClientes() {
        return registro.quantidade();
    }
}