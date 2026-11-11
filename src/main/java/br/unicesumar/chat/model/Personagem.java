package br.unicesumar.chat.model;

import java.util.Objects;

/**
 * Personagem fictício interpretado por IA, criado por um usuário. Imutável.
 * O nome segue as regras de apelido (aparece como remetente das respostas).
 */
public final class Personagem {

    public static final int PERSONALIDADE_MAXIMA = 400;
    public static final int CONTEXTO_MAXIMO = 1_200;

    private final long id;
    private final long donoId;
    private final String nome;
    private final String personalidade;
    private final String contexto;

    public Personagem(long id, long donoId, String nome, String personalidade, String contexto) {
        if (id <= 0 || donoId <= 0) {
            throw new IllegalArgumentException("Identificadores devem ser positivos.");
        }
        this.id = id;
        this.donoId = donoId;
        this.nome = normalizarNome(nome);
        this.personalidade = normalizarPersonalidade(personalidade);
        this.contexto = normalizarContexto(contexto);
    }

    public static String normalizarNome(String nome) {
        try {
            return RegrasConta.normalizarApelido(nome);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "O nome do personagem deve ter de 1 a 30 caracteres: letras, números,"
                            + " ponto, hífen ou sublinhado, sem espaços.");
        }
    }

    public static String normalizarPersonalidade(String personalidade) {
        String texto = personalidade == null ? "" : personalidade.strip();
        if (texto.isEmpty() || texto.length() > PERSONALIDADE_MAXIMA || temControle(texto)) {
            throw new IllegalArgumentException(
                    "A personalidade deve ter de 1 a " + PERSONALIDADE_MAXIMA + " caracteres.");
        }
        return texto;
    }

    public static String normalizarContexto(String contexto) {
        String texto = contexto == null ? "" : contexto.strip();
        if (texto.length() > CONTEXTO_MAXIMO || temControle(texto)) {
            throw new IllegalArgumentException(
                    "O contexto deve ter no máximo " + CONTEXTO_MAXIMO + " caracteres.");
        }
        return texto;
    }

    /** Quebras de linha e tabulação são permitidas; outros controles, não. */
    private static boolean temControle(String texto) {
        return texto.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }

    public long getId() {
        return id;
    }

    public long getDonoId() {
        return donoId;
    }

    public String getNome() {
        return nome;
    }

    public String getPersonalidade() {
        return personalidade;
    }

    public String getContexto() {
        return contexto;
    }

    @Override
    public boolean equals(Object objeto) {
        return objeto instanceof Personagem outro && id == outro.id && donoId == outro.donoId
                && nome.equals(outro.nome) && personalidade.equals(outro.personalidade)
                && contexto.equals(outro.contexto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, donoId, nome, personalidade, contexto);
    }
}
