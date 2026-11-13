package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoEvento;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Aparência por conta pelo protocolo v2: entrega após o login, gravação, isolamento e reinício. */
class AparenciaTcpTest {

    private static final String SENHA = "senha-segura-1";
    private static final ProvedorIA SEM_IA = new ProvedorIA() {
        @Override public String gerar(PedidoIA pedido) { return "ok"; }
        @Override public String descricao() { return "teste"; }
    };

    @TempDir
    Path pasta;

    private Servidor servidor;
    private ServerSocket socket;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Socket> clientes = new ArrayList<>();
    private long req = 1;

    private Servidor.Servicos iniciar() throws IOException {
        Servidor.Servicos servicos = Servidor.Servicos.montar(
                new ConnectionFactory(pasta.resolve("aparencia.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), SEM_IA,
                // Folga nas taxas: o teste faz várias gravações seguidas (o limite tem testes próprios).
                LimitesServidor.padrao().comTaxas(new LimitesServidor.Taxa(1_000, 60_000),
                        new LimitesServidor.Taxa(100, 6_000), new LimitesServidor.Taxa(1_000, 60_000),
                        System::nanoTime));
        servidor = new Servidor(0, servicos);
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(() -> servidor.atenderConexoes(socket));
        return servicos;
    }

    private void parar() throws Exception {
        for (Socket c : clientes) {
            c.close();
        }
        clientes.clear();
        if (socket != null) {
            socket.close();
            assertTrue(servidor.vagas().aguardarOcupadas(0, Duration.ofSeconds(10)));
            socket = null;
        }
    }

    @AfterEach
    void encerrar() throws Exception {
        parar();
        threads.shutdown();
        assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
    }

    private final class Cliente {
        final BufferedReader entrada;
        final BufferedWriter saida;
        final List<String> recebidas = new ArrayList<>();

        Cliente(String login) throws Exception {
            Socket conexao = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort());
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

        /** Primeiro evento APARENCIA, enviado logo após a entrada. */
        Aparencia aparenciaInicial() throws Exception {
            ProtocoloJson.Evento evento = ProtocoloJson.lerEvento(
                    esperar(l -> l.startsWith("{\"tipo\":\"APARENCIA\"")));
            assertNull(evento.req());
            return evento.aparencia();
        }

        String definir(String paleta, String modo, String fundo) throws IOException {
            long numero = req++;
            enviar(ProtocoloJson.requisicao(TipoRequisicao.DEFINIR_APARENCIA, numero,
                    "paleta", paleta, "modo", modo, "fundo", fundo));
            return esperar(l -> l.contains("\"req\":" + numero));
        }
    }

    @Test
    void contaNovaRecebeOPadraoSakuraLogoAposEntrar() throws Exception {
        iniciar().autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray());
        Cliente ana = new Cliente("ana");

        assertEquals(Aparencia.PADRAO, ana.aparenciaInicial());
        // A aparência chega depois da lista de conversas e do histórico da Geral.
        int conversas = indice(ana.recebidas, "{\"tipo\":\"CONVERSAS\"");
        int historico = indice(ana.recebidas, "{\"tipo\":\"HISTORICO\"");
        int aparencia = indice(ana.recebidas, "{\"tipo\":\"APARENCIA\"");
        assertTrue(conversas < historico && historico < aparencia, ana.recebidas.toString());
    }

    @Test
    void cadaContaTemSuaAparenciaEElaVoltaNaReconexaoENoReinicio() throws Exception {
        Servidor.Servicos servicos = iniciar();
        servicos.autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray());
        servicos.autenticacao().cadastrar("bia", "Bia", SENHA.toCharArray());

        Cliente ana = new Cliente("ana");
        Cliente bia = new Cliente("bia");
        ana.aparenciaInicial();
        bia.aparenciaInicial();

        ProtocoloJson.Evento respostaAna = ProtocoloJson.lerEvento(ana.definir("FUJI", "ESCURO", "FLORAL"));
        ProtocoloJson.Evento respostaBia = ProtocoloJson.lerEvento(bia.definir("MYOSOTIS", "CLARO", "LISO"));
        Aparencia fujiEscuro = new Aparencia(Aparencia.Paleta.FUJI, Aparencia.Modo.ESCURO, Aparencia.Fundo.FLORAL);
        Aparencia myosotisClaro = new Aparencia(Aparencia.Paleta.MYOSOTIS, Aparencia.Modo.CLARO, Aparencia.Fundo.LISO);
        assertEquals(TipoEvento.APARENCIA, respostaAna.tipo());
        assertEquals(fujiEscuro, respostaAna.aparencia());
        assertEquals(myosotisClaro, respostaBia.aparencia());

        // A escolha de uma conta não é enviada à outra.
        assertTrue(bia.recebidas.stream().noneMatch(l -> l.contains("FUJI")), bia.recebidas.toString());
        assertTrue(ana.recebidas.stream().noneMatch(l -> l.contains("MYOSOTIS")), ana.recebidas.toString());

        // Reconexão: a própria aparência volta, sem pedido do cliente.
        assertEquals(fujiEscuro, new Cliente("ana").aparenciaInicial());

        // Reinício do servidor sobre o mesmo banco.
        parar();
        iniciar();
        assertEquals(fujiEscuro, new Cliente("ana").aparenciaInicial());
        assertEquals(myosotisClaro, new Cliente("bia").aparenciaInicial());
    }

    @Test
    void valoresInvalidosSaoRecusadosSemAlterarAAparenciaGravada() throws Exception {
        iniciar().autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray());
        Cliente ana = new Cliente("ana");
        ana.aparenciaInicial();
        ana.definir("SUZURAN", "ESCURO", "NEUTRO");

        for (String[] invalida : new String[][] {
                {"ROSA", "CLARO", "FLORAL"}, {"SAKURA", "SEPIA", "FLORAL"}, {"SAKURA", "CLARO", "XADREZ"},
                {"", "CLARO", "FLORAL"}, {"sakura'; DROP TABLE usuario; --", "CLARO", "FLORAL"}}) {
            String erro = ana.definir(invalida[0], invalida[1], invalida[2]);
            assertTrue(erro.contains("\"ERRO\"") && erro.contains("DADOS_INVALIDOS"), erro);
        }

        long numero = req++;
        ana.enviar(ProtocoloJson.requisicao(TipoRequisicao.DEFINIR_APARENCIA, numero, "paleta", "FUJI"));
        String semCampos = ana.esperar(l -> l.contains("\"req\":" + numero));
        assertTrue(semCampos.contains("REQUISICAO_INVALIDA"), semCampos);

        assertEquals(new Aparencia(Aparencia.Paleta.SUZURAN, Aparencia.Modo.ESCURO, Aparencia.Fundo.NEUTRO),
                new Cliente("ana").aparenciaInicial());
    }

    private static int indice(List<String> linhas, String prefixo) {
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).startsWith(prefixo)) {
                return i;
            }
        }
        return -1;
    }
}
