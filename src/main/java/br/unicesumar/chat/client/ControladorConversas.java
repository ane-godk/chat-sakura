package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa.Tipo;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.protocolo.ProtocoloJson;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Estado das conversas do cliente e ponte entre a rede e a interface.
 *
 * Recebe os eventos da {@link ConexaoServidor} (threads de rede) e os
 * repassa à {@link Visao} sempre pela EDT. Visitantes e clientes sem conta
 * usam só o Chat Geral (protocolo de texto); com conta, o protocolo v2 traz
 * conversas privadas e grupos.
 *
 * Cada conversa guarda as próprias linhas no formato de texto do chat, de
 * modo que balões, sons, exportação TXT e temas funcionam como antes.
 * Os métodos para a interface devem ser chamados na EDT.
 */
final class ControladorConversas {

    static final long ID_GERAL = br.unicesumar.chat.model.Conversa.ID_GERAL;
    static final String TITULO_GERAL = "Chat Geral";

    static final int HISTORICO_INICIAL = 50;

    enum EstadoConexao {
        DESCONECTADO,
        CONECTANDO,
        CONECTADO,
        /** Conexão perdida: aguardando o servidor voltar para pedir o login de novo. */
        RECONECTANDO
    }

    /** Item imutável da barra lateral ({@code avatarId} nulo para Geral e grupos). */
    record ItemLateral(long id, Tipo tipo, String titulo, String previa, int naoLidas,
                       String avatarId, boolean gerando) {

        ItemLateral(long id, Tipo tipo, String titulo, String previa, int naoLidas) {
            this(id, tipo, titulo, previa, naoLidas, null, false);
        }
    }

    /** Aviso exibido no início de toda conversa com personagem. */
    static String avisoIA(String nome) {
        return "SISTEMA: " + nome + " é um personagem fictício interpretado por IA (Qwen), não uma pessoa real.";
    }

    /** Parte visual coordenada pelo controlador; chamada somente na EDT. */
    interface Visao {

        /** Uma nova sessão começa: a conversa indicada é esvaziada. */
        void reiniciarConversa(long idConversa, String usuarioAtual);

        /** Nova linha na conversa ativa. */
        void exibirLinha(long idConversa, String linha);

        /** {@code true} após o separador de fim do histórico da conversa ativa. */
        boolean historicoConcluido(long idConversa);

        void estadoAlterado(EstadoConexao estado);

        void mensagemEnviada();

        /** O servidor confirmou a conta; o apelido passa a ser o nome exibido. */
        void usuarioIdentificado(String apelido);

        /** Cadastro ou login recusado (mensagem para o usuário). */
        void autenticacaoRecusada(String mensagem);

        /** A conversa ativa mudou ou foi recarregada: substituir o conteúdo. */
        default void exibirConversa(Conversa conversa) {
        }

        /** Barra lateral (lista, prévias e não lidas). */
        default void conversasAtualizadas(List<ItemLateral> itens, long idAtiva) {
        }

        /** Operação de conversa recusada pelo servidor. */
        default void erroConversa(String mensagem) {
        }

        /** Perfil próprio (apelido e avatar) confirmado ou alterado. */
        default void perfilAtualizado(String apelido, String avatarId) {
        }

        /** A IA começou ou terminou de gerar uma resposta na conversa. */
        default void estadoIA(long idConversa, boolean gerando, String descricao) {
        }

        /**
         * Reconexão em andamento: {@code tentativa} falhas até agora (0 no
         * início) e próxima tentativa em {@code proximaEmMs} (0 se ainda não
         * agendada).
         */
        default void reconexaoEmAndamento(int tentativa, long proximaEmMs) {
        }

        /**
         * O servidor voltou. A senha não fica guardada: a interface pede o
         * login de novo e chama {@code conectar}, ou {@code cancelarReconexao}.
         */
        default void reconexaoDisponivel(String endereco, int porta, String login) {
        }

        /** A reconexão terminou sem novo login (cancelada pelo usuário). */
        default void reconexaoEncerrada() {
        }
    }

    /** Conversa do cliente; acessada somente na EDT. */
    static final class Conversa {

        private record Entrada(Long mensagemId, String linha) {
        }

        private final long id;
        private Tipo tipo;
        private String titulo;
        private final List<Entrada> entradas = new ArrayList<>();
        private final Set<Long> mensagens = new HashSet<>();
        private boolean historicoCarregado;
        private boolean historicoSolicitado;
        private int naoLidas;
        private String previa = "";
        private long ultimaId;
        private long idPrevia;
        private String avatarId;
        private boolean gerando;

        Conversa(long id, Tipo tipo, String titulo) {
            this.id = id;
            this.tipo = Objects.requireNonNull(tipo);
            this.titulo = Objects.requireNonNull(titulo);
        }

        long id() {
            return id;
        }

        Tipo tipo() {
            return tipo;
        }

        String titulo() {
            return titulo;
        }

        int naoLidas() {
            return naoLidas;
        }

        boolean gerando() {
            return gerando;
        }

        List<String> linhas() {
            return entradas.stream().map(Entrada::linha).toList();
        }

        /** Texto puro da conversa (base da exportação TXT). */
        String texto() {
            StringBuilder texto = new StringBuilder();
            for (Entrada entrada : entradas) {
                texto.append(entrada.linha()).append('\n');
            }
            return texto.toString();
        }

        private void limpar() {
            entradas.clear();
            mensagens.clear();
            historicoCarregado = false;
            historicoSolicitado = false;
            naoLidas = 0;
            previa = "";
            ultimaId = 0;
            idPrevia = 0;
        }

        /** @return {@code false} se a mensagem já estava na conversa */
        private boolean adicionar(Mensagem mensagem) {
            if (!mensagens.add(mensagem.getId())) {
                return false;
            }
            entradas.add(new Entrada(mensagem.getId(), mensagem.formatar()));
            registrarUltima(mensagem);
            return true;
        }

        private void registrarUltima(Mensagem mensagem) {
            if (mensagem.getId() > ultimaId) {
                ultimaId = mensagem.getId();
            }
            // Prévia: a mensagem mais recente que não seja aviso de presença.
            if (!mensagem.isAvisoDePresenca() && mensagem.getId() > idPrevia) {
                idPrevia = mensagem.getId();
                previa = mensagem.getRemetente().getNome() + ": " + mensagem.getConteudo();
            }
        }

        /**
         * Histórico: linhas locais antes, depois o histórico entre os
         * separadores e, por fim, mensagens recebidas ao vivo ainda não
         * incluídas (sem duplicar).
         */
        private void aplicarHistorico(List<Mensagem> historico) {

            Set<Long> doHistorico = new HashSet<>();
            historico.forEach(m -> doHistorico.add(m.getId()));

            List<Entrada> locais = new ArrayList<>();
            List<Entrada> aoVivo = new ArrayList<>();
            for (Entrada entrada : entradas) {
                if (entrada.mensagemId() == null) {
                    locais.add(entrada);
                } else if (!doHistorico.contains(entrada.mensagemId())) {
                    aoVivo.add(entrada);
                }
            }
            aoVivo.sort(Comparator.comparing(Entrada::mensagemId));

            entradas.clear();
            entradas.addAll(locais);
            entradas.add(new Entrada(null, FormatoLinhaChat.INICIO_HISTORICO));
            for (Mensagem mensagem : historico) {
                entradas.add(new Entrada(mensagem.getId(), mensagem.formatar()));
                mensagens.add(mensagem.getId());
                registrarUltima(mensagem);
            }
            entradas.add(new Entrada(null, FormatoLinhaChat.FIM_HISTORICO));
            entradas.addAll(aoVivo);

            historicoCarregado = true;
        }
    }

    private final Visao visao;
    private final NotificacoesSonoras notificacoes;
    private final Executor edt;
    private final ConexaoServidor conexao;

    // Estado acessado somente na EDT.
    private final Map<Long, Conversa> conversas = new LinkedHashMap<>();
    private final Map<Long, Consumer<ProtocoloJson.Evento>> pendentes = new HashMap<>();
    // Ação quando o servidor responde ERRO a um pedido (correlacionado pelo req).
    private final Map<Long, Runnable> errosPendentes = new HashMap<>();
    private long idConversaAtiva = ID_GERAL;

    // Reconexão (EDT). Guarda endereço, porta e login, nunca a senha.
    private final ReconexaoAutomatica reconexao;
    private String ultimoEndereco;
    private int ultimaPorta;
    private String ultimoLogin;
    private boolean sessaoConfirmada;
    private boolean saidaVoluntaria;
    private boolean sessaoSubstituida;
    private long idParaRestaurar = -1;
    private boolean modoContas;
    private boolean estadoInicialRecebido;
    private long proximaRequisicao = 1;
    private long idProprio;
    private String avatarProprio = br.unicesumar.chat.model.CatalogoAvatares.PADRAO;

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
        this(visao, notificacoes, edt, fabrica, ReconexaoAutomatica::new);
    }

    /** @param fabricaReconexao cria a reconexão (intervalos substituíveis nos testes) */
    ControladorConversas(
            Visao visao,
            NotificacoesSonoras notificacoes,
            Executor edt,
            Function<ConexaoServidor.Ouvinte, ConexaoServidor> fabrica,
            Function<ReconexaoAutomatica.Ouvinte, ReconexaoAutomatica> fabricaReconexao
    ) {
        this.visao = Objects.requireNonNull(visao, "Visão obrigatória.");
        this.notificacoes = Objects.requireNonNull(notificacoes, "Notificações obrigatórias.");
        this.edt = Objects.requireNonNull(edt, "Executor da EDT obrigatório.");
        this.conexao = Objects.requireNonNull(fabrica, "Fábrica obrigatória.")
                .apply(new EventosConexao());
        this.reconexao = Objects.requireNonNull(fabricaReconexao, "Fábrica de reconexão obrigatória.")
                .apply(new EventosReconexao());

        conversas.put(ID_GERAL, new Conversa(ID_GERAL, Tipo.GERAL, TITULO_GERAL));
    }

    // ------------------------------------------------------------------
    // Conexão
    // ------------------------------------------------------------------

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
        String login = null;

        if (pedido instanceof ConexaoServidor.Visitante visitante) {
            nome = visitante.nome();
        } else if (pedido instanceof ConexaoServidor.Cadastrar cadastro) {
            nome = cadastro.apelido();
            login = cadastro.login();
        } else {
            nome = ((ConexaoServidor.Entrar) pedido).login();
            login = nome;
        }

        nomeUsuario = nome;

        // Para uma eventual reconexão: endereço, porta e login, sem senha.
        // Uma reconexão em andamento termina com este novo pedido.
        reconexao.cancelar();
        ultimoEndereco = endereco;
        ultimaPorta = porta;
        if (login == null || !login.equals(ultimoLogin)) {
            idParaRestaurar = -1;
        }
        ultimoLogin = login;
        sessaoConfirmada = false;
        saidaVoluntaria = false;
        sessaoSubstituida = false;

        // Nova sessão: só o Chat Geral, vazio, antes de qualquer linha chegar.
        Conversa geral = conversas.get(ID_GERAL);
        conversas.clear();
        geral.limpar();
        conversas.put(ID_GERAL, geral);
        idConversaAtiva = ID_GERAL;
        pendentes.clear();
        errosPendentes.clear();
        modoContas = false;
        estadoInicialRecebido = false;

        visao.reiniciarConversa(ID_GERAL, nome);
        visao.conversasAtualizadas(itens(), idConversaAtiva);

        boolean iniciada = conexao.iniciar(endereco, porta, pedido);

        visao.estadoAlterado(estado());

        return iniciada;
    }

    /** Envia para a conversa ativa. @return {@code false} se não há conexão confirmada */
    boolean enviar(String texto) {

        if (!modoContas) {
            return conexao.enviar(texto);
        }

        return conexao.enviar(ProtocoloJson.requisicao(
                ProtocoloJson.TipoRequisicao.ENVIAR, proximaRequisicao++,
                "conversaId", idConversaAtiva, "texto", texto));
    }

    /** "Sair": encerra a sessão e cancela qualquer reconexão (sem reconectar depois). */
    void desconectar() {
        saidaVoluntaria = true;
        idParaRestaurar = -1;
        boolean reconectando = reconexao.ativa();
        reconexao.cancelar();
        conexao.desconectar();
        if (reconectando) {
            visao.reconexaoEncerrada();
            visao.estadoAlterado(estado());
        }
    }

    /** Desiste de esperar o servidor (ou do novo login pedido na reconexão). */
    void cancelarReconexao() {
        reconexao.cancelar();
        idParaRestaurar = -1;
        visao.reconexaoEncerrada();
        visao.estadoAlterado(estado());
    }

    /** Libera a thread de reconexão (fechamento da janela). */
    void encerrar() {
        desconectar();
        reconexao.fechar();
    }

    EstadoConexao estado() {

        if (conexao.conectado()) {
            return EstadoConexao.CONECTADO;
        }

        if (conexao.conectando()) {
            return EstadoConexao.CONECTANDO;
        }

        if (reconexao.ativa()) {
            return EstadoConexao.RECONECTANDO;
        }

        return EstadoConexao.DESCONECTADO;
    }

    /**
     * Fim de sessão (na EDT). Uma sessão com conta que caiu sem o usuário
     * pedir — e que não foi substituída por outro login — passa a esperar o
     * servidor voltar. Envios incertos não são repetidos.
     */
    private void aoEncerrarSessao() {

        boolean reconectar = modoContas && sessaoConfirmada && !saidaVoluntaria
                && !sessaoSubstituida && ultimoLogin != null;
        sessaoConfirmada = false;

        if (!reconectar) {
            return;
        }

        idParaRestaurar = idConversaAtiva;

        if (reconexao.iniciar(ultimoEndereco, ultimaPorta)) {
            adicionarLocal("SISTEMA: Conexão com o servidor perdida. Tentando reconectar...");
            visao.reconexaoEmAndamento(0, 0);
            visao.estadoAlterado(estado());
        }
    }

    /** Recebe os resultados da reconexão e os leva à EDT. */
    private final class EventosReconexao implements ReconexaoAutomatica.Ouvinte {

        @Override
        public void tentativaFalhou(int numero, long proximaEmMs) {
            edt.execute(() -> {
                visao.reconexaoEmAndamento(numero, proximaEmMs);
                visao.estadoAlterado(estado());
            });
        }

        @Override
        public void servidorDisponivel() {
            edt.execute(() -> {
                if (saidaVoluntaria || ultimoLogin == null) {
                    return;
                }
                visao.estadoAlterado(estado());
                visao.reconexaoDisponivel(ultimoEndereco, ultimaPorta, ultimoLogin);
            });
        }
    }

    String nomeUsuario() {
        return nomeUsuario;
    }

    /** Sessão com conta (conversas privadas e grupos disponíveis). */
    boolean modoContas() {
        return modoContas && conexao.conectado();
    }

    // ------------------------------------------------------------------
    // Conversas
    // ------------------------------------------------------------------

    Conversa conversaAtiva() {
        return conversas.get(idConversaAtiva);
    }

    List<Conversa> conversas() {
        return List.copyOf(conversas.values());
    }

    /** Torna a conversa ativa; carrega o histórico sob demanda (sem bloquear). */
    void selecionar(long id) {

        Conversa conversa = conversas.get(id);

        if (conversa == null) {
            return;
        }

        idConversaAtiva = id;
        conversa.naoLidas = 0;

        visao.exibirConversa(conversa);

        if (modoContas && !conversa.historicoCarregado && !conversa.historicoSolicitado) {
            conversa.historicoSolicitado = true;
            // Erro (ou pedido não enviado) libera nova tentativa ao abrir a
            // conversa de novo; a resposta é aplicada pela conversaId dela.
            boolean pedido = requisitarComFalha(ProtocoloJson.TipoRequisicao.HISTORICO, this::tratarHistorico,
                    () -> liberarNovaTentativa(id),
                    "conversaId", id, "limite", HISTORICO_INICIAL);
            if (!pedido) {
                liberarNovaTentativa(id);
            }
        } else {
            marcarLida(conversa);
        }

        visao.conversasAtualizadas(itens(), idConversaAtiva);
    }

    /** Lista de usuários cadastrados (exceto o próprio), entregue na EDT. */
    boolean solicitarUsuarios(Consumer<List<ProtocoloJson.UsuarioJson>> aoReceber) {
        return requisitar(ProtocoloJson.TipoRequisicao.LISTAR_USUARIOS,
                evento -> aoReceber.accept(evento.usuarios()));
    }

    /** Abre (ou reutiliza) a conversa privada e a seleciona. */
    boolean abrirPrivada(long usuarioId) {
        return requisitar(ProtocoloJson.TipoRequisicao.ABRIR_PRIVADA, this::abrirResposta,
                "usuarioId", usuarioId);
    }

    /** Cria o grupo e o seleciona. */
    boolean criarGrupo(String nome, List<Long> participantes) {
        return requisitar(ProtocoloJson.TipoRequisicao.CRIAR_GRUPO, this::abrirResposta,
                "nome", nome, "participantes", participantes);
    }

    /** Cria um personagem de IA e abre a conversa com ele. */
    boolean criarPersonagem(String nome, String personalidade, String contexto) {
        return requisitar(ProtocoloJson.TipoRequisicao.CRIAR_PERSONAGEM, this::abrirResposta,
                "nome", nome, "personalidade", personalidade, "contexto", contexto);
    }

    /** Memória do personagem como o servidor informou ({@code atualizadaEm} nulo: nunca resumida). */
    record MemoriaInfo(long conversaId, String resumo, String atualizadaEm) {
    }

    /** Pede a memória do personagem da conversa ativa (somente conversas de IA). */
    boolean verMemoria(Consumer<MemoriaInfo> aoReceber) {
        return pedirMemoria(ProtocoloJson.TipoRequisicao.MEMORIA_VER, aoReceber);
    }

    /** Apaga a memória do personagem da conversa ativa; o histórico continua. */
    boolean apagarMemoria(Consumer<MemoriaInfo> aoApagar) {
        return pedirMemoria(ProtocoloJson.TipoRequisicao.MEMORIA_APAGAR, aoApagar);
    }

    private boolean pedirMemoria(ProtocoloJson.TipoRequisicao tipo, Consumer<MemoriaInfo> aoReceber) {
        Conversa ativa = conversaAtiva();
        if (ativa == null || ativa.tipo != Tipo.IA) {
            return false;
        }
        return requisitar(tipo, evento -> aoReceber.accept(new MemoriaInfo(evento.conversaId(),
                evento.resumoMemoria(), evento.memoriaAtualizadaEm())), "conversaId", ativa.id);
    }

    /** Troca o próprio avatar (o servidor valida o identificador). */
    boolean definirAvatar(String avatarId) {
        return requisitar(ProtocoloJson.TipoRequisicao.DEFINIR_AVATAR, this::aplicarPerfil, "avatarId", avatarId);
    }

    String avatarProprio() {
        return avatarProprio;
    }

    private void aplicarPerfil(ProtocoloJson.Evento evento) {

        ProtocoloJson.UsuarioJson perfil = evento.perfil();

        if (perfil.id() == idProprio) {
            avatarProprio = br.unicesumar.chat.model.CatalogoAvatares.normalizar(perfil.avatar());
            visao.perfilAtualizado(nomeUsuario, avatarProprio);
        } else {
            // Avatar de outra pessoa: atualiza as conversas em que ela aparece.
            requisitar(ProtocoloJson.TipoRequisicao.LISTAR_CONVERSAS, e -> {
                e.conversas().forEach(this::atualizar);
                visao.conversasAtualizadas(itens(), idConversaAtiva);
            });
        }
    }

    private void tratarEstadoIA(ProtocoloJson.Evento evento) {

        Conversa conversa = conversas.get(evento.conversaId());

        if (conversa == null) {
            return;
        }

        String estado = evento.estado();
        conversa.gerando = "GERANDO".equals(estado);

        if ("ERRO".equals(estado)) {
            String linha = "SISTEMA: Sakura IA indisponível: " + evento.mensagemErro();
            conversa.entradas.add(new Conversa.Entrada(null, linha));
            if (conversa.id == idConversaAtiva) {
                visao.exibirLinha(conversa.id, linha);
            }
        }

        if ("TRUNCADA".equals(estado)) {
            // A resposta foi gravada e marcada com "[…]"; o aviso explica o corte.
            visao.erroConversa(conversa.titulo + ": " + evento.mensagemErro());
        }

        visao.estadoIA(conversa.id, conversa.gerando, evento.mensagemErro());
    }

    private void abrirResposta(ProtocoloJson.Evento evento) {
        Conversa conversa = atualizar(evento.conversa());
        selecionar(conversa.id());
    }

    private boolean requisitar(ProtocoloJson.TipoRequisicao tipo,
                               Consumer<ProtocoloJson.Evento> aoResponder,
                               Object... campos) {
        return requisitarComFalha(tipo, aoResponder, null, campos);
    }

    /** @param aoFalhar executado se o servidor responder ERRO a este pedido (mesmo req) */
    private boolean requisitarComFalha(ProtocoloJson.TipoRequisicao tipo,
                                       Consumer<ProtocoloJson.Evento> aoResponder,
                                       Runnable aoFalhar,
                                       Object... campos) {

        if (!modoContas()) {
            return false;
        }

        long req = proximaRequisicao++;
        pendentes.put(req, aoResponder);
        if (aoFalhar != null) {
            errosPendentes.put(req, aoFalhar);
        }
        boolean enviado = conexao.enviarComando(ProtocoloJson.requisicao(tipo, req, campos));
        if (!enviado) {
            pendentes.remove(req);
            errosPendentes.remove(req);
        }
        return enviado;
    }

    /**
     * Depois de um novo login (reconexão): volta à conversa que estava
     * aberta, se ela ainda existir; o histórico é pedido de novo.
     */
    private void restaurarConversaAtiva() {
        long id = idParaRestaurar;
        idParaRestaurar = -1;
        if (id > 0 && id != ID_GERAL && conversas.containsKey(id)) {
            selecionar(id);
        }
    }

    /** O histórico da conversa não veio: abrir a conversa de novo pede outra vez. */
    private void liberarNovaTentativa(long idConversa) {
        Conversa conversa = conversas.get(idConversa);
        if (conversa != null && !conversa.historicoCarregado) {
            conversa.historicoSolicitado = false;
        }
    }

    private void marcarLida(Conversa conversa) {
        if (modoContas() && conversa.id != ID_GERAL && conversa.ultimaId > 0) {
            conexao.enviarComando(ProtocoloJson.requisicao(ProtocoloJson.TipoRequisicao.MARCAR_LIDA, null,
                    "conversaId", conversa.id, "mensagemId", conversa.ultimaId));
        }
    }

    /** Itens da barra: Geral primeiro e as demais pela atividade mais recente. */
    List<ItemLateral> itens() {

        List<Conversa> demais = new ArrayList<>(conversas.values());
        demais.removeIf(c -> c.id == ID_GERAL);
        demais.sort(Comparator.comparingLong((Conversa c) -> c.ultimaId)
                .thenComparingLong(c -> c.id).reversed());

        List<ItemLateral> itens = new ArrayList<>();
        Conversa geral = conversas.get(ID_GERAL);
        itens.add(item(geral));
        demais.forEach(c -> itens.add(item(c)));
        return Collections.unmodifiableList(itens);
    }

    private static ItemLateral item(Conversa c) {
        return new ItemLateral(c.id, c.tipo, c.titulo, c.gerando ? "digitando…" : c.previa, c.naoLidas,
                c.avatarId, c.gerando);
    }

    // ------------------------------------------------------------------
    // Eventos do protocolo v2 (na EDT)
    // ------------------------------------------------------------------

    private void tratarEvento(ProtocoloJson.Evento evento) {

        Consumer<ProtocoloJson.Evento> resposta =
                evento.req() == null ? null : pendentes.remove(evento.req());
        Runnable falha = evento.req() == null ? null : errosPendentes.remove(evento.req());

        switch (evento.tipo()) {

            case CONVERSAS -> {
                evento.conversas().forEach(this::atualizar);
                if (!estadoInicialRecebido) {
                    estadoInicialRecebido = true;
                    adicionarLocal(ProtocoloSakuraLocal.confirmacao(nomeUsuario));
                    restaurarConversaAtiva();
                }
            }

            case CONVERSA -> {
                if (resposta != null) {
                    resposta.accept(evento);
                    return;
                }
                atualizar(evento.conversa());
            }

            case HISTORICO -> {
                if (resposta != null) {
                    resposta.accept(evento);
                } else {
                    tratarHistorico(evento);
                }
                return;
            }

            case MENSAGEM -> receber(evento.mensagem());

            case USUARIOS -> {
                if (resposta != null) {
                    resposta.accept(evento);
                }
                return;
            }

            case ERRO -> {
                if (falha != null) {
                    falha.run();
                }
                if ("SESSAO_SUBSTITUIDA".equals(evento.codigo())) {
                    // Outro login da mesma conta assumiu: não reconectar sozinho.
                    sessaoSubstituida = true;
                }
                visao.erroConversa(evento.mensagemErro());
                return;
            }

            case PERFIL -> {
                if (resposta != null) {
                    resposta.accept(evento);
                } else {
                    aplicarPerfil(evento);
                }
            }

            case IA_STATUS -> tratarEstadoIA(evento);

            case MEMORIA -> {
                if (resposta != null) {
                    resposta.accept(evento);
                }
                return;
            }

            case PING -> {
                // Respondido pela conexão; nunca chega aqui.
            }
        }

        visao.conversasAtualizadas(itens(), idConversaAtiva);
    }

    private void tratarHistorico(ProtocoloJson.Evento evento) {

        Conversa conversa = conversas.get(evento.conversaId());

        if (conversa == null) {
            return;
        }

        List<Mensagem> historico = new ArrayList<>();
        for (ProtocoloJson.MensagemJson json : evento.mensagens()) {
            try {
                historico.add(json.paraMensagem());
            } catch (RuntimeException e) {
                System.err.println("Mensagem do histórico ignorada: " + e.getMessage());
            }
        }

        if (conversa.historicoCarregado) {
            // Resposta repetida ou atrasada: só o que ainda falta, sem duplicar.
            historico.forEach(conversa::adicionar);
        } else {
            conversa.aplicarHistorico(historico);
        }

        if (conversa.id == idConversaAtiva) {
            visao.exibirConversa(conversa);
            marcarLida(conversa);
        }

        visao.conversasAtualizadas(itens(), idConversaAtiva);
    }

    /** Cria ou atualiza a conversa a partir do servidor. */
    private Conversa atualizar(ProtocoloJson.ConversaJson json) {

        Tipo tipo = Tipo.valueOf(json.tipo());
        Conversa conversa = conversas.computeIfAbsent(json.id(), id -> {
            Conversa nova = new Conversa(id, tipo, titulo(json));
            if (tipo == Tipo.IA) {
                nova.entradas.add(new Conversa.Entrada(null, avisoIA(titulo(json))));
            }
            return nova;
        });
        conversa.tipo = tipo;
        conversa.titulo = titulo(json);
        conversa.avatarId = avatarDe(json);

        if (json.ultima() != null) {
            try {
                conversa.registrarUltima(json.ultima().paraMensagem());
            } catch (RuntimeException e) {
                // Prévia ausente: a conversa continua utilizável.
            }
        }

        if (conversa.id != ID_GERAL && conversa.id != idConversaAtiva) {
            conversa.naoLidas = Math.max(0, json.naoLidas());
        }

        return conversa;
    }

    /** Privada: avatar da outra pessoa; IA: marcador do personagem; demais: nenhum. */
    private String avatarDe(ProtocoloJson.ConversaJson json) {

        if ("IA".equals(json.tipo())) {
            return AvatarSakura.IA;
        }

        if ("PRIVADA".equals(json.tipo()) && json.participantes() != null) {
            return json.participantes().stream()
                    .filter(p -> !p.apelido().equalsIgnoreCase(nomeUsuario))
                    .map(ProtocoloJson.ParticipanteJson::avatar)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private String titulo(ProtocoloJson.ConversaJson json) {

        if (json.id() == ID_GERAL) {
            return TITULO_GERAL;
        }

        if ("PRIVADA".equals(json.tipo()) && json.participantes() != null) {
            return json.participantes().stream()
                    .map(ProtocoloJson.ParticipanteJson::apelido)
                    .filter(apelido -> !apelido.equalsIgnoreCase(nomeUsuario))
                    .findFirst()
                    .orElse("Conversa privada");
        }

        return json.nome() == null ? "Conversa" : json.nome();
    }

    private void receber(ProtocoloJson.MensagemJson json) {

        Mensagem mensagem;

        try {
            mensagem = json.paraMensagem();
        } catch (RuntimeException e) {
            System.err.println("Mensagem ignorada: " + e.getMessage());
            return;
        }

        Conversa conversa = conversas.get(mensagem.getConversaId());

        if (conversa == null) {
            // Conversa nova (criada por outra pessoa): busca os dados completos.
            conversa = new Conversa(mensagem.getConversaId(), Tipo.PRIVADA, "Nova conversa");
            conversas.put(conversa.id, conversa);
            requisitar(ProtocoloJson.TipoRequisicao.LISTAR_CONVERSAS,
                    evento -> {
                        evento.conversas().forEach(this::atualizar);
                        visao.conversasAtualizadas(itens(), idConversaAtiva);
                    });
        }

        if (!conversa.adicionar(mensagem)) {
            return;
        }

        String linha = mensagem.formatar();
        boolean ativa = conversa.id == idConversaAtiva;
        boolean propria = mensagem.getRemetente().getNome().equalsIgnoreCase(nomeUsuario);

        if (ativa) {
            visao.exibirLinha(conversa.id, linha);
            marcarLida(conversa);
        } else if (!propria && !mensagem.isAvisoDePresenca()) {
            conversa.naoLidas++;
        }

        // Som só para mensagens novas de outros participantes.
        notificacoes.linhaRecebida(linha, nomeUsuario, !ativa || visao.historicoConcluido(conversa.id));
    }

    /** Linha local (avisos e erros) no Chat Geral. */
    private void adicionarLocal(String linha) {

        Conversa geral = conversas.get(ID_GERAL);
        geral.entradas.add(new Conversa.Entrada(null, linha));

        if (idConversaAtiva == ID_GERAL) {
            visao.exibirLinha(ID_GERAL, linha);
        }
    }

    /** Recebe os eventos de rede e os leva à EDT na ordem de chegada. */
    private final class EventosConexao implements ConexaoServidor.Ouvinte {

        @Override
        public void linhaRecebida(String linha) {
            edt.execute(() -> {
                Conversa geral = conversas.get(ID_GERAL);
                geral.entradas.add(new Conversa.Entrada(null, linha));

                FormatoLinhaChat.Linha analisada = FormatoLinhaChat.analisar(linha);
                if (analisada.tipo() == FormatoLinhaChat.Tipo.MENSAGEM) {
                    geral.previa = analisada.remetente() + ": " + analisada.conteudo();
                }

                if (idConversaAtiva == ID_GERAL) {
                    visao.exibirLinha(ID_GERAL, linha);
                }

                // Som apenas para mensagens novas de outros participantes.
                notificacoes.linhaRecebida(
                        linha,
                        nomeUsuario,
                        visao.historicoConcluido(ID_GERAL)
                );

                visao.conversasAtualizadas(itens(), idConversaAtiva);
            });
        }

        @Override
        public void conexaoConfirmada() {
            edt.execute(() -> sessaoConfirmada = true);
            notificacoes.conexaoConfirmada();
        }

        @Override
        public void enviosDescartados(int quantidade) {
            edt.execute(() -> visao.erroConversa(quantidade == 1
                    ? "Uma mensagem pode não ter sido enviada antes da queda. Confira e envie de novo se precisar."
                    : quantidade + " mensagens podem não ter sido enviadas antes da queda."
                            + " Confira e envie de novo se precisar."));
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
            edt.execute(ControladorConversas.this::aoEncerrarSessao);
        }

        @Override
        public void identidadeConfirmada(String apelido) {
            // Na fila da EDT antes do estado inicial e do histórico.
            edt.execute(() -> {
                nomeUsuario = apelido;
                modoContas = true;
                visao.usuarioIdentificado(apelido);
            });
        }

        @Override
        public void autenticacaoRecusada(String mensagem) {
            edt.execute(() -> visao.autenticacaoRecusada(mensagem));
        }

        @Override
        public void perfilRecebido(br.unicesumar.chat.model.PerfilUsuario perfil) {
            edt.execute(() -> {
                idProprio = perfil.getId();
                avatarProprio = perfil.getAvatarId();
                visao.perfilAtualizado(perfil.getApelido(), avatarProprio);
            });
        }

        @Override
        public void eventoRecebido(ProtocoloJson.Evento evento) {
            edt.execute(() -> {
                try {
                    tratarEvento(evento);
                } catch (RuntimeException e) {
                    System.err.println("Evento do servidor ignorado: " + e.getMessage());
                }
            });
        }
    }

    /** Linha de confirmação exibida localmente no protocolo v2. */
    private static final class ProtocoloSakuraLocal {
        static String confirmacao(String apelido) {
            return br.unicesumar.chat.protocolo.ProtocoloSakura.PREFIXO_CONFIRMACAO + apelido;
        }
    }
}
