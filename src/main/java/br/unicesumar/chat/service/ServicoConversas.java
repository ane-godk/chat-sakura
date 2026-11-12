package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversas privadas e grupos.
 *
 * Regras de segurança:
 * <ul>
 *   <li>o usuário vem sempre da sessão autenticada (nunca do cliente);</li>
 *   <li>toda operação fora da Geral exige conta e participação;</li>
 *   <li>conversa inexistente e conversa sem acesso têm a mesma resposta;</li>
 *   <li>mensagens são entregues somente aos participantes autenticados.</li>
 * </ul>
 *
 * Ordem: cada conversa tem o próprio bloqueio (gravação e entrega juntas);
 * conversas independentes não se bloqueiam. A Geral usa o bloqueio do
 * {@link ChatService}, que também garante o histórico atômico na conexão.
 */
public final class ServicoConversas {

    public static final int HISTORICO_MAXIMO = 100;
    public static final int HISTORICO_PADRAO = 50;
    static final int USUARIOS_MAXIMOS = 500;

    static final String MENSAGEM_NAO_ENCONTRADA = "Conversa não encontrada.";

    private static final Conversa GERAL =
            new Conversa(Conversa.ID_GERAL, Conversa.Tipo.GERAL, "Chat Geral", List.of());

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final UsuarioRepository usuarios;
    private final ChatService chat;
    private final ClientRegistry registro;
    private final Clock relogio;

    private final ConcurrentHashMap<Long, Object> bloqueios = new ConcurrentHashMap<>();
    private final Object bloqueioCriacao = new Object();

    public ServicoConversas(
            ConversaRepository conversas,
            MensagemRepository mensagens,
            UsuarioRepository usuarios,
            ChatService chat,
            ClientRegistry registro,
            Clock relogio
    ) {
        this.conversas = Objects.requireNonNull(conversas);
        this.mensagens = Objects.requireNonNull(mensagens);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.chat = Objects.requireNonNull(chat);
        this.registro = Objects.requireNonNull(registro);
        this.relogio = Objects.requireNonNull(relogio);
    }

    /** Usuários cadastrados (exceto o próprio), com indicação de presença. */
    public record UsuarioPublico(long id, String apelido, String avatarId, boolean online) {
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public List<ResumoConversa> listar(Usuario usuario) throws ConversaException {

        long id = exigirConta(usuario);

        return protegido("listagem", () -> {
            List<ResumoConversa> lista = new ArrayList<>();
            lista.add(new ResumoConversa(GERAL, ultima(Conversa.ID_GERAL), 0));
            for (Conversa conversa : conversas.conversasDoUsuario(id)) {
                lista.add(resumo(conversa, id));
            }
            return lista;
        });
    }

    public List<UsuarioPublico> listarUsuarios(Usuario usuario) throws ConversaException {

        long id = exigirConta(usuario);

        return protegido("listagem de usuários", () -> {
            Set<Long> conectados = registro.contasConectadas();
            List<UsuarioPublico> lista = new ArrayList<>();
            for (PerfilUsuario perfil : usuarios.listar(USUARIOS_MAXIMOS)) {
                if (perfil.getId() != id) {
                    lista.add(new UsuarioPublico(perfil.getId(), perfil.getApelido(),
                            perfil.getAvatarId(), conectados.contains(perfil.getId())));
                }
            }
            return lista;
        });
    }

    public List<Mensagem> historico(Usuario usuario, long conversaId, Long antesDeId, int quantidade)
            throws ConversaException {

        if (quantidade < 1 || quantidade > HISTORICO_MAXIMO) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS,
                    "Quantidade de mensagens inválida.");
        }

        if (conversaId != Conversa.ID_GERAL) {
            exigirParticipacao(exigirConta(usuario), conversaId);
        }

        return protegido("histórico", () -> mensagens.buscarHistorico(conversaId, antesDeId, quantidade));
    }

    // ------------------------------------------------------------------
    // Criação
    // ------------------------------------------------------------------

    /** Abre (ou reutiliza) a conversa privada com outro usuário cadastrado. */
    public ResumoConversa abrirPrivada(Usuario usuario, long outroId) throws ConversaException {

        long id = exigirConta(usuario);

        if (outroId == id) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS,
                    "Escolha outra pessoa para conversar.");
        }

        exigirUsuario(outroId);

        boolean criada = false;
        long conversaId;

        synchronized (bloqueioCriacao) {
            Optional<Long> existente = protegido("conversa privada", () -> conversas.buscarPrivada(id, outroId));
            if (existente.isPresent()) {
                conversaId = existente.get();
            } else {
                conversaId = protegido("conversa privada",
                        () -> conversas.criarPrivada(id, outroId, LocalDateTime.now(relogio)));
                criada = true;
            }
        }

        if (criada) {
            avisarParticipantes(conversaId, Set.of(outroId));
        }

        return resumoDe(conversaId, id);
    }

    /** Cria um grupo: o criador como ADMIN e os convidados (cadastrados) como MEMBRO. */
    public ResumoConversa criarGrupo(Usuario usuario, String nome, List<Long> convidados)
            throws ConversaException {

        long id = exigirConta(usuario);
        String nomeTratado;

        try {
            nomeTratado = Conversa.normalizarNomeGrupo(nome);
        } catch (IllegalArgumentException e) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, e.getMessage());
        }

        Set<Long> membros = new LinkedHashSet<>(convidados);
        membros.remove(id);

        if (membros.isEmpty() || membros.size() >= Conversa.PARTICIPANTES_MAXIMOS) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS,
                    "Escolha de 1 a " + (Conversa.PARTICIPANTES_MAXIMOS - 1) + " participantes.");
        }

        for (long membro : membros) {
            exigirUsuario(membro);
        }

        long conversaId = protegido("grupo",
                () -> conversas.criarGrupo(nomeTratado, id, membros, LocalDateTime.now(relogio)));

        avisarParticipantes(conversaId, membros);

        return resumoDe(conversaId, id);
    }

    // ------------------------------------------------------------------
    // Mensagens
    // ------------------------------------------------------------------

    /** Grava e entrega a mensagem; o remetente é o usuário da sessão. */
    public Mensagem enviar(Usuario usuario, long conversaId, String texto) throws ConversaException {
        return enviar(usuario, conversaId, texto, null);
    }

    /**
     * @param origem sessão que pede o envio; se informada, precisa continuar
     *               registrada (verificado sob o bloqueio da conversa)
     * @throws SessaoEncerradaException se {@code origem} foi substituída ou encerrada
     */
    public Mensagem enviar(Usuario usuario, long conversaId, String texto, ConexaoCliente origem)
            throws ConversaException {

        Objects.requireNonNull(usuario, "Usuário obrigatório.");

        try {
            if (conversaId == Conversa.ID_GERAL) {
                return chat.enviarMensagem(usuario, texto, origem);
            }

            long id = exigirConta(usuario);
            Set<Long> participantes = protegido("envio", () -> conversas.participantes(conversaId));

            if (!participantes.contains(id)) {
                throw naoEncontrada();
            }

            Mensagem mensagem = new Mensagem(null, usuario, texto,
                    LocalDateTime.now(relogio), TipoMensagem.MENSAGEM, conversaId);

            synchronized (bloqueios.computeIfAbsent(conversaId, chave -> new Object())) {
                if (origem != null && !registro.contem(origem)) {
                    throw new SessaoEncerradaException();
                }
                Mensagem persistida = protegido("envio", () -> mensagens.salvar(mensagem));
                registro.transmitirPara(persistida, participantes);
                return persistida;
            }

        } catch (IllegalArgumentException e) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, e.getMessage());
        } catch (IllegalStateException e) {
            registrarErroInterno("envio", e);
            throw indisponivel();
        }
    }

    /** Registra a leitura até {@code mensagemId} (somente participantes; a Geral não conta). */
    public void marcarLida(Usuario usuario, long conversaId, long mensagemId) throws ConversaException {

        if (conversaId == Conversa.ID_GERAL) {
            return;
        }

        long id = exigirConta(usuario);
        exigirParticipacao(id, conversaId);
        protegido("leitura", () -> {
            conversas.marcarLida(conversaId, id, mensagemId);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // IA e perfis (uso interno do servidor)
    // ------------------------------------------------------------------

    /**
     * Exige conta e participação (fora da Geral) antes de qualquer consulta
     * ao estado interno da conversa. Inexistente e sem acesso respondem igual.
     */
    void exigirAcesso(Usuario usuario, long conversaId) throws ConversaException {
        if (conversaId != Conversa.ID_GERAL) {
            exigirParticipacao(exigirConta(usuario), conversaId);
        }
    }

    /** Conversa pelo id (sem verificação de acesso: uso interno). */
    Optional<Conversa> buscarConversa(long conversaId) {
        return conversas.buscar(conversaId);
    }

    /**
     * Grava e entrega a resposta do personagem, com o mesmo bloqueio por
     * conversa das mensagens dos participantes (ordem preservada).
     */
    Mensagem salvarRespostaIA(long conversaId, String nomePersonagem, String texto) {

        Set<Long> participantes = conversas.participantes(conversaId);
        Mensagem mensagem = new Mensagem(null, new Usuario(nomePersonagem), texto,
                LocalDateTime.now(relogio), TipoMensagem.IA, conversaId);

        synchronized (bloqueios.computeIfAbsent(conversaId, chave -> new Object())) {
            Mensagem persistida = mensagens.salvar(mensagem);
            registro.transmitirPara(persistida, participantes);
            return persistida;
        }
    }

    /** Avisa os participantes sobre o estado da geração da IA. */
    void notificarIA(long conversaId, String estado, String mensagem) {
        try {
            Set<Long> participantes = conversas.participantes(conversaId);
            registro.paraUsuarios(participantes, cliente -> cliente.estadoIA(conversaId, estado, mensagem));
        } catch (RuntimeException e) {
            registrarErroInterno("aviso da IA", e);
        }
    }

    /** Anuncia o perfil público alterado a todas as sessões com conta. */
    public void anunciarPerfil(PerfilUsuario perfil) {
        registro.paraUsuarios(registro.contasConectadas(), cliente -> cliente.perfilAtualizado(perfil));
    }

    ResumoConversa resumoPara(long conversaId, long usuarioId) throws ConversaException {
        return resumoDe(conversaId, usuarioId);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void avisarParticipantes(long conversaId, Set<Long> destinatarios) {
        for (long destinatario : destinatarios) {
            try {
                ResumoConversa resumo = resumoDe(conversaId, destinatario);
                registro.paraUsuarios(Set.of(destinatario), cliente -> cliente.conversaDisponivel(resumo));
            } catch (ConversaException e) {
                // Aviso em tempo real é opcional: a conversa aparece na próxima listagem.
            }
        }
    }

    private ResumoConversa resumoDe(long conversaId, long usuarioId) throws ConversaException {
        Conversa conversa = protegido("conversa", () -> conversas.buscar(conversaId))
                .orElseThrow(ServicoConversas::naoEncontrada);
        return protegido("conversa", () -> resumo(conversa, usuarioId));
    }

    private ResumoConversa resumo(Conversa conversa, long usuarioId) {
        return new ResumoConversa(conversa, ultima(conversa.getId()),
                conversas.naoLidas(conversa.getId(), usuarioId));
    }

    private Mensagem ultima(long conversaId) {
        List<Mensagem> ultimas = mensagens.buscarHistorico(conversaId, null, 1);
        return ultimas.isEmpty() ? null : ultimas.get(0);
    }

    private static long exigirConta(Usuario usuario) throws ConversaException {
        if (usuario == null || usuario.getId() == null) {
            throw new ConversaException(ConversaException.Codigo.CONTA_NECESSARIA,
                    "Entre com uma conta para usar conversas privadas e grupos.");
        }
        return usuario.getId();
    }

    private void exigirParticipacao(long usuarioId, long conversaId) throws ConversaException {
        if (!protegido("participação", () -> conversas.participa(conversaId, usuarioId))) {
            throw naoEncontrada();
        }
    }

    private void exigirUsuario(long usuarioId) throws ConversaException {
        if (protegido("usuário", () -> usuarios.buscarPorId(usuarioId)).isEmpty()) {
            throw new ConversaException(ConversaException.Codigo.NAO_ENCONTRADA, "Usuário não encontrado.");
        }
    }

    private static ConversaException naoEncontrada() {
        return new ConversaException(ConversaException.Codigo.NAO_ENCONTRADA, MENSAGEM_NAO_ENCONTRADA);
    }

    private static ConversaException indisponivel() {
        return new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                "Não foi possível concluir a operação agora. Tente novamente.");
    }

    @FunctionalInterface
    private interface Operacao<T> {
        T executar();
    }

    /** Falhas internas viram INDISPONIVEL; o log registra só a operação e o tipo. */
    private static <T> T protegido(String operacao, Operacao<T> acao) throws ConversaException {
        try {
            return acao.executar();
        } catch (IllegalArgumentException e) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, "Dados inválidos.");
        } catch (RuntimeException e) {
            registrarErroInterno(operacao, e);
            throw indisponivel();
        }
    }

    private static void registrarErroInterno(String operacao, RuntimeException e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        System.err.println("Chat Sakura (conversas): erro interno em " + operacao + ": "
                + e.getClass().getSimpleName() + " / " + causa.getClass().getSimpleName());
    }
}
