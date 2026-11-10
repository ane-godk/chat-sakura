package br.unicesumar.chat.client;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Estado das conversas do cliente e ponte entre a rede e a interface.
 *
 * Recebe os eventos da {@link ConexaoServidor} (threads de rede) e os
 * repassa à {@link Visao} sempre pela EDT. Nesta etapa existe somente o
 * Chat Geral; o mapa de conversas prepara a futura barra lateral.
 *
 * Os métodos públicos para a interface devem ser chamados na EDT.
 */
final class ControladorConversas {

    static final String ID_GERAL = "geral";
    static final String TITULO_GERAL = "Chat Geral";

    enum EstadoConexao {
        DESCONECTADO,
        CONECTANDO,
        CONECTADO
    }

    /** Parte visual coordenada pelo controlador; chamada somente na EDT. */
    interface Visao {

        /** Uma nova sessão começa: a conversa indicada é esvaziada. */
        void reiniciarConversa(String idConversa, String usuarioAtual);

        void exibirLinha(String idConversa, String linha);

        /** {@code true} após o separador de fim do histórico da sessão. */
        boolean historicoConcluido(String idConversa);

        void estadoAlterado(EstadoConexao estado);

        void mensagemEnviada();

        /** O servidor confirmou a conta; o apelido passa a ser o nome exibido. */
        void usuarioIdentificado(String apelido);

        /** Cadastro ou login recusado (mensagem para o usuário). */
        void autenticacaoRecusada(String mensagem);
    }

    /** Conversa do cliente; acessada somente na EDT. */
    static final class Conversa {

        private final String id;
        private final String titulo;
        private final List<String> linhas = new ArrayList<>();

        Conversa(String id, String titulo) {
            this.id = Objects.requireNonNull(id);
            this.titulo = Objects.requireNonNull(titulo);
        }

        String id() {
            return id;
        }

        String titulo() {
            return titulo;
        }

        List<String> linhas() {
            return Collections.unmodifiableList(linhas);
        }

        /** Texto puro da conversa (base da exportação TXT). */
        String texto() {
            StringBuilder texto = new StringBuilder();
            for (String linha : linhas) {
                texto.append(linha).append('\n');
            }
            return texto.toString();
        }
    }

    private final Visao visao;
    private final NotificacoesSonoras notificacoes;
    private final Executor edt;
    private final ConexaoServidor conexao;

    private final Map<String, Conversa> conversas = new LinkedHashMap<>();
    private final String idConversaAtiva = ID_GERAL;

    // Escrito na EDT antes de iniciar a conexão; lido na EDT.
    private volatile String nomeUsuario = "";

    ControladorConversas(Visao visao, NotificacoesSonoras notificacoes) {
        this(visao, notificacoes, SwingUtilities::invokeLater, ConexaoServidor::new);
    }

    /**
     * @param edt     executor que roda na EDT (substituível nos testes)
     * @param fabrica cria a conexão a partir do ouvinte do controlador
     */
    ControladorConversas(
            Visao visao,
            NotificacoesSonoras notificacoes,
            Executor edt,
            Function<ConexaoServidor.Ouvinte, ConexaoServidor> fabrica
    ) {
        this.visao = Objects.requireNonNull(visao, "Visão obrigatória.");
        this.notificacoes = Objects.requireNonNull(notificacoes, "Notificações obrigatórias.");
        this.edt = Objects.requireNonNull(edt, "Executor da EDT obrigatório.");
        this.conexao = Objects.requireNonNull(fabrica, "Fábrica obrigatória.")
                .apply(new EventosConexao());

        conversas.put(ID_GERAL, new Conversa(ID_GERAL, TITULO_GERAL));
    }

    /**
     * Inicia a conexão sem bloquear quem chama.
     *
     * @return {@code false} se já existe uma sessão em andamento
     */
    boolean conectar(String endereco, int porta, String nome) {
        return conectar(endereco, porta, new ConexaoServidor.Visitante(nome));
    }

    /**
     * Inicia a conexão (visitante, login ou cadastro) sem bloquear quem
     * chama. Com conta, o nome exibido só é conhecido após a confirmação
     * do servidor; até lá vale o login ou o apelido informado.
     */
    boolean conectar(String endereco, int porta, ConexaoServidor.PedidoEntrada pedido) {

        if (conexao.ocupada()) {
            pedido.limpar();
            return false;
        }

        String nome;

        if (pedido instanceof ConexaoServidor.Visitante visitante) {
            nome = visitante.nome();
        } else if (pedido instanceof ConexaoServidor.Cadastrar cadastro) {
            nome = cadastro.apelido();
        } else {
            nome = ((ConexaoServidor.Entrar) pedido).login();
        }

        nomeUsuario = nome;

        // Esvaziada antes de a conexão começar: nenhuma linha da nova
        // sessão pode chegar antes da limpeza.
        conversaAtiva().linhas.clear();
        visao.reiniciarConversa(idConversaAtiva, nome);

        boolean iniciada = conexao.iniciar(endereco, porta, pedido);

        visao.estadoAlterado(estado());

        return iniciada;
    }

    /** @return {@code false} se não há conexão confirmada */
    boolean enviar(String texto) {
        return conexao.enviar(texto);
    }

    void desconectar() {
        conexao.desconectar();
    }

    EstadoConexao estado() {

        if (conexao.conectado()) {
            return EstadoConexao.CONECTADO;
        }

        if (conexao.conectando()) {
            return EstadoConexao.CONECTANDO;
        }

        return EstadoConexao.DESCONECTADO;
    }

    String nomeUsuario() {
        return nomeUsuario;
    }

    Conversa conversaAtiva() {
        return conversas.get(idConversaAtiva);
    }

    List<Conversa> conversas() {
        return List.copyOf(conversas.values());
    }

    /** Recebe os eventos de rede e os leva à EDT na ordem de chegada. */
    private final class EventosConexao implements ConexaoServidor.Ouvinte {

        @Override
        public void linhaRecebida(String linha) {
            edt.execute(() -> {
                Conversa conversa = conversas.get(ID_GERAL);
                conversa.linhas.add(linha);
                visao.exibirLinha(conversa.id(), linha);

                // Som apenas para mensagens novas de outros participantes.
                notificacoes.linhaRecebida(
                        linha,
                        nomeUsuario,
                        visao.historicoConcluido(conversa.id())
                );
            });
        }

        @Override
        public void conexaoConfirmada() {
            notificacoes.conexaoConfirmada();
        }

        @Override
        public void mensagemEnviada() {
            edt.execute(visao::mensagemEnviada);
            notificacoes.mensagemEnviada();
        }

        @Override
        public void estadoAlterado() {
            edt.execute(() -> visao.estadoAlterado(estado()));
        }

        @Override
        public void sessaoEncerrada() {
            notificacoes.sessaoEncerrada();
        }

        @Override
        public void identidadeConfirmada(String apelido) {
            // Na fila da EDT antes da confirmação e do histórico.
            edt.execute(() -> {
                nomeUsuario = apelido;
                visao.usuarioIdentificado(apelido);
            });
        }

        @Override
        public void autenticacaoRecusada(String mensagem) {
            edt.execute(() -> visao.autenticacaoRecusada(mensagem));
        }
    }
}
