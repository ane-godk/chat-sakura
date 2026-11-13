package br.unicesumar.chat.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Forma de pesquisa de um texto: decomposição Unicode (NFKD), sem marcas
 * diacríticas, em minúsculas ({@link Locale#ROOT}) e com espaços repetidos
 * reduzidos a um. "Ação", "ACAO" e "ａçãｏ" (largura total) têm a mesma forma.
 *
 * O texto original nunca é alterado: a forma de pesquisa é derivada dele.
 */
public final class TextoBusca {

    /** Termo de pesquisa: de 2 a 100 caracteres na forma de pesquisa. */
    public static final int TERMO_MINIMO = 2;
    public static final int TERMO_MAXIMO = 100;

    /** Caracteres de contexto antes do termo no trecho exibido. */
    static final int CONTEXTO_ANTES = 40;
    static final int TRECHO_MAXIMO = 140;

    private TextoBusca() {
    }

    /** Trecho de uma mensagem, com a posição do termo encontrado (fim exclusivo; -1 se ausente). */
    public record Trecho(String texto, int inicio, int fim) {
    }

    public static String normalizar(String texto) {
        return mapear(texto).forma().toString();
    }

    /**
     * Termo de pesquisa validado.
     *
     * @throws IllegalArgumentException se ficar curto ou longo demais
     */
    public static String termo(String texto) {
        String forma = normalizar(texto == null ? "" : texto).strip();
        if (forma.length() < TERMO_MINIMO) {
            throw new IllegalArgumentException("Digite pelo menos " + TERMO_MINIMO + " caracteres para pesquisar.");
        }
        if (forma.length() > TERMO_MAXIMO) {
            throw new IllegalArgumentException("O termo de pesquisa pode ter no máximo " + TERMO_MAXIMO
                    + " caracteres.");
        }
        return forma;
    }

    /**
     * Trecho do texto original em torno da primeira ocorrência do termo
     * (já na forma de pesquisa), com reticências quando cortado.
     */
    public static Trecho trecho(String original, String termo) {
        Objects.requireNonNull(original);
        Mapa mapa = mapear(original);
        int posicao = termo == null || termo.isEmpty() ? -1 : mapa.forma().indexOf(termo);

        int inicioOriginal = -1;
        int fimOriginal = -1;
        if (posicao >= 0) {
            inicioOriginal = mapa.origem()[posicao];
            int ultimo = posicao + termo.length() - 1;
            fimOriginal = mapa.origem()[ultimo] + Character.charCount(original.codePointAt(mapa.origem()[ultimo]));
        }

        int corteInicio = 0;
        if (inicioOriginal > CONTEXTO_ANTES) {
            corteInicio = ajustar(original, inicioOriginal - CONTEXTO_ANTES);
        }
        int corteFim = Math.min(original.length(),
                Math.max(corteInicio + TRECHO_MAXIMO, fimOriginal < 0 ? 0 : fimOriginal));
        corteFim = ajustar(original, corteFim);

        String prefixo = corteInicio > 0 ? "…" : "";
        String sufixo = corteFim < original.length() ? "…" : "";
        String texto = prefixo + original.substring(corteInicio, corteFim) + sufixo;

        if (inicioOriginal < 0) {
            return new Trecho(texto, -1, -1);
        }
        int deslocamento = prefixo.length() - corteInicio;
        return new Trecho(texto, inicioOriginal + deslocamento, fimOriginal + deslocamento);
    }

    /** Não separa pares substitutos (emojis). */
    private static int ajustar(String texto, int indice) {
        if (indice > 0 && indice < texto.length() && Character.isLowSurrogate(texto.charAt(indice))) {
            return indice - 1;
        }
        return indice;
    }

    /** Forma de pesquisa e, para cada caractere dela, o índice de origem no texto original. */
    private record Mapa(StringBuilder forma, int[] origem) {
    }

    private static Mapa mapear(String texto) {
        StringBuilder forma = new StringBuilder(texto.length());
        int[] origem = new int[texto.length() * 4 + 4];
        int i = 0;
        while (i < texto.length()) {
            int ponto = texto.codePointAt(i);
            String parte;
            if (Character.isWhitespace(ponto) || Character.isSpaceChar(ponto)) {
                parte = forma.length() > 0 && forma.charAt(forma.length() - 1) == ' ' ? "" : " ";
            } else {
                String decomposta = Normalizer.normalize(new String(Character.toChars(ponto)), Normalizer.Form.NFKD);
                parte = semMarcas(semMarcas(decomposta).toLowerCase(Locale.ROOT));
            }
            for (int k = 0; k < parte.length(); k++) {
                if (forma.length() >= origem.length) {
                    origem = java.util.Arrays.copyOf(origem, origem.length * 2);
                }
                origem[forma.length()] = i;
                forma.append(parte.charAt(k));
            }
            i += Character.charCount(ponto);
        }
        return new Mapa(forma, origem);
    }

    private static String semMarcas(String texto) {
        StringBuilder saida = new StringBuilder(texto.length());
        texto.codePoints()
                .filter(c -> Character.getType(c) != Character.NON_SPACING_MARK
                        && Character.getType(c) != Character.ENCLOSING_MARK
                        && Character.getType(c) != Character.COMBINING_SPACING_MARK)
                .forEach(saida::appendCodePoint);
        return saida.toString();
    }
}
