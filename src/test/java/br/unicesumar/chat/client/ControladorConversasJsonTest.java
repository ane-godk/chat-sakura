package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controlador no protocolo v2: barra lateral, não lidas, troca de conversa,
 * histórico sem duplicação e exportação da conversa ativa. Os eventos são
 * injetados como se viessem da rede; tudo roda na EDT.
 */
class ControladorConversasJsonTest {

    private static final class Visao implements ControladorConversas.Visao {

        List<ControladorConversas.ItemLateral> itens = List.of();
        long ativa;
        List<String> exibida = new ArrayList<>();
        final List<String> linhasAtivas = new ArrayList<>();

        @Override public void reiniciarConversa(long id, String usuario) { exibida = new ArrayList<>(); }
        @Override public void exibirLinha(long id, String linha) { linhasAtivas.add(id + "|" + linha); }
        @Override public boolean historicoConcluido(long id) { return true; }
        @Override public void estadoAlterado(ControladorConversas.EstadoConexao e) { }
        @Override public void mensagemEnviada() { }
        @Override public void usuarioIdentificado(String apelido) { }
        @Override public void autenticacaoRecusada(String mensagem) { }

        @Override
        public void exibirConversa(ControladorConversas.Conversa conversa) {
            exibida = new ArrayList<>(conversa.linhas());
        }

        @Override
        public void conversasAtualizadas(List<ControladorConversas.ItemLateral> novos, long idAtiva) {
            itens = novos;
            ativa = idAtiva;
        }

        ControladorConversas.ItemLateral item(long id) {
            return itens.stream().filter(i -> i.id() == id).findFirst().orElseThrow();
        }
    }

    private final Visao visao = new Visao();
    private final AtomicReference<ConexaoServidor.Ouvinte> ouvinte = new AtomicReference<>();
    private ControladorConversas controlador;

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable t) {
                erro.set(t);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    private void criar() throws Exception {
        naEdt(() -> controlador = new ControladorConversas(
                visao,
                new NotificacoesSonoras(new ReprodutorNulo(), System::currentTimeMillis),
                Runnable::run,
                o -> {
                    ouvinte.set(o);
                    return new ConexaoServidor(o);
                }));
    }

    private static final class ReprodutorNulo implements ReprodutorSom {
        @Override public void tocar(SomSakura som) { }
        @Override public void fechar() { }
    }

    private void evento(String linha) throws Exception {
        ProtocoloJson.Evento evento = ProtocoloJson.lerEvento(linha);
        naEdt(() -> ouvinte.get().eventoRecebido(evento));
    }

    private static Mensagem mensagem(long id, long conversa, long autorId, String autor, String texto) {
        return new Mensagem(id, new Usuario(autorId, autor), texto,
                LocalDateTime.of(2026, 11, 10, 15, (int) (id % 60)), TipoMensagem.MENSAGEM, conversa);
    }

    private static String mensagemJson(Mensagem m) {
        return ProtocoloJson.eventoMensagem(ProtocoloJson.MensagemJson.de(m));
    }

    private static final Conversa GERAL = new Conversa(1, Conversa.Tipo.GERAL, "Chat Geral", List.of());
    private static final Conversa GRUPO = new Conversa(5, Conversa.Tipo.GRUPO, "Turma", List.of(
            new Conversa.Participante(1, "Ana", "sakura", Conversa.Papel.ADMIN),
            new Conversa.Participante(2, "Bia", "sakura", Conversa.Papel.MEMBRO)));
    private static final Conversa PRIVADA = new Conversa(7, Conversa.Tipo.PRIVADA, null, List.of(
            new Conversa.Participante(1, "Ana", "sakura", Conversa.Papel.MEMBRO),
            new Conversa.Participante(2, "Bia", "sakura", Conversa.Papel.MEMBRO)));

    /** Ana entra: Geral, grupo com 2 não lidas e privada com Bia. */
    private void entrarComoAna() throws Exception {
        criar();
        naEdt(() -> ouvinte.get().identidadeConfirmada("Ana"));
        evento(ProtocoloJson.eventoConversas(null, List.of(
                ProtocoloJson.ConversaJson.de(GERAL, null, 0),
                ProtocoloJson.ConversaJson.de(GRUPO, mensagem(20, 5, 2, "Bia", "Bem-vindas!"), 2),
                ProtocoloJson.ConversaJson.de(PRIVADA, mensagem(10, 7, 2, "Bia", "Oi!"), 0))));
        evento(ProtocoloJson.eventoHistorico(null, 1, List.of(
                ProtocoloJson.MensagemJson.de(mensagem(3, 1, 2, "Bia", "Olá, Geral")))));
    }

    @Test
    void barraDeveListarConversasComTitulosPreviasENaoLidas() throws Exception {
        entrarComoAna();

        assertEquals(List.of(1L, 5L, 7L), visao.itens.stream().map(ControladorConversas.ItemLateral::id).toList(),
                "Geral primeiro; depois a atividade mais recente");
        assertEquals("Bia", visao.item(7).titulo(), "Privada mostra o nome da outra pessoa");
        assertEquals("Turma", visao.item(5).titulo());
        assertEquals(2, visao.item(5).naoLidas());
        assertEquals("Bia: Bem-vindas!", visao.item(5).previa());
        assertTrue(visao.exibida.contains("[10/11 15:03] Bia: Olá, Geral"));

        // Nova mensagem na privada inativa: conta como não lida e sobe na lista.
        evento(mensagemJson(mensagem(30, 7, 2, "Bia", "Tudo bem?")));
        assertEquals(1, visao.item(7).naoLidas());
        assertEquals("Bia: Tudo bem?", visao.item(7).previa());
        assertEquals(List.of(1L, 7L, 5L), visao.itens.stream().map(ControladorConversas.ItemLateral::id).toList());

        // Mensagem própria (de outra sessão) não conta como não lida.
        evento(mensagemJson(mensagem(31, 5, 1, "Ana", "Oi, turma")));
        assertEquals(2, visao.item(5).naoLidas());

        // Selecionar zera o contador e troca o conteúdo exibido.
        naEdt(() -> controlador.selecionar(7));
        assertEquals(0, visao.item(7).naoLidas());
        assertEquals(7, visao.ativa);
        assertEquals(List.of("[10/11 15:30] Bia: Tudo bem?"), visao.exibida);
    }

    @Test
    void historicoDeveMesclarMensagensAoVivoSemDuplicarNemMisturarConversas() throws Exception {
        entrarComoAna();
        naEdt(() -> controlador.selecionar(5));
        visao.linhasAtivas.clear();

        // Chega uma mensagem ao vivo antes do histórico.
        evento(mensagemJson(mensagem(22, 5, 2, "Bia", "Ao vivo")));
        assertEquals(List.of("5|[10/11 15:22] Bia: Ao vivo"), visao.linhasAtivas);

        evento(ProtocoloJson.eventoHistorico(null, 5, List.of(
                ProtocoloJson.MensagemJson.de(mensagem(20, 5, 2, "Bia", "Bem-vindas!")),
                ProtocoloJson.MensagemJson.de(mensagem(22, 5, 2, "Bia", "Ao vivo")))));

        List<String> esperadas = List.of(
                FormatoLinhaChat.INICIO_HISTORICO,
                "[10/11 15:20] Bia: Bem-vindas!",
                "[10/11 15:22] Bia: Ao vivo",
                FormatoLinhaChat.FIM_HISTORICO);
        assertEquals(esperadas, visao.exibida, "Sem duplicar a mensagem ao vivo");

        // Mensagem do Geral não entra na conversa ativa.
        evento(mensagemJson(mensagem(40, 1, 3, "Caio", "No Geral")));
        assertEquals(1, visao.linhasAtivas.size());
        AtomicReference<String> texto = new AtomicReference<>();
        naEdt(() -> texto.set(controlador.conversaAtiva().texto()));
        assertEquals(String.join("\n", esperadas) + "\n", texto.get(), "Exportação só da conversa ativa");

        // Ao voltar ao Geral, a mensagem está lá.
        naEdt(() -> controlador.selecionar(1));
        assertTrue(visao.exibida.contains("[10/11 15:40] Caio: No Geral"));
        assertFalse(visao.exibida.stream().anyMatch(l -> l.contains("Ao vivo")));
    }

    @Test
    void mensagemDeConversaDesconhecidaDeveCriarUmaEntradaNaBarra() throws Exception {
        entrarComoAna();
        evento(mensagemJson(mensagem(50, 9, 3, "Caio", "Criei um grupo novo")));

        assertEquals("Nova conversa", visao.item(9).titulo());
        assertEquals(1, visao.item(9).naoLidas());

        evento(ProtocoloJson.eventoConversa(null, ProtocoloJson.ConversaJson.de(
                new Conversa(9, Conversa.Tipo.GRUPO, "Projeto", List.of()), null, 1)));
        assertEquals("Projeto", visao.item(9).titulo());
    }

    @Test
    void conversaComIADeveAvisarQueEPersonagemEMostrarDigitandoEErros() throws Exception {
        entrarComoAna();
        Conversa naruto = new Conversa(11, Conversa.Tipo.IA, "Naruto", List.of(
                new Conversa.Participante(1, "Ana", "sakura", Conversa.Papel.ADMIN)));
        evento(ProtocoloJson.eventoConversa(null, ProtocoloJson.ConversaJson.de(naruto, null, 0)));

        assertEquals(AvatarSakura.IA, visao.item(11).avatarId());
        naEdt(() -> controlador.selecionar(11));
        assertEquals(ControladorConversas.avisoIA("Naruto"), visao.exibida.get(0), "Aviso de personagem de IA");

        evento(ProtocoloJson.eventoEstadoIA(11, "GERANDO", "Qwen qwen-plus"));
        assertTrue(visao.item(11).gerando());
        assertEquals("digitando…", visao.item(11).previa());
        AtomicReference<String> titulo = new AtomicReference<>();
        naEdt(() -> titulo.set(ClienteChat.tituloDe(controlador.conversaAtiva())));
        assertEquals("Naruto · personagem de IA · digitando…", titulo.get());

        Mensagem resposta = new Mensagem(60L, new Usuario("Naruto"), "Dattebayo!",
                LocalDateTime.of(2026, 11, 10, 16, 0), TipoMensagem.IA, 11);
        evento(mensagemJson(resposta));
        evento(ProtocoloJson.eventoEstadoIA(11, "CONCLUIDO", "Qwen qwen-plus"));
        assertFalse(visao.item(11).gerando());
        assertEquals("Naruto: Dattebayo!", visao.item(11).previa());
        assertTrue(visao.linhasAtivas.contains("11|[10/11 16:00] Naruto: Dattebayo!"));

        evento(ProtocoloJson.eventoEstadoIA(11, "ERRO", "Chave do Qwen inválida ou sem permissão (QWEN_API_KEY)."));
        assertTrue(visao.linhasAtivas.contains(
                "11|SISTEMA: Sakura IA indisponível: Chave do Qwen inválida ou sem permissão (QWEN_API_KEY)."));

        // Resposta da IA em conversa inativa conta como não lida.
        naEdt(() -> controlador.selecionar(1));
        evento(mensagemJson(new Mensagem(61L, new Usuario("Naruto"), "Ainda está aí?",
                LocalDateTime.of(2026, 11, 10, 16, 1), TipoMensagem.IA, 11)));
        assertEquals(1, visao.item(11).naoLidas());
    }

    @Test
    void avataresDevemAparecerNoPerfilENaConversaPrivada() throws Exception {
        entrarComoAna();
        AtomicReference<String> avatar = new AtomicReference<>();
        naEdt(() -> avatar.set(controlador.avatarProprio()));
        assertEquals("sakura", avatar.get());

        // Privada mostra o avatar da outra pessoa.
        Conversa privadaComGato = new Conversa(7, Conversa.Tipo.PRIVADA, null, List.of(
                new Conversa.Participante(1, "Ana", "sakura", Conversa.Papel.MEMBRO),
                new Conversa.Participante(2, "Bia", "gato", Conversa.Papel.MEMBRO)));
        evento(ProtocoloJson.eventoConversa(null, ProtocoloJson.ConversaJson.de(privadaComGato, null, 0)));
        assertEquals("gato", visao.item(7).avatarId());
        assertNull(visao.item(5).avatarId(), "Grupo usa o ícone de grupo");

        assertTrue(br.unicesumar.chat.model.CatalogoAvatares.AVATARES.size() >= 6);
        assertEquals("sakura", br.unicesumar.chat.model.CatalogoAvatares.normalizar("desconhecido"));
        assertFalse(br.unicesumar.chat.model.CatalogoAvatares.valido("../x.png"));
    }

    @Test
    void nomeDoArquivoExportadoDeveIdentificarAConversa() {
        assertEquals("chat-sakura-turma-de-java-2026-11-10.txt",
                ClienteChat.nomeArquivoExportacao("Turma de Java!", "2026-11-10"));
        assertEquals("chat-sakura-joao-2026-11-10.txt", ClienteChat.nomeArquivoExportacao("João", "2026-11-10"));
        assertEquals("chat-sakura-conversa-2026-11-10.txt", ClienteChat.nomeArquivoExportacao("🌸", "2026-11-10"));
    }

    @Test
    void barraLateralDeveSeguirOsTemasClaroENoturno() throws Exception {
        GerenciadorTema.Tema original = GerenciadorTema.atual();
        AtomicReference<int[]> cores = new AtomicReference<>();

        try {
            naEdt(() -> {
                BarraConversas barra = new BarraConversas(id -> { });
                barra.atualizar(List.of(
                        new ControladorConversas.ItemLateral(1, Conversa.Tipo.GERAL, "Chat Geral", "", 0),
                        new ControladorConversas.ItemLateral(5, Conversa.Tipo.GRUPO, "Turma", "Bia: oi", 3)), 1);
                barra.setSize(BarraConversas.LARGURA, 300);
                barra.doLayout();
                barra.validate();

                int[] pixels = new int[2];
                int i = 0;
                for (GerenciadorTema.Tema tema : new GerenciadorTema.Tema[] {
                        GerenciadorTema.Tema.CLARO, GerenciadorTema.Tema.NOTURNO}) {
                    GerenciadorTema.definir(tema);
                    BufferedImage imagem = new BufferedImage(BarraConversas.LARGURA, 300, BufferedImage.TYPE_INT_RGB);
                    barra.paint(imagem.getGraphics());
                    pixels[i++] = imagem.getRGB(BarraConversas.LARGURA / 2, 280);
                }
                cores.set(pixels);
            });
        } finally {
            naEdt(() -> GerenciadorTema.definir(original));
        }

        assertNotEquals(cores.get()[0], cores.get()[1], "O fundo da barra muda com o tema");
        int brilhoClaro = brilho(cores.get()[0]);
        int brilhoNoturno = brilho(cores.get()[1]);
        assertTrue(brilhoClaro > brilhoNoturno, "Claro é mais claro que o Noturno");
    }

    private static int brilho(int rgb) {
        return ((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff);
    }
}
