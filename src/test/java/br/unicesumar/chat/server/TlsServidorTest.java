package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoRequisicao;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import br.unicesumar.chat.service.HashSenha;
import br.unicesumar.chat.tls.CertificadosTeste;
import br.unicesumar.chat.tls.ContextoTls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Servidor em modo TLS: fluxo completo, limites, handshake e configuração. */
class TlsServidorTest {

    private static final String SENHA = "senha-segura-1";

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private Servidor servidor;
    private ServerSocket socket;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> abertos = new ArrayList<>();
    private long req = 1;

    private void iniciar(LimitesServidor limites) throws Exception {
        servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("tls.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), new ProvedorIAFalso(), limites);
        servidor = new Servidor(0, servicos, TransporteServidor.tls(CertificadosTeste.servidor("servidor.p12"),
                InetAddress.getLoopbackAddress()));
        socket = servidor.abrirSocket();
        threads.execute(() -> servidor.atenderConexoes(socket));
    }

    @AfterEach
    void encerrar() throws Exception {
        for (Socket s : abertos) {
            s.close();
        }
        if (socket != null) {
            socket.close();
            assertTrue(servidor.vagas().aguardarOcupadas(0, Duration.ofSeconds(10)));
        }
        if (servicos != null) {
            assertTrue(servicos.ia().aguardar(10_000));
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
    }

    /** Cliente bruto sobre TLS (cadeia e nome validados como no cliente real). */
    private final class Cliente {
        final SSLSocket conexao;
        final BufferedReader entrada;
        final BufferedWriter saida;
        final List<String> recebidas = new ArrayList<>();
        boolean responderPing = true;

        Cliente() throws Exception {
            SSLContext contexto = CertificadosTeste.cliente();
            conexao = (SSLSocket) contexto.getSocketFactory().createSocket("localhost", socket.getLocalPort());
            abertos.add(conexao);
            ContextoTls.configurarCliente(conexao, contexto);
            conexao.setSoTimeout(10_000);
            conexao.startHandshake();
            entrada = new BufferedReader(new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8));
            saida = new BufferedWriter(new OutputStreamWriter(conexao.getOutputStream(), StandardCharsets.UTF_8));
            assertEquals("SISTEMA: Bem-vindo ao Chat Sakura!", ler());
            ler();
        }

        Cliente conta(String login, boolean cadastrar) throws Exception {
            enviar(ProtocoloSakura.NEGOCIACAO);
            assertEquals(ProtocoloSakura.NEGOCIACAO_ACEITA, ler());
            enviar(cadastrar ? ProtocoloSakura.comandoCadastrar(login, login.toUpperCase(), SENHA.toCharArray())
                    : ProtocoloSakura.comandoEntrar(login, SENHA.toCharArray()));
            assertTrue(ProtocoloSakura.lerResposta(ler()).sucesso(), recebidas.toString());
            esperar(l -> l.startsWith("{\"tipo\":\"HISTORICO\""));
            return this;
        }

        void enviar(String linha) throws IOException {
            saida.write(linha);
            saida.newLine();
            saida.flush();
        }

        String ler() throws IOException {
            while (true) {
                String linha = entrada.readLine();
                assertNotNull(linha, "Conexão encerrada. Recebidas: " + recebidas);
                recebidas.add(linha);
                if (responderPing && linha.startsWith("{\"tipo\":\"PING\"")) {
                    enviar(ProtocoloJson.requisicao(TipoRequisicao.PONG, null));
                    continue;
                }
                return linha;
            }
        }

        String esperar(Predicate<String> criterio) throws IOException {
            for (int i = 0; i < 500; i++) {
                String linha = ler();
                if (criterio.test(linha)) {
                    return linha;
                }
            }
            throw new AssertionError("Não recebida: " + recebidas);
        }

        long pedir(TipoRequisicao tipo, Object... campos) throws IOException {
            long numero = req++;
            enviar(ProtocoloJson.requisicao(tipo, numero, campos));
            return numero;
        }

        ProtocoloJson.Evento resposta(long numero) throws Exception {
            return ProtocoloJson.lerEvento(esperar(l -> l.contains("\"req\":" + numero + ",")
                    || l.contains("\"req\":" + numero + "}")));
        }
    }

    @Test
    void fluxoCompletoDoChatFuncionaSobreTls() throws Exception {
        iniciar(LimitesServidor.padrao());

        Cliente ana = new Cliente().conta("ana", true);
        assertTrue(Set.of("TLSv1.3", "TLSv1.2").contains(ana.conexao.getSession().getProtocol()),
                ana.conexao.getSession().getProtocol());
        Cliente bia = new Cliente().conta("bia", true);

        // Visitante (protocolo de texto) também funciona sobre TLS.
        Cliente caio = new Cliente();
        caio.enviar("Caio");
        assertEquals("SISTEMA: Conectado como Caio", caio.ler());

        ana.pedir(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", "Olá via TLS");
        bia.esperar(l -> l.contains("Olá via TLS"));
        caio.esperar(l -> l.endsWith("ANA: Olá via TLS"));

        long u = ana.pedir(TipoRequisicao.LISTAR_USUARIOS);
        long idBia = ana.resposta(u).usuarios().stream().filter(x -> x.apelido().equals("BIA"))
                .findFirst().orElseThrow().id();
        long privada = ana.resposta(ana.pedir(TipoRequisicao.ABRIR_PRIVADA, "usuarioId", idBia)).conversa().id();
        ana.pedir(TipoRequisicao.ENVIAR, "conversaId", privada, "texto", "Privada segura");
        bia.esperar(l -> l.contains("Privada segura"));
        long h = ana.pedir(TipoRequisicao.HISTORICO, "conversaId", privada);
        assertTrue(ana.resposta(h).mensagens().stream().anyMatch(m -> m.texto().equals("Privada segura")));

        long grupo = ana.resposta(ana.pedir(TipoRequisicao.CRIAR_GRUPO, "nome", "Equipe",
                "participantes", List.of(idBia))).conversa().id();
        ana.pedir(TipoRequisicao.ENVIAR, "conversaId", grupo, "texto", "Grupo seguro");
        bia.esperar(l -> l.contains("Grupo seguro"));

        long ia = ana.resposta(ana.pedir(TipoRequisicao.CRIAR_PERSONAGEM, "nome", "Naruto",
                "personalidade", "animado", "contexto", "")).conversa().id();
        ana.pedir(TipoRequisicao.ENVIAR, "conversaId", ia, "texto", "Oi, Naruto!");
        ana.esperar(l -> l.contains("\"tipo\":\"IA\"") && l.contains(ProvedorIAFalso.PREFIXO.strip()));
        assertEquals(ProtocoloJson.TipoEvento.MEMORIA,
                ana.resposta(ana.pedir(TipoRequisicao.MEMORIA_VER, "conversaId", ia)).tipo());

        // Novo login da Ana (TLS) substitui a sessão anterior.
        Cliente ana2 = new Cliente().conta("ana", false);
        ana.esperar(l -> l.contains("\"SESSAO_SUBSTITUIDA\""));
        ana2.pedir(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", "Nova sessão");
        bia.esperar(l -> l.contains("Nova sessão"));
    }

    @Test
    void heartbeatFuncionaSobreTls() throws Exception {
        iniciar(LimitesServidor.padrao().comTempos(Duration.ofSeconds(30), Duration.ofMillis(300)));
        Cliente ana = new Cliente().conta("ana", true);
        Cliente bia = new Cliente().conta("bia", true);
        ana.responderPing = false;
        bia.responderPing = false;

        // Ana responde a dois PINGs e continua; Bia não responde e é desconectada.
        for (int i = 0; i < 2; i++) {
            ana.esperar(l -> l.startsWith("{\"tipo\":\"PING\""));
            ana.enviar(ProtocoloJson.requisicao(TipoRequisicao.PONG, null));
        }
        bia.esperar(l -> l.startsWith("{\"tipo\":\"PING\""));
        assertNull(bia.entrada.readLine(), "Sem PONG, a sessão TLS é encerrada");

        ana.pedir(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", "Ainda aqui");
        ana.esperar(l -> l.contains("Ainda aqui"));
    }

    @Test
    void limiteDeTaxaContinuaAtivoSobreTls() throws Exception {
        AtomicLong nanos = new AtomicLong();
        iniciar(LimitesServidor.padrao().comTaxas(new LimitesServidor.Taxa(3, 1), new LimitesServidor.Taxa(1, 1),
                new LimitesServidor.Taxa(5, 1), nanos::get));
        Cliente ana = new Cliente().conta("ana", true);
        for (int i = 0; i < 6; i++) {
            ana.pedir(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", "spam " + i);
        }
        long fim = ana.pedir(TipoRequisicao.LISTAR_CONVERSAS);
        ana.resposta(fim);
        assertEquals(3, ana.recebidas.stream().filter(l -> l.startsWith("{\"tipo\":\"MENSAGEM\"")
                && l.contains("spam ")).count());
        assertEquals(1, ana.recebidas.stream().filter(l -> l.contains("\"codigo\":\"LIMITE\"")).count());
    }

    @Test
    void handshakeInterrompidoEncerraNoPrazoELiberaAVaga() throws Exception {
        iniciar(LimitesServidor.padrao().comTempos(Duration.ofMillis(300), Duration.ofSeconds(30)));

        // Conecta por TCP e não inicia a negociação TLS.
        Socket parado = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort());
        abertos.add(parado);
        parado.setSoTimeout(5_000);
        // O servidor só envia o alerta TLS de encerramento (registro 21) e fecha.
        byte[] recebido = parado.getInputStream().readAllBytes();
        assertTrue(recebido.length == 0 || recebido[0] == 21, "Somente um alerta TLS, se algo");
        assertFalse(new String(recebido, StandardCharsets.UTF_8).contains("SISTEMA"));
        assertTrue(servidor.vagas().aguardarOcupadas(0, Duration.ofSeconds(5)));

        // Um cliente TLS normal segue sendo atendido.
        new Cliente().conta("ana", true);
    }

    @Test
    void clienteTcpPuroNaPortaTlsNaoRecebeNadaLegivelNemEntra() throws Exception {
        iniciar(LimitesServidor.padrao());

        Socket puro = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort());
        abertos.add(puro);
        puro.setSoTimeout(5_000);
        puro.getOutputStream().write("Ana\nSAKURA 2\n".getBytes(StandardCharsets.UTF_8));
        puro.getOutputStream().flush();

        InputStream entrada = puro.getInputStream();
        byte[] recebido = entrada.readAllBytes();
        String texto = new String(recebido, StandardCharsets.UTF_8);
        assertFalse(texto.contains("SISTEMA"), "Nenhuma linha do protocolo em texto claro");
        assertFalse(texto.contains("Bem-vindo"));
        assertTrue(servidor.vagas().aguardarOcupadas(0, Duration.ofSeconds(5)));
        assertEquals(0, servicos.chat().contarClientes(), "Ninguém entrou");
    }

    @Test
    void somenteTls12E13SaoAceitos() throws Exception {
        iniciar(LimitesServidor.padrao());
        List<String> habilitados = List.of(((SSLServerSocket) socket).getEnabledProtocols());
        assertFalse(habilitados.isEmpty());
        assertTrue(Set.of("TLSv1.3", "TLSv1.2").containsAll(habilitados), habilitados.toString());

        SSLContext contexto = CertificadosTeste.cliente();
        SSLSocket antigo = (SSLSocket) contexto.getSocketFactory().createSocket("localhost", socket.getLocalPort());
        abertos.add(antigo);
        antigo.setSoTimeout(5_000);
        antigo.setEnabledProtocols(new String[] {"TLSv1.1"});
        assertThrows(IOException.class, antigo::startHandshake, "TLS 1.1 é recusado");
    }

    @Test
    void configuracaoDoAmbienteExigeTlsOuModoExplicito() throws Exception {
        IllegalStateException semCertificado = assertThrows(IllegalStateException.class,
                () -> TransporteServidor.doAmbiente(Map.of()));
        assertTrue(semCertificado.getMessage().contains("CHAT_SAKURA_TLS_KEYSTORE"), semCertificado.getMessage());

        IllegalStateException senhaErrada = assertThrows(IllegalStateException.class,
                () -> TransporteServidor.doAmbiente(Map.of(
                        "CHAT_SAKURA_TLS_KEYSTORE", CertificadosTeste.arquivo("servidor.p12").toString(),
                        "CHAT_SAKURA_TLS_KEYSTORE_SENHA", "senha-errada-123")));
        assertFalse(senhaErrada.getMessage().contains("senha-errada-123"), "A senha nunca aparece na mensagem");

        TransporteServidor tls = TransporteServidor.doAmbiente(Map.of(
                "CHAT_SAKURA_TLS_KEYSTORE", CertificadosTeste.arquivo("servidor.p12").toString(),
                "CHAT_SAKURA_TLS_KEYSTORE_SENHA", CertificadosTeste.SENHA));
        assertTrue(tls.seguro());
        assertNull(tls.endereco(), "TLS: todas as interfaces (uso em rede)");
        assertTrue(tls.descricao().startsWith("TLS ("));

        TransporteServidor dev = TransporteServidor.doAmbiente(Map.of("CHAT_SAKURA_MODO", "tcp-desenvolvimento"));
        assertFalse(dev.seguro());
        assertTrue(dev.endereco().isLoopbackAddress(), "Sem TLS: somente loopback por padrão");
        assertTrue(dev.descricao().contains("SEM TLS"));
        try (ServerSocket aberto = dev.abrir(0)) {
            assertTrue(aberto.getInetAddress().isLoopbackAddress());
        }

        assertThrows(IllegalStateException.class,
                () -> TransporteServidor.doAmbiente(Map.of("CHAT_SAKURA_MODO", "texto")));
    }
}
