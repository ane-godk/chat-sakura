package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Mostra a memória do personagem (as notas que ele usa sobre conversas
 * antigas) e permite apagá-la, com confirmação. É aberto pelo usuário, por
 * isso é um diálogo; nada aqui é exibido automaticamente.
 */
final class DialogoMemoria {

    static final String SEM_MEMORIA = "Ainda não há memória. Ela é criada aos poucos, a cada 10 mensagens,"
            + " com notas curtas sobre o que você contou nesta conversa.";

    private DialogoMemoria() {
    }

    /** Texto exibido para a memória informada. */
    static String texto(ControladorConversas.MemoriaInfo memoria) {
        return memoria.resumo().isBlank() ? SEM_MEMORIA : memoria.resumo();
    }

    /** @param apagar executado somente se o usuário confirmar a exclusão */
    static void mostrar(Component pai, String personagem, ControladorConversas.MemoriaInfo memoria, Runnable apagar) {

        PaletaSakura cores = TemaSakura.cores();

        JTextArea area = new JTextArea(texto(memoria), 8, 42);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(TemaSakura.FONTE_TEXTO);
        area.setBackground(cores.cartao());
        area.setForeground(cores.texto());
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        area.getAccessibleContext().setAccessibleName("Memória de " + personagem);

        JScrollPane rolagem = new JScrollPane(area);
        rolagem.setPreferredSize(new Dimension(460, 180));

        JLabel explicacao = new JLabel("<html>Notas que " + personagem + " usa para lembrar de conversas antigas."
                + (memoria.atualizadaEm() == null ? "" : " Atualizada em " + memoria.atualizadaEm().replace('T', ' ')
                + ".") + "<br>Apagar a memória não apaga as mensagens desta conversa.</html>");
        explicacao.setFont(TemaSakura.FONTE_PEQUENA);
        explicacao.setForeground(cores.textoSuave());

        JPanel painel = new JPanel(new BorderLayout(0, 8));
        painel.setOpaque(false);
        painel.add(explicacao, BorderLayout.NORTH);
        painel.add(rolagem, BorderLayout.CENTER);

        rolagem.setBorder(BorderFactory.createLineBorder(cores.rosaBorda()));
        rolagem.getVerticalScrollBar().setUI(new BarraRolagemSakura());

        // Botões e fundo da paleta ativa (o diálogo segue a aparência escolhida).
        BotaoSakura botaoApagar = new BotaoSakura("Apagar memória", BotaoSakura.Estilo.SECUNDARIO);
        BotaoSakura botaoFechar = new BotaoSakura("Fechar", BotaoSakura.Estilo.PRINCIPAL);
        JOptionPane opcoes = new JOptionPane(painel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[] {botaoApagar, botaoFechar}, botaoFechar);
        botaoApagar.addActionListener(e -> opcoes.setValue(0));
        botaoFechar.addActionListener(e -> opcoes.setValue(1));
        pintarFundo(opcoes, cores.fundo());

        JDialog dialogo = opcoes.createDialog(pai, "Memória de " + personagem);
        dialogo.getContentPane().setBackground(cores.fundo());
        try {
            dialogo.setVisible(true);
        } finally {
            dialogo.dispose();
        }

        if (Integer.valueOf(0).equals(opcoes.getValue())) {
            int confirmar = JOptionPane.showConfirmDialog(pai,
                    "Apagar a memória de " + personagem + "?\n"
                            + "As mensagens continuam na conversa, mas " + personagem
                            + " deixa de considerá-las nas próximas respostas.",
                    "Apagar memória", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmar == JOptionPane.YES_OPTION) {
                apagar.run();
            }
        }
    }

    private static void pintarFundo(java.awt.Container container, java.awt.Color cor) {
        container.setBackground(cor);
        for (Component filho : container.getComponents()) {
            if (filho instanceof JPanel subpainel) {
                pintarFundo(subpainel, cor);
            }
        }
    }
}
