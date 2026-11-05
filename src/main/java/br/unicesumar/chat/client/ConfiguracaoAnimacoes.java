package br.unicesumar.chat.client;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nível das animações da interface.
 *
 * O valor inicial vem da propriedade {@code -Dchat.sakura.animacoes}
 * ({@code completas}, {@code reduzidas} ou {@code desativadas}) e pode ser
 * alterado durante o uso pelo menu de contexto da área de conversa.
 */
final class ConfiguracaoAnimacoes {

    static final String PROPRIEDADE = "chat.sakura.animacoes";

    enum Modo {
        COMPLETAS("Completas"),
        REDUZIDAS("Reduzidas (sem pétalas)"),
        DESATIVADAS("Desativadas");

        private final String rotulo;

        Modo(String rotulo) {
            this.rotulo = rotulo;
        }

        String rotulo() {
            return rotulo;
        }
    }

    private static volatile Modo modo = interpretar(System.getProperty(PROPRIEDADE));

    private static final List<Runnable> OUVINTES = new CopyOnWriteArrayList<>();

    private ConfiguracaoAnimacoes() {
    }

    static Modo interpretar(String valor) {

        if (valor == null) {
            return Modo.COMPLETAS;
        }

        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "reduzidas", "reduzida" -> Modo.REDUZIDAS;
            case "desativadas", "desativada", "false", "off", "nao", "não" -> Modo.DESATIVADAS;
            default -> Modo.COMPLETAS;
        };
    }

    static Modo modo() {
        return modo;
    }

    static void definirModo(Modo novoModo) {
        modo = novoModo;
        OUVINTES.forEach(Runnable::run);
    }

    /** Transições curtas (balões, botões, indicador, avião). */
    static boolean transicoesAtivas() {
        return modo != Modo.DESATIVADAS;
    }

    /** Animação contínua das pétalas no fundo. */
    static boolean petalasAtivas() {
        return modo == Modo.COMPLETAS;
    }

    static void aoMudar(Runnable ouvinte) {
        OUVINTES.add(ouvinte);
    }

    static void removerOuvinte(Runnable ouvinte) {
        OUVINTES.remove(ouvinte);
    }
}
