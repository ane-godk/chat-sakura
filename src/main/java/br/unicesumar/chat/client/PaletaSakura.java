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
        Color dobraAviao
) {

    /** Sakura Claro: as cores originais da interface, sem alterações. */
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
            new Color(120, 30, 70, 110)
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
