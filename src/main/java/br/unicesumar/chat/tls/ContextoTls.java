package br.unicesumar.chat.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Configuração TLS do Chat Sakura, com as APIs padrão do Java 17 (JSSE).
 *
 * <ul>
 *   <li>Somente TLS 1.3 e TLS 1.2 (SSLv3, TLS 1.0 e 1.1 ficam desativados);
 *       as suítes de cifra são as padrão do JDK, que já excluem as fracas.</li>
 *   <li>Servidor: certificado e chave privada de um keystore (PKCS12 ou JKS),
 *       cuja senha vem de fora do código e é apagada depois do uso.</li>
 *   <li>Cliente: cadeia validada contra um truststore (ou o do JDK) e
 *       verificação do nome/IP do servidor (identificação de endpoint
 *       "HTTPS", que confere o SAN do certificado). Nunca há TrustManager
 *       permissivo nem verificação de nome desligada.</li>
 * </ul>
 */
public final class ContextoTls {

    /** Em ordem de preferência. */
    public static final List<String> PROTOCOLOS = List.of("TLSv1.3", "TLSv1.2");

    private ContextoTls() {
    }

    /**
     * Contexto do servidor a partir do keystore. A senha é apagada ao final.
     *
     * @throws GeneralSecurityException com mensagem sem a senha
     */
    public static SSLContext servidor(Path keystore, char[] senha) throws GeneralSecurityException, IOException {
        try {
            if (!Files.isRegularFile(keystore)) {
                throw new IOException("Keystore não encontrado: " + keystore);
            }
            KeyStore chaves;
            try {
                chaves = KeyStore.getInstance(keystore.toFile(), senha);
            } catch (IOException e) {
                if (e.getCause() instanceof UnrecoverableKeyException) {
                    throw new GeneralSecurityException("Senha do keystore incorreta.");
                }
                throw new IOException("Não foi possível ler o keystore (senha incorreta ou arquivo inválido).");
            }
            KeyManagerFactory fabrica = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            fabrica.init(chaves, senha);
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(fabrica.getKeyManagers(), null, null);
            return contexto;
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    /**
     * Contexto do cliente.
     *
     * @param truststore certificados confiáveis; {@code null} usa os do JDK
     * @param senha      senha do truststore (pode ser {@code null}); apagada ao final
     */
    public static SSLContext cliente(Path truststore, char[] senha) throws GeneralSecurityException, IOException {
        try {
            KeyStore confianca = null;
            if (truststore != null) {
                if (!Files.isRegularFile(truststore)) {
                    throw new IOException("Truststore não encontrado: " + truststore);
                }
                confianca = KeyStore.getInstance(truststore.toFile(), senha == null ? new char[0] : senha);
            }
            TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            fabrica.init(confianca);
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(null, fabrica.getTrustManagers(), null);
            return contexto;
        } finally {
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
        }
    }

    /** Protocolos aceitos que esta JVM suporta (TLS 1.3 só se disponível). */
    public static String[] protocolos(SSLContext contexto) {
        List<String> suportados = Arrays.asList(contexto.getSupportedSSLParameters().getProtocols());
        List<String> aceitos = new ArrayList<>();
        for (String protocolo : PROTOCOLOS) {
            if (suportados.contains(protocolo)) {
                aceitos.add(protocolo);
            }
        }
        if (aceitos.isEmpty()) {
            throw new IllegalStateException("Esta JVM não suporta TLS 1.2 nem TLS 1.3.");
        }
        return aceitos.toArray(String[]::new);
    }

    public static void configurar(SSLServerSocket servidor, SSLContext contexto) {
        servidor.setEnabledProtocols(protocolos(contexto));
        servidor.setNeedClientAuth(false);
        servidor.setUseClientMode(false);
    }

    /** Cliente: protocolos aceitos e verificação do nome (ou IP) do servidor. */
    public static void configurarCliente(SSLSocket socket, SSLContext contexto) {
        SSLParameters parametros = socket.getSSLParameters();
        parametros.setProtocols(protocolos(contexto));
        parametros.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parametros);
    }

    /**
     * Confere a validade (datas) do certificado do servidor. A validação PKIX
     * não examina as datas de um certificado autoassinado que está ele mesmo
     * no truststore; esta verificação cobre esse caso.
     */
    public static void verificarValidade(SSLSession sessao) throws SSLHandshakeException {
        try {
            Certificate[] cadeia = sessao.getPeerCertificates();
            if (cadeia.length > 0 && cadeia[0] instanceof X509Certificate certificado) {
                certificado.checkValidity();
            }
        } catch (CertificateExpiredException e) {
            throw handshake("O certificado do servidor expirou.", e);
        } catch (CertificateNotYetValidException e) {
            throw handshake("O certificado do servidor ainda não é válido.", e);
        } catch (SSLException e) {
            throw handshake("Não foi possível ler o certificado do servidor.", e);
        }
    }

    private static SSLHandshakeException handshake(String mensagem, Exception causa) {
        SSLHandshakeException erro = new SSLHandshakeException(mensagem);
        erro.initCause(causa);
        return erro;
    }

    /** Mensagem compreensível para uma falha de conexão segura (sem detalhes sensíveis). */
    public static String explicar(IOException erro, String endereco) {

        List<Throwable> causas = new ArrayList<>();
        for (Throwable t = erro; t != null && !causas.contains(t); t = t.getCause()) {
            causas.add(t);
        }
        String texto = String.join(" | ", causas.stream()
                .map(t -> String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT)).toList());

        if (causas.stream().anyMatch(t -> t instanceof CertificateExpiredException) || texto.contains("expirou")) {
            return "O certificado do servidor expirou. Peça ao responsável pelo servidor para renová-lo.";
        }
        if (causas.stream().anyMatch(t -> t instanceof CertificateNotYetValidException)
                || texto.contains("ainda não é válido")) {
            return "O certificado do servidor ainda não é válido (confira a data e a hora dos computadores).";
        }
        if (texto.contains("no subject alternative") || texto.contains("no name matching")) {
            return "O certificado do servidor não corresponde ao endereço \"" + endereco + "\". Conecte-se usando"
                    + " o nome ou o IP que constam no certificado.";
        }
        if (texto.contains("pkix path") || texto.contains("unable to find valid certification path")
                || causas.stream().anyMatch(t -> t instanceof CertPathValidatorException)) {
            return "O certificado do servidor não é confiável. Importe o certificado público do servidor no"
                    + " truststore do cliente (CHAT_SAKURA_TRUSTSTORE). A conexão foi encerrada sem enviar dados.";
        }
        if (texto.contains("unsupported or unrecognized ssl message") || texto.contains("plaintext connection")) {
            return "O servidor não respondeu com TLS (ele pode estar no modo de desenvolvimento sem TLS)."
                    + " A conexão foi encerrada sem enviar dados.";
        }
        if (texto.contains("protocol_version") || texto.contains("no appropriate protocol")) {
            return "O servidor não aceita TLS 1.2 nem TLS 1.3.";
        }
        if (causas.stream().anyMatch(t -> t instanceof SocketTimeoutException)) {
            return "O servidor não concluiu a negociação TLS a tempo.";
        }
        if (causas.stream().anyMatch(t -> t instanceof EOFException) || texto.contains("remote host terminated")) {
            return "O servidor encerrou a negociação TLS.";
        }
        if (texto.contains("truststore") || texto.contains("keystore")) {
            return erro.getMessage();
        }
        return "Falha na conexão segura (TLS).";
    }
}
