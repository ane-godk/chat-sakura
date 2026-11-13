package br.unicesumar.chat.service;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.MensagemFixada;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pesquisa e mensagens fixadas com o banco real (SQLite temporário): acesso,
 * filtros, paginação, limite de fixações (inclusive concorrente),
 * persistência e exclusões.
 */
class ServicoMensagensTest {

    @TempDir
    Path pasta;

    private ConnectionFactory fabrica;
    private Servidor.Servicos servicos;
    private ServicoMensagens mensagens;
    private Usuario ana;
    private Usuario bia;
    private Usuario caio;
    private long privadaAnaBia;
    private long grupo;

    private Servidor.Servicos montar() {
        return Servidor.Servicos.montar(fabrica, new HashSenha(1_000),
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC")), new ProvedorIAFalso());
    }

    private Usuario conta(String login, String apelido) throws Exception {
        return new Usuario(servicos.autenticacao().cadastrar(login, apelido, "senha-segura-1".toCharArray()).getId(),
                apelido);
    }

    @BeforeEach
    void preparar() throws Exception {
        fabrica = new ConnectionFactory(pasta.resolve("mensagens.db").toString());
        servicos = montar();
        mensagens = servicos.mensagens();
        ana = conta("ana", "Ana");
        bia = conta("bia", "Bia");
        caio = conta("caio", "Caio");
        privadaAnaBia = servicos.conversas().abrirPrivada(ana, bia.getId()).conversa().getId();
        grupo = servicos.conversas().criarGrupo(ana, "Jardim", List.of(bia.getId())).conversa().getId();
    }

    private Mensagem enviar(Usuario quem, long conversa, String texto) throws Exception {
        return servicos.conversas().enviar(quem, conversa, texto);
    }

    private static List<String> textos(ServicoMensagens.Pagina pagina) {
        return pagina.resultados().stream().map(e -> e.mensagem().getConteudo()).toList();
    }

    // ------------------------------------------------------------------ pesquisa

    @Test
    void pesquisaEncontraNoHistoricoPersistidoSemDiferenciarAcentosEMaiusculas() throws Exception {
        enviar(ana, privadaAnaBia, "Vamos à REUNIÃO amanhã?");
        enviar(bia, privadaAnaBia, "Sim! reuniao às 18h");
        enviar(bia, privadaAnaBia, "Outra coisa");

        ServicoMensagens.Pagina pagina = mensagens.pesquisar(ana, privadaAnaBia, "Reunião", null, null, null,
                null, 20);

        assertEquals(List.of("Sim! reuniao às 18h", "Vamos à REUNIÃO amanhã?"), textos(pagina),
                "Mais recentes primeiro");
        assertFalse(pagina.temMais());
        ServicoMensagens.Encontrada primeira = pagina.resultados().get(0);
        assertEquals("reuniao", primeira.trecho().texto().substring(primeira.trecho().inicio(),
                primeira.trecho().fim()));
        assertEquals("Bia", primeira.mensagem().getRemetente().getNome());
    }

    @Test
    void pesquisaSemResultados() throws Exception {
        enviar(ana, grupo, "Olá, jardim");
        assertTrue(mensagens.pesquisar(bia, grupo, "orquídea", null, null, null, null, 20).resultados().isEmpty());
    }

    @Test
    void pesquisaComUnicodeEmojiETermoLiteral() throws Exception {
        enviar(ana, grupo, "Festa 🌸 さくら no sábado");
        enviar(ana, grupo, "100% de desconto_total");
        assertEquals(1, mensagens.pesquisar(bia, grupo, "さくら", null, null, null, null, 20).resultados().size());
        assertEquals(1, mensagens.pesquisar(bia, grupo, "🌸 さ", null, null, null, null, 20).resultados().size());
        assertEquals(1, mensagens.pesquisar(bia, grupo, "SABADO", null, null, null, null, 20).resultados().size());
        // % e _ não são curingas.
        assertEquals(1, mensagens.pesquisar(bia, grupo, "0% de", null, null, null, null, 20).resultados().size());
        assertEquals(0, mensagens.pesquisar(bia, grupo, "_%", null, null, null, null, 20).resultados().size());
        assertEquals(1, mensagens.pesquisar(bia, grupo, "o_t", null, null, null, null, 20).resultados().size());
    }

    @Test
    void paginacaoSemRepeticoesNemLacunas() throws Exception {
        for (int i = 1; i <= 23; i++) {
            enviar(i % 2 == 0 ? ana : bia, grupo, "nota " + i);
        }
        enviar(ana, grupo, "sem o termo");

        List<String> todas = new ArrayList<>();
        Long antesDe = null;
        int paginas = 0;
        ServicoMensagens.Pagina pagina;
        do {
            pagina = mensagens.pesquisar(ana, grupo, "nota", null, null, null, antesDe, 10);
            paginas++;
            todas.addAll(textos(pagina));
            antesDe = pagina.resultados().get(pagina.resultados().size() - 1).mensagem().getId();
        } while (pagina.temMais());

        assertEquals(3, paginas);
        assertEquals(23, todas.size());
        assertEquals(23, new HashSet<>(todas).size(), "Sem duplicatas");
        assertEquals("nota 23", todas.get(0));
        assertEquals("nota 1", todas.get(22));
    }

    @Test
    void filtrosPorRemetenteEPeriodo() throws Exception {
        enviar(ana, grupo, "plano da Ana");
        enviar(bia, grupo, "plano da Bia");

        assertEquals(List.of("plano da Bia"),
                textos(mensagens.pesquisar(ana, grupo, "plano", bia.getId(), null, null, null, 20)));

        LocalDate hoje = LocalDate.of(2026, 11, 13);
        assertEquals(2, mensagens.pesquisar(ana, grupo, "plano", null, hoje, hoje, null, 20).resultados().size());
        assertEquals(0, mensagens.pesquisar(ana, grupo, "plano", null, hoje.plusDays(1), null, null, 20)
                .resultados().size());
        assertEquals(0, mensagens.pesquisar(ana, grupo, "plano", null, null, hoje.minusDays(1), null, 20)
                .resultados().size());
        ConversaException invertido = assertThrows(ConversaException.class,
                () -> mensagens.pesquisar(ana, grupo, "plano", null, hoje, hoje.minusDays(1), null, 20));
        assertEquals(ConversaException.Codigo.DADOS_INVALIDOS, invertido.getCodigo());
    }

    @Test
    void pesquisaRespeitaAcessoENaoRevelaConversasAlheias() throws Exception {
        enviar(ana, privadaAnaBia, "segredo da dupla");

        ConversaException alheia = assertThrows(ConversaException.class,
                () -> mensagens.pesquisar(caio, privadaAnaBia, "segredo", null, null, null, null, 20));
        ConversaException inexistente = assertThrows(ConversaException.class,
                () -> mensagens.pesquisar(caio, 987_654, "segredo", null, null, null, null, 20));
        assertEquals(ConversaException.Codigo.NAO_ENCONTRADA, alheia.getCodigo());
        assertEquals(inexistente.getMessage(), alheia.getMessage(), "Mesma resposta: não revela a conversa");

        // Pesquisar no Geral nunca encontra mensagens de outras conversas.
        assertTrue(mensagens.pesquisar(caio, Conversa.ID_GERAL, "segredo", null, null, null, null, 20)
                .resultados().isEmpty());
        assertEquals(ConversaException.Codigo.CONTA_NECESSARIA, assertThrows(ConversaException.class,
                () -> mensagens.pesquisar(new Usuario("Visitante"), Conversa.ID_GERAL, "oi", null, null, null,
                        null, 20)).getCodigo());
        assertEquals(ConversaException.Codigo.DADOS_INVALIDOS, assertThrows(ConversaException.class,
                () -> mensagens.pesquisar(ana, grupo, "a", null, null, null, null, 20)).getCodigo());
    }

    @Test
    void avisosDePresencaNaoEntramNaPesquisa() throws Exception {
        new br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository(fabrica).salvar(new Mensagem(null,
                new Usuario("Caio"), "Caio se conectou", java.time.LocalDateTime.of(2026, 11, 13, 12, 0),
                br.unicesumar.chat.model.TipoMensagem.ENTRADA, Conversa.ID_GERAL));
        servicos.chat().enviarMensagem(new Usuario(ana.getId(), "Ana"), "Caio chegou!", null);
        List<String> encontrados = textos(mensagens.pesquisar(ana, Conversa.ID_GERAL, "caio", null, null, null,
                null, 20));
        assertEquals(List.of("Caio chegou!"), encontrados);
    }

    // ------------------------------------------------------------------ fixadas

    @Test
    void fixarEDesafixarNaPrivadaPorQualquerParticipante() throws Exception {
        Mensagem m = enviar(ana, privadaAnaBia, "Endereço: Rua das Flores, 10");

        List<MensagemFixada> lista = mensagens.fixar(bia, privadaAnaBia, m.getId());
        assertEquals(1, lista.size());
        assertEquals("Bia", lista.get(0).fixadaPor());
        assertEquals(m.getConteudo(), mensagens.fixadas(ana, privadaAnaBia).get(0).mensagem().getConteudo());

        assertEquals(1, mensagens.fixar(ana, privadaAnaBia, m.getId()).size(), "Repetir não duplica");
        assertTrue(mensagens.desafixar(ana, privadaAnaBia, m.getId()).isEmpty());

        // A mensagem original continua no histórico.
        assertEquals(m.getConteudo(), servicos.conversas().historico(ana, privadaAnaBia, null, 10).stream()
                .filter(x -> x.getId().equals(m.getId())).findFirst().orElseThrow().getConteudo());
    }

    @Test
    void noGrupoSoAAdministracaoFixaMasTodosVeem() throws Exception {
        Mensagem m = enviar(bia, grupo, "Regras do grupo");

        ConversaException membro = assertThrows(ConversaException.class,
                () -> mensagens.fixar(bia, grupo, m.getId()));
        assertEquals(ConversaException.Codigo.SEM_PERMISSAO, membro.getCodigo());

        mensagens.fixar(ana, grupo, m.getId());   // Ana criou o grupo: ADMIN
        assertEquals(1, mensagens.fixadas(bia, grupo).size());
        assertEquals(ConversaException.Codigo.SEM_PERMISSAO, assertThrows(ConversaException.class,
                () -> mensagens.desafixar(bia, grupo, m.getId())).getCodigo());

        Conversa conversa = servicos.conversas().buscarConversa(grupo).orElseThrow();
        assertTrue(ServicoMensagens.podeFixar(conversa, ana.getId()));
        assertFalse(ServicoMensagens.podeFixar(conversa, bia.getId()));
    }

    @Test
    void naoParticipanteNaoVeNemFixaEGeralNaoTemFixadas() throws Exception {
        Mensagem m = enviar(ana, privadaAnaBia, "só nossa");

        for (Callable<?> tentativa : List.<Callable<?>>of(
                () -> mensagens.fixadas(caio, privadaAnaBia),
                () -> mensagens.fixar(caio, privadaAnaBia, m.getId()),
                () -> mensagens.desafixar(caio, privadaAnaBia, m.getId()))) {
            ConversaException e = assertThrows(ConversaException.class, tentativa::call);
            assertEquals(ConversaException.Codigo.NAO_ENCONTRADA, e.getCodigo());
        }

        Mensagem geral = servicos.chat().enviarMensagem(new Usuario(ana.getId(), "Ana"), "aviso geral", null);
        assertEquals(ConversaException.Codigo.SEM_PERMISSAO, assertThrows(ConversaException.class,
                () -> mensagens.fixar(ana, Conversa.ID_GERAL, geral.getId())).getCodigo());
        assertTrue(mensagens.fixadas(caio, Conversa.ID_GERAL).isEmpty());

        // Mensagem de outra conversa não pode ser fixada nesta.
        Mensagem doGrupo = enviar(ana, grupo, "do grupo");
        assertEquals(ConversaException.Codigo.NAO_ENCONTRADA, assertThrows(ConversaException.class,
                () -> mensagens.fixar(ana, privadaAnaBia, doGrupo.getId())).getCodigo());
    }

    @Test
    void limiteDeDezFixacoesPorConversa() throws Exception {
        List<Mensagem> enviadas = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            enviadas.add(enviar(ana, grupo, "item " + i));
        }
        for (int i = 0; i < 10; i++) {
            mensagens.fixar(ana, grupo, enviadas.get(i).getId());
        }
        ConversaException limite = assertThrows(ConversaException.class,
                () -> mensagens.fixar(ana, grupo, enviadas.get(10).getId()));
        assertEquals(ConversaException.Codigo.LIMITE, limite.getCodigo());

        // Outra conversa tem o próprio limite.
        Mensagem privada = enviar(ana, privadaAnaBia, "na privada");
        assertEquals(1, mensagens.fixar(ana, privadaAnaBia, privada.getId()).size());

        mensagens.desafixar(ana, grupo, enviadas.get(0).getId());
        assertEquals(10, mensagens.fixar(ana, grupo, enviadas.get(10).getId()).size());
    }

    @Test
    void limiteValeComPedidosConcorrentesEmServidoresDiferentes() throws Exception {
        List<Mensagem> enviadas = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            enviadas.add(enviar(ana, grupo, "concorrente " + i));
        }
        // Dois "servidores" sobre o mesmo arquivo: o bloqueio em memória não basta, vale a transação.
        ServicoMensagens outro = montar().mensagens();

        ExecutorService threads = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> pedidos = new ArrayList<>();
        for (int i = 0; i < enviadas.size(); i++) {
            ServicoMensagens servico = i % 2 == 0 ? mensagens : outro;
            long id = enviadas.get(i).getId();
            pedidos.add(threads.submit(() -> {
                try {
                    servico.fixar(ana, grupo, id);
                    return true;
                } catch (ConversaException e) {
                    assertEquals(ConversaException.Codigo.LIMITE, e.getCodigo());
                    return false;
                }
            }));
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(30, TimeUnit.SECONDS));

        int aceitos = 0;
        for (Future<Boolean> pedido : pedidos) {
            aceitos += pedido.get() ? 1 : 0;
        }
        assertEquals(MensagemFixada.LIMITE_POR_CONVERSA, aceitos);
        assertEquals(MensagemFixada.LIMITE_POR_CONVERSA, mensagens.fixadas(ana, grupo).size());
    }

    @Test
    void fixacoesEPesquisaPersistemAposReinicio() throws Exception {
        Mensagem m = enviar(ana, grupo, "Fixada antes do reinício");
        mensagens.fixar(ana, grupo, m.getId());

        ServicoMensagens reiniciado = montar().mensagens();
        assertEquals(List.of(m.getId()), reiniciado.fixadas(bia, grupo).stream()
                .map(f -> f.mensagem().getId()).toList());
        assertEquals(1, reiniciado.pesquisar(bia, grupo, "reinicio", null, null, null, null, 20)
                .resultados().size());
    }

    @Test
    void excluirMensagemOuConversaRemoveAsFixacoes() throws Exception {
        Mensagem a = enviar(ana, grupo, "apagar esta");
        Mensagem b = enviar(ana, grupo, "manter esta");
        Mensagem c = enviar(ana, privadaAnaBia, "conversa inteira");
        mensagens.fixar(ana, grupo, a.getId());
        mensagens.fixar(ana, grupo, b.getId());
        mensagens.fixar(ana, privadaAnaBia, c.getId());

        try (Connection conexao = fabrica.abrirConexao(); Statement s = conexao.createStatement()) {
            conexao.setAutoCommit(true);
            s.executeUpdate("DELETE FROM mensagem WHERE id = " + a.getId());
            s.executeUpdate("DELETE FROM conversa WHERE id = " + privadaAnaBia);
        }

        assertEquals(List.of(b.getId()), mensagens.fixadas(ana, grupo).stream()
                .map(f -> f.mensagem().getId()).toList());
        try (Connection conexao = fabrica.abrirConexao(); Statement s = conexao.createStatement();
             var r = s.executeQuery("SELECT count(*) FROM mensagem_fixada WHERE conversa_id = " + privadaAnaBia)) {
            r.next();
            assertEquals(0, r.getInt(1));
        }
    }

    @Test
    void fixacaoSobreviveAContaDeQuemFixouSerApagada() throws Exception {
        Mensagem m = enviar(bia, privadaAnaBia, "lembrete");
        mensagens.fixar(bia, privadaAnaBia, m.getId());
        try (Connection conexao = fabrica.abrirConexao(); Statement s = conexao.createStatement()) {
            s.executeUpdate("UPDATE mensagem_fixada SET fixada_por = NULL");
        }
        MensagemFixada f = mensagens.fixadas(ana, privadaAnaBia).get(0);
        assertNull(f.fixadaPor());
        assertEquals("lembrete", f.mensagem().getConteudo());
        Set<Long> ids = new HashSet<>();
        mensagens.fixadas(ana, privadaAnaBia).forEach(x -> ids.add(x.mensagem().getId()));
        assertEquals(Set.of(m.getId()), ids);
    }
}
