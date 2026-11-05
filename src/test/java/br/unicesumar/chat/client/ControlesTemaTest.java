package br.unicesumar.chat.client;

import br.unicesumar.chat.client.ConfiguracaoAnimacoes.Modo;
import br.unicesumar.chat.client.GerenciadorTema.Tema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Botão de tema, menu da conversa e preferências com uma janela real
 * (não exibida). Usa sempre um arquivo de preferências temporário.
 * Pulado em ambientes sem interface gráfica.
 */
class ControlesTemaTest {

    @TempDir
    Path pasta;

    private final List<ClienteChat> janelas = new ArrayList<>();

    @BeforeEach
    void exigirInterfaceGrafica() {
        assumeFalse(GraphicsEnvironment.isHeadless());
    }

    @AfterEach
    void limpar() throws Exception {
        naEdt(() -> janelas.forEach(ClienteChat::dispose));
        naEdt(() -> { });   // processa o evento de janela fechada
        naEdt(() -> {
            GerenciadorTema.definir(Tema.CLARO);
            ConfiguracaoAnimacoes.definirModo(Modo.COMPLETAS);
        });
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

    private PreferenciasSakura preferencias() {
        return new PreferenciasSakura(pasta.resolve(".chat-sakura").resolve("preferencias.properties"));
    }

    private ClienteChat abrir(PreferenciasSakura p) {
        ClienteChat janela = new ClienteChat(p);
        janelas.add(janela);
        return janela;
    }

    private static Object campo(ClienteChat janela, String nome) throws Exception {
        Field f = ClienteChat.class.getDeclaredField(nome);
        f.setAccessible(true);
        return f.get(janela);
    }

    private static List<JRadioButtonMenuItem> opcoes(JPopupMenu menu) {
        List<JRadioButtonMenuItem> itens = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof JRadioButtonMenuItem item) {
                itens.add(item);
            }
        }
        return itens;
    }

    @Test
    void botaoDeveAlternarOTemaAtualizandoIconeDicaENomeAcessivel() throws Exception {
        PreferenciasSakura p = preferencias();

        naEdt(() -> {
            try {
                ClienteChat janela = abrir(p);
                BotaoSakura botao = (BotaoSakura) campo(janela, "botaoTema");

                assertEquals(Tema.CLARO, GerenciadorTema.atual());
                assertEquals(ClienteChat.ROTULO_ATIVAR_NOTURNO, botao.getToolTipText());
                assertEquals(ClienteChat.ROTULO_ATIVAR_NOTURNO,
                        botao.getAccessibleContext().getAccessibleName());

                botao.doClick();

                assertEquals(Tema.NOTURNO, GerenciadorTema.atual());
                assertEquals(ClienteChat.ROTULO_VOLTAR_CLARO, botao.getToolTipText());
                assertEquals(ClienteChat.ROTULO_VOLTAR_CLARO,
                        botao.getAccessibleContext().getAccessibleName());
                assertInstanceOf(TooltipSakura.class, botao.createToolTip());

                botao.doClick();
                assertEquals(Tema.CLARO, GerenciadorTema.atual());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(p.aguardarGravacoes(5000));
        assertEquals("claro", p.ler().getProperty("tema"));
    }

    @Test
    void trocaPeloBotaoDevePreservarConversaEDemaisRecursos() throws Exception {
        naEdt(() -> {
            try {
                ClienteChat janela = abrir(preferencias());
                PainelConversa conversa = (PainelConversa) campo(janela, "areaMensagens");
                conversa.definirUsuarioAtual("Ana");
                for (String l : List.of("SISTEMA: ===== FIM DO HISTÓRICO =====",
                        "[04/11 20:40] Bia: oi", "SISTEMA: [04/11 20:41] Caio se conectou",
                        "[04/11 20:42] Ana: olá")) {
                    conversa.adicionarLinha(l);
                }
                Component[] antes = conversa.getComponents();
                String texto = conversa.texto();
                BotaoSakura botao = (BotaoSakura) campo(janela, "botaoTema");

                for (int i = 0; i < 5; i++) {
                    botao.doClick();
                }

                assertEquals(Tema.NOTURNO, GerenciadorTema.atual());
                assertArrayEquals(antes, conversa.getComponents());
                assertEquals(texto, conversa.texto());

                BalaoMensagem balao = (BalaoMensagem) antes[1];
                JMenuItem copiar = (JMenuItem) balao.menuDeContexto().getComponent(0);
                assertEquals(MenuMensagem.ROTULO_COPIAR, copiar.getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void menuDaConversaDeveTrocarOTemaEManterAsAnimacoes() throws Exception {
        PreferenciasSakura p = preferencias();

        naEdt(() -> {
            abrir(p);
            JPopupMenu menu = MenuConversa.criar();
            List<JRadioButtonMenuItem> itens = opcoes(menu);

            assertEquals(5, itens.size(), "2 temas + 3 modos de animação");
            assertEquals(MenuConversa.ROTULO_CLARO, itens.get(0).getText());
            assertEquals(MenuConversa.ROTULO_NOTURNO, itens.get(1).getText());
            assertTrue(itens.get(0).isSelected());
            assertFalse(itens.get(1).isSelected());

            itens.get(1).doClick();
            assertEquals(Tema.NOTURNO, GerenciadorTema.atual());

            JPopupMenu reaberto = MenuConversa.criar();
            assertTrue(opcoes(reaberto).get(1).isSelected(), "Tema ativo indicado.");

            opcoes(reaberto).get(3).doClick();   // Reduzidas
            assertEquals(Modo.REDUZIDAS, ConfiguracaoAnimacoes.modo());

            for (String chave : new String[] {"MenuItem.selectionBackground",
                    "RadioButtonMenuItem.selectionBackground", "ToolTip.background", "Panel.background"}) {
                assertEquals(UIManager.getLookAndFeelDefaults().get(chave), UIManager.get(chave), chave);
            }
        });

        assertTrue(p.aguardarGravacoes(5000));
        assertEquals("noturno", p.ler().getProperty("tema"));
        assertEquals("reduzidas", p.ler().getProperty("animacoes"));
    }

    @Test
    void preferenciasSalvasDevemSerAplicadasAntesDeMontarAInterface() throws Exception {
        PreferenciasSakura p = preferencias();
        Files.createDirectories(p.arquivo().getParent());
        Files.writeString(p.arquivo(), "tema=noturno\nsons=silenciados\nanimacoes=desativadas\n",
                StandardCharsets.UTF_8);
        byte[] antes = Files.readAllBytes(p.arquivo());

        naEdt(() -> {
            try {
                ClienteChat janela = abrir(p);
                NotificacoesSonoras sons = (NotificacoesSonoras) campo(janela, "notificacoes");
                BotaoSakura botao = (BotaoSakura) campo(janela, "botaoTema");

                assertEquals(Tema.NOTURNO, GerenciadorTema.atual());
                assertEquals(Modo.DESATIVADAS, ConfiguracaoAnimacoes.modo());
                assertTrue(sons.silenciado());
                assertEquals(ClienteChat.ROTULO_VOLTAR_CLARO, botao.getToolTipText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(p.aguardarGravacoes(5000));
        assertArrayEquals(antes, Files.readAllBytes(p.arquivo()),
                "Aplicar as preferências não regrava o arquivo.");
    }

    @Test
    void botaoDeSomDeveSalvarAPreferencia() throws Exception {
        PreferenciasSakura p = preferencias();

        naEdt(() -> {
            try {
                BotaoSakura som = (BotaoSakura) campo(abrir(p), "botaoSom");
                som.doClick();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(p.aguardarGravacoes(5000));
        assertEquals("silenciados", p.ler().getProperty("sons"));
    }

    @Test
    void fecharAJanelaDeveRemoverOsOuvintes() throws Exception {
        int[] antes = new int[1];
        naEdt(() -> antes[0] = GerenciadorTema.quantidadeOuvintes());

        naEdt(() -> {
            ClienteChat janela = abrir(preferencias());
            assertEquals(antes[0] + 1, GerenciadorTema.quantidadeOuvintes());
            janela.dispose();
        });
        naEdt(() -> { });

        naEdt(() -> assertEquals(antes[0], GerenciadorTema.quantidadeOuvintes()));
    }
}
