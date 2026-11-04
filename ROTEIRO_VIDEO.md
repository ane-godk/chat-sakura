# Roteiro do Vídeo de Demonstração — Chat Sakura

Duração prevista: **cerca de 4 min 30 s** (faixa aceitável de 3 a 5 minutos).

Convenções usadas neste roteiro:

- **[TELA]** é uma ação que precisa aparecer na gravação.
- **[FALA]** é uma sugestão de narração. Pode ser dita enquanto a ação acontece ou sobre a tela parada.

---

## 1. Objetivo do vídeo

Demonstrar o Chat Sakura em funcionamento e mostrar, na prática, os itens avaliados:

| Critério da avaliação | Onde aparece no vídeo |
|---|---|
| Multithreading correto | Parte 2 (três clientes atendidos ao mesmo tempo) e parte 6 (queda de um cliente sem derrubar o servidor) |
| Broadcast e sincronização | Parte 3 (mensagens chegam a todos, na mesma ordem) e parte 8 (explicação) |
| Persistência JDBC e histórico | Parte 5 (histórico ao reconectar) e parte 7 (histórico preservado após reiniciar o servidor) |
| Organização em pacotes | Parte 8 (estrutura de pastas no VS Code) |
| Testes unitários | Parte 9 (`mvn clean test`) |
| Organização do código e relatório | Parte 8 e encerramento (menção ao `RELATORIO_TECNICO.md`) |

---

## 2. Preparação do ambiente (fazer antes de gravar)

### 2.1 Conferir Java e Maven

Em um terminal PowerShell:

```powershell
java -version
mvn -v
```

O `java -version` deve indicar a versão 17 (ou superior), e o Maven deve estar instalado.

### 2.2 Compilar e testar o projeto

Na pasta do projeto (`chat-sakura`):

```powershell
mvn clean test
```

O resultado esperado é `Tests run: 52, Failures: 0, Errors: 0` e `BUILD SUCCESS`.

Em seguida, compile as classes que serão executadas:

```powershell
mvn -q compile
```

### 2.3 Criar um banco separado só para a gravação

**Não apague o banco `chat-sakura.db` do projeto.** O servidor cria o arquivo `chat-sakura.db` na pasta de onde ele é iniciado. Por isso, basta iniciá-lo a partir de outra pasta para ter um banco limpo, exclusivo da gravação.

Crie uma pasta ao lado do projeto (fora do repositório Git):

```powershell
cd ..
mkdir chat-sakura-demo
```

A estrutura fica assim:

```text
ProjetosJava\
├── chat-sakura\        (projeto)
└── chat-sakura-demo\   (banco da gravação será criado aqui)
```

> Os comandos a seguir supõem que a pasta do projeto se chama `chat-sakura`. Se o nome for outro, ajuste o caminho `..\chat-sakura\pom.xml`.

### 2.4 Ocultar caminhos pessoais no terminal

O prompt do PowerShell mostra o caminho completo da pasta, o que pode incluir o nome do usuário do Windows. Antes de gravar, execute em **cada** terminal:

```powershell
function prompt { "PS> " }
```

Isso vale apenas para a sessão atual e não altera nada no sistema.

### 2.5 Abrir os terminais

Abra **quatro** janelas do PowerShell (ou abas do Windows Terminal):

| Terminal | Pasta | Uso |
|---|---|---|
| T1 — Servidor | `chat-sakura-demo` | Servidor |
| T2 — Cliente Ana | `chat-sakura` | Cliente 1 |
| T3 — Cliente Bia | `chat-sakura` | Cliente 2 |
| T4 — Cliente Caio | `chat-sakura` | Cliente 3 |

Deixe o VS Code aberto, com o projeto carregado, para a parte 8.

### 2.6 Comandos que serão usados

**Servidor** (T1, na pasta `chat-sakura-demo`):

```powershell
mvn -q -f ..\chat-sakura\pom.xml exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

**Cliente** (T2, T3 e T4, na pasta `chat-sakura`):

```powershell
java -cp target\classes br.unicesumar.chat.client.ClienteChat
```

O cliente não depende de bibliotecas externas, por isso pode ser iniciado direto com `java`, o que é mais rápido. Se preferir usar o Maven, o comando equivalente é:

```powershell
mvn -q exec:java "-Dexec.mainClass=br.unicesumar.chat.client.ClienteChat"
```

**Encerrar o servidor:** `Ctrl + C` no terminal T1. Se aparecer a pergunta `Deseja finalizar o arquivo em lotes (S/N)?`, digite `S` e pressione Enter.

### 2.7 Organizar as janelas na tela

Todas as janelas do cliente abrem no centro da tela, sobrepostas (tamanho inicial 800 × 580). Depois de abri-las, organize em uma **grade 2 × 2**, usando o encaixe do Windows (`Win + ←` ou `Win + →`, seguido de `Win + ↑` ou `Win + ↓`):

```text
┌──────────────────────┬──────────────────────┐
│  Cliente Ana         │  Cliente Bia         │
├──────────────────────┼──────────────────────┤
│  Cliente Caio        │  T1 — Servidor       │
└──────────────────────┴──────────────────────┘
```

Em uma tela Full HD, cada quadrante fica com 960 × 540, acima do tamanho mínimo da janela (600 × 420). Os terminais T2, T3 e T4 podem ficar atrás das janelas, pois não mostram nada de importante.

> Faça um ensaio completo antes de gravar. Ao final do ensaio, encerre tudo e apague **apenas** o `chat-sakura.db` da pasta `chat-sakura-demo`, se quiser começar a gravação com o banco de demonstração vazio.

---

## 3. Sequência da gravação

| Parte | Tempo | Conteúdo |
|---|---|---|
| 1 | 0:00 – 0:20 | Abertura |
| 2 | 0:20 – 1:00 | Servidor e três clientes conectados |
| 3 | 1:00 – 1:40 | Troca de mensagens e broadcast |
| 4 | 1:40 – 2:05 | Rejeição de apelido duplicado |
| 5 | 2:05 – 2:35 | Reconexão e recuperação do histórico |
| 6 | 2:35 – 2:55 | Encerramento abrupto de um cliente |
| 7 | 2:55 – 3:30 | Persistência: reinício do servidor |
| 8 | 3:30 – 4:00 | Exportação TXT e explicação técnica |
| 9 | 4:00 – 4:30 | Testes automatizados e encerramento |

### Parte 1 — Abertura (0:00 – 0:20)

**[TELA]** VS Code aberto no projeto, ou a grade de janelas ainda sem clientes conectados.

**[FALA]**
> "Olá! Este é o Chat Sakura, um chat multiusuário com histórico, desenvolvido em Java 17 com Maven. O sistema usa sockets TCP, uma thread por cliente, persistência em SQLite com JDBC e interface gráfica em Swing. Vou demonstrar o funcionamento e, no final, a execução dos testes automatizados."

### Parte 2 — Servidor e três clientes (0:20 – 1:00)

**[TELA]**

1. No T1, execute o comando do servidor. Aguarde aparecer:
   ```text
   Servidor iniciado na porta 12345
   Aguardando conexões...
   ```
2. Em cada janela de cliente, clique em **Conectar** e preencha:
   - Nome: `Ana`, depois `Bia`, depois `Caio`;
   - Servidor: `localhost`;
   - Porta: `12345`.
3. Mostre que cada janela exibe `SISTEMA: Conectado como ...` e o bloco `===== HISTÓRICO RECENTE =====` (vazio na primeira conexão com o banco da gravação).
4. Mostre no T1 as linhas `Ana entrou. Clientes: 1`, `Bia entrou. Clientes: 2` e `Caio entrou. Clientes: 3`.

**[FALA]**
> "Iniciei o servidor na porta 12345. Agora vou conectar três clientes, com apelidos diferentes. O servidor aceita cada conexão e cria uma nova thread com um ClienteHandler só para aquele cliente. Por isso os três são atendidos ao mesmo tempo. No terminal do servidor dá para ver a contagem de clientes conectados."

### Parte 3 — Troca de mensagens e broadcast (1:00 – 1:40)

**[TELA]** Envie, em sequência:

- Ana: `Oi, pessoal!`
- Bia: `Oi, Ana! Tudo bem?`
- Caio: `Cheguei também!`

Mostre que **as três mensagens aparecem nas três janelas, na mesma ordem**, inclusive na janela de quem enviou.

**[FALA]**
> "Quando um cliente envia uma mensagem, o servidor primeiro valida o texto, depois grava a mensagem no banco SQLite e só então faz o broadcast para todos os conectados. A gravação e o envio acontecem dentro do mesmo bloco sincronizado. Por isso, todos recebem as mensagens exatamente na mesma ordem."

### Parte 4 — Rejeição de apelido duplicado (1:40 – 2:05)

**[TELA]**

1. Na janela do Caio, clique em **Sair**.
2. Clique em **Conectar** e informe o nome `ana`, em minúsculas, com servidor `localhost` e porta `12345`.
3. Mostre as mensagens:
   ```text
   SISTEMA: Não foi possível entrar: Já existe um usuário conectado com esse nome.
   SISTEMA: O servidor recusou a conexão.
   ```

**[FALA]**
> "Agora vou tentar entrar com o apelido 'ana', em minúsculas, enquanto a Ana ainda está conectada. O servidor recusa, porque a comparação de nomes não diferencia maiúsculas de minúsculas. A verificação e o registro do nome acontecem dentro do mesmo bloco sincronizado. Assim, duas pessoas nunca conseguem usar o mesmo nome ao mesmo tempo."

### Parte 5 — Reconexão e recuperação do histórico (2:05 – 2:35)

**[TELA]**

1. Na janela do Caio, clique em **Conectar** com o nome `Caio`.
2. Mostre o bloco entre `===== HISTÓRICO RECENTE =====` e `===== FIM DO HISTÓRICO =====`, com as três mensagens da parte 3, **cada uma uma única vez**.
3. Bia envia: `Bem-vindo de volta, Caio!`. Mostre que a mensagem aparece **depois** do fim do histórico.

**[FALA]**
> "Ao reconectar, o Caio recebe as últimas mensagens gravadas no banco, em ordem cronológica e sem repetição. O servidor lê o histórico e registra o cliente de forma atômica. Então cada mensagem chega pelo histórico ou ao vivo, nunca pelos dois caminhos. As mensagens novas aparecem depois do histórico."

### Parte 6 — Encerramento abrupto de um cliente (2:35 – 2:55)

**[TELA]**

1. Traga o terminal T4 (o do Caio) para frente e feche a janela do terminal no **X**. Isso encerra o processo do cliente sem o comando de saída.
2. Mostre no T1 a mensagem de desconexão do Caio (`Caio saiu. Clientes: 2`, podendo vir precedida de `Conexão encerrada: ...`).
3. Ana envia: `O servidor continua funcionando!`. Mostre que Bia recebe normalmente.

**[FALA]**
> "Agora vou encerrar o cliente do Caio de forma abrupta, fechando o processo. O servidor detecta a queda, remove o Caio da lista e fecha o socket. Apenas a thread dele é encerrada: o servidor e os outros clientes continuam funcionando."

### Parte 7 — Persistência: reinício do servidor (2:55 – 3:30)

**[TELA]**

1. No T1, pressione `Ctrl + C` para encerrar o servidor (confirme com `S`, se solicitado). Mostre que as janelas de Ana e Bia passam a indicar **Desconectado**.
2. Inicie o servidor de novo com o mesmo comando, na mesma pasta `chat-sakura-demo`.
3. Na janela da Ana, clique em **Conectar** com o nome `Ana`.
4. Mostre que o histórico traz as mensagens enviadas **antes** do reinício.

**[FALA]**
> "Vou desligar o servidor e ligá-lo de novo. Como as mensagens ficam gravadas no SQLite, elas não se perdem. Ao reconectar, a Ana recebe o histórico com as mensagens de antes do reinício. O acesso ao banco é feito com JDBC: as operações com dados do usuário usam PreparedStatement, e todos os recursos são fechados com try-with-resources."

### Parte 8 — Exportação TXT e explicação técnica (3:30 – 4:00)

**[TELA]**

1. Na janela da Ana, clique em **Exportar TXT**, escolha a pasta `chat-sakura-demo` e salve. Mostre a mensagem `Histórico exportado com sucesso!`.
2. *(Opcional, se houver tempo)* Abra o arquivo `.txt` no Bloco de Notas por alguns segundos.
3. Mostre no VS Code a árvore `src/main/java/br/unicesumar/chat` com os pacotes `model`, `persistence`, `persistence/jdbc`, `service`, `server` e `client`.
4. *(Opcional)* Abra o método `ClientRegistry.transmitir` e mostre o bloco `synchronized (clientes)`.

> Atenção: a janela de salvar arquivo mostra o caminho da pasta. Navegue até `chat-sakura-demo` antes de gravar essa parte, ou desfoque o caminho na edição.

**[FALA]**
> "A conversa exibida pode ser exportada para um arquivo TXT. Quanto à organização, o projeto é dividido em camadas: model, com as entidades imutáveis; persistence, com o repositório e a implementação JDBC; service, com as regras do chat e a sincronização; server, com o servidor TCP; e client, com a interface Swing. A lista de clientes é compartilhada entre as threads e protegida por synchronized. O broadcast percorre essa lista dentro de synchronized (clientes), e a falha de um cliente não impede o envio para os demais. Os detalhes estão no relatório técnico do projeto."

### Parte 9 — Testes automatizados e encerramento (4:00 – 4:30)

**[TELA]**

1. Feche as janelas dos clientes e encerre o servidor com `Ctrl + C`.
   > **Importante:** o `mvn clean` apaga a pasta `target`, que é usada pelo servidor e pelos clientes em execução. Por isso, execute os testes só depois de encerrar tudo.
2. Em um terminal na pasta `chat-sakura`, execute:
   ```powershell
   mvn clean test
   ```
3. Mostre o resumo final:
   ```text
   Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
   BUILD SUCCESS
   ```

> A execução leva alguns segundos. Se preferir, acelere esse trecho na edição, mas não substitua a execução real por uma imagem.

**[FALA]**
> "Por fim, os testes automatizados com JUnit 5. São 52 testes, que verificam as validações, a persistência, o broadcast, a rejeição de nomes duplicados e o histórico. Também há testes de concorrência, que usam ExecutorService e awaitTermination para simular vários clientes ao mesmo tempo. Todos passaram. Agradeço a atenção!"

---

## 4. O que pode ser apenas narrado

Os pontos abaixo não precisam de demonstração visual. Podem ser explicados enquanto outra ação acontece na tela:

- uma thread por conexão (`ClienteHandler implements Runnable`);
- a mensagem é gravada antes do broadcast;
- `synchronized (clientes)` e o bloqueio único de mensagens no `ChatService`;
- histórico lido e cliente registrado de forma atômica;
- `PreparedStatement` e `try-with-resources` no JDBC;
- atualizações da interface feitas com `SwingUtilities.invokeLater`.

---

## 5. Checklist antes de gravar

- [ ] `java -version` mostra Java 17 ou superior.
- [ ] `mvn clean test` mostra 52 testes e `BUILD SUCCESS`.
- [ ] `mvn -q compile` executado depois dos testes.
- [ ] Pasta `chat-sakura-demo` criada fora do repositório; o banco do projeto não será usado.
- [ ] A porta 12345 não está em uso, ou seja, não há outro servidor aberto.
- [ ] Prompt do PowerShell simplificado (`function prompt { "PS> " }`) em todos os terminais.
- [ ] Notificações do Windows e de aplicativos desativadas (modo "Não perturbe").
- [ ] Janelas organizadas em grade 2 × 2 e tamanho de fonte legível.
- [ ] Ensaio completo realizado pelo menos uma vez.
- [ ] Microfone testado.

## 6. Checklist antes de entregar

- [ ] Duração entre 3 e 5 minutos.
- [ ] Aparecem: servidor iniciado, três clientes conectados, broadcast, apelido duplicado recusado, histórico na reconexão, queda abrupta sem derrubar o servidor, histórico após reiniciar o servidor, exportação TXT e `mvn clean test` com sucesso.
- [ ] Áudio audível em todo o vídeo.
- [ ] Nenhum dado pessoal visível (nome de usuário do Windows, e-mails, outros arquivos).
- [ ] O arquivo TXT exportado e o banco `chat-sakura-demo\chat-sakura.db` **não** foram adicionados ao repositório.
- [ ] Vídeo assistido do início ao fim antes do envio.
