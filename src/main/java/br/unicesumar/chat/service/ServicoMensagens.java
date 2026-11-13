package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.FiltroPesquisa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.MensagemFixada;
import br.unicesumar.chat.model.TextoBusca;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.FixacaoRepository;
import br.unicesumar.chat.persistence.MensagemRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pesquisa no histórico persistido e mensagens fixadas.
 *
 * <h2>Acesso (sempre verificado aqui, nunca só no cliente)</h2>
 * <ul>
 *   <li>A conta vem da sessão autenticada.</li>
 *   <li>Chat Geral: qualquer conta pode pesquisar (o histórico já é público
 *       a todos); não há mensagens fixadas no Geral.</li>
 *   <li>Privadas, grupos e IA: somente participantes. Conversa inexistente
 *       e conversa alheia têm a mesma resposta ({@code NAO_ENCONTRADA}), para
 *       não revelar que ela existe.</li>
 *   <li>Fixar e desafixar: na conversa privada e na de IA, qualquer
 *       participante (as duas pessoas têm os mesmos direitos); no grupo, só
 *       quem tem o papel ADMIN (o criador), papel que já existia.</li>
 * </ul>
 *
 * O limite de {@value MensagemFixada#LIMITE_POR_CONVERSA} fixações por
 * conversa é verificado numa transação {@code BEGIN IMMEDIATE} e, além
 * disso, sob um bloqueio por conversa.
 */
public final class ServicoMensagens {

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final FixacaoRepository fixacoes;
    private final ClientRegistry registro;
    private final Clock relogio;

    private final ConcurrentHashMap<Long, Object> bloqueios = new ConcurrentHashMap<>();

    public ServicoMensagens(ConversaRepository conversas, MensagemRepository mensagens, FixacaoRepository fixacoes,
                            ClientRegistry registro, Clock relogio) {
        this.conversas = Objects.requireNonNull(conversas);
        this.mensagens = Objects.requireNonNull(mensagens);
        this.fixacoes = Objects.requireNonNull(fixacoes);
        this.registro = Objects.requireNonNull(registro);
        this.relogio = Objects.requireNonNull(relogio);
    }

    /** Mensagem encontrada e o trecho a exibir, com a posição do termo. */
    public record Encontrada(Mensagem mensagem, TextoBusca.Trecho trecho) {
    }

    /** Uma página de resultados; {@code temMais} indica que há outra página. */
    public record Pagina(List<Encontrada> resultados, boolean temMais) {
    }

    // ------------------------------------------------------------------
    // Pesquisa
    // ------------------------------------------------------------------

    /**
     * @param texto      termo digitado (2 a 100 caracteres após a normalização)
     * @param antesDeId  paginação: o id da última mensagem da página anterior
     */
    public Pagina pesquisar(Usuario usuario, long conversaId, String texto, Long remetenteId,
                            LocalDate desde, LocalDate ate, Long antesDeId, int limite)
            throws ConversaException {

        long id = exigirConta(usuario);
        exigirAcesso(id, conversaId);

        if (limite < 1 || limite > FiltroPesquisa.LIMITE_MAXIMO) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS,
                    "Quantidade de resultados inválida.");
        }

        // Um a mais que o pedido: indica se há outra página, sem contar tudo.
        FiltroPesquisa filtro;
        try {
            filtro = new FiltroPesquisa(conversaId, TextoBusca.termo(texto), remetenteId, desde, ate, antesDeId,
                    limite + 1);
        } catch (IllegalArgumentException e) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, e.getMessage());
        }

        List<Mensagem> encontradas = protegido("pesquisa", () -> mensagens.pesquisar(filtro));

        boolean temMais = encontradas.size() > limite;
        List<Encontrada> pagina = new ArrayList<>();
        Set<Long> vistas = new HashSet<>();
        for (Mensagem m : encontradas.subList(0, Math.min(limite, encontradas.size()))) {
            if (vistas.add(m.getId())) {
                pagina.add(new Encontrada(m, TextoBusca.trecho(m.getConteudo(), filtro.termo())));
            }
        }
        return new Pagina(pagina, temMais);
    }

    // ------------------------------------------------------------------
    // Mensagens fixadas
    // ------------------------------------------------------------------

    public List<MensagemFixada> fixadas(Usuario usuario, long conversaId) throws ConversaException {
        long id = exigirConta(usuario);
        exigirAcesso(id, conversaId);
        if (conversaId == Conversa.ID_GERAL) {
            return List.of();
        }
        return protegido("listagem de fixadas", () -> fixacoes.listar(conversaId));
    }

    /** Fixa a mensagem e avisa os demais participantes conectados. @return a lista atualizada */
    public List<MensagemFixada> fixar(Usuario usuario, long conversaId, long mensagemId) throws ConversaException {

        long id = exigirConta(usuario);
        exigirPermissaoDeFixar(id, conversaId);

        MensagemFixada.Resultado resultado;
        synchronized (bloqueios.computeIfAbsent(conversaId, chave -> new Object())) {
            resultado = protegido("fixação", () -> fixacoes.fixar(conversaId, mensagemId, id,
                    LocalDateTime.now(relogio), MensagemFixada.LIMITE_POR_CONVERSA));
        }

        switch (resultado) {
            case MENSAGEM_INEXISTENTE -> throw new ConversaException(ConversaException.Codigo.NAO_ENCONTRADA,
                    "Mensagem não encontrada nesta conversa.");
            case LIMITE -> throw new ConversaException(ConversaException.Codigo.LIMITE,
                    "Esta conversa já tem " + MensagemFixada.LIMITE_POR_CONVERSA
                            + " mensagens fixadas. Desafixe uma antes de fixar outra.");
            case FIXADA, JA_FIXADA -> {
                // JA_FIXADA: pedido repetido; o resultado é o mesmo.
            }
        }

        return atualizarParticipantes(id, conversaId, resultado == MensagemFixada.Resultado.FIXADA);
    }

    /** Desafixa (a mensagem original continua na conversa). @return a lista atualizada */
    public List<MensagemFixada> desafixar(Usuario usuario, long conversaId, long mensagemId)
            throws ConversaException {

        long id = exigirConta(usuario);
        exigirPermissaoDeFixar(id, conversaId);

        boolean removida;
        synchronized (bloqueios.computeIfAbsent(conversaId, chave -> new Object())) {
            removida = protegido("desafixação", () -> fixacoes.desafixar(conversaId, mensagemId));
        }
        return atualizarParticipantes(id, conversaId, removida);
    }

    private List<MensagemFixada> atualizarParticipantes(long autorId, long conversaId, boolean mudou)
            throws ConversaException {

        List<MensagemFixada> lista = protegido("listagem de fixadas", () -> fixacoes.listar(conversaId));

        if (mudou) {
            try {
                Set<Long> outros = new HashSet<>(conversas.participantes(conversaId));
                outros.remove(autorId);
                registro.paraUsuarios(outros, cliente -> cliente.fixadasAlteradas(conversaId, lista));
            } catch (RuntimeException e) {
                // Aviso em tempo real é opcional: a lista é pedida ao abrir a conversa.
                registrarErroInterno("aviso de fixadas", e);
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // Regras de acesso
    // ------------------------------------------------------------------

    private void exigirAcesso(long usuarioId, long conversaId) throws ConversaException {
        if (conversaId != Conversa.ID_GERAL
                && !protegido("participação", () -> conversas.participa(conversaId, usuarioId))) {
            throw naoEncontrada();
        }
    }

    private void exigirPermissaoDeFixar(long usuarioId, long conversaId) throws ConversaException {

        if (conversaId == Conversa.ID_GERAL) {
            throw new ConversaException(ConversaException.Codigo.SEM_PERMISSAO,
                    "Não é possível fixar mensagens no Chat Geral.");
        }

        Conversa conversa = protegido("conversa", () -> conversas.buscar(conversaId))
                .filter(c -> c.participa(usuarioId))
                .orElseThrow(ServicoMensagens::naoEncontrada);

        if (conversa.getTipo() == Conversa.Tipo.GRUPO) {
            boolean admin = conversa.getParticipantes().stream()
                    .anyMatch(p -> p.id() == usuarioId && p.papel() == Conversa.Papel.ADMIN);
            if (!admin) {
                throw new ConversaException(ConversaException.Codigo.SEM_PERMISSAO,
                        "Somente a administração do grupo pode fixar ou desafixar mensagens.");
            }
        }
    }

    /** Política de fixação para a interface (o servidor verifica de novo a cada pedido). */
    public static boolean podeFixar(Conversa conversa, long usuarioId) {
        return switch (conversa.getTipo()) {
            case GERAL -> false;
            case PRIVADA, IA -> conversa.participa(usuarioId);
            case GRUPO -> conversa.getParticipantes().stream()
                    .anyMatch(p -> p.id() == usuarioId && p.papel() == Conversa.Papel.ADMIN);
        };
    }

    // ------------------------------------------------------------------

    private static long exigirConta(Usuario usuario) throws ConversaException {
        if (usuario == null || usuario.getId() == null) {
            throw new ConversaException(ConversaException.Codigo.CONTA_NECESSARIA,
                    "Entre com uma conta para pesquisar e fixar mensagens.");
        }
        return usuario.getId();
    }

    private static ConversaException naoEncontrada() {
        return new ConversaException(ConversaException.Codigo.NAO_ENCONTRADA,
                ServicoConversas.MENSAGEM_NAO_ENCONTRADA);
    }

    @FunctionalInterface
    private interface Operacao<T> {
        T executar();
    }

    private static <T> T protegido(String operacao, Operacao<T> acao) throws ConversaException {
        try {
            return acao.executar();
        } catch (IllegalArgumentException e) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, "Dados inválidos.");
        } catch (RuntimeException e) {
            registrarErroInterno(operacao, e);
            throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                    "Não foi possível concluir a operação agora. Tente novamente.");
        }
    }

    private static void registrarErroInterno(String operacao, RuntimeException e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        System.err.println("Chat Sakura (mensagens): erro interno em " + operacao + ": "
                + e.getClass().getSimpleName() + " / " + causa.getClass().getSimpleName());
    }
}
