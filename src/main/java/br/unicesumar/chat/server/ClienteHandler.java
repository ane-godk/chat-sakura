
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
import java.util.List;
import java.util.Objects;

public final class ClienteHandler
        implements Runnable, ConexaoCliente {

    private final Socket socket;
    private final ChatService chatService;

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

            try {
                chatService.registrarCliente(this);
                registrado = true;
            } catch (IllegalArgumentException e) {
                enviarLinha(
                        "SISTEMA: Não foi possível entrar: "
                                + e.getMessage()
                );
                return;
            }

            enviarLinha(
                    "SISTEMA: Conectado como " + usuario.getNome()
            );

            enviarHistorico();

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

                chatService.removerCliente(this);

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

    private void enviarHistorico() throws IOException {

        List<Mensagem> historico =
                chatService.buscarHistorico(20);

        enviarLinha(
                "SISTEMA: ===== HISTÓRICO RECENTE ====="
        );

        for (Mensagem mensagem : historico) {
            enviar(mensagem);
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

    @Override
    public void enviar(Mensagem mensagem) throws IOException {
        enviarLinha(mensagem.formatar());
    }

    @Override
    public Usuario getUsuario() {
        return usuario;
    }
}
