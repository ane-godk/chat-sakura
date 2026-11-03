# AGENTS.md — Chat Sakura

## Contexto do projeto

Chat multiusuário com histórico, desenvolvido em Java 17, utilizando Maven, sockets TCP, threads, Swing, JDBC e SQLite.

## Arquitetura obrigatória

- `domain`: classes de domínio e contratos, sem dependências de rede, JDBC ou arquivos.
- `persistence`: implementações dos repositórios em memória e JDBC.
- `server`: servidor TCP, gerenciamento de conexões e broadcast.
- `client`: interface gráfica Swing e comunicação com o servidor.

## Regras de implementação

1. A classe `Mensagem` deve permanecer imutável.
2. O domínio não pode importar `java.sql` nem `java.io`.
3. A persistência deve implementar `MensagemRepository`.
4. O servidor deve aceitar múltiplos clientes por meio de `ServerSocket`.
5. Cada conexão deve possuir um `ClienteHandler implements Runnable`, iniciado em uma nova thread.
6. A coleção de clientes ativos deve ser thread-safe.
7. O broadcast deve percorrer os clientes dentro de `synchronized (clientes)`.
8. Falhas de envio para um cliente não podem interromper os demais.
9. O JDBC deve utilizar `PreparedStatement` e `try-with-resources`.
10. Atualizações da interface Swing originadas em threads de rede devem utilizar `SwingUtilities.invokeLater`.
11. O projeto deve possuir pelo menos cinco testes unitários independentes de rede e banco real.
12. O teste de concorrência deve utilizar `ExecutorService` e `awaitTermination`, sem `Thread.sleep`.

## Diretrizes de qualidade

- Utilizar nomes claros para classes e métodos.
- Manter responsabilidades separadas por camada.
- Tratar erros de conexão e persistência.
- Preservar a compatibilidade com Java 17.
- Executar `mvn test` antes de cada entrega.
- Não incluir bancos locais, credenciais ou arquivos compilados no Git.
