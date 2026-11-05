package br.unicesumar.chat.client;

import br.unicesumar.chat.client.FormatoLinhaChat.Linha;
import br.unicesumar.chat.client.FormatoLinhaChat.Tipo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Avisos de presença no cliente: classificação, apresentação e exportação. */
class AvisoPresencaClienteTest {

    private static final String ENTRADA = "SISTEMA: [04/11 20:34] Ana se conectou";
    private static final String SAIDA = "SISTEMA: [04/11 20:38] Ana se desconectou";

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

    // --- Classificação ---

    @Test
    void deveReconhecerEntradaESaida() {

        Linha entrada = FormatoLinhaChat.analisar(ENTRADA);
        Linha saida = FormatoLinhaChat.analisar(SAIDA);

        assertEquals(Tipo.AVISO_PRESENCA, entrada.tipo());
        assertEquals("[04/11 20:34]", entrada.horario());
        assertEquals("Ana", entrada.remetente());
        assertEquals("se conectou", entrada.conteudo());

        assertEquals(Tipo.AVISO_PRESENCA, saida.tipo());
        assertEquals("se desconectou", saida.conteudo());

        assertFalse(entrada.enviadaPor("Ana"), "Aviso não é mensagem do participante.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Imitações digitadas por usuários: chegam como mensagens.
            "[04/11 20:34] Ana: SISTEMA: [04/11 20:34] Bia se conectou",
            "[04/11 20:34] SISTEMA: [04/11 20:34] Bia se conectou",
            "[04/11 20:34] Bia: se conectou"
    })
    void imitacoesDeUsuariosDevemContinuarSendoMensagens(String linha) {

        assertEquals(Tipo.MENSAGEM, FormatoLinhaChat.analisar(linha).tipo());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SISTEMA: [04/11 20:34] Ana se conectou agora",
            "SISTEMA: [04/11 20:34] Ana entrou",
            "SISTEMA: [4/11 20:34] Ana se conectou",
            "SISTEMA: [04/11 20h34] Ana se conectou",
            "SISTEMA: [04/11 20:34] Ana Maria se conectou",
            "SISTEMA: [04/11 20:34]  se conectou",
            "SISTEMA: Ana se conectou",
            "SISTEMA: [04/11 20:34] NomeComMaisDeTrintaCaracteresXYZ se conectou"
    })
    void formatosInvalidosDevemSerTratadosComoSistemaComum(String linha) {

        assertEquals(Tipo.SISTEMA, FormatoLinhaChat.analisar(linha).tipo());
    }

    @Test
    void linhaQueNaoComecaComSistemaNaoEAviso() {

        assertEquals(Tipo.OUTRO, FormatoLinhaChat.analisar(" " + ENTRADA).tipo());
    }

    // --- Apresentação ---

    @Test
    void deveExibirParticipanteAcaoEHorario() throws Exception {

        naEdt(() -> {
            AvisoPresenca entrada = new AvisoPresenca(FormatoLinhaChat.analisar(ENTRADA));
            AvisoPresenca saida = new AvisoPresenca(FormatoLinhaChat.analisar(SAIDA));

            assertEquals("Ana se conectou · 20:34", entrada.textoExibido());
            assertEquals("Ana se desconectou · 20:38", saida.textoExibido());
            assertEquals("20:34", entrada.horarioExibido());
            assertTrue(entrada.entrada());
            assertFalse(saida.entrada());
            assertEquals(ItemConversa.Alinhamento.LARGURA_TOTAL, entrada.alinhamento());
        });
    }

    @Test
    void pilulaDeveCaberNoTamanhoMinimoMesmoComNomeLongo() throws Exception {

        naEdt(() -> {
            String nome = "a".repeat(30);
            AvisoPresenca aviso = new AvisoPresenca(FormatoLinhaChat.analisar(
                    "SISTEMA: [04/11 20:38] " + nome + " se desconectou"));

            Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            int pilula = aviso.larguraPilula(g.getFontRenderContext());
            g.dispose();

            // Área de conversa no tamanho mínimo da janela (600 × 420).
            int disponivel = 540 - 2 * PainelConversa.MARGEM_H;
            assertTrue(pilula <= disponivel, "Pílula de " + pilula + " px");
        });
    }

    @Test
    void historicoEAvisosAoVivoDevemTerAMesmaApresentacaoEOrdem() throws Exception {

        List<String> linhas = List.of(
                "SISTEMA: Conectado como Bia",
                "SISTEMA: ===== HISTÓRICO RECENTE =====",
                ENTRADA,
                "[04/11 20:35] Ana: Oi!",
                SAIDA,
                "SISTEMA: ===== FIM DO HISTÓRICO =====",
                "SISTEMA: [04/11 20:40] Caio se conectou",
                "[04/11 20:41] Caio: SISTEMA: [04/11 20:41] Ana se conectou"
        );

        naEdt(() -> {
            PainelConversa painel = new PainelConversa();
            painel.definirUsuarioAtual("Bia");
            linhas.forEach(painel::adicionarLinha);
            painel.setSize(600, 10);
            painel.setSize(600, painel.getPreferredSize().height);
            painel.doLayout();

            Component[] itens = painel.getComponents();

            assertInstanceOf(AvisoPresenca.class, itens[2]);   // histórico
            assertInstanceOf(BalaoMensagem.class, itens[3]);
            assertInstanceOf(AvisoPresenca.class, itens[4]);   // histórico
            assertInstanceOf(AvisoPresenca.class, itens[6]);   // ao vivo
            assertInstanceOf(BalaoMensagem.class, itens[7]);   // imitação

            assertEquals("Ana se conectou · 20:34", ((AvisoPresenca) itens[2]).textoExibido());
            assertEquals("Caio se conectou · 20:40", ((AvisoPresenca) itens[6]).textoExibido());

            // Ordem e posição vertical preservadas, sem sobreposição.
            for (int i = 1; i < itens.length; i++) {
                assertTrue(itens[i].getY() >= itens[i - 1].getY() + itens[i - 1].getHeight());
            }

            // Exportação: linhas originais, exatamente como recebidas.
            assertEquals(String.join("\n", linhas) + "\n", painel.texto());
        });
    }
}
