package br.unicesumar.chat.service;

import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.model.CatalogoAvatares;
import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Personagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcConversaRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static br.unicesumar.chat.service.ConversaException.Codigo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Personagens de IA, histórico, isolamento e avatares sobre SQLite temporário. */
class ServicoIATest {

    @TempDir
    Path pasta;

    private ConnectionFactory fabrica;
    private Servidor.Servicos servicos;
    private Usuario ana;
    private Usuario bia;

    /** Sessão que só registra o que recebe. */
    private static final class Sessao implements ConexaoCliente {
        final Usuario usuario;
        final List<Mensagem> mensagens = Collections.synchronizedList(new ArrayList<>());
        final List<String> estados = Collections.synchronizedList(new ArrayList<>());
        final List<String> perfis = Collections.synchronizedList(new ArrayList<>());

        Sessao(Usuario usuario) {
            this.usuario = usuario;
        }

        @Override public Usuario getUsuario() { return usuario; }
        @Override public void enviar(Mensagem m) { mensagens.add(m); }
        @Override public void estadoIA(long id, String estado, String msg) { estados.add(estado + ":" + msg); }
        @Override public void perfilAtualizado(br.unicesumar.chat.model.PerfilUsuario p) {
            perfis.add(p.getApelido() + ":" + p.getAvatarId());
        }
    }

    private void montar(ProvedorIA provedor) throws Exception {
        fabrica = new ConnectionFactory(pasta.resolve("ia.db").toString());
        servicos = Servidor.Servicos.montar(fabrica, new HashSenha(1_000), Clock.systemDefaultZone(), provedor);
        if (ana == null) {
            ana = servicos.autenticacao().cadastrar("ana", "Ana", "senha-da-ana".toCharArray()).comoUsuario();
            bia = servicos.autenticacao().cadastrar("bia", "Bia", "senha-da-bia".toCharArray()).comoUsuario();
        }
    }

    private Sessao conectar(Usuario usuario) {
        Sessao sessao = new Sessao(usuario);
        ClientRegistry registro = registro();
        registro.registrar(sessao);
        return sessao;
    }

    /** O registro do serviço de conversas (mesmo usado nas entregas). */
    private ClientRegistry registro() {
        try {
            var campo = ServicoConversas.class.getDeclaredField("registro");
            campo.setAccessible(true);
            return (ClientRegistry) campo.get(servicos.conversas());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private long criarNaruto() throws Exception {
        return servicos.ia().criarPersonagem(ana, "Naruto",
                "animado, determinado, amigável, motivador e descontraído",
                "Estamos treinando juntos na Vila da Folha.").conversa().getId();
    }

    /** Envio como o servidor faz: verificar, gravar, agendar a resposta. */
    private Mensagem enviar(Usuario usuario, long conversa, String texto) throws Exception {
        servicos.ia().verificarEnvio(usuario, conversa);
        Mensagem m = servicos.conversas().enviar(usuario, conversa, texto);
        servicos.ia().aposMensagem(usuario, m);
        return m;
    }

    private static ConversaException.Codigo codigo(Executavel acao) {
        return assertThrows(ConversaException.class, acao::executar).getCodigo();
    }

    @FunctionalInterface
    private interface Executavel {
        void executar() throws Exception;
    }

    @Test
    void personagemDeveSerCriadoValidadoEPersistido() throws Exception {
        montar(new ProvedorIAFalso());
        ResumoConversa resumo = servicos.ia().criarPersonagem(ana, " Naruto ", " animado ", "");

        assertEquals(Conversa.Tipo.IA, resumo.conversa().getTipo());
        assertEquals("Naruto", resumo.conversa().getNome());
        assertEquals(List.of(ana.getId()), resumo.conversa().getParticipantes().stream()
                .map(Conversa.Participante::id).toList(), "Somente a dona participa");

        Personagem salvo = new JdbcConversaRepository(fabrica).personagemDaConversa(resumo.conversa().getId())
                .orElseThrow();
        assertEquals("animado", salvo.getPersonalidade());
        assertEquals(ana.getId(), salvo.getDonoId());

        assertEquals(DADOS_INVALIDOS, codigo(() -> servicos.ia().criarPersonagem(ana, "Naruto Uzumaki", "x", "")));
        assertEquals(DADOS_INVALIDOS, codigo(() -> servicos.ia().criarPersonagem(ana, "", "x", "")));
        assertEquals(DADOS_INVALIDOS, codigo(() -> servicos.ia().criarPersonagem(ana, "Naruto", " ", "")));
        assertEquals(DADOS_INVALIDOS, codigo(() -> servicos.ia().criarPersonagem(ana, "Naruto", "x".repeat(401), "")));
        assertEquals(DADOS_INVALIDOS, codigo(() -> servicos.ia().criarPersonagem(ana, "Naruto", "x", "c".repeat(1201))));
        assertEquals(CONTA_NECESSARIA, codigo(() -> servicos.ia().criarPersonagem(new Usuario("Zoe"), "Naruto", "x", "")));
    }

    @Test
    void conversaComIADeveGravarPerguntaERespostaNaOrdemEIsoladas() throws Exception {
        ProvedorIAFalso falso = new ProvedorIAFalso();
        montar(falso);
        Sessao sAna = conectar(ana);
        Sessao sBia = conectar(bia);

        long naruto = criarNaruto();
        long sakura = servicos.ia().criarPersonagem(ana, "Sakura", "inteligente e firme", "").conversa().getId();

        enviar(ana, naruto, "Bora treinar o Rasengan?");
        assertTrue(servicos.ia().aguardar(5_000));
        enviar(ana, sakura, "Oi, Sakura!");
        assertTrue(servicos.ia().aguardar(5_000));
        enviar(ana, naruto, "E agora, sensei?");
        assertTrue(servicos.ia().aguardar(5_000));

        List<Mensagem> historico = servicos.conversas().historico(ana, naruto, null, 50);
        assertEquals(List.of("MENSAGEM:Ana", "IA:Naruto", "MENSAGEM:Ana", "IA:Naruto"), historico.stream()
                .map(m -> m.getTipo() + ":" + m.getRemetente().getNome()).toList());
        assertTrue(historico.get(1).getConteudo().startsWith(ProvedorIAFalso.PREFIXO), "Simulado é identificado");
        assertNull(historico.get(1).getRemetente().getId(), "Resposta da IA não tem conta");
        assertEquals(ana.getId(), historico.get(0).getRemetente().getId());
        assertTrue(historico.get(0).getId() < historico.get(1).getId());

        // Pedido da 3ª geração: instruções do Naruto e só o histórico dele.
        ProvedorIA.PedidoIA terceiro = falso.pedidos().get(2);
        assertTrue(terceiro.instrucoes().contains("Naruto") && terceiro.instrucoes().contains("Vila da Folha")
                && terceiro.instrucoes().contains("motivador"));
        assertEquals(List.of("Bora treinar o Rasengan?", "E agora, sensei?"), terceiro.historico().stream()
                .filter(t -> t.papel() == ProvedorIA.Papel.USUARIO).map(ProvedorIA.Turno::texto).toList());
        assertTrue(terceiro.historico().stream().noneMatch(t -> t.texto().contains("Sakura!")), "Sem misturar");
        assertFalse(terceiro.instrucoes().contains("senha-da-ana"));
        assertEquals(ProvedorIA.Papel.USUARIO, terceiro.historico().get(terceiro.historico().size() - 1).papel());

        // Entregas e estados só para a dona; Bia não vê nada.
        assertTrue(sAna.estados.contains("GERANDO:SIMULADO") && sAna.estados.contains("CONCLUIDO:SIMULADO"));
        assertTrue(sBia.mensagens.isEmpty() && sBia.estados.isEmpty());
        assertEquals(6, sAna.mensagens.size(), "3 perguntas e 3 respostas");

        // Isolamento: Bia não lista, não lê, não envia.
        assertTrue(servicos.conversas().listar(bia).stream().noneMatch(r -> r.conversa().getTipo() == Conversa.Tipo.IA));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servicos.conversas().historico(bia, naruto, null, 10)));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servicos.conversas().enviar(bia, naruto, "oi")));
        assertEquals(3, servicos.conversas().listar(ana).size(), "Geral + 2 personagens");

        // Geração fora da thread que chamou (e fora da EDT).
        assertTrue(falso.threads().stream().allMatch(t -> t.startsWith("sakura-ia-")), falso.threads().toString());
    }

    @Test
    void historicoEPersonagensDevemSobreviverAoReinicio() throws Exception {
        montar(new ProvedorIAFalso());
        long naruto = criarNaruto();
        enviar(ana, naruto, "Lembra de mim?");
        assertTrue(servicos.ia().aguardar(5_000));

        montar(new ProvedorIAFalso());

        assertEquals(2, servicos.conversas().historico(ana, naruto, null, 10).size());
        assertEquals("Naruto", servicos.conversas().listar(ana).stream()
                .filter(r -> r.conversa().getId() == naruto).findFirst().orElseThrow().conversa().getNome());
    }

    @Test
    void falhaDaIANaoDeveGravarRespostaNemTravarOChat() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ProvedorIA lento = new ProvedorIA() {
            @Override
            public String gerar(PedidoIA pedido) throws FalhaIA {
                entrou.countDown();
                try {
                    liberar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new FalhaIA(FalhaIA.Codigo.LIMITE, "Limite de uso do Qwen atingido.");
            }

            @Override
            public String descricao() {
                return "Qwen teste";
            }
        };
        montar(lento);
        Sessao sAna = conectar(ana);
        long naruto = criarNaruto();

        enviar(ana, naruto, "Oi!");
        assertTrue(entrou.await(5, TimeUnit.SECONDS));

        // Enquanto a IA "pensa": novo envio ao personagem é recusado...
        assertEquals(LIMITE, codigo(() -> enviar(ana, naruto, "Oi de novo?")));
        // ...mas o Chat Geral e as demais conversas seguem funcionando.
        servicos.conversas().enviar(bia, Conversa.ID_GERAL, "Geral funcionando");
        long privada = servicos.conversas().abrirPrivada(ana, bia.getId()).conversa().getId();
        servicos.conversas().enviar(ana, privada, "Privada funcionando");

        liberar.countDown();
        assertTrue(servicos.ia().aguardar(5_000));

        assertEquals(List.of("Oi!"), servicos.conversas().historico(ana, naruto, null, 10).stream()
                .map(Mensagem::getConteudo).toList(), "Nenhuma resposta inventada");
        assertTrue(sAna.estados.contains("ERRO:Limite de uso do Qwen atingido."));
    }

    @Test
    void usuarioNaoAutorizadoNaoDeveSaberDoPersonagemAlheioNemGastarCota() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ProvedorIA lento = new ProvedorIA() {
            @Override
            public String gerar(PedidoIA pedido) {
                entrou.countDown();
                try {
                    liberar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Dattebayo!";
            }

            @Override
            public String descricao() {
                return "Qwen teste";
            }
        };
        montar(lento);
        Sessao sBia = conectar(bia);
        long naruto = criarNaruto();

        // Ana conversa com o personagem; a resposta está sendo gerada.
        enviar(ana, naruto, "Oi, Naruto!");
        assertTrue(entrou.await(5, TimeUnit.SECONDS));

        // Bia tenta usar o id da conversa de IA da Ana: mesma resposta de uma
        // conversa inexistente, nunca LIMITE ("gerando") — e muitas vezes, além da cota.
        ConversaException alheia = assertThrows(ConversaException.class,
                () -> servicos.ia().verificarEnvio(bia, naruto));
        ConversaException inexistente = assertThrows(ConversaException.class,
                () -> servicos.ia().verificarEnvio(bia, 987_654));
        assertEquals(NAO_ENCONTRADA, alheia.getCodigo());
        assertEquals(inexistente.getCodigo(), alheia.getCodigo());
        assertEquals(inexistente.getMessage(), alheia.getMessage(), "Nada revela que a conversa existe");

        for (int i = 0; i < ServicoIA.GERACOES_POR_MINUTO * 2; i++) {
            assertEquals(NAO_ENCONTRADA, codigo(() -> enviar(bia, naruto, "invasão")));
        }

        liberar.countDown();
        assertTrue(servicos.ia().aguardar(5_000));
        assertTrue(sBia.estados.isEmpty(), "Bia não recebe o estado da geração alheia: " + sBia.estados);

        // A cota de IA da Bia continua intacta para o próprio personagem.
        long sasuke = servicos.ia().criarPersonagem(bia, "Sasuke", "sério e reservado", "").conversa().getId();
        for (int i = 0; i < ServicoIA.GERACOES_POR_MINUTO; i++) {
            enviar(bia, sasuke, "treino " + i);
            assertTrue(servicos.ia().aguardar(5_000));
        }

        // Nada da Bia entrou no histórico da Ana, e a Bia não recebeu nada dele.
        assertEquals(List.of("Oi, Naruto!", "Dattebayo!"), servicos.conversas().historico(ana, naruto, null, 10)
                .stream().map(Mensagem::getConteudo).toList());
        assertTrue(sBia.mensagens.stream().noneMatch(m -> m.getConversaId() == naruto));
    }

    @Test
    void limiteDeUsoPorContaDeveSerAplicado() throws Exception {
        montar(new ProvedorIAFalso());
        long naruto = criarNaruto();
        for (int i = 0; i < ServicoIA.GERACOES_POR_MINUTO; i++) {
            enviar(ana, naruto, "msg " + i);
            assertTrue(servicos.ia().aguardar(5_000));
        }
        assertEquals(LIMITE, codigo(() -> enviar(ana, naruto, "uma a mais")));
    }

    @Test
    void contextoDeveRespeitarLimitesERespostasLongasSeremDivididas() {
        Personagem p = new Personagem(1, 1, "Naruto", "animado", "");
        List<Mensagem> muitas = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            muitas.add(new Mensagem(i, new Usuario(1L, "Ana"), "x".repeat(490), java.time.LocalDateTime.now(),
                    TipoMensagem.MENSAGEM, 2));
        }
        ProvedorIA.PedidoIA pedido = ServicoIA.montarPedido(p, "Ana", muitas);
        int total = pedido.historico().stream().mapToInt(t -> t.texto().length()).sum();
        assertTrue(total <= ServicoIA.CARACTERES_CONTEXTO, "Contexto limitado: " + total);
        assertEquals(20L, muitas.get(19).getId(), "Mantém as mais recentes");

        List<String> partes = ServicoIA.dividir("Frase um. ".repeat(120), 500);
        assertTrue(partes.size() > 1 && partes.stream().allMatch(s -> s.length() <= 500 && !s.isBlank()));
    }

    @Test
    void geracaoNaoDeveOcorrerNaEdtMesmoQuandoDisparadaDela() throws Exception {
        ProvedorIAFalso falso = new ProvedorIAFalso();
        montar(falso);
        long naruto = criarNaruto();
        SwingUtilities.invokeAndWait(() -> {
            try {
                enviar(ana, naruto, "Disparado da EDT");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        assertTrue(servicos.ia().aguardar(5_000));
        assertEquals(1, falso.threads().size());
        assertFalse(falso.threads().get(0).contains("AWT-EventQueue"));
    }

    @Test
    void avatarDeveSerValidadoPersistidoEAnunciado() throws Exception {
        montar(new ProvedorIAFalso());
        Sessao sBia = conectar(bia);

        assertTrue(CatalogoAvatares.AVATARES.size() >= 6);
        assertEquals("lua", servicos.autenticacao().definirAvatar(ana, "lua").getAvatarId());
        servicos.conversas().anunciarPerfil(new JdbcUsuarioRepository(fabrica).buscarPorId(ana.getId()).orElseThrow());
        assertEquals(List.of("Ana:lua"), sBia.perfis);

        for (String invalido : new String[] {null, "", "../../etc/passwd", "C:\\imagem.png", "LUA", "dragao"}) {
            assertThrows(AutenticacaoException.class, () -> servicos.autenticacao().definirAvatar(ana, invalido),
                    String.valueOf(invalido));
        }
        assertThrows(AutenticacaoException.class,
                () -> servicos.autenticacao().definirAvatar(new Usuario("Zoe"), "lua"));

        // Reinício: o avatar continua salvo e volta no login.
        montar(new ProvedorIAFalso());
        assertEquals("lua", servicos.autenticacao().autenticar("ana", "senha-da-ana".toCharArray()).getAvatarId());
    }
}
