package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ClientRegistry;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o protocolo do ClienteHandler sem rede: o socket é simulado
 * com fluxos em memória e o handler é executado na thread do teste.
 */
class ClienteHandlerTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-11-03T15:00:00Z"),
            ZoneOffset.UTC
    );

    private static final String CONFIRMACAO =
            "SISTEMA: Conectado como Ana";

    /** Saída em memória que executa uma ação ao detectar um texto. */
    private static final class SaidaComGatilho
            extends ByteArrayOutputStream {

        private final String gatilho;
        private Runnable acao;

        SaidaComGatilho(String gatilho) {
            this.gatilho = gatilho;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            super.write(b, off, len);

            if (acao != null
                    && toString(StandardCharsets.UTF_8).contains(gatilho)) {
                Runnable executar = acao;
                acao = null;
                executar.run();
            }
        }

        List<String> linhas() {
            return toString(StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static final class SocketFalso extends Socket {

        private final InputStream entrada;
        private final OutputStream saida;
        private boolean fechado;

        SocketFalso(String entrada, OutputStream saida) {
            this.entrada = new ByteArrayInputStream(
                    entrada.getBytes(StandardCharsets.UTF_8)
            );
            this.saida = saida;
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
    }

    private static ChatService criarServico(MensagemRepository repositorio) {
        return new ChatService(repositorio, new ClientRegistry(), RELOGIO);
    }

    private static Mensagem mensagem(String nome, String texto, int minuto) {
        return new Mensagem(
                new Usuario(nome),
                texto,
                LocalDateTime.of(2026, 11, 3, 14, minuto)
        );
    }

    @Test
    void deveEnviarConfirmacaoEHistoricoAntesDeBroadcastRecebidoNaConexao() {

        MensagemRepositoryEmMemoria repositorio =
                new MensagemRepositoryEmMemoria();

        repositorio.salvar(mensagem("Bia", "Antiga 1", 1));
        repositorio.salvar(mensagem("Bia", "Antiga 2", 2));

        ChatService servico = criarServico(repositorio);

        SaidaComGatilho saida = new SaidaComGatilho(CONFIRMACAO);

        // Broadcast disparado com o cliente já registrado e antes do
        // envio do histórico: a janela da condição de corrida.
        saida.acao = () -> servico.enviarMensagem(
                new Usuario("Bia"),
                "Durante a conexão"
        );

        SocketFalso socket = new SocketFalso("Ana\n/sair\n", saida);

        new ClienteHandler(socket, servico).run();

        List<String> linhas = saida.linhas();

        int confirmacao = linhas.indexOf(CONFIRMACAO);
        int inicioHistorico = linhas.indexOf(
                "SISTEMA: ===== HISTÓRICO RECENTE ====="
        );
        int fimHistorico = linhas.indexOf(
                "SISTEMA: ===== FIM DO HISTÓRICO ====="
        );

        assertEquals(2, confirmacao, "Confirmação deve vir após a saudação.");
        assertEquals(confirmacao + 1, inicioHistorico);

        assertEquals(
                List.of(
                        "[03/11 14:01] Bia: Antiga 1",
                        "[03/11 14:02] Bia: Antiga 2"
                ),
                linhas.subList(inicioHistorico + 1, fimHistorico)
        );

        String aoVivo = "[03/11 15:00] Bia: Durante a conexão";

        assertEquals(fimHistorico + 1, linhas.indexOf(aoVivo));
        assertEquals(
                1,
                linhas.stream().filter(aoVivo::equals).count(),
                "A mensagem não pode ser duplicada."
        );

        assertTrue(socket.isClosed());
        assertEquals(0, servico.contarClientes());
    }

    @Test
    void deveEntregarMensagensDiretamenteAposOHistorico() {

        ChatService servico = criarServico(new MensagemRepositoryEmMemoria());

        ByteArrayOutputStream saida = new ByteArrayOutputStream();

        SocketFalso socket = new SocketFalso(
                "Ana\nPrimeira\nSegunda\n/sair\n",
                saida
        );

        new ClienteHandler(socket, servico).run();

        List<String> linhas =
                saida.toString(StandardCharsets.UTF_8).lines().toList();

        int fimHistorico = linhas.indexOf(
                "SISTEMA: ===== FIM DO HISTÓRICO ====="
        );

        assertEquals(
                List.of(
                        "[03/11 15:00] Ana: Primeira",
                        "[03/11 15:00] Ana: Segunda"
                ),
                linhas.subList(fimHistorico + 1, linhas.size())
        );
    }

    @Test
    void deveRecusarNomeDuplicadoSemEnviarConfirmacao() {

        ChatService servico = criarServico(new MensagemRepositoryEmMemoria());

        SaidaComGatilho saidaAna = new SaidaComGatilho(CONFIRMACAO);
        ByteArrayOutputStream saidaDuplicada = new ByteArrayOutputStream();

        // Enquanto Ana está conectada, outro cliente tenta usar "ana".
        saidaAna.acao = () -> new ClienteHandler(
                new SocketFalso("ana\n", saidaDuplicada),
                servico
        ).run();

        new ClienteHandler(
                new SocketFalso("Ana\n/sair\n", saidaAna),
                servico
        ).run();

        String resposta = saidaDuplicada.toString(StandardCharsets.UTF_8);

        assertTrue(resposta.contains("SISTEMA: Não foi possível entrar:"));
        assertFalse(resposta.contains("SISTEMA: Conectado como"));
        assertEquals(0, servico.contarClientes());
    }

    @Test
    void deveInformarFalhaAoCarregarHistoricoSemRegistrarCliente() {

        MensagemRepository repositorioComFalha = new MensagemRepository() {

            @Override
            public Mensagem salvar(Mensagem mensagem) {
                throw new IllegalStateException("Banco indisponível.");
            }

            @Override
            public List<Mensagem> buscarUltimas(int quantidade) {
                throw new IllegalStateException("Banco indisponível.");
            }
        };

        ChatService servico = criarServico(repositorioComFalha);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        SocketFalso socket = new SocketFalso("Ana\n", saida);

        new ClienteHandler(socket, servico).run();

        String resposta = saida.toString(StandardCharsets.UTF_8);

        assertTrue(resposta.contains(
                "SISTEMA: Não foi possível carregar o histórico."
        ));
        assertFalse(resposta.contains("SISTEMA: Conectado como"));
        assertEquals(0, servico.contarClientes());
        assertTrue(socket.isClosed());
    }
}
