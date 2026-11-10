package br.unicesumar.chat.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Hash de senhas com PBKDF2-HMAC-SHA256 ({@code javax.crypto}).
 *
 * <ul>
 *   <li>600.000 iterações (recomendação OWASP para PBKDF2-HMAC-SHA256);</li>
 *   <li>sal aleatório de 16 bytes por conta, gerado com {@link SecureRandom};</li>
 *   <li>hash derivado de 32 bytes (256 bits).</li>
 * </ul>
 *
 * O hash é guardado como {@code pbkdf2-sha256$<iterações>$<base64>}: o número
 * de iterações acompanha cada conta e pode aumentar no futuro sem invalidar
 * as existentes. A comparação usa {@link MessageDigest#isEqual}, de tempo
 * constante. Nenhum valor derivado é registrado em log.
 */
public final class HashSenha {

    public static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    public static final String PREFIXO = "pbkdf2-sha256";

    public static final int ITERACOES_PADRAO = 600_000;
    public static final int BYTES_SAL = 16;
    public static final int BYTES_HASH = 32;

    /** Limites aceitos ao ler um hash (protege contra valores adulterados). */
    static final int ITERACOES_MINIMAS = 1_000;
    static final int ITERACOES_MAXIMAS = 10_000_000;

    /** Credenciais derivadas para gravação; {@code toString} não as expõe. */
    public record Credenciais(String hash, String sal) {
        @Override
        public String toString() {
            return "Credenciais[****]";
        }
    }

    private final int iteracoes;
    private final SecureRandom aleatorio;

    // Sal fixo usado só para igualar o tempo de logins com conta inexistente.
    private final byte[] salFicticio;

    public HashSenha() {
        this(ITERACOES_PADRAO);
    }

    /** @param iteracoes custo da derivação (valores baixos só em testes) */
    public HashSenha(int iteracoes) {
        if (iteracoes < ITERACOES_MINIMAS || iteracoes > ITERACOES_MAXIMAS) {
            throw new IllegalArgumentException("Número de iterações fora do intervalo permitido.");
        }
        this.iteracoes = iteracoes;
        this.aleatorio = new SecureRandom();
        this.salFicticio = new byte[BYTES_SAL];
        aleatorio.nextBytes(salFicticio);
    }

    public int iteracoes() {
        return iteracoes;
    }

    /** Deriva o hash com um sal novo. Não altera nem guarda a senha. */
    public Credenciais gerar(char[] senha) {

        Objects.requireNonNull(senha, "Senha obrigatória.");

        byte[] sal = new byte[BYTES_SAL];
        aleatorio.nextBytes(sal);

        byte[] derivado = derivar(senha, sal, iteracoes);

        try {
            return new Credenciais(
                    PREFIXO + "$" + iteracoes + "$" + Base64.getEncoder().encodeToString(derivado),
                    Base64.getEncoder().encodeToString(sal)
            );
        } finally {
            Arrays.fill(derivado, (byte) 0);
        }
    }

    /**
     * @return {@code true} se a senha corresponde; {@code false} também para
     *         hash ou sal malformados (sem lançar exceção com os dados)
     */
    public boolean verificar(char[] senha, String hashArmazenado, String salArmazenado) {

        if (senha == null || hashArmazenado == null || salArmazenado == null) {
            return false;
        }

        String[] partes = hashArmazenado.split("\\$", -1);

        if (partes.length != 3 || !PREFIXO.equals(partes[0])) {
            return false;
        }

        byte[] esperado;
        byte[] sal;
        int custo;

        try {
            custo = Integer.parseInt(partes[1]);
            esperado = Base64.getDecoder().decode(partes[2]);
            sal = Base64.getDecoder().decode(salArmazenado);
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (custo < ITERACOES_MINIMAS || custo > ITERACOES_MAXIMAS
                || esperado.length != BYTES_HASH || sal.length != BYTES_SAL) {
            return false;
        }

        byte[] calculado = derivar(senha, sal, custo);

        try {
            return MessageDigest.isEqual(calculado, esperado);
        } finally {
            Arrays.fill(calculado, (byte) 0);
        }
    }

    /**
     * Mesmo custo de uma verificação real, sem resultado: usado quando o
     * login não existe, para que o tempo de resposta não revele contas.
     */
    public void simularVerificacao(char[] senha) {
        byte[] descartado = derivar(senha == null ? new char[0] : senha, salFicticio, iteracoes);
        Arrays.fill(descartado, (byte) 0);
    }

    private static byte[] derivar(char[] senha, byte[] sal, int custo) {

        PBEKeySpec especificacao = new PBEKeySpec(senha, sal, custo, BYTES_HASH * 8);

        try {
            return SecretKeyFactory.getInstance(ALGORITMO)
                    .generateSecret(especificacao)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            // Mensagem fixa: nunca inclui a senha.
            throw new IllegalStateException("PBKDF2 indisponível nesta JVM.", e);
        } finally {
            especificacao.clearPassword();
        }
    }
}
