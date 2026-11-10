package br.unicesumar.chat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
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

    static final String PREFIXO_CONFIRMACAO = "SISTEMA: Conectado como ";

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

        Objects.requireNonNull(endereco, "Endereço obrigatório.");
        Objects.requireNonNull(nome, "Nome obrigatório.");

        if (conectado.get() || !conectando.compareAndSet(false, true)) {
            return false;
        }

        // Registrado antes da thread: "Sair" já consegue interromper
        // uma tentativa de conexão em andamento.
        Socket novaConexao = new Socket();
        socket = novaConexao;

        tarefas.iniciar(
                () -> executarSessao(novaConexao, endereco, porta, nome),
                "sakura-conexao"
        );

        return true;
    }

    private void executarSessao(
            Socket novaConexao,
            String endereco,
            int porta,
            String nome
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

            escreverLinha(nome);

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

            conectado.set(false);
            conectando.set(false);

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
     * Envia uma linha em segundo plano.
     *
     * @return {@code false} se não há conexão confirmada
     */
    boolean enviar(String texto) {

        Objects.requireNonNull(texto, "Texto obrigatório.");

        if (!conectado.get()) {
            return false;
        }

        tarefas.iniciar(() -> {

            try {
                escreverLinha(texto);
                ouvinte.mensagemEnviada();

            } catch (IOException e) {
                ouvinte.linhaRecebida("SISTEMA: Falha ao enviar: " + e.getMessage());
            }

        }, "sakura-envio");

        return true;
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
