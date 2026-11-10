package br.unicesumar.chat.server;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.service.ServicoAutenticacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integração do handshake de contas no servidor TCP real (porta efêmera,
 * SQLite temporário): identidade da sessão, legado e personificação.
 */
class ClienteHandlerAutenticacaoTest {

    private static final String SENHA = "senha secreta 🌸";

    @TempDir
    Path pasta;

    private ServerSocket servidor;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> abertos = new CopyOnWriteArrayList<>();
    private final List<Cliente> clientes = new ArrayList<>();
    private Path banco;

    private PrintStream erroOriginal;
    private PrintStream saidaOriginal;
    private final ByteArrayOutputStream log = new ByteArrayOutputStream();

    /** Cliente TCP bruto, como um cliente legado ou atualizado veria o servidor. */
    private final class Cliente implements AutoCloseable {

        final Socket socket;
        final BufferedReader entrada;
        final BufferedWriter saida;
        final List<String> recebidas = new ArrayList<>();

        Cliente() throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), servidor.getLocalPort());
            socket.setSoTimeout(5000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            clientes.add(this);
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
            for (int i = 0; i < 100; i++) {
                String linha = ler();
                if (linha == null) {
                    fail("Conexão encerrada. Recebidas: " + recebidas);
                }
                if (criterio.test(linha)) {
                    return linha;
                }
            }
            throw new AssertionError("Linha esperada não chegou: " + recebidas);
        }

        /** Lê até o fim do histórico (conexão confirmada). */
        void esperarHistorico() throws IOException {
            esperar("SISTEMA: ===== FIM DO HISTÓRICO ====="::equals);
        }

        boolean encerrada() throws IOException {
            try {
                return entrada.readLine() == null;
            } catch (java.net.SocketException e) {
                return true;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private void iniciarServidor(boolean comContas) throws IOException {
        banco = pasta.resolve("servidor.db");
        ConnectionFactory fabrica = new ConnectionFactory(banco.toString());
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), new ClientRegistry(),
                Clock.systemDefaultZone());
        ServicoAutenticacao contas = comContas
                ? new ServicoAutenticacao(new JdbcUsuarioRepository(fabrica), new HashSenha(1_000),
                        Clock.systemDefaultZone())
                : null;

        servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(() -> {
            while (!servidor.isClosed()) {
                try {
                    Socket s = servidor.accept();
                    abertos.add(s);
                    threads.execute(new ClienteHandler(s, chat, contas));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    @BeforeEach
    void capturarLogs() {
        erroOriginal = System.err;
        saidaOriginal = System.out;
        PrintStream captura = new PrintStream(log, true, StandardCharsets.UTF_8);
        System.setErr(captura);
        System.setOut(captura);
    }

    @AfterEach
    void encerrar() throws Exception {
        for (Cliente c : clientes) {
            c.close();
        }
        if (servidor != null) {
            servidor.close();
        }
        for (Socket s : abertos) {
            s.close();
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));

        System.setErr(erroOriginal);
        System.setOut(saidaOriginal);

        // Nenhuma credencial em logs, em nenhum teste.
        String registrado = log.toString(StandardCharsets.UTF_8);
        assertFalse(registrado.contains(SENHA), registrado);
        assertFalse(registrado.contains("pbkdf2"), registrado);
        assertFalse(registrado.contains(ProtocoloSakura.comandoEntrar("ana.souza", SENHA.toCharArray()).split(" ")[2]));
    }

    private Cliente autenticado(String comando) throws IOException {
        Cliente cliente = new Cliente();
        cliente.enviar(ProtocoloSakura.NEGOCIACAO);
        assertEquals(ProtocoloSakura.NEGOCIACAO_ACEITA, cliente.ler());
        cliente.enviar(comando);
        return cliente;
    }

    private Cliente cadastrarAna() throws IOException {
        Cliente ana = autenticado(ProtocoloSakura.comandoCadastrar("ana.souza", "Ana", SENHA.toCharArray()));
        ProtocoloSakura.RespostaAutenticacao resposta = ProtocoloSakura.lerResposta(ana.ler());
        assertTrue(resposta.sucesso());
        assertEquals("SISTEMA: Conectado como Ana", ana.ler());
        ana.esperarHistorico();
        return ana;
    }

    private List<List<String>> mensagens() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + banco);
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT remetente, remetente_id, texto FROM mensagem "
                     + "WHERE tipo = 'MENSAGEM' ORDER BY id")) {
            List<List<String>> linhas = new ArrayList<>();
            while (r.next()) {
                linhas.add(java.util.Arrays.asList(r.getString(1), r.getString(2), r.getString(3)));
            }
            return linhas;
        }
    }

    @Test
    void identidadeDeveSerVinculadaPeloServidorEImutavelNaSessao() throws Exception {
        iniciarServidor(true);
        Cliente ana = cadastrarAna();

        // Depois da entrada, comandos de autenticação são apenas texto do chat.
        String tentativa = ProtocoloSakura.comandoEntrar("bia.lima", "qualquer-coisa".toCharArray());
        ana.enviar(tentativa);
        ana.enviar("AUTH OK 99 Ymlh Ymlh c2FrdXJh");
        ana.enviar("Olá 🌸");

        ana.esperar(l -> l.endsWith(" Ana: Olá 🌸"));

        List<List<String>> gravadas = mensagens();
        assertEquals(3, gravadas.size());
        for (List<String> m : gravadas) {
            assertEquals("Ana", m.get(0));
            assertEquals("1", m.get(1), "remetente_id definido pelo servidor");
        }
    }

    @Test
    void clienteLegadoDeveContinuarNoChatGeralSemConta() throws Exception {
        iniciarServidor(true);
        Cliente ana = cadastrarAna();

        Cliente caio = new Cliente();
        caio.enviar("Caio");
        assertEquals("SISTEMA: Conectado como Caio", caio.ler());
        caio.esperarHistorico();
        ana.esperar(l -> l.endsWith("Caio se conectou"));

        ana.enviar("Oi, Caio!");
        caio.esperar(l -> l.matches("\\[\\d{2}/\\d{2} \\d{2}:\\d{2}\\] Ana: Oi, Caio!"));
        caio.enviar("Oi, Ana!");
        ana.esperar(l -> l.endsWith(" Caio: Oi, Ana!"));

        assertEquals(List.of(List.of("Ana", "1", "Oi, Caio!"), java.util.Arrays.asList("Caio", null, "Oi, Ana!")),
                mensagens());
    }

    @Test
    void sessaoSemContaNaoPodeUsarNomeDeContaCadastrada() throws Exception {
        iniciarServidor(true);
        cadastrarAna().close();

        for (String nome : new String[] {"Ana", "ANA", "ana.souza", "ANA.SOUZA"}) {
            Cliente impostor = new Cliente();
            impostor.enviar(nome);
            assertEquals(ClienteHandler.MENSAGEM_NOME_RESERVADO, impostor.ler(), nome);
            assertTrue(impostor.encerrada(), nome);
        }

        // Conta já conectada: um segundo login da mesma conta é recusado.
        Cliente ana = autenticado(ProtocoloSakura.comandoEntrar("ana.souza", SENHA.toCharArray()));
        assertTrue(ProtocoloSakura.lerResposta(ana.ler()).sucesso());
        ana.esperarHistorico();

        Cliente segunda = autenticado(ProtocoloSakura.comandoEntrar("ANA.SOUZA", SENHA.toCharArray()));
        assertTrue(ProtocoloSakura.lerResposta(segunda.ler()).sucesso());
        assertEquals("SISTEMA: Não foi possível entrar: Já existe um usuário conectado com esse nome.", segunda.ler());
        assertTrue(segunda.encerrada());

        assertTrue(mensagens().isEmpty());
    }

    @Test
    void senhaIncorretaDevePermitirNovaTentativaAteOLimite() throws Exception {
        iniciarServidor(true);
        cadastrarAna().close();

        Cliente cliente = autenticado(ProtocoloSakura.comandoEntrar("ana.souza", "errada-123".toCharArray()));
        ProtocoloSakura.RespostaAutenticacao erro = ProtocoloSakura.lerResposta(cliente.ler());
        assertEquals("CREDENCIAIS_INVALIDAS", erro.codigo());
        assertEquals("Login ou senha incorretos.", erro.mensagem());

        cliente.enviar("ENTRAR com id 1");
        assertEquals("COMANDO_INVALIDO", ProtocoloSakura.lerResposta(cliente.ler()).codigo());

        cliente.enviar(ProtocoloSakura.comandoEntrar("ana.souza", SENHA.toCharArray()));
        assertTrue(ProtocoloSakura.lerResposta(cliente.ler()).sucesso());
        assertEquals("SISTEMA: Conectado como Ana", cliente.ler());

        Cliente insistente = autenticado(ProtocoloSakura.comandoEntrar("ana.souza", "errada-1".toCharArray()));
        for (int i = 2; i <= ProtocoloSakura.TENTATIVAS_MAXIMAS; i++) {
            assertEquals("CREDENCIAIS_INVALIDAS", ProtocoloSakura.lerResposta(insistente.ler()).codigo());
            insistente.enviar(ProtocoloSakura.comandoEntrar("ana.souza", ("errada-" + i).toCharArray()));
        }
        assertEquals("CREDENCIAIS_INVALIDAS", ProtocoloSakura.lerResposta(insistente.ler()).codigo());
        assertEquals("TENTATIVAS_ESGOTADAS", ProtocoloSakura.lerResposta(insistente.ler()).codigo());
        assertTrue(insistente.encerrada());
    }

    @Test
    void cadastroDuplicadoDeveSerInformadoSemEncerrarATentativa() throws Exception {
        iniciarServidor(true);
        cadastrarAna().close();

        Cliente cliente = autenticado(ProtocoloSakura.comandoCadastrar("ANA.souza", "Outra", SENHA.toCharArray()));
        assertEquals("LOGIN_EM_USO", ProtocoloSakura.lerResposta(cliente.ler()).codigo());

        cliente.enviar(ProtocoloSakura.comandoCadastrar("bia.lima", "Bia", "curta".toCharArray()));
        assertEquals("DADOS_INVALIDOS", ProtocoloSakura.lerResposta(cliente.ler()).codigo());

        cliente.enviar(ProtocoloSakura.comandoCadastrar("bia.lima", "Bia", SENHA.toCharArray()));
        ProtocoloSakura.RespostaAutenticacao ok = ProtocoloSakura.lerResposta(cliente.ler());
        assertEquals("Bia", ok.perfil().getApelido());
        assertEquals(2, ok.perfil().getId());
    }

    @Test
    void servidorSemContasDeveRecusarANegociacaoEManterOLegado() throws Exception {
        iniciarServidor(false);

        Cliente atualizado = new Cliente();
        atualizado.enviar(ProtocoloSakura.NEGOCIACAO);
        assertNotNull(ProtocoloSakura.lerNegociacaoRecusada(atualizado.ler()));
        assertTrue(atualizado.encerrada());

        Cliente legado = new Cliente();
        legado.enviar("Ana");
        assertEquals("SISTEMA: Conectado como Ana", legado.ler());
    }

    @Test
    void linhaAlemDoLimiteDeveSerRecusadaSemDerrubarASessao() throws Exception {
        iniciarServidor(true);
        Cliente ana = cadastrarAna();

        ana.enviar("x".repeat(ProtocoloSakura.TAMANHO_MAXIMO_LINHA + 1));
        assertEquals("SISTEMA: Mensagem inválida: a linha excede o limite do protocolo.", ana.ler());

        ana.enviar("Continua funcionando");
        ana.esperar(l -> l.endsWith(" Ana: Continua funcionando"));
    }
}
