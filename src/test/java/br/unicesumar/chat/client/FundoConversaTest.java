package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FundoConversaTest {

    private static BufferedImage desenhar(int largura, int altura) throws Exception {
        AtomicReference<BufferedImage> resultado = new AtomicReference<>();
        AtomicReference<Throwable> erro = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            try {
                FundoConversa fundo = new FundoConversa(new BorderLayout());
                fundo.setSize(largura, altura);

                BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = imagem.createGraphics();
                fundo.paint(g);
                g.dispose();
                resultado.set(imagem);
            } catch (Throwable e) {
                erro.set(e);
            }
        });

        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
        return resultado.get();
    }

    private static int alfa(int argb) {
        return (argb >>> 24) & 0xff;
    }

    private static int verde(int argb) {
        return (argb >> 8) & 0xff;
    }

    @Test
    void deveDesenharEmTamanhosExtremosSemErro() {

        assertDoesNotThrow(() -> desenhar(1, 1));
        assertDoesNotThrow(() -> desenhar(600, 180));
        assertDoesNotThrow(() -> desenhar(1400, 900));
    }

    @Test
    void deveTerDegradeDoBrancoAoRosaECantosArredondados() throws Exception {

        BufferedImage imagem = desenhar(760, 320);

        // Canto externo transparente (cantos arredondados).
        assertEquals(0, alfa(imagem.getRGB(0, 0)));

        // Centro do topo quase branco; centro da base mais rosado.
        int topo = imagem.getRGB(380, 6);
        int base = imagem.getRGB(380, 312);

        assertEquals(255, alfa(topo));
        assertTrue(verde(topo) > verde(base), "O degradê deve ir do branco ao rosa.");
    }
}
