# Chat Sakura

O **Chat Sakura** é um aplicativo de mensagens para Windows feito em Java. Com ele você:

- cria uma **conta com senha** e conversa no **Chat Geral**;
- abre **conversas privadas** e **grupos** com outras contas;
- escolhe **avatar**, **tema** (quatro paletas florais, claro ou escuro) e fundo da conversa;
- pesquisa mensagens antigas e **fixa** as importantes;
- conversa com **personagens de IA** (opcional), interpretados pelo modelo Qwen.

Neste guia, o Chat Sakura funciona **somente no seu computador**: o servidor (que guarda as contas e as mensagens) e as janelas de chat rodam na mesma máquina. Para conversar entre duas pessoas, abra duas janelas de chat, uma para cada conta.

Os **personagens de IA são opcionais**. Todo o resto funciona sem eles.

---

## Como executar no Windows (passo a passo)

> Resumo: baixe o ZIP, extraia, dê dois cliques em **`INICIAR-CHAT-SAKURA.bat`** e crie sua conta.
> O computador precisa do **Java 17 ou superior**. Se não tiver e não puder instalar, use o [pacote portátil](#computador-sem-java-pacote-portátil).

### Antes de começar: o Java está instalado?

1. Aperte as teclas **Windows + R**, digite `cmd` e aperte **Enter**. Abre uma janela preta (o Prompt de Comando).
2. Digite o comando abaixo e aperte **Enter**:
   ```
   java -version
   ```
3. Veja a primeira linha da resposta:
   - `openjdk version "17..."`, `"21..."` ou outro número **17 ou maior**: está tudo certo. Feche a janela e siga para o passo 1.
   - `java version "1.8..."` (Java 8) ou `'java' não é reconhecido...`: o Java adequado não está instalado. Instale o **JDK 17** em <https://adoptium.net/temurin/releases/?version=17> (escolha Windows, x64, **JDK**, arquivo `.msi`; durante a instalação, deixe marcadas as opções **Set JAVA_HOME** e **Add to PATH**). Se você não tiver permissão para instalar programas, use o [pacote portátil](#computador-sem-java-pacote-portátil).

Você **não precisa** instalar o Maven, criar certificados nem configurar nada: o inicializador cuida disso.

### Passo 1: baixar o projeto

1. Abra <https://github.com/ane-godk/chat-sakura> no navegador.
2. Clique no botão verde **`<> Code`** (acima da lista de arquivos).
3. Clique em **Download ZIP**. O arquivo `chat-sakura-main.zip` vai para a pasta **Downloads**.

### Passo 2: extrair o ZIP

1. Abra a pasta **Downloads** e clique com o botão direito em `chat-sakura-main.zip`.
2. Escolha **Extrair tudo...** e clique em **Extrair**.
3. Abra a pasta extraída **`chat-sakura-main`**. Dentro dela estão a pasta `src`, o arquivo `pom.xml` e o arquivo **`INICIAR-CHAT-SAKURA.bat`**.

> **Importante:** não execute o arquivo de dentro do ZIP sem extrair (o Windows abre o ZIP como se fosse uma pasta, mas não deixa gravar nela).
> Se aparecer uma pasta `chat-sakura-main` dentro de outra `chat-sakura-main`, use a de dentro: a que contém o `INICIAR-CHAT-SAKURA.bat`.

### Passo 3: executar o inicializador

1. Dê **dois cliques** em **`INICIAR-CHAT-SAKURA.bat`**. Se o Windows esconder as extensões, ele aparece como **`INICIAR-CHAT-SAKURA`**, com o tipo "Arquivo em Lotes do Windows".
2. Se aparecer **"O Windows protegeu o computador"**, clique em **Mais informações** e depois em **Executar assim mesmo**. O aviso aparece porque o arquivo veio da internet.

Abre uma janela preta chamada **"Chat Sakura - inicializador"**, que mostra cada etapa:

```text
 [1/4] Java 17.0.x encontrado.
 [2/4] Preparando o programa. Na primeira vez, as bibliotecas sao baixadas
       da internet e isso pode levar alguns minutos. Aguarde...
       Pronto.
 [3/4] Abrindo o servidor na janela "Chat Sakura - Servidor"...
Servidor pronto.
 [4/4] Abrindo a janela do Chat Sakura...
```

- **Na primeira vez**, a etapa 2 baixa da internet o Maven e as bibliotecas do projeto, e por isso pode levar alguns minutos (depende da internet). Nas vezes seguintes, leva poucos segundos.
- **Não feche** a janela do inicializador enquanto usa o chat: ela mostra as mensagens do programa.

### Passo 4: confirmar que o servidor iniciou

Abre uma segunda janela preta, **"Chat Sakura - Servidor"**. O servidor está pronto quando ela mostra:

```text
Servidor iniciado na porta 12345
Transporte: TCP SEM TLS (somente desenvolvimento) em 127.0.0.1
Sakura IA: ...
Aguardando conexões...
```

Deixe essa janela aberta: ela é o servidor. (A linha "Sakura IA" diz se os personagens de IA estão disponíveis; veja [Personagens de IA](#personagens-de-ia-com-ollama-opcional).)

### Passo 5: confirmar que o chat abriu

Abre a janela **"Chat Sakura — modo local (neste computador)"**, já com a tela **Conectar ao Chat Sakura** na frente. Nela, o endereço `127.0.0.1` e a porta `12345` já vêm preenchidos e não precisam ser alterados, com o aviso:

> Conexão local: servidor neste computador (127.0.0.1:12345), modo de desenvolvimento sem TLS. Nada sai deste computador.

### Passo 6: criar sua conta

1. Na tela de entrada, clique em **Criar conta**.
2. Preencha:
   - **Login:** de 3 a 30 caracteres, só letras sem acento, números, ponto, hífen ou sublinhado. Exemplo: `ana.souza`.
   - **Apelido:** o nome que os outros veem no chat. Exemplo: `Ana`.
   - **Senha** e **Confirmar senha:** de 8 a 128 caracteres.
3. Clique em **Conectar**.

Pronto: **você já entra conectado**. A faixa no topo da conversa mostra "Conta criada! Você já está conectado como Ana" e o selo no canto superior direito mostra **"Conectado como Ana"** com uma bolinha verde.

Se algo estiver errado (login inválido, senhas diferentes, apelido em uso), uma mensagem explica o problema e a tela de entrada continua aberta para correção.

### Passo 7: entrar de novo numa conta existente

Nas próximas vezes, na tela de entrada, deixe selecionado **Entrar**, digite o **login** e a **senha** e clique em **Conectar**. Se a tela de entrada não estiver aberta, clique no botão **Conectar** do cabeçalho.

As contas e as mensagens ficam guardadas: continuam lá depois de fechar e abrir o Chat Sakura.

### Passo 8: começar a conversar

- **Chat Geral:** já vem aberto. Digite na caixa de baixo e aperte **Enter** (ou clique em **Enviar**).
- **Uma segunda pessoa no mesmo computador:** com o servidor aberto, dê dois cliques em `INICIAR-CHAT-SAKURA.bat` **de novo**. O inicializador percebe que o servidor já está rodando e abre só mais uma janela de chat. Nela, crie a segunda conta.
- **Conversa privada:** clique em **Privada** (barra da esquerda) e escolha a outra conta.
- **Grupo:** clique em **Grupo**, dê um nome e marque os participantes.
- **Personagem de IA:** clique em **IA** (precisa do [Ollama](#personagens-de-ia-com-ollama-opcional)).

### Passo 9: encerrar

1. Feche a janela do Chat Sakura (botão **X**).
2. A janela do inicializador pergunta: **"Deseja encerrar o servidor tambem? Digite S (sim) ou N (nao)"**.
   - **S**: encerra o servidor (a janela "Chat Sakura - Servidor" fecha sozinha).
   - **N**: deixa o servidor aberto, por exemplo se outra janela de chat ainda estiver em uso. Para encerrá-lo depois, feche a janela "Chat Sakura - Servidor".
3. Aperte qualquer tecla para fechar a janela do inicializador.

### Onde ficam os dados

Tudo fica na pasta **`dados`**, criada ao lado do `INICIAR-CHAT-SAKURA.bat`:

| Arquivo | Conteúdo |
|---|---|
| `dados\chat-sakura.db` | Contas, conversas e mensagens (banco SQLite) |
| `dados\logs\servidor.log` | Mensagens do servidor |
| `dados\logs\cliente.log` | Mensagens das janelas de chat |
| `dados\logs\compilacao.log` | Detalhes da etapa "Preparando o programa" |

Para começar do zero, feche tudo e apague a pasta `dados`. As preferências visuais de quem não entrou numa conta ficam em `%USERPROFILE%\.chat-sakura\`.

---

## Computador sem Java (pacote portátil)

O **pacote portátil** já traz um Java próprio (reduzido, só com o necessário). No computador onde ele vai rodar, **não é preciso instalar nada** (nem Java, nem Maven) e **não é preciso ser administrador**. É a opção recomendada para computadores de laboratório.

### Gerar o pacote (no seu computador, uma vez)

Precisa de um computador com o **JDK 17 ou superior** (o mesmo do [passo a passo](#antes-de-começar-o-java-está-instalado)) e internet na primeira vez.

1. Na pasta do projeto, dê dois cliques em **`GERAR-PACOTE-PORTATIL.bat`**.
2. Aguarde: ele compila, roda todos os testes e monta o pacote. Leva alguns minutos.
3. Ao final, a janela mostra onde ficou o pacote:
   - pasta `dist\ChatSakura-Portatil\`;
   - arquivo **`dist\ChatSakura-Portatil.zip`** (cerca de 45 MB).

O pacote contém só `runtime\` (o Java reduzido), `chat-sakura.jar`, `INICIAR-CHAT-SAKURA.bat` e `LEIA-ME.txt`. Nenhum banco, senha, certificado ou configuração pessoal entra nele.

### Usar o pacote em outro computador

1. Copie `ChatSakura-Portatil.zip` para um pendrive (ou envie para você mesmo por e-mail ou nuvem).
2. No outro computador, extraia o ZIP (**botão direito → Extrair tudo...**) para uma pasta onde se possa gravar, como a **Área de Trabalho** ou **Documentos**. Não use o pendrive se ele for somente leitura.
3. Abra a pasta **`ChatSakura-Portatil`** e dê dois cliques em **`INICIAR-CHAT-SAKURA.bat`**.
4. Siga a partir do [passo 4](#passo-4-confirmar-que-o-servidor-iniciou): é tudo igual, mas sem a etapa "Preparando o programa" e sem precisar de internet.

O pacote foi gerado e testado no Windows 11 (64 bits). Ele funciona em Windows 10 ou 11 de 64 bits; não funciona em Linux, macOS nem em Windows de 32 bits.

---

## Requisitos

| Item | Pelo código-fonte (ZIP do GitHub) | Pelo pacote portátil |
|---|---|---|
| Sistema | Windows 10 ou 11 | Windows 10 ou 11 (64 bits) |
| Java | **JDK 17 ou superior** (o JRE, sem compilador, não basta) | Não precisa (vem no pacote) |
| Maven | Não precisa (o inicializador usa o Maven Wrapper, que baixa o Maven sozinho) | Não precisa |
| Internet | Na **primeira** execução, para baixar o Maven e as bibliotecas | Não precisa |
| Administrador | Não precisa | Não precisa |
| Espaço em disco | Recomendado: 500 MB livres (bibliotecas do Maven, guardadas em `%USERPROFILE%\.m2`) | Cerca de 60 MB para a pasta extraída |
| IA (opcional) | Ollama com `qwen2.5:3b` (cerca de 2 GB; 8 GB de RAM ou mais recomendados) | Igual |

Para conferir a versão do Java, use `java -version` (veja [Antes de começar](#antes-de-começar-o-java-está-instalado)). Se o Maven estiver instalado, o inicializador o usa; senão, usa o `mvnw.cmd` (Maven Wrapper) que vem no projeto.

---

## Personagens de IA com Ollama (opcional)

Os personagens de IA usam o modelo **Qwen 2.5 3B** pelo **Ollama**, um programa gratuito que roda modelos de IA no próprio computador, sem enviar suas conversas para a internet.

**Sem o Ollama, o Chat Sakura funciona normalmente** (contas, Chat Geral, privadas e grupos). Só o botão **IA** responde com uma mensagem explicando que a IA não está disponível.

### Ativar a IA

1. Instale o Ollama: baixe em <https://ollama.com/download> e execute o instalador.
2. Abra o Prompt de Comando (**Windows + R**, `cmd`, **Enter**) e baixe o modelo (cerca de 2 GB, só na primeira vez):
   ```
   ollama pull qwen2.5:3b
   ```
3. Confira se o modelo está disponível:
   ```
   ollama list
   ```
   A lista deve mostrar `qwen2.5:3b`.
4. Execute (ou execute de novo) o `INICIAR-CHAT-SAKURA.bat`. Se o servidor já estava aberto, encerre-o antes: ele procura o Ollama só ao iniciar.

Não é preciso configurar nada: ao iniciar, o servidor procura o Ollama neste computador (`http://127.0.0.1:11434`) e, se encontrar o `qwen2.5:3b`, passa a usá-lo. Ele apenas consulta a lista de modelos: não baixa modelos e não muda nenhuma configuração do Ollama.

### Confirmar que o Qwen foi reconhecido

Na janela **"Chat Sakura - Servidor"**, procure a linha:

| Mensagem | Significado |
|---|---|
| `Sakura IA: Qwen qwen2.5:3b (contexto 4096 tokens, ...)` | IA ativa. |
| `Sakura IA: Qwen (não configurado). Ollama não encontrado em http://127.0.0.1:11434...` | O Ollama não está instalado ou não está aberto. Abra o Ollama e reinicie o servidor. |
| `Sakura IA: Qwen (não configurado). O Ollama está em execução, mas o modelo qwen2.5:3b não está instalado...` | Rode `ollama pull qwen2.5:3b` e reinicie o servidor. |

### Testar um personagem

1. Entre numa conta e clique em **IA** (barra da esquerda).
2. Preencha **Nome** (sem espaços, ex.: `Sakurinha`), **Personalidade** e **Contexto** e confirme.
3. Envie uma mensagem. O título mostra "digitando…" e, em alguns segundos, chega a resposta. A primeira resposta demora mais, porque o Ollama carrega o modelo na memória.

Quem já usa variáveis `QWEN_*` (por exemplo, a API remota do Qwen) continua com elas: a detecção automática só acontece quando nenhuma dessas variáveis está definida. Veja [Sakura IA (Qwen)](#sakura-ia-qwen).

---

## Solução de problemas

Quando algo dá errado, as janelas pretas **não fecham sozinhas**: leia a mensagem e aperte uma tecla para fechar.

| O que aparece | O que fazer |
|---|---|
| `ERRO: o Java nao foi encontrado neste computador.` | Instale o JDK 17 (veja [Antes de começar](#antes-de-começar-o-java-está-instalado)) ou use o [pacote portátil](#computador-sem-java-pacote-portátil). Depois de instalar, feche e abra de novo o inicializador. |
| `ERRO: o Chat Sakura precisa do Java 17 ou superior.` com `Versao encontrada: 1.8...` | O computador tem um Java antigo. Instale o JDK 17 ou use o pacote portátil. Se o Java novo já estiver instalado, a variável `JAVA_HOME` pode apontar para o antigo (a mensagem mostra qual Java foi usado). |
| `ERRO: nao foi possivel preparar o programa.` | Na primeira vez é preciso internet; redes de laboratório às vezes bloqueiam downloads. Confira também se o Java instalado é o **JDK** (o JRE não compila). O detalhe fica em `dados\logs\compilacao.log`. Alternativa: pacote portátil. |
| "Maven não encontrado" / `'mvn' não é reconhecido` | Não é preciso instalar o Maven: o inicializador usa o `mvnw.cmd` do projeto. Se a mensagem aparecer, confira se o `mvnw.cmd` e a pasta `.mvn` estão na mesma pasta do `.bat` (extraia o ZIP inteiro). |
| `ERRO: a porta 12345 esta sendo usada por outro programa.` | Outro programa (ou um servidor do Chat Sakura aberto pelo terminal no modo TLS) usa a porta. A mensagem mostra o PID: feche esse programa (Gerenciador de Tarefas → aba **Detalhes** → procure o PID) e execute de novo. O inicializador nunca encerra programas desconhecidos. |
| `ERRO: o servidor nao ficou pronto.` | Leia a janela "Chat Sakura - Servidor" (ou `dados\logs\servidor.log`). Feche-a e execute o inicializador de novo. |
| `Não foi possível abrir o banco de dados ...` (janela do servidor) | A pasta não permite gravação, ou o arquivo `dados\chat-sakura.db` está aberto em outro programa. Extraia o projeto para a Área de Trabalho ou Documentos e feche outros programas que usem o banco. |
| `ERRO: nao foi possivel criar a pasta "dados"` | O arquivo foi aberto de dentro do ZIP ou de uma pasta somente leitura. Extraia o ZIP (botão direito → **Extrair tudo...**). |
| Selo **"Desconectado"** e janela "Não foi possível conectar ao servidor 127.0.0.1:12345..." | O servidor não está aberto. Execute o `INICIAR-CHAT-SAKURA.bat` (ele abre o servidor) e clique em **Conectar**. |
| "Login ou senha incorretos." | Confira o login (não o apelido) e a senha. Uma conta criada em outro computador não existe neste: cada pasta `dados` tem as próprias contas. Depois de muitas tentativas erradas, o login fica bloqueado por alguns minutos. |
| A tela de entrada fecha, mas a conexão não acontece | Veja a linha `SISTEMA:` no Chat Geral e a janela do inicializador: elas dizem o motivo. "Falha inesperada no cliente" indica um erro do próprio programa: envie o `dados\logs\cliente.log`. |
| "Conexão segura recusada" ou "O servidor não respondeu com TLS" | Essas mensagens são do modo TLS, usado só quando o cliente é aberto pelo terminal. Pelo `INICIAR-CHAT-SAKURA.bat` a conexão é sempre local, sem TLS. Veja [Conexão segura (TLS)](#conexão-segura-tls). |
| Personagem responde "A Sakura IA (Qwen) não está configurada" | O Ollama ou o modelo não foram encontrados quando o servidor iniciou. Veja [Personagens de IA](#personagens-de-ia-com-ollama-opcional) e reinicie o servidor. |
| `'ollama' não é reconhecido` | O Ollama não está instalado (ou o Prompt de Comando foi aberto antes da instalação; abra outro). |
| A janela preta abre e fecha na hora | Isso não deve acontecer: o inicializador sempre espera uma tecla. Se acontecer, abra o Prompt de Comando, arraste o `INICIAR-CHAT-SAKURA.bat` para dentro dele e aperte **Enter**: a mensagem fica visível. |
| Caminhos com espaços ou acentos (ex.: `Área de Trabalho\Minha pasta`) | Funcionam (testado). Evite apenas pastas sincronizadas que ainda não terminaram de baixar os arquivos. |

### Como obter os logs

- **Servidor:** janela "Chat Sakura - Servidor" e o arquivo `dados\logs\servidor.log`.
- **Janelas de chat:** janela do inicializador e o arquivo `dados\logs\cliente.log`.
- **Preparação (Maven):** `dados\logs\compilacao.log`.

Os logs **não mostram senhas**. Para relatar um problema, envie esses arquivos e a saída de `java -version`.

---

## Executar manualmente pelo terminal (avançado)

Esta seção é para quem quer controlar cada parte (por exemplo, usar TLS ou outra pasta de dados). Para usar o chat, o `INICIAR-CHAT-SAKURA.bat` basta.

Os comandos abaixo são para o **PowerShell**, abertos na pasta do projeto (a que contém o `pom.xml`). Onde aparece `mvn`, quem não tem o Maven instalado pode usar `.\mvnw.cmd` no lugar.

### 1. Compilar e testar

```powershell
mvn clean test
```

### 2. Iniciar o servidor (TLS, o padrão)

Fora do inicializador, o servidor usa **TLS por padrão** e exige um certificado: crie-o uma vez, conforme [Conexão segura (TLS)](#conexão-segura-tls). Depois:

```powershell
$env:CHAT_SAKURA_TLS_KEYSTORE = "$env:USERPROFILE\chat-sakura-certificados\servidor.p12"
$env:CHAT_SAKURA_TLS_KEYSTORE_SENHA = Read-Host "Senha do keystore"   # não fica no histórico do terminal
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

O `compile` no mesmo comando garante que as classes existam, mesmo depois de um `mvn clean`. Se tudo der certo, o terminal mostra:

```text
Servidor iniciado na porta 12345
Transporte: TLS (TLSv1.3, TLSv1.2) em todas as interfaces
Aguardando conexões...
```

Sem o certificado configurado, o servidor **não inicia** e explica o que falta; ele nunca passa sozinho para TCP sem criptografia. Para encerrar, use `Ctrl + C`.

### 3. Iniciar os clientes

Em **outro terminal**, aponte o cliente para o certificado público do servidor (o truststore):

```powershell
$env:CHAT_SAKURA_TRUSTSTORE = "$env:USERPROFILE\chat-sakura-certificados\confianca.p12"
$env:CHAT_SAKURA_TRUSTSTORE_SENHA = Read-Host "Senha do truststore"
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.client.ClienteChat"
```

Na janela, clique em **Conectar** e escolha **Entrar**, **Criar conta** ou **Visitante** (apenas um nome, sem conta, só no Chat Geral). O endereço padrão é `localhost` e a porta, `12345`. A conexão usa TLS; a caixa **Sem TLS (somente desenvolvimento local)** só deve ser marcada com um servidor no modo de desenvolvimento.

### Modo local pelo terminal (sem TLS, como o inicializador)

O inicializador apenas compila o `target\chat-sakura.jar` e executa a classe `br.unicesumar.chat.local.InicioLocal`:

```powershell
mvn -q -DskipTests package
java -cp target\chat-sakura.jar br.unicesumar.chat.local.InicioLocal servidor   # terminal 1
java -cp target\chat-sakura.jar br.unicesumar.chat.local.InicioLocal cliente    # terminal 2
```

- `servidor`: TCP sem TLS **somente em 127.0.0.1:12345**, com o banco em `dados\chat-sakura.db`. Esse modo é definido no código; ele não lê `CHAT_SAKURA_MODO` nem `CHAT_SAKURA_ENDERECO`, então nunca escuta em outra interface.
- `cliente`: janela no modo local (endereço fixo, sem a caixa "Sem TLS").
- Outros comandos usados pelo `.bat`: `verificar` (porta livre, ocupada ou com o Chat Sakura), `aguardar <segundos>` e `parar` (encerra só o servidor aberto pelo inicializador, conferindo o PID e o horário de início).

O `ClienteChat` comum (sem o modo local) continua com TLS por padrão, sem rebaixamento automático.

### Reconexão automática

Se a conexão cair sem você clicar em **Sair**, o selo mostra "Reconectando..." e uma faixa acima da conversa informa as tentativas:
- O cliente tenta alcançar o servidor com intervalos crescentes: 1 s, 2 s, 4 s... até 30 s.
- Quando o servidor volta, a janela de login reabre com o servidor, a porta e o login preenchidos. A senha não fica guardada em lugar nenhum, então é pedida de novo.
- Depois do login, a lista de conversas é recarregada e a conversa que estava aberta volta, com o histórico.
- **Cancelar** (na faixa) ou **Sair** interrompem a espera.
- Mensagens que estavam saindo no momento da queda não são reenviadas automaticamente, para não duplicar; a faixa avisa quantas podem não ter chegado.
- Não há reconexão automática quando outro login da mesma conta assumiu a sessão, nem para visitantes.

**Avisos.** Erros recuperáveis (limite de envio, conversa não encontrada, histórico indisponível) aparecem numa faixa discreta acima da conversa, que some sozinha e nunca se empilha. Diálogos ficam só para o que exige ação, como login recusado ou servidor inacessível.

### Usar um banco separado (opcional)

O servidor comum (`Servidor`) cria o banco `chat-sakura.db` **na pasta de onde é iniciado**. Para não misturar com outro histórico, inicie-o a partir de outra pasta:

```powershell
# a partir da pasta que contém o projeto chat-sakura
mkdir chat-sakura-demo
cd chat-sakura-demo
mvn -q -f ..\chat-sakura\pom.xml compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

O inicializador local usa sempre a pasta `dados` ao lado do `.bat`. Para usar um banco existente com ele, feche tudo e copie o arquivo `.db` para `dados\chat-sakura.db`.

---

## Informações técnicas

### Funcionalidades

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
- **Aparência:** quatro paletas florais (Sakura, Myosotis, Fuji e Suzuran), cada uma em modo claro e escuro, e três fundos de conversa (floral, liso e neutro). Cada conta guarda a própria escolha no servidor.
- **Pesquisa e mensagens fixadas:** pesquisa no histórico salvo da conversa, com filtros de remetente e período, e até 10 mensagens fixadas por conversa.
- **Execução local de um clique:** `INICIAR-CHAT-SAKURA.bat` e pacote portátil com Java embutido.
- **Testes:** 535 testes automatizados.

### Tecnologias

| Tecnologia | Versão / uso |
|---|---|
| Java | 17 (`maven.compiler.release`) |
| Maven | Build e testes (Maven Wrapper 3.9.9 incluído) |
| Sockets TCP | `ServerSocket` / `Socket` |
| Threads | Uma por conexão no servidor |
| JDBC + SQLite | `org.xerial:sqlite-jdbc` 3.46.1.3 |
| JSON (protocolo v2) | `com.google.code.gson:gson` 2.11.0 |
| Java Swing | Interface do cliente |
| JUnit 5 | `junit-jupiter` 5.11.4 |
| Empacotamento | `maven-shade-plugin` (JAR único) e `jlink` (Java reduzido do pacote portátil) |

### Estrutura do projeto

```text
chat-sakura/
├── INICIAR-CHAT-SAKURA.bat     Inicializador local (dois cliques)
├── GERAR-PACOTE-PORTATIL.bat   Gera o pacote portátil com Java embutido
├── mvnw, mvnw.cmd, .mvn/       Maven Wrapper (dispensa instalar o Maven)
├── pacote-portatil/LEIA-ME.txt Instruções que vão dentro do pacote
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
    │   ├── ia/                ProvedorIA, ProvedorQwen, ConfiguracaoQwen, DeteccaoOllama, ProvedorIAFalso
    │   ├── server/            Servidor, ClienteHandler, LeitorLinhas
    │   ├── client/            ClienteChat, ConexaoServidor, ControladorConversas, BarraConversas, AvatarSakura, diálogos
    │   └── local/             InicioLocal (modo local do inicializador), SaidaDuplicada (logs)
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
| `ia` | Provedores de IA: Qwen (API compatível com OpenAI ou nativa do Ollama), detecção do Ollama local e SIMULADO |
| `client` | Interface gráfica Swing |
| `local` | Modo local do inicializador: verifica a porta, abre o servidor no loopback e o cliente sem TLS |

### Conexão segura (TLS)

O cliente e o servidor comuns conversam por **TLS 1.3** (ou 1.2, se a outra ponta não tiver o 1.3). Versões antigas (SSLv3, TLS 1.0 e 1.1) são recusadas. Tudo o que vai pela rede é criptografado: o login, a senha, as mensagens, os comandos e os históricos.

O inicializador local **não usa TLS** porque o servidor e o cliente ficam no mesmo computador (`127.0.0.1`) e nada passa pela rede. Isso vale só para ele: o `Servidor` e o `ClienteChat` comuns continuam exigindo TLS por padrão.

**Antes de enviar qualquer dado, o cliente:**
- confere se o certificado foi emitido por quem ele confia (o truststore);
- confere se o certificado vale para o nome ou IP digitado (campo SAN);
- confere se o certificado está dentro da validade.

Se algo falhar, a conexão é encerrada **sem enviar nada**, e uma mensagem explica o motivo. O cliente nunca tenta de novo sem TLS por conta própria, inclusive na reconexão automática, que refaz toda a validação.

#### Criar o certificado de desenvolvimento (uma vez, PowerShell)

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

#### Variáveis de ambiente

| Variável | Onde | Descrição |
|---|---|---|
| `CHAT_SAKURA_TLS_KEYSTORE` | servidor | Caminho do keystore (PKCS12 ou JKS) com o certificado e a chave privada. Obrigatória no modo TLS. |
| `CHAT_SAKURA_TLS_KEYSTORE_SENHA` | servidor | Senha do keystore. Lida do ambiente, nunca do código; não aparece nos logs. |
| `CHAT_SAKURA_MODO` | servidor | `tls` (padrão) ou `tcp-desenvolvimento`. Ignorada pelo inicializador local. |
| `CHAT_SAKURA_ENDERECO` | servidor | Interface de escuta. Padrão: todas no TLS; somente `127.0.0.1` no modo de desenvolvimento. Ignorada pelo inicializador local. |
| `CHAT_SAKURA_TRUSTSTORE` | cliente | Truststore com o certificado do servidor. Sem ela, vale o truststore do Java, que não conhece certificados autoassinados. |
| `CHAT_SAKURA_TRUSTSTORE_SENHA` | cliente | Senha do truststore. |

Para não deixar senhas no histórico do terminal, use `Read-Host`, como nos exemplos. A negociação TLS tem prazo de 10 s nos dois lados. Uma conexão que não negocia é fechada e libera a vaga, preservando os limites de conexões, as tentativas de login e a proteção contra clientes lentos.

#### Computadores diferentes na rede local

O inicializador e o pacote portátil funcionam só no próprio computador. Para clientes em outros computadores, use o servidor comum com TLS. O certificado precisa conter o endereço que os clientes vão digitar. Inclua o IP (ou o nome) do servidor no SAN ao criar o par de chaves, por exemplo para o IP `192.168.0.10`:

```powershell
keytool -genkeypair -alias chat-sakura -keyalg EC -groupname secp256r1 -dname "CN=chat-sakura" `
  -ext "SAN=dns:localhost,ip:127.0.0.1,ip:192.168.0.10" -validity 365 `
  -keystore servidor.p12 -storetype PKCS12 -storepass $senhaKs
```

Depois repita os passos 2 e 3, copie o `confianca.p12` (ou o `chat-sakura.crt`) para cada cliente e, no diálogo do cliente, informe `192.168.0.10` como endereço. Libere a porta 12345 (TCP) no firewall do Windows do servidor.

#### Renovar ou substituir o certificado

Os comandos acima **não sobrescrevem** arquivos existentes: o `keytool` recusa um alias repetido. Para renovar:
1. Mova (ou renomeie) o `servidor.p12` e o `confianca.p12` antigos.
2. Gere de novo com os passos 1 a 3.
3. Reinicie o servidor.
4. Distribua o novo `confianca.p12` aos clientes.

Um cliente com o certificado antigo recebe "certificado não confiável" até atualizar o truststore.

#### Problemas de certificado

| Mensagem no cliente | Causa e solução |
|---|---|
| "O certificado do servidor não é confiável" | O truststore não tem o certificado deste servidor (ou `CHAT_SAKURA_TRUSTSTORE` não está definida). Importe o `chat-sakura.crt`. |
| "O certificado do servidor não corresponde ao endereço ..." | O nome ou IP digitado não está no SAN. Use o endereço que consta no certificado ou gere um certificado com esse endereço. |
| "O certificado do servidor expirou" | Renove o certificado (seção acima). |
| "O servidor não respondeu com TLS" | O servidor está no modo de desenvolvimento sem TLS. Inicie-o com o certificado. |
| "O servidor encerrou a conexão" | Cliente marcado como "Sem TLS" falando com um servidor TLS. Desmarque a opção. |

#### Modo de desenvolvimento (sem TLS) pelo servidor comum

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

O inicializador local faz o equivalente sem variáveis de ambiente e sem a caixa "Sem TLS".

### Porta e banco de dados

- **Porta:** `12345`, fixa no código (`Servidor.PORTA_PADRAO`).
- **Banco:** `chat-sakura.db`, um arquivo SQLite criado automaticamente (pelo inicializador, em `dados\`; pelo servidor comum, no diretório de execução). Na inicialização, o `MigradorBanco` cria o esquema ou migra um banco antigo para a versão atual (`PRAGMA user_version`), com backup automático e transação única. Versões, estratégia e restauração de backup estão em [sql/MIGRACOES.md](sql/MIGRACOES.md). Os arquivos `.db` (inclusive os backups) e a pasta `dados/` são ignorados pelo Git.

### Persistência e histórico

Cada mensagem é validada, **gravada no SQLite antes de ser distribuída** e só então enviada a todos os clientes conectados. A gravação usa `PreparedStatement` com parâmetros vinculados, e o identificador (`id`) é gerado pelo banco.

Ao conectar, o cliente recebe as **20 mensagens mais recentes**, em ordem cronológica, entre as linhas `===== HISTÓRICO RECENTE =====` e `===== FIM DO HISTÓRICO =====`. A leitura do histórico e o registro do cliente são atômicos em relação ao envio de mensagens. Por isso, cada mensagem chega pelo histórico **ou** ao vivo, sem duplicatas nem perdas.

Como o histórico fica no banco, ele é preservado quando o servidor é reiniciado.

O esquema da tabela também está documentado em [sql/criar_tabelas.sql](sql/criar_tabelas.sql).

### Contas e autenticação

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

> **Segurança:** o PBKDF2 protege as senhas **armazenadas no banco**, e o **TLS** protege o tráfego: o cliente valida o certificado e o nome do servidor antes de enviar o login, as mensagens ou qualquer comando. O Base64 do handshake é só uma codificação; por isso, sem TLS (modo de desenvolvimento e inicializador local), a senha trafega legível, mas apenas dentro do próprio computador (`127.0.0.1`). Veja [Conexão segura (TLS)](#conexão-segura-tls).

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

### Limites e robustez do servidor

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

O inicializador local usa os valores padrão (as variáveis acima valem para o servidor comum).

- **Cliente lento:** cada sessão tem uma fila de saída limitada e um único escritor, e os broadcasts apenas enfileiram, sem I/O sob bloqueio. Uma sessão que não lê enche a própria fila e é desconectada, sem atrasar as demais.
- **Heartbeat:** só sessões v2 recebem `PING` e respondem `PONG`, o que o cliente atual faz sozinho. Clientes legados não precisam de heartbeat e podem ficar parados; conexões mortas são detectadas pelo keepalive TCP. Um cliente v2 anterior a esta versão, sem `PONG`, cai depois de cerca de 60 s sem enviar nada.
- **Excesso de pedidos:** é recusado na hora, sem espera, com `ERRO` código `LIMITE` (ou um aviso `SISTEMA:` no legado), no máximo um aviso por segundo.

### Conversas privadas e grupos

Com uma conta, a janela mostra uma **barra lateral** com:
- o Chat Geral;
- as conversas privadas e os grupos de que a pessoa participa;
- para cada uma, um avatar padrão, a prévia da última mensagem e o contador de não lidas.

O botão **Privada** inicia uma conversa privada com outra conta cadastrada. Se o par já conversou antes, a mesma conversa é reaberta. O botão **Grupo** cria um grupo com nome e participantes: quem cria é `ADMIN` e os convidados são `MEMBRO`.

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

#### Protocolo v2 (JSON)

Depois do `AUTH OK`, cada linha é um objeto JSON com o campo `tipo`. As linhas são processadas pela Gson em modo estrito e têm limite de 4096 caracteres. A especificação completa está em `ProtocoloJson`.

- **Requisições do cliente:** `LISTAR_CONVERSAS`, `LISTAR_USUARIOS`, `ABRIR_PRIVADA`, `CRIAR_GRUPO`, `ENVIAR`, `HISTORICO`, `MARCAR_LIDA`, `DEFINIR_APARENCIA` (`paleta`, `modo`, `fundo`), `PESQUISAR` (`conversaId`, `texto`, `remetenteId?`, `de?`, `ate?`, `antesDe?`, `limite?`), `LISTAR_FIXADAS`, `FIXAR` e `DESAFIXAR` (`conversaId`, `mensagemId`).
- **Eventos do servidor:** `CONVERSAS`, `CONVERSA`, `USUARIOS`, `HISTORICO`, `MENSAGEM`, `APARENCIA`, `RESULTADOS`, `FIXADAS` e `ERRO`. A presença no Geral chega como `MENSAGEM` do tipo `ENTRADA` ou `SAIDA`. `APARENCIA` é enviado logo após a entrada (também na reconexão) e em resposta a `DEFINIR_APARENCIA`. `FIXADAS` responde a `LISTAR_FIXADAS`, `FIXAR` e `DESAFIXAR` e também chega, sem pedido, aos demais participantes quando a lista muda. Clientes antigos ignoram eventos que não conhecem.
- **Correlação:** o campo opcional `req` é repetido na resposta.
- **Dados públicos apenas:** os eventos não contêm login, senha, hash nem sal.

Exemplo:

```json
{"tipo":"ENVIAR","req":7,"conversaId":3,"texto":"Oi, turma!"}
```

### Pesquisa e mensagens fixadas

Com uma conta conectada, o cabeçalho da conversa tem dois ícones: a **lupa** (pesquisar) e o **alfinete** (mensagens fixadas, com a quantidade ao lado). Eles abrem um painel à direita da conversa, sem janela modal; o mesmo ícone fecha o painel.

#### Pesquisa

- **Onde:** no histórico salvo no servidor da conversa aberta, não só nas mensagens carregadas na tela.
- **Termo:** palavra ou trecho, de 2 a 100 caracteres. Acentos e maiúsculas não importam ("reuniao" encontra "REUNIÃO"), e `%` e `_` são texto comum, nunca curingas.
- **Filtros opcionais:**
  - remetente: um dos participantes da conversa;
  - período: datas no formato dd/mm/aaaa, inclusive nas duas pontas.
- **Resultados:** remetente, data e trecho da mensagem com o termo em destaque, das mais recentes para as mais antigas, 20 por vez; **Mais resultados** traz a próxima página, sem repetir nem pular mensagens.
- **Abrir:** clicar num resultado abre a mensagem original, rola até ela e a destaca por alguns segundos. Se ela for mais antiga que o histórico carregado, o cliente busca as páginas anteriores (até 500 mensagens para trás), sem lacunas.
- **Avisos e entradas e saídas do Geral** não aparecem nos resultados. Quando nada é encontrado, o painel informa "Nenhuma mensagem encontrada."

#### Mensagens fixadas

- **Fixar:** pelo menu de contexto da mensagem (**Fixar mensagem** ou **Desafixar mensagem**). Uma mensagem fixada mostra um pequeno alfinete ao lado do horário.
- **Lista:** o painel do alfinete mostra as fixadas da conversa; clicar localiza e destaca a mensagem; **Desafixar** remove a fixação da selecionada (a mensagem continua na conversa).
- **Quem pode fixar e desafixar:**
  - **conversa privada:** qualquer uma das duas pessoas (elas têm os mesmos direitos na conversa);
  - **grupo:** só quem tem o papel ADMIN, que já existia (o criador do grupo); os membros veem a lista, mas não alteram;
  - **conversa com personagem:** o dono da conversa;
  - **Chat Geral:** ninguém, por ser aberto a todos.
- **Limite:** 10 fixadas por conversa, verificado no servidor, inclusive com pedidos simultâneos.
- **Exclusões:** apagar uma mensagem ou uma conversa apaga as fixações dela; se a conta de quem fixou for apagada, a fixação continua, sem o autor.

#### Segurança

- O servidor verifica a conta e a participação em cada pedido; o cliente não decide nada sozinho.
- Uma conversa de que você não participa responde exatamente como uma conversa inexistente, sem revelar mensagens nem que ela existe.
- A pesquisa usa SQL com parâmetros (o texto digitado nunca entra no comando) e limita cada página a 50 resultados.
- O texto das mensagens nunca é alterado: a migração v7 grava, numa coluna à parte (`texto_busca`), a forma usada na pesquisa.

### Exportação TXT

Na janela do cliente, clique em **Exportar TXT**, escolha o destino e salve. O arquivo, em UTF-8, contém as mensagens exibidas na janela naquele momento.

### Avatares

Com uma conta conectada, clique no **avatar ao lado do botão de tema**, no cabeçalho, para abrir a galeria e escolher um dos 6 avatares:

- flor de cerejeira (padrão);
- lua crescente;
- gatinho;
- estrela;
- onda;
- xícara de chá.

Os avatares são desenhados em Java2D a partir de um identificador do catálogo `CatalogoAvatares`. Não há upload nem caminhos de arquivo, e o servidor recusa identificadores fora do catálogo.

A escolha é gravada em `usuario.avatar_id`, volta após reconexão ou reinício do servidor e é anunciada aos demais clientes. Ela aparece no cabeçalho e no item das conversas privadas.

### Aparência

O botão **Aparência**, no cabeçalho, abre uma tela com três escolhas e uma prévia ao vivo:

| Paleta | Flor | Cores de referência (modo claro) |
|---|---|---|
| **Sakura** (padrão) | cerejeira japonesa | rosa `#B65F88`, `#F8D7E5`, `#FFF3F7` |
| **Myosotis** | não-me-esqueças | azul `#5589B5`, `#D9ECFA`, `#F2F9FF` |
| **Fuji** | glicínia japonesa | lilás `#9071BA`, `#E9DDF8`, `#F8F3FF` |
| **Suzuran** | lírio-do-vale | verde `#659876`, `#DCEEDC`, `#F3FAF2` |

- **Modo:** claro ou escuro. O escuro de cada flor parte do desenho do Sakura Noturno (fundos profundos sem preto puro e textos claros sem branco puro); não é o claro escurecido. O botão de lua/sol continua alternando só o modo.
- **Fundo da conversa:**
  - **floral:** degradê da paleta e ilustrações originais da flor nos cantos, desenhadas em Java2D (a Sakura usa a flor do projeto); o centro, onde ficam as mensagens, e a faixa do título ficam sempre livres;
  - **liso:** um tom pastel da paleta;
  - **neutro:** tom quase sem cor, sem decoração.
- **Botões:** **Aplicar** troca a aparência na hora, sem reiniciar; **Restaurar padrão** volta as opções a Sakura, claro e floral (ainda é preciso aplicar); **Cancelar** descarta.

Banner, ícones, botões, balões, barra lateral, campos, menus, avisos, indicador de conexão e os diálogos (entrada, memória, novas conversas) usam a paleta ativa. Todos os textos pequenos têm contraste de pelo menos 4,5:1 nas oito combinações (verificado em teste). As pétalas caindo só aparecem no fundo floral.

#### Onde a aparência fica guardada

- **Com conta:** no servidor, na tabela `preferencia_aparencia` do SQLite (migração v6), uma linha por conta, apagada junto com a conta. O servidor a envia logo após o login e após cada reconexão, por isso ela vale em qualquer computador. Uma conta nunca recebe nem altera a aparência de outra.
- **Sem conta (tela de entrada e visitantes):** no arquivo local `~/.chat-sakura/preferencias.properties`, nas chaves `tema` (modo), `paleta` e `fundo`. Ao sair de uma conta, a janela volta a essa aparência local. A escolha feita com a conta conectada não é gravada nesse arquivo.
- **Valores antigos ou inválidos:** um arquivo antigo, só com `tema`, abre em Sakura com fundo floral. Valores desconhecidos, no arquivo ou no banco, viram o padrão só daquele campo. O servidor recusa pedidos com valores inválidos (`DADOS_INVALIDOS`).
- **Durante uma reconexão:** a aparência da conta continua; uma escolha feita sem conexão é enviada quando a sessão volta.

### Sakura IA (Qwen)

Com uma conta, o botão **IA** da barra lateral cria um **personagem**, que tem:
- **nome**, sem espaços (ex.: `Naruto`);
- **personalidade**, com até 400 caracteres;
- **contexto inicial**, com até 1.200 caracteres.

Cada personagem tem uma conversa própria, visível **somente** para quem o criou. O histórico fica no SQLite e volta ao reabrir o aplicativo.

- **Aviso de IA:** a conversa começa com o aviso de que se trata de um personagem interpretado por IA, não de uma pessoa real.
- **Indicador:** enquanto a resposta é gerada, o título mostra "digitando…", e a interface continua respondendo.
- **Onde roda:** as chamadas ao modelo são feitas **somente pelo servidor**, em segundo plano. O cliente nunca acessa a API nem recebe a chave.

#### Modelo e forma de execução

O servidor usa um modelo da família **Qwen**. A mesma implementação (`ProvedorQwen`) funciona de duas formas:

| Forma | Quando usar | Configuração |
|---|---|---|
| **Ollama local** (recomendado) | Sem chave e sem internet, se o computador comportar o modelo | Nenhuma: detectado automaticamente com o `qwen2.5:3b` instalado. Ou, explicitamente, `QWEN_BASE_URL=http://localhost:11434/v1` e `QWEN_MODEL=qwen2.5:3b` |
| **API do Qwen no Alibaba Cloud Model Studio** | Com uma chave da API (contas novas costumam receber uma cota gratuita) | `QWEN_API_KEY`; modelo padrão `qwen-plus` |

**Ordem de escolha do provedor, ao iniciar o servidor:**
1. `CHAT_SAKURA_IA=simulado`: provedor **SIMULADO**, para demonstração sem IA real (respostas marcadas com `[SIMULADO]`).
2. Qualquer uma de `QWEN_API_KEY`, `QWEN_BASE_URL` ou `QWEN_MODEL` definida: vale essa configuração, e o Ollama não é consultado.
3. Nenhuma delas: o servidor consulta `http://127.0.0.1:11434/api/tags` (prazo de 2 s). Com o `qwen2.5:3b` instalado, usa o Ollama local com os ajustes locais (contexto 4096, uma geração por vez). As variáveis opcionais de ajuste (`QWEN_KEEP_ALIVE`, `QWEN_TEMPERATURA` etc.) continuam valendo.
4. Sem Ollama ou sem o modelo: o servidor inicia normalmente, sem IA, e o log explica o que falta. O personagem responde com uma mensagem clara de que o Qwen não está configurado; nenhuma resposta simulada é apresentada como sendo do Qwen.

#### Variáveis de ambiente (lidas pelo servidor)

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `QWEN_API_KEY` | para API remota | — | Chave da API. Nunca vai para o código, os logs ou os clientes. |
| `QWEN_BASE_URL` | não | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Base da API compatível com OpenAI. Confirme no console do Model Studio o endereço da sua região. |
| `QWEN_MODEL` | não | `qwen-plus` | Modelo da família Qwen. Outros modelos são recusados. |
| `QWEN_TIMEOUT_SEGUNDOS` | não | `45` | Tempo limite da resposta, entre 5 e 120 s. |
| `CHAT_SAKURA_IA` | não | — | `simulado` ativa o provedor **SIMULADO**. |

**API remota (PowerShell, com valor fictício):**

```powershell
$env:QWEN_API_KEY = "sk-sua-chave-aqui"
$env:QWEN_MODEL = "qwen-plus"        # opcional
mvn -q compile exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

Ao iniciar, o servidor mostra o provedor ativo, por exemplo `Sakura IA: Qwen qwen2.5:3b (contexto 4096 tokens, ...)`, `Sakura IA: Qwen qwen-plus (...)`, `Sakura IA: SIMULADO (...)` ou `Sakura IA: Qwen (não configurado). <o que falta>`.

#### Como testar a IA

1. Ative o Qwen (Ollama ou API) e inicie o servidor.
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

#### Memória dos personagens

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

#### Contexto, tokens e desempenho

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

### Testes

Na raiz do projeto:

```powershell
mvn clean test
```

A suíte tem **535 testes JUnit 5**. Todos usam bancos SQLite temporários, e nenhum acessa a internet ou o banco principal. Eles cobrem:

- robustez do servidor: cliente lento, fila de saída, ordem, vagas global e por IP, prazo de entrada, heartbeat v2, substituição de sessão, limites de taxa e de tentativas de login;
- usuários, mensagens e repositórios;
- regras do chat e concorrência (`ExecutorService` e `awaitTermination`, sem `Thread.sleep`);
- migrações do banco (0→3), com backup e rollback;
- contas e PBKDF2;
- handshake, identidade e personificação, incluindo nomes Unicode equivalentes, invisíveis e alfabetos misturados;
- migração v4 (colisões, rollback e backup), WAL e gravações concorrentes;
- cliente: ordem de envio, nova tentativa de histórico, avisos não modais e reconexão com restauração das conversas;
- sessão de ponta a ponta com banco novo (cadastro, saída, login, eventos iniciais, conversa privada e envio) em TCP de desenvolvimento, em TLS e na janela real; falhas locais e servidor inacessível com mensagens compreensíveis;
- modo local do inicializador: servidor sem certificados somente no loopback, destino fixo 127.0.0.1:12345 sem TLS, TLS preservado fora dele (sem rebaixamento, inclusive na reconexão), duas contas conversando, saída e novo login, porta livre, ocupada ou com o Chat Sakura, porta ocupada com mensagem clara, pasta de dados com espaços e acentos, logs com acentos e encerramento do servidor somente pelo PID registrado;
- IA: prompt em blocos e orçamento de contexto, memória (criação, isolamento, persistência, exclusão e filtro), fila limitada, tempo esgotado, resposta truncada, Ollama parado, API nativa e remota, reconexão durante a geração e detecção opcional do Ollama (com e sem o modelo, sem substituir configurações explícitas), todos com provedores e servidores simulados;
- codec JSON;
- conversas privadas, grupos, isolamento, mensagens offline e reinício;
- avatares;
- personagens de IA;
- provedor Qwen contra um servidor HTTP local que imita a API, incluindo erros, tempo limite e chave fora de logs e eventos;
- interface: barra lateral, não lidas, temas e exportação;
- aparência: as oito paletas (contraste, matiz, cache), alternância de modo, três fundos (centro livre, decoração só nas bordas), tela com prévia, preferência local e compatibilidade com arquivos antigos, gravação por conta (isolamento, reinício, valores inválidos, migração v6), entrega após login e reconexão, e atualização da interface somente na EDT;
- pesquisa e fixadas: normalização Unicode e trechos, resultados e ausência deles, paginação sem repetições, filtros, termo literal, acesso negado sem revelar a conversa, fixar e desafixar em privadas e grupos (papel ADMIN), limite de 10 com pedidos concorrentes em dois servidores sobre o mesmo banco, persistência após reinício, exclusão de mensagens e conversas, migração v7, protocolo sobre TLS, abertura de mensagem antiga com destaque e interface nas oito paletas.

Os arquivos `.bat` não são cobertos pelo JUnit. Eles foram verificados executando-os no Windows 11, numa cópia limpa do projeto em uma pasta com espaços e acentos: com e sem Maven instalado, sem Java (mensagem de orientação), com Java 8 (mensagem de versão antiga), com a porta 12345 ocupada, com o servidor já aberto (segunda janela de chat) e pelo pacote portátil sem nenhum Java no sistema.

### Observações e limitações

- **Uso local:** o inicializador e o pacote portátil funcionam somente no próprio computador (`127.0.0.1`), sem TLS. Para outros computadores, use o servidor comum com TLS.
- **Não execute `mvn clean` com o servidor ou os clientes abertos.** O comando apaga a pasta `target`, que eles estão usando.
- Execute apenas um servidor por vez: a porta `12345` não pode estar em uso.
- **TLS com certificado autoassinado:** cada cliente precisa receber o certificado público do servidor (truststore). Clientes legados em texto puro, como versões antigas ou `telnet`, não falam TLS: só funcionam com um servidor no modo de desenvolvimento.
- **Qwen:** verificado com o `qwen2.5:3b` local pelo Ollama. A API do Model Studio foi testada contra um servidor que imita a API; os nomes de modelo e as cotas gratuitas podem mudar.
- Grupos e personagens não podem ser editados nem excluídos pela interface.
- A exportação TXT inclui a conversa carregada na janela, não o histórico completo do banco.

Outras limitações e melhorias futuras estão descritas no relatório técnico.

### Documentação

| Documento | Conteúdo |
|---|---|
| [RELATORIO_TECNICO.md](RELATORIO_TECNICO.md) | Arquitetura, sincronização, segurança, protocolo, migrações, Qwen, testes e limitações |
| [ROTEIRO_VIDEO.md](ROTEIRO_VIDEO.md) | Roteiro da demonstração em vídeo |
| [sql/criar_tabelas.sql](sql/criar_tabelas.sql) | Esquema atual do banco (versão 3) |
| [sql/MIGRACOES.md](sql/MIGRACOES.md) | Versões do banco, backup e restauração |
| [AGENTS.md](AGENTS.md) | Requisitos e regras de implementação do projeto |

### Repositório

https://github.com/ane-godk/chat-sakura
