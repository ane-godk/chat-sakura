package br.unicesumar.chat.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** "Encerrar o servidor" do inicializador: só o processo registrado, nunca um desconhecido. */
class PararServidorTest {

    @TempDir
    Path dados;

    /** Processo auxiliar que fica parado até ser encerrado. */
    public static final class Dorminhoco {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }

    private Process iniciarDorminhoco() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), Dorminhoco.class.getName())
                .redirectErrorStream(true).start();
    }

    private void registrar(long pid, String inicio) throws Exception {
        Files.createDirectories(InicioLocal.arquivoProcesso(dados).getParent());
        Files.writeString(InicioLocal.arquivoProcesso(dados), pid + ";" + inicio, StandardCharsets.UTF_8);
    }

    @Test
    void encerraSomenteOProcessoRegistradoComOMesmoInicio() throws Exception {
        Process servidor = iniciarDorminhoco();
        try {
            registrar(servidor.pid(), servidor.toHandle().info().startInstant().orElseThrow().toString());

            assertEquals(InicioLocal.SAKURA_ATIVO, InicioLocal.parar(dados));
            assertTrue(servidor.waitFor(10, TimeUnit.SECONDS), "Processo registrado encerrado");
            assertTrue(Files.exists(InicioLocal.marcadorParada(dados)), "A janela do servidor fecha sem pedir tecla");
        } finally {
            servidor.destroyForcibly();
        }
    }

    @Test
    void pidReaproveitadoPorOutroProgramaNuncaEEncerrado() throws Exception {
        Process outro = iniciarDorminhoco();
        try {
            // Mesmo PID, mas horário de início diferente: não é o servidor do inicializador.
            registrar(outro.pid(), "2020-01-01T00:00:00Z");

            assertEquals(InicioLocal.PORTA_LIVRE, InicioLocal.parar(dados));
            assertFalse(outro.waitFor(1, TimeUnit.SECONDS), "O outro programa continua rodando");
            assertFalse(Files.exists(InicioLocal.marcadorParada(dados)));
        } finally {
            outro.destroyForcibly();
        }
    }

    @Test
    void semRegistroOuComArquivoInvalidoNadaEEncerrado() throws Exception {
        assertEquals(InicioLocal.PORTA_LIVRE, InicioLocal.parar(dados));
        Files.createDirectories(InicioLocal.arquivoProcesso(dados).getParent());
        Files.writeString(InicioLocal.arquivoProcesso(dados), "não é um pid");
        assertEquals(InicioLocal.PORTA_LIVRE, InicioLocal.parar(dados));
    }
}
