package br.unicesumar.chat.server;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloJson.Evento;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoEvento;
import br.unicesumar.chat.protocolo.ProtocoloJson.TipoRequisicao;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import br.unicesumar.chat.service.HashSenha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocolo v2 de ponta a ponta no servidor TCP real (SQLite temporário):
 * privadas, grupos, legado isolado, offline e reinício.
 */
class ConversasTcpTest {

    @TempDir
    Path pasta;

    private ServerSocket servidor;
    private ExecutorService threads;
    private final List<Socket> abertos = new CopyOnWriteArrayList<>();
    private final List<AutoCloseable> clientes = new ArrayList<>();
    private long proximoReq = 1;

    private void iniciarServidor() throws IOException {
        Servidor.Servicos servicos = Servidor.Servicos.montar(
                new ConnectionFactory(pasta.resolve("tcp.db").toString()), new HashSenha(1_000),
                Clock.systemDefaultZone());
        servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads = Executors.newCachedThreadPool();
        ServerSocket atual = servidor;
        threads.execute(() -> {
            while (!atual.isClosed()) {
                try {
                    Socket s = atual.accept();
                    abertos.add(s);
                    threads.execute(servicos.atender(s));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    private void pararServidor() throws Exception {
        for (AutoCloseable c : clientes) {
            c.close();
        }
        clientes.clear();
        servidor.close();
        for (Socket s : abertos) {
            s.close();
        }
        threads.shutdown();
        assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
    }

    @BeforeEach
    void iniciar() throws IOException {
        iniciarServidor();
    }

    @AfterEach
    void encerrar() throws Exception {
        pararServidor();
    }

    /** Cliente bruto (texto no legado; JSON após o login). */
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
            ler();
            ler();
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
            for (int i = 0; i < 200; i++) {
                String linha = ler();
                if (criterio.test(linha)) {
                    return linha;
                }
            }
            throw new AssertionError("Não recebida. Linhas: " + recebidas);
        }

        Evento esperarEvento(TipoEvento tipo, Long req) throws Exception {
            for (int i = 0; i < 200; i++) {
                Evento evento = ProtocoloJson.lerEvento(ler());
                if (evento.tipo() == tipo && (req == null || req.equals(evento.req()))) {
                    return evento;
                }
            }
            throw new AssertionError("Evento não recebido: " + tipo);
        }

        long requisitar(TipoRequisicao tipo, Object... campos) throws IOException {
            long req = proximoReq++;
            enviar(ProtocoloJson.requisicao(tipo, req, campos));
            return req;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private Cliente conta(String login, String apelido, boolean cadastrar) throws Exception {
        Cliente c = new Cliente();
        c.enviar(ProtocoloSakura.NEGOCIACAO);
        assertEquals(ProtocoloSakura.NEGOCIACAO_ACEITA, c.ler());
        char[] senha = "senha-de-teste".toCharArray();
        c.enviar(cadastrar
                ? ProtocoloSakura.comandoCadastrar(login, apelido, senha)
                : ProtocoloSakura.comandoEntrar(login, senha));
        assertTrue(ProtocoloSakura.lerResposta(c.ler()).sucesso());
        c.esperarEvento(TipoEvento.CONVERSAS, null);
        c.esperarEvento(TipoEvento.HISTORICO, null);
        return c;
    }

    private static long idUsuario(Cliente c, String apelido) throws Exception {
        long req = c.requisitar(TipoRequisicao.LISTAR_USUARIOS);
        return c.esperarEvento(TipoEvento.USUARIOS, req).usuarios().stream()
                .filter(u -> u.apelido().equals(apelido)).findFirst().orElseThrow().id();
    }

    /** Sincroniza: depois desta mensagem no Geral, tudo o que era anterior já chegou. */
    private static void marco(Cliente quemEnvia, List<Cliente> todos, String texto) throws Exception {
        quemEnvia.requisitar(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", texto);
        for (Cliente c : todos) {
            c.esperar(l -> l.contains(texto));
        }
    }

    @Test
    void privadaGrupoEIsolamentoDeTerceirosELegados() throws Exception {
        Cliente ana = conta("ana", "Ana", true);
        Cliente bia = conta("bia", "Bia", true);
        Cliente caio = conta("caio", "Caio", true);
        Cliente zoe = new Cliente();
        zoe.enviar("Zoe");
        zoe.esperar("SISTEMA: ===== FIM DO HISTÓRICO ====="::equals);

        // 1. Privada Ana ↔ Bia (reaberta por Bia sem duplicar).
        long req = ana.requisitar(TipoRequisicao.ABRIR_PRIVADA, "usuarioId", idUsuario(ana, "Bia"));
        long privada = ana.esperarEvento(TipoEvento.CONVERSA, req).conversa().id();
        bia.esperarEvento(TipoEvento.CONVERSA, null);
        req = bia.requisitar(TipoRequisicao.ABRIR_PRIVADA, "usuarioId", idUsuario(bia, "Ana"));
        assertEquals(privada, bia.esperarEvento(TipoEvento.CONVERSA, req).conversa().id());

        ana.requisitar(TipoRequisicao.ENVIAR, "conversaId", privada, "texto", "Segredo-Ana-Bia");
        assertEquals("Segredo-Ana-Bia", bia.esperarEvento(TipoEvento.MENSAGEM, null).mensagem().texto());

        // 2. Caio tenta ler e enviar: mesma resposta de conversa inexistente.
        req = caio.requisitar(TipoRequisicao.HISTORICO, "conversaId", privada);
        assertEquals("NAO_ENCONTRADA", caio.esperarEvento(TipoEvento.ERRO, req).codigo());
        req = caio.requisitar(TipoRequisicao.ENVIAR, "conversaId", privada, "texto", "intruso");
        assertEquals("NAO_ENCONTRADA", caio.esperarEvento(TipoEvento.ERRO, req).codigo());

        // 3. Grupo com Bia e Caio.
        req = ana.requisitar(TipoRequisicao.CRIAR_GRUPO, "nome", "Turma",
                "participantes", List.of(idUsuario(ana, "Bia"), idUsuario(ana, "Caio")));
        long grupo = ana.esperarEvento(TipoEvento.CONVERSA, req).conversa().id();
        for (Cliente c : List.of(bia, caio)) {
            assertEquals(grupo, c.esperarEvento(TipoEvento.CONVERSA, null).conversa().id());
        }
        for (Cliente autor : List.of(ana, bia, caio)) {
            autor.requisitar(TipoRequisicao.ENVIAR, "conversaId", grupo, "texto", "Grupo-" + autor.hashCode());
        }
        for (Cliente c : List.of(ana, bia, caio)) {
            // Autores diferentes: a ordem entre eles é livre; todos devem chegar.
            for (int i = 0; i < 200 && !List.of(ana, bia, caio).stream().allMatch(autor ->
                    c.recebidas.stream().anyMatch(l -> l.contains("Grupo-" + autor.hashCode()))); i++) {
                c.ler();
            }
            for (Cliente autor : List.of(ana, bia, caio)) {
                assertTrue(c.recebidas.stream().anyMatch(l -> l.contains("Grupo-" + autor.hashCode())));
            }
        }

        // Ponto de sincronização: tudo anterior já foi entregue a todos.
        marco(ana, List.of(ana, bia, caio, zoe), "Marco-final");

        assertTrue(caio.recebidas.stream().noneMatch(l -> l.contains("Segredo-Ana-Bia")), "Terceiro isolado");
        assertTrue(zoe.recebidas.stream().noneMatch(l -> l.contains("Segredo") || l.contains("Grupo-")),
                "Legado só recebe o Geral");
        assertTrue(zoe.recebidas.stream().anyMatch(l -> l.endsWith("Ana: Marco-final")));
        assertTrue(caio.recebidas.stream().noneMatch(l -> l.contains("\"login\"") || l.contains("pbkdf2")));
    }

    @Test
    void mensagensParaOfflineDevemSerRecuperadasAposReinicioDoServidor() throws Exception {
        Cliente ana = conta("ana", "Ana", true);
        Cliente bia = conta("bia", "Bia", true);
        long req = ana.requisitar(TipoRequisicao.CRIAR_GRUPO, "nome", "Estudos",
                "participantes", List.of(idUsuario(ana, "Bia")));
        long grupo = ana.esperarEvento(TipoEvento.CONVERSA, req).conversa().id();

        bia.close();
        ana.requisitar(TipoRequisicao.ENVIAR, "conversaId", grupo, "texto", "Enquanto você estava fora");
        ana.esperar(l -> l.contains("Enquanto você estava fora"));

        // Reinício completo do servidor sobre o mesmo banco.
        pararServidor();
        iniciarServidor();

        Cliente biaDeVolta = new Cliente();
        biaDeVolta.enviar(ProtocoloSakura.NEGOCIACAO);
        biaDeVolta.ler();
        biaDeVolta.enviar(ProtocoloSakura.comandoEntrar("bia", "senha-de-teste".toCharArray()));
        assertTrue(ProtocoloSakura.lerResposta(biaDeVolta.ler()).sucesso());

        Evento conversas = biaDeVolta.esperarEvento(TipoEvento.CONVERSAS, null);
        ProtocoloJson.ConversaJson estudos = conversas.conversas().stream()
                .filter(c -> c.id() == grupo).findFirst().orElseThrow();
        assertEquals(1, estudos.naoLidas());
        assertEquals("Enquanto você estava fora", estudos.ultima().texto());

        req = biaDeVolta.requisitar(TipoRequisicao.HISTORICO, "conversaId", grupo);
        assertEquals(List.of("Enquanto você estava fora"), biaDeVolta.esperarEvento(TipoEvento.HISTORICO, req)
                .mensagens().stream().map(ProtocoloJson.MensagemJson::texto).toList());
    }

    @Test
    void requisicoesInvalidasDevemResponderErroSemDerrubarASessao() throws Exception {
        Cliente ana = conta("ana", "Ana", true);

        ana.enviar("isto não é json");
        assertEquals("REQUISICAO_INVALIDA", ana.esperarEvento(TipoEvento.ERRO, null).codigo());
        ana.enviar("{\"tipo\":\"ENVIAR\",\"req\":5,\"conversaId\":\"1\",\"texto\":\"x\"}");
        assertEquals(5L, ana.esperarEvento(TipoEvento.ERRO, null).req());
        ana.enviar("{\"tipo\":\"HISTORICO\",\"req\":6,\"conversaId\":1,\"limite\":1000}");
        assertEquals("REQUISICAO_INVALIDA", ana.esperarEvento(TipoEvento.ERRO, 6L).codigo());
        long req = ana.requisitar(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", "x".repeat(501));
        assertEquals("DADOS_INVALIDOS", ana.esperarEvento(TipoEvento.ERRO, req).codigo());

        ana.requisitar(TipoRequisicao.ENVIAR, "conversaId", 1, "texto", "Ainda conectada");
        assertEquals("Ainda conectada", ana.esperarEvento(TipoEvento.MENSAGEM, null).mensagem().texto());
    }
}
