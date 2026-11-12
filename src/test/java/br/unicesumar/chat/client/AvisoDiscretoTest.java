package br.unicesumar.chat.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Avisos não modais: um por vez, temporários ou persistentes, nos dois temas (B3). */
class AvisoDiscretoTest {

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable t) {
                erro.set(t);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    @AfterEach
    void restaurarTema() throws Exception {
        naEdt(() -> GerenciadorTema.definir(GerenciadorTema.Tema.CLARO));
    }

    @Test
    void avisosSeguidosDevemSubstituirOAnteriorSemEmpilhar() throws Exception {
        naEdt(() -> {
            AvisoDiscreto aviso = new AvisoDiscreto();
            assertFalse(aviso.isVisible(), "Oculto até haver aviso");

            aviso.mostrar("Conversa não encontrada.", AvisoDiscreto.Tipo.ERRO);
            aviso.mostrar("Muitas solicitações em pouco tempo.", AvisoDiscreto.Tipo.ERRO);
            aviso.mostrar("Histórico indisponível.", AvisoDiscreto.Tipo.ERRO);

            assertTrue(aviso.isVisible());
            assertEquals(3, aviso.exibicoes());
            assertEquals("Histórico indisponível.", aviso.mensagem(), "Só o último aparece");
            assertEquals(1, aviso.getParent() == null ? 1 : aviso.getParent().getComponentCount(),
                    "Um único componente");
            assertTrue(aviso.temporario(), "Erro comum some sozinho");
            assertEquals("Aviso: Histórico indisponível.", aviso.getAccessibleContext().getAccessibleName());

            aviso.ocultar();
            assertFalse(aviso.isVisible());
            assertFalse(aviso.temporario());
        });
    }

    @Test
    void avisoPersistenteComAcaoDeveFicarAteSerAcionado() throws Exception {
        AtomicInteger cancelamentos = new AtomicInteger();
        naEdt(() -> {
            AvisoDiscreto aviso = new AvisoDiscreto();
            aviso.mostrar("Conexão perdida. Tentando reconectar...", AvisoDiscreto.Tipo.INFORMACAO,
                    true, "Cancelar", cancelamentos::incrementAndGet);

            assertTrue(aviso.isVisible());
            assertFalse(aviso.temporario(), "Reconexão fica visível");
            assertTrue(aviso.temAcao());

            aviso.acionar();
            assertEquals(1, cancelamentos.get());
            assertFalse(aviso.isVisible());

            // Um aviso comum depois não herda a ação anterior.
            aviso.mostrar("Reconexão cancelada.", AvisoDiscreto.Tipo.INFORMACAO);
            assertFalse(aviso.temAcao());
            aviso.acionar();
            assertEquals(1, cancelamentos.get());
        });
    }

    @Test
    void avisoDeveSerPintadoComCoresDosDoisTemas() throws Exception {
        int[] centro = new int[2];
        naEdt(() -> {
            for (GerenciadorTema.Tema tema : GerenciadorTema.Tema.values()) {
                GerenciadorTema.definir(tema);
                AvisoDiscreto aviso = new AvisoDiscreto();
                aviso.mostrar("Aviso de teste", AvisoDiscreto.Tipo.ERRO);
                aviso.setSize(400, 40);
                aviso.doLayout();
                BufferedImage imagem = new BufferedImage(400, 40, BufferedImage.TYPE_INT_ARGB);
                aviso.paint(imagem.getGraphics());
                centro[tema.ordinal()] = imagem.getRGB(5, 20);
            }
        });
        assertNotEquals(0, centro[0] >>> 24, "Fundo pintado no tema Claro");
        assertNotEquals(0, centro[1] >>> 24, "Fundo pintado no tema Noturno");
        assertNotEquals(centro[0], centro[1], "Cores acompanham o tema");
    }
}
