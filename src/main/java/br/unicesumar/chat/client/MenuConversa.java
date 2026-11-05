package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * Menu de clique direito na área vazia da conversa: seção "Tema"
 * (Sakura Claro / Sakura Noturno) e seção "Animações". Criado a cada
 * abertura, sempre no tema ativo. Os balões têm o seu próprio menu.
 */
final class MenuConversa {

    static final String TITULO_TEMA = "Tema";
    static final String ROTULO_CLARO = "Sakura Claro";
    static final String ROTULO_NOTURNO = "Sakura Noturno";

    private MenuConversa() {
    }

    static JPopupMenu criar() {

        JPopupMenu menu = MenuAnimacoes.novoMenu();

        menu.add(MenuAnimacoes.titulo(TITULO_TEMA));

        ButtonGroup temas = new ButtonGroup();
        for (GerenciadorTema.Tema tema : GerenciadorTema.Tema.values()) {
            JRadioButtonMenuItem item = new ItensMenuSakura.ItemRadio(
                    tema == GerenciadorTema.Tema.CLARO ? ROTULO_CLARO : ROTULO_NOTURNO);
            item.setSelected(GerenciadorTema.atual() == tema);
            item.addActionListener(e -> GerenciadorTema.definir(tema));
            temas.add(item);
            menu.add(item);
        }

        menu.add(new Divisor());
        MenuAnimacoes.adicionarOpcoes(menu);
        return menu;
    }

    /** Linha divisória com a cor da paleta (o JSeparator do Metal é claro). */
    static final class Divisor extends JComponent {

        private static final long serialVersionUID = 1L;

        Divisor() {
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 9);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(TemaSakura.cores().rosaBorda());
            g.fillRect(8, getHeight() / 2, getWidth() - 16, 1);
        }
    }

    /** Usado pelos testes para localizar os rótulos de seção. */
    static boolean ehTitulo(java.awt.Component c, String texto) {
        return c instanceof JLabel rotulo && texto.equals(rotulo.getText());
    }
}
