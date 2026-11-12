package br.unicesumar.chat.server;

import br.unicesumar.chat.tls.ContextoTls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Como o servidor aceita conexões.
 *
 * <ul>
 *   <li><b>TLS</b> (padrão e recomendado para uso em rede): exige
 *       {@code CHAT_SAKURA_TLS_KEYSTORE} e {@code CHAT_SAKURA_TLS_KEYSTORE_SENHA};
 *       sem eles o servidor não inicia (nunca cai para TCP puro sozinho).</li>
 *   <li><b>TCP sem TLS</b>: somente com {@code CHAT_SAKURA_MODO=tcp-desenvolvimento}.
 *       Escuta apenas no loopback (127.0.0.1) por padrão; outro endereço só se
 *       informado explicitamente em {@code CHAT_SAKURA_ENDERECO}, com aviso.</li>
 * </ul>
 *
 * @param contexto contexto TLS do servidor ({@code null} no modo TCP)
 * @param endereco interface de escuta ({@code null}: todas, somente em TLS)
 */
public record TransporteServidor(Modo modo, SSLContext contexto, InetAddress endereco) {

    public enum Modo { TLS, TCP_DESENVOLVIMENTO }

    static final String ORIENTACAO = "Configure CHAT_SAKURA_TLS_KEYSTORE e CHAT_SAKURA_TLS_KEYSTORE_SENHA (veja"
            + " o README, seção TLS), ou use CHAT_SAKURA_MODO=tcp-desenvolvimento apenas para testes locais.";

    public TransporteServidor {
        Objects.requireNonNull(modo);
        if (modo == Modo.TLS && contexto == null) {
            throw new IllegalArgumentException("Modo TLS exige um contexto TLS.");
        }
    }

    public static TransporteServidor tls(SSLContext contexto, InetAddress endereco) {
        return new TransporteServidor(Modo.TLS, contexto, endereco);
    }

    /** TCP sem TLS, escutando só no loopback. */
    public static TransporteServidor tcpDesenvolvimento() {
        return new TransporteServidor(Modo.TCP_DESENVOLVIMENTO, null, InetAddress.getLoopbackAddress());
    }

    public boolean seguro() {
        return modo == Modo.TLS;
    }

    /**
     * Lê o modo do ambiente.
     *
     * @throws IllegalStateException se o modo TLS não puder ser configurado
     *                               (mensagem sem senhas, com orientação)
     */
    public static TransporteServidor doAmbiente(Map<String, String> ambiente) {

        String modo = ambiente.getOrDefault("CHAT_SAKURA_MODO", "tls").strip().toLowerCase(Locale.ROOT);
        String endereco = ambiente.get("CHAT_SAKURA_ENDERECO");

        if (modo.equals("tcp-desenvolvimento")) {
            InetAddress escuta = endereco == null || endereco.isBlank()
                    ? InetAddress.getLoopbackAddress()
                    : resolver(endereco);
            return new TransporteServidor(Modo.TCP_DESENVOLVIMENTO, null, escuta);
        }

        if (!modo.equals("tls")) {
            throw new IllegalStateException("CHAT_SAKURA_MODO inválido: use \"tls\" ou \"tcp-desenvolvimento\".");
        }

        String keystore = ambiente.get("CHAT_SAKURA_TLS_KEYSTORE");
        String senha = ambiente.get("CHAT_SAKURA_TLS_KEYSTORE_SENHA");
        if (keystore == null || keystore.isBlank() || senha == null) {
            throw new IllegalStateException("O servidor usa TLS por padrão. " + ORIENTACAO);
        }

        try {
            SSLContext contexto = ContextoTls.servidor(Path.of(keystore.strip()), senha.toCharArray());
            return tls(contexto, endereco == null || endereco.isBlank() ? null : resolver(endereco));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Não foi possível carregar o certificado do servidor: "
                    + e.getMessage() + " " + ORIENTACAO);
        }
    }

    private static InetAddress resolver(String endereco) {
        try {
            return InetAddress.getByName(endereco.strip());
        } catch (UnknownHostException e) {
            throw new IllegalStateException("CHAT_SAKURA_ENDERECO inválido: " + endereco);
        }
    }

    /** Abre o socket de escuta conforme o modo. */
    public ServerSocket abrir(int porta) throws IOException {
        if (modo == Modo.TCP_DESENVOLVIMENTO) {
            return new ServerSocket(porta, 50, endereco);
        }
        SSLServerSocket servidor = (SSLServerSocket) contexto.getServerSocketFactory()
                .createServerSocket(porta, 50, endereco);
        ContextoTls.configurar(servidor, contexto);
        return servidor;
    }

    /** Descrição para o log de inicialização (sem segredos). */
    public String descricao() {
        String onde = endereco == null ? "todas as interfaces" : endereco.getHostAddress();
        return seguro()
                ? "TLS (" + String.join(", ", ContextoTls.protocolos(contexto)) + ") em " + onde
                : "TCP SEM TLS (somente desenvolvimento) em " + onde
                        + (endereco != null && !endereco.isLoopbackAddress()
                                ? " — ATENÇÃO: senhas e mensagens trafegam legíveis na rede" : "");
    }
}
