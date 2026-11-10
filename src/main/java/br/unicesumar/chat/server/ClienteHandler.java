
package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import br.unicesumar.chat.service.AutenticacaoException;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ConexaoCliente;
import br.unicesumar.chat.service.ServicoAutenticacao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClienteHandler
        implements Runnable, ConexaoCliente {

    static final int QUANTIDADE_HISTORICO = 20;

    private final Socket socket;
    private final ChatService chatService;

    // Protegidos pelo monitor deste handler (this).
    private final List<Mensagem> pendentes = new ArrayList<>();
    private boolean historicoEnviado;

    static final String MENSAGEM_NOME_RESERVADO =
            "SISTEMA: Não foi possível entrar: este nome pertence a uma conta"
                    + " cadastrada. Entre com login e senha em um cliente atualizado.";

    /** Pode ser nulo: servidor sem contas (somente modo legado). */
    private final ServicoAutenticacao autenticacao;

    private LeitorLinhas entrada;
    private BufferedWriter saida;

    // Identidade da sessão: definida somente pelo servidor, uma única vez,
    // a partir do apelido (modo legado) ou da conta autenticada.
    private Usuario usuario;

    public ClienteHandler(
            Socket socket,
            ChatService chatService
    ) {
        this(socket, chatService, null);
    }

    public ClienteHandler(
            Socket socket,
            ChatService chatService,
            ServicoAutenticacao autenticacao
    ) {
        this.socket = Objects.requireNonNull(
                socket,
                "Socket obrigatório."
        );

        this.chatService = Objects.requireNonNull(
                chatService,
                "Serviço obrigatório."
        );

        this.autenticacao = autenticacao;
    }

    @Override
    public void run() {

        boolean registrado = false;

        try {

            entrada = new LeitorLinhas(
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    ),
                    ProtocoloSakura.TAMANHO_MAXIMO_LINHA
            );

            saida = new BufferedWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            enviarLinha(
                    "SISTEMA: Bem-vindo ao Chat Sakura!"
            );

            enviarLinha(
                    "SISTEMA: Informe seu nome de usuário:"
            );

            String nome;

            try {
                nome = entrada.lerLinha();
            } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                enviarLinha("SISTEMA: Nome inválido: linha longa demais.");
                return;
            }

            if (nome == null) {
                return;
            }

            if (ProtocoloSakura.NEGOCIACAO.equals(nome)) {

                // Cliente atualizado: a identidade vem da conta.
                usuario = autenticarSessao();

                if (usuario == null) {
                    return;
                }

            } else {

                // Cliente legado ou visitante: o apelido não pode ser
                // o nome de uma conta cadastrada.
                try {
                    usuario = new Usuario(nome);
                } catch (IllegalArgumentException e) {
                    enviarLinha(
                            "SISTEMA: Nome inválido: " + e.getMessage()
                    );
                    return;
                }

                if (autenticacao != null
                        && autenticacao.nomeReservado(usuario.getNome())) {
                    enviarLinha(MENSAGEM_NOME_RESERVADO);
                    return;
                }
            }

            List<Mensagem> historico;

            try {
                historico = chatService.conectarCliente(
                        this,
                        QUANTIDADE_HISTORICO
                );
                registrado = true;
            } catch (IllegalArgumentException e) {
                enviarLinha(
                        "SISTEMA: Não foi possível entrar: "
                                + e.getMessage()
                );
                return;
            } catch (IllegalStateException e) {
                enviarLinha(
                        "SISTEMA: Não foi possível carregar o histórico."
                );
                System.err.println(
                        "Erro de persistência: " + e.getMessage()
                );
                return;
            }

            // Broadcasts recebidos a partir daqui ficam pendentes
            // até que a confirmação e o histórico sejam enviados.
            enviarLinha(
                    "SISTEMA: Conectado como " + usuario.getNome()
            );

            enviarHistorico(historico);

            liberarMensagensPendentes();

            System.out.println(
                    usuario.getNome()
                            + " entrou. Clientes: "
                            + chatService.contarClientes()
            );

            while (true) {

                // Depois da entrada, toda linha é conteúdo do chat: comandos
                // de autenticação não trocam a identidade da sessão.
                String texto;

                try {
                    texto = entrada.lerLinha();
                } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                    enviarLinha(
                            "SISTEMA: Mensagem inválida: a linha excede o"
                                    + " limite do protocolo."
                    );
                    continue;
                }

                if (texto == null) {
                    break;
                }

                texto = texto.trim();

                if (texto.equalsIgnoreCase("/sair")) {
                    break;
                }

                if (texto.isEmpty()) {
                    continue;
                }

                try {

                    chatService.enviarMensagem(
                            usuario,
                            texto
                    );

                } catch (IllegalArgumentException e) {

                    enviarLinha(
                            "SISTEMA: Mensagem inválida: "
                                    + e.getMessage()
                    );

                } catch (IllegalStateException e) {

                    enviarLinha(
                            "SISTEMA: Não foi possível salvar a mensagem."
                    );

                    System.err.println(
                            "Erro de persistência: "
                                    + e.getMessage()
                    );
                }
            }

        } catch (IOException e) {

            System.err.println(
                    "Conexão encerrada: " + e.getMessage()
            );

        } finally {

            if (registrado) {

                // Também emite o aviso de saída (uma única vez).
                chatService.desconectarCliente(this);

                System.out.println(
                        usuario.getNome()
                                + " saiu. Clientes: "
                                + chatService.contarClientes()
                );
            }

            try {
                socket.close();
            } catch (IOException e) {
                System.err.println(
                        "Erro ao fechar socket: " + e.getMessage()
                );
            }
        }
    }

    /**
     * Fase de autenticação (ver {@link ProtocoloSakura}). Permite novas
     * tentativas até o limite; a senha recebida é apagada após cada uma.
     *
     * @return o participante da conta autenticada, ou {@code null} se a
     *         conexão deve ser encerrada
     */
    private Usuario autenticarSessao() throws IOException {

        if (autenticacao == null) {
            enviarLinha(ProtocoloSakura.negociacaoRecusada(
                    "Este servidor não oferece contas. Entre como visitante."));
            return null;
        }

        enviarLinha(ProtocoloSakura.NEGOCIACAO_ACEITA);

        for (int tentativa = 1; tentativa <= ProtocoloSakura.TENTATIVAS_MAXIMAS; tentativa++) {

            String linha;

            try {
                linha = entrada.lerLinha();
            } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                linha = "";
            }

            if (linha == null) {
                return null;
            }

            ProtocoloSakura.PedidoAutenticacao pedido;

            try {
                pedido = ProtocoloSakura.lerPedido(linha);
            } catch (IllegalArgumentException e) {
                enviarLinha(ProtocoloSakura.respostaErro(
                        "COMANDO_INVALIDO", "Comando de autenticação inválido."));
                continue;
            }

            try {
                PerfilUsuario perfil = pedido.isCadastro()
                        ? autenticacao.cadastrar(pedido.getLogin(), pedido.getApelido(), pedido.getSenha())
                        : autenticacao.autenticar(pedido.getLogin(), pedido.getSenha());

                enviarLinha(ProtocoloSakura.respostaSucesso(perfil));

                return perfil.comoUsuario();

            } catch (AutenticacaoException e) {
                enviarLinha(ProtocoloSakura.respostaErro(e.getCodigo().name(), e.getMessage()));

            } finally {
                pedido.limpar();
            }
        }

        enviarLinha(ProtocoloSakura.respostaErro(
                "TENTATIVAS_ESGOTADAS", "Muitas tentativas. Conecte-se novamente."));

        return null;
    }

    private void enviarHistorico(List<Mensagem> historico)
            throws IOException {

        enviarLinha(
                "SISTEMA: ===== HISTÓRICO RECENTE ====="
        );

        // Escrita direta: enviar() enfileiraria até o fim do histórico.
        for (Mensagem mensagem : historico) {
            enviarLinha(mensagem.formatar());
        }

        enviarLinha(
                "SISTEMA: ===== FIM DO HISTÓRICO ====="
        );
    }

    private synchronized void enviarLinha(String texto)
            throws IOException {

        if (saida == null) {
            throw new IOException(
                    "Canal de saída não inicializado."
            );
        }

        saida.write(texto);
        saida.newLine();
        saida.flush();
    }

    /**
     * Envia as mensagens recebidas durante a conexão, na ordem de
     * chegada, e passa a entregar os próximos broadcasts diretamente.
     */
    private synchronized void liberarMensagensPendentes()
            throws IOException {

        for (Mensagem mensagem : pendentes) {
            enviarLinha(mensagem.formatar());
        }

        pendentes.clear();
        historicoEnviado = true;
    }

    @Override
    public synchronized void enviar(Mensagem mensagem)
            throws IOException {

        if (!historicoEnviado) {
            // Apenas enfileira (sem I/O), sem atrasar o broadcast.
            pendentes.add(mensagem);
            return;
        }

        enviarLinha(mensagem.formatar());
    }

    @Override
    public Usuario getUsuario() {
        return usuario;
    }
}
