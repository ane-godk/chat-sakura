# Chat Sakura

Sistema de chat multiusuário com histórico, desenvolvido em Java 17. Um servidor TCP atende vários clientes ao mesmo tempo, com uma thread por conexão. As mensagens são gravadas em SQLite via JDBC e distribuídas a todos os conectados. O cliente tem interface gráfica Swing com tema Sakura.

## Funcionalidades

- Comunicação em tempo real entre vários usuários, com broadcast.
- Servidor TCP com uma thread (`ClienteHandler`) por cliente.
- Interface gráfica em Java Swing, com tema Sakura.
- Identificação por apelido (1 a 30 caracteres, sem espaços).
- Rejeição de apelidos duplicados, sem diferenciar maiúsculas de minúsculas.
- Validação das mensagens (não vazias, no máximo 500 caracteres).
- Persistência das mensagens em SQLite, com JDBC.
- Envio das 20 mensagens mais recentes ao conectar, em ordem cronológica e sem duplicatas.
- Exportação da conversa exibida para arquivo TXT.
- Desconexões, inclusive abruptas, tratadas sem afetar os demais clientes.
- Testes automatizados de regras de negócio, persistência e concorrência.

## Tecnologias

| Tecnologia | Versão / uso |
|---|---|
| Java | 17 (`maven.compiler.release`) |
| Maven | Build e testes |
| Sockets TCP | `ServerSocket` / `Socket` |
| Threads | Uma por conexão no servidor |
| JDBC + SQLite | `org.xerial:sqlite-jdbc` 3.46.1.3 |
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
│   └── criar_tabelas.sql
└── src/
    ├── main/java/br/unicesumar/chat/
    │   ├── model/             Usuario, Mensagem, MensagemInvalidaException
    │   ├── persistence/       MensagemRepository, MensagemRepositoryEmMemoria
    │   │   └── jdbc/          ConnectionFactory, JdbcMensagemRepository
    │   ├── service/           ChatService, ClientRegistry, ConexaoCliente
    │   ├── server/            Servidor, ClienteHandler
    │   └── client/            ClienteChat
    └── test/java/br/unicesumar/chat/   Testes JUnit 5
```

| Pacote | Responsabilidade |
|---|---|
| `model` | Entidades imutáveis e validações (domínio, sem dependência de rede ou JDBC) |
| `persistence` | Contrato do repositório e implementação em memória |
| `persistence.jdbc` | Implementação com SQLite |
| `service` | Regras do chat, broadcast, registro de clientes e sincronização |
| `server` | Servidor TCP e protocolo com cada cliente |
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
java -cp target\classes br.unicesumar.chat.client.ClienteChat
```

O cliente não depende de bibliotecas externas, por isso pode ser iniciado direto com `java`. Esse comando exige que o projeto já tenha sido compilado (passo 1 ou 2).

Uma alternativa pelo Maven:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.client.ClienteChat"
```

Na janela, clique em **Conectar** e informe:

| Campo | Valor |
|---|---|
| Nome de usuário | por exemplo, `Ana` |
| Endereço do servidor | `localhost` |
| Porta | `12345` |

### 4. Demonstrar três clientes simultâneos

1. Inicie o servidor (passo 2).
2. Abra **três terminais** na raiz do projeto e execute o comando do cliente em cada um.
3. Conecte cada janela com um apelido diferente, por exemplo `Ana`, `Bia` e `Caio`.
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
- **Banco:** `chat-sakura.db`, um arquivo SQLite criado automaticamente no diretório de execução do servidor. A tabela `mensagem` é criada pelo próprio servidor na inicialização, com `CREATE TABLE IF NOT EXISTS`. O arquivo `.db` é ignorado pelo Git.

## Persistência e histórico

Cada mensagem é validada, **gravada no SQLite antes de ser distribuída** e só então enviada a todos os clientes conectados. A gravação usa `PreparedStatement` com parâmetros vinculados, e o identificador (`id`) é gerado pelo banco.

Ao conectar, o cliente recebe as **20 mensagens mais recentes**, em ordem cronológica, entre as linhas `===== HISTÓRICO RECENTE =====` e `===== FIM DO HISTÓRICO =====`. A leitura do histórico e o registro do cliente são atômicos em relação ao envio de mensagens. Por isso, cada mensagem chega pelo histórico **ou** ao vivo, sem duplicatas nem perdas.

Como o histórico fica no banco, ele é preservado quando o servidor é reiniciado.

O esquema da tabela também está documentado em [sql/criar_tabelas.sql](sql/criar_tabelas.sql).

## Exportação TXT

Na janela do cliente, clique em **Exportar TXT**, escolha o destino e salve. O arquivo, em UTF-8, contém as mensagens exibidas na janela naquele momento.

## Testes

Na raiz do projeto:

```powershell
mvn clean test
```

A suíte tem **52 testes JUnit 5**, que cobrem:

- validação de usuários e mensagens;
- repositório em memória e repositório SQLite (este último usando um arquivo temporário);
- regras do chat: broadcast, isolamento de falhas, nomes duplicados, persistência antes do envio e limite do histórico;
- protocolo do `ClienteHandler`, com socket simulado em memória;
- concorrência: envios simultâneos, ordem do broadcast, registro e remoção simultâneos, e entrada de clientes durante broadcasts. Esses testes usam `ExecutorService` e `awaitTermination`, sem `Thread.sleep`.

## Observações e limitações

- **Não execute `mvn clean` com o servidor ou os clientes abertos.** O comando apaga a pasta `target`, que eles estão usando.
- Execute apenas um servidor por vez: a porta `12345` não pode estar em uso.
- Um cliente que pare de ler o socket sem fechar a conexão pode atrasar o broadcast para os demais. Clientes desconectados, mesmo de forma abrupta, não causam esse problema.
- Não há autenticação nem criptografia: o protocolo é texto puro sobre TCP, adequado ao uso em rede local e ao escopo acadêmico.
- A exportação TXT inclui apenas o conteúdo exibido na janela, não o histórico completo do banco.

Outras limitações e melhorias futuras estão descritas no relatório técnico.

## Documentação

| Documento | Conteúdo |
|---|---|
| [RELATORIO_TECNICO.md](RELATORIO_TECNICO.md) | Arquitetura, diagramas, sincronização, persistência, testes e limitações |
| [ROTEIRO_VIDEO.md](ROTEIRO_VIDEO.md) | Roteiro da demonstração em vídeo |
| [sql/criar_tabelas.sql](sql/criar_tabelas.sql) | Script de criação da tabela |
| [AGENTS.md](AGENTS.md) | Requisitos e regras de implementação do projeto |

## Repositório

https://github.com/ane-godk/chat-sakura
