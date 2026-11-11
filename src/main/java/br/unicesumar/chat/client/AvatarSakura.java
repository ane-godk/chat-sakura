package br.unicesumar.chat.client;

import br.unicesumar.chat.model.CatalogoAvatares;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Avatares da galeria desenhados com Java2D a partir do identificador
 * (sem arquivos externos). As cores de cada avatar são fixas e legíveis
 * nos dois temas; o contorno segue a paleta ativa.
 */
final class AvatarSakura {

    /** Identificador visual dos personagens de IA (não escolhível como perfil). */
    static final String IA = "ia";

    private AvatarSakura() {
    }

    static void desenhar(Graphics2D g2, String id, double x, double y, double tamanho) {

        Graphics2D g = (Graphics2D) g2.create();

        try {
            g.translate(x, y);
            double t = tamanho;
            String avatar = IA.equals(id) ? IA : CatalogoAvatares.normalizar(id);

            g.setColor(fundo(avatar));
            g.fill(new Ellipse2D.Double(0, 0, t, t));

            switch (avatar) {
                case "lua" -> lua(g, t);
                case "gato" -> gato(g, t);
                case "estrela" -> estrela(g, t, new Color(0xFFE08A), 5, 0.36, 0.15);
                case "onda" -> onda(g, t);
                case "cha" -> cha(g, t);
                case IA -> estrela(g, t, Color.WHITE, 4, 0.36, 0.09);
                default -> IconeSakura.desenhar(g, t / 2, t / 2, t / 2 - t * 0.1);
            }

            g.setStroke(new BasicStroke((float) Math.max(1, t / 24)));
            g.setColor(TemaSakura.cores().rosaBorda());
            g.draw(new Ellipse2D.Double(0.5, 0.5, t - 1, t - 1));

        } finally {
            g.dispose();
        }
    }

    private static Color fundo(String id) {
        return switch (id) {
            case "lua" -> new Color(0x3B2F6B);
            case "gato" -> new Color(0xF6C9A8);
            case "estrela" -> new Color(0xB28DD8);
            case "onda" -> new Color(0x5FA8D3);
            case "cha" -> new Color(0x8DBF8B);
            case IA -> new Color(0xC2527F);
            default -> new Color(0xFBE1EA);
        };
    }

    private static void lua(Graphics2D g, double t) {
        Area lua = new Area(new Ellipse2D.Double(t * 0.24, t * 0.2, t * 0.56, t * 0.56));
        lua.subtract(new Area(new Ellipse2D.Double(t * 0.38, t * 0.12, t * 0.52, t * 0.52)));
        g.setColor(new Color(0xFFE7A3));
        g.fill(lua);
    }

    private static void gato(Graphics2D g, double t) {
        Color pelo = new Color(0x8A5A44);
        g.setColor(pelo);
        Path2D orelhas = new Path2D.Double();
        orelhas.moveTo(t * 0.24, t * 0.44);
        orelhas.lineTo(t * 0.3, t * 0.18);
        orelhas.lineTo(t * 0.46, t * 0.32);
        orelhas.moveTo(t * 0.76, t * 0.44);
        orelhas.lineTo(t * 0.7, t * 0.18);
        orelhas.lineTo(t * 0.54, t * 0.32);
        g.fill(orelhas);
        g.fill(new Ellipse2D.Double(t * 0.22, t * 0.28, t * 0.56, t * 0.5));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(t * 0.34, t * 0.44, t * 0.1, t * 0.1));
        g.fill(new Ellipse2D.Double(t * 0.56, t * 0.44, t * 0.1, t * 0.1));
        g.setColor(new Color(0xF49AB5));
        g.fill(new Ellipse2D.Double(t * 0.46, t * 0.57, t * 0.08, t * 0.06));
    }

    private static void estrela(Graphics2D g, double t, Color cor, int pontas, double externo, double interno) {
        Path2D estrela = new Path2D.Double();
        for (int i = 0; i < pontas * 2; i++) {
            double raio = t * (i % 2 == 0 ? externo : interno);
            double angulo = -Math.PI / 2 + i * Math.PI / pontas;
            double px = t / 2 + Math.cos(angulo) * raio;
            double py = t / 2 + Math.sin(angulo) * raio;
            if (i == 0) {
                estrela.moveTo(px, py);
            } else {
                estrela.lineTo(px, py);
            }
        }
        estrela.closePath();
        g.setColor(cor);
        g.fill(estrela);
    }

    private static void onda(Graphics2D g, double t) {
        g.setStroke(new BasicStroke((float) (t * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            double y = t * (0.34 + i * 0.15);
            Path2D linha = new Path2D.Double();
            linha.moveTo(t * 0.2, y);
            linha.curveTo(t * 0.35, y - t * 0.1, t * 0.45, y + t * 0.1, t * 0.6, y);
            linha.curveTo(t * 0.7, y - t * 0.07, t * 0.75, y - t * 0.02, t * 0.8, y);
            g.draw(linha);
        }
    }

    private static void cha(Graphics2D g, double t) {
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(t * 0.26, t * 0.42, t * 0.38, t * 0.3, t * 0.12, t * 0.12));
        g.setStroke(new BasicStroke((float) (t * 0.06)));
        g.draw(new Arc2D.Double(t * 0.56, t * 0.47, t * 0.18, t * 0.16, -90, 180, Arc2D.OPEN));
        g.setStroke(new BasicStroke((float) (t * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xF5FBF4));
        for (int i = 0; i < 2; i++) {
            double x = t * (0.36 + i * 0.14);
            Path2D vapor = new Path2D.Double();
            vapor.moveTo(x, t * 0.36);
            vapor.curveTo(x - t * 0.05, t * 0.3, x + t * 0.05, t * 0.26, x, t * 0.2);
            g.draw(vapor);
        }
    }

    /** Componente que mostra um avatar; o identificador pode mudar. */
    static final class Icone extends javax.swing.JPanel {

        private static final long serialVersionUID = 1L;

        private String avatarId;

        Icone(int tamanho, String avatarId) {
            this.avatarId = avatarId;
            setPreferredSize(new Dimension(tamanho, tamanho));
            setOpaque(false);
        }

        String avatarId() {
            return avatarId;
        }

        void definir(String novo) {
            this.avatarId = novo;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = TemaSakura.suavizar(g);
            try {
                int lado = Math.min(getWidth(), getHeight()) - 2;
                desenhar(g2, avatarId, (getWidth() - lado) / 2.0, (getHeight() - lado) / 2.0, lado);
            } finally {
                g2.dispose();
            }
        }
    }
}
