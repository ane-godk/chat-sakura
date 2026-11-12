# Chat Sakura

O Chat Sakura é um aplicativo de mensagens desenvolvido em Java 17, com:
- servidor TCP multithread;
- cliente Swing com os temas Sakura Claro e Noturno;
- histórico persistente em SQLite.

Ele oferece **Chat Geral**, **conversas privadas**, **grupos**, contas com senha, **avatares** e a **Sakura IA**: conversas com personagens personalizados, interpretados por um modelo **Qwen**.

## Funcionalidades

- **Contas:** cadastro e login com senha (PBKDF2-HMAC-SHA256). Visitantes e clientes antigos continuam no Chat Geral.
- **Conversas:**
  - Chat Geral em tempo real para todos;
  - conversas privadas entre duas contas, reaproveitadas quando o par já conversou;
  - grupos com nome e participantes (ADMIN e MEMBRO).
- **Barra lateral:** conversas com avatar, prévia, contador de não lidas e indicador "digitando…" da IA.
- **Avatares:** galeria de 6 avatares, persistidos e exibidos aos demais.
- **Sakura IA:** personagens com nome, personalidade e contexto. As respostas vêm do Qwen, e o histórico é próprio de cada personagem e persistente.
- **Histórico:** fica no SQLite, com migrações versionadas e backup automático, e mensagens para quem está offline são recuperadas depois.
- **Segurança:** o servidor define a identidade e verifica a autorização em cada operação.
- **Protocolo:** JSON validado (protocolo v2), com compatibilidade com o protocolo de texto original.
- **Interface:** sons, animações, preferências, menu "Copiar mensagem" e exportação TXT da conversa ativa.
- **Testes:** 377 testes automatizados.

## Tecnologias

| Tecnologia | Versão / uso |
|---|---|
| Java | 17 (`maven.compiler.release`) |
| Maven | Build e testes |
| Sockets TCP | `ServerSocket` / `Socket` |
| Threads | Uma por conexão no servidor |
| JDBC + SQLite | `org.xerial:sqlite-jdbc` 3.46.1.3 |
| JSON (protocolo v2) | `com.google.code.gson:gson` 2.11.0 |
| Java Swing | Interface do cliente |
| JUnit 5 | `junit-jupiter` 5.11.4 |

### Requisitos

- JDK 17 ou superior (`java -version`)
- Apache Maven 3.x (`mvn -v`)
- Acesso à internet na primeira execução, para o Maven baixar as dependências

## Estrutura do projeto

```text
chat-sakura/
├── pom.xml
├── README.md
├── RELATORIO_TECNICO.md
├── ROTEIRO_VIDEO.md
├── AGENTS.md
├── sql/
│   ├── criar_tabelas.sql
│   └── MIGRACOES.md
└── src/
    ├── main/java/br/unicesumar/chat/
    │   ├── model/             Usuario, Mensagem, Conversa, PerfilUsuario, Personagem, CatalogoAvatares
    │   ├── persistence/       MensagemRepository, UsuarioRepository, ConversaRepository
    │   │   └── jdbc/          ConnectionFactory, Jdbc*Repository, MigradorBanco
    │   ├── service/           ChatService, ClientRegistry, ServicoAutenticacao, ServicoConversas, ServicoIA
    │   ├── protocolo/         ProtocoloSakura (handshake), ProtocoloJson (protocolo v2)
    │   ├── ia/                ProvedorIA, ProvedorQwen, ConfiguracaoQwen, ProvedorIAFalso
    │   ├── server/            Servidor, ClienteHandler, LeitorLinhas
    │   └── client/            ClienteChat, ConexaoServidor, ControladorConversas, BarraConversas, AvatarSakura, diálogos
    └── test/java/br/unicesumar/chat/   Testes JUnit 5
```

| Pacote | Responsabilidade |
|---|---|
| `model` | Entidades imutáveis e validações (domínio, sem dependência de rede ou JDBC) |
| `persistence` | Contrato do repositório e implementação em memória |
| `persistence.jdbc` | Implementação com SQLite |
| `service` | Regras do chat, broadcast, registro de clientes, sincronização e contas |
| `protocolo` | Formato do handshake de autenticação, compartilhado por servidor e cliente |
| `server` | Servidor TCP e protocolo com cada cliente |
| `ia` | Provedores de IA: Qwen (API compatível com OpenAI) e SIMULADO |
| `client` | Interface gráfica Swing |

## Como executar (Windows / PowerShell)

### 1. Compilar e testar

Na **raiz do projeto**:

```powershell
mvn clean test
```

### 2. Iniciar o servidor

Na **raiz do projeto**:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

O `compile` no mesmo comando garante que as classes existam, mesmo depois de um `mvn clean`.

Se tudo der certo, o terminal mostra:

```text
Servidor iniciado na porta 12345
Aguardando conexões...
```

Para encerrar, use `Ctrl + C` (se o Windows perguntar se deseja finalizar o arquivo em lotes, responda `S`).

### 3. Iniciar os clientes

Em **outro terminal**, na **raiz do projeto**:

```powershell
java -cp "target\classes;$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar" br.unicesumar.chat.client.ClienteChat
```

O cliente usa a biblioteca **Gson** (protocolo JSON v2), baixada pelo Maven no passo 1. Esse comando exige que o projeto já tenha sido compilado (passo 1 ou 2).

Uma alternativa pelo Maven, que já inclui a Gson:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.client.ClienteChat"
```

Na janela, clique em **Conectar** e escolha um modo:

| Modo | Campos |
|---|---|
| **Entrar** | login e senha de uma conta existente |
| **Criar conta** | login, apelido (nome exibido no chat), senha e confirmação |
| **Visitante** | apenas um nome, sem conta (como nas versões anteriores) |

Em todos os modos, o endereço do servidor é `localhost` e a porta é `12345`. Veja [Contas e autenticação](#contas-e-autenticação).

**Queda da conexão (com conta).** Se a conexão cair sem você clicar em **Sair**, o selo mostra "Reconectando..." e uma faixa acima da conversa informa as tentativas:
- O cliente tenta alcançar o servidor com intervalos crescentes: 1 s, 2 s, 4 s... até 30 s.
- Quando o servidor volta, a janela de login reabre com o servidor, a porta e o login preenchidos. A senha não fica guardada em lugar nenhum, então é pedida de novo.
- Depois do login, a lista de conversas é recarregada e a conversa que estava aberta volta, com o histórico.
- **Cancelar** (na faixa) ou **Sair** interrompem a espera.
- Mensagens que estavam saindo no momento da queda não são reenviadas automaticamente, para não duplicar; a faixa avisa quantas podem não ter chegado.
- Não há reconexão automática quando outro login da mesma conta assumiu a sessão, nem para visitantes.

**Avisos.** Erros recuperáveis (limite de envio, conversa não encontrada, histórico indisponível) aparecem numa faixa discreta acima da conversa, que some sozinha e nunca se empilha. Diálogos ficam só para o que exige ação, como login recusado.

### 4. Demonstrar três clientes simultâneos

1. Inicie o servidor (passo 2).
2. Abra **três terminais** na raiz do projeto e execute o comando do cliente em cada um.
3. Em cada janela, clique em **Conectar** → **Criar conta** (por exemplo `ana.souza`/`Ana`, `bia.lima`/`Bia` e `caio.reis`/`Caio`). Nas próximas vezes, use **Entrar**.
4. Envie mensagens: todas aparecem nas três janelas, na mesma ordem.

As janelas abrem sobrepostas no centro da tela. Use `Win + ←` / `Win + →` para organizá-las lado a lado.

O passo a passo completo da demonstração está em [ROTEIRO_VIDEO.md](ROTEIRO_VIDEO.md).

### Usar um banco separado (opcional)

O servidor cria o banco `chat-sakura.db` **na pasta de onde é iniciado**. Para fazer uma demonstração sem misturar com o histórico do projeto, inicie o servidor a partir de uma **pasta separada**, criada ao lado do projeto:

```powershell
# a partir da pasta que contém o projeto chat-sakura
mkdir chat-sakura-demo
cd chat-sakura-demo
mvn -q -f ..\chat-sakura\pom.xml compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

Nesse caso, o banco é criado em `chat-sakura-demo\chat-sakura.db`, e o banco do projeto não é alterado. Os clientes continuam sendo iniciados na raiz do projeto.

## Porta e banco de dados

- **Porta:** `12345`, fixa no código (`Servidor.PORTA_PADRAO`).
- **Banco:** `chat-sakura.db`, um arquivo SQLite criado automaticamente no diretório de execução do servidor. Na inicialização, o `MigradorBanco` cria o esquema ou migra um banco antigo para a versão atual (`PRAGMA user_version`), com backup automático e transação única. Versões, estratégia e restauração de backup estão em [sql/MIGRACOES.md](sql/MIGRACOES.md). Os arquivos `.db` (inclusive os backups) são ignorados pelo Git.

## Persistência e histórico

Cada mensagem é validada, **gravada no SQLite antes de ser distribuída** e só então enviada a todos os clientes conectados. A gravação usa `PreparedStatement` com parâmetros vinculados, e o identificador (`id`) é gerado pelo banco.

Ao conectar, o cliente recebe as **20 mensagens mais recentes**, em ordem cronológica, entre as linhas `===== HISTÓRICO RECENTE =====` e `===== FIM DO HISTÓRICO =====`. A leitura do histórico e o registro do cliente são atômicos em relação ao envio de mensagens. Por isso, cada mensagem chega pelo histórico **ou** ao vivo, sem duplicatas nem perdas.

Como o histórico fica no banco, ele é preservado quando o servidor é reiniciado.

O esquema da tabela também está documentado em [sql/criar_tabelas.sql](sql/criar_tabelas.sql).

## Contas e autenticação

Cada conta tem um ID persistente, um **login** (de 3 a 30 caracteres: letras sem acento, números, `.`, `-` e `_`; único, sem diferenciar maiúsculas), um **apelido** exibido no chat (único) e um avatar padrão. A senha deve ter de 8 a 128 caracteres.

- **Senhas:** usam PBKDF2-HMAC-SHA256 (`javax.crypto`), com 600.000 iterações, sal aleatório de 16 bytes por conta (`SecureRandom`) e hash de 32 bytes. A comparação é feita em tempo constante.
  - O banco guarda apenas `pbkdf2-sha256$<iterações>$<hash>` e o sal. Nenhuma senha, hash ou sal aparece em logs ou é enviado ao cliente.
  - Login inexistente e senha incorreta recebem a mesma resposta.
- **Identidade:** é definida **pelo servidor** no início da sessão e não muda depois. O cliente nunca envia um ID. Mensagens de contas gravam `remetente_id`; mensagens de visitantes e do histórico antigo ficam com `NULL`.
- **Personificação:** visitantes e clientes antigos não podem usar um nome que seja login ou apelido de uma conta. Continua valendo a regra de um único participante ativo por nome.
- **Compatibilidade:** clientes antigos e visitantes continuam no Chat Geral como antes.
- **Handshake:** depois das duas linhas de boas-vindas, o cliente atualizado envia `SAKURA 2` em vez do apelido.
  - Em seguida vem `ENTRAR` ou `CADASTRAR`, com os campos em Base64 URL.
  - O servidor responde `AUTH OK` ou `AUTH ERRO`. São permitidas 5 tentativas por conexão.
  - Toda linha recebida pelo servidor tem limite de 4096 caracteres.
  - A especificação completa está em `ProtocoloSakura`.

> **Segurança:** o PBKDF2 protege as senhas **armazenadas no banco**, mas **não** protege o tráfego. A conexão TCP não usa TLS, e o Base64 é só uma codificação: a senha trafega legível para quem puder observar a rede. O sistema é adequado para uso acadêmico em rede local confiável e **não deve ser exposto publicamente sem TLS**.

- **Proteção contra força bruta** (`ProtecaoAutenticacao`). Todos os bloqueios são temporários, e a resposta é a mesma para logins existentes e inexistentes (`MUITAS_TENTATIVAS`):
  - 10 falhas de um IP em 5 minutos bloqueiam esse IP por 5 minutos.
  - 20 falhas de um mesmo login (de qualquer IP) em 15 minutos bloqueiam esse login por 15 minutos. Um único IP é barrado antes de chegar a esse número, então não consegue bloquear a conta de outra pessoa.
  - Cada IP pode fazer 5 cadastros a cada 10 minutos.
  - Os cálculos PBKDF2 simultâneos são limitados ao número de processadores (mínimo 2).
- **Nomes e Unicode** (`NormalizacaoNome`). Apelidos e nomes de visitante são comparados por uma chave normalizada: NFKC, minúsculas com `Locale.ROOT` e NFKC de novo. Assim "Ana", "ANA" e "Ａｎａ" (largura total) são o mesmo nome, e "Élia" equivale a "ÉLIA". O apelido continua exibido como foi digitado.
  - **Aceito:** letras (com acento) de um único alfabeto, números, ponto, hífen e sublinhado, até 30 caracteres. Japonês (han, hiragana, katakana) e coreano (hangul, han) contam como um alfabeto.
  - **Recusado:** caracteres invisíveis ou de formatação (U+200B, U+200D, U+2060, U+FEFF, U+00AD, marcas de direção), controles, uso privado, qualquer espaço Unicode (U+00A0, U+3000...) e mistura de alfabetos no mesmo nome (como latim com cirílico em "Аna").
  - Visitantes não podem usar nomes equivalentes ao login ou ao apelido de uma conta, e duas contas não podem ter apelidos equivalentes (índice único no banco, migração v4).
  - **Limite:** a normalização não resolve todos os homóglifos. Um nome inteiro em cirílico parecido com um latino ("АНА") é aceito e não colide com "ANA".
- **Uma sessão por conta:** um novo login válido substitui a sessão anterior da mesma conta. A sessão antiga recebe `SESSAO_SUBSTITUIDA` e é fechada; não há aviso de saída nem de nova entrada. Visitantes continuam com a regra do nome único.

## Limites e robustez do servidor

O servidor protege-se de clientes lentos, conexões abandonadas e excesso de pedidos (`LimitesServidor`):

| Proteção | Padrão | Variável de ambiente |
|---|---|---|
| Conexões simultâneas | 200 | `CHAT_SAKURA_MAX_CONEXOES` |
| Conexões por IP | 10 | `CHAT_SAKURA_MAX_CONEXOES_IP` |
| Tempo para concluir nome ou login | 30 s | `CHAT_SAKURA_PRAZO_ENTRADA_S` |
| PING v2 após inatividade (queda sem resposta em mais um intervalo) | 30 s | `CHAT_SAKURA_PING_S` |
| Linhas pendentes na fila de saída de cada sessão | 1000 | `CHAT_SAKURA_FILA_SAIDA` |
| Mensagens por sessão (rajada de 20) | 120 por minuto | `CHAT_SAKURA_MENSAGENS_POR_MINUTO` |
| Criações (privada, grupo, personagem, avatar) | rajada de 5, 10 por minuto | — |
| Consultas (listas e históricos) | rajada de 20, 120 por minuto | — |

- **Cliente lento:** cada sessão tem uma fila de saída limitada e um único escritor, e os broadcasts apenas enfileiram, sem I/O sob bloqueio. Uma sessão que não lê enche a própria fila e é desconectada, sem atrasar as demais.
- **Heartbeat:** só sessões v2 recebem `PING` e respondem `PONG`, o que o cliente atual faz sozinho. Clientes legados não precisam de heartbeat e podem ficar parados; conexões mortas são detectadas pelo keepalive TCP. Um cliente v2 anterior a esta versão, sem `PONG`, cai depois de cerca de 60 s sem enviar nada.
- **Excesso de pedidos:** é recusado na hora, sem espera, com `ERRO` código `LIMITE` (ou um aviso `SISTEMA:` no legado), no máximo um aviso por segundo.

## Conversas privadas e grupos

Com uma conta, a janela mostra uma **barra lateral** com:
- o Chat Geral;
- as conversas privadas e os grupos de que a pessoa participa;
- para cada uma, um avatar padrão, a prévia da última mensagem e o contador de não lidas.

O botão **Conversa** inicia uma conversa privada com outra conta cadastrada. Se o par já conversou antes, a mesma conversa é reaberta. O botão **Grupo** cria um grupo com nome e participantes: quem cria é `ADMIN` e os convidados são `MEMBRO`.

- **Área principal:** mostra só a conversa selecionada, e o campo de envio vai para ela. O histórico de cada conversa é carregado sob demanda, sem travar a interface. Mensagens de conversas inativas atualizam a prévia e o contador em tempo real.
- **Persistência:** conversas, participantes, mensagens e o ponto de leitura de cada um ficam no SQLite. Quem estava offline encontra as mensagens e as não lidas ao entrar de novo, inclusive depois de reiniciar o servidor.
- **Segurança:**
  - O servidor obtém o remetente só da sessão autenticada.
  - Toda operação fora do Geral exige que a pessoa participe da conversa.
  - Conversa inexistente e conversa sem acesso recebem a mesma resposta (`NAO_ENCONTRADA`), para não revelar metadados.
  - Mensagens são entregues somente aos participantes conectados.
- **Ordem:** cada conversa tem um bloqueio próprio, então as mensagens chegam na mesma ordem para todos, e conversas diferentes não se bloqueiam.
- **Visitantes e clientes antigos:** continuam apenas no Chat Geral e nunca recebem mensagens privadas ou de grupos.
- **Exportação TXT:** grava a conversa ativa, com o nome dela no arquivo sugerido.

### Protocolo v2 (JSON)

Depois do `AUTH OK`, cada linha é um objeto JSON com o campo `tipo`. As linhas são processadas pela Gson em modo estrito e têm limite de 4096 caracteres. A especificação completa está em `ProtocoloJson`.

- **Requisições do cliente:** `LISTAR_CONVERSAS`, `LISTAR_USUARIOS`, `ABRIR_PRIVADA`, `CRIAR_GRUPO`, `ENVIAR`, `HISTORICO`, `MARCAR_LIDA`.
- **Eventos do servidor:** `CONVERSAS`, `CONVERSA`, `USUARIOS`, `HISTORICO`, `MENSAGEM` e `ERRO`. A presença no Geral chega como `MENSAGEM` do tipo `ENTRADA` ou `SAIDA`.
- **Correlação:** o campo opcional `req` é repetido na resposta.
- **Dados públicos apenas:** os eventos não contêm login, senha, hash nem sal.

Exemplo:

```json
{"tipo":"ENVIAR","req":7,"conversaId":3,"texto":"Oi, turma!"}
```

## Exportação TXT

Na janela do cliente, clique em **Exportar TXT**, escolha o destino e salve. O arquivo, em UTF-8, contém as mensagens exibidas na janela naquele momento.

## Avatares

Com uma conta conectada, clique no **avatar ao lado do botão de tema**, no cabeçalho, para abrir a galeria e escolher um dos 6 avatares:

- flor de cerejeira (padrão);
- lua crescente;
- gatinho;
- estrela;
- onda;
- xícara de chá.

Os avatares são desenhados em Java2D a partir de um identificador do catálogo `CatalogoAvatares`. Não há upload nem caminhos de arquivo, e o servidor recusa identificadores fora do catálogo.

A escolha é gravada em `usuario.avatar_id`, volta após reconexão ou reinício do servidor e é anunciada aos demais clientes. Ela aparece no cabeçalho e no item das conversas privadas.

## Sakura IA (Qwen)

Com uma conta, o botão **IA** da barra lateral cria um **personagem**, que tem:
- **nome**, sem espaços (ex.: `Naruto`);
- **personalidade**, com até 400 caracteres;
- **contexto inicial**, com até 1.200 caracteres.

Cada personagem tem uma conversa própria, visível **somente** para quem o criou. O histórico fica no SQLite e volta ao reabrir o aplicativo.

- **Aviso de IA:** a conversa começa com o aviso de que se trata de um personagem interpretado por IA, não de uma pessoa real.
- **Indicador:** enquanto a resposta é gerada, o título mostra "digitando…", e a interface continua respondendo.
- **Onde roda:** as chamadas ao modelo são feitas **somente pelo servidor**, em segundo plano. O cliente nunca acessa a API nem recebe a chave.

### Modelo e forma de execução

O servidor usa um modelo da família **Qwen** pela API compatível com OpenAI (`/chat/completions`). A mesma implementação (`ProvedorQwen`) funciona de duas formas:

| Forma | Quando usar | Configuração |
|---|---|---|
| **API do Qwen no Alibaba Cloud Model Studio** (padrão) | Com uma chave da API (contas novas costumam receber uma cota gratuita) | `QWEN_API_KEY`; modelo padrão `qwen-plus` |
| **Ollama local** | Sem chave e sem internet, se o computador comportar o modelo | `QWEN_BASE_URL=http://localhost:11434/v1` e `QWEN_MODEL=qwen2.5:3b` |

### Variáveis de ambiente (lidas pelo servidor)

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `QWEN_API_KEY` | para API remota | — | Chave da API. Nunca vai para o código, os logs ou os clientes. |
| `QWEN_BASE_URL` | não | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Base da API compatível com OpenAI. Confirme no console do Model Studio o endereço da sua região. |
| `QWEN_MODEL` | não | `qwen-plus` | Modelo da família Qwen. Outros modelos são recusados. |
| `QWEN_TIMEOUT_SEGUNDOS` | não | `45` | Tempo limite da resposta, entre 5 e 120 s. |
| `CHAT_SAKURA_IA` | não | — | `simulado` ativa o provedor **SIMULADO**, para demonstração sem IA real. As respostas vêm marcadas com `[SIMULADO]`. |

Sem nenhuma variável, o servidor inicia normalmente. Nesse caso, o personagem responde com uma mensagem clara de que o Qwen não está configurado; nenhuma resposta simulada é apresentada como sendo do Qwen.

**Opção A — API (PowerShell, com valor fictício):**

```powershell
$env:QWEN_API_KEY = "sk-sua-chave-aqui"
$env:QWEN_MODEL = "qwen-plus"        # opcional
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

**Opção B — Ollama local:**

```powershell
# Instale o Ollama (https://ollama.com) e, em outro terminal:
ollama pull qwen2.5:3b       # cerca de 2 GB; recomenda-se 8 GB de RAM ou mais
ollama serve                 # se o serviço ainda não estiver rodando
$env:QWEN_BASE_URL = "http://localhost:11434/v1"
$env:QWEN_MODEL = "qwen2.5:3b"
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

Ao iniciar, o servidor mostra o provedor ativo, por exemplo `Sakura IA: Qwen qwen-plus`, `Sakura IA: SIMULADO` ou `Sakura IA: Qwen (não configurado)`.

### Como testar a IA

1. Configure o Qwen (opção A ou B) e inicie o servidor.
2. Abra um cliente, clique em **Conectar** e entre com uma conta (ou crie uma).
3. Clique em **IA** e crie o personagem do exemplo:
   - **Nome:** `Naruto`
   - **Personalidade:** animado, determinado, amigável, motivador e descontraído
   - **Contexto:** estamos treinando juntos na Vila da Folha; Naruto deve interpretar esse personagem em uma conversa fictícia, mantendo uma postura encorajadora e usando referências adequadas ao cenário.
4. Envie uma mensagem: o título mostra "digitando…" e, em seguida, chega a resposta do Qwen.
5. Feche e reabra o cliente (ou reinicie o servidor): o personagem e o histórico continuam lá.

**Erros tratados pelo servidor:**
- chave inválida;
- limite de uso atingido (HTTP 429);
- modelo ou endereço inexistente;
- Ollama parado;
- tempo esgotado;
- resposta inválida.

Cada caso aparece como mensagem na conversa, sem novas tentativas automáticas.

**Limites da IA:**
- contexto de até 20 mensagens recentes (6.000 caracteres);
- resposta de até 400 tokens;
- 8 mensagens por minuto por conta;
- uma resposta por vez em cada conversa.

## Testes

Na raiz do projeto:

```powershell
mvn clean test
```

A suíte tem **377 testes JUnit 5**. Todos usam bancos SQLite temporários, e nenhum acessa a internet ou o banco principal. Eles cobrem:

- robustez do servidor: cliente lento, fila de saída, ordem, vagas global e por IP, prazo de entrada, heartbeat v2, substituição de sessão, limites de taxa e de tentativas de login;
- usuários, mensagens e repositórios;
- regras do chat e concorrência (`ExecutorService` e `awaitTermination`, sem `Thread.sleep`);
- migrações do banco (0→3), com backup e rollback;
- contas e PBKDF2;
- handshake, identidade e personificação, incluindo nomes Unicode equivalentes, invisíveis e alfabetos misturados;
- migração v4 (colisões, rollback e backup), WAL e gravações concorrentes;
- cliente: ordem de envio, nova tentativa de histórico, avisos não modais e reconexão com restauração das conversas;
- codec JSON;
- conversas privadas, grupos, isolamento, mensagens offline e reinício;
- avatares;
- personagens de IA;
- provedor Qwen contra um servidor HTTP local que imita a API, incluindo erros, tempo limite e chave fora de logs e eventos;
- interface: barra lateral, não lidas, temas e exportação.

## Observações e limitações

- **Não execute `mvn clean` com o servidor ou os clientes abertos.** O comando apaga a pasta `target`, que eles estão usando.
- Execute apenas um servidor por vez: a porta `12345` não pode estar em uso.
- **Sem TLS:** as senhas são protegidas no banco (PBKDF2), mas o tráfego TCP não é criptografado. Use o sistema apenas em rede local confiável.
- **Qwen real não verificado** no ambiente de desenvolvimento, por falta de chave e de Ollama. A integração foi testada contra um servidor que imita a API. Os nomes de modelo e as cotas gratuitas do Model Studio podem mudar.
- Um cliente que pare de ler o socket sem fechar a conexão pode atrasar as entregas aos demais.
- Grupos e personagens não podem ser editados nem excluídos pela interface.
- A exportação TXT inclui a conversa carregada na janela, não o histórico completo do banco.

Outras limitações e melhorias futuras estão descritas no relatório técnico.

## Documentação

| Documento | Conteúdo |
|---|---|
| [RELATORIO_TECNICO.md](RELATORIO_TECNICO.md) | Arquitetura, sincronização, segurança, protocolo, migrações, Qwen, testes e limitações |
| [ROTEIRO_VIDEO.md](ROTEIRO_VIDEO.md) | Roteiro da demonstração em vídeo |
| [sql/criar_tabelas.sql](sql/criar_tabelas.sql) | Esquema atual do banco (versão 3) |
| [sql/MIGRACOES.md](sql/MIGRACOES.md) | Versões do banco, backup e restauração |
| [AGENTS.md](AGENTS.md) | Requisitos e regras de implementação do projeto |

## Repositório

https://github.com/ane-godk/chat-sakura
