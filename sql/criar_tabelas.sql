
CREATE TABLE IF NOT EXISTS mensagem (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    remetente TEXT NOT NULL,
    texto TEXT NOT NULL,
    horario TEXT NOT NULL,
    -- MENSAGEM (participante), ENTRADA ou SAIDA (avisos de presença)
    tipo TEXT NOT NULL DEFAULT 'MENSAGEM'
);

-- Bancos criados antes dos avisos de presença recebem a coluna
-- automaticamente ao iniciar o servidor (JdbcMensagemRepository):
-- ALTER TABLE mensagem ADD COLUMN tipo TEXT NOT NULL DEFAULT 'MENSAGEM';

CREATE INDEX IF NOT EXISTS idx_mensagem_horario
ON mensagem (horario);
