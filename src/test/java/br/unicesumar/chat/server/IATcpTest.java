package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoRequisicao;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** IA pelo protocolo v2: reconexão durante a geração e memória (ver/apagar) só para o dono. */
class IATcpTest {

    private static final String SENHA = "senha-segura-1";

    @TempDir
    Path pasta;

    private Servidor.Servicos servicos;
    private Servidor servidor;
    private ServerSocket socket;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> clientes = new ArrayList<>();
    private final CountDownLatch gerando = new CountDownLatch(1);
    private final CountDownLatch liberar = new CountDownLatch(1);
    private long req = 1;

    private void iniciar() throws Exception {
        ProvedorIA lento = new ProvedorIA() {
            @Override
            public String gerar(PedidoIA pedido) {
                if (pedido.finalidade() == Finalidade.RESPOSTA) {
                    gerando.countDown();
                    try {
                        liberar.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return "Dattebayo!";
            }

            @Override public String descricao() { return "teste"; }
        };
        servicos = Servidor.Servicos.montar(new ConnectionFactory(pasta.resolve("ia-tcp.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), lento);
        servidor = new Servidor(0, servicos);
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(() -> servidor.atenderConexoes(socket));
        servicos.autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray());
        servicos.autenticacao().cadastrar("bia", "Bia", SENHA.toCharArray());
    }

    @AfterEach
    void encerrar() throws Exception {
        liberar.countDown();
        for (Socket c : clientes) {
            c.close();
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

    private final class Cliente {
        final Socket conexao;
        final BufferedReader entrada;
        final BufferedWriter saida;
        final List<String> recebidas = new ArrayList<>();

        Cliente(String login) throws Exception {
            conexao = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort());
            conexao.setSoTimeout(10_000);
            clientes.add(conexao);
            entrada = new BufferedReader(new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8));
            saida = new BufferedWriter(new OutputStreamWriter(conexao.getOutputStream(), StandardCharsets.UTF_8));
            ler();
            ler();
            enviar(ProtocoloSakura.NEGOCIACAO);
            ler();
            enviar(ProtocoloSakura.comandoEntrar(login, SENHA.toCharArray()));
            assertTrue(ProtocoloSakura.lerResposta(ler()).sucesso());
        }

        void enviar(String linha) throws IOException {
            saida.write(linha);
            saida.newLine();
            saida.flush();
        }

        String ler() throws IOException {
            String linha = entrada.readLine();
            assertNotNull(linha, "Conexão encerrada. Recebidas: " + recebidas);
            recebidas.add(linha);
            return linha;
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
    }

    @Test
    void reconexaoDuranteAGeracaoMostraDigitandoERecebeAResposta() throws Exception {
        iniciar();
        Cliente ana = new Cliente("ana");
        ana.pedir(TipoRequisicao.CRIAR_PERSONAGEM, "nome", "Naruto", "personalidade", "animado", "contexto", "");
        long naruto = ProtocoloJson.lerEvento(ana.esperar(l -> l.startsWith("{\"tipo\":\"CONVERSA\"")))
                .conversa().id();
        ana.pedir(TipoRequisicao.ENVIAR, "conversaId", naruto, "texto", "Oi, Naruto!");
        assertTrue(gerando.await(10, TimeUnit.SECONDS));

        // Queda no meio da geração e novo login.
        ana.conexao.close();
        Cliente deVolta = new Cliente("ana");
        String estado = deVolta.esperar(l -> l.contains("\"IA_STATUS\""));
        assertTrue(estado.contains("\"conversaId\":" + naruto) && estado.contains("GERANDO"), estado);

        liberar.countDown();
        deVolta.esperar(l -> l.contains("\"tipo\":\"IA\"") && l.contains("Dattebayo!"));
        deVolta.esperar(l -> l.contains("\"IA_STATUS\"") && l.contains("CONCLUIDO"));

        // A resposta também ficou gravada (aparece no histórico).
        long historico = deVolta.pedir(TipoRequisicao.HISTORICO, "conversaId", naruto);
        String linha = deVolta.esperar(l -> l.contains("\"req\":" + historico));
        assertTrue(linha.contains("Oi, Naruto!") && linha.contains("Dattebayo!"), linha);
    }

    @Test
    void memoriaPeloProtocoloSoParaODono() throws Exception {
        iniciar();
        liberar.countDown();
        Cliente ana = new Cliente("ana");
        Cliente bia = new Cliente("bia");

        ana.pedir(TipoRequisicao.CRIAR_PERSONAGEM, "nome", "Naruto", "personalidade", "animado", "contexto", "");
        long naruto = ProtocoloJson.lerEvento(ana.esperar(l -> l.startsWith("{\"tipo\":\"CONVERSA\"")))
                .conversa().id();

        long ver = ana.pedir(TipoRequisicao.MEMORIA_VER, "conversaId", naruto);
        ProtocoloJson.Evento memoria = ProtocoloJson.lerEvento(ana.esperar(l -> l.contains("\"req\":" + ver)));
        assertEquals(ProtocoloJson.TipoEvento.MEMORIA, memoria.tipo());
        assertEquals("", memoria.resumoMemoria());
        assertNull(memoria.memoriaAtualizadaEm(), "Nunca resumida");

        long apagar = ana.pedir(TipoRequisicao.MEMORIA_APAGAR, "conversaId", naruto);
        assertEquals(ProtocoloJson.TipoEvento.MEMORIA,
                ProtocoloJson.lerEvento(ana.esperar(l -> l.contains("\"req\":" + apagar))).tipo());

        // A Bia, com o id da conversa da Ana, recebe o mesmo erro de uma conversa inexistente.
        for (TipoRequisicao tipo : new TipoRequisicao[] {TipoRequisicao.MEMORIA_VER, TipoRequisicao.MEMORIA_APAGAR}) {
            long alheio = bia.pedir(tipo, "conversaId", naruto);
            String erro = bia.esperar(l -> l.contains("\"req\":" + alheio));
            long inexistente = bia.pedir(tipo, "conversaId", 987_654);
            String erroInexistente = bia.esperar(l -> l.contains("\"req\":" + inexistente));
            assertTrue(erro.contains("\"NAO_ENCONTRADA\""), erro);
            assertEquals(erroInexistente.replace("\"req\":" + inexistente, ""), erro.replace("\"req\":" + alheio, ""),
                    "Nada revela a existência da conversa alheia");
        }
    }
}
