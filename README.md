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
- **Testes:** 423 testes automatizados.

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

### 2. Iniciar o servidor (TLS)

O servidor usa **TLS por padrão**. Na primeira vez, crie o certificado de desenvolvimento conforme [Conexão segura (TLS)](#conexão-segura-tls) (uma única vez). Depois, na **raiz do projeto**:

```powershell
$env:CHAT_SAKURA_TLS_KEYSTORE = "$env:USERPROFILE\chat-sakura-certificados\servidor.p12"
$env:CHAT_SAKURA_TLS_KEYSTORE_SENHA = Read-Host "Senha do keystore"   # não fica no histórico do terminal
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

O `compile` no mesmo comando garante que as classes existam, mesmo depois de um `mvn clean`.

Se tudo der certo, o terminal mostra:

```text
Servidor iniciado na porta 12345
Transporte: TLS (TLSv1.3, TLSv1.2) em todas as interfaces
Aguardando conexões...
```

Sem o certificado configurado, o servidor **não inicia** e explica o que falta; ele nunca passa sozinho para TCP sem criptografia. Para testes locais rápidos sem certificado, use o modo de desenvolvimento descrito em [Conexão segura (TLS)](#conexão-segura-tls).

Para encerrar, use `Ctrl + C` (se o Windows perguntar se deseja finalizar o arquivo em lotes, responda `S`).

### 3. Iniciar os clientes

Em **outro terminal**, na **raiz do projeto**, aponte o cliente para o certificado público do servidor (o truststore) e inicie-o:

```powershell
$env:CHAT_SAKURA_TRUSTSTORE = "$env:USERPROFILE\chat-sakura-certificados\confianca.p12"
$env:CHAT_SAKURA_TRUSTSTORE_SENHA = Read-Host "Senha do truststore"
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

Em todos os modos, o endereço do servidor é `localhost` e a porta é `12345`. A conexão usa TLS; a caixa **Sem TLS (somente desenvolvimento local)** só deve ser marcada com um servidor no modo de desenvolvimento. Veja [Contas e autenticação](#contas-e-autenticação).

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

## Conexão segura (TLS)

O cliente e o servidor conversam por **TLS 1.3** (ou 1.2, se a outra ponta não tiver o 1.3). Versões antigas (SSLv3, TLS 1.0 e 1.1) são recusadas. Tudo o que vai pela rede é criptografado: o login, a senha, as mensagens, os comandos e os históricos.

**Antes de enviar qualquer dado, o cliente:**
- confere se o certificado foi emitido por quem ele confia (o truststore);
- confere se o certificado vale para o nome ou IP digitado (campo SAN);
- confere se o certificado está dentro da validade.

Se algo falhar, a conexão é encerrada **sem enviar nada**, e uma mensagem explica o motivo. O cliente nunca tenta de novo sem TLS por conta própria, inclusive na reconexão automática, que refaz toda a validação.

### Criar o certificado de desenvolvimento (uma vez, PowerShell)

Os arquivos ficam **fora do projeto**. O `.gitignore` também recusa `*.p12`, `*.jks`, `*.pfx` e a pasta `certificados/`. O `keytool` vem com o JDK.

```powershell
mkdir "$env:USERPROFILE\chat-sakura-certificados"
cd "$env:USERPROFILE\chat-sakura-certificados"
$senhaKs = Read-Host "Senha do keystore do servidor"
$senhaTs = Read-Host "Senha do truststore do cliente"

# 1. Par de chaves do servidor (chave privada + certificado), válido para localhost e 127.0.0.1
keytool -genkeypair -alias chat-sakura -keyalg EC -groupname secp256r1 -dname "CN=localhost" `
  -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 365 `
  -keystore servidor.p12 -storetype PKCS12 -storepass $senhaKs

# 2. Exporta só o certificado público (pode ser copiado para os clientes)
keytool -exportcert -rfc -alias chat-sakura -keystore servidor.p12 -storepass $senhaKs -file chat-sakura.crt

# 3. Truststore dos clientes, com o certificado público
keytool -importcert -noprompt -alias chat-sakura -file chat-sakura.crt `
  -keystore confianca.p12 -storetype PKCS12 -storepass $senhaTs
```

`servidor.p12` contém a **chave privada**: fica só no computador do servidor e nunca vai para o Git nem para os clientes. `confianca.p12` (ou o `chat-sakura.crt`) é público.

### Variáveis de ambiente

| Variável | Onde | Descrição |
|---|---|---|
| `CHAT_SAKURA_TLS_KEYSTORE` | servidor | Caminho do keystore (PKCS12 ou JKS) com o certificado e a chave privada. Obrigatória no modo TLS. |
| `CHAT_SAKURA_TLS_KEYSTORE_SENHA` | servidor | Senha do keystore. Lida do ambiente, nunca do código; não aparece nos logs. |
| `CHAT_SAKURA_MODO` | servidor | `tls` (padrão) ou `tcp-desenvolvimento`. |
| `CHAT_SAKURA_ENDERECO` | servidor | Interface de escuta. Padrão: todas no TLS; somente `127.0.0.1` no modo de desenvolvimento. |
| `CHAT_SAKURA_TRUSTSTORE` | cliente | Truststore com o certificado do servidor. Sem ela, vale o truststore do Java, que não conhece certificados autoassinados. |
| `CHAT_SAKURA_TRUSTSTORE_SENHA` | cliente | Senha do truststore. |

Para não deixar senhas no histórico do terminal, use `Read-Host`, como nos exemplos. A negociação TLS tem prazo de 10 s nos dois lados. Uma conexão que não negocia é fechada e libera a vaga, preservando os limites de conexões, as tentativas de login e a proteção contra clientes lentos.

### Computadores diferentes na rede local

O certificado precisa conter o endereço que os clientes vão digitar. Inclua o IP (ou o nome) do servidor no SAN ao criar o par de chaves, por exemplo para o IP `192.168.0.10`:

```powershell
keytool -genkeypair -alias chat-sakura -keyalg EC -groupname secp256r1 -dname "CN=chat-sakura" `
  -ext "SAN=dns:localhost,ip:127.0.0.1,ip:192.168.0.10" -validity 365 `
  -keystore servidor.p12 -storetype PKCS12 -storepass $senhaKs
```

Depois repita os passos 2 e 3, copie o `confianca.p12` (ou o `chat-sakura.crt`) para cada cliente e, no diálogo do cliente, informe `192.168.0.10` como endereço. Libere a porta 12345 (TCP) no firewall do Windows do servidor.

### Renovar ou substituir o certificado

Os comandos acima **não sobrescrevem** arquivos existentes: o `keytool` recusa um alias repetido. Para renovar:
1. Mova (ou renomeie) o `servidor.p12` e o `confianca.p12` antigos.
2. Gere de novo com os passos 1 a 3.
3. Reinicie o servidor.
4. Distribua o novo `confianca.p12` aos clientes.

Um cliente com o certificado antigo recebe "certificado não confiável" até atualizar o truststore.

### Problemas de certificado

| Mensagem no cliente | Causa e solução |
|---|---|
| "O certificado do servidor não é confiável" | O truststore não tem o certificado deste servidor (ou `CHAT_SAKURA_TRUSTSTORE` não está definida). Importe o `chat-sakura.crt`. |
| "O certificado do servidor não corresponde ao endereço ..." | O nome ou IP digitado não está no SAN. Use o endereço que consta no certificado ou gere um certificado com esse endereço. |
| "O certificado do servidor expirou" | Renove o certificado (seção acima). |
| "O servidor não respondeu com TLS" | O servidor está no modo de desenvolvimento sem TLS. Inicie-o com o certificado. |
| "O servidor encerrou a conexão" | Cliente marcado como "Sem TLS" falando com um servidor TLS. Desmarque a opção. |

### Modo de desenvolvimento (sem TLS)

Somente para testes no próprio computador:

```powershell
$env:CHAT_SAKURA_MODO = "tcp-desenvolvimento"
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

- O servidor escuta **só em 127.0.0.1**, salvo se `CHAT_SAKURA_ENDERECO` indicar outra interface; nesse caso o log mostra um aviso.
- No cliente, marque **Sem TLS (somente desenvolvimento local)**.
- Nesse modo, senhas e mensagens trafegam legíveis.
- Clientes legados em texto puro só funcionam nesse modo.
- Na reconexão de uma sessão que usava TLS, a opção "Sem TLS" fica bloqueada.

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

> **Segurança:** o PBKDF2 protege as senhas **armazenadas no banco**, e o **TLS** protege o tráfego: o cliente valida o certificado e o nome do servidor antes de enviar o login, as mensagens ou qualquer comando. O Base64 do handshake é só uma codificação; por isso, sem TLS (modo de desenvolvimento), a senha trafega legível na rede. Veja [Conexão segura (TLS)](#conexão-segura-tls).

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

### Memória dos personagens

Cada conversa com um personagem tem uma **memória própria**: notas curtas sobre o que você contou, para que o personagem se lembre de conversas antigas sem que o histórico inteiro seja enviado ao modelo.

- **Quando é atualizada:** a cada 10 mensagens novas, logo depois de uma resposta. O personagem resume a conversa em até 6 notas curtas, com no máximo 1.200 caracteres no total.
- **A quem pertence:** à conversa, que pertence a uma única conta. Dois usuários podem ter personagens com o mesmo nome sem compartilhar nada. Ninguém além do dono consegue ver ou apagar essa memória, e ela não existe fora do seu personagem.
- **O que não entra:**
  - dados sensíveis, como e-mails, telefones, documentos, números longos e senhas;
  - frases que soam como instruções ("a partir de agora...", "ignore...").
  Esse filtro não depende do modelo obedecer.
- **Como é usada:** no prompt, a memória vai num bloco marcado como notas que podem estar incompletas e que não são instruções.
- **Ver e apagar:** numa conversa de IA, o botão **Memória**, ao lado do título, mostra as notas. **Apagar memória** pede confirmação e:
  - esvazia as notas;
  - faz o personagem deixar de considerar as mensagens anteriores;
  - **não apaga** o histórico, que continua visível.
- **Durante a atualização** (alguns segundos), um novo envio para aquele personagem recebe o aviso "O personagem está organizando a memória da conversa".

### Contexto, tokens e desempenho

- **Prompt em blocos:** identidade, personalidade, contexto, estilo de fala, memória, regras e histórico recente.
  - Personalidade e contexto vão entre aspas triplas, como dados do personagem, não como ordens.
  - O histórico contém só mensagens reais da conversa, sem falas inventadas.
  - Se você perguntar diretamente, o personagem pode dizer que é uma IA.
- **Orçamento de contexto:** janela menos resposta menos uma margem. As instruções nunca passam de 60% disso: se passarem, saem primeiro as notas mais antigas da memória e depois parte do contexto. Identidade, personalidade e regras ficam sempre inteiras. O resto vai para as mensagens mais recentes, que nunca são cortadas ao meio.
- **Resposta cortada pelo limite de tamanho:** termina na última frase completa e recebe a marca `[…]`, e a interface avisa. O corte nunca é escondido.
- **Uma geração por vez com Ollama local, com fila limitada:**
  - Com a fila cheia, o envio é recusado **antes** de gravar a mensagem, com o aviso "A Sakura IA está ocupada...".
  - Cada conversa tem no máximo uma geração em andamento.
  - Se você reconectar no meio de uma geração, o "digitando…" volta e a resposta chega normalmente.

| Variável | Ollama local | API remota | Descrição |
|---|---|---|---|
| `QWEN_API` | `ollama` (automático na porta 11434) | `openai` | `ollama` usa a API nativa `/api/chat`, que aceita `num_ctx`, `keep_alive` e `repeat_penalty`. `openai` usa `/chat/completions`. |
| `QWEN_CONTEXTO_TOKENS` | 4096 | 16384 | Janela considerada no orçamento (mínimo 2048). No Ollama, vai como `num_ctx`, porque o padrão muda entre versões. |
| `QWEN_RESPOSTA_TOKENS` | 320 | 400 | Limite da resposta (`num_predict` / `max_tokens`). |
| `QWEN_HISTORICO_MENSAGENS` | 16 | 20 | Máximo de mensagens recentes (também limitado a 6.000 caracteres). |
| `QWEN_GERACOES_SIMULTANEAS` | 1 | 2 | Gerações ao mesmo tempo. |
| `QWEN_FILA_MAXIMA` | 4 | 8 | Gerações aguardando vaga; além disso, recusa. |
| `QWEN_TEMPERATURA` / `QWEN_TOP_P` | 0,65 / 0,9 | 0,65 / 0,9 | Amostragem. Resumos de memória usam temperatura 0,2. Penalidade de repetição: `repeat_penalty` 1,1, ou `presence_penalty` 0,3 na API OpenAI. |
| `QWEN_KEEP_ALIVE` | `30m` | — | Tempo que o Ollama mantém o modelo carregado depois do último pedido. Vai em cada pedido e não altera a configuração global do Ollama. |
| `QWEN_AQUECER` | desligado | — | `true` carrega o modelo em segundo plano ao iniciar o servidor, sem atrasar a inicialização e sem baixar nada. |

**Memória da máquina com Ollama.** O `qwen2.5:3b` (Q4_K_M) ocupa cerca de 1,9 GB de pesos, mais aproximadamente 150 MB de cache de contexto com 4096 tokens. Com uma GPU de 2 GB, parte das camadas roda na CPU, então conte com cerca de 2,5 a 3 GB de RAM enquanto o modelo estiver carregado. Com `keep_alive` de 30 minutos, essa memória fica ocupada até 30 minutos depois da última mensagem; em troca, as respostas seguintes não pagam o carregamento, que leva alguns segundos. Use `QWEN_KEEP_ALIVE=5m` (o padrão do Ollama) para liberar a memória antes.

**Outros limites:** 8 mensagens por minuto por conta para a IA; uma resposta por vez em cada conversa.

## Testes

Na raiz do projeto:

```powershell
mvn clean test
```

A suíte tem **423 testes JUnit 5**. Todos usam bancos SQLite temporários, e nenhum acessa a internet ou o banco principal. Eles cobrem:

- robustez do servidor: cliente lento, fila de saída, ordem, vagas global e por IP, prazo de entrada, heartbeat v2, substituição de sessão, limites de taxa e de tentativas de login;
- usuários, mensagens e repositórios;
- regras do chat e concorrência (`ExecutorService` e `awaitTermination`, sem `Thread.sleep`);
- migrações do banco (0→3), com backup e rollback;
- contas e PBKDF2;
- handshake, identidade e personificação, incluindo nomes Unicode equivalentes, invisíveis e alfabetos misturados;
- migração v4 (colisões, rollback e backup), WAL e gravações concorrentes;
- cliente: ordem de envio, nova tentativa de histórico, avisos não modais e reconexão com restauração das conversas;
- IA: prompt em blocos e orçamento de contexto, memória (criação, isolamento, persistência, exclusão e filtro), fila limitada, tempo esgotado, resposta truncada, Ollama parado, API nativa e remota, e reconexão durante a geração (todos com provedores simulados);
- codec JSON;
- conversas privadas, grupos, isolamento, mensagens offline e reinício;
- avatares;
- personagens de IA;
- provedor Qwen contra um servidor HTTP local que imita a API, incluindo erros, tempo limite e chave fora de logs e eventos;
- interface: barra lateral, não lidas, temas e exportação.

## Observações e limitações

- **Não execute `mvn clean` com o servidor ou os clientes abertos.** O comando apaga a pasta `target`, que eles estão usando.
- Execute apenas um servidor por vez: a porta `12345` não pode estar em uso.
- **TLS com certificado autoassinado:** cada cliente precisa receber o certificado público do servidor (truststore). Clientes legados em texto puro, como versões antigas ou `telnet`, não falam TLS: só funcionam com um servidor no modo de desenvolvimento.
- **Qwen:** verificado com o `qwen2.5:3b` local pelo Ollama. A API do Model Studio foi testada contra um servidor que imita a API; os nomes de modelo e as cotas gratuitas podem mudar.
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
