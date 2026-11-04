package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Menu de contexto dos balões ("Copiar mensagem") e acesso seguro à
 * área de transferência.
 *
 * A cópia passa por {@link Copiador}, o que permite testar sem depender
 * da área de transferência real do sistema.
 */
final class MenuMensagem {

    static final String ROTULO_COPIAR = "Copiar mensagem";

    /** Destino do texto copiado. */
    @FunctionalInterface
    interface Copiador {
        void copiar(String texto) throws Exception;
    }

    /** Forma de exibir o menu (substituível nos testes). */
    @FunctionalInterface
    interface ExibidorMenu {
        void exibir(JPopupMenu menu, Component origem, int x, int y);
    }

    /** Área de transferência do sistema operacional. */
    static final Copiador AREA_DE_TRANSFERENCIA = texto ->
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(texto), null);

    /** Exibe o menu somente se a origem estiver visível na tela. */
    static final ExibidorMenu EXIBIR_NA_TELA = (menu, origem, x, y) -> {
        if (origem.isShowing()) {
            menu.show(origem, x, y);
        }
    };

    static {
        // Destaque do item em rosa-pétala, com texto escuro legível.
        UIManager.put("MenuItem.selectionBackground",
                new ColorUIResource(TemaSakura.ROSA_PETALA));
        UIManager.put("MenuItem.selectionForeground",
                new ColorUIResource(TemaSakura.TEXTO));
    }

    private MenuMensagem() {
    }

    /**
     * Copia o texto sem deixar exceções escaparem: a área de
     * transferência pode estar ocupada por outro programa ou indisponível.
     *
     * @return {@code true} se a cópia foi feita
     */
    static boolean copiarComSeguranca(Copiador copiador, String texto) {
        try {
            copiador.copiar(texto);
            return true;
        } catch (Exception e) {
            System.err.println(
                    "Chat Sakura: não foi possível copiar a mensagem: "
                            + e.getMessage()
            );
            return false;
        }
    }

    static JPopupMenu criar(Runnable acaoCopiar) {

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(TemaSakura.CARTAO);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TemaSakura.ROSA_BORDA, 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        JMenuItem copiar = new JMenuItem(ROTULO_COPIAR) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                if (getModel().isArmed()) {
                    Graphics2D g2 = TemaSakura.suavizar(g);
                    try {
                        g2.setColor(TemaSakura.ROSA_PETALA);
                        g2.fill(new RoundRectangle2D.Float(
                                0, 0, getWidth(), getHeight(), 10, 10));
                    } finally {
                        g2.dispose();
                    }
                }
                super.paintComponent(g);
            }
        };

        copiar.setOpaque(false);
        copiar.setFont(TemaSakura.FONTE_STATUS);
        copiar.setForeground(TemaSakura.TEXTO);
        copiar.setMnemonic(KeyEvent.VK_C);
        copiar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 12));
        RecursosSakura.florEmPixels(14)
                .ifPresent(flor -> copiar.setIcon(new ImageIcon(flor)));
        copiar.addActionListener(e -> acaoCopiar.run());

        menu.add(copiar);
        return menu;
    }
}
