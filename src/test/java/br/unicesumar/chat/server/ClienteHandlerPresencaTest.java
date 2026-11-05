package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;
import br.unicesumar.chat.service.ConexaoCliente;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Avisos de presença emitidos pelo ClienteHandler (sem rede real). */
class ClienteHandlerPresencaTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-11-04T20:34:00Z"), ZoneOffset.UTC);

    private static final class SocketFalso extends Socket {

        private final InputStream entrada;
        private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
        private boolean fechado;

        SocketFalso(InputStream entrada) {
            this.entrada = entrada;
        }

        @Override
        public InputStream getInputStream() {
            return entrada;
        }

        @Override
        public OutputStream getOutputStream() {
            return saida;
        }

        @Override
        public synchronized void close() {
            fechado = true;
        }

        @Override
        public boolean isClosed() {
            return fechado;
        }

        String texto() {
            // O handler escreve em UTF-8.
            return saida.toString(StandardCharsets.UTF_8);
        }
    }

    /** Entrega o nome e, em seguida, simula a queda da conexão. */
    private static InputStream nomeSeguidoDeQueda(String nome) {
        byte[] linha = (nome + "\n").getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            private int posicao;

            @Override
            public int read() throws IOException {
                if (posicao < linha.length) {
                    return linha[posicao++];
                }
                throw new IOException("Connection reset");
            }
        };
    }

    private static InputStream texto(String conteudo) {
        return new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Observador implements ConexaoCliente {

        private final Usuario usuario;
        private final List<Mensagem> recebidas = new ArrayList<>();

        Observador(String nome) {
            usuario = new Usuario(nome);
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public synchronized void enviar(Mensagem mensagem) {
            recebidas.add(mensagem);
        }

        synchronized List<String> linhas() {
            return recebidas.stream().map(Mensagem::formatar).toList();
        }
    }

    private final MensagemRepositoryEmMemoria repositorio = new MensagemRepositoryEmMemoria();
    private final ChatService servico = new ChatService(repositorio, new ClientRegistry(), RELOGIO);

    @Test
    void saidaNormalDeveGerarEntradaESaidaParaOsDemais() {

        Observador bia = new Observador("Bia");
        servico.conectarCliente(bia, 20);

        SocketFalso socket = new SocketFalso(texto("Ana\nOlá!\n/sair\n"));
        new ClienteHandler(socket, servico).run();

        assertEquals(List.of(
                "SISTEMA: [04/11 20:34] Ana se conectou",
                "[04/11 20:34] Ana: Olá!",
                "SISTEMA: [04/11 20:34] Ana se desconectou"
        ), bia.linhas());

        assertTrue(socket.isClosed());
        assertEquals(1, servico.contarClientes());
    }

    @Test
    void quedaAbruptaDeveGerarSaidaEFecharOSocket() {

        Observador bia = new Observador("Bia");
        servico.conectarCliente(bia, 20);

        SocketFalso socket = new SocketFalso(nomeSeguidoDeQueda("Ana"));
        new ClienteHandler(socket, servico).run();

        assertEquals(List.of(
                "SISTEMA: [04/11 20:34] Ana se conectou",
                "SISTEMA: [04/11 20:34] Ana se desconectou"
        ), bia.linhas());

        assertTrue(socket.isClosed());
        assertEquals(1, servico.contarClientes());
    }

    @Test
    void nomeRecusadoNaoDeveGerarAvisos() {

        Observador ana = new Observador("Ana");
        servico.conectarCliente(ana, 20);
        int gravadosAntes = repositorio.buscarUltimas(100).size();

        SocketFalso socket = new SocketFalso(texto("ana\n"));
        new ClienteHandler(socket, servico).run();

        assertTrue(socket.texto().contains("Não foi possível entrar"));
        assertTrue(ana.linhas().isEmpty());
        assertEquals(gravadosAntes, repositorio.buscarUltimas(100).size());
        assertEquals(1, servico.contarClientes());
    }

    @Test
    void quemEntraNaoRecebeOProprioAvisoMasRecebeOHistorico() {

        // Visita anterior da Bia: fica registrada no histórico.
        Observador bia = new Observador("Bia");
        servico.conectarCliente(bia, 20);
        servico.desconectarCliente(bia);

        SocketFalso socket = new SocketFalso(texto("Ana\n/sair\n"));
        new ClienteHandler(socket, servico).run();

        String saida = socket.texto();

        assertTrue(saida.contains("SISTEMA: [04/11 20:34] Bia se conectou"));
        assertTrue(saida.contains("SISTEMA: [04/11 20:34] Bia se desconectou"));
        assertFalse(saida.contains("Ana se conectou"), "Quem entra não recebe o próprio aviso.");

        long entradasAna = repositorio.buscarUltimas(100).stream()
                .filter(m -> m.getTipo() == TipoMensagem.ENTRADA)
                .filter(m -> m.getRemetente().getNome().equals("Ana"))
                .count();
        assertEquals(1, entradasAna);
    }
}
