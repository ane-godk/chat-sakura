package br.unicesumar.chat.client;

import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.model.Aparencia.Fundo;
import br.unicesumar.chat.model.Aparencia.Modo;
import br.unicesumar.chat.model.Aparencia.Paleta;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.server.LimitesServidor;
import br.unicesumar.chat.server.Servidor;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import br.unicesumar.chat.service.HashSenha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Aparência na janela real (não exibida): preferência local (visitante e
 * tela de entrada), aparência da conta vinda do servidor, saída da conta e
 * escolha feita sem conexão. Usa arquivo de preferências e banco temporários.
 */
class AparenciaClienteTest {

    private static final String SENHA = "senha-segura-1";
    private static final Aparencia SUZURAN_NEUTRO = new Aparencia(Paleta.SUZURAN, Modo.CLARO, Fundo.NEUTRO);
    private static final Aparencia FUJI_ESCURO = new Aparencia(Paleta.FUJI, Modo.ESCURO, Fundo.FLORAL);
    private static final Aparencia MYOSOTIS_LISO = new Aparencia(Paleta.MYOSOTIS, Modo.CLARO, Fundo.LISO);

    @TempDir
    Path pasta;

    private final List<ClienteChat> janelas = new ArrayList<>();

    @BeforeEach
    void exigirInterfaceGrafica() {
        assumeFalse(GraphicsEnvironment.isHeadless());
    }

    @AfterEach
    void limpar() throws Exception {
        naEdt(() -> janelas.forEach(ClienteChat::dispose));
        naEdt(() -> GerenciadorTema.definir(Aparencia.PADRAO));
    }

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    private static <T> T naEdt(Supplier<T> acao) throws Exception {
        AtomicReference<T> valor = new AtomicReference<>();
        naEdt(() -> valor.set(acao.get()));
        return valor.get();
    }

    private PreferenciasSakura preferencias(String conteudo) throws Exception {
        Path arquivo = pasta.resolve(".chat-sakura").resolve("preferencias.properties");
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return new PreferenciasSakura(arquivo);
    }

    private ClienteChat abrir(PreferenciasSakura p) throws Exception {
        ClienteChat janela = naEdt(() -> new ClienteChat(p));
        janelas.add(janela);
        return janela;
    }

    @Test
    void preferenciaLocalValeNaTelaDeEntradaEEscolhaSemContaFicaNoArquivo() throws Exception {
        PreferenciasSakura p = preferencias("tema=claro\npaleta=suzuran\nfundo=neutro\nsons=ligados\n");
        abrir(p);
        assertEquals(SUZURAN_NEUTRO, naEdt(GerenciadorTema::aparencia));
        assertSame(PaletasFlorais.de(Paleta.SUZURAN, false), TemaSakura.cores());

        naEdt(() -> GerenciadorTema.definir(FUJI_ESCURO));
        assertTrue(p.aguardarGravacoes(5_000));
        PreferenciasSakura.Valores salvos = PreferenciasSakura.resolver(p.ler(), null, null, null);
        assertEquals(FUJI_ESCURO, salvos.aparencia());
        assertEquals("ligados", p.ler().getProperty("sons"), "Outras chaves preservadas");
    }

    @Test
    void aparenciaDaContaNaoSobrescreveALocalEVoltaAoSair() throws Exception {
        PreferenciasSakura p = preferencias("tema=claro\npaleta=suzuran\nfundo=neutro\n");
        ClienteChat janela = abrir(p);

        // O servidor entrega a aparência da conta: aplicada sem gravar no arquivo.
        naEdt(() -> janela.aparenciaDaContaRecebida(FUJI_ESCURO));
        assertEquals(FUJI_ESCURO, naEdt(GerenciadorTema::aparencia));
        assertTrue(naEdt(janela::aparenciaDaConta));

        // Escolha sem conexão (como durante a reconexão): fica pendente, não vai ao arquivo.
        naEdt(() -> GerenciadorTema.definir(MYOSOTIS_LISO));
        assertTrue(p.aguardarGravacoes(5_000));
        assertEquals(SUZURAN_NEUTRO, PreferenciasSakura.resolver(p.ler(), null, null, null).aparencia());

        // Ao voltar, o servidor reenvia a aparência antiga: a escolha mais recente prevalece.
        naEdt(() -> janela.aparenciaDaContaRecebida(FUJI_ESCURO));
        assertEquals(MYOSOTIS_LISO, naEdt(GerenciadorTema::aparencia));

        // Saída da conta: volta a aparência local.
        naEdt(janela::encerrarAparenciaDaConta);
        assertEquals(SUZURAN_NEUTRO, naEdt(GerenciadorTema::aparencia));
        assertFalse(naEdt(janela::aparenciaDaConta));
        assertTrue(p.aguardarGravacoes(5_000));
        assertEquals(SUZURAN_NEUTRO, PreferenciasSakura.resolver(p.ler(), null, null, null).aparencia());
    }

    @Test
    void loginRestauraAAparenciaDaContaEAEscolhaVaiAoServidor() throws Exception {
        LimitesServidor limites = LimitesServidor.padrao().comTaxas(
                new LimitesServidor.Taxa(1_000, 60_000), new LimitesServidor.Taxa(100, 6_000),
                new LimitesServidor.Taxa(1_000, 60_000), System::nanoTime);
        Servidor.Servicos servicos = Servidor.Servicos.montar(
                new ConnectionFactory(pasta.resolve("aparencia-cliente.db").toString()),
                new HashSenha(1_000), Clock.systemDefaultZone(), new ProvedorIAFalso(), limites);
        Usuario ana = new Usuario(servicos.autenticacao().cadastrar("ana", "Ana", SENHA.toCharArray()).getId(), "Ana");
        servicos.preferencias().definir(ana, FUJI_ESCURO);

        // Atendimento nas threads do teste, para encerrá-las antes de apagar o banco.
        ExecutorService threads = Executors.newCachedThreadPool();
        List<Socket> abertos = new CopyOnWriteArrayList<>();
        ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(() -> {
            while (!socket.isClosed()) {
                try {
                    Socket s = socket.accept();
                    abertos.add(s);
                    threads.execute(servicos.atender(s));
                } catch (IOException e) {
                    return;
                }
            }
        });

        try {
            PreferenciasSakura p = preferencias("tema=claro\npaleta=suzuran\nfundo=neutro\n");
            ClienteChat janela = abrir(p);

            CountDownLatch aplicada = new CountDownLatch(1);
            Runnable ouvinte = () -> {
                if (GerenciadorTema.aparencia().equals(FUJI_ESCURO)) {
                    aplicada.countDown();
                }
            };
            GerenciadorTema.aoMudar(ouvinte);
            try {
                naEdt(() -> janela.controlador().conectar(InetAddress.getLoopbackAddress().getHostAddress(),
                        socket.getLocalPort(), new ConexaoServidor.Entrar("ana", SENHA.toCharArray())));
                assertTrue(aplicada.await(10, TimeUnit.SECONDS), "Aparência da conta aplicada após o login");
            } finally {
                GerenciadorTema.removerOuvinte(ouvinte);
            }
            assertSame(PaletasFlorais.de(Paleta.FUJI, true), TemaSakura.cores());

            // Escolha com a conta conectada: gravada no servidor, não no arquivo local.
            naEdt(() -> GerenciadorTema.definir(MYOSOTIS_LISO));
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!servicos.preferencias().aparencia(ana).equals(MYOSOTIS_LISO) && System.nanoTime() < limite) {
                Thread.onSpinWait();
            }
            assertEquals(MYOSOTIS_LISO, servicos.preferencias().aparencia(ana));
            assertTrue(p.aguardarGravacoes(5_000));
            assertEquals(SUZURAN_NEUTRO, PreferenciasSakura.resolver(p.ler(), null, null, null).aparencia());

            // Saída voluntária: a janela volta à aparência local.
            CountDownLatch local = new CountDownLatch(1);
            Runnable aoVoltar = () -> {
                if (GerenciadorTema.aparencia().equals(SUZURAN_NEUTRO)) {
                    local.countDown();
                }
            };
            GerenciadorTema.aoMudar(aoVoltar);
            try {
                naEdt(() -> janela.controlador().desconectar());
                assertTrue(local.await(10, TimeUnit.SECONDS), "Aparência local restaurada ao sair");
            } finally {
                GerenciadorTema.removerOuvinte(aoVoltar);
            }
        } finally {
            socket.close();
            for (Socket s : abertos) {
                s.close();
            }
            threads.shutdown();
            assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
