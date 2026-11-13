# Roteiro do Vídeo de Demonstração — Chat Sakura

Duração prevista: **cerca de 6 minutos**.

Convenções:
- **[TELA]**: ação que aparece na gravação.
- **[FALA]**: sugestão de narração.

---

## Preparação (antes de gravar)

1. Confira o Java 17 e o Maven:
   ```powershell
   java -version
   mvn -v
   ```
2. Compile e rode os testes na raiz do projeto (devem aparecer **306 testes, 0 falhas**):
   ```powershell
   mvn clean test
   ```
3. **Use um banco só para a demonstração**, criado em uma pasta separada ao lado do projeto:
   ```powershell
   cd ..
   mkdir chat-sakura-demo
   cd chat-sakura-demo
   ```
4. **Configure o Qwen** no mesmo terminal em que o servidor vai rodar (veja "Sakura IA (Qwen)" no README).
   - **API** (valor fictício):
     ```powershell
     $env:QWEN_API_KEY = "sk-sua-chave-aqui"
     ```
   - **Ollama local:**
     ```powershell
     $env:QWEN_BASE_URL = "http://localhost:11434/v1"
     $env:QWEN_MODEL = "qwen2.5:3b"
     ```
   - **Sem acesso ao Qwen:** `$env:CHAT_SAKURA_IA = "simulado"` usa o provedor SIMULADO, e as respostas aparecem marcadas com `[SIMULADO]`. Nesse caso, **diga no vídeo** que a resposta é simulada.
5. Deixe quatro terminais abertos:
   - **T1**: servidor, na pasta `chat-sakura-demo`;
   - **T2, T3 e T4**: clientes, na pasta `chat-sakura`.

**Comandos:**

O servidor usa TLS: crie antes o certificado de desenvolvimento (README, seção "Conexão segura (TLS)").

- **Servidor (T1, na pasta `chat-sakura-demo`):**
  ```powershell
  $env:CHAT_SAKURA_TLS_KEYSTORE = "$env:USERPROFILE\chat-sakura-certificados\servidor.p12"
  $env:CHAT_SAKURA_TLS_KEYSTORE_SENHA = Read-Host "Senha do keystore"
  mvn -q -f ..\chat-sakura\pom.xml compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
  ```
- **Cliente (T2, T3 e T4, na pasta `chat-sakura`):**
  ```powershell
  $env:CHAT_SAKURA_TRUSTSTORE = "$env:USERPROFILE\chat-sakura-certificados\confianca.p12"
  $env:CHAT_SAKURA_TRUSTSTORE_SENHA = Read-Host "Senha do truststore"
  java -cp "target\classes;$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar" br.unicesumar.chat.client.ClienteChat
  ```

---

## 1. Abertura do servidor (0:00 – 0:30)

- **[TELA]** Em T1, inicie o servidor. Mostre as linhas `Servidor iniciado na porta 12345` e `Sakura IA: Qwen qwen-plus` (ou o provedor configurado).
- **[FALA]** "Este é o Chat Sakura: servidor TCP em Java 17, com uma thread por cliente e histórico em SQLite. Na inicialização, o banco é criado ou migrado com backup, e o servidor mostra qual modelo Qwen vai usar."

## 2. Login dos clientes (0:30 – 1:15)

- **[TELA]** Em T2, T3 e T4, abra os clientes. Em cada um: **Conectar** → **Criar conta**:
  - `ana.souza` / `Ana`;
  - `bia.lima` / `Bia`;
  - `caio.reis` / `Caio`.

  Use uma senha qualquer com 8 caracteres ou mais.
- **[FALA]** "As contas têm senha protegida com PBKDF2, e quem define a identidade de cada sessão é o servidor. Repare na barra lateral de conversas."

## 3. Chat Geral (1:15 – 1:45)

- **[TELA]** Ana, Bia e Caio enviam uma mensagem no **Chat Geral**; as três chegam a todos, na mesma ordem.
- **[FALA]** "O Chat Geral é aberto a todos. Cada mensagem é gravada antes de ser distribuída, com sincronização para manter a ordem."

## 4. Conversa privada (1:45 – 2:30)

- **[TELA]** Na janela da Ana, clique em **Privada**, escolha **Bia** e envie "Segredo só nosso". Na janela da Bia, a conversa aparece com o contador **1**. Abra-a e responda.
- **[TELA]** Mostre a janela do Caio: a conversa não existe para ele.
- **[FALA]** "A conversa privada é entregue somente às duas participantes. O servidor confere a participação em cada operação: o Caio não consegue listar, ler nem enviar."

## 5. Grupo (2:30 – 3:00)

- **[TELA]** Ana clica em **Grupo**, dá o nome "Turma Sakura" e seleciona Bia e Caio (Ctrl + clique). Os três trocam mensagens no grupo.
- **[FALA]** "No grupo, quem cria é administradora e os convidados são membros. As mensagens ficam salvas, e quem estiver offline as recebe depois."

## 6. Avatares (3:00 – 3:20)

- **[TELA]** Ana clica no avatar ao lado do botão de tema e escolhe o **Gatinho**. Na janela da Bia, a conversa privada com a Ana passa a mostrar o gatinho.
- **[FALA]** "Os avatares vêm de uma galeria fixa, validada pelo servidor e salva no banco."

## 7. Criação de personagem (3:20 – 3:50)

- **[TELA]** Ana clica em **IA** e preenche:
  - **Nome:** `Naruto`
  - **Personalidade:** animado, determinado, amigável, motivador e descontraído.
  - **Contexto:** estamos treinando juntos na Vila da Folha; Naruto deve interpretar esse personagem em uma conversa fictícia, mantendo uma postura encorajadora.
- **[FALA]** "Cada personagem tem a própria conversa, visível só para quem o criou. A conversa avisa que é uma IA, e não uma pessoa real."

## 8. Conversa com o Qwen (3:50 – 4:40)

- **[TELA]** Ana envia "Oi, Naruto! Vamos treinar hoje?". O título mostra **digitando…**; enquanto isso, a janela continua respondendo, e Bia envia uma mensagem no Chat Geral. A resposta do Qwen chega.
- **[FALA]** "A pergunta vai ao Qwen pelo servidor, nunca pelo cliente. A chave fica em variável de ambiente, e a chamada roda em segundo plano, com tempo limite e limite de uso. O chat continua funcionando enquanto a IA pensa."
- *(Se estiver usando o SIMULADO: "Nesta gravação a resposta é simulada, marcada como [SIMULADO]; com a chave configurada, o mesmo fluxo usa o Qwen.")*

## 9. Persistência após reconexão (4:40 – 5:20)

- **[TELA]** Feche todas as janelas e encerre o servidor (`Ctrl + C`). Inicie o servidor de novo e entre como Ana (**Entrar**).
- **[TELA]** Mostre que o avatar, a conversa privada, o grupo e o **Naruto** continuam lá, com o histórico.
- **[FALA]** "Contas, conversas, avatares, personagens e históricos ficam no SQLite e voltam após reiniciar."

## 10. Aparência e temas florais (5:20 – 5:40)

- **[TELA]** Na janela da Ana, clique em **Aparência**, escolha **Fuji**, **Escuro** e fundo **Floral**, observe a prévia e clique em **Aplicar**. Na janela da Bia, escolha **Myosotis**, **Claro** e fundo **Liso**. Mostre que uma janela não muda a outra. Clique no botão de lua/sol para alternar só o modo. Mostre também **Exportar TXT** da conversa ativa.
- **[FALA]** "Cada conta escolhe a paleta (Sakura, Myosotis, Fuji ou Suzuran), o modo claro ou escuro e o fundo da conversa. A escolha fica no servidor e volta no próximo login. Há ainda sons, animações e exportação em TXT."

## 10b. Pesquisa e mensagens fixadas (5:40 – 6:00)

- **[TELA]** No grupo, clique na **lupa**, pesquise "reunião" e clique num resultado: a mensagem original é destacada. Clique com o botão direito numa mensagem e escolha **Fixar mensagem**; abra o **alfinete** para ver a lista. Na janela da Bia (membro), o alfinete aparece, mas o menu não oferece fixar.
- **[FALA]** "A pesquisa usa o histórico salvo, sem diferenciar acentos, com filtros de remetente e período. Cada conversa pode ter até 10 mensagens fixadas; nos grupos, só a administração fixa. O servidor verifica tudo."

## 11. Conclusão (6:00 – 6:20)

- **[TELA]** Mostre no VS Code a estrutura de pacotes (`model`, `persistence`, `service`, `protocolo`, `ia`, `server`, `client`) e o resultado `Tests run: 306, Failures: 0`.
- **[FALA]** "O projeto separa domínio, persistência, serviços, protocolo, IA, servidor e cliente, e tem 306 testes automatizados. Os detalhes estão no README e no relatório técnico. Obrigada!"

---

## Se algo der errado durante a gravação

| Situação | O que fazer |
|---|---|
| "Sakura IA indisponível: ... não está configurada" | O servidor foi iniciado sem as variáveis do Qwen. Encerre-o, defina `QWEN_API_KEY` (ou as do Ollama) no **mesmo** terminal e inicie de novo. |
| "Chave do Qwen inválida" ou "Limite de uso" | Confira a chave e a cota no console do Model Studio, ou use `CHAT_SAKURA_IA=simulado` e informe no vídeo. |
| "O serviço local do Qwen (Ollama) não está em execução" | Rode `ollama serve` e confirme o modelo com `ollama list`. |
| Porta 12345 em uso | Feche o outro servidor. |
| Cliente não abre (`NoClassDefFoundError: com/google/gson`) | Use o comando de cliente com a Gson no classpath, ou `mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.client.ClienteChat"`. |
