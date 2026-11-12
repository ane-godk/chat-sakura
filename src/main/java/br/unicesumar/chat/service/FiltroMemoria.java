package br.unicesumar.chat.service;

import br.unicesumar.chat.model.MemoriaPersonagem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filtro determinístico aplicado ao resumo gerado pelo modelo antes de
 * gravá-lo como memória. Não depende de o modelo obedecer às instruções:
 *
 * <ul>
 *   <li>só aceita notas em lista ("- ...");</li>
 *   <li>descarta notas com dados sensíveis (e-mails, sequências longas de
 *       dígitos como telefones, CPF ou cartões, e termos como senha ou
 *       token);</li>
 *   <li>descarta notas que soam como instruções ou regras ("a partir de
 *       agora", "ignore", "sempre responda"...), para que uma mensagem
 *       maliciosa não vire regra permanente do personagem;</li>
 *   <li>limita a quantidade de notas e o tamanho total.</li>
 * </ul>
 */
final class FiltroMemoria {

    static final int NOTAS_MAXIMAS = 8;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern SENSIVEL = Pattern.compile(
            "\\b(senha|senhas|password|token|cpf|rg|cnpj|cart[aã]o de cr[eé]dito|n[uú]mero do cart[aã]o"
                    + "|conta banc[aá]ria|chave pix|pix)\\b");
    private static final Pattern INSTRUCAO = Pattern.compile(
            "(ignor[ae]|a partir de agora|de agora em diante|sempre responda|nunca responda|instru[cç]"
                    + "|\\bregra|\\bprompt|\\bsystem\\b|\\bsistema\\b|voc[eê] deve|finja|aja como"
                    + "|esque[cç]a|revele|obede[cç]a)");

    private FiltroMemoria() {
    }

    /** @return as notas aceitas, uma por linha, já limitadas (pode ser vazio) */
    static String filtrar(String bruto, boolean truncado) {

        List<String> linhas = new ArrayList<>(List.of(bruto.split("\n")));
        if (truncado && !linhas.isEmpty()) {
            // A última nota pode ter sido cortada no meio: descartada.
            linhas.remove(linhas.size() - 1);
        }

        StringBuilder aceitas = new StringBuilder();
        int quantidade = 0;

        for (String linha : linhas) {
            String nota = linha.strip();
            if (!(nota.startsWith("-") || nota.startsWith("•") || nota.startsWith("*"))) {
                continue;
            }
            nota = "- " + nota.substring(1).strip();
            if (nota.length() < 4 || sensivel(nota) || instrucao(nota)) {
                continue;
            }
            if (nota.length() > 200) {
                nota = nota.substring(0, 200).strip();
            }
            if (aceitas.length() + nota.length() + 1 > MemoriaPersonagem.RESUMO_MAXIMO
                    || quantidade >= NOTAS_MAXIMAS) {
                break;
            }
            aceitas.append(nota).append('\n');
            quantidade++;
        }

        return aceitas.toString().strip();
    }

    static boolean sensivel(String nota) {
        String minuscula = nota.toLowerCase(Locale.ROOT);
        if (EMAIL.matcher(nota).find() || SENSIVEL.matcher(minuscula).find()) {
            return true;
        }
        // Oito ou mais dígitos num trecho (telefone, CPF, cartão), mesmo com separadores.
        int digitos = 0;
        for (char c : nota.toCharArray()) {
            if (Character.isDigit(c)) {
                digitos++;
                if (digitos >= 8) {
                    return true;
                }
            } else if (c != ' ' && c != '.' && c != '-' && c != '/' && c != '(' && c != ')') {
                digitos = 0;
            }
        }
        return false;
    }

    static boolean instrucao(String nota) {
        return INSTRUCAO.matcher(nota.toLowerCase(Locale.ROOT)).find();
    }
}
