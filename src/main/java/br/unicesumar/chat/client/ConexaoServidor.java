package br.unicesumar.chat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Comunicação TCP com o servidor do Chat Sakura (protocolo de texto atual).
 *
 * Não conhece Swing: a conexão, a leitura e cada envio rodam em threads
 * próprias, e os eventos são entregues ao {@link Ouvinte} nessas threads.
 * Quem atualiza a interface deve repassá-los à EDT.
 */
final class ConexaoServidor {

    static final int TEMPO_LIMITE_CONEXAO_MS = 5000;

    static final String PREFIXO_CONFIRMACAO = ProtocoloSakura.PREFIXO_CONFIRMACAO;

    /** Eventos da conexão, chamados fora da EDT. */
    interface Ouvinte {

        /** Linha a exibir: vinda do servidor ou aviso local de erro. */
        void linhaRecebida(String linha);

        /** O servidor confirmou a entrada do usuário. */
        void conexaoConfirmada();

        /** Uma mensagem foi escrita no socket com sucesso. */
        void mensagemEnviada();

        /** Mudou o estado (conectando, conectado ou desconectado). */
        void estadoAlterado();

        /** Fim da sessão: saída voluntária, recusa ou queda. */
        void sessaoEncerrada();

        /** Conta autenticada pelo servidor; o apelido é o nome exibido. */
        default void identidadeConfirmada(String apelido) {
        }

        /** Cadastro ou login recusado; mensagem própria para o usuário. */
        default void autenticacaoRecusada(String mensagem) {
        }

        /** Perfil público da conta autenticada (inclui o avatar). */
        default void perfilRecebido(br.unicesumar.chat.model.PerfilUsuario perfil) {
        }

        /** Evento do protocolo v2 (sessões com conta). */
        default void eventoRecebido(ProtocoloJson.Evento evento) {
        }

        /**
         * A sessão terminou com envios ainda na fila: eles não foram escritos
         * e não serão reenviados automaticamente (o recebimento é incerto).
         */
        default void enviosDescartados(int quantidade) {
        }
    }

    /** Forma de entrada escolhida pelo usuário. */
    sealed interface PedidoEntrada {

        /** Apaga a senha da memória (quando houver). */
        default void limpar() {
        }
    }

    /** Apelido sem conta, como o cliente legado. */
    record Visitante(String nome) implements PedidoEntrada {
        Visitante {
            Objects.requireNonNull(nome, "Nome obrigatório.");
        }
    }

    record Entrar(String login, char[] senha) implements PedidoEntrada {
        Entrar {
            Objects.requireNonNull(login, "Login obrigatório.");
            Objects.requireNonNull(senha, "Senha obrigatória.");
        }

        @Override
        public void limpar() {
            Arrays.fill(senha, '\0');
        }

        @Override
        public String toString() {
            return "Entrar[" + login + ", ****]";
        }
    }

    record Cadastrar(String login, String apelido, char[] senha) implements PedidoEntrada {
        Cadastrar {
            Objects.requireNonNull(login, "Login obrigatório.");
            Objects.requireNonNull(apelido, "Apelido obrigatório.");
            Objects.requireNonNull(senha, "Senha obrigatória.");
        }

        @Override
        public void limpar() {
            Arrays.fill(senha, '\0');
        }

        @Override
        public String toString() {
            return "Cadastrar[" + login + ", " + apelido + ", ****]";
        }
    }

    /** Inicia uma tarefa de rede em segundo plano (substituível nos testes). */
    interface IniciadorTarefas {
        void iniciar(Runnable tarefa, String nome);
    }

    static final IniciadorTarefas THREADS_DAEMON = (tarefa, nome) -> {
        Thread thread = new Thread(tarefa, nome);
        thread.setDaemon(true);
        thread.start();
    };

    private final Ouvinte ouvinte;
    private final IniciadorTarefas tarefas;

    private final AtomicBoolean conectado = new AtomicBoolean(false);
    private final AtomicBoolean conectando = new AtomicBoolean(false);

    private final Object bloqueioEscrita = new Object();

    private volatile Socket socket;
    private volatile BufferedWriter saida;

    /** Envios pendentes por sessão (excesso é recusado, nunca bloqueia a EDT). */
    static final int CAPACIDADE_ENVIOS = 500;

    // Um único escritor por sessão: mensagens e comandos saem na ordem em
    // que foram pedidos, sem criar uma thread por envio.
    private volatile ExecutorService escritor;

    ConexaoServidor(Ouvinte ouvinte) {
        this(ouvinte, THREADS_DAEMON);
    }

    ConexaoServidor(Ouvinte ouvinte, IniciadorTarefas tarefas) {
        this.ouvinte = Objects.requireNonNull(ouvinte, "Ouvinte obrigatório.");
        this.tarefas = Objects.requireNonNull(tarefas, "Iniciador obrigatório.");
    }

    boolean conectado() {
        return conectado.get();
    }

    boolean conectando() {
        return conectando.get();
    }

    /** {@code true} enquanto há uma sessão em andamento ou sendo aberta. */
    boolean ocupada() {
        return conectado.get() || conectando.get();
    }

    /**
     * Abre a conexão em segundo plano e retorna imediatamente.
     *
     * @return {@code false} se já existe uma conexão ativa ou em andamento
     */
    boolean iniciar(String endereco, int porta, String nome) {
        return iniciar(endereco, porta, new Visitante(nome));
    }

    /**
     * Abre a conexão em segundo plano e retorna imediatamente. A senha do
     * pedido é apagada assim que enviada (ou se a conexão não começar).
     *
     * @return {@code false} se já existe uma conexão ativa ou em andamento
     */
    boolean iniciar(String endereco, int porta, PedidoEntrada pedido) {

        Objects.requireNonNull(endereco, "Endereço obrigatório.");
        Objects.requireNonNull(pedido, "Pedido obrigatório.");

        if (conectado.get() || !conectando.compareAndSet(false, true)) {
            pedido.limpar();
            return false;
        }

        // Registrado antes da thread: "Sair" já consegue interromper
        // uma tentativa de conexão em andamento.
        Socket novaConexao = new Socket();
        socket = novaConexao;
        ExecutorService envios = novoEscritor();
        escritor = envios;

        tarefas.iniciar(
                () -> executarSessao(novaConexao, envios, endereco, porta, pedido),
                "sakura-conexao"
        );

        return true;
    }

    private void executarSessao(
            Socket novaConexao,
            ExecutorService envios,
            String endereco,
            int porta,
            PedidoEntrada pedido
    ) {

        try {
            novaConexao.connect(
                    new InetSocketAddress(endereco, porta),
                    TEMPO_LIMITE_CONEXAO_MS
            );

            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                            novaConexao.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            BufferedWriter escritor = new BufferedWriter(
                    new OutputStreamWriter(
                            novaConexao.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            synchronized (bloqueioEscrita) {
                saida = escritor;
            }

            // O servidor atual envia duas linhas iniciais.
            String saudacao = entrada.readLine();
            String pedidoNome = entrada.readLine();

            if (saudacao == null || pedidoNome == null) {
                throw new IOException("O servidor encerrou a conexão.");
            }

            ouvinte.linhaRecebida(saudacao);
            ouvinte.linhaRecebida(pedidoNome);

            if (pedido instanceof Visitante visitante) {
                escreverLinha(visitante.nome());
            } else {
                autenticar(entrada, pedido);
                executarSessaoJson(entrada);
                return;
            }

            String confirmacao = entrada.readLine();

            if (confirmacao == null
                    || !confirmacao.startsWith(PREFIXO_CONFIRMACAO)) {

                if (confirmacao != null) {
                    ouvinte.linhaRecebida(confirmacao);
                }

                throw new IOException("O servidor recusou a conexão.");
            }

            conectado.set(true);
            conectando.set(false);

            // Conexão confirmada pelo servidor.
            ouvinte.conexaoConfirmada();
            ouvinte.linhaRecebida(confirmacao);
            ouvinte.estadoAlterado();

            String linha;

            while (conectado.get()
                    && (linha = entrada.readLine()) != null) {

                ouvinte.linhaRecebida(linha);
            }

        } catch (IOException e) {

            ouvinte.linhaRecebida("SISTEMA: " + e.getMessage());

        } finally {

            pedido.limpar();

            conectado.set(false);
            conectando.set(false);

            // Nada é escrito depois do fim da sessão; o que estava na fila
            // é descartado e informado (sem reenvio automático).
            int descartados = envios.shutdownNow().size();
            if (descartados > 0) {
                ouvinte.enviosDescartados(descartados);
            }

            // Fim da sessão (Sair ou queda): o som toca uma única vez,
            // e só se a conexão chegou a ser confirmada.
            ouvinte.sessaoEncerrada();

            try {
                novaConexao.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar conexão: " + e.getMessage());
            }

            synchronized (bloqueioEscrita) {
                if (socket == novaConexao) {
                    socket = null;
                    saida = null;
                }
            }

            ouvinte.estadoAlterado();
        }
    }

    /**
     * Negociação e autenticação (ver {@link ProtocoloSakura}). Uma tentativa
     * por conexão: em caso de recusa a sessão termina e o usuário tenta de novo.
     */
    private void autenticar(BufferedReader entrada, PedidoEntrada pedido) throws IOException {

        escreverLinha(ProtocoloSakura.NEGOCIACAO);

        String negociacao = entrada.readLine();

        if (!ProtocoloSakura.NEGOCIACAO_ACEITA.equals(negociacao)) {
            String motivo;
            try {
                motivo = ProtocoloSakura.lerNegociacaoRecusada(negociacao);
            } catch (IllegalArgumentException e) {
                motivo = null;
            }
            ouvinte.autenticacaoRecusada(motivo != null
                    ? motivo
                    : "Este servidor não oferece contas. Entre como visitante.");
            throw new IOException("O servidor recusou a conexão.");
        }

        String comando;

        if (pedido instanceof Entrar entrar) {
            comando = ProtocoloSakura.comandoEntrar(entrar.login(), entrar.senha());
        } else if (pedido instanceof Cadastrar cadastro) {
            comando = ProtocoloSakura.comandoCadastrar(cadastro.login(), cadastro.apelido(), cadastro.senha());
        } else {
            throw new IllegalStateException("Pedido de entrada desconhecido.");
        }

        // A senha só existe a partir daqui na linha codificada.
        pedido.limpar();
        escreverLinha(comando);

        ProtocoloSakura.RespostaAutenticacao resposta;

        try {
            resposta = ProtocoloSakura.lerResposta(entrada.readLine());
        } catch (IllegalArgumentException e) {
            throw new IOException("Resposta inválida do servidor.");
        }

        if (!resposta.sucesso()) {
            ouvinte.autenticacaoRecusada(resposta.mensagem());
            throw new IOException("O servidor recusou a conexão.");
        }

        ouvinte.identidadeConfirmada(resposta.perfil().getApelido());
        ouvinte.perfilRecebido(resposta.perfil());
    }

    /**
     * Protocolo v2: o primeiro evento é a lista de conversas (entrada
     * confirmada) ou um erro (entrada recusada, por exemplo sessão repetida).
     */
    private void executarSessaoJson(BufferedReader entrada) throws IOException {

        ProtocoloJson.Evento primeiro;

        try {
            primeiro = ProtocoloJson.lerEvento(entrada.readLine());
        } catch (ProtocoloJson.ProtocoloInvalidoException e) {
            throw new IOException("Resposta inválida do servidor.");
        }

        if (primeiro.tipo() == ProtocoloJson.TipoEvento.ERRO) {
            ouvinte.autenticacaoRecusada(primeiro.mensagemErro());
            throw new IOException("O servidor recusou a conexão.");
        }

        if (primeiro.tipo() != ProtocoloJson.TipoEvento.CONVERSAS) {
            throw new IOException("Resposta inválida do servidor.");
        }

        conectado.set(true);
        conectando.set(false);

        ouvinte.conexaoConfirmada();
        ouvinte.eventoRecebido(primeiro);
        ouvinte.estadoAlterado();

        String linha;

        while (conectado.get() && (linha = entrada.readLine()) != null) {
            try {
                ProtocoloJson.Evento evento = ProtocoloJson.lerEvento(linha);
                if (evento.tipo() == ProtocoloJson.TipoEvento.PING) {
                    // Heartbeat: confirma que a sessão continua ativa.
                    escreverLinha(ProtocoloJson.requisicao(ProtocoloJson.TipoRequisicao.PONG, null));
                    continue;
                }
                ouvinte.eventoRecebido(evento);
            } catch (ProtocoloJson.ProtocoloInvalidoException | RuntimeException e) {
                System.err.println("Evento ignorado: " + e.getMessage());
            }
        }
    }

    /**
     * Envia uma requisição JSON em segundo plano, sem a animação e o som
     * de mensagem enviada.
     *
     * @return {@code false} se não há conexão confirmada
     */
    boolean enviarComando(String linha) {

        Objects.requireNonNull(linha, "Linha obrigatória.");

        return agendarEnvio(() -> {
            try {
                escreverLinha(linha);
            } catch (IOException e) {
                ouvinte.linhaRecebida("SISTEMA: Falha ao enviar: " + e.getMessage());
            }
        });
    }

    /**
     * Envia uma linha em segundo plano.
     *
     * @return {@code false} se não há conexão confirmada
     */
    boolean enviar(String texto) {

        Objects.requireNonNull(texto, "Texto obrigatório.");

        return agendarEnvio(() -> {

            try {
                escreverLinha(texto);
                ouvinte.mensagemEnviada();

            } catch (IOException e) {
                ouvinte.linhaRecebida("SISTEMA: Falha ao enviar: " + e.getMessage());
            }
        });
    }

    /**
     * Coloca o envio na fila do escritor da sessão, sem bloquear.
     *
     * @return {@code false} sem conexão confirmada ou com a fila cheia
     */
    private boolean agendarEnvio(Runnable envio) {

        ExecutorService atual = escritor;

        if (!conectado.get() || atual == null) {
            return false;
        }

        try {
            atual.execute(() -> {
                // A sessão pode ter terminado enquanto o envio esperava.
                if (conectado.get()) {
                    envio.run();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            ouvinte.linhaRecebida(atual.isShutdown()
                    ? "SISTEMA: Não enviado: a conexão foi encerrada."
                    : "SISTEMA: Não enviado: muitos envios pendentes. Aguarde um instante.");
            return false;
        }
    }

    private static ExecutorService novoEscritor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(CAPACIDADE_ENVIOS), tarefa -> {
                    Thread thread = new Thread(tarefa, "sakura-envio");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** Encerra a sessão atual; não bloqueia (apenas fecha o socket). */
    void desconectar() {

        conectado.set(false);

        Socket conexaoAtual = socket;

        if (conexaoAtual != null) {
            try {
                conexaoAtual.close();
            } catch (IOException e) {
                System.err.println("Erro ao desconectar: " + e.getMessage());
            }
        }

        ouvinte.estadoAlterado();
    }

    private void escreverLinha(String texto) throws IOException {

        synchronized (bloqueioEscrita) {

            if (saida == null) {
                throw new IOException("Não existe conexão ativa.");
            }

            saida.write(texto);
            saida.newLine();
            saida.flush();
        }
    }
}
