package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.model.Aparencia.Fundo;
import br.unicesumar.chat.model.Aparencia.Modo;
import br.unicesumar.chat.model.Aparencia.Paleta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Tela de aparência sem exibir a janela: opções, prévia ao vivo e "Restaurar padrão". */
class DialogoAparenciaTest {

    @AfterEach
    void voltarAoPadrao() throws Exception {
        SwingUtilities.invokeAndWait(() -> GerenciadorTema.definir(Aparencia.PADRAO));
    }

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    @Test
    void abreComAAparenciaAtualMarcada() throws Exception {
        naEdt(() -> {
            Aparencia atual = new Aparencia(Paleta.FUJI, Modo.ESCURO, Fundo.LISO);
            DialogoAparencia d = new DialogoAparencia(atual);
            assertTrue(d.opcao(Paleta.FUJI).isSelected());
            assertFalse(d.opcao(Paleta.SAKURA).isSelected());
            assertTrue(d.opcao(Modo.ESCURO).isSelected());
            assertTrue(d.opcao(Fundo.LISO).isSelected());
            assertEquals(atual, d.previa().aparencia());
        });
    }

    @Test
    void cliquesAtualizamAPreviaSemMudarAJanela() throws Exception {
        naEdt(() -> {
            DialogoAparencia d = new DialogoAparencia(Aparencia.PADRAO);
            d.opcao(Paleta.MYOSOTIS).doClick();
            d.opcao(Modo.ESCURO).doClick();
            d.opcao(Fundo.NEUTRO).doClick();

            Aparencia esperada = new Aparencia(Paleta.MYOSOTIS, Modo.ESCURO, Fundo.NEUTRO);
            assertEquals(esperada, d.escolhida());
            assertEquals(esperada, d.previa().aparencia());
            assertTrue(d.opcao(Paleta.MYOSOTIS).isSelected());
            assertFalse(d.opcao(Paleta.SAKURA).isSelected(), "Grupo exclusivo");
            assertEquals(Aparencia.PADRAO, GerenciadorTema.aparencia(), "Nada aplicado antes de Aplicar");
            assertTrue(d.previa().getAccessibleContext().getAccessibleName().contains("Myosotis"));
        });
    }

    @Test
    void restaurarPadraoVoltaASakuraClaroFloralSemAplicar() throws Exception {
        naEdt(() -> {
            Aparencia atual = new Aparencia(Paleta.SUZURAN, Modo.ESCURO, Fundo.NEUTRO);
            GerenciadorTema.definir(atual);
            DialogoAparencia d = new DialogoAparencia(atual);
            d.botaoRestaurar().doClick();
            assertEquals(Aparencia.PADRAO, d.escolhida());
            assertTrue(d.opcao(Paleta.SAKURA).isSelected());
            assertTrue(d.opcao(Modo.CLARO).isSelected());
            assertTrue(d.opcao(Fundo.FLORAL).isSelected());
            assertEquals(atual, GerenciadorTema.aparencia(), "Restaurar só muda a escolha");
        });
    }

    @Test
    void previaPintaComAPaletaCandidata() throws Exception {
        naEdt(() -> {
            PreviaAparencia previa = new PreviaAparencia(Aparencia.PADRAO);
            previa.setSize(320, 240);
            for (Paleta flor : Paleta.values()) {
                for (Modo modo : Modo.values()) {
                    Aparencia a = new Aparencia(flor, modo, Fundo.FLORAL);
                    previa.mostrar(a);
                    BufferedImage img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    previa.paint(g);
                    g.dispose();
                    // Pixel do fundo da "janela" da prévia (entre a borda e o cabeçalho).
                    assertEquals(PaletasFlorais.de(a).fundo().getRGB(), img.getRGB(5, 120), a.toString());
                }
            }
            assertEquals(Aparencia.PADRAO, GerenciadorTema.aparencia());
        });
    }

    @Test
    void descricaoLegivel() {
        assertEquals("Fuji, escuro, fundo floral",
                DialogoAparencia.descrever(new Aparencia(Paleta.FUJI, Modo.ESCURO, Fundo.FLORAL)));
        assertEquals("Sakura, claro, fundo neutro",
                DialogoAparencia.descrever(Aparencia.PADRAO.comFundo(Fundo.NEUTRO)));
    }
}
