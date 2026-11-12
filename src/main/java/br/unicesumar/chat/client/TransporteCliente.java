package br.unicesumar.chat.client;

import br.unicesumar.chat.tls.ContextoTls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;

/**
 * Como o cliente abre a conexão: TLS (padrão) ou TCP sem TLS, este somente
 * quando escolhido explicitamente para desenvolvimento.
 *
 * No TLS, o certificado é validado (cadeia, nome/IP e datas) na própria
 * abertura, antes de qualquer dado ser enviado: uma falha encerra a conexão
 * sem enviar credenciais e sem tentar TCP puro.
 */
interface TransporteCliente {

    /** Prazo da negociação TLS. */
    int PRAZO_HANDSHAKE_MS = 10_000;

    /** A conexão segura não pôde ser estabelecida; a mensagem é para o usuário. */
    final class FalhaConexaoSegura extends IOException {
        private static final long serialVersionUID = 1L;

        FalhaConexaoSegura(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }

    /**
     * Abre e prepara a conexão já dentro de {@code bruto} (que permite
     * interromper a tentativa fechando-o).
     *
     * @return o socket a usar (o próprio {@code bruto} ou o TLS sobre ele)
     * @throws IOException com mensagem compreensível em falhas de TLS
     */
    Socket abrir(Socket bruto, String endereco, int porta, int prazoConexaoMs) throws IOException;

    boolean seguro();

    /** TCP sem TLS: somente desenvolvimento (servidor em CHAT_SAKURA_MODO=tcp-desenvolvimento). */
    TransporteCliente TCP_DESENVOLVIMENTO = new TransporteCliente() {
        @Override
        public Socket abrir(Socket bruto, String endereco, int porta, int prazoConexaoMs) throws IOException {
            bruto.connect(new InetSocketAddress(endereco, porta), prazoConexaoMs);
            return bruto;
        }

        @Override
        public boolean seguro() {
            return false;
        }
    };

    /** TLS com o truststore do ambiente ({@code CHAT_SAKURA_TRUSTSTORE}) ou, sem ele, o do JDK. */
    static TransporteCliente tls() {
        return tls(System.getenv());
    }

    static TransporteCliente tls(Map<String, String> ambiente) {
        String caminho = ambiente.get("CHAT_SAKURA_TRUSTSTORE");
        String senha = ambiente.get("CHAT_SAKURA_TRUSTSTORE_SENHA");
        return tls(caminho == null || caminho.isBlank() ? null : Path.of(caminho.strip()),
                senha == null ? null : senha.toCharArray());
    }

    /**
     * @param truststore certificados confiáveis ({@code null}: os do JDK)
     * @param senha      senha do truststore (opcional); copiada a cada conexão
     */
    static TransporteCliente tls(Path truststore, char[] senha) {
        char[] guardada = senha == null ? null : senha.clone();
        return new TransporteCliente() {
            @Override
            public Socket abrir(Socket bruto, String endereco, int porta, int prazoConexaoMs) throws IOException {
                SSLContext contexto;
                try {
                    contexto = ContextoTls.cliente(truststore, guardada == null ? null : guardada.clone());
                } catch (GeneralSecurityException | IOException e) {
                    throw new FalhaConexaoSegura("Não foi possível carregar o truststore do cliente: "
                            + e.getMessage(), e);
                }

                bruto.connect(new InetSocketAddress(endereco, porta), prazoConexaoMs);

                SSLSocket tls = (SSLSocket) contexto.getSocketFactory().createSocket(bruto, endereco, porta, true);
                ContextoTls.configurarCliente(tls, contexto);
                try {
                    tls.setSoTimeout(PRAZO_HANDSHAKE_MS);
                    tls.startHandshake();
                    ContextoTls.verificarValidade(tls.getSession());
                    tls.setSoTimeout(0);
                } catch (IOException e) {
                    fechar(tls);
                    throw new FalhaConexaoSegura(ContextoTls.explicar(e, endereco), e);
                }
                return tls;
            }

            @Override
            public boolean seguro() {
                return true;
            }
        };
    }

    private static void fechar(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // Já encerrado.
        }
    }

    static TransporteCliente de(boolean seguro) {
        return seguro ? tls() : TCP_DESENVOLVIMENTO;
    }

    static boolean mesmoNivel(TransporteCliente anterior, TransporteCliente novo) {
        Objects.requireNonNull(novo);
        return anterior == null || !anterior.seguro() || novo.seguro();
    }
}
