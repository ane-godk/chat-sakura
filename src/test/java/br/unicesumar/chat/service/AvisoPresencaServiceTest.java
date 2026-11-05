package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryEmMemoria;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Avisos de entrada e saída: persistência, transmissão, ordem e falhas. */
class AvisoPresencaServiceTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-11-04T20:34:00Z"), ZoneOffset.UTC);

    private static final class ClienteFalso implements ConexaoCliente {

        private final Usuario usuario;
        private final List<Mensagem> recebidas = new ArrayList<>();

        ClienteFalso(String nome) {
            this.usuario = new Usuario(nome);
        }

        @Override
        public Usuario getUsuario() {
            return usuario;
        }

        @Override
        public synchronized void enviar(Mensagem mensagem) {
            recebidas.add(mensagem);
        }

        synchronized List<Mensagem> recebidas() {
            return new ArrayList<>(recebidas);
        }
    }

    private final MensagemRepositoryEmMemoria repositorio = new MensagemRepositoryEmMemoria();

    private ChatService servico(MensagemRepository repo) {
        return new ChatService(repo, new ClientRegistry(), RELOGIO);
    }

    private List<Mensagem> avisos(TipoMensagem tipo) {
        return repositorio.buscarUltimas(10_000).stream()
                .filter(m -> m.getTipo() == tipo).toList();
    }

    private static String resumo(Mensagem m) {
        return m.getTipo() + ":" + m.getRemetente().getNome();
    }

    @Test
    void entradaDeveChegarAosDemaisMasNaoAQuemEntrou() {

        ChatService servico = servico(repositorio);
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso ana = new ClienteFalso("Ana");

        servico.conectarCliente(bia, 20);
        servico.conectarCliente(ana, 20);

        assertEquals(List.of("ENTRADA:Ana"), bia.recebidas().stream().map(AvisoPresencaServiceTest::resumo).toList());
        assertTrue(ana.recebidas().isEmpty(), "Quem entra não recebe o próprio aviso.");

        List<Mensagem> entradas = avisos(TipoMensagem.ENTRADA);
        assertEquals(2, entradas.size());
        assertNotNull(entradas.get(1).getId(), "O aviso transmitido é o persistido.");
        assertEquals(entradas.get(1), bia.recebidas().get(0));
    }

    @Test
    void conexaoRecusadaNaoDeveRegistrarEntrada() {

        ChatService servico = servico(repositorio);
        ClienteFalso bia = new ClienteFalso("Bia");
        servico.conectarCliente(bia, 20);

        assertThrows(IllegalArgumentException.class,
                () -> servico.conectarCliente(new ClienteFalso("bia"), 20));

        assertEquals(1, avisos(TipoMensagem.ENTRADA).size());
        assertTrue(bia.recebidas().isEmpty());
        assertEquals(1, servico.contarClientes());
    }

    @Test
    void saidaDeveSerAvisadaUmaUnicaVez() {

        ChatService servico = servico(repositorio);
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso ana = new ClienteFalso("Ana");

        servico.conectarCliente(bia, 20);
        servico.conectarCliente(ana, 20);

        servico.desconectarCliente(ana);
        servico.desconectarCliente(ana);

        assertEquals(List.of("ENTRADA:Ana", "SAIDA:Ana"),
                bia.recebidas().stream().map(AvisoPresencaServiceTest::resumo).toList());
        assertEquals(1, avisos(TipoMensagem.SAIDA).size());
        assertEquals(1, servico.contarClientes());
    }

    @Test
    void clienteNuncaRegistradoNaoDeveGerarSaida() {

        ChatService servico = servico(repositorio);

        servico.desconectarCliente(new ClienteFalso("Ana"));

        assertTrue(avisos(TipoMensagem.SAIDA).isEmpty());
    }

    @Test
    void avisosEMensagensDevemManterAOrdemCronologica() {

        ChatService servico = servico(repositorio);
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso caio = new ClienteFalso("Caio");

        servico.conectarCliente(bia, 20);
        servico.conectarCliente(ana, 20);
        servico.enviarMensagem(ana.getUsuario(), "Oi, pessoal!");
        servico.conectarCliente(caio, 20);
        servico.desconectarCliente(ana);

        List<Mensagem> recebidas = bia.recebidas();

        assertEquals(List.of("ENTRADA:Ana", "MENSAGEM:Ana", "ENTRADA:Caio", "SAIDA:Ana"),
                recebidas.stream().map(AvisoPresencaServiceTest::resumo).toList());

        // Mesma ordem do repositório (após a entrada da própria Bia).
        List<Mensagem> gravadas = repositorio.buscarUltimas(100);
        assertEquals(gravadas.subList(1, gravadas.size()), recebidas);
    }

    @Test
    void historicoDeveIncluirOsAvisosAnterioresSemOProprio() {

        ChatService servico = servico(repositorio);
        ClienteFalso ana = new ClienteFalso("Ana");

        servico.conectarCliente(ana, 20);
        servico.enviarMensagem(ana.getUsuario(), "Até logo!");
        servico.desconectarCliente(ana);

        List<String> historico = servico.conectarCliente(new ClienteFalso("Bia"), 20)
                .stream().map(Mensagem::formatar).toList();

        assertEquals(List.of(
                "SISTEMA: [04/11 20:34] Ana se conectou",
                "[04/11 20:34] Ana: Até logo!",
                "SISTEMA: [04/11 20:34] Ana se desconectou"
        ), historico);
    }

    @Test
    void falhaAoGravarAvisoNaoDeveImpedirConexaoNemDesconexao() {

        MensagemRepository comFalhaNosAvisos = new MensagemRepository() {
            @Override
            public Mensagem salvar(Mensagem mensagem) {
                if (mensagem.isAvisoDePresenca()) {
                    throw new IllegalStateException("Banco indisponível.");
                }
                return repositorio.salvar(mensagem);
            }

            @Override
            public List<Mensagem> buscarUltimas(int quantidade) {
                return repositorio.buscarUltimas(quantidade);
            }
        };

        ChatService servico = servico(comFalhaNosAvisos);
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso ana = new ClienteFalso("Ana");

        assertDoesNotThrow(() -> servico.conectarCliente(bia, 20));
        assertDoesNotThrow(() -> servico.conectarCliente(ana, 20));
        assertEquals(2, servico.contarClientes());

        // Sem gravação, o aviso não é transmitido.
        assertTrue(bia.recebidas().isEmpty());

        // As mensagens continuam funcionando.
        servico.enviarMensagem(ana.getUsuario(), "ainda funciona");
        assertEquals(1, bia.recebidas().size());

        assertDoesNotThrow(() -> servico.desconectarCliente(ana));
        assertEquals(1, servico.contarClientes());
        assertEquals(1, bia.recebidas().size());
    }

    @Test
    void entradasSaidasEMensagensSimultaneasDevemChegarNaMesmaOrdemSemDuplicatas()
            throws Exception {

        ChatService servico = servico(repositorio);
        ClienteFalso observador = new ClienteFalso("Observador");
        servico.conectarCliente(observador, 20);

        int participantes = 8;
        int remetentes = 2;
        int mensagensPorRemetente = 100;

        ExecutorService executor = Executors.newFixedThreadPool(participantes + remetentes);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();

        try {
            for (int i = 0; i < participantes; i++) {
                ClienteFalso participante = new ClienteFalso("P" + i);
                tarefas.add(executor.submit(() -> {
                    inicio.await();
                    servico.conectarCliente(participante, 20);
                    servico.enviarMensagem(participante.getUsuario(), "oi de " + participante.getUsuario());
                    servico.desconectarCliente(participante);
                    servico.desconectarCliente(participante);
                    return null;
                }));
            }

            for (int r = 0; r < remetentes; r++) {
                final int numero = r;
                tarefas.add(executor.submit(() -> {
                    inicio.await();
                    for (int j = 0; j < mensagensPorRemetente; j++) {
                        servico.enviarMensagem(observador.getUsuario(), "R" + numero + " M" + j);
                    }
                    return null;
                }));
            }

            inicio.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }

            List<Mensagem> gravadas = repositorio.buscarUltimas(10_000);
            List<Mensagem> recebidas = observador.recebidas();

            // O observador recebe exatamente o que foi gravado após a sua entrada, na mesma ordem.
            assertEquals(gravadas.subList(1, gravadas.size()), recebidas);

            assertEquals(participantes + 1, avisos(TipoMensagem.ENTRADA).size());
            assertEquals(participantes, avisos(TipoMensagem.SAIDA).size());
            assertEquals(gravadas.size(), new HashSet<>(gravadas).size(), "Sem duplicatas.");

            // Para cada participante: entrada < mensagem < saída.
            for (int i = 0; i < participantes; i++) {
                String nome = "P" + i;
                List<TipoMensagem> sequencia = recebidas.stream()
                        .filter(m -> m.getRemetente().getNome().equals(nome))
                        .map(Mensagem::getTipo).toList();
                assertEquals(List.of(TipoMensagem.ENTRADA, TipoMensagem.MENSAGEM, TipoMensagem.SAIDA),
                        sequencia, nome);
            }

            assertEquals(1, servico.contarClientes());

        } finally {
            executor.shutdownNow();
        }
    }
}
