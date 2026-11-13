package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aparência ativa (paleta floral, modo claro ou escuro e fundo da conversa)
 * e aviso aos interessados.
 *
 * A troca sempre ocorre na EDT: chamadas de outras threads são repassadas
 * para ela. Os componentes leem a paleta no momento da pintura, por isso a
 * troca não reconstrói a interface: basta repintar.
 *
 * O modo inicial pode vir de {@code -Dchat.sakura.tema=noturno}.
 */
final class GerenciadorTema {

    static final String PROPRIEDADE = "chat.sakura.tema";

    /** Modo (claro ou escuro) da paleta ativa. */
    enum Tema {
        CLARO(PaletaSakura.CLARO),
        NOTURNO(PaletaSakura.NOTURNO);

        private final PaletaSakura paleta;

        Tema(PaletaSakura paleta) {
            this.paleta = paleta;
        }

        /** Paleta deste modo na flor Sakura (a original). */
        PaletaSakura paleta() {
            return paleta;
        }

        Tema oposto() {
            return this == CLARO ? NOTURNO : CLARO;
        }

        Aparencia.Modo modo() {
            return this == NOTURNO ? Aparencia.Modo.ESCURO : Aparencia.Modo.CLARO;
        }

        static Tema de(Aparencia.Modo modo) {
            return modo == Aparencia.Modo.ESCURO ? NOTURNO : CLARO;
        }
    }

    private static final List<Runnable> OUVINTES = new CopyOnWriteArrayList<>();

    private static volatile Aparencia atual = Aparencia.PADRAO;

    static {
        Aparencia inicial = Aparencia.PADRAO.comModo(interpretar(System.getProperty(PROPRIEDADE)).modo());
        atual = inicial;
        TemaSakura.definirPaleta(PaletasFlorais.de(inicial));
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

    /** Modo ativo. */
    static Tema atual() {
        return Tema.de(atual.modo());
    }

    /** Aparência ativa completa. */
    static Aparencia aparencia() {
        return atual;
    }

    /** Fundo da conversa escolhido. */
    static Aparencia.Fundo fundo() {
        return atual.fundo();
    }

    /** Alterna claro/escuro mantendo a flor e o fundo. */
    static void alternar() {
        definir(atual().oposto());
    }

    /** Muda só o modo; sem efeito se já estiver ativo. Executa na EDT. */
    static void definir(Tema tema) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> definir(tema));
            return;
        }
        definir(atual.comModo(tema.modo()));
    }

    /** Define a aparência inteira; sem efeito se já estiver ativa. Executa na EDT. */
    static void definir(Aparencia aparencia) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> definir(aparencia));
            return;
        }

        if (aparencia == null || aparencia.equals(atual)) {
            return;
        }

        atual = aparencia;
        TemaSakura.definirPaleta(PaletasFlorais.de(aparencia));
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
