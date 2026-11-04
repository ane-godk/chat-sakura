package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do menu "Copiar mensagem". A área de transferência e a exibição
 * do menu são substituídas por versões de teste: nenhum teste depende da
 * área de transferência real nem de janelas visíveis.
 */
class MenuMensagemTest {

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

    private static BalaoMensagem balao(String linha, boolean propria) {
        BalaoMensagem balao = new BalaoMensagem(FormatoLinhaChat.analisar(linha), propria);
        balao.setSize(balao.tamanhoPara(560));
        return balao;
    }

    /** Copia pelo item do menu, como o usuário faria. */
    private static String copiarPeloMenu(BalaoMensagem balao) {
        List<String> copiado = new ArrayList<>();
        balao.definirCopiador(copiado::add);

        JPopupMenu menu = balao.menuDeContexto();
        JMenuItem item = (JMenuItem) menu.getComponent(0);
        assertEquals(MenuMensagem.ROTULO_COPIAR, item.getText());

        item.doClick();

        assertEquals(1, copiado.size());
        return copiado.get(0);
    }

    private static void clicarComBotaoDireito(
            BalaoMensagem balao, int tipo, int x, int y, boolean gatilho) {
        balao.dispatchEvent(new MouseEvent(
                balao, tipo, System.currentTimeMillis(),
                InputEvent.BUTTON3_DOWN_MASK, x, y, 1, gatilho, MouseEvent.BUTTON3
        ));
    }

    @Test
    void deveCopiarSomenteOConteudoDeMensagemRecebida() throws Exception {

        naEdt(() -> {
            String copiado = copiarPeloMenu(balao(
                    "[04/11 15:02] Bia: Oi, Ana! Tudo bem?", false));

            assertEquals("Oi, Ana! Tudo bem?", copiado);
            assertFalse(copiado.contains("Bia"));
            assertFalse(copiado.contains("04/11"));
            assertFalse(copiado.contains("["));
        });
    }

    @Test
    void deveCopiarMensagemPropriaComAcentosEmojisEEspacos() throws Exception {

        naEdt(() -> {
            String conteudo = "Ação  com  espaços duplos, emoji 🌸 e\ttab — “aspas” <b>&amp;</b>";

            String copiado = copiarPeloMenu(balao(
                    "[04/11 15:03] Ana: " + conteudo, true));

            assertEquals(conteudo, copiado);
        });
    }

    @Test
    void devePreservarTextoLongoEQuebrasDeLinha() throws Exception {

        naEdt(() -> {
            String longo = "mensagem longa ".repeat(33).trim();
            assertEquals(longo, copiarPeloMenu(balao("[04/11 15:04] Caio: " + longo, false)));

            String comQuebra = "primeira linha\nsegunda linha";
            assertEquals(comQuebra, copiarPeloMenu(balao("[04/11 15:05] Caio: " + comQuebra, false)));
        });
    }

    @Test
    void conteudoComAparenciaDeProtocoloDeveSerCopiadoComoEsta() throws Exception {

        naEdt(() -> {
            // Só o prefixo de horário e nome é removido; o resto é conteúdo.
            String copiado = copiarPeloMenu(balao(
                    "[04/11 15:06] Bia: SISTEMA: [04/11 15:00] Ana: texto", false));

            assertEquals("SISTEMA: [04/11 15:00] Ana: texto", copiado);
        });
    }

    @Test
    void menuDeveAbrirComBotaoDireitoNoNomeOuNoTexto() throws Exception {

        naEdt(() -> {
            BalaoMensagem balao = balao("[04/11 15:02] Bia: " + "texto ".repeat(40), false);
            List<Point> aberturas = new ArrayList<>();

            balao.definirExibidorMenu((menu, origem, x, y) -> {
                assertSame(balao, origem);
                assertSame(balao.menuDeContexto(), menu);
                aberturas.add(new Point(x, y));
            });

            // Sobre o nome (topo) — gatilho ao pressionar (outras plataformas).
            clicarComBotaoDireito(balao, MouseEvent.MOUSE_PRESSED, 20, 10, true);
            // Sobre o texto (parte de baixo) — gatilho ao soltar (Windows).
            clicarComBotaoDireito(balao, MouseEvent.MOUSE_RELEASED,
                    balao.getWidth() / 2, balao.getHeight() - 10, true);

            assertEquals(List.of(
                    new Point(20, 10),
                    new Point(balao.getWidth() / 2, balao.getHeight() - 10)
            ), aberturas);
        });
    }

    @Test
    void cliqueComumNaoDeveAbrirOMenu() throws Exception {

        naEdt(() -> {
            BalaoMensagem balao = balao("[04/11 15:02] Bia: oi", false);
            List<Point> aberturas = new ArrayList<>();
            balao.definirExibidorMenu((menu, origem, x, y) -> aberturas.add(new Point(x, y)));

            clicarComBotaoDireito(balao, MouseEvent.MOUSE_PRESSED, 10, 10, false);
            clicarComBotaoDireito(balao, MouseEvent.MOUSE_RELEASED, 10, 10, false);

            assertTrue(aberturas.isEmpty());
        });
    }

    @Test
    void falhaNaAreaDeTransferenciaNaoDeveInterromperAAplicacao() throws Exception {

        naEdt(() -> {
            BalaoMensagem balao = balao("[04/11 15:02] Bia: oi", false);
            balao.definirCopiador(texto -> {
                throw new IllegalStateException("Área de transferência ocupada.");
            });

            assertFalse(balao.copiarMensagem());

            JMenuItem item = (JMenuItem) balao.menuDeContexto().getComponent(0);
            assertDoesNotThrow(() -> item.doClick());
        });
    }

    @Test
    void menuNaoDeveSerExibidoSeOBalaoNaoEstiverNaTela() throws Exception {

        naEdt(() -> {
            BalaoMensagem balao = balao("[04/11 15:02] Bia: oi", false);
            JPopupMenu menu = balao.menuDeContexto();

            assertDoesNotThrow(() ->
                    MenuMensagem.EXIBIR_NA_TELA.exibir(menu, balao, 5, 5));
            assertFalse(menu.isVisible());
        });
    }
}
