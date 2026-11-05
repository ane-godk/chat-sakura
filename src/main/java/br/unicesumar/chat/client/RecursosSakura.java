package br.unicesumar.chat.client;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Carregamento seguro dos recursos gráficos internos
 * ({@code src/main/resources/sakura}).
 *
 * Cada recurso é lido uma única vez, de forma preguiçosa e thread-safe
 * (idioma "holder"). Se um arquivo não existir ou estiver corrompido,
 * o resultado é vazio e a interface usa a alternativa desenhada em código.
 */
final class RecursosSakura {

    static final String BANNER = "/sakura/imagens/banner.jpg";
    static final String FLOR = "/sakura/imagens/flor.png";

    static final String FONTE_SEMIBOLD = "/sakura/fontes/Nunito-SemiBold.ttf";
    static final String FONTE_BOLD = "/sakura/fontes/Nunito-Bold.ttf";
    static final String FONTE_EXTRABOLD = "/sakura/fontes/Nunito-ExtraBold.ttf";

    private RecursosSakura() {
    }

    private static final class Imagens {
        static final Optional<BufferedImage> BANNER_CARREGADO =
                carregarImagem(BANNER);
        static final Optional<BufferedImage> FLOR_CARREGADA =
                carregarImagem(FLOR);
    }

    static Optional<BufferedImage> banner() {
        return Imagens.BANNER_CARREGADO;
    }

    static Optional<BufferedImage> flor() {
        return Imagens.FLOR_CARREGADA;
    }

    // Flores já redimensionadas, por tamanho em pixels e tema (uso na EDT).
    private static final java.util.Map<Integer, BufferedImage> FLORES_AJUSTADAS =
            new java.util.HashMap<>();
    private static final java.util.Map<Integer, BufferedImage> FLORES_NOTURNAS =
            new java.util.HashMap<>();

    /**
     * Flor no tamanho pedido (em pixels) para o tema ativo, ou vazio se
     * faltar a imagem. No tema noturno, a flor fica um pouco menos brilhante.
     */
    static Optional<BufferedImage> florEmPixels(int pixels) {
        int tamanho = Math.max(1, pixels);
        if (TemaSakura.cores().noturno()) {
            return florNoturna().map(f -> FLORES_NOTURNAS.computeIfAbsent(
                    tamanho, t -> redimensionar(f, t, t)));
        }
        return flor().map(original -> FLORES_AJUSTADAS.computeIfAbsent(
                tamanho, t -> redimensionar(original, t, t)));
    }

    /** Flor original ou noturna, conforme o tema ativo. */
    static Optional<BufferedImage> florNoTema() {
        return TemaSakura.cores().noturno() ? florNoturna() : flor();
    }

    /** Banner original ou noturno, conforme o tema ativo. */
    static Optional<BufferedImage> bannerNoTema() {
        return TemaSakura.cores().noturno() ? Noturnas.BANNER : banner();
    }

    private static Optional<BufferedImage> florNoturna() {
        return Noturnas.FLOR;
    }

    /** Versões noturnas, geradas em memória a partir dos originais. */
    private static final class Noturnas {
        static final Optional<BufferedImage> BANNER = banner().map(RecursosSakura::bannerNoturno);
        static final Optional<BufferedImage> FLOR = flor().map(f -> ajustarBrilho(f, 0.82));
    }

    /**
     * Escurece o banner e o tinge de ameixa: galho, flores e pagode
     * continuam visíveis, sobre um céu noturno suave (sem preto puro).
     */
    static BufferedImage bannerNoturno(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage noturno = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // Ameixa de fundo (#2A1C2B) misturada à imagem escurecida.
        int[] tinta = {0x2A, 0x1C, 0x2B};
        double[] fator = {0.50, 0.36, 0.46};
        double mistura = 0.35;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = original.getRGB(x, y);
                int[] canal = {(rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff};
                int saida = 0;
                for (int c = 0; c < 3; c++) {
                    double escuro = canal[c] * fator[c];
                    int v = (int) Math.round(escuro * (1 - mistura) + tinta[c] * mistura);
                    saida = (saida << 8) | Math.max(0, Math.min(255, v));
                }
                noturno.setRGB(x, y, saida);
            }
        }
        return noturno;
    }

    /** Multiplica o brilho (RGB) preservando a transparência. */
    static BufferedImage ajustarBrilho(BufferedImage original, double fator) {
        BufferedImage saida = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int argb = original.getRGB(x, y);
                int a = argb >>> 24;
                int r = (int) Math.min(255, ((argb >> 16) & 0xff) * fator);
                int g = (int) Math.min(255, ((argb >> 8) & 0xff) * fator);
                int b = (int) Math.min(255, (argb & 0xff) * fator);
                saida.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return saida;
    }

    static Optional<BufferedImage> carregarImagem(String caminho) {

        try (InputStream entrada =
                     RecursosSakura.class.getResourceAsStream(caminho)) {

            if (entrada == null) {
                avisar("Recurso não encontrado: " + caminho);
                return Optional.empty();
            }

            return Optional.ofNullable(ImageIO.read(entrada));

        } catch (IOException e) {
            avisar("Falha ao ler " + caminho + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<Font> carregarFonte(String caminho) {

        try (InputStream entrada =
                     RecursosSakura.class.getResourceAsStream(caminho)) {

            if (entrada == null) {
                avisar("Fonte não encontrada: " + caminho);
                return Optional.empty();
            }

            return Optional.of(Font.createFont(Font.TRUETYPE_FONT, entrada));

        } catch (IOException | FontFormatException e) {
            avisar("Falha ao ler a fonte " + caminho + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Redimensiona com boa qualidade: reduções sucessivas pela metade
     * com interpolação bilinear, seguidas do ajuste final.
     */
    static BufferedImage redimensionar(
            BufferedImage original,
            int largura,
            int altura
    ) {

        if (largura <= 0 || altura <= 0) {
            throw new IllegalArgumentException("Dimensões devem ser positivas.");
        }

        BufferedImage atual = original;
        int w = original.getWidth();
        int h = original.getHeight();

        do {
            w = w / 2 >= largura ? w / 2 : largura;
            h = h / 2 >= altura ? h / 2 : altura;

            BufferedImage passo = new BufferedImage(
                    w, h, BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D g = passo.createGraphics();

            try {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                g.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY
                );
                g.drawImage(atual, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }

            atual = passo;

        } while (w != largura || h != altura);

        return atual;
    }

    private static void avisar(String mensagem) {
        System.err.println("Chat Sakura: " + mensagem
                + " (usando alternativa desenhada em código)");
    }
}
