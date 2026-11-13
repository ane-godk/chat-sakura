package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Flor da paleta e modo de uma imagem derivada. */
    private record Variante(Aparencia.Paleta flor, boolean noturno) {
    }

    private record FlorAjustada(Variante variante, int tamanho) {
    }

    // Flores já coloridas e redimensionadas (pequenas: todas ficam em cache).
    private static final Map<Variante, Optional<BufferedImage>> FLORES = new ConcurrentHashMap<>();
    private static final Map<FlorAjustada, BufferedImage> FLORES_AJUSTADAS = new ConcurrentHashMap<>();

    // Banners coloridos (1500 × 500): só os dois mais recentes ficam na memória.
    private static final int BANNERS_EM_CACHE = 2;
    private static final Map<Variante, Optional<BufferedImage>> BANNERS =
            Collections.synchronizedMap(new LinkedHashMap<>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Variante, Optional<BufferedImage>> mais) {
                    return size() > BANNERS_EM_CACHE;
                }
            });

    private static Variante varianteAtiva() {
        return new Variante(GerenciadorTema.aparencia().paleta(), TemaSakura.cores().noturno());
    }

    /**
     * Flor no tamanho pedido (em pixels) para a aparência ativa, ou vazio se
     * faltar a imagem. Ela recebe a cor da paleta e, no modo escuro, fica um
     * pouco menos brilhante.
     */
    static Optional<BufferedImage> florEmPixels(int pixels) {
        return florEmPixels(pixels, varianteAtiva().flor(), varianteAtiva().noturno());
    }

    /** Flor de uma paleta e modo quaisquer (usada também na prévia da aparência). */
    static Optional<BufferedImage> florEmPixels(int pixels, Aparencia.Paleta paleta, boolean noturno) {
        int tamanho = Math.max(1, pixels);
        Variante variante = new Variante(paleta, noturno);
        return florNa(variante).map(f -> FLORES_AJUSTADAS.computeIfAbsent(
                new FlorAjustada(variante, tamanho), chave -> redimensionar(f, tamanho, tamanho)));
    }

    /** Flor na cor e no modo da aparência ativa. */
    static Optional<BufferedImage> florNoTema() {
        return florNa(varianteAtiva());
    }

    /** Banner na cor e no modo da aparência ativa. */
    static Optional<BufferedImage> bannerNoTema() {
        Variante v = varianteAtiva();
        if (v.flor() == Aparencia.Paleta.SAKURA) {
            return v.noturno() ? Noturnas.BANNER : banner();
        }
        return BANNERS.computeIfAbsent(v, chave -> banner().map(b -> {
            BufferedImage colorido = colorir(b, chave.flor());
            return chave.noturno()
                    ? bannerNoturno(colorido, PaletasFlorais.de(chave.flor(), true).cartao(), FATOR_NOTURNO_NEUTRO)
                    : colorido;
        }));
    }

    private static Optional<BufferedImage> florNa(Variante v) {
        if (v.flor() == Aparencia.Paleta.SAKURA) {
            return v.noturno() ? Noturnas.FLOR : flor();
        }
        return FLORES.computeIfAbsent(v, chave -> flor().map(f -> {
            BufferedImage colorida = colorir(f, chave.flor());
            return chave.noturno() ? ajustarBrilho(colorida, 0.82) : colorida;
        }));
    }

    /** Matizes da cerejeira (rosa), recoloridos nas demais flores. */
    private static final float MATIZ_ROSA = 331f / 360f;
    private static final float FAIXA_ROSA = 50f / 360f;

    /**
     * Leva os tons rosados da imagem ao matiz da flor (o céu, o galho e o
     * pagode não mudam). A transparência é preservada. Gera uma imagem nova.
     */
    static BufferedImage colorir(BufferedImage original, Aparencia.Paleta flor) {
        int w = original.getWidth();
        int h = original.getHeight();
        int[] pixels = original.getRGB(0, 0, w, h, null, 0, w);
        float giro = PaletasFlorais.matiz(flor) - PaletasFlorais.matiz(Aparencia.Paleta.SAKURA);
        float saturacao = PaletasFlorais.REFERENCIAS.get(flor).saturacao();
        float[] hsb = new float[3];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            Color.RGBtoHSB((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, hsb);
            float distancia = Math.abs(hsb[0] - MATIZ_ROSA);
            distancia = Math.min(distancia, 1f - distancia);
            if (hsb[1] < 0.04f || distancia > FAIXA_ROSA) {
                continue;
            }
            float matiz = hsb[0] + giro;
            matiz -= (float) Math.floor(matiz);
            int rgb = Color.HSBtoRGB(matiz, Math.min(1f, hsb[1] * saturacao), hsb[2]);
            pixels[i] = (argb & 0xff000000) | (rgb & 0x00ffffff);
        }
        BufferedImage saida = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        saida.setRGB(0, 0, w, h, pixels, 0, w);
        return saida;
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
        // Ameixa de fundo (#2A1C2B) misturada à imagem escurecida.
        return bannerNoturno(original, new Color(0x2A, 0x1C, 0x2B), new double[] {0.50, 0.36, 0.46});
    }

    /** Escurecimento por canal das demais flores (sem puxar para o ameixa). */
    private static final double[] FATOR_NOTURNO_NEUTRO = {0.44, 0.44, 0.44};

    /** Escurece o banner por canal e o mistura à cor de fundo noturna da paleta. */
    static BufferedImage bannerNoturno(BufferedImage original, Color fundoNoturno, double[] fator) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage noturno = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        int[] tinta = {fundoNoturno.getRed(), fundoNoturno.getGreen(), fundoNoturno.getBlue()};
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
