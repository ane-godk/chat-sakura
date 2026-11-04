# Chat Sakura

Sistema de chat multiusuário desenvolvido em Java 17, com comunicação TCP, interface gráfica Swing, persistência SQLite e testes automatizados com JUnit 5.

## Funcionalidades

- Comunicação em tempo real entre múltiplos usuários.
- Servidor TCP com atendimento simultâneo por threads.
- Interface gráfica desenvolvida com Java Swing.
- Identificação dos participantes por nome de usuário.
- Validação de nomes duplicados.
- Armazenamento das mensagens em banco SQLite.
- Recuperação das mensagens recentes ao conectar.
- Exportação das mensagens exibidas para arquivo TXT.
- Testes unitários, de persistência e de concorrência.

## Tecnologias utilizadas

- Java 17
- Maven
- Java Swing
- Java Sockets (TCP)
- SQLite e JDBC
- JUnit 5
- Git e GitHub

## Arquitetura

O projeto utiliza separação de responsabilidades em pacotes:

| Pacote | Responsabilidade |
|---|---|
| `model` | Entidades e validações |
| `persistence` | Contrato de persistência |
| `persistence.jdbc` | Implementação SQLite |
| `service` | Regras de negócio e gerenciamento de clientes |
| `server` | Servidor TCP e tratamento das conexões |
| `client` | Interface gráfica Swing |

## Requisitos

- JDK 17 ou superior
- Apache Maven

## Como executar

### 1. Compilar e testar

Na raiz do projeto:

```bash
mvn clean test
```

### 2. Iniciar o servidor

```bash
mvn exec:java "-Dexec.mainClass=br.unicesumar.chat.server.Servidor"
```

O servidor utiliza a porta `12345` e mantém as mensagens no banco SQLite.

### 3. Iniciar o cliente

Em outro terminal:

```bash
mvn exec:java "-Dexec.mainClass=br.unicesumar.chat.client.ClienteChat"
```

Na janela, clique em **Conectar** e informe:

- Nome: `Ana`
- Servidor: `localhost`
- Porta: `12345`

Para testar a comunicação multiusuário, abra mais duas instâncias do cliente, utilizando nomes diferentes.

## Exportação TXT

Na interface do cliente, clique em **Exportar TXT**, selecione o destino e salve o arquivo.

O TXT contém as mensagens exibidas na janela no momento da exportação.

## Testes

O projeto possui testes para:

- Validação de usuários e mensagens.
- Persistência em memória.
- Persistência SQLite.
- Regras de negócio do chat.
- Gerenciamento de clientes.
- Concorrência e integridade das mensagens.

Para executar todos os testes:

```bash
mvn clean test
```

## Demonstração

A demonstração apresenta o servidor em execução e três clientes conectados simultaneamente, com troca de mensagens, recuperação de histórico e exportação TXT.

## Repositório

https://github.com/ane-godk/chat-sakura
