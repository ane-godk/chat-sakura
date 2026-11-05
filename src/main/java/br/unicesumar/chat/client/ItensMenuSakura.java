package br.unicesumar.chat.client;

import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Itens de menu com a identidade Sakura, sem alterar o {@code UIManager}.
 *
 * Cada item usa a sua própria instância de UI, com as cores de seleção
 * definidas a partir da paleta ativa. Assim, nenhum outro menu da
 * aplicação (nem os do Swing) é afetado.
 */
final class ItensMenuSakura {

    private ItensMenuSakura() {
    }

    /** UI de item comum com as cores de seleção da paleta. */
    static final class UiItem extends BasicMenuItemUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            selectionBackground = TemaSakura.cores().rosaPetala();
            selectionForeground = TemaSakura.cores().texto();
        }

        Color fundoSelecao() {
            return selectionBackground;
        }

        Color textoSelecao() {
            return selectionForeground;
        }
    }

    /** UI de item de escolha única com as cores de seleção da paleta. */
    static final class UiItemRadio extends BasicRadioButtonMenuItemUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            selectionBackground = TemaSakura.cores().rosaPetala();
            selectionForeground = TemaSakura.cores().texto();
            // O ícone do Metal tem cores fixas (claras); este segue a paleta.
            checkIcon = new IconeOpcao();
        }

        Color fundoSelecao() {
            return selectionBackground;
        }

        Color textoSelecao() {
            return selectionForeground;
        }
    }

    static final class Item extends JMenuItem {

        private static final long serialVersionUID = 1L;

        Item(String texto) {
            super(texto);
            estilizar(this);
        }

        @Override
        public void updateUI() {
            setUI(new UiItem());
        }

        @Override
        public java.awt.Color getForeground() {
            return TemaSakura.cores().texto();
        }

        @Override
        protected void paintComponent(Graphics g) {
            pintarDestaque(this, g);
            super.paintComponent(g);
        }
    }

    static final class ItemRadio extends JRadioButtonMenuItem {

        private static final long serialVersionUID = 1L;

        ItemRadio(String texto) {
            super(texto);
            estilizar(this);
        }

        @Override
        public void updateUI() {
            setUI(new UiItemRadio());
        }

        @Override
        public java.awt.Color getForeground() {
            return TemaSakura.cores().texto();
        }

        @Override
        protected void paintComponent(Graphics g) {
            pintarDestaque(this, g);
            super.paintComponent(g);
        }
    }

    /** Círculo de opção desenhado com a paleta ativa (ponto quando selecionado). */
    static final class IconeOpcao implements javax.swing.Icon {

        private static final int TAMANHO = 12;

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2 = TemaSakura.suavizar(g);
            try {
                g2.setStroke(new java.awt.BasicStroke(1.4f));
                g2.setColor(TemaSakura.cores().rosa());
                g2.draw(new java.awt.geom.Ellipse2D.Float(x + 0.7f, y + 0.7f, TAMANHO - 1.4f, TAMANHO - 1.4f));
                if (c instanceof javax.swing.AbstractButton botao && botao.isSelected()) {
                    g2.setColor(TemaSakura.cores().rosaEscuro());
                    g2.fill(new java.awt.geom.Ellipse2D.Float(x + 3.2f, y + 3.2f, TAMANHO - 6.4f, TAMANHO - 6.4f));
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return TAMANHO;
        }

        @Override
        public int getIconHeight() {
            return TAMANHO;
        }
    }

    private static void estilizar(JMenuItem item) {
        // Não opaco: o destaque arredondado é pintado pelo próprio item.
        item.setOpaque(false);
        // Borda vazia: a borda padrão do Metal desenha linhas azuis no
        // item destacado, fora da identidade Sakura.
        item.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 8, 5, 12));
        item.setFont(TemaSakura.FONTE_STATUS);
        item.setForeground(TemaSakura.cores().texto());
    }

    /** Fundo arredondado em rosa-pétala quando o item está destacado. */
    private static void pintarDestaque(JMenuItem item, Graphics g) {
        if (!item.getModel().isArmed()) {
            return;
        }
        Graphics2D g2 = TemaSakura.suavizar(g);
        try {
            g2.setColor(TemaSakura.cores().rosaPetala());
            g2.fill(new RoundRectangle2D.Float(0, 0, item.getWidth(), item.getHeight(), 10, 10));
        } finally {
            g2.dispose();
        }
    }
}
