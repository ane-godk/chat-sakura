package br.unicesumar.chat.client;

import br.unicesumar.chat.model.CatalogoAvatares;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Galeria de avatares fixos (somente identificadores do catálogo). */
final class DialogoAvatar {

    private DialogoAvatar() {
    }

    static Optional<String> escolher(Component pai, String atual) {

        JPanel grade = new JPanel(new GridLayout(2, 3, TemaSakura.ESPACO_M, TemaSakura.ESPACO_M));
        grade.setOpaque(false);

        ButtonGroup grupo = new ButtonGroup();
        AtomicReference<String> selecionado = new AtomicReference<>(CatalogoAvatares.normalizar(atual));

        for (CatalogoAvatares.Avatar avatar : CatalogoAvatares.AVATARES) {
            Opcao opcao = new Opcao(avatar);
            opcao.setSelected(avatar.id().equals(selecionado.get()));
            opcao.addActionListener(e -> selecionado.set(avatar.id()));
            grupo.add(opcao);
            grade.add(opcao);
        }

        AtomicReference<String> escolha = new AtomicReference<>();

        DialogoNovaConversa.exibir(pai, "Avatar do perfil", "Escolha seu avatar:", grade, null,
                () -> escolha.set(selecionado.get()));

        return Optional.ofNullable(escolha.get());
    }

    /** Botão da galeria: avatar desenhado e nome, com destaque quando selecionado. */
    private static final class Opcao extends JToggleButton {

        private static final long serialVersionUID = 1L;

        private final AvatarSakura.Icone icone;

        Opcao(CatalogoAvatares.Avatar avatar) {
            super();
            setLayout(new BorderLayout(0, 4));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setPreferredSize(new Dimension(112, 104));
            setToolTipText(avatar.nome());
            getAccessibleContext().setAccessibleName("Avatar " + avatar.nome());

            icone = new AvatarSakura.Icone(56, avatar.id());
            JLabel nome = new JLabel(avatar.nome(), SwingConstants.CENTER);
            nome.setFont(TemaSakura.FONTE_PEQUENA);
            nome.setForeground(TemaSakura.cores().texto());

            add(icone, BorderLayout.CENTER);
            add(nome, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = TemaSakura.suavizar(g);
            try {
                g2.setColor(isSelected() ? TemaSakura.cores().rosaPetala() : TemaSakura.cores().cartao());
                g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 16, 16));
                g2.setColor(isSelected() ? TemaSakura.cores().rosa() : TemaSakura.cores().rosaBorda());
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 16, 16));
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
