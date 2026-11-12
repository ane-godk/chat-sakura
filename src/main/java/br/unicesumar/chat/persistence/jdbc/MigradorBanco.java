package br.unicesumar.chat.persistence.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.unicesumar.chat.model.NormalizacaoNome;

/**
 * Migrações versionadas do banco SQLite, controladas por
 * {@code PRAGMA user_version}.
 *
 * <ul>
 *   <li><b>0</b>: banco vazio ou legado (tabela {@code mensagem} com ou sem
 *       a coluna {@code tipo}); nenhum banco anterior definia a versão.</li>
 *   <li><b>1</b>: tabela {@code mensagem} com a coluna {@code tipo}.</li>
 *   <li><b>2</b>: usuários, conversas, participantes, pares privados e
 *       personagens; toda mensagem pertence a uma conversa.</li>
 *   <li><b>3</b>: {@code conversa_participante.ultima_lida_id} (não lidas).</li>
 *   <li><b>4</b>: {@code usuario.apelido_chave} único (apelidos equivalentes
 *       após normalização Unicode não coexistem).</li>
 *   <li><b>5</b>: {@code memoria_personagem} (memória resumida por conversa).</li>
 * </ul>
 *
 * Garantias: antes de alterar um banco existente é feito um backup
 * consistente com {@code VACUUM INTO} (válido também em modo WAL); todas as
 * migrações pendentes rodam numa única transação, com verificação de chaves
 * estrangeiras e de integridade antes do commit; qualquer falha desfaz tudo
 * e o banco permanece na versão anterior. Um banco de versão futura é
 * recusado sem ser alterado. Todo o SQL é fixo, sem dados externos.
 */
public final class MigradorBanco {

    /** Identificador estável da conversa Geral. */
    public static final long ID_CONVERSA_GERAL = 1;

    /** Uma migração leva o banco da versão {@code n - 1} para {@code n}. */
    @FunctionalInterface
    interface Migracao {
        void aplicar(Connection conexao) throws SQLException;
    }

    /** Posição {@code i} da lista leva o banco à versão {@code i + 1}. */
    static final List<Migracao> MIGRACOES = List.of(
            MigradorBanco::migrarParaV1,
            MigradorBanco::migrarParaV2,
            MigradorBanco::migrarParaV3,
            MigradorBanco::migrarParaV4,
            MigradorBanco::migrarParaV5
    );

    public static final int VERSAO_ATUAL = MIGRACOES.size();

    private static final DateTimeFormatter FORMATO_BACKUP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Resultado da inicialização; {@code backup} é nulo se não foi preciso. */
    public record Resultado(int versaoAnterior, int versaoAtual, Path backup) {

        public boolean migrou() {
            return versaoAnterior != versaoAtual;
        }
    }

    private final ConnectionFactory fabrica;
    private final List<Migracao> migracoes;
    private final Clock relogio;

    public MigradorBanco(ConnectionFactory fabrica) {
        this(fabrica, MIGRACOES, Clock.systemDefaultZone());
    }

    MigradorBanco(ConnectionFactory fabrica, List<Migracao> migracoes, Clock relogio) {
        this.fabrica = Objects.requireNonNull(fabrica, "A fábrica de conexões é obrigatória.");
        this.migracoes = List.copyOf(migracoes);
        this.relogio = Objects.requireNonNull(relogio, "O relógio é obrigatório.");
    }

    /**
     * Leva o banco à versão atual. Em um banco já atualizado apenas verifica
     * a integridade, sem alterar nada.
     *
     * @throws MigracaoBancoException se o banco é de versão futura, se o
     *                                backup falhar ou se a migração falhar
     */
    public Resultado migrar() {

        int versaoAlvo = migracoes.size();

        try (Connection conexao = fabrica.abrirConexao()) {

            int versao = lerVersao(conexao);

            if (versao > versaoAlvo) {
                throw new MigracaoBancoException(
                        "O banco está na versão " + versao
                                + ", mais nova que a suportada (" + versaoAlvo
                                + "). Ele não foi alterado; atualize o Chat Sakura."
                );
            }

            if (versao == versaoAlvo) {
                verificarIntegridade(conexao);
                return new Resultado(versao, versao, null);
            }

            Path backup = possuiTabelas(conexao)
                    ? criarBackup(conexao, versao)
                    : null;

            aplicarMigracoes(conexao, versao, versaoAlvo, backup);

            if (backup != null) {
                System.out.println(
                        "Banco migrado da versão " + versao + " para a "
                                + versaoAlvo + ". Backup: " + backup
                );
            }

            return new Resultado(versao, versaoAlvo, backup);

        } catch (SQLException e) {
            throw new MigracaoBancoException(
                    "Não foi possível abrir ou verificar o banco de dados: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private void aplicarMigracoes(
            Connection conexao,
            int versao,
            int versaoAlvo,
            Path backup
    ) throws SQLException {

        conexao.setAutoCommit(false);

        try {
            for (int proxima = versao + 1; proxima <= versaoAlvo; proxima++) {
                migracoes.get(proxima - 1).aplicar(conexao);
            }

            definirVersao(conexao, versaoAlvo);
            verificarIntegridade(conexao);

            conexao.commit();

        } catch (SQLException | RuntimeException e) {

            try {
                conexao.rollback();
            } catch (SQLException falhaRollback) {
                e.addSuppressed(falhaRollback);
            }

            throw new MigracaoBancoException(
                    "A migração do banco da versão " + versao + " para a "
                            + versaoAlvo + " falhou e foi desfeita; o banco"
                            + " permanece na versão " + versao + "."
                            + (backup == null ? "" : " Backup: " + backup + ".")
                            + " Causa: " + e.getMessage(),
                    e
            );

        } finally {
            conexao.setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // Migrações
    // ------------------------------------------------------------------

    /** Versão 1: garante a tabela {@code mensagem} com a coluna {@code tipo}. */
    static void migrarParaV1(Connection conexao) throws SQLException {

        try (Statement comando = conexao.createStatement()) {

            if (!existeTabela(conexao, "mensagem")) {
                comando.executeUpdate("""
                        CREATE TABLE mensagem (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            remetente TEXT NOT NULL,
                            texto TEXT NOT NULL,
                            horario TEXT NOT NULL,
                            tipo TEXT NOT NULL DEFAULT 'MENSAGEM'
                        )
                        """);

            } else if (!possuiColuna(conexao, "mensagem", "tipo")) {
                // Registros anteriores aos avisos de presença: todos são
                // mensagens; nenhum evento é inventado.
                comando.executeUpdate(
                        "ALTER TABLE mensagem ADD COLUMN "
                                + "tipo TEXT NOT NULL DEFAULT 'MENSAGEM'"
                );
            }
        }
    }

    /**
     * Versão 2: novas tabelas, conversa Geral e reconstrução de
     * {@code mensagem} com {@code conversa_id} e {@code remetente_id}.
     *
     * Mensagens legadas vão para a Geral preservando id, remetente, texto,
     * horário e tipo; {@code remetente_id} fica NULL, pois apelidos antigos
     * não são associados a contas autenticadas.
     */
    static void migrarParaV2(Connection conexao) throws SQLException {

        try (Statement comando = conexao.createStatement()) {

            comando.executeUpdate("""
                    CREATE TABLE usuario (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        login TEXT NOT NULL COLLATE NOCASE UNIQUE
                            CHECK (length(login) BETWEEN 1 AND 30),
                        apelido TEXT NOT NULL
                            CHECK (length(apelido) BETWEEN 1 AND 30),
                        senha_hash TEXT NOT NULL CHECK (length(senha_hash) > 0),
                        senha_sal TEXT NOT NULL CHECK (length(senha_sal) > 0),
                        avatar_id TEXT NOT NULL DEFAULT 'sakura',
                        criado_em TEXT NOT NULL
                    )
                    """);

            comando.executeUpdate("""
                    CREATE TABLE personagem (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        dono_id INTEGER NOT NULL
                            REFERENCES usuario (id) ON DELETE CASCADE,
                        nome TEXT NOT NULL CHECK (length(nome) BETWEEN 1 AND 40),
                        personalidade TEXT NOT NULL,
                        contexto TEXT NOT NULL DEFAULT '',
                        criado_em TEXT NOT NULL
                    )
                    """);

            comando.executeUpdate("""
                    CREATE TABLE conversa (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tipo TEXT NOT NULL
                            CHECK (tipo IN ('GERAL', 'PRIVADA', 'GRUPO', 'IA')),
                        nome TEXT,
                        criador_id INTEGER
                            REFERENCES usuario (id) ON DELETE SET NULL,
                        personagem_id INTEGER
                            REFERENCES personagem (id) ON DELETE CASCADE,
                        criado_em TEXT NOT NULL,
                        CHECK ((tipo = 'IA') = (personagem_id IS NOT NULL)),
                        CHECK (tipo <> 'GRUPO'
                            OR (nome IS NOT NULL AND length(trim(nome)) > 0))
                    )
                    """);

            comando.executeUpdate("""
                    CREATE TABLE conversa_participante (
                        conversa_id INTEGER NOT NULL
                            REFERENCES conversa (id) ON DELETE CASCADE,
                        usuario_id INTEGER NOT NULL
                            REFERENCES usuario (id) ON DELETE CASCADE,
                        papel TEXT NOT NULL DEFAULT 'MEMBRO'
                            CHECK (papel IN ('ADMIN', 'MEMBRO')),
                        entrou_em TEXT NOT NULL,
                        PRIMARY KEY (conversa_id, usuario_id)
                    )
                    """);

            comando.executeUpdate("""
                    CREATE TABLE conversa_privada (
                        conversa_id INTEGER PRIMARY KEY
                            REFERENCES conversa (id) ON DELETE CASCADE,
                        usuario_menor_id INTEGER NOT NULL
                            REFERENCES usuario (id) ON DELETE CASCADE,
                        usuario_maior_id INTEGER NOT NULL
                            REFERENCES usuario (id) ON DELETE CASCADE,
                        CHECK (usuario_menor_id < usuario_maior_id),
                        UNIQUE (usuario_menor_id, usuario_maior_id)
                    )
                    """);

            // Uma única conversa Geral, com id estável.
            comando.executeUpdate(
                    "CREATE UNIQUE INDEX ux_conversa_geral ON conversa (tipo) "
                            + "WHERE tipo = 'GERAL'"
            );
            comando.executeUpdate(
                    "CREATE INDEX idx_conversa_personagem ON conversa (personagem_id)"
            );
            comando.executeUpdate(
                    "CREATE INDEX idx_conversa_criador ON conversa (criador_id)"
            );
            comando.executeUpdate(
                    "CREATE INDEX idx_participante_usuario "
                            + "ON conversa_participante (usuario_id)"
            );
            comando.executeUpdate(
                    "CREATE INDEX idx_privada_maior ON conversa_privada (usuario_maior_id)"
            );
            comando.executeUpdate(
                    "CREATE INDEX idx_personagem_dono ON personagem (dono_id)"
            );

            comando.executeUpdate(
                    "INSERT INTO conversa (id, tipo, nome, criado_em) VALUES ("
                            + ID_CONVERSA_GERAL + ", 'GERAL', 'Chat Geral', "
                            + "strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'))"
            );

            // Reconstrução de mensagem (o SQLite não acrescenta coluna com
            // REFERENCES e valor padrão não nulo). Nada referencia mensagem.
            comando.executeUpdate("""
                    CREATE TABLE mensagem_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        conversa_id INTEGER NOT NULL
                            REFERENCES conversa (id) ON DELETE CASCADE,
                        remetente_id INTEGER
                            REFERENCES usuario (id) ON DELETE SET NULL,
                        remetente TEXT NOT NULL,
                        texto TEXT NOT NULL,
                        horario TEXT NOT NULL,
                        tipo TEXT NOT NULL DEFAULT 'MENSAGEM'
                    )
                    """);

            comando.executeUpdate(
                    "INSERT INTO mensagem_v2 "
                            + "(id, conversa_id, remetente_id, remetente, texto, horario, tipo) "
                            + "SELECT id, " + ID_CONVERSA_GERAL
                            + ", NULL, remetente, texto, horario, tipo "
                            + "FROM mensagem ORDER BY id"
            );

            // Preserva o contador do AUTOINCREMENT: ids de mensagens antigas
            // apagadas nunca são reutilizados.
            long sequenciaAntiga = lerSequencia(conexao, "mensagem");

            comando.executeUpdate("DROP TABLE mensagem");
            comando.executeUpdate("ALTER TABLE mensagem_v2 RENAME TO mensagem");

            preservarSequencia(conexao, "mensagem", sequenciaAntiga);

            comando.executeUpdate(
                    "CREATE INDEX idx_mensagem_conversa ON mensagem (conversa_id, id)"
            );
            comando.executeUpdate(
                    "CREATE INDEX idx_mensagem_remetente ON mensagem (remetente_id)"
            );
        }
    }

    /**
     * Versão 3: última mensagem lida por participante (contador de não
     * lidas que sobrevive à reconexão). Coluna sem chave estrangeira: 0
     * significa "nenhuma lida".
     */
    static void migrarParaV3(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate(
                    "ALTER TABLE conversa_participante ADD COLUMN "
                            + "ultima_lida_id INTEGER NOT NULL DEFAULT 0"
            );
        }
    }

    /**
     * Versão 4: {@code usuario.apelido_chave}, a chave normalizada do apelido
     * (NFKC + minúsculas, {@link NormalizacaoNome}), com índice único. O
     * apelido exibido não muda.
     *
     * Contas existentes com apelidos equivalentes ("Ána" e "ÁNA", "Ana" e
     * "Ａｎａ") impedem a migração: nenhuma conta é escolhida, renomeada ou
     * excluída automaticamente. O erro lista as contas envolvidas e a
     * transação é desfeita (o banco fica na v3, e o backup permanece).
     */
    static void migrarParaV4(Connection conexao) throws SQLException {

        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate("ALTER TABLE usuario ADD COLUMN apelido_chave TEXT");
        }

        Map<String, List<String>> contasPorChave = new LinkedHashMap<>();
        Map<Long, String> chavePorId = new LinkedHashMap<>();

        try (Statement comando = conexao.createStatement();
             ResultSet contas = comando.executeQuery("SELECT id, apelido FROM usuario ORDER BY id")) {
            while (contas.next()) {
                long id = contas.getLong("id");
                String apelido = contas.getString("apelido");
                String chave = NormalizacaoNome.chave(apelido);
                chavePorId.put(id, chave);
                contasPorChave.computeIfAbsent(chave, c -> new ArrayList<>())
                        .add("id " + id + " (\"" + apelido + "\")");
            }
        }

        List<String> colisoes = new ArrayList<>();
        contasPorChave.forEach((chave, contas) -> {
            if (contas.size() > 1) {
                colisoes.add(String.join(", ", contas));
            }
        });

        if (!colisoes.isEmpty()) {
            throw new SQLException(
                    "Há contas com apelidos equivalentes após a normalização Unicode: "
                            + String.join("; ", colisoes)
                            + ". Nenhuma conta foi alterada. Renomeie manualmente uma"
                            + " das contas de cada grupo e inicie o servidor novamente."
            );
        }

        try (PreparedStatement atualizar = conexao.prepareStatement(
                "UPDATE usuario SET apelido_chave = ? WHERE id = ?")) {
            for (Map.Entry<Long, String> conta : chavePorId.entrySet()) {
                atualizar.setString(1, conta.getValue());
                atualizar.setLong(2, conta.getKey());
                atualizar.executeUpdate();
            }
        }

        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate(
                    "CREATE UNIQUE INDEX ux_usuario_apelido_chave ON usuario (apelido_chave)"
            );
        }
    }

    /**
     * Versão 5: {@code memoria_personagem}, a memória resumida de cada
     * conversa com um personagem. A chave é a conversa (uma memória por
     * conversa), com o dono e o personagem gravados para conferência; apagar
     * a conversa, o personagem ou a conta apaga a memória (CASCADE). Como os
     * ids são AUTOINCREMENT, um personagem recriado nunca herda memória.
     */
    static void migrarParaV5(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate("""
                    CREATE TABLE memoria_personagem (
                        conversa_id INTEGER PRIMARY KEY
                            REFERENCES conversa (id) ON DELETE CASCADE,
                        dono_id INTEGER NOT NULL
                            REFERENCES usuario (id) ON DELETE CASCADE,
                        personagem_id INTEGER NOT NULL
                            REFERENCES personagem (id) ON DELETE CASCADE,
                        resumo TEXT NOT NULL DEFAULT ''
                            CHECK (length(resumo) <= 1200),
                        ate_mensagem_id INTEGER NOT NULL DEFAULT 0,
                        esquecer_ate_id INTEGER NOT NULL DEFAULT 0,
                        atualizada_em TEXT NOT NULL
                    )
                    """);
            comando.executeUpdate(
                    "CREATE INDEX idx_memoria_dono ON memoria_personagem (dono_id)"
            );
        }
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /**
     * Cópia consistente do banco com {@code VACUUM INTO}: o SQLite lê um
     * instantâneo da base (incluindo o que ainda está no arquivo WAL) e grava
     * um arquivo novo e compacto. A cópia é conferida com {@code quick_check}.
     */
    private Path criarBackup(Connection conexao, int versao) throws SQLException {

        Path banco = Path.of(fabrica.caminhoBanco()).toAbsolutePath();
        String nome = banco.getFileName().toString();
        String base = nome.endsWith(".db") ? nome.substring(0, nome.length() - 3) : nome;
        String carimbo = LocalDateTime.now(relogio).format(FORMATO_BACKUP);

        Path backup = banco.resolveSibling(base + ".backup-v" + versao + "-" + carimbo + ".db");

        for (int sufixo = 2; Files.exists(backup); sufixo++) {
            backup = banco.resolveSibling(
                    base + ".backup-v" + versao + "-" + carimbo + "-" + sufixo + ".db");
        }

        try (PreparedStatement comando = conexao.prepareStatement("VACUUM INTO ?")) {
            comando.setString(1, backup.toString());
            comando.executeUpdate();
        } catch (SQLException e) {
            throw new MigracaoBancoException(
                    "Não foi possível criar o backup antes da migração; o banco "
                            + "não foi alterado. Causa: " + e.getMessage(),
                    e
            );
        }

        try (Connection copia = new ConnectionFactory(backup.toString()).abrirConexao();
             Statement comando = copia.createStatement();
             ResultSet resultado = comando.executeQuery("PRAGMA quick_check")) {

            if (!resultado.next() || !"ok".equalsIgnoreCase(resultado.getString(1))) {
                throw new MigracaoBancoException(
                        "O backup " + backup + " não passou na verificação; "
                                + "o banco não foi alterado."
                );
            }
        }

        return backup;
    }

    static int lerVersao(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery("PRAGMA user_version")) {
            return resultado.next() ? resultado.getInt(1) : 0;
        }
    }

    /** PRAGMA não aceita parâmetro; o valor é um inteiro interno. */
    private static void definirVersao(Connection conexao, int versao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate("PRAGMA user_version = " + versao);
        }
    }

    /** Chaves estrangeiras e estrutura do arquivo íntegras. */
    private static void verificarIntegridade(Connection conexao) throws SQLException {

        try (Statement comando = conexao.createStatement()) {

            try (ResultSet violacoes = comando.executeQuery("PRAGMA foreign_key_check")) {
                if (violacoes.next()) {
                    throw new SQLException(
                            "Violação de chave estrangeira na tabela "
                                    + violacoes.getString("table") + "."
                    );
                }
            }

            try (ResultSet resultado = comando.executeQuery("PRAGMA quick_check")) {
                if (!resultado.next() || !"ok".equalsIgnoreCase(resultado.getString(1))) {
                    throw new SQLException(
                            "Verificação de integridade falhou: "
                                    + resultado.getString(1)
                    );
                }
            }
        }
    }

    private static boolean possuiTabelas(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(
                     "SELECT count(*) FROM sqlite_master "
                             + "WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
            return resultado.next() && resultado.getInt(1) > 0;
        }
    }

    static boolean existeTabela(Connection conexao, String tabela) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            comando.setString(1, tabela);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next();
            }
        }
    }

    /** Nome de tabela interno e fixo (PRAGMA não aceita parâmetro). */
    private static boolean possuiColuna(Connection conexao, String tabela, String coluna)
            throws SQLException {
        try (Statement comando = conexao.createStatement();
             ResultSet colunas = comando.executeQuery("PRAGMA table_info(" + tabela + ")")) {
            while (colunas.next()) {
                if (coluna.equalsIgnoreCase(colunas.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static long lerSequencia(Connection conexao, String tabela) throws SQLException {
        if (!existeTabela(conexao, "sqlite_sequence")) {
            return 0;
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT seq FROM sqlite_sequence WHERE name = ?")) {
            comando.setString(1, tabela);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? resultado.getLong(1) : 0;
            }
        }
    }

    private static void preservarSequencia(Connection conexao, String tabela, long minimo)
            throws SQLException {

        if (minimo <= lerSequencia(conexao, tabela)) {
            return;
        }

        try (PreparedStatement atualizar = conexao.prepareStatement(
                "UPDATE sqlite_sequence SET seq = ? WHERE name = ?")) {
            atualizar.setLong(1, minimo);
            atualizar.setString(2, tabela);

            if (atualizar.executeUpdate() == 0) {
                try (PreparedStatement inserir = conexao.prepareStatement(
                        "INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)")) {
                    inserir.setString(1, tabela);
                    inserir.setLong(2, minimo);
                    inserir.executeUpdate();
                }
            }
        }
    }
}
