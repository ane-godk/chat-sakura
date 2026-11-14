package br.unicesumar.chat.local;

import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorIndisponivel;
import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.server.LimitesServidor;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.server.TransporteServidor;
import br.unicesumar.chat.service.HashSenha;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Execução local de um clique (usada pelo {@code INICIAR-CHAT-SAKURA.bat}).
 *
 * <ul>
 *   <li>{@code verificar}: a porta 12345 está livre, já tem um servidor do
 *       Chat Sakura ou está ocupada por outro programa?</li>
 *   <li>{@code aguardar <segundos>}: espera o servidor local responder;</li>
 *   <li>{@code servidor}: servidor em TCP de desenvolvimento, somente em
 *       127.0.0.1, com o banco em {@code dados\};</li>
 *   <li>{@code cliente} (padrão): janela no modo local, já apontando para
 *       127.0.0.1:12345 sem TLS.</li>
 * </ul>
 *
 * O modo local é definido aqui, no código, e não por variáveis de ambiente:
 * o servidor comum ({@link Servidor#main}) continua exigindo TLS. Nenhum
 * certificado, keystore ou senha técnica é usado.
 */
public final class InicioLocal {

    public static final int PORTA = Servidor.PORTA_PADRAO;

    /** Primeira linha que o servidor envia a cada conexão (modo sem TLS). */
    static final String SAUDACAO = "SISTEMA: Bem-vindo ao Chat Sakura!";

    /** Códigos de saída lidos pelo arquivo .bat. */
    static final int SAKURA_ATIVO = 0;
    static final int PORTA_LIVRE = 1;
    static final int PORTA_OCUPADA = 2;
    static final int SEM_RESPOSTA = 3;
    static final int FALHA = 4;

    /** Situação da porta local. */
    enum EstadoPorta {
        LIVRE, CHAT_SAKURA, OUTRO_PROGRAMA
    }

    private InicioLocal() {
    }

    public static void main(String[] args) {
        String comando = args.length == 0 ? "cliente" : args[0];
        switch (comando) {
            case "verificar" -> System.exit(verificar(PORTA));
            case "aguardar" -> System.exit(aguardar(PORTA, args.length > 1 ? segundos(args[1]) : 60,
                    marcadorFalha(pastaDados())));
            case "servidor" -> System.exit(servidor(pastaDados()));
            case "parar" -> System.exit(parar(pastaDados()));
            case "cliente" -> cliente(pastaDados());
            default -> {
                System.err.println("Comando desconhecido: " + comando
                        + " (use verificar, aguardar, servidor ou cliente).");
                System.exit(FALHA);
            }
        }
    }

    // ------------------------------------------------------------------
    // Porta
    // ------------------------------------------------------------------

    /** Conecta em 127.0.0.1 e lê a primeira linha, sem enviar nada. */
    static EstadoPorta sondar(int porta, int prazoMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), prazoMs);
            socket.setSoTimeout(prazoMs);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String linha = entrada.readLine();
            return SAUDACAO.equals(linha) ? EstadoPorta.CHAT_SAKURA : EstadoPorta.OUTRO_PROGRAMA;
        } catch (ConnectException e) {
            return EstadoPorta.LIVRE;
        } catch (SocketTimeoutException e) {
            // Conectou, mas não respondeu como o servidor local (por exemplo, um servidor TLS).
            return EstadoPorta.OUTRO_PROGRAMA;
        } catch (IOException e) {
            return EstadoPorta.OUTRO_PROGRAMA;
        }
    }

    static int verificar(int porta) {
        return switch (sondar(porta, 2_000)) {
            case LIVRE -> {
                System.out.println("A porta " + porta + " está livre.");
                yield PORTA_LIVRE;
            }
            case CHAT_SAKURA -> {
                System.out.println("Já existe um servidor do Chat Sakura aberto neste computador.");
                yield SAKURA_ATIVO;
            }
            case OUTRO_PROGRAMA -> {
                System.out.println("A porta " + porta + " está sendo usada por outro programa"
                        + " (pode ser um servidor do Chat Sakura no modo TLS).");
                yield PORTA_OCUPADA;
            }
        };
    }

    /**
     * Espera o servidor responder. Desiste no prazo ou assim que o servidor
     * registrar que não conseguiu iniciar ({@code marcador}).
     */
    static int aguardar(int porta, int segundos, Path marcador) {
        long limite = System.nanoTime() + segundos * 1_000_000_000L;
        while (System.nanoTime() < limite) {
            EstadoPorta estado = sondar(porta, 1_000);
            if (estado == EstadoPorta.CHAT_SAKURA) {
                System.out.println("Servidor pronto.");
                return SAKURA_ATIVO;
            }
            if (marcador != null && Files.exists(marcador)) {
                System.out.println("O servidor não conseguiu iniciar.");
                return FALHA;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return SEM_RESPOSTA;
            }
        }
        System.out.println("O servidor não respondeu em " + segundos + " s.");
        return SEM_RESPOSTA;
    }

    private static int segundos(String texto) {
        try {
            return Math.max(1, Math.min(600, Integer.parseInt(texto.strip())));
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    // ------------------------------------------------------------------
    // Servidor e cliente
    // ------------------------------------------------------------------

    /** Criado pelo servidor quando não consegue iniciar; o .bat o apaga antes de abrir o servidor. */
    static Path marcadorFalha(Path dados) {
        return dados.resolve("logs").resolve("servidor-falhou.txt");
    }

    /**
     * Servidor local: TCP sem TLS somente em 127.0.0.1 (nunca em outra
     * interface, seja qual for o ambiente: as variáveis CHAT_SAKURA_MODO e
     * CHAT_SAKURA_ENDERECO não são lidas aqui) e banco em {@code dados}.
     *
     * @param porta    12345 no uso normal (0 nos testes: porta livre)
     * @param provedor IA do servidor
     * @throws IOException se a pasta de dados não puder ser criada
     */
    public static Servidor criarServidor(Path dados, int porta, ProvedorIA provedor) throws IOException {
        Files.createDirectories(dados);
        return new Servidor(porta,
                Servidor.Servicos.montar(new ConnectionFactory(dados.resolve("chat-sakura.db").toString()),
                        new HashSenha(), Clock.systemDefaultZone(), provedor, LimitesServidor.padrao()),
                TransporteServidor.tcpDesenvolvimento());
    }

    /** Identificação do processo do servidor aberto pelo inicializador (PID e horário de início). */
    static Path arquivoProcesso(Path dados) {
        return dados.resolve("logs").resolve("servidor.pid");
    }

    /** Avisa a janela do servidor que o encerramento foi pedido (fecha sem pedir tecla). */
    static Path marcadorParada(Path dados) {
        return dados.resolve("logs").resolve("servidor-parado.txt");
    }

    /**
     * Encerra somente o servidor aberto pelo inicializador nesta pasta: o PID
     * gravado precisa existir e ter o mesmo horário de início (um PID reutilizado
     * por outro programa nunca é encerrado).
     */
    static int parar(Path dados) {
        java.util.Optional<ProcessHandle> processo = processoDoServidor(dados);
        if (processo.isEmpty()) {
            System.out.println("O servidor já estava fechado.");
            return PORTA_LIVRE;
        }
        try {
            Files.writeString(marcadorParada(dados), "Encerrado pelo inicializador.", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // A janela do servidor apenas pedirá uma tecla para fechar.
        }
        processo.get().destroy();
        try {
            processo.get().onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            processo.get().destroyForcibly();
        }
        System.out.println("Servidor encerrado.");
        return SAKURA_ATIVO;
    }

    static java.util.Optional<ProcessHandle> processoDoServidor(Path dados) {
        try {
            String[] partes = Files.readString(arquivoProcesso(dados), StandardCharsets.UTF_8).strip().split(";");
            long pid = Long.parseLong(partes[0]);
            String inicio = partes.length > 1 ? partes[1] : "";
            return ProcessHandle.of(pid)
                    .filter(ProcessHandle::isAlive)
                    .filter(p -> p.info().startInstant().map(Object::toString).orElse("?").equals(inicio));
        } catch (IOException | RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    private static void registrarProcesso(Path dados) {
        ProcessHandle atual = ProcessHandle.current();
        try {
            Files.writeString(arquivoProcesso(dados), atual.pid() + ";"
                    + atual.info().startInstant().map(Object::toString).orElse("?"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Aviso: sem " + arquivoProcesso(dados).getFileName()
                    + "; para encerrar o servidor, feche a janela dele.");
        }
    }

    /** @return código de saída (só retorna se o servidor não puder continuar) */
    static int servidor(Path dados) {
        int codigo = executarServidor(dados);
        try {
            Files.writeString(marcadorFalha(dados), "O servidor não iniciou. Veja servidor.log.", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // Sem o marcador, o .bat apenas espera o prazo.
        }
        return codigo;
    }

    private static int executarServidor(Path dados) {
        registrarSaida(dados, "servidor.log");
        registrarProcesso(dados);
        Path banco = dados.resolve("chat-sakura.db");
        System.out.println("Chat Sakura (modo local): dados em " + banco);

        Servidor servidor;
        try {
            servidor = criarServidor(dados, PORTA, ProvedorIndisponivel.doAmbienteComOllama(System.getenv()));
        } catch (IOException | RuntimeException e) {
            System.err.println("Não foi possível abrir o banco de dados " + banco + ": " + e.getMessage());
            System.err.println("Confira se a pasta pode ser gravada (não use uma pasta somente leitura,"
                    + " como dentro do ZIP sem extrair) e se outro programa não está usando o arquivo.");
            return FALHA;
        }

        // Só retorna se não conseguir abrir a porta (a causa já foi explicada no log).
        servidor.iniciar();
        return FALHA;
    }

    static void cliente(Path dados) {
        registrarSaida(dados, "cliente.log");
        ClienteChat.abrir(true);
    }

    /**
     * Pasta {@code dados} ao lado do programa: na pasta do projeto quando o
     * JAR está em {@code target}, ou ao lado do JAR no pacote portátil.
     * Calculada a partir do JAR (e não de argumentos), o que funciona com
     * espaços e acentos no caminho.
     */
    static Path pastaDados() {
        try {
            Path origem = Path.of(InicioLocal.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return pastaDados(origem);
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of("dados").toAbsolutePath();
        }
    }

    static Path pastaDados(Path origem) {
        Path pasta = Files.isDirectory(origem) ? origem : origem.getParent();
        // target\chat-sakura.jar ou target\classes: a raiz do projeto fica acima de target.
        while (pasta != null && pasta.getFileName() != null
                && (pasta.getFileName().toString().equals("target") || pasta.getFileName().toString().equals("classes"))) {
            pasta = pasta.getParent();
        }
        return (pasta == null ? Path.of(".") : pasta).toAbsolutePath().resolve("dados");
    }

    /** Mantém a saída na janela e também em {@code dados\logs\<arquivo>} (UTF-8). */
    private static void registrarSaida(Path dados, String arquivo) {
        try {
            Path logs = dados.resolve("logs");
            Files.createDirectories(logs);
            OutputStream destino = Files.newOutputStream(logs.resolve(arquivo),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            String inicio = System.lineSeparator() + "===== "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    + " =====" + System.lineSeparator();
            destino.write(inicio.getBytes(StandardCharsets.UTF_8));
            System.setOut(new PrintStream(new SaidaDuplicada(System.out, destino), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new SaidaDuplicada(System.err, destino), true, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            System.err.println("Aviso: não foi possível gravar o log em " + dados.resolve("logs")
                    + " (" + e.getMessage() + "). As mensagens continuam nesta janela.");
        }
    }
}
