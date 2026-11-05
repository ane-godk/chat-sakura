package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

/**
 * Seção "Animações" do menu da conversa (e peças comuns dos menus Sakura).
 * O menu completo da área de conversa é montado por {@link MenuConversa}.
 */
final class MenuAnimacoes {

    static final String TITULO = "Animações";

    private MenuAnimacoes() {
    }

    /** Menu só com a seção de animações. */
    static JPopupMenu criar() {
        JPopupMenu menu = novoMenu();
        adicionarOpcoes(menu);
        return menu;
    }

    /** Menu vazio com fundo e borda do tema ativo. */
    static JPopupMenu novoMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(TemaSakura.cores().cartao());
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TemaSakura.cores().rosaBorda(), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        return menu;
    }

    /** Rótulo de seção no estilo Sakura. */
    static JLabel titulo(String texto) {
        JLabel titulo = new JLabel(texto);
        titulo.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        titulo.setForeground(TemaSakura.cores().rosaHover());
        titulo.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return titulo;
    }

    /** Título "Animações" e as três opções, com a atual selecionada. */
    static void adicionarOpcoes(JPopupMenu menu) {

        menu.add(titulo(TITULO));

        ButtonGroup grupo = new ButtonGroup();

        for (ConfiguracaoAnimacoes.Modo modo : ConfiguracaoAnimacoes.Modo.values()) {

            JRadioButtonMenuItem item = new ItensMenuSakura.ItemRadio(modo.rotulo());
            item.setSelected(ConfiguracaoAnimacoes.modo() == modo);
            item.addActionListener(e -> ConfiguracaoAnimacoes.definirModo(modo));

            grupo.add(item);
            menu.add(item);
        }
    }
}
