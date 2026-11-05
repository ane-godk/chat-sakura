package br.unicesumar.chat.client;

import java.awt.Color;
import java.util.List;

/**
 * Paleta de cores de um tema Sakura, organizada por papel semântico.
 *
 * Todos os componentes consultam a paleta ativa ({@link TemaSakura#cores()})
 * no momento da pintura, o que permite trocar de tema sem reconstruir a
 * interface. {@link #CLARO} reproduz exatamente as cores originais.
 */
record PaletaSakura(

        // Rosas de destaque
        Color rosa,
        Color rosaEscuro,
        Color rosaHover,
        Color rosaPressionado,
        Color rosaPetala,
        Color rosaBorda,
        Color rosaFlor,
        Color rosaHoverClaro,

        // Fundos e textos
        Color fundo,
        Color cartao,
        Color campoDesabilitado,
        Color texto,
        Color textoSuave,
        Color textoHorario,
        Color textoCabecalho,

        // Estados
        Color verde,
        Color ambar,
        Color ambarTexto,
        Color coral,
        Color coralTexto,

        // Balões
        Color balaoProprioTopo,
        Color balaoProprioBase,
        Color balaoProprioBorda,
        Color balaoOutroTopo,
        Color balaoOutroBase,
        Color sombraSuave,

        /** Nomes de outros participantes (o próprio usa rosaEscuro). */
        List<Color> coresParticipantes,

        // Botão principal (degradê topo/base por estado) e texto
        Color botaoPrincipalTopo,
        Color botaoPrincipalBase,
        Color botaoPrincipalHoverTopo,
        Color botaoPrincipalHoverBase,
        Color botaoPrincipalPressionadoTopo,
        Color botaoPrincipalPressionadoBase,
        Color textoBotaoPrincipal,

        // Botão secundário (degradê topo/base por estado)
        Color botaoSecundarioTopo,
        Color botaoSecundarioBase,
        Color botaoSecundarioHoverTopo,
        Color botaoSecundarioHoverBase,
        Color botaoSecundarioPressionadoTopo,
        Color botaoSecundarioPressionadoBase,

        // Efeitos dos botões
        Color sombraBotao,
        Color brilhoBotaoPrincipal,
        Color brilhoBotaoSecundario,

        // Superfícies decoradas
        Color brancoTranslucido,
        Color degradeConversaTopo,
        Color degradeConversaBase,
        Color veuCabecalho,
        Color haloTitulo,
        Color brilhoFocoCampo,
        Color dobraAviao,

        /** Nome do próprio usuário dentro do balão (contraste ≥ 4,5:1). */
        Color nomeProprio,

        /** Indica tema escuro: imagens (banner, flor) recebem ajuste noturno. */
        boolean noturno
) {

    /**
     * Sakura Claro: as cores originais da interface. Única mudança: o nome
     * do próprio usuário passou de rosaEscuro para (165, 50, 98), elevando
     * o contraste sobre o balão rosa de ~3,9:1 para ~5:1.
     */
    static final PaletaSakura CLARO = new PaletaSakura(
            new Color(214, 84, 136),
            new Color(190, 64, 116),
            new Color(165, 50, 98),
            new Color(142, 40, 84),
            new Color(255, 228, 238),
            new Color(246, 205, 222),
            new Color(248, 187, 208),
            new Color(255, 240, 246),

            new Color(255, 248, 251),
            Color.WHITE,
            new Color(252, 242, 246),
            new Color(74, 52, 66),
            new Color(125, 100, 115),
            new Color(110, 86, 100),
            new Color(110, 70, 95),

            new Color(64, 160, 120),
            new Color(230, 160, 60),
            new Color(160, 95, 15),
            new Color(214, 92, 92),
            new Color(184, 60, 60),

            new Color(255, 232, 241),
            new Color(252, 218, 232),
            new Color(244, 196, 216),
            Color.WHITE,
            new Color(255, 250, 252),
            new Color(142, 40, 84, 18),

            List.of(
                    new Color(122, 74, 160),
                    new Color(36, 115, 135),
                    new Color(165, 85, 35),
                    new Color(50, 120, 70),
                    new Color(90, 90, 160)
            ),

            new Color(198, 70, 122),
            new Color(170, 52, 102),
            new Color(180, 58, 110),
            new Color(155, 44, 92),
            new Color(150, 42, 88),
            new Color(132, 36, 78),
            Color.WHITE,

            Color.WHITE,
            new Color(255, 240, 246),
            new Color(255, 240, 246),
            new Color(253, 226, 237),
            new Color(252, 222, 234),
            new Color(250, 214, 228),

            new Color(142, 40, 84),
            new Color(255, 255, 255, 60),
            new Color(255, 255, 255, 200),

            new Color(255, 255, 255, 220),
            Color.WHITE,
            new Color(255, 241, 247),
            new Color(255, 255, 255, 150),
            new Color(255, 255, 255, 170),
            new Color(248, 187, 208, 150),
            new Color(120, 30, 70, 110),

            new Color(165, 50, 98),
            false
    );

    /**
     * Sakura Noturno: ameixa escuro, vinho suave e rosa antigo, sem preto
     * puro nem cores neon. Textos claros e rosados, nunca branco puro.
     */
    static final PaletaSakura NOTURNO = new PaletaSakura(
            // Rosas de destaque
            new Color(0xC4, 0x6E, 0x96),     // rosa (bordas, anel de foco)
            new Color(0xF2, 0xA7, 0xC3),     // rosaEscuro → rosa antigo claro (título, separador)
            new Color(0xF2, 0xC4, 0xD7),     // rosaHover (texto do botão secundário, nome no aviso)
            new Color(0xF7, 0xD2, 0xE1),     // rosaPressionado
            new Color(0x4A, 0x2A, 0x40),     // rosaPetala (seleção, destaque de menu)
            new Color(0x4A, 0x32, 0x47),     // rosaBorda
            new Color(0xE8, 0xA3, 0xBF),     // rosaFlor (pétalas)
            new Color(0x3A, 0x28, 0x36),     // rosaHoverClaro (pílulas)

            // Fundos e textos
            new Color(0x1E, 0x14, 0x20),     // fundo da janela (ameixa profundo)
            new Color(0x2A, 0x1C, 0x2B),     // cartao (menus, campo)
            new Color(0x26, 0x1A, 0x27),     // campoDesabilitado
            new Color(0xF3, 0xE6, 0xEE),     // texto
            new Color(0xC9, 0xB3, 0xC2),     // textoSuave
            new Color(0xBF, 0xA6, 0xB7),     // textoHorario
            new Color(0xEB, 0xD7, 0xE2),     // textoCabecalho

            // Estados
            new Color(0x7F, 0xC8, 0xA9),
            new Color(0xE8, 0xB6, 0x6B),
            new Color(0xF0, 0xC2, 0x7A),
            new Color(0xE5, 0x8C, 0x8C),
            new Color(0xF2, 0xA0, 0xA0),

            // Balões
            new Color(0x5A, 0x2D, 0x48),     // próprio (vinho suave)
            new Color(0x4E, 0x27, 0x40),
            new Color(0x6E, 0x3A, 0x58),
            new Color(0x34, 0x23, 0x3A),     // dos outros
            new Color(0x2F, 0x20, 0x35),
            new Color(0, 0, 0, 60),          // sombra suave

            List.of(
                    new Color(0xC9, 0xA7, 0xF0),
                    new Color(0x7C, 0xC6, 0xD6),
                    new Color(0xF0, 0xA9, 0x79),
                    new Color(0x9A, 0xD3, 0xA0),
                    new Color(0xA9, 0xAE, 0xF2)
            ),

            // Botão principal
            new Color(0xB2, 0x4D, 0x7E),
            new Color(0x96, 0x40, 0x6B),
            new Color(0xA0, 0x42, 0x6F),
            new Color(0x86, 0x37, 0x60),
            new Color(0x8A, 0x38, 0x62),
            new Color(0x74, 0x30, 0x4F),
            new Color(0xFF, 0xF4, 0xF8),

            // Botão secundário
            new Color(0x34, 0x23, 0x3A),
            new Color(0x2F, 0x20, 0x35),
            new Color(0x3E, 0x2A, 0x40),
            new Color(0x38, 0x25, 0x3A),
            new Color(0x4A, 0x2A, 0x40),
            new Color(0x43, 0x26, 0x39),

            // Efeitos dos botões
            new Color(0, 0, 0),
            new Color(255, 255, 255, 40),
            new Color(255, 255, 255, 22),

            // Superfícies decoradas
            new Color(0x2A, 0x1C, 0x2B, 225),   // selo de status translúcido
            new Color(0x2A, 0x1C, 0x2B),
            new Color(0x24, 0x18, 0x26),
            new Color(0x1E, 0x14, 0x20, 175),   // véu escuro atrás do título
            new Color(0x1E, 0x14, 0x20, 190),   // halo escuro do título
            new Color(0xE8, 0xA3, 0xBF, 90),
            new Color(120, 30, 70, 110),

            new Color(0xF2, 0xA7, 0xC3),
            true
    );

    PaletaSakura {
        coresParticipantes = List.copyOf(coresParticipantes);
    }

    /** Cor do nome de um participante, estável por nome. */
    Color corParticipante(String nome) {
        return coresParticipantes.get(
                FormatoLinhaChat.indiceCor(nome, coresParticipantes.size()));
    }
}
