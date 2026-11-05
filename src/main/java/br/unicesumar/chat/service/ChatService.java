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

            // Persiste antes de distribuir; o mesmo bloqueio garante
            // a ordem das mensagens e o histórico atômico na conexão.
            Mensagem persistida = repositorio.salvar(mensagem);

            registro.transmitir(persistida);

            return persistida;
        }
    }

    public List<Mensagem> buscarHistorico(int quantidade) {
        return repositorio.buscarUltimas(quantidade);
    }

    public void registrarCliente(ConexaoCliente cliente) {
        registro.registrar(cliente);
    }

    /**
     * Lê o histórico e registra o cliente de forma atômica em relação
     * ao envio de mensagens. Como {@link #enviarMensagem} usa o mesmo
     * bloqueio, toda mensagem ou está no histórico retornado, ou será
     * entregue ao cliente por broadcast — nunca nos dois, nunca em nenhum.
     */
    public List<Mensagem> registrarClienteComHistorico(
            ConexaoCliente cliente,
            int quantidade
    ) {

        synchronized (bloqueioMensagens) {

            List<Mensagem> historico =
                    repositorio.buscarUltimas(quantidade);

            registro.registrar(cliente);

            return historico;
        }
    }

    public void removerCliente(ConexaoCliente cliente) {
        registro.remover(cliente);
    }

    /**
     * Conexão completa de um participante: lê o histórico, registra o
     * cliente e avisa os demais sobre a entrada — tudo sob o mesmo
     * bloqueio das mensagens. Assim o histórico e o registro continuam
     * atômicos, e o aviso ocupa a posição correta na ordem da conversa.
     *
     * Um nome duplicado falha no registro, antes do aviso: conexões
     * recusadas não geram entrada. O próprio cliente não recebe o seu
     * aviso (ele já recebe a confirmação de conexão).
     */
    public List<Mensagem> conectarCliente(
            ConexaoCliente cliente,
            int quantidadeHistorico
    ) {

        synchronized (bloqueioMensagens) {

            List<Mensagem> historico =
                    repositorio.buscarUltimas(quantidadeHistorico);

            registro.registrar(cliente);

            registrarAvisoDePresenca(
                    Mensagem.entrada(cliente.getUsuario(), LocalDateTime.now(relogio)),
                    cliente
            );

            return historico;
        }
    }

    /**
     * Desconexão de um participante (normal ou inesperada). O aviso de
     * saída só é emitido se o cliente ainda estava registrado, o que evita
     * avisos duplicados. Nunca lança exceção, para não impedir o
     * fechamento do socket.
     */
    public void desconectarCliente(ConexaoCliente cliente) {

        synchronized (bloqueioMensagens) {

            if (!registro.remover(cliente)) {
                return;
            }

            registrarAvisoDePresenca(
                    Mensagem.saida(cliente.getUsuario(), LocalDateTime.now(relogio)),
                    null
            );
        }
    }

    /**
     * Grava o aviso e, somente se a gravação der certo, transmite-o.
     * Uma falha de persistência é registrada e não interrompe a conexão
     * nem a desconexão. Chamado sob {@code bloqueioMensagens}.
     */
    private void registrarAvisoDePresenca(
            Mensagem aviso,
            ConexaoCliente excluido
    ) {

        Mensagem persistido;

        try {
            persistido = repositorio.salvar(aviso);
        } catch (RuntimeException e) {
            System.err.println(
                    "Não foi possível registrar o aviso \""
                            + aviso.getRemetente().getNome() + " "
                            + aviso.getConteudo() + "\": " + e.getMessage()
            );
            return;
        }

        registro.transmitirExceto(persistido, excluido);
    }

    public int contarClientes() {
        return registro.quantidade();
    }
}