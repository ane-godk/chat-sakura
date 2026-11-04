package br.unicesumar.chat.client;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Insets;

/**
 * Área de conversa somente leitura, com estilos por tipo de linha.
 *
 * O texto inserido é exatamente a linha recebida seguida de "\n",
 * como no {@code JTextArea} anterior; os estilos alteram apenas a
 * aparência. Deve ser usada somente na Event Dispatch Thread.
 */
final class AreaConversa extends JTextPane {

    private static final long serialVersionUID = 1L;

    private String usuarioAtual = "";

    AreaConversa() {
        setEditable(false);
        setFont(TemaSakura.FONTE_TEXTO);
        setBackground(TemaSakura.CARTAO);
        setForeground(TemaSakura.TEXTO);
        setSelectionColor(TemaSakura.ROSA_PETALA);
        setSelectedTextColor(TemaSakura.TEXTO);
        setMargin(new Insets(12, 14, 12, 14));

        // A rolagem automática é controlada por ClienteChat.
        if (getCaret() instanceof DefaultCaret cursor) {
            cursor.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        }
    }

    /** Nome do usuário local, usado para destacar as próprias mensagens. */
    void definirUsuarioAtual(String nome) {
        usuarioAtual = nome == null ? "" : nome;
    }

    void limpar() {
        try {
            getStyledDocument().remove(0, getStyledDocument().getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Texto puro da conversa, com quebras "\n" (o {@code getText()} do
     * {@code JTextPane} converteria para o separador do sistema).
     */
    String texto() {
        StyledDocument documento = getStyledDocument();
        try {
            return documento.getText(0, documento.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    void adicionarLinha(String linha) {

        FormatoLinhaChat.Linha analisada = FormatoLinhaChat.analisar(linha);
        StyledDocument documento = getStyledDocument();

        int inicio = documento.getLength();

        switch (analisada.tipo()) {

            case MENSAGEM -> {
                boolean propria = analisada.enviadaPor(usuarioAtual);

                inserir(analisada.horario(), estiloHorario());
                inserir(" ", estiloBase());
                inserir(analisada.remetente(), estiloNome(analisada, propria));
                inserir(": ", estiloHorario());
                inserir(analisada.conteudo(), estiloConteudo(propria));
            }

            case SISTEMA -> inserir(analisada.texto(), estiloSistema());

            case SEPARADOR_HISTORICO -> inserir(analisada.texto(), estiloSeparador());

            default -> inserir(analisada.texto(), estiloBase());
        }

        inserir("\n", estiloBase());

        documento.setParagraphAttributes(
                inicio,
                documento.getLength() - inicio,
                estiloParagrafo(analisada.tipo()),
                true
        );
    }

    private void inserir(String trecho, AttributeSet estilo) {
        StyledDocument documento = getStyledDocument();
        try {
            documento.insertString(documento.getLength(), trecho, estilo);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SimpleAttributeSet estiloBase() {
        SimpleAttributeSet estilo = new SimpleAttributeSet();
        StyleConstants.setFontFamily(estilo, TemaSakura.FONTE_TEXTO.getFamily());
        StyleConstants.setFontSize(estilo, TemaSakura.FONTE_TEXTO.getSize());
        StyleConstants.setForeground(estilo, TemaSakura.TEXTO);
        return estilo;
    }

    private static SimpleAttributeSet estiloHorario() {
        SimpleAttributeSet estilo = estiloBase();
        StyleConstants.setFontSize(estilo, TemaSakura.FONTE_PEQUENA.getSize());
        StyleConstants.setForeground(estilo, TemaSakura.TEXTO_SUAVE);
        return estilo;
    }

    private static SimpleAttributeSet estiloNome(
            FormatoLinhaChat.Linha linha,
            boolean propria
    ) {
        Color[] cores = TemaSakura.CORES_PARTICIPANTES;

        Color cor = propria
                ? TemaSakura.ROSA_ESCURO
                : cores[FormatoLinhaChat.indiceCor(linha.remetente(), cores.length)];

        SimpleAttributeSet estilo = estiloBase();
        StyleConstants.setBold(estilo, true);
        StyleConstants.setForeground(estilo, cor);
        return estilo;
    }

    private static SimpleAttributeSet estiloConteudo(boolean propria) {
        SimpleAttributeSet estilo = estiloBase();
        if (propria) {
            StyleConstants.setBackground(estilo, TemaSakura.ROSA_HOVER_CLARO);
        }
        return estilo;
    }

    private static SimpleAttributeSet estiloSistema() {
        SimpleAttributeSet estilo = estiloBase();
        StyleConstants.setItalic(estilo, true);
        StyleConstants.setFontSize(estilo, 13);
        StyleConstants.setForeground(estilo, TemaSakura.TEXTO_SUAVE);
        return estilo;
    }

    private static SimpleAttributeSet estiloSeparador() {
        SimpleAttributeSet estilo = estiloBase();
        StyleConstants.setBold(estilo, true);
        StyleConstants.setFontSize(estilo, TemaSakura.FONTE_PEQUENA.getSize());
        StyleConstants.setForeground(estilo, TemaSakura.ROSA_ESCURO);
        return estilo;
    }

    private static SimpleAttributeSet estiloParagrafo(FormatoLinhaChat.Tipo tipo) {

        SimpleAttributeSet paragrafo = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragrafo, 0.1f);

        if (tipo == FormatoLinhaChat.Tipo.SEPARADOR_HISTORICO) {
            StyleConstants.setAlignment(paragrafo, StyleConstants.ALIGN_CENTER);
            StyleConstants.setSpaceAbove(paragrafo, 6f);
            StyleConstants.setSpaceBelow(paragrafo, 6f);
        } else {
            StyleConstants.setAlignment(paragrafo, StyleConstants.ALIGN_LEFT);
            StyleConstants.setSpaceAbove(paragrafo, 3f);
        }

        return paragrafo;
    }
}
