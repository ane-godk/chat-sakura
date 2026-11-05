package br.unicesumar.chat.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import javax.swing.text.StyleContext;
import java.util.Arrays;
import java.util.Map;

/**
 * Identidade visual Sakura: paleta ativa, fontes e espaçamentos.
 *
 * As cores ficam em {@link PaletaSakura}; os componentes as consultam por
 * {@link #cores()} no momento da pintura. Os pares de texto e fundo têm
 * contraste de pelo menos 4,5:1 para textos pequenos (verificado em teste).
 */
final class TemaSakura {

    private static volatile PaletaSakura paletaAtiva = PaletaSakura.CLARO;

    /** Paleta do tema ativo. */
    static PaletaSakura cores() {
        return paletaAtiva;
    }

    /** Usado pelo {@link GerenciadorTema}. */
    static void definirPaleta(PaletaSakura paleta) {
        paletaAtiva = paleta;
    }

    // Espaçamentos e raios
    static final int ESPACO_P = 8;
    static final int ESPACO_M = 12;
    static final int ESPACO_G = 16;
    static final int RAIO_CONTROLE = 14;
    static final int RAIO_CARTAO = 18;


    private static final String FAMILIA = escolherFamilia();

    // Nunito (SIL OFL 1.1, em resources/sakura/fontes) para título e
    // controles; Segoe UI para o texto corrido. Sem a Nunito, todas as
    // fontes recaem na família do sistema.
    private static final Font NUNITO_SEMIBOLD =
            RecursosSakura.carregarFonte(RecursosSakura.FONTE_SEMIBOLD).orElse(null);
    private static final Font NUNITO_BOLD =
            RecursosSakura.carregarFonte(RecursosSakura.FONTE_BOLD).orElse(null);
    private static final Font NUNITO_EXTRABOLD =
            RecursosSakura.carregarFonte(RecursosSakura.FONTE_EXTRABOLD).orElse(null);

    // Fontes
    static final Font FONTE_TITULO = destaque(NUNITO_EXTRABOLD, Font.BOLD, 30f);
    static final Font FONTE_TITULO_DIALOGO = destaque(NUNITO_EXTRABOLD, Font.BOLD, 19f);
    static final Font FONTE_SUBTITULO = destaque(NUNITO_SEMIBOLD, Font.PLAIN, 14f);
    static final Font FONTE_BOTAO = destaque(NUNITO_BOLD, Font.BOLD, 13f);
    static final Font FONTE_STATUS = destaque(NUNITO_BOLD, Font.BOLD, 13f);
    static final Font FONTE_PEQUENA_NEGRITO = destaque(NUNITO_BOLD, Font.BOLD, 12.5f);
    static final Font FONTE_NOME = destaque(NUNITO_BOLD, Font.BOLD, 13f);
    static final Font FONTE_TEXTO = fonte(Font.PLAIN, 14f);
    static final Font FONTE_PEQUENA = fonte(Font.PLAIN, 12f);
    static final Font FONTE_SISTEMA = fonte(Font.ITALIC, 12.5f);

    private TemaSakura() {
    }

    private static String escolherFamilia() {
        String[] disponiveis = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        return Arrays.asList(disponiveis).contains("Segoe UI")
                ? "Segoe UI"
                : Font.SANS_SERIF;
    }

    /**
     * Fonte do sistema em versão composta (com fontes de reserva), que
     * exibe também emojis e símbolos ausentes da família principal.
     */
    static Font fonte(int estilo, float tamanho) {
        return StyleContext.getDefaultStyleContext()
                .getFont(FAMILIA, estilo, Math.round(tamanho))
                .deriveFont(estilo, tamanho);
    }

    /**
     * A fonte preferida, se ela exibir todo o texto; senão, a fonte
     * composta do sistema com o mesmo estilo e tamanho (por exemplo,
     * para nomes com emoji, ausentes na Nunito).
     */
    static Font fonteQueExibe(Font preferida, String texto) {
        return texto == null || preferida.canDisplayUpTo(texto) < 0
                ? preferida
                : fonte(preferida.getStyle(), preferida.getSize2D());
    }

    /** Fonte de destaque (Nunito) ou, na falta dela, a do sistema. */
    private static Font destaque(Font base, int estiloAlternativo, float tamanho) {
        return base != null
                ? base.deriveFont(tamanho)
                : fonte(estiloAlternativo, tamanho);
    }

    /** Mistura duas cores (t = 0 → a; t = 1 → b), inclusive a transparência. */
    static Color misturar(Color a, Color b, double t) {

        if (a == null || b == null) {
            return t < 0.5 ? a : b;
        }

        double p = Math.max(0, Math.min(1, t));

        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * p),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * p),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * p),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * p)
        );
    }

    /** A mesma cor com outra opacidade (0 a 255). */
    static Color comAlfa(Color cor, int alfa) {
        return new Color(cor.getRed(), cor.getGreen(), cor.getBlue(),
                Math.max(0, Math.min(255, alfa)));
    }

    /** Razão de contraste WCAG 2.x entre duas cores opacas (1 a 21). */
    static double contraste(Color a, Color b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminancia(Color c) {
        return 0.2126 * linear(c.getRed())
                + 0.7152 * linear(c.getGreen())
                + 0.0722 * linear(c.getBlue());
    }

    private static double linear(int canal) {
        double v = canal / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Indica se as fontes de destaque internas foram carregadas. */
    static boolean fontesDestaqueCarregadas() {
        return NUNITO_SEMIBOLD != null
                && NUNITO_BOLD != null
                && NUNITO_EXTRABOLD != null;
    }

    /**
     * Cria uma cópia do contexto gráfico com suavização de formas
     * e de texto (usando as configurações do sistema, quando houver).
     */
    static Graphics2D suavizar(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setRenderingHint(
                RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE
        );

        Object dicasTexto = Toolkit.getDefaultToolkit()
                .getDesktopProperty("awt.font.desktophints");

        if (dicasTexto instanceof Map<?, ?> mapa) {
            g2.addRenderingHints(mapa);
        } else {
            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );
        }

        return g2;
    }
}
