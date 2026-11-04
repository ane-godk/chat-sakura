package br.unicesumar.chat.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
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
    static final Color VERDE_CLARO = new Color(230, 245, 238);
    static final Color AMBAR = new Color(230, 160, 60);
    static final Color AMBAR_CLARO = new Color(253, 243, 224);
    static final Color AMBAR_TEXTO = new Color(160, 95, 15);
    static final Color CORAL = new Color(214, 92, 92);
    static final Color CORAL_CLARO = new Color(252, 232, 232);
    static final Color CORAL_TEXTO = new Color(184, 60, 60);

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

    private static final String FAMILIA = escolherFamilia();

    // Fontes
    static final Font FONTE_TITULO = fonte(Font.BOLD, 24f);
    static final Font FONTE_TITULO_DIALOGO = fonte(Font.BOLD, 18f);
    static final Font FONTE_SUBTITULO = fonte(Font.PLAIN, 13f);
    static final Font FONTE_TEXTO = fonte(Font.PLAIN, 14f);
    static final Font FONTE_BOTAO = fonte(Font.BOLD, 13f);
    static final Font FONTE_PEQUENA = fonte(Font.PLAIN, 12f);
    static final Font FONTE_PEQUENA_NEGRITO = fonte(Font.BOLD, 12f);

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

    static Font fonte(int estilo, float tamanho) {
        return new Font(FAMILIA, estilo, 1).deriveFont(estilo, tamanho);
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
