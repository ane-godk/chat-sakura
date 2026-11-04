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
