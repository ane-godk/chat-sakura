
CREATE TABLE IF NOT EXISTS mensagem (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    remetente TEXT NOT NULL,
    texto TEXT NOT NULL,
    horario TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mensagem_horario
ON mensagem (horario);
