package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Painel de pesquisa e fixadas, balões com fixação e destaque, nos oito estilos. */
class InterfacePesquisaFixadasTest {

    @AfterEach
    void voltarAoPadrao() throws Exception {
        SwingUtilities.invokeAndWait(() -> GerenciadorTema.definir(Aparencia.PADRAO));
    }

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    private static ControladorConversas controlador() {
        return new ControladorConversas(new ControladorConversas.Visao() {
            @Override public void reiniciarConversa(long id, String u) { }
            @Override public void exibirLinha(long id, String l) { }
            @Override public boolean historicoConcluido(long id) { return true; }
            @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { }
            @Override public void mensagemEnviada() { }
            @Override public void usuarioIdentificado(String a) { }
            @Override public void autenticacaoRecusada(String m) { }
        }, new NotificacoesSonoras(new ReprodutorSom() {
            @Override public void tocar(SomSakura s) { }
            @Override public void fechar() { }
        }, System::currentTimeMillis), Runnable::run, ConexaoServidor::new);
    }

    private static ProtocoloJson.ResultadoJson resultado(long id, String remetente, String texto, int inicio,
                                                         int fim) {
        return new ProtocoloJson.ResultadoJson(new ProtocoloJson.MensagemJson(id, 1, 2L, remetente, texto,
                "2026-11-13T10:15", "MENSAGEM"), texto, inicio, fim);
    }

    /** Ações de fixação simuladas. */
    private static final class Fixacoes implements BalaoMensagem.AcoesFixacao {
        boolean pode = true;
        final Set<Long> fixadas = new HashSet<>();
        final List<Long> alternadas = new ArrayList<>();

        @Override public boolean podeFixar() { return pode; }
        @Override public boolean fixada(long id) { return fixadas.contains(id); }
        @Override public void alternar(long id) { alternadas.add(id); }
    }

    private static List<String> rotulos(JPopupMenu menu) {
        List<String> rotulos = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item) {
                rotulos.add(item.getText());
            }
        }
        return rotulos;
    }

    @Test
    void menuDoBalaoOfereceFixarSoQuandoPermitidoEAlternaParaDesafixar() throws Exception {
        naEdt(() -> {
            Fixacoes acoes = new Fixacoes();
            FormatoLinhaChat.Linha linha = FormatoLinhaChat.analisar("[13/11 10:15] Bia: Regras do jardim");
            BalaoMensagem balao = new BalaoMensagem(linha, false, 42L, acoes);

            assertEquals(List.of(MenuMensagem.ROTULO_COPIAR, MenuMensagem.ROTULO_FIXAR), rotulos(balao.menuDeContexto()));
            ((JMenuItem) balao.menuDeContexto().getComponent(1)).doClick();
            assertEquals(List.of(42L), acoes.alternadas);

            acoes.fixadas.add(42L);
            balao.atualizarFixacao();
            assertTrue(balao.fixada(), "Indicador de fixada");
            assertEquals(MenuMensagem.ROTULO_DESAFIXAR, rotulos(balao.menuDeContexto()).get(1));

            acoes.pode = false;
            assertEquals(List.of(MenuMensagem.ROTULO_COPIAR), rotulos(balao.menuDeContexto()),
                    "Sem permissão (membro de grupo, Geral): só copiar");

            BalaoMensagem semId = new BalaoMensagem(linha, false);
            assertNull(semId.rotuloFixar(), "Protocolo de texto: sem fixação");
        });
    }

    @Test
    void painelDaConversaGuardaOsIdsEDestacaAMensagem() throws Exception {
        naEdt(() -> {
            Fixacoes acoes = new Fixacoes();
            acoes.fixadas.add(8L);
            PainelConversa painel = new PainelConversa();
            painel.definirAcoesFixacao(acoes);
            painel.definirUsuarioAtual("Ana");
            painel.carregar(List.of("SISTEMA: ===== HISTÓRICO RECENTE =====", "[13/11 10:00] Bia: primeira",
                    "[13/11 10:01] Ana: segunda", "SISTEMA: ===== FIM DO HISTÓRICO ====="),
                    java.util.Arrays.asList(null, 7L, 8L, null));
            JScrollPane rolagem = new JScrollPane(painel);
            rolagem.setSize(400, 200);
            rolagem.validate();

            assertFalse(painel.balao(7L).fixada());
            assertTrue(painel.balao(8L).fixada());
            assertTrue(painel.destacar(7L));
            assertTrue(painel.balao(7L).destacada());
            assertEquals("Mensagem destacada",
                    painel.balao(7L).getAccessibleContext().getAccessibleDescription());
            assertTrue(painel.balao(8L).getAccessibleContext().getAccessibleName().endsWith("(fixada)"));
            assertFalse(painel.destacar(999L), "Mensagem não exibida");

            acoes.fixadas.clear();
            painel.atualizarFixadas();
            assertFalse(painel.balao(8L).fixada());

            painel.adicionarLinha("[13/11 10:02] Bia: ao vivo", 9L);
            assertNotNull(painel.balao(9L));
        });
    }

    @Test
    void resultadosSemDuplicatasComEstadoEPaginacao() throws Exception {
        naEdt(() -> {
            PainelBuscaFixadas painel = new PainelBuscaFixadas(controlador(), () -> { });
            painel.mostrarResultados(new ControladorConversas.PaginaPesquisa(1, List.of(), false));
            assertEquals(PainelBuscaFixadas.NENHUM_RESULTADO, painel.estadoPesquisa());
            assertFalse(painel.botaoMais().isVisible());

            painel.mostrarResultados(new ControladorConversas.PaginaPesquisa(1, List.of(
                    resultado(30, "Bia", "reunião às 18h", 0, 7), resultado(20, "Ana", "nova reunião", 5, 12)), true));
            assertTrue(painel.botaoMais().isVisible());
            assertTrue(painel.estadoPesquisa().startsWith("2 mensagens encontradas"));

            // Página seguinte repetindo um item: sem duplicar.
            painel.mostrarResultados(new ControladorConversas.PaginaPesquisa(1, List.of(
                    resultado(20, "Ana", "nova reunião", 5, 12), resultado(10, "Bia", "reunião antiga", 0, 7)),
                    false));
            assertEquals(List.of("reunião às 18h", "nova reunião", "reunião antiga"), painel.textosResultados());
            assertFalse(painel.botaoMais().isVisible());
        });
    }

    @Test
    void validacoesLocaisNaoAbremJanelasEDatasSaoEstritas() throws Exception {
        naEdt(() -> {
            PainelBuscaFixadas painel = new PainelBuscaFixadas(controlador(), () -> { });
            painel.campoTermo().setText("a");
            painel.pesquisar();
            assertEquals("Digite pelo menos 2 caracteres.", painel.estadoPesquisa());

            painel.campoTermo().setText("flores");
            painel.campoDe().setText("31/02/2026");
            painel.pesquisar();
            assertEquals("Use datas no formato dd/mm/aaaa.", painel.estadoPesquisa());

            painel.campoDe().setText("");
            painel.pesquisar();   // sem conexão: nada é enviado
            assertEquals("Conecte-se com uma conta para pesquisar.", painel.estadoPesquisa());

            assertEquals(java.time.LocalDate.of(2026, 11, 13), PainelBuscaFixadas.data("13/11/2026"));
            assertNull(PainelBuscaFixadas.data("  "));
        });
    }

    @Test
    void textoDoServidorEEscapadoNoHtml() {
        assertEquals("&lt;b&gt;oi&lt;/b&gt; &amp; &quot;tchau&quot;",
                PainelBuscaFixadas.escapar("<b>oi</b> & \"tchau\""));
    }

    @Test
    void painelEItensSeguemAsOitoPaletas() throws Exception {
        naEdt(() -> {
            PainelBuscaFixadas painel = new PainelBuscaFixadas(controlador(), () -> { });
            painel.mostrarResultados(new ControladorConversas.PaginaPesquisa(1,
                    List.of(resultado(5, "Bia", "<script>alert(1)</script> flores", 27, 33)), false));
            JList<ProtocoloJson.ResultadoJson> lista = painel.listaResultados();
            lista.setSize(280, 400);

            for (Aparencia.Paleta flor : Aparencia.Paleta.values()) {
                for (Aparencia.Modo modo : Aparencia.Modo.values()) {
                    GerenciadorTema.definir(new Aparencia(flor, modo, Aparencia.Fundo.FLORAL));
                    painel.aplicarTema();
                    PaletaSakura c = TemaSakura.cores();
                    // O renderizador reutiliza o componente: lê cada estado logo após pedir.
                    Component normal = lista.getCellRenderer().getListCellRendererComponent(
                            lista, lista.getModel().getElementAt(0), 0, false, false);
                    assertEquals(c.cartao(), normal.getBackground(), flor + "/" + modo);
                    String html = ((javax.swing.JLabel) normal).getText();
                    Component escolhido = lista.getCellRenderer().getListCellRendererComponent(
                            lista, lista.getModel().getElementAt(0), 0, true, false);
                    assertEquals(c.rosaPetala(), escolhido.getBackground(), flor + "/" + modo);
                    assertFalse(html.contains("<script>"), "Conteúdo escapado");
                    assertTrue(html.contains(String.format("#%02x%02x%02x", c.texto().getRed(),
                            c.texto().getGreen(), c.texto().getBlue())), "Texto na cor da paleta");
                    assertTrue(TemaSakura.contraste(c.texto(), c.cartao()) >= 4.5);
                    assertTrue(TemaSakura.contraste(c.rosaEscuro(), c.cartao()) >= 3.0,
                            "Termo destacado legível");
                }
            }
        });
    }

    @Test
    void alfineteELupaSaoDesenhadosEmQualquerPaleta() throws Exception {
        naEdt(() -> {
            for (Aparencia.Modo modo : Aparencia.Modo.values()) {
                GerenciadorTema.definir(Aparencia.PADRAO.comModo(modo));
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(20, 20,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = img.createGraphics();
                IconesDesenhados.lupa(g, 2, 2, 16, TemaSakura.cores().rosaEscuro());
                IconesDesenhados.alfinete(g, 2, 2, 16, TemaSakura.cores().rosaEscuro());
                g.dispose();
                int pintados = 0;
                for (int y = 0; y < 20; y++) {
                    for (int x = 0; x < 20; x++) {
                        pintados += (img.getRGB(x, y) >>> 24) > 0 ? 1 : 0;
                    }
                }
                assertTrue(pintados > 30);
            }
        });
    }
}
