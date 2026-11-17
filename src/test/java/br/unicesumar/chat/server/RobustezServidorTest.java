package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoEvento;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoRequisicao;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
import br.unicesumar.chat.service.HashSenha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robustez do servidor TCP real sobre SQLite temporário (S1, S2, S4, S5, S8):
 * cliente lento, vagas, prazos, heartbeat, substituição de sessão e spam.
 */
class RobustezServidorTest {

    private static final String SENHA = "senha-segura-1";

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private Servidor servidor;
    private ServerSocket socketServidor;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Cliente> clientes = new CopyOnWriteArrayList<>();
    private long proximoReq = 1;

    private void iniciar(LimitesServidor limites) throws IOException {
        servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("robustez.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), new ProvedorIAFalso(), limites);
        servidor = new Servidor(0, servicos);
        socketServidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(() -> servidor.atenderConexoes(socketServidor));
    }

    @AfterEach
    void encerrar() throws Exception {
        for (Cliente c : clientes) {
            c.close();
        }
        if (socketServidor != null) {
            socketServidor.close();
        }
        if (servidor != null) {
            // As sessões terminam em threads próprias: espera antes de apagar o banco.
            assertTrue(servidor.vagas().aguardarOcupadas(0, Duration.ofSeconds(10)));
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
    }

    /** Cliente TCP bruto (texto no legado, JSON após o login). */
    private final class Cliente implements AutoCloseable {

        final Socket socket;
        final BufferedReader entrada;
        final BufferedWriter saida;
        final List<String> recebidas = new ArrayList<>();

        Cliente(int porta) throws IOException {
            this(porta, 0);
        }

        /** @param bufferRecepcao tamanho do buffer de recepção (0: padrão) */
        Cliente(int porta, int bufferRecepcao) throws IOException {
            socket = new Socket();
            if (bufferRecepcao > 0) {
                socket.setReceiveBufferSize(bufferRecepcao);
            }
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), 5_000);
            socket.setSoTimeout(5_000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            clientes.add(this);
        }

        void boasVindas() throws IOException {
            assertEquals("SISTEMA: Bem-vindo ao Chat Sakura!", ler());
            assertEquals("SISTEMA: Informe seu nome de usuário:", ler());
        }

        void enviar(String linha) throws IOException {
            saida.write(linha);
            saida.newLine();
            saida.flush();
        }

        String ler() throws IOException {
            String linha = entrada.readLine();
            recebidas.add(linha);
            return linha;
        }

        String esperar(Predicate<String> criterio) throws IOException {
            for (int i = 0; i < 5_000; i++) {
                String linha = ler();
                assertNotNull(linha, "Conexão encerrada. Recebidas: " + recebidas);
                if (criterio.test(linha)) {
                    return linha;
                }
            }
            throw new AssertionError("Não recebida. Linhas: " + recebidas);
        }

        ProtocoloJson.Evento esperarEvento(TipoEvento tipo) throws Exception {
            return ProtocoloJson.lerEvento(esperar(l -> l.startsWith("{\"tipo\":\"" + tipo.name() + "\"")));
        }

        /** {@code true} quando o servidor fechou a conexão. */
        boolean encerrada() throws IOException {
            try {
                while (true) {
                    String linha = entrada.readLine();
                    if (linha == null) {
                        return true;
                    }
                    recebidas.add(linha);
                }
            } catch (SocketException e) {
                return true;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private int porta() {
        return socketServidor.getLocalPort();
    }

    private Cliente legado(String nome) throws IOException {
        Cliente cliente = new Cliente(porta());
        cliente.boasVindas();
        cliente.enviar(nome);
        assertEquals("SISTEMA: Conectado como " + nome, cliente.ler());
        cliente.esperar("SISTEMA: ===== FIM DO HISTÓRICO ====="::equals);
        return cliente;
    }

    private void cadastrar(String login, String apelido) throws Exception {
        servicos.autenticacao().cadastrar(login, apelido, SENHA.toCharArray());
    }

    private Cliente entrar(String login) throws Exception {
        Cliente cliente = new Cliente(porta());
        cliente.boasVindas();
        cliente.enviar(ProtocoloSakura.NEGOCIACAO);
        assertEquals(ProtocoloSakura.NEGOCIACAO_ACEITA, cliente.ler());
        cliente.enviar(ProtocoloSakura.comandoEntrar(login, SENHA.toCharArray()));
        assertTrue(ProtocoloSakura.lerResposta(cliente.ler()).sucesso(), cliente.recebidas.toString());
        cliente.esperarEvento(TipoEvento.CONVERSAS);
        cliente.esperarEvento(TipoEvento.HISTORICO);
        return cliente;
    }

    private String enviarJson(long conversaId, String texto) {
        return ProtocoloJson.requisicao(TipoRequisicao.ENVIAR, proximoReq++, "conversaId", conversaId, "texto", texto);
    }

    private static boolean ehMensagemCom(String linha, String texto) {
        return linha.startsWith("{\"tipo\":\"MENSAGEM\"") && linha.contains("\"texto\":\"" + texto + "\"");
    }

    // ------------------------------------------------------------------
    // S2 — vagas e prazos
    // ------------------------------------------------------------------

    @Test
    void limiteGlobalDeveRecusarExcedenteELiberarVagaAoDesconectar() throws Exception {
        iniciar(LimitesServidor.padrao().comConexoes(2, 10));

        // Escopos aninhados: as conexões são abertas na mesma ordem e ficam
        // abertas até o fim do teste; só "primeiro" fecha antes, de propósito.
        Cliente primeiro = new Cliente(porta());
        primeiro.boasVindas();
        try (Cliente segundo = new Cliente(porta())) {
            segundo.boasVindas();

            try (Cliente excedente = new Cliente(porta())) {
                assertEquals(Servidor.MENSAGEM_LOTADO, excedente.ler());
                assertTrue(excedente.encerrada(), "Recusado sem ocupar thread nem vaga");
                assertEquals(2, servidor.vagas().ocupadas());

                primeiro.close();
                assertTrue(servidor.vagas().aguardarOcupadas(1, Duration.ofSeconds(5)),
                        "Vaga liberada após desconexão");

                try (Cliente novo = new Cliente(porta())) {
                    novo.boasVindas();
                    assertEquals(2, servidor.vagas().ocupadas());
                }
            }
        }
    }

    @Test
    void limitePorIpDeveSerAplicado() throws Exception {
        iniciar(LimitesServidor.padrao().comConexoes(50, 2));

        // As duas primeiras conexões continuam abertas enquanto a terceira é recusada.
        try (Cliente primeiro = new Cliente(porta())) {
            primeiro.boasVindas();
            try (Cliente segundo = new Cliente(porta())) {
                segundo.boasVindas();

                try (Cliente terceiro = new Cliente(porta())) {
                    assertEquals(Servidor.MENSAGEM_LOTADO, terceiro.ler());
                    assertTrue(terceiro.encerrada());
                    assertEquals(2, servidor.vagas().ocupadas(InetAddress.getLoopbackAddress().getHostAddress()));
                }
            }
        }
    }

    @Test
    void conexaoParadaNaEntradaDeveSerEncerradaNoPrazo() throws Exception {
        iniciar(LimitesServidor.padrao().comTempos(Duration.ofMillis(300), Duration.ofSeconds(30)));

        // Nunca informa o nome.
        try (Cliente calado = new Cliente(porta())) {
            calado.boasVindas();

            // Começa a negociação v2 e para antes do login.
            try (Cliente negociando = new Cliente(porta())) {
                negociando.boasVindas();
                negociando.enviar(ProtocoloSakura.NEGOCIACAO);
                assertEquals(ProtocoloSakura.NEGOCIACAO_ACEITA, negociando.ler());

                assertEquals(ClienteHandler.MENSAGEM_PRAZO_ENTRADA, calado.ler());
                assertTrue(calado.encerrada());
                assertTrue(negociando.encerrada());
                assertTrue(servidor.vagas().aguardarOcupadas(0, Duration.ofSeconds(5)), "Threads e vagas liberadas");
            }
        }
    }

    @Test
    void entradaConcluidaNoPrazoNaoDeveSerDerrubada() throws Exception {
        iniciar(LimitesServidor.padrao().comTempos(Duration.ofMillis(300), Duration.ofSeconds(30)));
        cadastrar("ana", "Ana");

        Cliente ana = entrar("ana");
        Cliente caio = legado("Caio");

        // Bem depois do prazo de entrada, ambos continuam funcionando.
        try (Cliente calado = new Cliente(porta())) {
            calado.boasVindas();
            assertTrue(calado.encerrada(), "O prazo de entrada passou");

            caio.enviar("ainda aqui");
            ana.esperar(l -> ehMensagemCom(l, "ainda aqui"));
            caio.esperar(l -> l.endsWith("Caio: ainda aqui"));
        }
    }

    // ------------------------------------------------------------------
    // S2 — heartbeat v2 e compatibilidade com o legado
    // ------------------------------------------------------------------

    @Test
    void heartbeatV2DeveManterSessaoQueRespondeEDerrubarAQueNaoResponde() throws Exception {
        iniciar(LimitesServidor.padrao().comTempos(Duration.ofSeconds(30), Duration.ofMillis(200)));
        cadastrar("ana", "Ana");
        cadastrar("bia", "Bia");

        Cliente caio = legado("Caio");
        Cliente ana = entrar("ana");
        Cliente bia = entrar("bia");

        // Ana responde a dois PINGs seguidos e segue usando o chat.
        for (int i = 0; i < 2; i++) {
            ana.esperarEvento(TipoEvento.PING);
            ana.enviar(ProtocoloJson.requisicao(TipoRequisicao.PONG, null));
        }

        // Bia não responde: depois do PING a conexão é encerrada.
        bia.esperarEvento(TipoEvento.PING);
        assertTrue(bia.encerrada(), "Sessão v2 inativa encerrada");

        ana.enviar(enviarJson(1, "sigo aqui"));
        ana.esperar(l -> ehMensagemCom(l, "sigo aqui"));

        // O legado ficou parado o tempo todo, sem heartbeat, e continua conectado.
        caio.esperar(l -> l.endsWith("Ana: sigo aqui"));
        caio.enviar("também estou aqui");
        caio.esperar(l -> l.endsWith("Caio: também estou aqui"));
        assertTrue(caio.recebidas.stream().noneMatch(l -> l.contains("PING")), "Legado nunca recebe PING");
    }

    // ------------------------------------------------------------------
    // S1 — cliente lento e ordem
    // ------------------------------------------------------------------

    /** Servidor só com Chat Geral em memória (sem SQLite), com buffers TCP pequenos. */
    private ChatService servidorEmMemoria(LimitesServidor limites, MensagemRepositoryEmMemoria repositorio,
                                          CountDownLatch encerrados) throws IOException {
        ChatService chat = new ChatService(repositorio, new ClientRegistry(), Clock.systemDefaultZone());
        socketServidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        ServerSocket atual = socketServidor;
        threads.execute(() -> {
            while (!atual.isClosed()) {
                try {
                    Socket s = atual.accept();
                    s.setSendBufferSize(4_096);
                    threads.execute(new ClienteHandler(s, chat, null, null, null, limites, encerrados::countDown));
                } catch (IOException e) {
                    return;
                }
            }
        });
        return chat;
    }

    @Test
    void clienteQueNaoLeDeveSerDerrubadoSemAtrasarOsDemais() throws Exception {
        CountDownLatch encerrados = new CountDownLatch(1);
        ChatService chat = servidorEmMemoria(LimitesServidor.padrao().comCapacidadeFila(50),
                new MensagemRepositoryEmMemoria(), encerrados);

        Cliente ana = legado("Ana");

        // Cliente lento: entra e nunca mais lê nada (o socket fica aberto até o fim do teste).
        try (Cliente lento = new Cliente(porta(), 2_048)) {
            lento.boasVindas();
            lento.enviar("Lento");
            ana.esperar(l -> l.endsWith("Lento se conectou"));

            Usuario bot = new Usuario("Bot");
            String recheio = "x".repeat(400);
            int lotes = 100;
            int porLote = 20;

            // Ana lê tudo; o lento enche o buffer TCP e depois a fila.
            for (int lote = 0; lote < lotes; lote++) {
                for (int i = 0; i < porLote; i++) {
                    chat.enviarMensagem(bot, "m" + (lote * porLote + i) + " " + recheio);
                }
                int ultima = lote * porLote + porLote - 1;
                ana.esperar(l -> l.contains("Bot: m" + ultima + " "));
            }

            assertTrue(encerrados.await(10, TimeUnit.SECONDS), "O cliente lento foi desconectado");
            assertEquals(1, chat.contarClientes(), "Só Ana continua registrada");

            // Ana recebeu todas as mensagens, em ordem.
            List<Integer> numeros = ana.recebidas.stream()
                    .filter(l -> l.contains("Bot: m"))
                    .map(l -> Integer.parseInt(l.substring(l.indexOf("Bot: m") + 6,
                            l.indexOf(' ', l.indexOf("Bot: m") + 6))))
                    .toList();
            assertEquals(lotes * porLote, numeros.size());
            for (int i = 0; i < numeros.size(); i++) {
                assertEquals(i, numeros.get(i));
            }

            // O servidor segue normal depois da queda.
            chat.enviarMensagem(bot, "depois da queda");
            ana.esperar(l -> l.endsWith("Bot: depois da queda"));
        }
    }

    @Test
    void broadcastsConcorrentesDevemChegarNaOrdemDoHistorico() throws Exception {
        MensagemRepositoryEmMemoria repositorio = new MensagemRepositoryEmMemoria();
        ChatService chat = servidorEmMemoria(LimitesServidor.padrao(), repositorio, new CountDownLatch(1));
        Cliente ana = legado("Ana");

        ExecutorService remetentes = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            Usuario autor = new Usuario("Autor" + t);
            remetentes.execute(() -> {
                for (int i = 0; i < 100; i++) {
                    chat.enviarMensagem(autor, "n" + i);
                }
            });
        }
        remetentes.shutdown();
        assertTrue(remetentes.awaitTermination(10, TimeUnit.SECONDS));

        List<String> esperado = repositorio.buscarUltimas(1_000).stream()
                .filter(m -> m.getTipo() == TipoMensagem.MENSAGEM)
                .map(Mensagem::formatar)
                .toList();
        assertEquals(400, esperado.size());

        List<String> recebido = new ArrayList<>();
        while (recebido.size() < esperado.size()) {
            String linha = ana.ler();
            assertNotNull(linha);
            if (linha.contains(": n")) {
                recebido.add(linha);
            }
        }
        assertEquals(esperado, recebido, "Mesma ordem do histórico persistido");
    }

    // ------------------------------------------------------------------
    // S5 — substituição de sessão
    // ------------------------------------------------------------------

    @Test
    void novoLoginDeveSubstituirSessaoAntigaQueNaoEnviaMais() throws Exception {
        iniciar(LimitesServidor.padrao());
        cadastrar("ana", "Ana");
        cadastrar("bia", "Bia");

        Cliente bia = entrar("bia");
        Cliente antiga = entrar("ana");
        bia.esperar(l -> l.contains("\"remetente\":\"Ana\"") && l.contains("\"tipo\":\"ENTRADA\""));

        Cliente nova = entrar("ana");

        antiga.esperar(l -> l.contains("\"SESSAO_SUBSTITUIDA\""));
        // Tentativa da sessão antiga depois de substituída: recusada.
        antiga.enviar(enviarJson(1, "fantasma"));
        assertTrue(antiga.encerrada(), "Sessão antiga fechada pelo servidor");

        nova.enviar(enviarJson(1, "sessao nova"));
        bia.esperar(l -> ehMensagemCom(l, "sessao nova"));
        nova.esperar(l -> ehMensagemCom(l, "sessao nova"));

        assertTrue(bia.recebidas.stream().noneMatch(l -> l.contains("fantasma")));
        assertTrue(servicos.conversas().historico(new Usuario(2L, "Bia"), 1, null, 100).stream()
                .noneMatch(m -> m.getConteudo().equals("fantasma")), "Nada gravado pela sessão substituída");
        assertTrue(bia.recebidas.stream().noneMatch(l -> l.contains("\"tipo\":\"SAIDA\"")),
                "Substituição não gera aviso de saída");
        assertEquals(1, bia.recebidas.stream()
                .filter(l -> l.contains("\"remetente\":\"Ana\"") && l.contains("\"tipo\":\"ENTRADA\"")).count(),
                "Nem uma segunda entrada");
        assertEquals(2, servicos.chat().contarClientes());

        // Reconexões rápidas: a última sessão sempre vale.
        Cliente ultima = nova;
        for (int i = 0; i < 4; i++) {
            Cliente proxima = entrar("ana");
            assertTrue(ultima.encerrada());
            ultima = proxima;
        }
        assertEquals(2, servicos.chat().contarClientes());
        ultima.enviar(enviarJson(1, "a ultima vale"));
        bia.esperar(l -> ehMensagemCom(l, "a ultima vale"));
    }

    // ------------------------------------------------------------------
    // S8 — spam
    // ------------------------------------------------------------------

    @Test
    void excessoDeRequisicoesDeveSerRecusadoComUmAvisoSemDerrubarASessao() throws Exception {
        AtomicLong nanos = new AtomicLong();
        iniciar(LimitesServidor.padrao().comTaxas(
                new LimitesServidor.Taxa(3, 1),
                new LimitesServidor.Taxa(1, 1),
                new LimitesServidor.Taxa(2, 1),
                nanos::get));
        cadastrar("ana", "Ana");

        Cliente ana = entrar("ana");
        for (int i = 0; i < 6; i++) {
            ana.enviar(enviarJson(1, "spam " + i));
        }

        // Uma consulta (outra categoria) responde normalmente depois do spam.
        long req = proximoReq++;
        ana.enviar(ProtocoloJson.requisicao(TipoRequisicao.LISTAR_CONVERSAS, req));
        ana.esperar(l -> l.startsWith("{\"tipo\":\"CONVERSAS\"") && l.contains("\"req\":" + req));

        long entregues = ana.recebidas.stream().filter(l -> l.startsWith("{\"tipo\":\"MENSAGEM\"")
                && l.contains("spam ")).count();
        long avisos = ana.recebidas.stream().filter(l -> l.contains("\"codigo\":\"LIMITE\"")).count();
        assertEquals(3, entregues, "Somente a rajada permitida");
        assertEquals(1, avisos, "Um único aviso, sem repetição excessiva");

        assertEquals(3, servicos.conversas().historico(new Usuario(1L, "Ana"), 1, null, 100).stream()
                .filter(m -> m.getConteudo().startsWith("spam")).count(), "Excesso nem chega ao banco");

        // Criações têm limite próprio.
        nanos.addAndGet(2_000_000_000L);
        ana.enviar(ProtocoloJson.requisicao(TipoRequisicao.CRIAR_PERSONAGEM, proximoReq++,
                "nome", "Naruto", "personalidade", "animado", "contexto", ""));
        ana.esperarEvento(TipoEvento.CONVERSA);
        ana.enviar(ProtocoloJson.requisicao(TipoRequisicao.CRIAR_PERSONAGEM, proximoReq++,
                "nome", "Sasuke", "personalidade", "sério", "contexto", ""));
        assertTrue(ana.esperar(l -> l.contains("\"tipo\":\"ERRO\"")).contains("\"LIMITE\""));
    }

    @Test
    void clienteLegadoDeveSerLimitadoComAvisoEmTexto() throws Exception {
        AtomicLong nanos = new AtomicLong();
        iniciar(LimitesServidor.padrao().comTaxas(
                new LimitesServidor.Taxa(3, 1),
                new LimitesServidor.Taxa(1, 1),
                new LimitesServidor.Taxa(2, 1),
                nanos::get));

        Cliente caio = legado("Caio");
        for (int i = 0; i < 6; i++) {
            caio.enviar("rapido " + i);
        }
        caio.esperar(ClienteHandler.MENSAGEM_LIMITE_LEGADO::equals);

        nanos.addAndGet(60_000_000_000L);
        caio.enviar("depois de esperar");
        caio.esperar(l -> l.endsWith("Caio: depois de esperar"));

        assertEquals(3, caio.recebidas.stream().filter(l -> l.contains("Caio: rapido")).count());
    }

    // ------------------------------------------------------------------
    // S4 — tentativas por IP no servidor real
    // ------------------------------------------------------------------

    @Test
    void ipComMuitasFalhasDeveSerBloqueadoMesmoReconectando() throws Exception {
        iniciar(LimitesServidor.padrao());
        cadastrar("ana", "Ana");

        // Duas conexões com 5 tentativas erradas cada (limite por conexão).
        for (int conexao = 0; conexao < 2; conexao++) {
            // O servidor encerra cada conexão; o socket do teste fecha depois de confirmar isso.
            try (Cliente atacante = new Cliente(porta())) {
                atacante.boasVindas();
                atacante.enviar(ProtocoloSakura.NEGOCIACAO);
                atacante.ler();
                for (int i = 0; i < ProtocoloSakura.TENTATIVAS_MAXIMAS; i++) {
                    atacante.enviar(ProtocoloSakura.comandoEntrar("ana", "errada-1234".toCharArray()));
                    assertFalse(ProtocoloSakura.lerResposta(atacante.ler()).sucesso());
                }
                assertTrue(atacante.encerrada());
            }
        }

        // Reconectar não zera o limite: mesmo a senha certa é recusada por ora.
        try (Cliente terceira = new Cliente(porta())) {
            terceira.boasVindas();
            terceira.enviar(ProtocoloSakura.NEGOCIACAO);
            terceira.ler();
            terceira.enviar(ProtocoloSakura.comandoEntrar("ana", SENHA.toCharArray()));
            ProtocoloSakura.RespostaAutenticacao resposta = ProtocoloSakura.lerResposta(terceira.ler());
            assertEquals("MUITAS_TENTATIVAS", resposta.codigo());
            assertTrue(terceira.encerrada(), "Bloqueado: a conexão é encerrada sem novas tentativas");
        }
    }
}
