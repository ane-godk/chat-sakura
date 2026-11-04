package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que a área de conversa preserva o texto puro (base da
 * exportação TXT) e aplica os estilos esperados. Os componentes são
 * manipulados na Event Dispatch Thread, sem exibir janelas.
 */
class AreaConversaTest {

    private static final List<String> CONVERSA = List.of(
            "SISTEMA: Bem-vindo ao Chat Sakura!",
            "SISTEMA: Informe seu nome de usuário:",
            "SISTEMA: Conectado como Ana",
            "SISTEMA: ===== HISTÓRICO RECENTE =====",
            "[04/11 15:02] Bia: Oi, Ana! Tudo bem?",
            "[04/11 15:03] Ana: hora: 10:30 <b>&amp;</b> \"aspas\" ção 🌸\tfim",
            "SISTEMA: ===== FIM DO HISTÓRICO =====",
            "[04/11 15:04] Caio: SISTEMA: isto é só uma mensagem",
            "linha inesperada [sem formato]",
            "",
            "SISTEMA: Falha ao enviar: Não existe conexão ativa."
    );

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

    private static AreaConversa criarConversa() {
        AreaConversa area = new AreaConversa();
        area.definirUsuarioAtual("Ana");
        CONVERSA.forEach(area::adicionarLinha);
        return area;
    }

    private static AttributeSet atributosEm(AreaConversa area, String trecho) {
        String texto = area.texto();
        int posicao = texto.indexOf(trecho);
        assertTrue(posicao >= 0, "Trecho não encontrado: " + trecho);
        return area.getStyledDocument()
                .getCharacterElement(posicao)
                .getAttributes();
    }

    @Test
    void textoPuroDeveSerIgualAoDoJTextAreaAnterior() throws Exception {

        naEdt(() -> {
            AreaConversa area = criarConversa();

            // Comportamento anterior: JTextArea.append(linha + "\n").
            JTextArea anterior = new JTextArea();
            CONVERSA.forEach(linha -> anterior.append(linha + "\n"));

            assertEquals(anterior.getText(), area.texto());
            assertEquals(
                    String.join("\n", CONVERSA) + "\n",
                    area.texto()
            );
            assertFalse(area.texto().contains("\r"));
        });
    }

    @Test
    void limparDeveRemoverTodoOConteudo() throws Exception {

        naEdt(() -> {
            AreaConversa area = criarConversa();

            area.limpar();

            assertEquals("", area.texto());
            assertTrue(area.texto().isBlank());
        });
    }

    @Test
    void deveDestacarNomesHorarioESistema() throws Exception {

        naEdt(() -> {
            AreaConversa area = criarConversa();

            AttributeSet propria = atributosEm(area, "Ana: hora");
            assertTrue(StyleConstants.isBold(propria));
            assertEquals(
                    TemaSakura.ROSA_ESCURO,
                    StyleConstants.getForeground(propria)
            );

            AttributeSet outra = atributosEm(area, "Bia: Oi");
            assertTrue(StyleConstants.isBold(outra));
            assertNotEquals(
                    TemaSakura.ROSA_ESCURO,
                    StyleConstants.getForeground(outra)
            );

            AttributeSet horario = atributosEm(area, "[04/11 15:02]");
            assertEquals(
                    TemaSakura.TEXTO_SUAVE,
                    StyleConstants.getForeground(horario)
            );

            AttributeSet sistema = atributosEm(area, "SISTEMA: Conectado");
            assertTrue(StyleConstants.isItalic(sistema));
        });
    }

    @Test
    void deveCentralizarSeparadoresDoHistorico() throws Exception {

        naEdt(() -> {
            AreaConversa area = criarConversa();
            StyledDocument documento = area.getStyledDocument();

            int separador = area.texto().indexOf("SISTEMA: ===== HISTÓRICO");
            int mensagem = area.texto().indexOf("[04/11 15:02]");

            assertEquals(
                    StyleConstants.ALIGN_CENTER,
                    StyleConstants.getAlignment(
                            documento.getParagraphElement(separador).getAttributes()
                    )
            );

            assertEquals(
                    StyleConstants.ALIGN_LEFT,
                    StyleConstants.getAlignment(
                            documento.getParagraphElement(mensagem).getAttributes()
                    )
            );
        });
    }

    @Test
    void linhasInesperadasDevemUsarEstiloPadrao() throws Exception {

        naEdt(() -> {
            AreaConversa area = criarConversa();

            AttributeSet inesperada = atributosEm(area, "linha inesperada");
            assertFalse(StyleConstants.isBold(inesperada));
            assertFalse(StyleConstants.isItalic(inesperada));
            assertEquals(
                    TemaSakura.TEXTO,
                    StyleConstants.getForeground(inesperada)
            );

            // "SISTEMA:" no meio de uma mensagem não vira linha do sistema.
            AttributeSet conteudo = atributosEm(area, "SISTEMA: isto");
            assertFalse(StyleConstants.isItalic(conteudo));
        });
    }
}
