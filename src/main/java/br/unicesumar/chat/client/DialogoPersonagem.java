package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Personagem;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Criação de personagem de IA: nome, personalidade e contexto. Valida os
 * limites antes de enviar; o servidor valida de novo.
 */
final class DialogoPersonagem {

    /** Dados do personagem a criar. */
    record Dados(String nome, String personalidade, String contexto) {
    }

    private DialogoPersonagem() {
    }

    static Optional<Dados> exibir(Component pai) {

        CampoTextoSakura campoNome = new CampoTextoSakura(18, "Ex.: Naruto", null);
        JTextArea campoPersonalidade = area("Ex.: animado, determinado, amigável e motivador.");
        JTextArea campoContexto = area("Ex.: estamos treinando juntos na Vila da Folha.");

        JPanel campos = new JPanel(new GridLayout(0, 1, 0, 4));
        campos.setOpaque(false);
        campos.add(rotulo("Nome do personagem (sem espaços):"));
        campos.add(campoNome);

        JPanel textos = new JPanel(new GridLayout(0, 1, 0, 4));
        textos.setOpaque(false);
        textos.add(bloco("Personalidade (até " + Personagem.PERSONALIDADE_MAXIMA + " caracteres):", campoPersonalidade));
        textos.add(bloco("Contexto inicial (até " + Personagem.CONTEXTO_MAXIMO + " caracteres):", campoContexto));

        JLabel aviso = rotulo("O personagem é interpretado por IA (Qwen) e não é uma pessoa real.");
        aviso.setFont(TemaSakura.FONTE_SISTEMA);

        JPanel conteudo = new JPanel(new BorderLayout(0, TemaSakura.ESPACO_P));
        conteudo.setOpaque(false);
        conteudo.add(campos, BorderLayout.NORTH);
        conteudo.add(textos, BorderLayout.CENTER);
        conteudo.add(aviso, BorderLayout.SOUTH);

        AtomicReference<Dados> escolha = new AtomicReference<>();

        DialogoNovaConversa.exibir(pai, "Sakura IA", "Novo personagem de IA", conteudo, campoNome, () ->
                escolha.set(new Dados(
                        Personagem.normalizarNome(campoNome.getText()),
                        Personagem.normalizarPersonalidade(campoPersonalidade.getText()),
                        Personagem.normalizarContexto(campoContexto.getText()))));

        return Optional.ofNullable(escolha.get());
    }

    private static JTextArea area(String dica) {
        JTextArea area = new JTextArea(4, 28);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(TemaSakura.FONTE_TEXTO);
        area.setBackground(TemaSakura.cores().cartao());
        area.setForeground(TemaSakura.cores().texto());
        area.setCaretColor(TemaSakura.cores().rosaEscuro());
        area.setToolTipText(dica);
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return area;
    }

    private static JPanel bloco(String rotulo, JTextArea area) {
        JScrollPane rolagem = new JScrollPane(area);
        rolagem.setBorder(BorderFactory.createLineBorder(TemaSakura.cores().rosaBorda()));
        rolagem.setPreferredSize(new Dimension(340, 90));
        rolagem.getVerticalScrollBar().setUI(new BarraRolagemSakura());

        JPanel bloco = new JPanel(new BorderLayout(0, 4));
        bloco.setOpaque(false);
        bloco.add(rotulo(rotulo), BorderLayout.NORTH);
        bloco.add(rolagem, BorderLayout.CENTER);
        return bloco;
    }

    private static JLabel rotulo(String texto) {
        JLabel rotulo = new JLabel(texto);
        rotulo.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        rotulo.setForeground(TemaSakura.cores().texto());
        return rotulo;
    }
}
