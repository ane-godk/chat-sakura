package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Paleta centralizada (Sakura Claro) e menus sem alterações globais. */
class PaletaSakuraTest {

    private static final PaletaSakura C = PaletaSakura.CLARO;

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
    void temaAtivoDeveSerOSakuraClaro() {
        assertSame(PaletaSakura.CLARO, TemaSakura.cores());
    }

    @Test
    void todosOsPapeisDevemEstarDefinidos() throws Exception {
        for (RecordComponent papel : PaletaSakura.class.getRecordComponents()) {
            assertNotNull(papel.getAccessor().invoke(C), papel.getName());
        }
        assertEquals(5, C.coresParticipantes().size());
    }

    /** As cores principais continuam exatamente iguais às da interface original. */
    @Test
    void sakuraClaroDevePreservarAsCoresOriginais() {
        assertEquals(new Color(214, 84, 136), C.rosa());
        assertEquals(new Color(190, 64, 116), C.rosaEscuro());
        assertEquals(new Color(165, 50, 98), C.rosaHover());
        assertEquals(new Color(142, 40, 84), C.rosaPressionado());
        assertEquals(new Color(255, 228, 238), C.rosaPetala());
        assertEquals(new Color(246, 205, 222), C.rosaBorda());
        assertEquals(new Color(248, 187, 208), C.rosaFlor());
        assertEquals(new Color(255, 240, 246), C.rosaHoverClaro());
        assertEquals(new Color(255, 248, 251), C.fundo());
        assertEquals(Color.WHITE, C.cartao());
        assertEquals(new Color(74, 52, 66), C.texto());
        assertEquals(new Color(125, 100, 115), C.textoSuave());
        assertEquals(new Color(110, 86, 100), C.textoHorario());
        assertEquals(new Color(110, 70, 95), C.textoCabecalho());
        assertEquals(new Color(255, 232, 241), C.balaoProprioTopo());
        assertEquals(new Color(252, 218, 232), C.balaoProprioBase());
        assertEquals(new Color(244, 196, 216), C.balaoProprioBorda());
        assertEquals(new Color(255, 250, 252), C.balaoOutroBase());

        // Cores que antes estavam escritas direto nos componentes.
        assertEquals(new Color(198, 70, 122), C.botaoPrincipalTopo());
        assertEquals(new Color(170, 52, 102), C.botaoPrincipalBase());
        assertEquals(new Color(132, 36, 78), C.botaoPrincipalPressionadoBase());
        assertEquals(new Color(253, 226, 237), C.botaoSecundarioHoverBase());
        assertEquals(new Color(255, 241, 247), C.degradeConversaBase());
        assertEquals(new Color(255, 255, 255, 150), C.veuCabecalho());
        assertEquals(new Color(255, 255, 255, 170), C.haloTitulo());
        assertEquals(new Color(248, 187, 208, 150), C.brilhoFocoCampo());
        assertEquals(new Color(120, 30, 70, 110), C.dobraAviao());

        assertEquals(List.of(
                new Color(122, 74, 160),
                new Color(36, 115, 135),
                new Color(165, 85, 35),
                new Color(50, 120, 70),
                new Color(90, 90, 160)
        ), C.coresParticipantes());
    }

    @Test
    void textosDevemTerContrasteMinimoDe4e5() {
        Object[][] pares = {
                {"texto / cartão", C.texto(), C.cartao()},
                {"texto / balão próprio", C.texto(), C.balaoProprioBase()},
                {"texto suave / cartão", C.textoSuave(), C.cartao()},
                {"texto suave / pílula do sistema", C.textoSuave(), C.rosaHoverClaro()},
                {"texto suave / fundo (contador)", C.textoSuave(), C.fundo()},
                {"horário / balão próprio", C.textoHorario(), C.balaoProprioBase()},
                {"horário / balão dos outros", C.textoHorario(), C.balaoOutroBase()},
                {"nome no aviso / pílula", C.rosaHover(), C.rosaHoverClaro()},
                {"separador / fundo da conversa", C.rosaEscuro(), C.degradeConversaTopo()},
                {"botão principal", C.textoBotaoPrincipal(), C.botaoPrincipalTopo()},
                {"botão secundário", C.rosaHover(), C.botaoSecundarioBase()},
                {"contador âmbar / fundo", C.ambarTexto(), C.fundo()},
                {"contador coral / fundo", C.coralTexto(), C.fundo()}
        };

        for (Object[] par : pares) {
            double contraste = TemaSakura.contraste((Color) par[1], (Color) par[2]);
            assertTrue(contraste >= 4.5, par[0] + ": " + String.format("%.2f", contraste));
        }

        for (Color participante : C.coresParticipantes()) {
            assertTrue(TemaSakura.contraste(participante, C.balaoOutroBase()) >= 4.5);
        }
    }

    @Test
    void contrasteDeveSeguirAFormulaWcag() {
        assertEquals(21.0, TemaSakura.contraste(Color.BLACK, Color.WHITE), 1e-9);
        assertEquals(1.0, TemaSakura.contraste(Color.WHITE, Color.WHITE), 1e-9);
    }

    @Test
    void corDoParticipanteDeveSerEstavelEDaPaleta() {
        assertEquals(C.corParticipante("Bia"), C.corParticipante("BIA"));
        assertTrue(C.coresParticipantes().contains(C.corParticipante("Caio")));
    }

    // --- Menus sem UIManager global ---

    @Test
    void menusNaoDevemAlterarOUIManager() throws Exception {

        naEdt(() -> {
            String[] chaves = {
                    "MenuItem.selectionBackground", "MenuItem.selectionForeground",
                    "RadioButtonMenuItem.selectionBackground", "RadioButtonMenuItem.selectionForeground"
            };

            MenuMensagem.criar(() -> { });
            MenuAnimacoes.criar();

            for (String chave : chaves) {
                assertEquals(UIManager.getLookAndFeelDefaults().get(chave), UIManager.get(chave),
                        chave + " foi alterada globalmente.");
            }
        });
    }

    @Test
    void itensDosMenusDevemUsarAsCoresDaPaletaPorItem() throws Exception {

        naEdt(() -> {
            JPopupMenu copiar = MenuMensagem.criar(() -> { });
            JMenuItem item = (JMenuItem) copiar.getComponent(0);

            assertInstanceOf(ItensMenuSakura.UiItem.class, item.getUI());
            ItensMenuSakura.UiItem ui = (ItensMenuSakura.UiItem) item.getUI();
            assertEquals(C.rosaPetala(), ui.fundoSelecao());
            assertEquals(C.texto(), ui.textoSelecao());

            JPopupMenu animacoes = MenuAnimacoes.criar();
            int radios = 0;
            for (Component componente : animacoes.getComponents()) {
                if (componente instanceof JMenuItem radio) {
                    assertInstanceOf(ItensMenuSakura.UiItemRadio.class, radio.getUI());
                    assertEquals(C.rosaPetala(), ((ItensMenuSakura.UiItemRadio) radio.getUI()).fundoSelecao());
                    radios++;
                }
            }
            assertEquals(ConfiguracaoAnimacoes.Modo.values().length, radios);
        });
    }

    @Test
    void indicadorDeveObterACorDoPontoDaPaleta() {
        assertEquals(C.verde(), IndicadorConexao.Estado.CONECTADO.ponto());
        assertEquals(C.ambar(), IndicadorConexao.Estado.CONECTANDO.ponto());
        assertEquals(C.coral(), IndicadorConexao.Estado.DESCONECTADO.ponto());
    }
}
