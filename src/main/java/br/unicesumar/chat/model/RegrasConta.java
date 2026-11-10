package br.unicesumar.chat.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização e validação dos dados de uma conta. As mensagens de erro
 * podem ser exibidas ao usuário e nunca contêm a senha.
 */
public final class RegrasConta {

    public static final int LOGIN_MINIMO = 3;
    public static final int LOGIN_MAXIMO = 30;
    public static final int APELIDO_MAXIMO = Usuario.TAMANHO_MAXIMO_NOME;
    public static final int SENHA_MINIMA = 8;
    public static final int SENHA_MAXIMA = 128;

    public static final String AVATAR_PADRAO = "sakura";

    private static final Pattern LOGIN = Pattern.compile(
            "[a-z0-9._-]{" + LOGIN_MINIMO + "," + LOGIN_MAXIMO + "}");

    /** Letras (com acento), números, ponto, hífen e sublinhado. */
    private static final Pattern APELIDO = Pattern.compile(
            "[\\p{L}\\p{N}._-]{1," + APELIDO_MAXIMO + "}");

    private static final Pattern AVATAR = Pattern.compile("[a-z0-9-]{1,40}");

    private RegrasConta() {
    }

    /** Login sem diferenciar maiúsculas: guardado em minúsculas. */
    public static String normalizarLogin(String login) {

        String normalizado = login == null
                ? ""
                : login.strip().toLowerCase(Locale.ROOT);

        if (!LOGIN.matcher(normalizado).matches()) {
            throw new IllegalArgumentException(
                    "O login deve ter de " + LOGIN_MINIMO + " a " + LOGIN_MAXIMO
                            + " caracteres: letras sem acento, números, ponto,"
                            + " hífen ou sublinhado."
            );
        }

        return normalizado;
    }

    /** Apelido exibido no chat; forma NFC para comparações estáveis. */
    public static String normalizarApelido(String apelido) {

        String normalizado = apelido == null
                ? ""
                : Normalizer.normalize(apelido.strip(), Normalizer.Form.NFC);

        if (!APELIDO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException(
                    "O apelido deve ter de 1 a " + APELIDO_MAXIMO
                            + " caracteres: letras, números, ponto, hífen ou"
                            + " sublinhado, sem espaços."
            );
        }

        return normalizado;
    }

    /** Valida sem copiar a senha; a mensagem nunca a inclui. */
    public static void validarSenha(char[] senha) {

        if (senha == null
                || senha.length < SENHA_MINIMA
                || senha.length > SENHA_MAXIMA) {
            throw new IllegalArgumentException(
                    "A senha deve ter de " + SENHA_MINIMA + " a " + SENHA_MAXIMA
                            + " caracteres."
            );
        }

        for (char caractere : senha) {
            if (Character.isISOControl(caractere)) {
                throw new IllegalArgumentException(
                        "A senha contém caracteres de controle não permitidos."
                );
            }
        }
    }

    public static String validarAvatar(String avatarId) {

        if (avatarId == null || !AVATAR.matcher(avatarId).matches()) {
            throw new IllegalArgumentException("Identificador de avatar inválido.");
        }

        return avatarId;
    }
}
