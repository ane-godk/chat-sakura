package br.unicesumar.chat.client;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Preferências locais do usuário em {@code ~/.chat-sakura/preferencias.properties}
 * (nunca no SQLite do chat): tema, sons e animações.
 *
 * <h2>Precedência na inicialização</h2>
 * <ol>
 *   <li>Propriedade {@code -D} válida ({@code chat.sakura.tema},
 *       {@code chat.sakura.sons}, {@code chat.sakura.animacoes});</li>
 *   <li>valor válido salvo no arquivo;</li>
 *   <li>padrão: Sakura Claro, sons ligados, animações completas.</li>
 * </ol>
 * Uma propriedade {@code -D} vale só para a execução: ela não é gravada no
 * arquivo. O arquivo só muda quando o usuário altera uma preferência.
 *
 * <h2>Robustez</h2>
 * Leitura tolerante (linhas inválidas são ignoradas, valores inválidos usam o
 * padrão só daquela chave); gravação em fila única, relendo o arquivo para
 * preservar chaves desconhecidas, com arquivo temporário e substituição
 * atômica. Falhas são registradas e nunca interrompem o chat.
 */
final class PreferenciasSakura {

    static final String CHAVE_TEMA = "tema";
    static final String CHAVE_SONS = "sons";
    static final String CHAVE_ANIMACOES = "animacoes";

    static final String PROPRIEDADE_SONS = NotificacoesSonoras.PROPRIEDADE;

    /** Valores efetivos de uma execução. */
    record Valores(GerenciadorTema.Tema tema, boolean silenciado, ConfiguracaoAnimacoes.Modo animacoes) {
        static final Valores PADRAO = new Valores(
                GerenciadorTema.Tema.CLARO, false, ConfiguracaoAnimacoes.Modo.COMPLETAS);
    }

    private static final class Instancia {
        static final PreferenciasSakura PADRAO = new PreferenciasSakura(arquivoPadrao());
    }

    private final Path arquivo;
    private final ExecutorService gravacao = Executors.newSingleThreadExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "sakura-preferencias");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean avisouFalha;

    PreferenciasSakura(Path arquivo) {
        this.arquivo = arquivo;
    }

    /** Preferências do usuário atual (arquivo na pasta pessoal). */
    static PreferenciasSakura padrao() {
        return Instancia.PADRAO;
    }

    static Path arquivoPadrao() {
        return Paths.get(System.getProperty("user.home"), ".chat-sakura", "preferencias.properties");
    }

    Path arquivo() {
        return arquivo;
    }

    // ---------- Interpretação dos valores ----------

    static Optional<GerenciadorTema.Tema> tema(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "claro", "light" -> Optional.of(GerenciadorTema.Tema.CLARO);
            case "noturno", "escuro", "dark" -> Optional.of(GerenciadorTema.Tema.NOTURNO);
            default -> Optional.empty();
        };
    }

    /** @return {@code true} para silenciados */
    static Optional<Boolean> silenciado(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "silenciados", "silenciado", "false", "off", "nao", "não" -> Optional.of(true);
            case "ligados", "ligado", "true", "on", "sim" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    static Optional<ConfiguracaoAnimacoes.Modo> animacoes(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "completas", "completa" -> Optional.of(ConfiguracaoAnimacoes.Modo.COMPLETAS);
            case "reduzidas", "reduzida" -> Optional.of(ConfiguracaoAnimacoes.Modo.REDUZIDAS);
            case "desativadas", "desativada", "false", "off" -> Optional.of(ConfiguracaoAnimacoes.Modo.DESATIVADAS);
            default -> Optional.empty();
        };
    }

    static String texto(GerenciadorTema.Tema tema) {
        return tema == GerenciadorTema.Tema.NOTURNO ? "noturno" : "claro";
    }

    static String textoSons(boolean silenciado) {
        return silenciado ? "silenciados" : "ligados";
    }

    static String texto(ConfiguracaoAnimacoes.Modo modo) {
        return modo.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolve os valores da execução: {@code -D} válida, depois o arquivo,
     * depois o padrão — chave por chave.
     */
    static Valores resolver(Properties arquivo, String propTema, String propSons, String propAnimacoes) {
        return new Valores(
                tema(propTema).or(() -> tema(arquivo.getProperty(CHAVE_TEMA)))
                        .orElse(Valores.PADRAO.tema()),
                silenciado(propSons).or(() -> silenciado(arquivo.getProperty(CHAVE_SONS)))
                        .orElse(Valores.PADRAO.silenciado()),
                animacoes(propAnimacoes).or(() -> animacoes(arquivo.getProperty(CHAVE_ANIMACOES)))
                        .orElse(Valores.PADRAO.animacoes())
        );
    }

    /** Valores para esta execução, a partir do arquivo e das propriedades do sistema. */
    Valores valoresIniciais() {
        return resolver(ler(),
                System.getProperty(GerenciadorTema.PROPRIEDADE),
                System.getProperty(PROPRIEDADE_SONS),
                System.getProperty(ConfiguracaoAnimacoes.PROPRIEDADE));
    }

    // ---------- Leitura ----------

    /**
     * Lê o arquivo de forma tolerante. Arquivo ausente ou ilegível resulta em
     * propriedades vazias; linhas malformadas são ignoradas uma a uma.
     */
    Properties ler() {

        Properties propriedades = new Properties();

        if (!Files.isRegularFile(arquivo)) {
            return propriedades;
        }

        String conteudo;
        try {
            conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            avisar("não foi possível ler " + arquivo + ": " + e.getMessage());
            return propriedades;
        }

        try (Reader leitor = new StringReader(conteudo)) {
            propriedades.load(leitor);
            return propriedades;
        } catch (IOException | IllegalArgumentException e) {
            // Corrompido: recupera linha por linha o que for válido.
            propriedades.clear();
            for (String linha : conteudo.split("\\R")) {
                try (Reader leitorLinha = new StringReader(linha)) {
                    propriedades.load(leitorLinha);
                } catch (IOException | IllegalArgumentException ignorada) {
                    // Linha inválida: descartada.
                }
            }
            return propriedades;
        }
    }

    // ---------- Gravação ----------

    void salvarTema(GerenciadorTema.Tema tema) {
        salvar(CHAVE_TEMA, texto(tema));
    }

    void salvarSons(boolean silenciado) {
        salvar(CHAVE_SONS, textoSons(silenciado));
    }

    void salvarAnimacoes(ConfiguracaoAnimacoes.Modo modo) {
        salvar(CHAVE_ANIMACOES, texto(modo));
    }

    /** Agenda a gravação de uma chave (fila única, em ordem). Não bloqueia. */
    void salvar(String chave, String valor) {
        try {
            gravacao.execute(() -> gravarAgora(chave, valor));
        } catch (RuntimeException e) {
            avisar("gravação recusada: " + e.getMessage());
        }
    }

    /** Aguarda as gravações pendentes (usado nos testes e ao encerrar). */
    boolean aguardarGravacoes(long tempoMs) {
        try {
            return gravacao.submit(() -> { }).get(tempoMs, TimeUnit.MILLISECONDS) == null;
        } catch (Exception e) {
            return false;
        }
    }

    private void gravarAgora(String chave, String valor) {
        try {
            // Relê para não perder chaves desconhecidas nem outras alterações.
            Properties atuais = ler();
            atuais.setProperty(chave, valor);

            Path pasta = arquivo.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }

            Path temporario = Files.createTempFile(pasta, "preferencias", ".tmp");
            try {
                try (Writer escritor = Files.newBufferedWriter(temporario, StandardCharsets.UTF_8)) {
                    atuais.store(escritor, "Preferencias do Chat Sakura (tema, sons, animacoes)");
                }
                try {
                    Files.move(temporario, arquivo,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporario);
            }
        } catch (IOException | RuntimeException e) {
            avisar("não foi possível salvar " + arquivo + ": " + e.getMessage());
        }
    }

    private void avisar(String mensagem) {
        if (!avisouFalha) {
            avisouFalha = true;
            System.err.println("Chat Sakura (preferências): " + mensagem
                    + " — o chat continua com os valores atuais.");
        }
    }
}
