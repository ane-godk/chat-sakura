package br.unicesumar.chat.client;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Quebra automática de texto para exibição, por largura em pixels.
 *
 * Quebra entre palavras; uma palavra maior que a largura é dividida
 * por caracteres, sem separar pares substitutos (como emojis). Serve
 * apenas à apresentação: o texto original não é alterado.
 */
final class QuebraTexto {

    private QuebraTexto() {
    }

    static List<String> quebrar(String texto, FontMetrics metricas, int largura) {

        List<String> linhas = new ArrayList<>();
        int limite = Math.max(1, largura);

        StringBuilder atual = new StringBuilder();

        for (String palavra : texto.split(" ", -1)) {

            String candidata = atual.length() == 0
                    ? palavra
                    : atual + " " + palavra;

            if (metricas.stringWidth(candidata) <= limite) {
                atual.setLength(0);
                atual.append(candidata);
                continue;
            }

            if (atual.length() > 0) {
                linhas.add(atual.toString());
                atual.setLength(0);
            }

            if (metricas.stringWidth(palavra) <= limite) {
                atual.append(palavra);
                continue;
            }

            // Palavra longa demais: divide por caracteres (code points).
            int indice = 0;

            while (indice < palavra.length()) {

                int codigo = palavra.codePointAt(indice);
                String caractere = new String(Character.toChars(codigo));

                if (atual.length() > 0
                        && metricas.stringWidth(atual + caractere) > limite) {
                    linhas.add(atual.toString());
                    atual.setLength(0);
                }

                atual.append(caractere);
                indice += Character.charCount(codigo);
            }
        }

        if (atual.length() > 0 || linhas.isEmpty()) {
            linhas.add(atual.toString());
        }

        return linhas;
    }
}
