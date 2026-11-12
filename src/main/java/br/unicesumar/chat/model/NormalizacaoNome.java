package br.unicesumar.chat.model;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Normalização Unicode única para comparar nomes (apelidos de contas,
 * visitantes, nomes reservados e sessões).
 *
 * <p><b>Chave de comparação</b> ({@link #chave}): NFKC, minúsculas com
 * {@link Locale#ROOT} e NFKC de novo (a conversão de caixa pode gerar formas
 * não normalizadas). Assim "Ana", "ANA", "Ａｎａ" (largura total) e "Ａna"
 * têm a mesma chave. A forma exibida continua a original (apenas em NFC).
 *
 * <p><b>Rejeitado em nomes novos</b> ({@link #problema}):
 * <ul>
 *   <li>caracteres de controle (Cc), de formatação invisíveis (Cf, como
 *       U+200B, U+200D, U+2060, U+FEFF, U+00AD, marcas de direção),
 *       não atribuídos, de uso privado e substitutos isolados;</li>
 *   <li>qualquer espaço ou separador Unicode (inclusive U+00A0, U+2007,
 *       U+202F e U+3000);</li>
 *   <li>mistura de escritas no mesmo nome (por exemplo latim com cirílico
 *       ou grego, a fonte mais comum de homóglifos como "Аna" com "А"
 *       cirílico). Japonês (han, hiragana e katakana) e coreano (hangul e
 *       han) contam como uma escrita.</li>
 * </ul>
 *
 * <p><b>Limite conhecido:</b> NFKC não resolve todos os homóglifos. Um nome
 * inteiro em cirílico que se pareça com um nome latino ("АНА") é aceito e
 * não colide com "ANA"; o cliente mostra quem tem conta e quem é visitante
 * pelo identificador da conta, não pela aparência do nome.
 */
public final class NormalizacaoNome {

    private static final Set<Character.UnicodeScript> NEUTRAS = EnumSet.of(
            Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED, Character.UnicodeScript.UNKNOWN);

    private static final Set<Character.UnicodeScript> JAPONES = EnumSet.of(
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA);

    private static final Set<Character.UnicodeScript> COREANO = EnumSet.of(
            Character.UnicodeScript.HAN, Character.UnicodeScript.HANGUL);

    private NormalizacaoNome() {
    }

    /** Chave de comparação (nunca exibida). */
    public static String chave(String nome) {
        Objects.requireNonNull(nome, "Nome obrigatório.");
        String compativel = Normalizer.normalize(nome.strip(), Normalizer.Form.NFKC);
        return Normalizer.normalize(compativel.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
    }

    /** {@code true} se os nomes são equivalentes após a normalização. */
    public static boolean equivalentes(String a, String b) {
        return chave(a).equals(chave(b));
    }

    /** Forma exibida estável (NFC, sem espaços nas pontas). */
    public static String exibicao(String nome) {
        return Normalizer.normalize(Objects.requireNonNull(nome).strip(), Normalizer.Form.NFC);
    }

    /**
     * @return a descrição do problema para o usuário, ou {@code null} se o
     *         nome não tem caracteres invisíveis, espaços nem mistura de escritas
     */
    public static String problema(String nome) {

        Set<Character.UnicodeScript> escritas = EnumSet.noneOf(Character.UnicodeScript.class);

        for (int i = 0; i < nome.length(); ) {
            int ponto = nome.codePointAt(i);
            i += Character.charCount(ponto);

            int tipo = Character.getType(ponto);

            if (tipo == Character.CONTROL || tipo == Character.FORMAT || tipo == Character.UNASSIGNED
                    || tipo == Character.PRIVATE_USE || tipo == Character.SURROGATE) {
                return "O nome contém caracteres invisíveis ou de controle.";
            }

            if (Character.isWhitespace(ponto) || Character.isSpaceChar(ponto)) {
                return "O nome não pode conter espaços.";
            }

            Character.UnicodeScript escrita = Character.UnicodeScript.of(ponto);
            if (!NEUTRAS.contains(escrita)) {
                escritas.add(escrita);
            }
        }

        if (escritas.size() > 1 && !JAPONES.containsAll(escritas) && !COREANO.containsAll(escritas)) {
            return "O nome não pode misturar alfabetos diferentes (por exemplo, latino e cirílico).";
        }

        return null;
    }
}
