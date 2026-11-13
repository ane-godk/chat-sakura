package br.unicesumar.chat.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Aparência escolhida por uma conta: paleta floral, modo claro ou escuro e
 * tipo de fundo da conversa. Imutável.
 */
public record Aparencia(Paleta paleta, Modo modo, Fundo fundo) {

    /** Paletas florais (a Sakura é a original e a padrão). */
    public enum Paleta {
        SAKURA("Sakura", "cerejeira japonesa"),
        MYOSOTIS("Myosotis", "não-me-esqueças"),
        FUJI("Fuji", "glicínia japonesa"),
        SUZURAN("Suzuran", "lírio-do-vale");

        private final String nome;
        private final String flor;

        Paleta(String nome, String flor) {
            this.nome = nome;
            this.flor = flor;
        }

        public String nome() {
            return nome;
        }

        public String flor() {
            return flor;
        }
    }

    public enum Modo { CLARO, ESCURO }

    /** Fundo da conversa: ilustração floral, pastel liso ou neutro sem decoração. */
    public enum Fundo { FLORAL, LISO, NEUTRO }

    /** Padrão para contas novas: Sakura, claro, fundo floral. */
    public static final Aparencia PADRAO = new Aparencia(Paleta.SAKURA, Modo.CLARO, Fundo.FLORAL);

    public Aparencia {
        Objects.requireNonNull(paleta, "Paleta obrigatória.");
        Objects.requireNonNull(modo, "Modo obrigatório.");
        Objects.requireNonNull(fundo, "Fundo obrigatório.");
    }

    public boolean escuro() {
        return modo == Modo.ESCURO;
    }

    public Aparencia comPaleta(Paleta nova) {
        return new Aparencia(nova, modo, fundo);
    }

    public Aparencia comModo(Modo novo) {
        return new Aparencia(paleta, novo, fundo);
    }

    public Aparencia comFundo(Fundo novo) {
        return new Aparencia(paleta, modo, novo);
    }

    /**
     * Leitura tolerante (dados antigos, arquivos ou bancos): cada valor
     * ausente ou desconhecido vira o padrão daquele campo.
     */
    public static Aparencia ler(String paleta, String modo, String fundo) {
        return new Aparencia(
                valor(Paleta.class, paleta).orElse(PADRAO.paleta()),
                valor(Modo.class, modo).orElse(PADRAO.modo()),
                valor(Fundo.class, fundo).orElse(PADRAO.fundo()));
    }

    /**
     * Leitura estrita (pedidos de clientes): todos os campos precisam ser válidos.
     *
     * @throws IllegalArgumentException com mensagem para o usuário
     */
    public static Aparencia exigir(String paleta, String modo, String fundo) {
        return new Aparencia(
                valor(Paleta.class, paleta).orElseThrow(() -> new IllegalArgumentException("Paleta inválida.")),
                valor(Modo.class, modo).orElseThrow(() -> new IllegalArgumentException("Modo inválido.")),
                valor(Fundo.class, fundo).orElseThrow(() -> new IllegalArgumentException("Fundo inválido.")));
    }

    private static <E extends Enum<E>> java.util.Optional<E> valor(Class<E> tipo, String texto) {
        if (texto == null || texto.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Enum.valueOf(tipo, texto.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
