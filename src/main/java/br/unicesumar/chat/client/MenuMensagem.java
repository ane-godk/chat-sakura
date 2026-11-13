package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

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

    static final String ROTULO_FIXAR = "Fixar mensagem";
    static final String ROTULO_DESAFIXAR = "Desafixar mensagem";

    static JPopupMenu criar(Runnable acaoCopiar) {
        return criar(acaoCopiar, null, null);
    }

    /**
     * @param rotuloFixar "Fixar mensagem" ou "Desafixar mensagem"; nulo se a
     *                    mensagem não pode ser fixada por este usuário
     */
    static JPopupMenu criar(Runnable acaoCopiar, String rotuloFixar, Runnable acaoFixar) {
        JPopupMenu menu = criarBase(acaoCopiar);
        if (rotuloFixar != null && acaoFixar != null) {
            JMenuItem fixar = new ItensMenuSakura.Item(rotuloFixar);
            fixar.setMnemonic(KeyEvent.VK_F);
            fixar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 12));
            fixar.setIcon(new IconeDesenhado(IconesDesenhados::alfinete, 14));
            fixar.addActionListener(e -> acaoFixar.run());
            menu.add(fixar);
        }
        return menu;
    }

    /** Ícone vetorial na cor de destaque da paleta ativa. */
    static final class IconeDesenhado implements javax.swing.Icon {
        private final BotaoSakura.DesenhoIcone desenho;
        private final int tamanho;

        IconeDesenhado(BotaoSakura.DesenhoIcone desenho, int tamanho) {
            this.desenho = desenho;
            this.tamanho = tamanho;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = TemaSakura.suavizar(g);
            try {
                desenho.desenhar(g2, x, y, tamanho, TemaSakura.cores().rosaEscuro());
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return tamanho;
        }

        @Override
        public int getIconHeight() {
            return tamanho;
        }
    }

    private static JPopupMenu criarBase(Runnable acaoCopiar) {

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(TemaSakura.cores().cartao());
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TemaSakura.cores().rosaBorda(), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        // Item com cores próprias (sem alterar o UIManager).
        JMenuItem copiar = new ItensMenuSakura.Item(ROTULO_COPIAR);
        copiar.setMnemonic(KeyEvent.VK_C);
        copiar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 12));
        RecursosSakura.florEmPixels(14)
                .ifPresent(flor -> copiar.setIcon(new ImageIcon(flor)));
        copiar.addActionListener(e -> acaoCopiar.run());

        menu.add(copiar);
        return menu;
    }
}
