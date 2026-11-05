package br.unicesumar.chat.client;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tema ativo (Sakura Claro ou Sakura Noturno) e aviso aos interessados.
 *
 * A troca sempre ocorre na EDT: chamadas de outras threads são repassadas
 * para ela. Os componentes leem a paleta no momento da pintura, por isso a
 * troca não reconstrói a interface: basta repintar.
 *
 * O tema inicial pode vir de {@code -Dchat.sakura.tema=noturno}.
 */
final class GerenciadorTema {

    static final String PROPRIEDADE = "chat.sakura.tema";

    enum Tema {
        CLARO(PaletaSakura.CLARO),
        NOTURNO(PaletaSakura.NOTURNO);

        private final PaletaSakura paleta;

        Tema(PaletaSakura paleta) {
            this.paleta = paleta;
        }

        PaletaSakura paleta() {
            return paleta;
        }

        Tema oposto() {
            return this == CLARO ? NOTURNO : CLARO;
        }
    }

    private static final List<Runnable> OUVINTES = new CopyOnWriteArrayList<>();

    private static volatile Tema atual = Tema.CLARO;

    static {
        Tema inicial = interpretar(System.getProperty(PROPRIEDADE));
        atual = inicial;
        TemaSakura.definirPaleta(inicial.paleta());
    }

    private GerenciadorTema() {
    }

    static Tema interpretar(String valor) {
        if (valor == null) {
            return Tema.CLARO;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "noturno", "escuro", "dark" -> Tema.NOTURNO;
            default -> Tema.CLARO;
        };
    }

    static Tema atual() {
        return atual;
    }

    static void alternar() {
        definir(atual.oposto());
    }

    /** Define o tema; sem efeito se já estiver ativo. Executa na EDT. */
    static void definir(Tema tema) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> definir(tema));
            return;
        }

        if (tema == atual) {
            return;
        }

        atual = tema;
        TemaSakura.definirPaleta(tema.paleta());
        OUVINTES.forEach(Runnable::run);
    }

    static void aoMudar(Runnable ouvinte) {
        if (!OUVINTES.contains(ouvinte)) {
            OUVINTES.add(ouvinte);
        }
    }

    static void removerOuvinte(Runnable ouvinte) {
        OUVINTES.remove(ouvinte);
    }

    static int quantidadeOuvintes() {
        return OUVINTES.size();
    }
}
