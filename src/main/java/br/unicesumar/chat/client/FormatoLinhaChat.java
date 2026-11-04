package br.unicesumar.chat.client;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifica uma linha recebida do servidor para fins de exibição.
 *
 * Não depende de Swing e não altera o texto: os trechos devolvidos,
 * concatenados, reproduzem exatamente a linha original. Qualquer linha
 * que não siga um formato conhecido é classificada como {@link Tipo#OUTRO}
 * e exibida com o estilo padrão.
 */
final class FormatoLinhaChat {

    static final String PREFIXO_SISTEMA = "SISTEMA: ";

    static final String INICIO_HISTORICO =
            "SISTEMA: ===== HISTÓRICO RECENTE =====";

    static final String FIM_HISTORICO =
            "SISTEMA: ===== FIM DO HISTÓRICO =====";

    /**
     * Formato produzido por {@code Mensagem.formatar()} no servidor:
     * {@code [dd/MM HH:mm] Nome: conteúdo}. O nome não tem espaços e
     * tem no máximo 30 caracteres, conforme as regras de {@code Usuario}.
     */
    private static final Pattern MENSAGEM = Pattern.compile(
            "^(\\[\\d{2}/\\d{2} \\d{2}:\\d{2}\\]) (\\S{1,30}): (.*)$",
            Pattern.DOTALL
    );

    enum Tipo {
        MENSAGEM,
        SISTEMA,
        SEPARADOR_HISTORICO,
        OUTRO
    }

    /**
     * Resultado da análise. Para {@link Tipo#MENSAGEM}, os trechos são:
     * {@code horario + " " + remetente + ": " + conteudo}. Para os demais
     * tipos, apenas {@code texto} é preenchido.
     */
    record Linha(
            Tipo tipo,
            String texto,
            String horario,
            String remetente,
            String conteudo
    ) {

        boolean enviadaPor(String nomeUsuario) {
            return tipo == Tipo.MENSAGEM
                    && nomeUsuario != null
                    && remetente.equalsIgnoreCase(nomeUsuario.trim());
        }
    }

    private FormatoLinhaChat() {
    }

    static Linha analisar(String linha) {

        String texto = Objects.requireNonNullElse(linha, "");

        if (texto.equals(INICIO_HISTORICO) || texto.equals(FIM_HISTORICO)) {
            return new Linha(Tipo.SEPARADOR_HISTORICO, texto, null, null, null);
        }

        // Somente linhas que COMEÇAM com o prefixo são do sistema.
        // Mensagens de usuários sempre começam pelo horário.
        if (texto.startsWith(PREFIXO_SISTEMA)) {
            return new Linha(Tipo.SISTEMA, texto, null, null, null);
        }

        Matcher partes = MENSAGEM.matcher(texto);

        if (partes.matches()) {
            return new Linha(
                    Tipo.MENSAGEM,
                    texto,
                    partes.group(1),
                    partes.group(2),
                    partes.group(3)
            );
        }

        return new Linha(Tipo.OUTRO, texto, null, null, null);
    }

    /**
     * Índice estável de cor para um remetente, sem diferenciar
     * maiúsculas de minúsculas (como a regra de nomes do servidor).
     */
    static int indiceCor(String remetente, int quantidadeCores) {

        if (quantidadeCores <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade de cores deve ser positiva."
            );
        }

        String chave = Objects.requireNonNullElse(remetente, "")
                .toLowerCase(Locale.ROOT);

        return Math.floorMod(chave.hashCode(), quantidadeCores);
    }
}
