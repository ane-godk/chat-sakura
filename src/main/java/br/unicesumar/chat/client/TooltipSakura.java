package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.border.Border;
import java.awt.Color;

/**
 * Dica (tooltip) com as cores do tema ativo, criada pelo próprio componente
 * em {@code createToolTip()}: não altera o {@code UIManager}.
 */
final class TooltipSakura extends JToolTip {

    private static final long serialVersionUID = 1L;

    TooltipSakura(JComponent dono) {
        setComponent(dono);
        setOpaque(true);
        setFont(TemaSakura.FONTE_PEQUENA);
    }

    @Override
    public Color getBackground() {
        return TemaSakura.cores().cartao();
    }

    @Override
    public Color getForeground() {
        return TemaSakura.cores().texto();
    }

    @Override
    public Border getBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TemaSakura.cores().rosaBorda(), 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)
        );
    }
}
