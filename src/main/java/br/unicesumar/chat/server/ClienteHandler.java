
package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ConexaoCliente;

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

    private BufferedReader entrada;
    private BufferedWriter saida;
    private Usuario usuario;

    public ClienteHandler(
            Socket socket,
            ChatService chatService
    ) {
        this.socket = Objects.requireNonNull(
                socket,
                "Socket obrigatório."
        );

        this.chatService = Objects.requireNonNull(
                chatService,
                "Serviço obrigatório."
        );
    }

    @Override
    public void run() {

        boolean registrado = false;

        try {

            entrada = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                    )
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

            String nome = entrada.readLine();

            if (nome == null) {
                return;
            }

            try {
                usuario = new Usuario(nome);
            } catch (IllegalArgumentException e) {
                enviarLinha(
                        "SISTEMA: Nome inválido: " + e.getMessage()
                );
                return;
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

            String texto;

            while ((texto = entrada.readLine()) != null) {

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
