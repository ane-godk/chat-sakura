package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.protocolo.ProtocoloJson.UsuarioJson;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diálogos temáticos para iniciar uma conversa privada ou criar um grupo,
 * a partir da lista de usuários cadastrados recebida do servidor. Só
 * montam a escolha (na EDT); o servidor valida tudo novamente.
 */
final class DialogoNovaConversa {

    /** Escolha de um grupo: nome e participantes convidados. */
    record Grupo(String nome, List<Long> participantes) {
    }

    private DialogoNovaConversa() {
    }

    static Optional<Long> escolherContato(Component pai, List<UsuarioJson> usuarios) {

        if (usuarios.isEmpty()) {
            JOptionPane.showMessageDialog(pai, "Ainda não há outras contas cadastradas.",
                    "Chat Sakura", JOptionPane.INFORMATION_MESSAGE);
            return Optional.empty();
        }

        JList<UsuarioJson> lista = lista(usuarios, ListSelectionModel.SINGLE_SELECTION);
        lista.setSelectedIndex(0);

        AtomicReference<Long> escolha = new AtomicReference<>();

        exibir(pai, "Nova conversa privada", "Com quem você quer conversar?", rolagem(lista), null, () -> {
            UsuarioJson usuario = lista.getSelectedValue();
            if (usuario == null) {
                throw new IllegalArgumentException("Escolha uma pessoa.");
            }
            escolha.set(usuario.id());
        });

        return Optional.ofNullable(escolha.get());
    }

    static Optional<Grupo> criarGrupo(Component pai, List<UsuarioJson> usuarios) {

        if (usuarios.isEmpty()) {
            JOptionPane.showMessageDialog(pai, "Cadastre outras contas para criar um grupo.",
                    "Chat Sakura", JOptionPane.INFORMATION_MESSAGE);
            return Optional.empty();
        }

        CampoTextoSakura campoNome = new CampoTextoSakura(18, "Ex.: Turma de Java", null);
        JList<UsuarioJson> lista = lista(usuarios, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel conteudo = new JPanel(new BorderLayout(0, TemaSakura.ESPACO_P));
        conteudo.setOpaque(false);
        conteudo.add(campoNome, BorderLayout.NORTH);
        conteudo.add(rolagem(lista), BorderLayout.CENTER);

        AtomicReference<Grupo> escolha = new AtomicReference<>();

        exibir(pai, "Novo grupo", "Nome do grupo e participantes (Ctrl ou Shift para vários):",
                conteudo, campoNome, () -> {
                    String nome = Conversa.normalizarNomeGrupo(campoNome.getText());
                    List<Long> ids = lista.getSelectedValuesList().stream().map(UsuarioJson::id).toList();
                    if (ids.isEmpty()) {
                        throw new IllegalArgumentException("Escolha ao menos um participante.");
                    }
                    escolha.set(new Grupo(nome, ids));
                });

        return Optional.ofNullable(escolha.get());
    }

    // ------------------------------------------------------------------

    private static JList<UsuarioJson> lista(List<UsuarioJson> usuarios, int modoSelecao) {

        JList<UsuarioJson> lista = new JList<>(usuarios.toArray(new UsuarioJson[0]));
        lista.setSelectionMode(modoSelecao);
        lista.setVisibleRowCount(Math.min(8, usuarios.size()));
        lista.setFont(TemaSakura.FONTE_TEXTO);
        lista.setBackground(TemaSakura.cores().cartao());
        lista.setForeground(TemaSakura.cores().texto());
        lista.setSelectionBackground(TemaSakura.cores().rosaPetala());
        lista.setSelectionForeground(TemaSakura.cores().texto());
        lista.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object valor, int i,
                                                          boolean selecionado, boolean foco) {
                UsuarioJson usuario = (UsuarioJson) valor;
                super.getListCellRendererComponent(l, (usuario.online() ? "● " : "○ ") + usuario.apelido()
                        + (usuario.online() ? "  (online)" : ""), i, selecionado, foco);
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
        return lista;
    }

    private static JScrollPane rolagem(JList<?> lista) {
        JScrollPane rolagem = new JScrollPane(lista);
        rolagem.setBorder(BorderFactory.createLineBorder(TemaSakura.cores().rosaBorda()));
        rolagem.setPreferredSize(new Dimension(280, 220));
        rolagem.getVerticalScrollBar().setUI(new BarraRolagemSakura());
        return rolagem;
    }

    static void exibir(Component pai, String tituloJanela, String orientacao,
                               JComponent conteudo, JComponent foco, Runnable confirmar) {

        JLabel titulo = new JLabel(orientacao);
        titulo.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        titulo.setForeground(TemaSakura.cores().texto());

        JPanel formulario = new JPanel(new BorderLayout(0, TemaSakura.ESPACO_M));
        formulario.setOpaque(false);
        JPanel topo = new JPanel(new BorderLayout(TemaSakura.ESPACO_M, 0));
        topo.setOpaque(false);
        topo.add(new IconeSakura(28), BorderLayout.WEST);
        topo.add(titulo, BorderLayout.CENTER);
        formulario.add(topo, BorderLayout.NORTH);
        formulario.add(conteudo, BorderLayout.CENTER);

        BotaoSakura botaoOk = new BotaoSakura("Confirmar", BotaoSakura.Estilo.PRINCIPAL);
        BotaoSakura botaoCancelar = new BotaoSakura("Cancelar", BotaoSakura.Estilo.SECUNDARIO);

        JOptionPane painel = new JOptionPane(formulario, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION, null, new Object[] {botaoOk, botaoCancelar}, botaoOk);

        AtomicReference<JDialog> dialogo = new AtomicReference<>();

        botaoOk.addActionListener(e -> {
            try {
                confirmar.run();
                painel.setValue(JOptionPane.OK_OPTION);
            } catch (IllegalArgumentException erro) {
                JOptionPane.showMessageDialog(dialogo.get(), erro.getMessage(), "Chat Sakura",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        botaoCancelar.addActionListener(e -> painel.setValue(JOptionPane.CANCEL_OPTION));

        aplicarFundo(painel, TemaSakura.cores().fundo());

        JDialog janela = painel.createDialog(pai, tituloJanela);
        dialogo.set(janela);
        janela.getContentPane().setBackground(TemaSakura.cores().fundo());
        janela.getRootPane().setDefaultButton((JButton) painel.getOptions()[0]);
        if (foco != null) {
            janela.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    foco.requestFocusInWindow();
                }
            });
        }

        try {
            janela.setVisible(true);
        } finally {
            janela.dispose();
        }
    }

    private static void aplicarFundo(Container container, Color cor) {
        container.setBackground(cor);
        for (Component filho : container.getComponents()) {
            if (filho instanceof JPanel subpainel) {
                aplicarFundo(subpainel, cor);
            }
        }
    }
}
