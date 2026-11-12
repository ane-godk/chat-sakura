package br.unicesumar.chat.tls;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Certificados temporários para os testes, gerados uma vez por execução com
 * o {@code keytool} do próprio JDK (chaves EC P-256), numa pasta temporária
 * apagada ao final. Nada é gravado no projeto.
 *
 * <ul>
 *   <li>{@code servidor.p12}: CN=localhost, SAN localhost e 127.0.0.1 (válido);</li>
 *   <li>{@code expirado.p12}: mesmo SAN, validade encerrada há dias;</li>
 *   <li>{@code nome-errado.p12}: SAN somente outro.exemplo;</li>
 *   <li>{@code estranho.p12}: SAN correto, mas fora do truststore;</li>
 *   <li>{@code confianca.p12}: truststore do cliente com os certificados
 *       públicos de servidor, expirado e nome-errado.</li>
 * </ul>
 */
public final class CertificadosTeste {

    /** Senha dos arquivos temporários de teste (nunca usada fora daqui). */
    public static final String SENHA = "senha-somente-teste";

    private static Path pasta;

    private CertificadosTeste() {
    }

    public static synchronized Path pasta() {
        if (pasta == null) {
            try {
                pasta = gerar();
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException("Falha ao gerar certificados de teste: " + e.getMessage(), e);
            }
            Path criada = pasta;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> apagar(criada)));
        }
        return pasta;
    }

    public static Path arquivo(String nome) {
        return pasta().resolve(nome);
    }

    /** Contexto TLS do servidor com o keystore indicado. */
    public static SSLContext servidor(String keystore) throws Exception {
        return ContextoTls.servidor(arquivo(keystore), SENHA.toCharArray());
    }

    /** Contexto TLS do cliente que confia em servidor, expirado e nome-errado. */
    public static SSLContext cliente() throws Exception {
        return ContextoTls.cliente(arquivo("confianca.p12"), SENHA.toCharArray());
    }

    private static Path gerar() throws IOException, InterruptedException {
        Path destino = Files.createTempDirectory("chat-sakura-tls-");
        String san = "SAN=dns:localhost,ip:127.0.0.1";

        par(destino, "servidor", san, null);
        par(destino, "expirado", san, "-10d");
        par(destino, "nome-errado", "SAN=dns:outro.exemplo", null);
        par(destino, "estranho", san, null);

        for (String nome : List.of("servidor", "expirado", "nome-errado")) {
            keytool(destino, "-exportcert", "-rfc", "-alias", nome, "-keystore", nome + ".p12",
                    "-storepass", SENHA, "-file", nome + ".crt");
            keytool(destino, "-importcert", "-noprompt", "-alias", nome, "-file", nome + ".crt",
                    "-keystore", "confianca.p12", "-storetype", "PKCS12", "-storepass", SENHA);
        }
        return destino;
    }

    private static void par(Path destino, String nome, String san, String inicio) throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>(List.of("-genkeypair", "-alias", nome, "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=" + (nome.equals("nome-errado") ? "outro.exemplo" : "localhost"),
                "-ext", san, "-validity", "2", "-keystore", nome + ".p12", "-storetype", "PKCS12",
                "-storepass", SENHA, "-keypass", SENHA));
        if (inicio != null) {
            comando.add("-startdate");
            comando.add(inicio);
        }
        keytool(destino, comando.toArray(String[]::new));
    }

    private static void keytool(Path pasta, String... argumentos) throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>();
        comando.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        comando.addAll(List.of(argumentos));
        Process processo = new ProcessBuilder(comando).directory(pasta.toFile()).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!processo.waitFor(60, TimeUnit.SECONDS) || processo.exitValue() != 0) {
            throw new IOException("keytool falhou: " + saida);
        }
    }

    private static void apagar(Path pasta) {
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // Pasta temporária: o sistema limpa depois.
        }
    }
}
