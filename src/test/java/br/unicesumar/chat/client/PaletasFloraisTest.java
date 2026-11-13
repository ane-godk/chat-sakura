package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.model.Aparencia.Paleta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** As quatro flores em claro e escuro: identidade, contraste e troca pelo gerenciador. */
class PaletasFloraisTest {

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

    private static float matiz(Color c) {
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0] * 360f;
    }

    private static float distanciaMatiz(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return Math.min(d, 360f - d);
    }

    @Test
    void sakuraClaroEEscuroSaoAsPaletasOriginais() {
        assertSame(PaletaSakura.CLARO, PaletasFlorais.de(Paleta.SAKURA, false));
        assertSame(PaletaSakura.NOTURNO, PaletasFlorais.de(Paleta.SAKURA, true));
        assertSame(PaletaSakura.CLARO, PaletasFlorais.de(Aparencia.PADRAO));
    }

    @Test
    void oitoPaletasDistintasCompletasECalculadasUmaVez() throws Exception {
        Set<PaletaSakura> todas = new HashSet<>();
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura p = PaletasFlorais.de(flor, escuro);
                assertSame(p, PaletasFlorais.de(flor, escuro), "cache");
                assertEquals(escuro, p.noturno(), flor + " noturno");
                for (RecordComponent papel : PaletaSakura.class.getRecordComponents()) {
                    assertNotNull(papel.getAccessor().invoke(p), flor + "." + papel.getName());
                }
                assertEquals(5, p.coresParticipantes().size());
                todas.add(p);
            }
        }
        assertEquals(8, todas.size());
        Set<Integer> fundos = new HashSet<>();
        todas.forEach(p -> fundos.add(p.fundo().getRGB()));
        assertEquals(8, fundos.size(), "Cada paleta tem o próprio fundo");
    }

    @Test
    void modoClaroUsaAsCoresDeReferenciaDaFlor() {
        assertEquals(new Color(0x5589B5), PaletasFlorais.de(Paleta.MYOSOTIS, false).rosa());
        assertEquals(new Color(0xD9ECFA), PaletasFlorais.de(Paleta.MYOSOTIS, false).rosaPetala());
        assertEquals(new Color(0xF2F9FF), PaletasFlorais.de(Paleta.MYOSOTIS, false).fundo());
        assertEquals(new Color(0x9071BA), PaletasFlorais.de(Paleta.FUJI, false).rosa());
        assertEquals(new Color(0xE9DDF8), PaletasFlorais.de(Paleta.FUJI, false).rosaPetala());
        assertEquals(new Color(0xF8F3FF), PaletasFlorais.de(Paleta.FUJI, false).fundo());
        assertEquals(new Color(0x659876), PaletasFlorais.de(Paleta.SUZURAN, false).rosa());
        assertEquals(new Color(0xDCEEDC), PaletasFlorais.de(Paleta.SUZURAN, false).rosaPetala());
        assertEquals(new Color(0xF3FAF2), PaletasFlorais.de(Paleta.SUZURAN, false).fundo());
    }

    @Test
    void cadaFlorTemOMatizProprioNosDoisModos() {
        for (Paleta flor : Paleta.values()) {
            float esperado = PaletasFlorais.matiz(flor) * 360f;
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura p = PaletasFlorais.de(flor, escuro);
                for (Color c : new Color[] {p.rosa(), p.botaoPrincipalTopo(), p.balaoProprioTopo(), p.rosaFlor()}) {
                    assertTrue(distanciaMatiz(matiz(c), esperado) < 30,
                            flor + (escuro ? " escuro" : " claro") + ": " + c + " fora do matiz " + esperado);
                }
            }
        }
    }

    @Test
    void escuroNaoEOClaroEscurecido() {
        for (Paleta flor : Paleta.values()) {
            PaletaSakura claro = PaletasFlorais.de(flor, false);
            PaletaSakura escuro = PaletasFlorais.de(flor, true);
            // Fundo escuro, texto claro (a relação se inverte, não só escurece).
            assertTrue(TemaSakura.contraste(escuro.fundo(), Color.BLACK) < 2, flor + " fundo escuro");
            assertTrue(TemaSakura.contraste(escuro.texto(), Color.BLACK) > 12, flor + " texto claro");
            assertTrue(TemaSakura.contraste(claro.texto(), Color.BLACK) < 4, flor + " texto escuro no claro");
            assertNotEquals(Color.BLACK.getRGB(), escuro.fundo().getRGB());
            assertNotEquals(Color.WHITE.getRGB(), escuro.texto().getRGB());
        }
    }

    @Test
    void estadosNaoMudamComAFlor() {
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura base = escuro ? PaletaSakura.NOTURNO : PaletaSakura.CLARO;
                PaletaSakura p = PaletasFlorais.de(flor, escuro);
                assertEquals(base.verde(), p.verde());
                assertEquals(base.coral(), p.coral());
                assertEquals(base.ambar(), p.ambar());
            }
        }
    }

    @Test
    void textosPequenosTemContrasteMinimoNasOitoPaletas() {
        for (Paleta flor : Paleta.values()) {
            for (boolean escuro : new boolean[] {false, true}) {
                PaletaSakura p = PaletasFlorais.de(flor, escuro);
                for (Object[] par : TemaNoturnoTest.paresDeTexto(p)) {
                    double contraste = TemaSakura.contraste((Color) par[1], (Color) par[2]);
                    assertTrue(contraste >= 4.5, flor + (escuro ? " escuro, " : " claro, ") + par[0] + ": "
                            + String.format("%.2f", contraste));
                }
            }
        }
    }

    @Test
    void selecionarCadaPaletaEAlternarOModoMantemAFlorEOFundo() throws Exception {
        AtomicInteger avisos = new AtomicInteger();
        Runnable ouvinte = avisos::incrementAndGet;
        naEdt(() -> {
            GerenciadorTema.aoMudar(ouvinte);
            try {
                for (Paleta flor : Paleta.values()) {
                    Aparencia escolha = new Aparencia(flor, Aparencia.Modo.CLARO, Aparencia.Fundo.LISO);
                    GerenciadorTema.definir(escolha);
                    assertSame(PaletasFlorais.de(flor, false), TemaSakura.cores());
                    assertEquals(GerenciadorTema.Tema.CLARO, GerenciadorTema.atual());

                    GerenciadorTema.alternar();
                    assertSame(PaletasFlorais.de(flor, true), TemaSakura.cores());
                    assertEquals(escolha.comModo(Aparencia.Modo.ESCURO), GerenciadorTema.aparencia());
                    assertEquals(Aparencia.Fundo.LISO, GerenciadorTema.fundo());

                    GerenciadorTema.definir(GerenciadorTema.Tema.CLARO);
                    assertEquals(escolha, GerenciadorTema.aparencia());
                }
                int antes = avisos.get();
                GerenciadorTema.definir(GerenciadorTema.aparencia());   // sem mudança, sem aviso
                assertEquals(antes, avisos.get());
            } finally {
                GerenciadorTema.removerOuvinte(ouvinte);
            }
        });
        assertEquals(12, avisos.get(), "Uma notificação por mudança efetiva");
    }

    @Test
    void trocaDeAparenciaPedidaForaDaEdtAconteceNaEdt() throws Exception {
        AtomicReference<Boolean> naEdt = new AtomicReference<>();
        Runnable ouvinte = () -> naEdt.set(SwingUtilities.isEventDispatchThread());
        GerenciadorTema.aoMudar(ouvinte);
        try {
            Aparencia fuji = new Aparencia(Paleta.FUJI, Aparencia.Modo.ESCURO, Aparencia.Fundo.FLORAL);
            GerenciadorTema.definir(fuji);                // thread do teste
            SwingUtilities.invokeAndWait(() -> { });      // aguarda a EDT
            assertEquals(fuji, GerenciadorTema.aparencia());
            assertTrue(naEdt.get());
        } finally {
            GerenciadorTema.removerOuvinte(ouvinte);
        }
    }

    @Test
    void componentesSeguemAFlorAtiva() throws Exception {
        naEdt(() -> {
            CampoTextoSakura campo = new CampoTextoSakura(10, "Digite", null);
            GerenciadorTema.definir(new Aparencia(Paleta.SUZURAN, Aparencia.Modo.ESCURO, Aparencia.Fundo.NEUTRO));
            PaletaSakura s = PaletasFlorais.de(Paleta.SUZURAN, true);
            assertEquals(s.texto(), campo.getForeground());
            assertEquals(s.rosaPetala(), campo.getSelectionColor());
        });
    }
}
