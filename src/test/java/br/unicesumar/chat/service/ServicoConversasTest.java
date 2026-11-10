package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcConversaRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static br.unicesumar.chat.service.ConversaException.Codigo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Conversas privadas e grupos sobre SQLite temporário, com sessões falsas. */
class ServicoConversasTest {

    @TempDir
    Path pasta;

    private ConnectionFactory fabrica;
    private ClientRegistry registro;
    private ServicoConversas servico;
    private Usuario ana;
    private Usuario bia;
    private Usuario caio;

    /** Sessão que só registra o que recebe. */
    private static final class Sessao implements ConexaoCliente {

        final Usuario usuario;
        final List<Mensagem> recebidas = Collections.synchronizedList(new ArrayList<>());
        final List<ResumoConversa> avisos = Collections.synchronizedList(new ArrayList<>());

        Sessao(Usuario usuario) {
            this.usuario = usuario;
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public void enviar(Mensagem mensagem) {
            recebidas.add(mensagem);
        }

        @Override
        public void conversaDisponivel(ResumoConversa resumo) {
            avisos.add(resumo);
        }

        List<String> textos() {
            synchronized (recebidas) {
                return recebidas.stream().map(Mensagem::getConteudo).toList();
            }
        }
    }

    private void montar() {
        JdbcMensagemRepository mensagens = new JdbcMensagemRepository(fabrica);
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(fabrica);
        registro = new ClientRegistry();
        ChatService chat = new ChatService(mensagens, registro, Clock.systemDefaultZone());
        servico = new ServicoConversas(new JdbcConversaRepository(fabrica), mensagens, usuarios,
                chat, registro, Clock.systemDefaultZone());
    }

    @BeforeEach
    void iniciar() throws Exception {
        fabrica = new ConnectionFactory(pasta.resolve("conversas.db").toString());
        montar();
        ServicoAutenticacao contas = new ServicoAutenticacao(new JdbcUsuarioRepository(fabrica),
                new HashSenha(1_000), Clock.systemDefaultZone());
        ana = contas.cadastrar("ana", "Ana", "senha-da-ana".toCharArray()).comoUsuario();
        bia = contas.cadastrar("bia", "Bia", "senha-da-bia".toCharArray()).comoUsuario();
        caio = contas.cadastrar("caio", "Caio", "senha-do-caio".toCharArray()).comoUsuario();
    }

    private Sessao conectar(Usuario usuario) {
        Sessao sessao = new Sessao(usuario);
        registro.registrar(sessao);
        return sessao;
    }

    private static ConversaException.Codigo codigo(Executavel acao) {
        return assertThrows(ConversaException.class, acao::executar).getCodigo();
    }

    @FunctionalInterface
    private interface Executavel {
        void executar() throws Exception;
    }

    @Test
    void conversaPrivadaDeveSerReutilizadaParaOMesmoPar() throws Exception {
        Sessao sessaoBia = conectar(bia);

        ResumoConversa deAna = servico.abrirPrivada(ana, bia.getId());
        ResumoConversa deBia = servico.abrirPrivada(bia, ana.getId());

        assertEquals(deAna.conversa().getId(), deBia.conversa().getId());
        assertEquals(Conversa.Tipo.PRIVADA, deAna.conversa().getTipo());
        assertEquals(List.of("Ana", "Bia"),
                deAna.conversa().getParticipantes().stream().map(Conversa.Participante::apelido).sorted().toList());
        assertEquals(1, sessaoBia.avisos.size(), "Bia é avisada só na criação");

        assertEquals(DADOS_INVALIDOS, codigo(() -> servico.abrirPrivada(ana, ana.getId())));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.abrirPrivada(ana, 999)));
        assertEquals(CONTA_NECESSARIA, codigo(() -> servico.abrirPrivada(new Usuario("Visitante"), bia.getId())));
    }

    @Test
    void mensagemPrivadaDeveChegarSomenteAosDoisParticipantes() throws Exception {
        Sessao sAna = conectar(ana);
        Sessao sBia = conectar(bia);
        Sessao sCaio = conectar(caio);
        Sessao legado = conectar(new Usuario("Zoe"));

        long id = servico.abrirPrivada(ana, bia.getId()).conversa().getId();
        Mensagem enviada = servico.enviar(ana, id, "Segredo entre nós");

        assertEquals(List.of("Segredo entre nós"), sAna.textos());
        assertEquals(List.of("Segredo entre nós"), sBia.textos());
        assertTrue(sCaio.textos().isEmpty(), "Terceiro não recebe");
        assertTrue(legado.textos().isEmpty(), "Sessão sem conta não recebe");

        assertEquals(id, enviada.getConversaId());
        assertEquals(ana.getId(), enviada.getRemetente().getId());

        // Caio não lê, não envia, não marca como lida e não vê na lista.
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.historico(caio, id, null, 50)));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.enviar(caio, id, "intruso")));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.marcarLida(caio, id, enviada.getId())));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.historico(caio, 9999, null, 50)),
                "Inexistente e sem acesso têm a mesma resposta");
        assertEquals(List.of(Conversa.ID_GERAL),
                servico.listar(caio).stream().map(r -> r.conversa().getId()).toList());
        assertEquals(CONTA_NECESSARIA, codigo(() -> servico.historico(new Usuario("Zoe"), id, null, 50)));

        assertEquals(1, sBia.textos().size(), "A tentativa do intruso não foi entregue");
    }

    @Test
    void grupoDeveTerPapeisEEntregarSomenteAosMembros() throws Exception {
        Sessao sAna = conectar(ana);
        Sessao sBia = conectar(bia);
        Sessao sCaio = conectar(caio);

        ResumoConversa grupo = servico.criarGrupo(ana, "  Turma de Java ", List.of(bia.getId(), ana.getId(), bia.getId()));
        Conversa conversa = grupo.conversa();

        assertEquals("Turma de Java", conversa.getNome());
        assertEquals(List.of("ADMIN:Ana", "MEMBRO:Bia"), conversa.getParticipantes().stream()
                .map(p -> p.papel() + ":" + p.apelido()).toList());
        assertEquals(1, sBia.avisos.size());

        servico.enviar(bia, conversa.getId(), "Oi, turma!");
        assertEquals(List.of("Oi, turma!"), sAna.textos());
        assertEquals(List.of("Oi, turma!"), sBia.textos());
        assertTrue(sCaio.textos().isEmpty());
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.enviar(caio, conversa.getId(), "posso entrar?")));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.historico(caio, conversa.getId(), null, 10)));

        assertEquals(DADOS_INVALIDOS, codigo(() -> servico.criarGrupo(ana, " ", List.of(bia.getId()))));
        assertEquals(DADOS_INVALIDOS, codigo(() -> servico.criarGrupo(ana, "x".repeat(41), List.of(bia.getId()))));
        assertEquals(DADOS_INVALIDOS, codigo(() -> servico.criarGrupo(ana, "Só eu", List.of(ana.getId()))));
        assertEquals(NAO_ENCONTRADA, codigo(() -> servico.criarGrupo(ana, "Fantasma", List.of(777L))));
    }

    @Test
    void destinatarioOfflineDeveRecuperarMensagensENaoLidasAposReinicio() throws Exception {
        conectar(ana);
        long grupo = servico.criarGrupo(ana, "Estudos", List.of(bia.getId(), caio.getId())).conversa().getId();
        long privada = servico.abrirPrivada(ana, bia.getId()).conversa().getId();

        servico.enviar(ana, grupo, "Reunião às 19h");
        servico.enviar(ana, privada, "Me responde quando puder");
        servico.enviar(ana, privada, "Bia?");

        // Reinício do servidor: novos repositórios e serviço sobre o mesmo banco.
        montar();

        List<ResumoConversa> listaBia = servico.listar(bia);
        assertEquals(3, listaBia.size());
        ResumoConversa resumoPrivada = listaBia.stream()
                .filter(r -> r.conversa().getId() == privada).findFirst().orElseThrow();
        assertEquals(2, resumoPrivada.naoLidas());
        assertEquals("Bia?", resumoPrivada.ultima().getConteudo());

        List<Mensagem> historico = servico.historico(bia, privada, null, 50);
        assertEquals(List.of("Me responde quando puder", "Bia?"),
                historico.stream().map(Mensagem::getConteudo).toList());
        assertEquals(List.of("Reunião às 19h"),
                servico.historico(caio, grupo, null, 50).stream().map(Mensagem::getConteudo).toList());

        servico.marcarLida(bia, privada, historico.get(1).getId());
        assertEquals(0, servico.listar(bia).stream()
                .filter(r -> r.conversa().getId() == privada).findFirst().orElseThrow().naoLidas());
        assertEquals(0, servico.listar(ana).stream()
                .filter(r -> r.conversa().getId() == privada).findFirst().orElseThrow().naoLidas(),
                "As próprias mensagens não contam como não lidas");

        // Paginação: mensagens anteriores a um id.
        assertEquals(List.of("Me responde quando puder"), servico.historico(bia, privada, historico.get(1).getId(), 50)
                .stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void ordemDeveSerAMesmaParaTodosOsMembrosSobConcorrencia() throws Exception {
        Sessao sAna = conectar(ana);
        Sessao sBia = conectar(bia);
        Sessao sCaio = conectar(caio);

        long grupo = servico.criarGrupo(ana, "Concorrência", List.of(bia.getId(), caio.getId())).conversa().getId();
        long privada = servico.abrirPrivada(bia, caio.getId()).conversa().getId();

        ExecutorService executor = Executors.newFixedThreadPool(6);
        List<Usuario> autores = List.of(ana, bia, caio);
        for (int t = 0; t < 6; t++) {
            Usuario autor = autores.get(t % 3);
            int fio = t;
            executor.execute(() -> {
                for (int i = 0; i < 20; i++) {
                    try {
                        servico.enviar(autor, grupo, "g" + fio + "-" + i);
                        if (autor != ana) {
                            servico.enviar(autor, privada, "p" + fio + "-" + i);
                        }
                    } catch (ConversaException e) {
                        throw new AssertionError(e);
                    }
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        List<Long> ordemGravada = servico.historico(ana, grupo, null, 100).stream().map(Mensagem::getId).toList();
        assertEquals(100, ordemGravada.size());

        for (Sessao sessao : List.of(sAna, sBia, sCaio)) {
            List<Long> recebidasNoGrupo;
            synchronized (sessao.recebidas) {
                recebidasNoGrupo = sessao.recebidas.stream()
                        .filter(m -> m.getConversaId() == grupo).map(Mensagem::getId).toList();
            }
            assertEquals(120, recebidasNoGrupo.size());
            List<Long> ordenada = new ArrayList<>(recebidasNoGrupo);
            Collections.sort(ordenada);
            assertEquals(ordenada, recebidasNoGrupo, "Entrega na ordem de gravação");
        }

        assertTrue(sAna.recebidas.stream().noneMatch(m -> m.getConversaId() == privada));
        assertEquals(80, sBia.recebidas.stream().filter(m -> m.getConversaId() == privada).count());
    }

    @Test
    void chatGeralDeveContinuarAbertoATodos() throws Exception {
        Sessao sAna = conectar(ana);
        Sessao legado = conectar(new Usuario("Zoe"));

        servico.enviar(new Usuario("Zoe"), Conversa.ID_GERAL, "Oi do visitante");
        servico.enviar(ana, Conversa.ID_GERAL, "Oi da Ana");

        assertEquals(List.of("Oi do visitante", "Oi da Ana"), sAna.textos());
        assertEquals(List.of("Oi do visitante", "Oi da Ana"), legado.textos());
        assertEquals(2, servico.historico(new Usuario("Zoe"), Conversa.ID_GERAL, null, 10).size());

        List<ResumoConversa> lista = servico.listar(ana);
        assertEquals(Conversa.Tipo.GERAL, lista.get(0).conversa().getTipo());
        assertEquals("Oi da Ana", lista.get(0).ultima().getConteudo());
        assertEquals(DADOS_INVALIDOS, codigo(() -> servico.enviar(ana, Conversa.ID_GERAL, " ")));

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + pasta.resolve("conversas.db"));
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM mensagem WHERE conversa_id = 1")) {
            assertTrue(r.next());
            assertEquals(2, r.getInt(1));
        }
    }

    @Test
    void listaDeUsuariosNaoDeveExporLoginNemIncluirOProprio() throws Exception {
        conectar(bia);
        List<ServicoConversas.UsuarioPublico> usuarios = servico.listarUsuarios(ana);

        assertEquals(List.of("Bia:true", "Caio:false"),
                usuarios.stream().map(u -> u.apelido() + ":" + u.online()).toList());
        assertEquals(CONTA_NECESSARIA, codigo(() -> servico.listarUsuarios(new Usuario("Zoe"))));
        assertFalse(ServicoConversas.UsuarioPublico.class.getRecordComponents().length > 4);
        assertTrue(java.util.Arrays.stream(PerfilUsuario.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("login")), "O perfil interno tem login...");
        assertTrue(java.util.Arrays.stream(ServicoConversas.UsuarioPublico.class.getRecordComponents())
                .noneMatch(c -> c.getName().equals("login")), "...mas a lista pública não");
    }
}
