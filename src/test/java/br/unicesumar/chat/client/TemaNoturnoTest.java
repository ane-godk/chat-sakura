package br.unicesumar.chat.client;

import br.unicesumar.chat.client.GerenciadorTema.Tema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Sakura Noturno, contraste nos dois temas e troca de tema durante o uso. */
class TemaNoturnoTest {

    @AfterEach
    void voltarAoClaro() throws Exception {
        naEdt(() -> GerenciadorTema.definir(Tema.CLARO));
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

    // --- Paleta noturna ---

    @Test
    void paletaNoturnaDeveTerTodosOsPapeisEDiferirDaClara() throws Exception {
        PaletaSakura n = PaletaSakura.NOTURNO;
        for (RecordComponent papel : PaletaSakura.class.getRecordComponents()) {
            assertNotNull(papel.getAccessor().invoke(n), papel.getName());
        }
        assertTrue(n.noturno());
        assertFalse(PaletaSakura.CLARO.noturno());
        assertNotEquals(PaletaSakura.CLARO.fundo(), n.fundo());
        assertEquals(5, n.coresParticipantes().size());
    }

    @Test
    void noturnoNaoDeveUsarPretoPuroNemBrancoPuroOpacos() {
        PaletaSakura n = PaletaSakura.NOTURNO;
        Color[] fundos = {n.fundo(), n.cartao(), n.campoDesabilitado(), n.degradeConversaTopo(),
                n.degradeConversaBase(), n.balaoOutroTopo(), n.balaoOutroBase(),
                n.balaoProprioTopo(), n.balaoProprioBase(), n.rosaHoverClaro()};
        for (Color c : fundos) {
            assertNotEquals(Color.BLACK.getRGB(), c.getRGB(), "preto puro: " + c);
        }
        Color[] textos = {n.texto(), n.textoSuave(), n.textoHorario(), n.textoBotaoPrincipal(), n.nomeProprio()};
        for (Color c : textos) {
            assertNotEquals(Color.WHITE.getRGB(), c.getRGB(), "branco puro: " + c);
        }
    }

    /** Pares de texto pequeno e fundo, incluindo as duas pontas de cada degradê. */
    private static List<Object[]> paresDeTexto(PaletaSakura c) {
        List<Object[]> pares = new ArrayList<>();
        for (Color fundo : new Color[] {c.balaoProprioTopo(), c.balaoProprioBase()}) {
            pares.add(new Object[] {"nome próprio / balão próprio", c.nomeProprio(), fundo});
            pares.add(new Object[] {"texto / balão próprio", c.texto(), fundo});
            pares.add(new Object[] {"horário / balão próprio", c.textoHorario(), fundo});
        }
        for (Color fundo : new Color[] {c.balaoOutroTopo(), c.balaoOutroBase()}) {
            pares.add(new Object[] {"texto / balão dos outros", c.texto(), fundo});
            pares.add(new Object[] {"horário / balão dos outros", c.textoHorario(), fundo});
            for (Color nome : c.coresParticipantes()) {
                pares.add(new Object[] {"participante / balão dos outros", nome, fundo});
            }
        }
        for (Color fundo : new Color[] {c.degradeConversaTopo(), c.degradeConversaBase()}) {
            pares.add(new Object[] {"separador / conversa", c.rosaEscuro(), fundo});
            pares.add(new Object[] {"linha padrão / conversa", c.texto(), fundo});
        }
        pares.add(new Object[] {"sistema / pílula", c.textoSuave(), c.rosaHoverClaro()});
        pares.add(new Object[] {"aviso: nome / pílula", c.rosaHover(), c.rosaHoverClaro()});
        pares.add(new Object[] {"aviso: horário / pílula", c.textoHorario(), c.rosaHoverClaro()});
        pares.add(new Object[] {"campo: texto", c.texto(), c.cartao()});
        pares.add(new Object[] {"campo: texto de orientação", c.textoSuave(), c.cartao()});
        pares.add(new Object[] {"menu: texto destacado", c.texto(), c.rosaPetala()});
        pares.add(new Object[] {"menu: título", c.rosaHover(), c.cartao()});
        pares.add(new Object[] {"contador", c.textoSuave(), c.fundo()});
        pares.add(new Object[] {"contador âmbar", c.ambarTexto(), c.fundo()});
        pares.add(new Object[] {"contador coral", c.coralTexto(), c.fundo()});
        for (Color fundo : new Color[] {c.botaoPrincipalTopo(), c.botaoPrincipalBase(),
                c.botaoPrincipalHoverTopo(), c.botaoPrincipalPressionadoTopo()}) {
            pares.add(new Object[] {"botão principal", c.textoBotaoPrincipal(), fundo});
        }
        for (Color fundo : new Color[] {c.botaoSecundarioTopo(), c.botaoSecundarioBase()}) {
            pares.add(new Object[] {"botão secundário", c.rosaHover(), fundo});
        }
        for (Color fundo : new Color[] {c.botaoSecundarioHoverTopo(), c.botaoSecundarioPressionadoBase()}) {
            pares.add(new Object[] {"botão secundário destacado", c.rosaPressionado(), fundo});
        }
        pares.add(new Object[] {"selo de status", c.texto(), TemaSakura.comAlfa(c.brancoTranslucido(), 255)});
        // Fora da lista, por ser isento (WCAG 1.4.3): texto de botão desabilitado.
        return pares;
    }

    @Test
    void textosPequenosDevemTerContrasteMinimoNosDoisTemas() {
        for (PaletaSakura paleta : List.of(PaletaSakura.CLARO, PaletaSakura.NOTURNO)) {
            for (Object[] par : paresDeTexto(paleta)) {
                double contraste = TemaSakura.contraste((Color) par[1], (Color) par[2]);
                assertTrue(contraste >= 4.5, (paleta.noturno() ? "Noturno, " : "Claro, ")
                        + par[0] + ": " + String.format("%.2f", contraste));
            }
        }
    }

    /** Regressão: o nome próprio no Sakura Claro tinha ~3,9:1. */
    @Test
    void nomeProprioNoClaroDeveTerContrasteCorrigido() {
        PaletaSakura c = PaletaSakura.CLARO;
        assertEquals(new Color(165, 50, 98), c.nomeProprio());
        assertTrue(TemaSakura.contraste(c.nomeProprio(), c.balaoProprioTopo()) >= 4.5);
        assertTrue(TemaSakura.contraste(c.nomeProprio(), c.balaoProprioBase()) >= 4.5);
        assertEquals(new Color(190, 64, 116), c.rosaEscuro(), "Demais usos do rosa escuro intactos.");
    }

    // --- Gerenciador de tema ---

    @Test
    void gerenciadorDeveTrocarAPaletaEAvisarUmaVezPorMudanca() throws Exception {
        AtomicInteger avisos = new AtomicInteger();
        Runnable ouvinte = avisos::incrementAndGet;

        naEdt(() -> {
            GerenciadorTema.aoMudar(ouvinte);
            GerenciadorTema.aoMudar(ouvinte);   // registro repetido é ignorado

            GerenciadorTema.definir(Tema.NOTURNO);
            assertSame(PaletaSakura.NOTURNO, TemaSakura.cores());
            GerenciadorTema.definir(Tema.NOTURNO);   // sem mudança, sem aviso

            GerenciadorTema.alternar();
            assertSame(PaletaSakura.CLARO, TemaSakura.cores());

            GerenciadorTema.removerOuvinte(ouvinte);
            GerenciadorTema.alternar();
        });

        assertEquals(2, avisos.get());
    }

    @Test
    void trocaPedidaForaDaEdtDeveSerFeitaNaEdt() throws Exception {
        AtomicReference<Boolean> naEdt = new AtomicReference<>();
        Runnable ouvinte = () -> naEdt.set(SwingUtilities.isEventDispatchThread());
        GerenciadorTema.aoMudar(ouvinte);
        try {
            GerenciadorTema.definir(Tema.NOTURNO);       // chamada da thread do teste
            SwingUtilities.invokeAndWait(() -> { });     // aguarda a EDT
            assertEquals(Tema.NOTURNO, GerenciadorTema.atual());
            assertTrue(naEdt.get());
        } finally {
            GerenciadorTema.removerOuvinte(ouvinte);
        }
    }

    @Test
    void deveInterpretarAPropriedadeDeTema() {
        assertEquals(Tema.CLARO, GerenciadorTema.interpretar(null));
        assertEquals(Tema.NOTURNO, GerenciadorTema.interpretar(" Noturno "));
        assertEquals(Tema.NOTURNO, GerenciadorTema.interpretar("escuro"));
        assertEquals(Tema.CLARO, GerenciadorTema.interpretar("outro"));
    }

    // --- Troca durante o uso ---

    @Test
    void alternanciasRepetidasDevemPreservarConversaLayoutERolagem() throws Exception {
        naEdt(() -> {
            PainelConversa painel = new PainelConversa();
            painel.definirUsuarioAtual("Ana");
            List<String> linhas = new ArrayList<>(List.of(
                    "SISTEMA: Conectado como Ana",
                    "SISTEMA: ===== HISTÓRICO RECENTE =====",
                    "SISTEMA: [04/11 20:30] Bia se conectou",
                    "SISTEMA: ===== FIM DO HISTÓRICO ====="));
            for (int i = 0; i < 40; i++) {
                linhas.add("[04/11 20:3" + (i % 10) + "] " + (i % 2 == 0 ? "Bia" : "Ana") + ": mensagem " + i);
            }
            linhas.forEach(painel::adicionarLinha);

            JScrollPane rolagem = new JScrollPane(painel);
            rolagem.setSize(600, 300);
            rolagem.validate();
            rolagem.getVerticalScrollBar().setValue(420);   // lendo mensagens antigas
            rolagem.validate();

            Component[] antes = painel.getComponents();
            List<Rectangle> posicoes = new ArrayList<>();
            for (Component c : antes) {
                posicoes.add(c.getBounds());
            }
            String texto = painel.texto();
            int rolagemAntes = rolagem.getVerticalScrollBar().getValue();

            for (int i = 0; i < 10; i++) {
                GerenciadorTema.alternar();
                rolagem.validate();
            }
            GerenciadorTema.alternar();   // termina no Noturno (11 trocas)
            rolagem.validate();

            assertEquals(Tema.NOTURNO, GerenciadorTema.atual());
            assertArrayEquals(antes, painel.getComponents(), "Mesmos componentes, sem duplicação.");
            for (int i = 0; i < antes.length; i++) {
                assertEquals(posicoes.get(i), antes[i].getBounds(), "posição do item " + i);
            }
            assertEquals(texto, painel.texto());
            assertEquals(String.join("\n", linhas) + "\n", painel.texto());
            assertEquals(rolagemAntes, rolagem.getVerticalScrollBar().getValue());
        });
    }

    @Test
    void componentesDevemSeguirAPaletaAtivaAposATroca() throws Exception {
        naEdt(() -> {
            CampoTextoSakura campo = new CampoTextoSakura(10, "Digite", null);
            IndicadorConexao indicador = new IndicadorConexao();
            BalaoMensagem proprio = new BalaoMensagem(FormatoLinhaChat.analisar("[04/11 20:40] Ana: oi"), true);
            BalaoMensagem outro = new BalaoMensagem(FormatoLinhaChat.analisar("[04/11 20:40] Bia: oi"), false);
            JMenuItem itemAntes = (JMenuItem) proprio.menuDeContexto().getComponent(0);

            assertEquals(PaletaSakura.CLARO.texto(), campo.getForeground());
            assertEquals(PaletaSakura.CLARO.nomeProprio(), proprio.corNome());

            GerenciadorTema.definir(Tema.NOTURNO);
            PaletaSakura n = PaletaSakura.NOTURNO;

            assertEquals(n.texto(), campo.getForeground());
            assertEquals(n.rosaPetala(), campo.getSelectionColor());
            assertEquals(n.texto(), indicador.getForeground());
            assertEquals(n.coral(), IndicadorConexao.Estado.DESCONECTADO.ponto());
            assertEquals(n.nomeProprio(), proprio.corNome());
            assertEquals(n.corParticipante("Bia"), outro.corNome());

            JMenuItem itemDepois = (JMenuItem) proprio.menuDeContexto().getComponent(0);
            assertNotSame(itemAntes, itemDepois, "Menu recriado no novo tema.");
            assertEquals(n.rosaPetala(), ((ItensMenuSakura.UiItem) itemDepois.getUI()).fundoSelecao());

            assertInstanceOf(TooltipSakura.class, indicador.createToolTip());
            assertEquals(n.cartao(), indicador.createToolTip().getBackground());
        });
    }

    @Test
    void trocaDeTemaNaoDeveAlterarOUIManager() throws Exception {
        naEdt(() -> {
            GerenciadorTema.definir(Tema.NOTURNO);
            MenuAnimacoes.criar();
            MenuMensagem.criar(() -> { });
            new IndicadorConexao().createToolTip();
            GerenciadorTema.definir(Tema.CLARO);

            for (String chave : new String[] {"MenuItem.selectionBackground", "MenuItem.selectionForeground",
                    "RadioButtonMenuItem.selectionBackground", "ToolTip.background", "ToolTip.foreground",
                    "Panel.background", "Label.foreground"}) {
                assertEquals(UIManager.getLookAndFeelDefaults().get(chave), UIManager.get(chave), chave);
            }
        });
    }

    // --- Imagens noturnas em memória ---

    private static double luminanciaMedia(BufferedImage img) {
        double soma = 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 7) {
            for (int x = 0; x < img.getWidth(); x += 7) {
                int rgb = img.getRGB(x, y);
                soma += ((rgb >> 16) & 255) * 0.2126 + ((rgb >> 8) & 255) * 0.7152 + (rgb & 255) * 0.0722;
                n++;
            }
        }
        return soma / n;
    }

    @Test
    void bannerEFlorNoturnosDevemSerGeradosSemAlterarOsOriginais() throws Exception {
        naEdt(() -> {
            BufferedImage bannerOriginal = RecursosSakura.banner().orElseThrow();
            BufferedImage bannerClaro = RecursosSakura.bannerNoTema().orElseThrow();
            assertSame(bannerOriginal, bannerClaro);

            GerenciadorTema.definir(Tema.NOTURNO);
            BufferedImage bannerNoturno = RecursosSakura.bannerNoTema().orElseThrow();
            BufferedImage florNoturna = RecursosSakura.florEmPixels(32).orElseThrow();

            assertNotSame(bannerOriginal, bannerNoturno);
            assertEquals(bannerOriginal.getWidth(), bannerNoturno.getWidth());
            assertEquals(bannerOriginal.getHeight(), bannerNoturno.getHeight());
            assertTrue(luminanciaMedia(bannerNoturno) < luminanciaMedia(bannerOriginal) * 0.6,
                    "Banner noturno mais escuro.");

            GerenciadorTema.definir(Tema.CLARO);
            BufferedImage florClara = RecursosSakura.florEmPixels(32).orElseThrow();
            assertNotSame(florClara, florNoturna);
            assertTrue(luminanciaMedia(florNoturna) < luminanciaMedia(florClara));

            // O original continua igual (mesmo objeto e mesmo conteúdo).
            assertSame(bannerOriginal, RecursosSakura.banner().orElseThrow());
        });
    }
}
