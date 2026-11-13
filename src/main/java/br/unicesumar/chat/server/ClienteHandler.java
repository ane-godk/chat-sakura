
package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.MemoriaPersonagem;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.model.RegrasConta;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocolo.ProtocoloJson;
import br.unicesumar.chat.protocolo.ProtocoloSakura;
import br.unicesumar.chat.service.AutenticacaoException;
import br.unicesumar.chat.service.ChatService;
import br.unicesumar.chat.service.ConexaoCliente;
import br.unicesumar.chat.service.ConversaException;
import br.unicesumar.chat.service.ResumoConversa;
import br.unicesumar.chat.service.ServicoAutenticacao;
import br.unicesumar.chat.service.ServicoConversas;
import br.unicesumar.chat.service.ServicoIA;
import br.unicesumar.chat.service.ServicoPreferencias;
import br.unicesumar.chat.service.ServicoMensagens;
import br.unicesumar.chat.model.FiltroPesquisa;
import br.unicesumar.chat.model.MensagemFixada;
import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.service.SessaoEncerradaException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uma conexão com um cliente (legado em texto ou protocolo v2 em JSON).
 *
 * Robustez:
 * <ul>
 *   <li>toda escrita passa pela {@link FilaSaida} (limitada, um único
 *       escritor): broadcasts só enfileiram, nunca fazem I/O sob bloqueio;
 *       fila cheia derruba a sessão (cliente que não lê);</li>
 *   <li>prazo total para concluir a entrada (nome ou login);</li>
 *   <li>v2: PING após inatividade e queda se não houver resposta; o legado
 *       não precisa de heartbeat e pode ficar parado à vontade;</li>
 *   <li>limites de taxa por sessão (token bucket, sem espera);</li>
 *   <li>um novo login da mesma conta substitui esta sessão, que é
 *       encerrada e não consegue mais enviar.</li>
 * </ul>
 */
public final class ClienteHandler
        implements Runnable, ConexaoCliente {

    static final int QUANTIDADE_HISTORICO = 20;

    static final String MENSAGEM_LIMITE =
            "Muitas solicitações em pouco tempo. Aguarde um instante.";
    static final String MENSAGEM_LIMITE_LEGADO =
            "SISTEMA: Você está enviando mensagens rápido demais. Aguarde um instante.";
    static final String MENSAGEM_PRAZO_ENTRADA =
            "SISTEMA: Tempo para entrar esgotado. Conecte-se novamente.";

    /** Prazos e encerramentos agendados de todas as sessões (uma thread). */
    private static final ScheduledExecutorService VIGIA =
            Executors.newSingleThreadScheduledExecutor(tarefa -> {
                Thread thread = new Thread(tarefa, "chat-vigia");
                thread.setDaemon(true);
                return thread;
            });

    private static final long AVISO_LIMITE_NANOS = 1_000_000_000L;

    private final Socket socket;
    private final ChatService chatService;
    private final LimitesServidor limites;
    private final Runnable aoEncerrar;
    private final String origem;

    // Protegidos pelo monitor deste handler (this).
    private final List<Mensagem> pendentes = new ArrayList<>();
    private boolean historicoEnviado;

    static final String MENSAGEM_NOME_RESERVADO =
            "SISTEMA: Não foi possível entrar: este nome pertence a uma conta"
                    + " cadastrada. Entre com login e senha em um cliente atualizado.";

    /** Nulos juntos: servidor sem contas (somente modo legado). */
    private final ServicoAutenticacao autenticacao;
    private final ServicoConversas conversas;
    private final ServicoIA ia;
    private final ServicoPreferencias preferencias;
    private final ServicoMensagens servicoMensagens;

    // Sessão autenticada no protocolo v2 (eventos JSON); definido uma vez.
    private volatile boolean sessaoJson;

    // Substituída por outro login ou derrubada: não atende mais pedidos.
    private volatile boolean encerrada;
    private volatile boolean entradaConcluida;
    private final AtomicBoolean socketFechado = new AtomicBoolean();
    private final AtomicBoolean vagaLiberada = new AtomicBoolean();

    private final BaldeTokens baldeMensagens;
    private final BaldeTokens baldeCriacoes;
    private final BaldeTokens baldeConsultas;
    private final BaldeTokens baldeLeituras;
    private final BaldeTokens baldeLinhas;
    private boolean avisouLimite;
    private long ultimoAvisoLimite;

    private LeitorLinhas entrada;
    private volatile FilaSaida saida;
    private ScheduledFuture<?> prazoEntrada;

    // Identidade da sessão: definida somente pelo servidor, uma única vez,
    // a partir do apelido (modo legado) ou da conta autenticada.
    private volatile Usuario usuario;

    public ClienteHandler(
            Socket socket,
            ChatService chatService
    ) {
        this(socket, chatService, null, null);
    }

    /**
     * @param autenticacao contas (nulo junto com {@code conversas}: só legado)
     * @param conversas    conversas privadas e grupos do protocolo v2
     */
    public ClienteHandler(
            Socket socket,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas
    ) {
        this(socket, chatService, autenticacao, conversas, null);
    }

    /** @param ia personagens de IA (nulo: recurso indisponível) */
    public ClienteHandler(
            Socket socket,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas,
            ServicoIA ia
    ) {
        this(socket, chatService, autenticacao, conversas, ia, LimitesServidor.padrao(), () -> { });
    }

    /**
     * @param limites    limites de recursos desta sessão
     * @param aoEncerrar chamado uma única vez ao fim da sessão (libera a vaga)
     */
    public ClienteHandler(
            Socket socket,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas,
            ServicoIA ia,
            LimitesServidor limites,
            Runnable aoEncerrar
    ) {
        this(socket, chatService, autenticacao, conversas, ia, limites, aoEncerrar, null, null);
    }

    public ClienteHandler(
            Socket socket,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas,
            ServicoIA ia,
            LimitesServidor limites,
            Runnable aoEncerrar,
            ServicoPreferencias preferencias
    ) {
        this(socket, chatService, autenticacao, conversas, ia, limites, aoEncerrar, preferencias, null);
    }

    /**
     * @param preferencias aparência por conta (nulo: recurso indisponível)
     * @param mensagens    pesquisa e mensagens fixadas (nulo: recurso indisponível)
     */
    public ClienteHandler(
            Socket socket,
            ChatService chatService,
            ServicoAutenticacao autenticacao,
            ServicoConversas conversas,
            ServicoIA ia,
            LimitesServidor limites,
            Runnable aoEncerrar,
            ServicoPreferencias preferencias,
            ServicoMensagens mensagens
    ) {
        this.ia = ia;
        this.preferencias = preferencias;
        this.servicoMensagens = mensagens;

        if ((autenticacao == null) != (conversas == null)) {
            throw new IllegalArgumentException(
                    "Contas e conversas devem ser informadas juntas."
            );
        }

        this.conversas = conversas;
        this.socket = Objects.requireNonNull(
                socket,
                "Socket obrigatório."
        );

        this.chatService = Objects.requireNonNull(
                chatService,
                "Serviço obrigatório."
        );

        this.autenticacao = autenticacao;
        this.limites = Objects.requireNonNull(limites, "Limites obrigatórios.");
        this.aoEncerrar = Objects.requireNonNull(aoEncerrar);

        InetAddress endereco = socket.getInetAddress();
        this.origem = endereco == null ? null : endereco.getHostAddress();

        this.baldeMensagens = limites.mensagens().novoBalde(limites.relogioNanos());
        this.baldeCriacoes = limites.criacoes().novoBalde(limites.relogioNanos());
        this.baldeConsultas = limites.consultas().novoBalde(limites.relogioNanos());
        this.baldeLeituras = limites.leituras().novoBalde(limites.relogioNanos());
        this.baldeLinhas = limites.linhas().novoBalde(limites.relogioNanos());
    }

    @Override
    public void run() {

        boolean registrado = false;

        try {

            // TLS: a negociação termina (ou falha) antes de qualquer leitura ou
            // escrita, com prazo próprio; falha encerra e libera a vaga.
            if (socket instanceof SSLSocket tls && !concluirHandshakeTls(tls)) {
                return;
            }

            configurarSocket();

            entrada = new LeitorLinhas(
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    ),
                    ProtocoloSakura.TAMANHO_MAXIMO_LINHA
            );

            saida = new FilaSaida(
                    limites.capacidadeFila(),
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(),
                                    StandardCharsets.UTF_8
                            )
                    ),
                    "chat-saida-" + socket.getPort(),
                    this::clienteLento,
                    this::fecharSocket
            );
            // Encerrada por substituição ou prazo: fecha assim que o aviso
            // final for entregue (a leitura bloqueada termina na hora).
            saida.aoConcluir(() -> {
                if (encerrada) {
                    fecharSocket();
                }
            });
            saida.iniciar();

            // Prazo total da entrada: impede conexões paradas no handshake.
            prazoEntrada = VIGIA.schedule(this::prazoDeEntradaEsgotado,
                    limites.prazoEntrada().toMillis(), TimeUnit.MILLISECONDS);

            enviarLinha(
                    "SISTEMA: Bem-vindo ao Chat Sakura!"
            );

            enviarLinha(
                    "SISTEMA: Informe seu nome de usuário:"
            );

            String nome;

            try {
                nome = entrada.lerLinha();
            } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                enviarLinha("SISTEMA: Nome inválido: linha longa demais.");
                return;
            }

            if (nome == null) {
                return;
            }

            if (ProtocoloSakura.NEGOCIACAO.equals(nome)) {

                // Cliente atualizado: a identidade vem da conta.
                usuario = autenticarSessao();

                if (usuario == null) {
                    return;
                }

                sessaoJson = true;

            } else {

                // Cliente legado ou visitante: o apelido não pode ser
                // o nome de uma conta cadastrada.
                try {
                    usuario = new Usuario(nome);
                    // Mesmas regras dos apelidos de conta: sem invisíveis,
                    // espaços Unicode nem mistura de alfabetos.
                    RegrasConta.normalizarNomeVisitante(nome);
                } catch (IllegalArgumentException e) {
                    enviarLinha(
                            "SISTEMA: Nome inválido: " + e.getMessage()
                    );
                    return;
                }

                if (autenticacao != null
                        && autenticacao.nomeReservado(usuario.getNome())) {
                    enviarLinha(MENSAGEM_NOME_RESERVADO);
                    return;
                }
            }

            List<Mensagem> historico;

            try {
                historico = chatService.conectarCliente(
                        this,
                        QUANTIDADE_HISTORICO
                );
                registrado = true;
            } catch (IllegalArgumentException e) {
                enviarLinha(sessaoJson
                        ? ProtocoloJson.eventoErro(null, "ENTRADA_RECUSADA",
                                "Não foi possível entrar: " + e.getMessage())
                        : "SISTEMA: Não foi possível entrar: " + e.getMessage()
                );
                return;
            } catch (IllegalStateException e) {
                enviarLinha(sessaoJson
                        ? ProtocoloJson.eventoErro(null, "INDISPONIVEL",
                                "Não foi possível carregar o histórico.")
                        : "SISTEMA: Não foi possível carregar o histórico."
                );
                System.err.println(
                        "Erro de persistência: " + e.getMessage()
                );
                return;
            }

            concluirEntrada();

            if (sessaoJson) {
                // Protocolo v2: estado inicial e eventos JSON até o fim.
                iniciarSessaoJson(historico);
                executarSessaoJson();
                return;
            }

            // Broadcasts recebidos a partir daqui ficam pendentes
            // até que a confirmação e o histórico sejam enviados.
            enviarLinha(
                    "SISTEMA: Conectado como " + usuario.getNome()
            );

            enviarHistorico(historico);

            liberarMensagensPendentes();

            System.out.println(
                    usuario.getNome()
                            + " entrou. Clientes: "
                            + chatService.contarClientes()
            );

            executarSessaoLegada();

        } catch (IOException e) {

            System.err.println(
                    "Conexão encerrada: " + e.getMessage()
            );

        } catch (SessaoEncerradaException e) {

            // Substituída por um novo login: nada mais a fazer.

        } finally {

            concluirEntrada();

            if (registrado) {

                // Também emite o aviso de saída (uma única vez). Uma sessão
                // substituída já saiu do registro: nem aviso, nem remoção
                // da sessão nova.
                chatService.desconectarCliente(this);

                System.out.println(
                        usuario.getNome()
                                + " saiu. Clientes: "
                                + chatService.contarClientes()
                );
            }

            // Entrega o que já estava na fila (sem esperar um cliente lento
            // além do prazo) e só então fecha o socket.
            FilaSaida fila = saida;
            if (fila != null) {
                fila.encerrar();
                try {
                    fila.aguardar(limites.prazoEncerramento().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            fecharSocket();

            if (vagaLiberada.compareAndSet(false, true)) {
                aoEncerrar.run();
            }
        }
    }

    /** Laço do cliente legado: sem heartbeat e sem prazo de inatividade. */
    private void executarSessaoLegada() throws IOException {

        while (!encerrada) {

            // Depois da entrada, toda linha é conteúdo do chat: comandos
            // de autenticação não trocam a identidade da sessão.
            String texto;

            try {
                texto = entrada.lerLinha();
            } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                enviarLinha(
                        "SISTEMA: Mensagem inválida: a linha excede o"
                                + " limite do protocolo."
                );
                continue;
            }

            if (texto == null || encerrada) {
                break;
            }

            texto = texto.trim();

            if (texto.equalsIgnoreCase("/sair")) {
                break;
            }

            if (texto.isEmpty()) {
                continue;
            }

            if (!baldeLinhas.consumir() || !baldeMensagens.consumir()) {
                avisarLimite(MENSAGEM_LIMITE_LEGADO);
                continue;
            }

            try {

                chatService.enviarMensagem(
                        usuario,
                        texto,
                        this
                );

            } catch (IllegalArgumentException e) {

                enviarLinha(
                        "SISTEMA: Mensagem inválida: "
                                + e.getMessage()
                );

            } catch (IllegalStateException e) {

                enviarLinha(
                        "SISTEMA: Não foi possível salvar a mensagem."
                );

                System.err.println(
                        "Erro de persistência: "
                                + e.getMessage()
                );
            }
        }
    }

    /**
     * Fase de autenticação (ver {@link ProtocoloSakura}). Permite novas
     * tentativas até o limite; a senha recebida é apagada após cada uma.
     *
     * @return o participante da conta autenticada, ou {@code null} se a
     *         conexão deve ser encerrada
     */
    private Usuario autenticarSessao() throws IOException {

        if (autenticacao == null) {
            enviarLinha(ProtocoloSakura.negociacaoRecusada(
                    "Este servidor não oferece contas. Entre como visitante."));
            return null;
        }

        enviarLinha(ProtocoloSakura.NEGOCIACAO_ACEITA);

        for (int tentativa = 1; tentativa <= ProtocoloSakura.TENTATIVAS_MAXIMAS; tentativa++) {

            String linha;

            try {
                linha = entrada.lerLinha();
            } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                linha = "";
            }

            if (linha == null) {
                return null;
            }

            ProtocoloSakura.PedidoAutenticacao pedido;

            try {
                pedido = ProtocoloSakura.lerPedido(linha);
            } catch (IllegalArgumentException e) {
                enviarLinha(ProtocoloSakura.respostaErro(
                        "COMANDO_INVALIDO", "Comando de autenticação inválido."));
                continue;
            }

            try {
                PerfilUsuario perfil = pedido.isCadastro()
                        ? autenticacao.cadastrar(pedido.getLogin(), pedido.getApelido(), pedido.getSenha(), origem)
                        : autenticacao.autenticar(pedido.getLogin(), pedido.getSenha(), origem);

                enviarLinha(ProtocoloSakura.respostaSucesso(perfil));

                return perfil.comoUsuario();

            } catch (AutenticacaoException e) {
                enviarLinha(ProtocoloSakura.respostaErro(e.getCodigo().name(), e.getMessage()));

                if (e.getCodigo() == AutenticacaoException.Codigo.MUITAS_TENTATIVAS) {
                    return null;
                }

            } finally {
                pedido.limpar();
            }
        }

        enviarLinha(ProtocoloSakura.respostaErro(
                "TENTATIVAS_ESGOTADAS", "Muitas tentativas. Conecte-se novamente."));

        return null;
    }

    private void enviarHistorico(List<Mensagem> historico)
            throws IOException {

        enviarLinha(
                "SISTEMA: ===== HISTÓRICO RECENTE ====="
        );

        // Direto na fila: enviar() reteria até o fim do histórico.
        for (Mensagem mensagem : historico) {
            enviarLinha(mensagem.formatar());
        }

        enviarLinha(
                "SISTEMA: ===== FIM DO HISTÓRICO ====="
        );
    }

    /** Só enfileira (sem I/O): seguro sob qualquer bloqueio do chat. */
    private void enviarLinha(String texto) throws IOException {

        FilaSaida fila = saida;

        if (fila == null) {
            throw new IOException(
                    "Canal de saída não inicializado."
            );
        }

        if (!fila.enfileirar(texto)) {
            throw new IOException("Conexão encerrada.");
        }
    }

    /**
     * Envia as mensagens recebidas durante a conexão, na ordem de
     * chegada, e passa a entregar os próximos broadcasts diretamente.
     */
    private synchronized void liberarMensagensPendentes()
            throws IOException {

        for (Mensagem mensagem : pendentes) {
            escreverMensagem(mensagem);
        }

        pendentes.clear();
        historicoEnviado = true;
    }

    @Override
    public synchronized void enviar(Mensagem mensagem)
            throws IOException {

        if (!historicoEnviado) {
            // Apenas guarda (sem I/O), sem atrasar o broadcast; limitado
            // como a fila de saída.
            if (pendentes.size() >= limites.capacidadeFila()) {
                clienteLento();
                throw new IOException("Fila de saída cheia.");
            }
            pendentes.add(mensagem);
            return;
        }

        escreverMensagem(mensagem);
    }

    /**
     * Sessões legadas recebem somente o Chat Geral, em texto; sessões v2
     * recebem eventos JSON. Defesa extra: o registro já filtra privadas.
     */
    private void escreverMensagem(Mensagem mensagem) throws IOException {

        if (sessaoJson) {
            enviarLinha(ProtocoloJson.eventoMensagem(ProtocoloJson.MensagemJson.de(mensagem)));
        } else if (mensagem.isDoChatGeral()) {
            enviarLinha(mensagem.formatar());
        }
    }

    @Override
    public synchronized void conversaDisponivel(ResumoConversa resumo) throws IOException {
        // Antes do estado inicial, a conversa já virá na lista completa.
        if (sessaoJson && historicoEnviado) {
            enviarLinha(ProtocoloJson.eventoConversa(null, paraJson(resumo)));
        }
    }

    // ------------------------------------------------------------------
    // Encerramento, prazos e limites
    // ------------------------------------------------------------------

    /**
     * Substituída por um novo login da mesma conta (já fora do registro):
     * avisa o cliente e fecha a conexão logo após entregar o aviso. Não
     * bloqueia: chamado sob os bloqueios do chat.
     */
    @Override
    public void encerrar(String motivo) {

        encerrada = true;

        FilaSaida fila = saida;
        if (fila != null) {
            fila.enfileirar(sessaoJson
                    ? ProtocoloJson.eventoErro(null, "SESSAO_SUBSTITUIDA", motivo)
                    : "SISTEMA: " + motivo);
            fila.encerrar();
        }

        System.out.println("Sessão encerrada por novo login: " + nomeParaLog());
        VIGIA.schedule(this::fecharSocket, limites.prazoEncerramento().toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Fila cheia: o cliente não está lendo; derruba sem afetar os demais. */
    private void clienteLento() {
        if (!encerrada) {
            encerrada = true;
            System.err.println("Cliente lento desconectado (fila de saída cheia): " + nomeParaLog());
        }
        fecharSocket();
    }

    private void prazoDeEntradaEsgotado() {
        if (entradaConcluida) {
            return;
        }
        System.err.println("Tempo para entrar esgotado: " + (origem == null ? "?" : origem));
        encerrada = true;
        FilaSaida fila = saida;
        if (fila != null) {
            fila.enfileirar(MENSAGEM_PRAZO_ENTRADA);
            fila.encerrar();
        }
        // Garantia: fecha mesmo que o aviso não possa ser entregue.
        VIGIA.schedule(this::fecharSocket, limites.prazoEncerramento().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void concluirEntrada() {
        entradaConcluida = true;
        ScheduledFuture<?> prazo = prazoEntrada;
        if (prazo != null) {
            prazo.cancel(false);
        }
    }

    private void fecharSocket() {
        if (socketFechado.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println(
                        "Erro ao fechar socket: " + e.getMessage()
                );
            }
        }
    }

    /** Prazo máximo da negociação TLS (também limitado pelo prazo de entrada). */
    static final long PRAZO_HANDSHAKE_TLS_MS = 10_000;

    /**
     * Negociação TLS com prazo. Registra só o endereço e o tipo da falha
     * (sem dados recebidos); o socket é fechado no {@code finally} de {@link #run}.
     */
    private boolean concluirHandshakeTls(SSLSocket tls) {
        long prazo = Math.min(PRAZO_HANDSHAKE_TLS_MS, limites.prazoEntrada().toMillis());
        try {
            tls.setSoTimeout((int) prazo);
            tls.startHandshake();
            tls.setSoTimeout(0);
            return true;
        } catch (IOException e) {
            String motivo = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
            System.err.println("Negociação TLS recusada (" + (origem == null ? "?" : origem) + "): "
                    + e.getClass().getSimpleName() + (motivo.isEmpty() ? "" : " - " + motivo));
            return false;
        }
    }

    private void configurarSocket() {
        try {
            // Detecta, pelo sistema operacional, conexões mortas também no
            // legado (que não usa heartbeat).
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
        } catch (SocketException e) {
            // Opções indisponíveis: segue sem elas.
        }
    }

    private void definirTempoLimite(int milissegundos) {
        try {
            socket.setSoTimeout(milissegundos);
        } catch (SocketException e) {
            // Socket já fechado: a próxima leitura encerra a sessão.
        }
    }

    /** Aviso de limite no máximo uma vez por segundo (o excesso é descartado). */
    private void avisarLimite(String linha) throws IOException {
        long agora = limites.relogioNanos().getAsLong();
        if (!avisouLimite || agora - ultimoAvisoLimite >= AVISO_LIMITE_NANOS) {
            avisouLimite = true;
            ultimoAvisoLimite = agora;
            enviarLinha(linha);
        }
    }

    private String nomeParaLog() {
        Usuario atual = usuario;
        return atual == null ? (origem == null ? "?" : origem) : atual.getNome();
    }

    // ------------------------------------------------------------------
    // Protocolo v2 (JSON)
    // ------------------------------------------------------------------

    /**
     * Estado inicial: lista de conversas (confirma a entrada) e histórico
     * da Geral; só então os broadcasts pendentes são liberados.
     */
    private void iniciarSessaoJson(List<Mensagem> historicoGeral) throws IOException {

        List<ResumoConversa> lista;

        try {
            lista = conversas.listar(usuario);
        } catch (ConversaException e) {
            lista = List.of();
            System.err.println("Lista de conversas indisponível para " + usuario.getNome());
        }

        enviarLinha(ProtocoloJson.eventoConversas(null, lista.stream().map(ClienteHandler::paraJson).toList()));
        enviarLinha(ProtocoloJson.eventoHistorico(null, Conversa.ID_GERAL,
                historicoGeral.stream().map(ProtocoloJson.MensagemJson::de).toList()));

        // Aparência da conta (também na reconexão); falha no banco vira o padrão.
        if (preferencias != null) {
            enviarLinha(ProtocoloJson.eventoAparencia(null, preferencias.aparencia(usuario)));
        }

        liberarMensagensPendentes();

        // Reconexão durante uma geração: o cliente volta a ver "digitando…"
        // e recebe a resposta (e o CONCLUIDO) quando ela ficar pronta.
        if (ia != null) {
            for (ResumoConversa resumo : lista) {
                long id = resumo.conversa().getId();
                if (resumo.conversa().getTipo() == Conversa.Tipo.IA && ia.gerando(id)) {
                    estadoIA(id, ServicoIA.GERANDO, ia.provedor().descricao());
                }
            }
        }

        System.out.println(usuario.getNome() + " entrou (v2). Clientes: " + chatService.contarClientes());
    }

    /**
     * Laço v2. Após {@code intervaloPing} sem nada recebido, o servidor envia
     * PING; se outro intervalo passar sem nenhuma linha (PONG ou pedido), a
     * sessão é considerada inativa e encerrada.
     */
    private void executarSessaoJson() throws IOException {

        definirTempoLimite((int) Math.min(Integer.MAX_VALUE, limites.intervaloPing().toMillis()));
        boolean pingPendente = false;

        while (!encerrada) {

            String linha;

            try {
                linha = entrada.lerLinha();
            } catch (SocketTimeoutException e) {
                if (pingPendente) {
                    System.err.println("Sessão inativa encerrada (sem resposta ao PING): " + nomeParaLog());
                    return;
                }
                pingPendente = true;
                enviarLinha(ProtocoloJson.eventoPing());
                continue;
            } catch (LeitorLinhas.LinhaMuitoLongaException e) {
                pingPendente = false;
                enviarLinha(ProtocoloJson.eventoErro(null, "LINHA_LONGA",
                        "A linha excede o limite do protocolo."));
                continue;
            }

            pingPendente = false;

            if (linha == null || encerrada) {
                return;
            }

            if (linha.isBlank()) {
                continue;
            }

            if (!baldeLinhas.consumir()) {
                avisarLimite(ProtocoloJson.eventoErro(null, "LIMITE", MENSAGEM_LIMITE));
                continue;
            }

            ProtocoloJson.Requisicao requisicao;

            try {
                requisicao = ProtocoloJson.lerRequisicao(linha);
            } catch (ProtocoloJson.ProtocoloInvalidoException e) {
                enviarLinha(ProtocoloJson.eventoErro(e.getReq(), "REQUISICAO_INVALIDA", e.getMessage()));
                continue;
            }

            if (!dentroDoLimite(requisicao)) {
                continue;
            }

            try {
                atender(requisicao);
            } catch (ProtocoloJson.ProtocoloInvalidoException e) {
                enviarLinha(ProtocoloJson.eventoErro(requisicao.req(), "REQUISICAO_INVALIDA", e.getMessage()));
            } catch (ConversaException e) {
                enviarLinha(ProtocoloJson.eventoErro(requisicao.req(), e.getCodigo().name(), e.getMessage()));
            }
        }
    }

    /**
     * Token bucket por categoria. Recusa na hora (sem esperar); o aviso de
     * limite é limitado a um por segundo e a confirmação de leitura em
     * excesso é descartada em silêncio.
     */
    private boolean dentroDoLimite(ProtocoloJson.Requisicao r) throws IOException {

        BaldeTokens balde = switch (r.tipo()) {
            case ENVIAR -> baldeMensagens;
            case ABRIR_PRIVADA, CRIAR_GRUPO, CRIAR_PERSONAGEM, DEFINIR_AVATAR, MEMORIA_APAGAR,
                 DEFINIR_APARENCIA, FIXAR, DESAFIXAR -> baldeCriacoes;
            case LISTAR_CONVERSAS, LISTAR_USUARIOS, HISTORICO, MEMORIA_VER, PESQUISAR, LISTAR_FIXADAS -> baldeConsultas;
            case MARCAR_LIDA -> baldeLeituras;
            case PONG -> null;
        };

        if (balde == null || balde.consumir()) {
            return true;
        }

        if (r.tipo() != ProtocoloJson.TipoRequisicao.MARCAR_LIDA) {
            avisarLimite(ProtocoloJson.eventoErro(r.req(), "LIMITE", MENSAGEM_LIMITE));
        }
        return false;
    }

    /** O remetente e o leitor são sempre o usuário desta sessão. */
    private void atender(ProtocoloJson.Requisicao r)
            throws IOException, ProtocoloJson.ProtocoloInvalidoException, ConversaException {

        Long req = r.req();

        switch (r.tipo()) {

            case PONG -> {
                // Apenas confirma que a sessão está ativa.
            }

            case LISTAR_CONVERSAS -> enviarLinha(ProtocoloJson.eventoConversas(req,
                    conversas.listar(usuario).stream().map(ClienteHandler::paraJson).toList()));

            case LISTAR_USUARIOS -> enviarLinha(ProtocoloJson.eventoUsuarios(req,
                    conversas.listarUsuarios(usuario).stream()
                            .map(u -> new ProtocoloJson.UsuarioJson(u.id(), u.apelido(), u.avatarId(), u.online()))
                            .toList()));

            case ABRIR_PRIVADA -> enviarLinha(ProtocoloJson.eventoConversa(req,
                    paraJson(conversas.abrirPrivada(usuario, r.id("usuarioId")))));

            case CRIAR_GRUPO -> enviarLinha(ProtocoloJson.eventoConversa(req,
                    paraJson(conversas.criarGrupo(usuario, r.texto("nome"),
                            r.ids("participantes", Conversa.PARTICIPANTES_MAXIMOS)))));

            // A própria mensagem volta pelo broadcast (o remetente participa).
            // Numa conversa de IA, a resposta é gerada depois, em segundo plano.
            // A participação é verificada antes de qualquer estado da IA.
            case ENVIAR -> {
                long conversaId = r.id("conversaId");
                String texto = r.texto("texto");
                if (ia != null) {
                    ia.verificarEnvio(usuario, conversaId);
                }
                Mensagem enviada = conversas.enviar(usuario, conversaId, texto, this);
                if (ia != null) {
                    ia.aposMensagem(usuario, enviada);
                }
            }

            case CRIAR_PERSONAGEM -> {
                if (ia == null) {
                    throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                            "A Sakura IA não está disponível neste servidor.");
                }
                enviarLinha(ProtocoloJson.eventoConversa(req, paraJson(ia.criarPersonagem(usuario,
                        r.texto("nome"), r.texto("personalidade"), r.texto("contexto")))));
            }

            case DEFINIR_AVATAR -> {
                PerfilUsuario perfil;
                try {
                    perfil = autenticacao.definirAvatar(usuario, r.texto("avatarId"));
                } catch (AutenticacaoException e) {
                    throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, e.getMessage());
                }
                enviarLinha(ProtocoloJson.eventoPerfil(req, paraJson(perfil)));
                conversas.anunciarPerfil(perfil);
            }

            case HISTORICO -> {
                long conversaId = r.id("conversaId");
                List<Mensagem> mensagens = conversas.historico(usuario, conversaId, r.idOpcional("antesDe"),
                        r.inteiro("limite", 1, ServicoConversas.HISTORICO_MAXIMO, ServicoConversas.HISTORICO_PADRAO));
                enviarLinha(ProtocoloJson.eventoHistorico(req, conversaId,
                        mensagens.stream().map(ProtocoloJson.MensagemJson::de).toList()));
            }

            case MARCAR_LIDA -> conversas.marcarLida(usuario, r.id("conversaId"), r.id("mensagemId"));

            // Memória do personagem: somente o dono (os demais recebem NAO_ENCONTRADA).
            case MEMORIA_VER, MEMORIA_APAGAR -> {
                if (ia == null) {
                    throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                            "A Sakura IA não está disponível neste servidor.");
                }
                long conversaId = r.id("conversaId");
                MemoriaPersonagem memoria = r.tipo() == ProtocoloJson.TipoRequisicao.MEMORIA_VER
                        ? ia.memoria(usuario, conversaId)
                        : ia.apagarMemoria(usuario, conversaId);
                enviarLinha(ProtocoloJson.eventoMemoria(req, conversaId, memoria.resumo(),
                        memoria.vazio() && memoria.atualizadaEm().equals(java.time.LocalDateTime.MIN)
                                ? null : memoria.atualizadaEm().toString()));
            }

            // Sempre a conta desta sessão: não há campo de usuário na requisição.
            case DEFINIR_APARENCIA -> {
                if (preferencias == null) {
                    throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                            "Este servidor não guarda preferências de aparência.");
                }
                Aparencia aparencia;
                try {
                    aparencia = Aparencia.exigir(r.texto("paleta"), r.texto("modo"), r.texto("fundo"));
                } catch (IllegalArgumentException e) {
                    throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, e.getMessage());
                }
                enviarLinha(ProtocoloJson.eventoAparencia(req, preferencias.definir(usuario, aparencia)));
            }

            // Pesquisa e fixações: acesso e papel verificados no ServicoMensagens.
            case PESQUISAR -> {
                long conversaId = r.id("conversaId");
                ServicoMensagens.Pagina pagina = exigirMensagens().pesquisar(usuario, conversaId, r.texto("texto"),
                        r.idOpcional("remetenteId"), r.dataOpcional("de"), r.dataOpcional("ate"),
                        r.idOpcional("antesDe"),
                        r.inteiro("limite", 1, FiltroPesquisa.LIMITE_MAXIMO, FiltroPesquisa.LIMITE_PADRAO));
                enviarLinha(ProtocoloJson.eventoResultados(req, conversaId, pagina.resultados().stream()
                        .map(e -> new ProtocoloJson.ResultadoJson(ProtocoloJson.MensagemJson.de(e.mensagem()),
                                e.trecho().texto(), e.trecho().inicio(), e.trecho().fim()))
                        .toList(), pagina.temMais()));
            }

            case LISTAR_FIXADAS -> {
                long conversaId = r.id("conversaId");
                enviarLinha(ProtocoloJson.eventoFixadas(req, conversaId,
                        paraJson(exigirMensagens().fixadas(usuario, conversaId))));
            }

            case FIXAR, DESAFIXAR -> {
                long conversaId = r.id("conversaId");
                long mensagemId = r.id("mensagemId");
                List<MensagemFixada> lista = r.tipo() == ProtocoloJson.TipoRequisicao.FIXAR
                        ? exigirMensagens().fixar(usuario, conversaId, mensagemId)
                        : exigirMensagens().desafixar(usuario, conversaId, mensagemId);
                enviarLinha(ProtocoloJson.eventoFixadas(req, conversaId, paraJson(lista)));
            }
        }
    }

    private ServicoMensagens exigirMensagens() throws ConversaException {
        if (servicoMensagens == null) {
            throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                    "Este servidor não oferece pesquisa nem mensagens fixadas.");
        }
        return servicoMensagens;
    }

    private static List<ProtocoloJson.FixadaJson> paraJson(List<MensagemFixada> fixadas) {
        return fixadas.stream()
                .map(f -> new ProtocoloJson.FixadaJson(ProtocoloJson.MensagemJson.de(f.mensagem()), f.fixadaPor(),
                        f.fixadaEm().toString()))
                .toList();
    }

    @Override
    public synchronized void fixadasAlteradas(long conversaId, List<MensagemFixada> fixadas) throws IOException {
        if (sessaoJson && historicoEnviado) {
            enviarLinha(ProtocoloJson.eventoFixadas(null, conversaId, paraJson(fixadas)));
        }
    }

    private static ProtocoloJson.UsuarioJson paraJson(PerfilUsuario perfil) {
        return new ProtocoloJson.UsuarioJson(perfil.getId(), perfil.getApelido(), perfil.getAvatarId(), true);
    }

    @Override
    public synchronized void perfilAtualizado(PerfilUsuario perfil) throws IOException {
        if (sessaoJson && historicoEnviado) {
            enviarLinha(ProtocoloJson.eventoPerfil(null, paraJson(perfil)));
        }
    }

    @Override
    public synchronized void estadoIA(long conversaId, String estado, String mensagem) throws IOException {
        if (sessaoJson && historicoEnviado) {
            enviarLinha(ProtocoloJson.eventoEstadoIA(conversaId, estado, mensagem));
        }
    }

    private static ProtocoloJson.ConversaJson paraJson(ResumoConversa resumo) {
        return ProtocoloJson.ConversaJson.de(resumo.conversa(), resumo.ultima(), resumo.naoLidas());
    }

    @Override
    public Usuario getUsuario() {
        return usuario;
    }
}
