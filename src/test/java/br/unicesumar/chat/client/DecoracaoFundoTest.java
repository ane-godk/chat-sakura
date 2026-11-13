package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.model.Aparencia.Fundo;
import br.unicesumar.chat.model.Aparencia.Paleta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Os três fundos em todas as paletas: centro limpo, decoração só nas bordas, contraste e cache. */
class DecoracaoFundoTest {

    private static final int L = 600;
    private static final int A = 400;

    @AfterEach
    void voltarAoPadrao() throws Exception {
        SwingUtilities.invokeAndWait(() -> GerenciadorTema.definir(Aparencia.PADRAO));
    }

    private static int distancia(int argb, Color c) {
        return Math.max(Math.abs(((argb >> 16) & 0xff) - c.getRed()),
                Math.max(Math.abs(((argb >> 8) & 0xff) - c.getGreen()), Math.abs((argb & 0xff) - c.getBlue())));
    }

    /** Cor esperada sem decoração no ponto (x, y). */
    private static Color base(PaletaSakura c, Fundo fundo, int y) {
        return switch (fundo) {
            case FLORAL -> TemaSakura.misturar(c.degradeConversaTopo(), c.degradeConversaBase(), y / (double) A);
            case LISO -> DecoracaoFundo.corLisa(c);
            case NEUTRO -> DecoracaoFundo.corNeutra(c);
        };
    }

    @Test
    void centroFicaLimpoEmTodasAsCombinacoes() {
        Rectangle livre = DecoracaoFundo.areaLivre(L, A);
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura c = PaletasFlorais.de(flor, escuro);
                for (Fundo fundo : Fundo.values()) {
                    BufferedImage img = DecoracaoFundo.compor(c, flor, fundo, L, A, 1.0);
                    assertEquals(L, img.getWidth());
                    for (int y = livre.y; y < livre.y + livre.height; y += 9) {
                        for (int x = livre.x; x < livre.x + livre.width; x += 9) {
                            int argb = img.getRGB(x, y);
                            assertEquals(255, argb >>> 24);
                            assertTrue(distancia(argb, base(c, fundo, y)) <= 2,
                                    flor + "/" + escuro + "/" + fundo + " decorado em " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    /** Pixels das bordas (fora da área livre) que diferem da cor de fundo. */
    private static int pixelsDecorados(PaletaSakura c, Paleta flor, Fundo fundo) {
        BufferedImage img = DecoracaoFundo.compor(c, flor, fundo, L, A, 1.0);
        Rectangle livre = DecoracaoFundo.areaLivre(L, A);
        int decorados = 0;
        for (int y = 4; y < A - 4; y += 2) {
            for (int x = 4; x < L - 4; x += 2) {
                if (!livre.contains(x, y) && distancia(img.getRGB(x, y), base(c, fundo, y)) > 6) {
                    decorados++;
                }
            }
        }
        return decorados;
    }

    @Test
    void floralTemIlustracaoNasBordasELisoENeutroNao() {
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura c = PaletasFlorais.de(flor, escuro);
                assertTrue(pixelsDecorados(c, flor, Fundo.FLORAL) > 80, flor + " floral sem ilustração");
                assertEquals(0, pixelsDecorados(c, flor, Fundo.LISO), flor + " liso decorado");
                assertEquals(0, pixelsDecorados(c, flor, Fundo.NEUTRO), flor + " neutro decorado");
            }
        }
    }

    /** O título da conversa fica no canto superior esquerdo do cartão: nenhuma ilustração ali. */
    @Test
    void faixaDoTituloFicaLivre() {
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura c = PaletasFlorais.de(flor, escuro);
                BufferedImage img = DecoracaoFundo.compor(c, flor, Fundo.FLORAL, L, A, 1.0);
                for (int y = 6; y < 24; y += 2) {
                    for (int x = 12; x < 160; x += 2) {
                        assertTrue(distancia(img.getRGB(x, y), base(c, Fundo.FLORAL, y)) <= 2,
                                flor + (escuro ? " escuro" : " claro") + ": decoração sob o título em " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void neutroQuaseSemCorELisoComOTomDaPaleta() {
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura c = PaletasFlorais.de(flor, escuro);
                Color neutra = DecoracaoFundo.corNeutra(c);
                float[] hsb = Color.RGBtoHSB(neutra.getRed(), neutra.getGreen(), neutra.getBlue(), null);
                assertTrue(hsb[1] < 0.06, flor + " neutro saturado: " + neutra);
                assertNotEquals(DecoracaoFundo.corLisa(c), neutra);
            }
        }
        assertNotEquals(DecoracaoFundo.corLisa(PaletasFlorais.de(Paleta.FUJI, false)),
                DecoracaoFundo.corLisa(PaletasFlorais.de(Paleta.SUZURAN, false)));
    }

    @Test
    void textoDaConversaTemContrasteEmQualquerFundo() {
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura c = PaletasFlorais.de(flor, escuro);
                for (Fundo fundo : Fundo.values()) {
                    for (Color atras : DecoracaoFundo.coresDoCentro(c, fundo)) {
                        for (Color texto : new Color[] {c.texto(), c.rosaEscuro()}) {
                            double contraste = TemaSakura.contraste(texto, atras);
                            assertTrue(contraste >= 4.5, flor + "/" + escuro + "/" + fundo + ": "
                                    + String.format("%.2f", contraste));
                        }
                    }
                }
            }
        }
    }

    @Test
    void escalaDeTelaGeraImagemMaiorSemErro() {
        BufferedImage img = DecoracaoFundo.compor(PaletaSakura.CLARO, Paleta.SAKURA, Fundo.FLORAL, 300, 200, 1.5);
        assertEquals(450, img.getWidth());
        assertEquals(300, img.getHeight());
        assertDoesNotThrow(() -> DecoracaoFundo.compor(PaletaSakura.NOTURNO, Paleta.FUJI, Fundo.FLORAL, 1, 1, 1.0));
    }

    @Test
    void petalasCaindoSoNoFundoFloral() {
        ConfiguracaoAnimacoes.Modo completas = ConfiguracaoAnimacoes.Modo.COMPLETAS;
        assertTrue(FundoConversa.petalasDevemRodar(completas, true, true, Fundo.FLORAL));
        assertFalse(FundoConversa.petalasDevemRodar(completas, true, true, Fundo.LISO));
        assertFalse(FundoConversa.petalasDevemRodar(completas, true, true, Fundo.NEUTRO));
        assertFalse(FundoConversa.petalasDevemRodar(ConfiguracaoAnimacoes.Modo.REDUZIDAS, true, true, Fundo.FLORAL));
    }

    @Test
    void fundoDaConversaReusaOCacheERefazSoQuandoAAparenciaMuda() throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                FundoConversa fundo = new FundoConversa(new BorderLayout());
                fundo.setSize(500, 300);
                BufferedImage tela = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB);

                pintar(fundo, tela);
                BufferedImage primeira = fundo.composicao();
                pintar(fundo, tela);
                assertSame(primeira, fundo.composicao(), "Mesma aparência e tamanho: cache reutilizado");

                GerenciadorTema.definir(new Aparencia(Paleta.FUJI, Aparencia.Modo.CLARO, Fundo.FLORAL));
                pintar(fundo, tela);
                BufferedImage fuji = fundo.composicao();
                assertNotSame(primeira, fuji, "Nova paleta: refeito");

                GerenciadorTema.definir(new Aparencia(Paleta.FUJI, Aparencia.Modo.CLARO, Fundo.LISO));
                pintar(fundo, tela);
                assertNotSame(fuji, fundo.composicao(), "Novo fundo: refeito");
                assertEquals(DecoracaoFundo.corLisa(TemaSakura.cores()).getRGB(), tela.getRGB(250, 150));
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    private static void pintar(FundoConversa fundo, BufferedImage tela) {
        Graphics2D g = tela.createGraphics();
        try {
            fundo.paint(g);
        } finally {
            g.dispose();
        }
    }

    @Test
    void bannerEFlorRecebemACorDaPaletaSemAlterarOOriginal() throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BufferedImage original = RecursosSakura.banner().orElseThrow();
                int pixelOriginal = original.getRGB(10, 10);
                GerenciadorTema.definir(new Aparencia(Paleta.MYOSOTIS, Aparencia.Modo.CLARO, Fundo.FLORAL));
                BufferedImage azul = RecursosSakura.bannerNoTema().orElseThrow();
                assertNotSame(original, azul);
                assertSame(azul, RecursosSakura.bannerNoTema().orElseThrow(), "Em cache");
                assertEquals(original.getWidth(), azul.getWidth());
                assertEquals(pixelOriginal, original.getRGB(10, 10), "Original intacto");

                BufferedImage flor = RecursosSakura.florEmPixels(32).orElseThrow();
                assertSame(flor, RecursosSakura.florEmPixels(32).orElseThrow());
                assertNotSame(flor, RecursosSakura.florEmPixels(32, Paleta.SAKURA, false).orElseThrow());
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }
}
