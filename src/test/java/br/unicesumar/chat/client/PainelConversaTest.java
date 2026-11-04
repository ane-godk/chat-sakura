package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes da conversa em balões. Substituem os cinco testes do antigo
 * AreaConversaTest, preservando as mesmas garantias (texto puro idêntico,
 * limpeza, apresentação por tipo, separadores e linhas inesperadas), e
 * acrescentam alinhamento, largura e quebra automática.
 * Os componentes são manipulados na EDT, sem exibir janelas.
 */
class PainelConversaTest {

    private static final List<String> CONVERSA = List.of(
            "SISTEMA: Bem-vindo ao Chat Sakura!",
            "SISTEMA: Informe seu nome de usuário:",
            "SISTEMA: Conectado como Ana",
            "SISTEMA: ===== HISTÓRICO RECENTE =====",
            "[04/11 15:02] Bia: Oi, Ana! Tudo bem?",
            "[04/11 15:03] Ana: hora: 10:30 <b>&amp;</b> \"aspas\"  dois  espaços ção 🌸\tfim",
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

    private static PainelConversa criarConversa(int largura) {
        PainelConversa painel = new PainelConversa();
        painel.definirUsuarioAtual("Ana");
        CONVERSA.forEach(painel::adicionarLinha);
        dimensionar(painel, largura);
        return painel;
    }

    private static void dimensionar(PainelConversa painel, int largura) {
        painel.setSize(largura, 10);
        painel.setSize(largura, painel.getPreferredSize().height);
        painel.doLayout();
    }

    private static <T> T item(PainelConversa painel, int indice, Class<T> tipo) {
        Component componente = painel.getComponent(indice);
        assertInstanceOf(tipo, componente, "Item " + indice);
        return tipo.cast(componente);
    }

    // --- Garantias adaptadas do AreaConversaTest ---

    @Test
    void textoPuroDeveSerIgualAoExportadoAntes() throws Exception {

        naEdt(() -> {
            PainelConversa painel = criarConversa(600);

            // Comportamento anterior à P8.2: JTextArea.append(linha + "\n").
            JTextArea anterior = new JTextArea();
            CONVERSA.forEach(linha -> anterior.append(linha + "\n"));

            assertEquals(anterior.getText(), painel.texto());
            assertEquals(String.join("\n", CONVERSA) + "\n", painel.texto());
            assertFalse(painel.texto().contains("\r"));
        });
    }

    @Test
    void limparDeveRemoverTodoOConteudo() throws Exception {

        naEdt(() -> {
            PainelConversa painel = criarConversa(600);

            painel.limpar();

            assertEquals("", painel.texto());
            assertEquals(0, painel.getComponentCount());
        });
    }

    @Test
    void deveCriarApresentacaoPorTipoNaOrdemRecebida() throws Exception {

        naEdt(() -> {
            PainelConversa painel = criarConversa(600);

            assertEquals(CONVERSA.size(), painel.getComponentCount());

            LinhaTexto sistema = item(painel, 2, LinhaTexto.class);
            assertEquals(LinhaTexto.Estilo.SISTEMA, sistema.estilo());
            assertEquals("Conectado como Ana", sistema.textoExibido());

            BalaoMensagem bia = item(painel, 4, BalaoMensagem.class);
            assertEquals("Bia", bia.remetente());
            assertEquals("04/11 15:02", bia.horarioExibido());
            assertEquals("Oi, Ana! Tudo bem?", bia.conteudo());
            assertFalse(bia.propria());

            BalaoMensagem ana = item(painel, 5, BalaoMensagem.class);
            assertTrue(ana.propria());
            assertEquals(
                    "hora: 10:30 <b>&amp;</b> \"aspas\"  dois  espaços ção 🌸\tfim",
                    ana.conteudo()
            );
        });
    }

    @Test
    void separadoresDevemExibirRotulosSimplificados() throws Exception {

        naEdt(() -> {
            PainelConversa painel = criarConversa(600);

            SeparadorHistorico inicio = item(painel, 3, SeparadorHistorico.class);
            SeparadorHistorico fim = item(painel, 6, SeparadorHistorico.class);

            assertEquals("Histórico recente", inicio.rotulo());
            assertEquals("Fim do histórico", fim.rotulo());
            assertEquals(ItemConversa.Alinhamento.LARGURA_TOTAL, inicio.alinhamento());

            // A linha original continua no texto exportado.
            assertTrue(painel.texto().contains("SISTEMA: ===== HISTÓRICO RECENTE =====\n"));
        });
    }

    @Test
    void linhasInesperadasDevemUsarApresentacaoPadrao() throws Exception {

        naEdt(() -> {
            PainelConversa painel = criarConversa(600);

            LinhaTexto inesperada = item(painel, 8, LinhaTexto.class);
            assertEquals(LinhaTexto.Estilo.PADRAO, inesperada.estilo());
            assertEquals("linha inesperada [sem formato]", inesperada.textoExibido());

            LinhaTexto vazia = item(painel, 9, LinhaTexto.class);
            assertEquals(LinhaTexto.Estilo.PADRAO, vazia.estilo());
            assertTrue(vazia.getHeight() > 0);

            // "SISTEMA:" no conteúdo de um usuário continua sendo mensagem.
            BalaoMensagem caio = item(painel, 7, BalaoMensagem.class);
            assertEquals("Caio", caio.remetente());
            assertEquals("SISTEMA: isto é só uma mensagem", caio.conteudo());
        });
    }

    // --- Novos: alinhamento, largura e quebra ---

    @Test
    void mensagensPropriasDevemFicarADireitaEAsDemaisAEsquerda() throws Exception {

        naEdt(() -> {
            int largura = 600;
            PainelConversa painel = criarConversa(largura);

            Rectangle bia = painel.getComponent(4).getBounds();
            Rectangle ana = painel.getComponent(5).getBounds();

            assertEquals(PainelConversa.MARGEM_H, bia.x);
            assertEquals(largura - PainelConversa.MARGEM_H, ana.x + ana.width);
            assertTrue(ana.x > bia.x);
        });
    }

    @Test
    void itensDevemSeguirAOrdemSemSobreposicao() throws Exception {

        naEdt(() -> {
            PainelConversa painel = criarConversa(600);

            int fimAnterior = 0;
            for (Component componente : painel.getComponents()) {
                Rectangle area = componente.getBounds();
                assertTrue(area.y >= fimAnterior, "Itens sobrepostos.");
                assertTrue(area.height > 0);
                fimAnterior = area.y + area.height;
            }

            assertTrue(painel.getPreferredSize().height >= fimAnterior);
        });
    }

    @Test
    void balaoNaoDeveUltrapassarALarguraMaximaEDeveQuebrarTextoLongo() throws Exception {

        naEdt(() -> {
            PainelConversa painel = new PainelConversa();
            painel.definirUsuarioAtual("Ana");
            painel.adicionarLinha("[04/11 15:05] Bia: Oi!");
            painel.adicionarLinha("[04/11 15:06] Bia: " + "mensagem longa ".repeat(30));
            painel.adicionarLinha("[04/11 15:07] Ana: " + "x".repeat(400));

            int largura = 600;
            dimensionar(painel, largura);

            int disponivel = largura - 2 * PainelConversa.MARGEM_H;
            int maxima = (int) (disponivel * BalaoMensagem.FRACAO_MAXIMA);

            Rectangle curta = painel.getComponent(0).getBounds();
            Rectangle longa = painel.getComponent(1).getBounds();
            Rectangle semEspacos = painel.getComponent(2).getBounds();

            assertTrue(curta.width < longa.width, "Balão curto deve ser mais estreito.");
            assertTrue(longa.width <= maxima);
            assertTrue(semEspacos.width <= maxima);
            assertTrue(longa.height > curta.height * 2, "Texto longo deve quebrar em várias linhas.");
            assertTrue(semEspacos.x + semEspacos.width <= largura, "Sem rolagem horizontal.");
        });
    }

    @Test
    void balaoDeveSeAjustarAoRedimensionar() throws Exception {

        naEdt(() -> {
            PainelConversa painel = new PainelConversa();
            painel.adicionarLinha("[04/11 15:06] Bia: " + "texto que quebra ".repeat(20));

            dimensionar(painel, 800);
            Rectangle larga = painel.getComponent(0).getBounds();

            dimensionar(painel, 420);
            Rectangle estreita = painel.getComponent(0).getBounds();

            assertTrue(estreita.width < larga.width);
            assertTrue(estreita.height > larga.height);
            assertTrue(estreita.width <= (int) ((420 - 2 * PainelConversa.MARGEM_H)
                    * BalaoMensagem.FRACAO_MAXIMA));
        });
    }

    @Test
    void emojisDevemSerExibiveisNoTextoENosNomes() throws Exception {

        naEdt(() -> {
            int sakura = 0x1F338;

            assertTrue(TemaSakura.FONTE_TEXTO.canDisplay(sakura));
            assertTrue(TemaSakura.FONTE_SISTEMA.canDisplay(sakura));

            BalaoMensagem balao = new BalaoMensagem(
                    FormatoLinhaChat.analisar("[04/11 15:02] Bia🌸: oi"),
                    false
            );

            assertEquals("Bia🌸", balao.remetente());
            assertEquals(-1, balao.fonteNome().canDisplayUpTo("Bia🌸"));

            // Nomes comuns continuam com a fonte de destaque.
            BalaoMensagem comum = new BalaoMensagem(
                    FormatoLinhaChat.analisar("[04/11 15:02] Bia: oi"),
                    false
            );
            assertSame(TemaSakura.FONTE_NOME, comum.fonteNome());
        });
    }

    @Test
    void horarioDeveSerExibidoSemColchetes() {

        assertEquals("04/11 15:02", BalaoMensagem.semColchetes("[04/11 15:02]"));
        assertEquals("sem", BalaoMensagem.semColchetes("sem"));
    }
}
