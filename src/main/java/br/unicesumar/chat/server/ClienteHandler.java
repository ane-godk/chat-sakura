
package br.unicesumar.chat.server;

import br.unicesumar.chat.domain.Mensagem;
import br.unicesumar.chat.domain.MensagemRepository;
import br.unicesumar.chat.domain.Usuario;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClienteHandler implements Runnable {

    private final Socket socket;
    private final Servidor servidor;
    private final MensagemRepository repositorio;

    private BufferedReader entrada;
    private BufferedWriter saida;

    private Usuario usuario;

    public ClienteHandler(
            Socket socket,
            Servidor servidor,
            MensagemRepository repositorio
    ) {

        this.socket = socket;
        this.servidor = servidor;
        this.repositorio = repositorio;
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

            enviarMensagem(
                    "SISTEMA: Bem-vindo ao Chat Sakura!"
            );

            enviarMensagem(
                    "SISTEMA: Informe seu nome de usuário:"
            );

            String nome = entrada.readLine();

            if (nome == null || nome.isBlank()) {
                enviarMensagem(
                        "SISTEMA: Nome de usuário inválido."
                );
                return;
            }

            usuario = new Usuario(nome.trim());

            servidor.adicionarCliente(this);
            registrado = true;

            enviarMensagem(
                    "SISTEMA: Conectado como "
                            + usuario.getNome()
            );

            enviarHistorico();

            Mensagem entradaUsuario = new Mensagem(
                    "Sistema",
                    usuario.getNome() + " entrou no chat."
            );

            servidor.broadcast(entradaUsuario);

            String texto;

            while ((texto = entrada.readLine()) != null) {

                texto = texto.trim();

                if (texto.equalsIgnoreCase("/sair")) {
                    break;
                }

                if (texto.isEmpty()) {
                    continue;
                }

                Mensagem mensagem = new Mensagem(
                        usuario.getNome(),
                        texto
                );

                repositorio.salvar(mensagem);

                servidor.broadcast(mensagem);

                System.out.println(mensagem);
            }

        } catch (IOException e) {

            System.err.println(
                    "Conexão encerrada: " + e.getMessage()
            );

        } finally {

            if (registrado) {

                servidor.removerCliente(this);

                Mensagem saidaUsuario = new Mensagem(
                        "Sistema",
                        usuario.getNome() + " saiu do chat."
                );

                servidor.broadcast(saidaUsuario);
            }

            try {
                socket.close();
            } catch (IOException e) {
                System.err.println(
                        "Erro ao fechar conexão: " + e.getMessage()
                );
            }
        }
    }

    private void enviarHistorico() throws IOException {

        List<Mensagem> historico =
                repositorio.buscarUltimas(20);

        enviarMensagem(
                "SISTEMA: ===== HISTÓRICO RECENTE ====="
        );

        for (Mensagem mensagem : historico) {
            enviarMensagem(mensagem.toString());
        }

        enviarMensagem(
                "SISTEMA: ===== FIM DO HISTÓRICO ====="
        );
    }

    public synchronized void enviarMensagem(String texto)
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

    public String getNomeUsuario() {

        if (usuario == null) {
            return "Desconhecido";
        }

        return usuario.getNome();
    }
}
