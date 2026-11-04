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
 * Identidade visual Sakura: cores, fontes e espaçamentos da interface.
 *
 * Os pares de texto e fundo foram escolhidos com contraste de pelo
 * menos 4,5:1 para textos pequenos (por exemplo, branco sobre
 * {@link #ROSA_ESCURO}).
 */
final class TemaSakura {

    // Rosas
    static final Color ROSA = new Color(214, 84, 136);
    static final Color ROSA_ESCURO = new Color(190, 64, 116);
    static final Color ROSA_HOVER = new Color(165, 50, 98);
    static final Color ROSA_PRESSIONADO = new Color(142, 40, 84);
    static final Color ROSA_PETALA = new Color(255, 228, 238);
    static final Color ROSA_BORDA = new Color(246, 205, 222);
    static final Color ROSA_FLOR = new Color(248, 187, 208);
    static final Color ROSA_HOVER_CLARO = new Color(255, 240, 246);

    // Fundos e textos
    static final Color FUNDO = new Color(255, 248, 251);
    static final Color CARTAO = Color.WHITE;
    static final Color CAMPO_DESABILITADO = new Color(252, 242, 246);
    static final Color TEXTO = new Color(74, 52, 66);
    static final Color TEXTO_SUAVE = new Color(125, 100, 115);

    // Estados
    static final Color VERDE = new Color(64, 160, 120);
    static final Color AMBAR = new Color(230, 160, 60);
    static final Color AMBAR_TEXTO = new Color(160, 95, 15);
    static final Color CORAL = new Color(214, 92, 92);
    static final Color CORAL_TEXTO = new Color(184, 60, 60);

    /** Fundo translúcido de selos e controles sobre a ilustração. */
    static final Color BRANCO_TRANSLUCIDO = new Color(255, 255, 255, 220);

    /**
     * Cores dos nomes de outros participantes (contraste acima de 4,5:1
     * sobre branco). O próprio usuário usa {@link #ROSA_ESCURO}.
     */
    static final Color[] CORES_PARTICIPANTES = {
            new Color(122, 74, 160),
            new Color(36, 115, 135),
            new Color(165, 85, 35),
            new Color(50, 120, 70),
            new Color(90, 90, 160)
    };

    // Espaçamentos e raios
    static final int ESPACO_P = 8;
    static final int ESPACO_M = 12;
    static final int ESPACO_G = 16;
    static final int RAIO_CONTROLE = 14;
    static final int RAIO_CARTAO = 18;

    /** Horário dos balões (contraste ~5,4:1 sobre o balão rosado). */
    static final Color TEXTO_HORARIO = new Color(110, 86, 100);

    // Balões
    static final Color BALAO_PROPRIO_TOPO = new Color(255, 232, 241);
    static final Color BALAO_PROPRIO_BASE = new Color(252, 218, 232);
    static final Color BALAO_PROPRIO_BORDA = new Color(244, 196, 216);
    static final Color BALAO_OUTRO_BASE = new Color(255, 250, 252);
    static final Color SOMBRA_SUAVE = new Color(142, 40, 84, 18);

    /** Texto secundário sobre a ilustração do cabeçalho (contraste ~5,7:1). */
    static final Color TEXTO_CABECALHO = new Color(110, 70, 95);

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
