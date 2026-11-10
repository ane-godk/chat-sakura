# Migrações do banco SQLite

O esquema do banco é criado e atualizado pelo `MigradorBanco` (`br.unicesumar.chat.persistence.jdbc`). Ele roda sempre que o servidor inicia, porque o `JdbcMensagemRepository` o chama no construtor. A versão do esquema fica gravada no próprio arquivo, em `PRAGMA user_version`.

O esquema atual está em [criar_tabelas.sql](criar_tabelas.sql). Um teste automatizado confere que esse script produz exatamente o mesmo esquema que o migrador.

## Versões

| Versão | Conteúdo |
|---|---|
| 0 | Banco vazio ou legado. Nenhuma versão anterior gravava `user_version`. Pode ter só a tabela `mensagem`, com ou sem a coluna `tipo`. |
| 1 | `mensagem` com a coluna `tipo` (`MENSAGEM`, `ENTRADA` ou `SAIDA`). Corresponde à migração que já existia no repositório. |
| 2 | Tabelas `usuario`, `conversa`, `conversa_participante`, `conversa_privada` e `personagem`. A conversa Geral é criada com `id = 1`. Cada mensagem ganha `conversa_id` e `remetente_id`. |
| 3 | Coluna `conversa_participante.ultima_lida_id` (padrão 0). Guarda a última mensagem lida por participante, para que o contador de não lidas sobreviva à reconexão e ao reinício. É um `ADD COLUMN` simples, que não altera nenhum dado existente. |

### O que a migração para a versão 2 faz com o histórico

- Cada mensagem antiga vai para a conversa Geral (`conversa_id = 1`). O `id`, o remetente textual, o texto, o horário e o tipo são preservados exatamente como estavam, inclusive os avisos `ENTRADA` e `SAIDA`. A ordem é mantida.
- O `remetente_id` fica `NULL` em todas as mensagens antigas. Nenhuma conta é criada a partir de um apelido antigo, e um apelido antigo nunca é associado automaticamente a uma conta futura com o mesmo nome.
- O contador do `AUTOINCREMENT` é preservado. O id de uma mensagem já apagada não é reutilizado.
- O SQLite não permite acrescentar uma coluna com `REFERENCES` e valor padrão não nulo. Por isso a tabela `mensagem` é reconstruída: cria-se `mensagem_v2`, os dados são copiados, a tabela antiga é apagada e a nova é renomeada. Nenhuma outra tabela referencia `mensagem`.
- O histórico enviado ao conectar continua vindo só da conversa Geral. Mensagens de outras conversas nunca entram nele.

## Como a migração é feita

1. O migrador lê `user_version`.
   - Se a versão for **maior** que a suportada, o servidor não inicia e o arquivo não é alterado.
   - Se for **igual**, ele apenas verifica a integridade (`foreign_key_check` e `quick_check`), sem alterar nada. Por isso executar o servidor de novo não duplica nem modifica dados.
2. Se o banco já tem tabelas, ele faz um **backup consistente** antes de qualquer alteração:
   - O backup usa `VACUUM INTO`, que lê um instantâneo da base, incluindo o que ainda está no arquivo `-wal` em modo WAL, e grava um arquivo novo.
   - Não é uma cópia simples do arquivo aberto.
   - O backup é conferido com `quick_check`.
   - Se o backup falhar, a migração não começa.
3. Todas as migrações pendentes rodam numa **única transação**, junto com a gravação da nova `user_version`. Antes do commit, ele verifica chaves estrangeiras e integridade.
4. Se qualquer etapa falhar, a transação é desfeita, o que inclui as alterações de esquema. O banco fica exatamente na versão anterior. O erro (`MigracaoBancoException`) informa a causa e o caminho do backup, e o servidor não inicia com um esquema incompleto.

O `ConnectionFactory` ativa `PRAGMA foreign_keys = ON` em toda conexão, pois no SQLite essa configuração vale por conexão.

## Backups

- **Nome:** `<banco>.backup-v<versão anterior>-<aaaaMMdd-HHmmss>.db`, por exemplo `chat-sakura.backup-v0-20261110-120000.db`.
- **Local:** a mesma pasta do banco. Se já existir um arquivo com esse nome, um sufixo `-2`, `-3`… é acrescentado.
- Os backups são ignorados pelo Git (`*.db`) e não são apagados automaticamente. Depois de confirmar que tudo funciona, eles podem ser removidos manualmente.

## Como restaurar um backup

1. **Encerre o servidor** (`Ctrl + C`). Nenhum processo pode estar usando o banco.
2. Na pasta do banco, guarde o arquivo atual e os arquivos auxiliares, se existirem:
   ```powershell
   Rename-Item chat-sakura.db chat-sakura.db.com-problema
   if (Test-Path chat-sakura.db-wal) { Rename-Item chat-sakura.db-wal chat-sakura.db-wal.com-problema }
   if (Test-Path chat-sakura.db-shm) { Remove-Item chat-sakura.db-shm }
   ```
3. Copie o backup para o nome original:
   ```powershell
   Copy-Item chat-sakura.backup-v0-20261110-120000.db chat-sakura.db
   ```
4. Inicie o servidor:
   - **com a mesma versão do Chat Sakura:** o banco restaurado é migrado de novo, e um novo backup é criado antes;
   - **com a versão anterior do Chat Sakura:** ela usa o banco restaurado sem alteração.

Dica: um backup pode ser inspecionado sem risco abrindo uma **cópia** dele em qualquer ferramenta SQLite. O comando `PRAGMA user_version;` mostra a versão.

## Como acrescentar uma nova migração

1. Escreva `migrarParaVn(Connection)` no `MigradorBanco` e acrescente-a ao fim de `MIGRACOES`. A versão atual é o tamanho da lista.
2. Use somente SQL fixo ou `PreparedStatement`. Não chame `commit`: a transação é controlada pelo migrador.
3. Atualize `criar_tabelas.sql` e esta página.
4. Crie testes em bancos temporários: a partir de cada versão anterior, com falha simulada e com reexecução.
