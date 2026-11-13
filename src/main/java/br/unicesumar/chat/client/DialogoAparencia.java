package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tela de aparência: paleta (quatro flores), modo (claro ou escuro) e fundo
 * da conversa, com prévia ao vivo. Nada muda na janela até "Aplicar";
 * "Restaurar padrão" só volta as opções a Sakura, claro e floral (ainda é
 * preciso aplicar); "Cancelar" descarta tudo.
 */
final class DialogoAparencia {

    private final Map<Aparencia.Paleta, Opcao> opcoesPaleta = new EnumMap<>(Aparencia.Paleta.class);
    private final Map<Aparencia.Modo, Opcao> opcoesModo = new EnumMap<>(Aparencia.Modo.class);
    private final Map<Aparencia.Fundo, Opcao> opcoesFundo = new EnumMap<>(Aparencia.Fundo.class);
    private final PreviaAparencia previa;
    private final BotaoSakura botaoRestaurar = new BotaoSakura("Restaurar padrão", BotaoSakura.Estilo.SECUNDARIO);
    private final JPanel conteudo;

    private Aparencia escolhida;

    DialogoAparencia(Aparencia atual) {
        this.escolhida = atual;
        this.previa = new PreviaAparencia(atual);
        this.conteudo = montar();
        selecionar(atual);
    }

    /** Exibe o diálogo (modal, na EDT); vazio se cancelado. */
    static Optional<Aparencia> exibir(Component pai, Aparencia atual) {
        DialogoAparencia dialogo = new DialogoAparencia(atual);
        AtomicReference<Aparencia> resultado = new AtomicReference<>();
        DialogoNovaConversa.exibir(pai, "Aparência", "Escolha a paleta, o modo e o fundo da conversa:",
                dialogo.conteudo, null, "Aplicar", () -> resultado.set(dialogo.escolhida()));
        return Optional.ofNullable(resultado.get());
    }

    /** Descrição curta para leitores de tela e avisos. */
    static String descrever(Aparencia a) {
        return a.paleta().nome() + ", " + (a.escuro() ? "escuro" : "claro") + ", fundo " + switch (a.fundo()) {
            case FLORAL -> "floral";
            case LISO -> "liso";
            case NEUTRO -> "neutro";
        };
    }

    Aparencia escolhida() {
        return escolhida;
    }

    PreviaAparencia previa() {
        return previa;
    }

    JComponent conteudo() {
        return conteudo;
    }

    /** Marca as opções e atualiza a prévia (sem aplicar). */
    void selecionar(Aparencia aparencia) {
        escolhida = aparencia;
        opcoesPaleta.forEach((p, o) -> o.setSelected(p == aparencia.paleta()));
        opcoesModo.forEach((m, o) -> o.setSelected(m == aparencia.modo()));
        opcoesFundo.forEach((f, o) -> o.setSelected(f == aparencia.fundo()));
        previa.mostrar(aparencia);
    }

    void restaurarPadrao() {
        selecionar(Aparencia.PADRAO);
    }

    Opcao opcao(Aparencia.Paleta paleta) {
        return opcoesPaleta.get(paleta);
    }

    Opcao opcao(Aparencia.Modo modo) {
        return opcoesModo.get(modo);
    }

    Opcao opcao(Aparencia.Fundo fundo) {
        return opcoesFundo.get(fundo);
    }

    BotaoSakura botaoRestaurar() {
        return botaoRestaurar;
    }

    // ------------------------------------------------------------------

    private JPanel montar() {
        JPanel paletas = grade(2, 2);
        ButtonGroup grupoPaleta = new ButtonGroup();
        for (Aparencia.Paleta p : Aparencia.Paleta.values()) {
            PaletasFlorais.Referencia ref = PaletasFlorais.REFERENCIAS.get(p);
            Opcao o = new Opcao(p.nome(), p.flor(), new Color[] {ref.destaque(), ref.suave(), ref.fundo()});
            o.addActionListener(e -> selecionar(escolhida.comPaleta(p)));
            grupoPaleta.add(o);
            opcoesPaleta.put(p, o);
            paletas.add(o);
        }

        JPanel modos = grade(1, 2);
        ButtonGroup grupoModo = new ButtonGroup();
        for (Aparencia.Modo m : Aparencia.Modo.values()) {
            Opcao o = new Opcao(m == Aparencia.Modo.CLARO ? "Claro" : "Escuro",
                    m == Aparencia.Modo.CLARO ? "tons suaves" : "para pouca luz", null);
            o.addActionListener(e -> selecionar(escolhida.comModo(m)));
            grupoModo.add(o);
            opcoesModo.put(m, o);
            modos.add(o);
        }

        JPanel fundos = grade(1, 3);
        ButtonGroup grupoFundo = new ButtonGroup();
        for (Aparencia.Fundo f : Aparencia.Fundo.values()) {
            Opcao o = switch (f) {
                case FLORAL -> new Opcao("Floral", "flores nos cantos", null);
                case LISO -> new Opcao("Liso", "pastel da paleta", null);
                case NEUTRO -> new Opcao("Neutro", "sem decoração", null);
            };
            o.addActionListener(e -> selecionar(escolhida.comFundo(f)));
            grupoFundo.add(o);
            opcoesFundo.put(f, o);
            fundos.add(o);
        }

        JPanel opcoes = new JPanel();
        opcoes.setOpaque(false);
        opcoes.setLayout(new javax.swing.BoxLayout(opcoes, javax.swing.BoxLayout.Y_AXIS));
        opcoes.add(secao("Paleta", paletas));
        opcoes.add(secao("Modo", modos));
        opcoes.add(secao("Fundo da conversa", fundos));

        botaoRestaurar.setToolTipText("Voltar a Sakura, claro e fundo floral (use Aplicar para confirmar)");
        botaoRestaurar.addActionListener(e -> restaurarPadrao());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rodape.setOpaque(false);
        rodape.add(botaoRestaurar);

        JLabel tituloPrevia = rotulo("Prévia");
        JPanel ladoPrevia = new JPanel(new BorderLayout(0, 6));
        ladoPrevia.setOpaque(false);
        ladoPrevia.add(tituloPrevia, BorderLayout.NORTH);
        ladoPrevia.add(previa, BorderLayout.CENTER);

        JPanel painel = new JPanel(new BorderLayout(TemaSakura.ESPACO_G, TemaSakura.ESPACO_M));
        painel.setOpaque(false);
        painel.add(opcoes, BorderLayout.CENTER);
        painel.add(ladoPrevia, BorderLayout.EAST);
        painel.add(rodape, BorderLayout.SOUTH);
        return painel;
    }

    private static JPanel grade(int linhas, int colunas) {
        JPanel grade = new JPanel(new GridLayout(linhas, colunas, 6, 6));
        grade.setOpaque(false);
        return grade;
    }

    private static JLabel rotulo(String texto) {
        JLabel rotulo = new JLabel(texto);
        rotulo.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
        rotulo.setForeground(TemaSakura.cores().rosaHover());
        return rotulo;
    }

    private static JPanel secao(String titulo, JPanel opcoes) {
        JPanel secao = new JPanel(new BorderLayout(0, 4));
        secao.setOpaque(false);
        secao.setBorder(BorderFactory.createEmptyBorder(0, 0, TemaSakura.ESPACO_M, 0));
        secao.add(rotulo(titulo), BorderLayout.NORTH);
        secao.add(opcoes, BorderLayout.CENTER);
        secao.setAlignmentX(Component.LEFT_ALIGNMENT);
        return secao;
    }

    /** Opção em cartão (nome, detalhe e, nas paletas, amostras de cor). */
    static final class Opcao extends JToggleButton {

        private static final long serialVersionUID = 1L;

        private final transient Color[] amostras;

        Opcao(String nome, String detalhe, Color[] amostras) {
            this.amostras = amostras;
            setLayout(new BorderLayout(0, 0));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(6, amostras == null ? 10 : 34, 6, 10));
            setPreferredSize(new Dimension(amostras == null ? 112 : 150, 44));
            getAccessibleContext().setAccessibleName(nome + " (" + detalhe + ")");
            setToolTipText(nome + ": " + detalhe);

            JLabel rotuloNome = new JLabel(nome);
            rotuloNome.setFont(TemaSakura.FONTE_PEQUENA_NEGRITO);
            rotuloNome.setForeground(TemaSakura.cores().texto());
            JLabel rotuloDetalhe = new JLabel(detalhe);
            rotuloDetalhe.setFont(TemaSakura.FONTE_PEQUENA);
            rotuloDetalhe.setForeground(TemaSakura.cores().textoSuave());
            add(rotuloNome, BorderLayout.NORTH);
            add(rotuloDetalhe, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            PaletaSakura c = TemaSakura.cores();
            Graphics2D g2 = TemaSakura.suavizar(g);
            try {
                g2.setColor(isSelected() ? c.rosaPetala() : c.cartao());
                g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 14, 14));
                g2.setColor(isSelected() ? c.rosa() : c.rosaBorda());
                g2.setStroke(new java.awt.BasicStroke(isSelected() ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 14, 14));
                if (amostras != null) {
                    // Três círculos sobrepostos com as cores de referência da flor.
                    double r = 9;
                    double cy = getHeight() / 2.0;
                    for (int i = amostras.length - 1; i >= 0; i--) {
                        double cx = 12 + i * 6;
                        Ellipse2D bola = new Ellipse2D.Double(cx - r / 2, cy - r / 2 - (i - 1) * 6, r, r);
                        g2.setColor(amostras[i]);
                        g2.fill(bola);
                        g2.setColor(c.rosaBorda());
                        g2.draw(bola);
                    }
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
