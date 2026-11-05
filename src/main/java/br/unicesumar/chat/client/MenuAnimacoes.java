package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

/** Menu de contexto da área de conversa para escolher o nível das animações. */
final class MenuAnimacoes {

    static final String TITULO = "Animações";

    private MenuAnimacoes() {
    }

    static JPopupMenu criar() {

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(TemaSakura.cores().cartao());
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TemaSakura.cores().rosaBorda(), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        JLabel titulo = new JLabel(TITULO);
        titulo.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        titulo.setForeground(TemaSakura.cores().rosaHover());
        titulo.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        menu.add(titulo);

        ButtonGroup grupo = new ButtonGroup();

        for (ConfiguracaoAnimacoes.Modo modo : ConfiguracaoAnimacoes.Modo.values()) {

            JRadioButtonMenuItem item = new ItensMenuSakura.ItemRadio(modo.rotulo());
            item.setSelected(ConfiguracaoAnimacoes.modo() == modo);
            item.addActionListener(e -> ConfiguracaoAnimacoes.definirModo(modo));

            grupo.add(item);
            menu.add(item);
        }

        return menu;
    }
}
