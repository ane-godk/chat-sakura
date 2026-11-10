-- Chat Sakura - esquema atual do banco SQLite (versão 3).
--
-- O servidor NÃO executa este arquivo: o esquema é criado e migrado por
-- br.unicesumar.chat.persistence.jdbc.MigradorBanco na inicialização
-- (ver sql/MIGRACOES.md). Este script documenta o resultado e pode criar
-- um banco vazio equivalente para estudo.
--
-- As chaves estrangeiras valem por conexão no SQLite; o ConnectionFactory
-- ativa PRAGMA foreign_keys = ON em toda conexão.

PRAGMA foreign_keys = ON;

-- Contas autenticadas (o login ainda não está implementado).
-- login é único sem diferenciar maiúsculas; apelido é o nome exibido.
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
);

-- Personagens de IA criados por um usuário.
CREATE TABLE personagem (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dono_id INTEGER NOT NULL
        REFERENCES usuario (id) ON DELETE CASCADE,
    nome TEXT NOT NULL CHECK (length(nome) BETWEEN 1 AND 40),
    personalidade TEXT NOT NULL,
    contexto TEXT NOT NULL DEFAULT '',
    criado_em TEXT NOT NULL
);

-- Conversas. A Geral tem id 1 e é única; IA exige personagem
-- (e só IA tem personagem); grupo exige nome.
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
);

-- Quem participa de cada conversa privada, de grupo ou de IA.
-- A Geral é aberta e não usa esta tabela.
CREATE TABLE conversa_participante (
    conversa_id INTEGER NOT NULL
        REFERENCES conversa (id) ON DELETE CASCADE,
    usuario_id INTEGER NOT NULL
        REFERENCES usuario (id) ON DELETE CASCADE,
    papel TEXT NOT NULL DEFAULT 'MEMBRO'
        CHECK (papel IN ('ADMIN', 'MEMBRO')),
    entrou_em TEXT NOT NULL,
    -- Versão 3: última mensagem lida (contador de não lidas).
    ultima_lida_id INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (conversa_id, usuario_id)
);

-- Par canônico (menor id, maior id): no máximo uma conversa privada
-- por par de usuários.
CREATE TABLE conversa_privada (
    conversa_id INTEGER PRIMARY KEY
        REFERENCES conversa (id) ON DELETE CASCADE,
    usuario_menor_id INTEGER NOT NULL
        REFERENCES usuario (id) ON DELETE CASCADE,
    usuario_maior_id INTEGER NOT NULL
        REFERENCES usuario (id) ON DELETE CASCADE,
    CHECK (usuario_menor_id < usuario_maior_id),
    UNIQUE (usuario_menor_id, usuario_maior_id)
);

-- Mensagens e avisos de presença. remetente guarda o apelido exibido;
-- remetente_id fica NULL nas mensagens legadas e enquanto não houver login.
-- tipo: MENSAGEM, ENTRADA ou SAIDA.
CREATE TABLE mensagem (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversa_id INTEGER NOT NULL
        REFERENCES conversa (id) ON DELETE CASCADE,
    remetente_id INTEGER
        REFERENCES usuario (id) ON DELETE SET NULL,
    remetente TEXT NOT NULL,
    texto TEXT NOT NULL,
    horario TEXT NOT NULL,
    tipo TEXT NOT NULL DEFAULT 'MENSAGEM'
);

CREATE UNIQUE INDEX ux_conversa_geral ON conversa (tipo) WHERE tipo = 'GERAL';
CREATE INDEX idx_conversa_personagem ON conversa (personagem_id);
CREATE INDEX idx_conversa_criador ON conversa (criador_id);
CREATE INDEX idx_participante_usuario ON conversa_participante (usuario_id);
CREATE INDEX idx_privada_maior ON conversa_privada (usuario_maior_id);
CREATE INDEX idx_personagem_dono ON personagem (dono_id);
CREATE INDEX idx_mensagem_conversa ON mensagem (conversa_id, id);
CREATE INDEX idx_mensagem_remetente ON mensagem (remetente_id);

INSERT INTO conversa (id, tipo, nome, criado_em)
VALUES (1, 'GERAL', 'Chat Geral', strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'));

PRAGMA user_version = 3;
