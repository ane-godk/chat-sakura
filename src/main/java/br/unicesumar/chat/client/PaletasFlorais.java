package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * As oito paletas (quatro flores × claro/escuro), calculadas uma única vez.
 *
 * Sakura Claro e Sakura Escuro são exatamente {@link PaletaSakura#CLARO} e
 * {@link PaletaSakura#NOTURNO}. As demais partem delas: cada papel recebe o
 * matiz e a saturação da flor (cinzas e cores de estado não mudam), as
 * cores de referência da flor entram nos papéis principais e, por fim, os
 * pares de texto e fundo são ajustados até pelo menos 4,5:1. Assim cada
 * modo escuro herda o desenho próprio do Sakura Noturno (sem preto puro,
 * textos rosados) em vez de ser o claro escurecido.
 */
final class PaletasFlorais {

    /** Cores de referência: destaque, fundo suave e fundo principal (modo claro). */
    record Referencia(Color destaque, Color suave, Color fundo, float saturacao) {
    }

    static final Map<Aparencia.Paleta, Referencia> REFERENCIAS = Map.of(
            Aparencia.Paleta.SAKURA, new Referencia(new Color(0xB65F88), new Color(0xF8D7E5), new Color(0xFFF3F7), 1f),
            Aparencia.Paleta.MYOSOTIS, new Referencia(new Color(0x5589B5), new Color(0xD9ECFA), new Color(0xF2F9FF), 1f),
            Aparencia.Paleta.FUJI, new Referencia(new Color(0x9071BA), new Color(0xE9DDF8), new Color(0xF8F3FF), 0.85f),
            Aparencia.Paleta.SUZURAN, new Referencia(new Color(0x659876), new Color(0xDCEEDC), new Color(0xF3FAF2), 0.72f));

    /** Papéis que não mudam com a flor: estados (online, aviso, erro) e nomes dos participantes. */
    private static final Set<String> FIXOS = Set.of(
            "verde", "ambar", "ambarTexto", "coral", "coralTexto", "coresParticipantes", "noturno");

    /** Contraste mínimo usado no ajuste (um pouco acima de 4,5 para absorver arredondamentos). */
    static final double CONTRASTE_ALVO = 4.6;

    /** Pares {texto, fundo}: ajusta-se o texto. */
    private static final String[][] PARES_TEXTO = {
            {"nomeProprio", "balaoProprioTopo"}, {"nomeProprio", "balaoProprioBase"},
            {"texto", "balaoProprioTopo"}, {"texto", "balaoProprioBase"},
            {"textoHorario", "balaoProprioTopo"}, {"textoHorario", "balaoProprioBase"},
            {"texto", "balaoOutroTopo"}, {"texto", "balaoOutroBase"},
            {"textoHorario", "balaoOutroTopo"}, {"textoHorario", "balaoOutroBase"},
            {"rosaEscuro", "degradeConversaTopo"}, {"rosaEscuro", "degradeConversaBase"},
            {"texto", "degradeConversaTopo"}, {"texto", "degradeConversaBase"},
            {"textoSuave", "rosaHoverClaro"}, {"rosaHover", "rosaHoverClaro"}, {"textoHorario", "rosaHoverClaro"},
            {"texto", "cartao"}, {"textoSuave", "cartao"}, {"texto", "rosaPetala"}, {"rosaHover", "cartao"},
            {"textoSuave", "fundo"}, {"ambarTexto", "fundo"}, {"coralTexto", "fundo"},
            {"rosaHover", "botaoSecundarioTopo"}, {"rosaHover", "botaoSecundarioBase"},
            {"rosaPressionado", "botaoSecundarioHoverTopo"}, {"rosaPressionado", "botaoSecundarioPressionadoBase"},
            {"texto", "brancoTranslucido"}, {"textoCabecalho", "fundo"}, {"textoCabecalho", "cartao"},
    };

    /** Pares {texto, fundo} dos botões principais: ajusta-se o fundo (o texto é quase branco). */
    private static final String[][] PARES_FUNDO = {
            {"textoBotaoPrincipal", "botaoPrincipalTopo"}, {"textoBotaoPrincipal", "botaoPrincipalBase"},
            {"textoBotaoPrincipal", "botaoPrincipalHoverTopo"}, {"textoBotaoPrincipal", "botaoPrincipalHoverBase"},
            {"textoBotaoPrincipal", "botaoPrincipalPressionadoTopo"},
            {"textoBotaoPrincipal", "botaoPrincipalPressionadoBase"},
    };

    private static final Map<Aparencia.Paleta, PaletaSakura> CLARAS = new EnumMap<>(Aparencia.Paleta.class);
    private static final Map<Aparencia.Paleta, PaletaSakura> ESCURAS = new EnumMap<>(Aparencia.Paleta.class);

    static {
        for (Aparencia.Paleta flor : Aparencia.Paleta.values()) {
            CLARAS.put(flor, flor == Aparencia.Paleta.SAKURA ? PaletaSakura.CLARO : gerar(flor, false));
            ESCURAS.put(flor, flor == Aparencia.Paleta.SAKURA ? PaletaSakura.NOTURNO : gerar(flor, true));
        }
    }

    private PaletasFlorais() {
    }

    /** Paleta pronta (a mesma instância a cada chamada). */
    static PaletaSakura de(Aparencia.Paleta flor, boolean escuro) {
        return (escuro ? ESCURAS : CLARAS).get(flor);
    }

    static PaletaSakura de(Aparencia aparencia) {
        return de(aparencia.paleta(), aparencia.escuro());
    }

    /** Matiz (0 a 1) da cor de destaque da flor. */
    static float matiz(Aparencia.Paleta flor) {
        Color c = REFERENCIAS.get(flor).destaque();
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0];
    }

    // ------------------------------------------------------------------

    private static PaletaSakura gerar(Aparencia.Paleta flor, boolean escuro) {
        PaletaSakura base = escuro ? PaletaSakura.NOTURNO : PaletaSakura.CLARO;
        Referencia ref = REFERENCIAS.get(flor);
        float alvo = matiz(flor);
        float origem = matiz(Aparencia.Paleta.SAKURA);

        Map<String, Object> papeis = componentes(base);
        for (Map.Entry<String, Object> papel : papeis.entrySet()) {
            if (!FIXOS.contains(papel.getKey()) && papel.getValue() instanceof Color cor) {
                papel.setValue(recolorir(cor, origem, alvo, ref.saturacao()));
            }
        }

        if (!escuro) {
            // Cores de referência da flor nos papéis principais do modo claro.
            papeis.put("rosa", ref.destaque());
            papeis.put("rosaPetala", ref.suave());
            papeis.put("fundo", ref.fundo());
        }

        ajustarContraste(papeis);
        return construir(papeis);
    }

    /** Quanto do desvio de matiz em relação ao rosa (ameixa, vinho…) é mantido na nova flor. */
    private static final float DESVIO_MANTIDO = 0.35f;

    /**
     * Leva a cor ao matiz da flor, mantendo só parte do desvio que ela tinha
     * em relação ao rosa (assim o ameixa do Noturno vira azul-noite, e não
     * verde-água), e ajusta a saturação. Cinzas puros ficam como estão.
     */
    static Color recolorir(Color cor, float origem, float alvo, float fatorSaturacao) {
        float[] hsb = Color.RGBtoHSB(cor.getRed(), cor.getGreen(), cor.getBlue(), null);
        if (hsb[1] < 0.02f) {
            return cor;
        }
        float desvio = hsb[0] - origem;
        desvio -= (float) Math.floor(desvio + 0.5f);   // entre -0,5 e 0,5
        float matiz = alvo + desvio * DESVIO_MANTIDO;
        matiz -= (float) Math.floor(matiz);
        float saturacao = Math.min(1f, hsb[1] * fatorSaturacao);
        int rgb = Color.HSBtoRGB(matiz, saturacao, hsb[2]);
        return new Color((rgb & 0xFFFFFF) | (cor.getAlpha() << 24), true);
    }

    private static void ajustarContraste(Map<String, Object> papeis) {
        for (int rodada = 0; rodada < 60; rodada++) {
            boolean mudou = false;
            for (String[] par : PARES_TEXTO) {
                mudou |= afastar(papeis, par[0], par[1]);
            }
            for (String[] par : PARES_FUNDO) {
                mudou |= afastar(papeis, par[1], par[0]);
            }
            mudou |= ajustarParticipantes(papeis);
            if (!mudou) {
                return;
            }
        }
    }

    /** Afasta {@code movel} de {@code fixo} (rumo ao preto ou ao branco) um pequeno passo, se preciso. */
    private static boolean afastar(Map<String, Object> papeis, String movel, String fixo) {
        Color m = (Color) papeis.get(movel);
        Color f = opaca((Color) papeis.get(fixo));
        Color ajustada = passo(m, f);
        if (ajustada == m) {
            return false;
        }
        papeis.put(movel, ajustada);
        return true;
    }

    private static Color passo(Color movel, Color fixo) {
        if (TemaSakura.contraste(opaca(movel), fixo) >= CONTRASTE_ALVO) {
            return movel;
        }
        boolean clarear = luminancia(fixo) < 0.18;
        Color alvo = clarear ? new Color(255, 255, 255, movel.getAlpha()) : new Color(0, 0, 0, movel.getAlpha());
        return TemaSakura.misturar(movel, alvo, 0.06);
    }

    @SuppressWarnings("unchecked")
    private static boolean ajustarParticipantes(Map<String, Object> papeis) {
        List<Color> nomes = new ArrayList<>((List<Color>) papeis.get("coresParticipantes"));
        boolean mudou = false;
        for (String fundo : new String[] {"balaoOutroTopo", "balaoOutroBase"}) {
            Color f = opaca((Color) papeis.get(fundo));
            for (int i = 0; i < nomes.size(); i++) {
                Color ajustada = passo(nomes.get(i), f);
                if (ajustada != nomes.get(i)) {
                    nomes.set(i, ajustada);
                    mudou = true;
                }
            }
        }
        papeis.put("coresParticipantes", List.copyOf(nomes));
        return mudou;
    }

    private static Color opaca(Color c) {
        return c.getAlpha() == 255 ? c : TemaSakura.comAlfa(c, 255);
    }

    private static double luminancia(Color c) {
        // Contraste com o preto: (L + 0,05) / 0,05.
        return TemaSakura.contraste(c, Color.BLACK) * 0.05 - 0.05;
    }

    // --- Reflexão sobre o record (todos os papéis, na ordem declarada) ---

    private static Map<String, Object> componentes(PaletaSakura paleta) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        try {
            for (RecordComponent papel : PaletaSakura.class.getRecordComponents()) {
                mapa.put(papel.getName(), papel.getAccessor().invoke(paleta));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Paleta ilegível.", e);
        }
        return mapa;
    }

    private static PaletaSakura construir(Map<String, Object> papeis) {
        RecordComponent[] componentes = PaletaSakura.class.getRecordComponents();
        Class<?>[] tipos = new Class<?>[componentes.length];
        Object[] valores = new Object[componentes.length];
        for (int i = 0; i < componentes.length; i++) {
            tipos[i] = componentes[i].getType();
            valores[i] = papeis.get(componentes[i].getName());
        }
        try {
            Constructor<PaletaSakura> construtor = PaletaSakura.class.getDeclaredConstructor(tipos);
            return construtor.newInstance(valores);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Paleta inválida.", e);
        }
    }
}
