package br.unicesumar.chat.service;

import br.unicesumar.chat.ia.ParametrosIA;
import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorIA.FalhaIA;
import br.unicesumar.chat.ia.ProvedorIA.PedidoIA;
import br.unicesumar.chat.ia.ProvedorIA.Resposta;
import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.MemoriaPersonagem;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Personagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.MemoriaRepository;
import br.unicesumar.chat.persistence.MensagemRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Personagens de IA, geração das respostas e memória individual.
 *
 * <p><b>Execução</b>: a chamada ao provedor roda num executor próprio, fora
 * da thread da conexão, da EDT e de qualquer bloqueio do chat. O executor tem
 * {@link ParametrosIA#geracoesSimultaneas()} threads (Ollama local: 1) e fila
 * limitada a {@link ParametrosIA#filaMaxima()}; com a fila cheia o envio é
 * recusado antes de gravar a mensagem. Uma conversa nunca tem duas gerações
 * ao mesmo tempo, e o estado é liberado em sucesso, erro, tempo esgotado ou
 * recusa da fila.
 *
 * <p><b>Memória</b>: a cada {@value #RESUMIR_A_CADA} mensagens novas, depois
 * de uma resposta, um resumo curto é pedido ao modelo e filtrado
 * ({@link FiltroMemoria}) antes de ser gravado por conversa. O resumo usa o
 * mesmo controle de exclusão da geração: nunca roda junto com uma geração
 * da mesma conversa. Só o dono vê ou apaga a memória do seu personagem.
 */
public final class ServicoIA {

    /** Limite adicional de caracteres do histórico enviado (além do orçamento de tokens). */
    static final int CARACTERES_CONTEXTO = MontadorPrompt.HISTORICO_MAXIMO_CARACTERES;

    /** Limite de uso por conta: gerações por minuto. */
    static final int GERACOES_POR_MINUTO = 8;

    /** Mensagens novas (usuário e personagem) que disparam a atualização da memória. */
    static final int RESUMIR_A_CADA = 10;
    static final int MENSAGENS_RESUMO_MAXIMAS = 40;
    static final int TOKENS_RESUMO = 220;

    public static final String GERANDO = "GERANDO";
    public static final String CONCLUIDO = "CONCLUIDO";
    public static final String ERRO = "ERRO";
    /** Resposta gravada, mas interrompida pelo limite de tamanho (marcada com "[…]"). */
    public static final String TRUNCADA = "TRUNCADA";

    static final String MENSAGEM_OCUPADA =
            "A Sakura IA está ocupada respondendo outras conversas. Tente de novo em alguns segundos.";
    static final String MENSAGEM_TRUNCADA =
            "A resposta foi interrompida pelo limite de tamanho (marcada com […]).";

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final ServicoConversas servicoConversas;
    private final ProvedorIA provedor;
    private final MemoriaRepository memorias;
    private final Clock relogio;
    private final ParametrosIA parametros;
    private final ThreadPoolExecutor executor;

    // Conversas com geração ou resumo em andamento (exclusão por conversa).
    private final Set<Long> emGeracao = ConcurrentHashMap.newKeySet();
    private final Set<Long> emResumo = ConcurrentHashMap.newKeySet();
    private final Map<Long, Deque<Long>> usoPorConta = new ConcurrentHashMap<>();

    // Tarefas aceitas e ainda não concluídas (gerações e resumos).
    private final Object monitorTarefas = new Object();
    private int tarefasPendentes;

    public ServicoIA(ConversaRepository conversas, MensagemRepository mensagens,
                     ServicoConversas servicoConversas, ProvedorIA provedor, MemoriaRepository memorias,
                     Clock relogio) {
        this.conversas = Objects.requireNonNull(conversas);
        this.mensagens = Objects.requireNonNull(mensagens);
        this.servicoConversas = Objects.requireNonNull(servicoConversas);
        this.provedor = Objects.requireNonNull(provedor);
        this.memorias = Objects.requireNonNull(memorias);
        this.relogio = Objects.requireNonNull(relogio);
        this.parametros = Objects.requireNonNull(provedor.parametros());

        AtomicInteger contador = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                parametros.geracoesSimultaneas(), parametros.geracoesSimultaneas(),
                0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(parametros.filaMaxima()),
                tarefa -> {
                    Thread thread = new Thread(tarefa, "sakura-ia-" + contador.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public ProvedorIA provedor() {
        return provedor;
    }

    public ParametrosIA parametros() {
        return parametros;
    }

    /** Aquecimento opcional do modelo, em segundo plano (não bloqueia a inicialização). */
    public void aquecerEmSegundoPlano() {
        Thread thread = new Thread(provedor::aquecer, "sakura-ia-aquecimento");
        thread.setDaemon(true);
        thread.start();
    }

    /** {@code true} enquanto o personagem gera uma resposta nesta conversa. */
    public boolean gerando(long conversaId) {
        return emGeracao.contains(conversaId) && !emResumo.contains(conversaId);
    }

    /** Cria o personagem e sua conversa individual (somente o dono participa). */
    public ResumoConversa criarPersonagem(Usuario usuario, String nome, String personalidade, String contexto)
            throws ConversaException {

        if (usuario == null || usuario.getId() == null) {
            throw new ConversaException(ConversaException.Codigo.CONTA_NECESSARIA,
                    "Entre com uma conta para criar personagens.");
        }

        String nomeTratado;
        String personalidadeTratada;
        String contextoTratado;

        try {
            nomeTratado = Personagem.normalizarNome(nome);
            personalidadeTratada = Personagem.normalizarPersonalidade(personalidade);
            contextoTratado = Personagem.normalizarContexto(contexto);
        } catch (IllegalArgumentException e) {
            throw new ConversaException(ConversaException.Codigo.DADOS_INVALIDOS, e.getMessage());
        }

        long conversaId;
        try {
            conversaId = conversas.criarConversaIA(usuario.getId(), nomeTratado, personalidadeTratada,
                    contextoTratado, LocalDateTime.now(relogio));
        } catch (RuntimeException e) {
            registrarErro("criação de personagem", e);
            throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                    "Não foi possível criar o personagem agora.");
        }

        return servicoConversas.resumoPara(conversaId, usuario.getId());
    }

    /**
     * Antes de gravar uma mensagem: numa conversa de IA, recusa se já há
     * resposta ou resumo em andamento, se a fila da IA está cheia ou se o
     * limite de uso foi atingido. A participação é verificada primeiro.
     */
    public void verificarEnvio(Usuario usuario, long conversaId) throws ConversaException {

        // Autorização primeiro: quem não participa recebe NAO_ENCONTRADA sem
        // saber se a conversa é de IA, se está gerando, nem gastar cota.
        servicoConversas.exigirAcesso(usuario, conversaId);

        if (!isConversaIA(conversaId)) {
            return;
        }

        if (emResumo.contains(conversaId)) {
            throw new ConversaException(ConversaException.Codigo.LIMITE,
                    "O personagem está organizando a memória da conversa. Tente de novo em instantes.");
        }

        if (emGeracao.contains(conversaId)) {
            throw new ConversaException(ConversaException.Codigo.LIMITE,
                    "Aguarde o personagem terminar de responder.");
        }

        if (executor.getQueue().remainingCapacity() == 0) {
            throw new ConversaException(ConversaException.Codigo.LIMITE, MENSAGEM_OCUPADA);
        }

        if (usuario.getId() != null && !registrarUso(usuario.getId())) {
            throw new ConversaException(ConversaException.Codigo.LIMITE,
                    "Limite de " + GERACOES_POR_MINUTO + " mensagens por minuto para a IA. Aguarde um pouco.");
        }
    }

    /** Depois de gravada a mensagem do dono: agenda a resposta do personagem. */
    public void aposMensagem(Usuario usuario, Mensagem mensagem) {

        long conversaId = mensagem.getConversaId();

        if (!isConversaIA(conversaId) || !emGeracao.add(conversaId)) {
            return;
        }

        servicoConversas.notificarIA(conversaId, GERANDO, provedor.descricao());

        if (!agendar(() -> gerar(usuario, conversaId))) {
            // Fila cheia entre a verificação e o agendamento: a mensagem fica
            // gravada, sem resposta, e o usuário é avisado.
            emGeracao.remove(conversaId);
            servicoConversas.notificarIA(conversaId, ERRO, MENSAGEM_OCUPADA);
        }
    }

    private void gerar(Usuario usuario, long conversaId) {

        boolean respondeu = false;

        try {
            Personagem personagem = conversas.personagemDaConversa(conversaId).orElseThrow();
            MemoriaPersonagem memoria = memoriaDe(conversaId, personagem);

            List<Mensagem> recentes = new ArrayList<>();
            for (Mensagem m : mensagens.buscarHistorico(conversaId, null, parametros.historicoMensagens())) {
                if (m.getId() > memoria.esquecerAteId()) {
                    recentes.add(m);
                }
            }

            PedidoIA pedido = MontadorPrompt.resposta(personagem, usuario.getNome(), memoria.resumo(), recentes,
                    parametros);

            Resposta resposta = provedor.gerarResposta(pedido);
            String texto = resposta.truncada() ? MontadorPrompt.marcarTruncada(resposta.texto()) : resposta.texto();

            for (String parte : dividir(texto, Mensagem.TAMANHO_MAXIMO_CONTEUDO)) {
                servicoConversas.salvarRespostaIA(conversaId, personagem.getNome(), parte);
            }

            servicoConversas.notificarIA(conversaId,
                    resposta.truncada() ? TRUNCADA : CONCLUIDO,
                    resposta.truncada() ? MENSAGEM_TRUNCADA : provedor.descricao());
            respondeu = true;

        } catch (FalhaIA e) {
            System.err.println("Chat Sakura (IA): falha " + e.getCodigo() + " na conversa " + conversaId);
            servicoConversas.notificarIA(conversaId, ERRO, e.getMessage());

        } catch (RuntimeException e) {
            registrarErro("geração", e);
            servicoConversas.notificarIA(conversaId, ERRO, "Não foi possível gerar a resposta agora.");

        } finally {
            emGeracao.remove(conversaId);
        }

        if (respondeu) {
            agendarResumoSeNecessario(usuario.getNome(), conversaId);
        }
    }

    // ------------------------------------------------------------------
    // Memória
    // ------------------------------------------------------------------

    /** Memória gravada da conversa, conferida contra o personagem; vazia se ausente ou inconsistente. */
    private MemoriaPersonagem memoriaDe(long conversaId, Personagem personagem) {
        Optional<MemoriaPersonagem> gravada = memorias.buscar(conversaId);
        if (gravada.isPresent() && gravada.get().donoId() == personagem.getDonoId()
                && gravada.get().personagemId() == personagem.getId()) {
            return gravada.get();
        }
        if (gravada.isPresent()) {
            System.err.println("Chat Sakura (IA): memória inconsistente ignorada na conversa " + conversaId);
        }
        return MemoriaPersonagem.vazia(conversaId, personagem.getDonoId(), personagem.getId());
    }

    /** Mensagens ainda não resumidas (sem avisos de presença), em ordem. */
    private List<Mensagem> naoResumidas(long conversaId, MemoriaPersonagem memoria) {
        long base = Math.max(memoria.ateMensagemId(), memoria.esquecerAteId());
        List<Mensagem> novas = new ArrayList<>();
        for (Mensagem m : mensagens.buscarHistorico(conversaId, null, MENSAGENS_RESUMO_MAXIMAS)) {
            if (m.getId() > base && !m.isAvisoDePresenca()) {
                novas.add(m);
            }
        }
        return novas;
    }

    private void agendarResumoSeNecessario(String apelido, long conversaId) {
        try {
            Personagem personagem = conversas.personagemDaConversa(conversaId).orElseThrow();
            if (naoResumidas(conversaId, memoriaDe(conversaId, personagem)).size() >= RESUMIR_A_CADA) {
                // Sem vaga na fila, fica para a próxima resposta.
                agendar(() -> resumir(apelido, conversaId));
            }
        } catch (RuntimeException e) {
            registrarErro("agendamento da memória", e);
        }
    }

    /**
     * Atualiza a memória. Não roda se houver geração em andamento na conversa
     * (e impede que uma comece enquanto isso). Um resumo que termine depois de
     * a memória ser apagada não é gravado.
     */
    private void resumir(String apelido, long conversaId) {

        if (!emGeracao.add(conversaId)) {
            return;
        }
        emResumo.add(conversaId);

        try {
            Personagem personagem = conversas.personagemDaConversa(conversaId).orElseThrow();
            MemoriaPersonagem memoria = memoriaDe(conversaId, personagem);
            List<Mensagem> novas = naoResumidas(conversaId, memoria);

            if (novas.size() < RESUMIR_A_CADA) {
                return;
            }

            Resposta resposta = provedor.gerarResposta(
                    MontadorPrompt.resumo(personagem, apelido, memoria.resumo(), novas, TOKENS_RESUMO));
            String notas = FiltroMemoria.filtrar(resposta.texto(), resposta.truncada());

            memorias.salvarResumo(conversaId, personagem.getDonoId(), personagem.getId(),
                    notas.isEmpty() ? memoria.resumo() : notas,
                    novas.get(novas.size() - 1).getId(), memoria.esquecerAteId(), LocalDateTime.now(relogio));

        } catch (FalhaIA e) {
            System.err.println("Chat Sakura (IA): memória não atualizada (" + e.getCodigo() + ") na conversa "
                    + conversaId);
        } catch (RuntimeException e) {
            registrarErro("atualização da memória", e);
        } finally {
            emResumo.remove(conversaId);
            emGeracao.remove(conversaId);
        }
    }

    /** Memória do personagem (somente o dono; para os demais, NAO_ENCONTRADA). */
    public MemoriaPersonagem memoria(Usuario usuario, long conversaId) throws ConversaException {
        Personagem personagem = exigirDono(usuario, conversaId);
        try {
            return memoriaDe(conversaId, personagem);
        } catch (RuntimeException e) {
            registrarErro("consulta da memória", e);
            throw indisponivel();
        }
    }

    /**
     * Apaga a memória sem apagar o histórico: as mensagens existentes deixam
     * de ir ao modelo e de entrar em resumos futuros.
     */
    public MemoriaPersonagem apagarMemoria(Usuario usuario, long conversaId) throws ConversaException {

        Personagem personagem = exigirDono(usuario, conversaId);

        if (emGeracao.contains(conversaId)) {
            throw new ConversaException(ConversaException.Codigo.LIMITE,
                    "Aguarde o personagem terminar de responder para apagar a memória.");
        }

        try {
            List<Mensagem> ultima = mensagens.buscarHistorico(conversaId, null, 1);
            long ultimaId = ultima.isEmpty() ? 0 : ultima.get(0).getId();
            memorias.apagar(conversaId, personagem.getDonoId(), personagem.getId(), ultimaId,
                    LocalDateTime.now(relogio));
            return memoriaDe(conversaId, personagem);
        } catch (RuntimeException e) {
            registrarErro("exclusão da memória", e);
            throw indisponivel();
        }
    }

    private Personagem exigirDono(Usuario usuario, long conversaId) throws ConversaException {

        servicoConversas.exigirAcesso(usuario, conversaId);

        Optional<Personagem> personagem;
        try {
            personagem = conversas.personagemDaConversa(conversaId);
        } catch (RuntimeException e) {
            registrarErro("consulta do personagem", e);
            throw indisponivel();
        }

        if (personagem.isEmpty() || !Objects.equals(personagem.get().getDonoId(), usuario.getId())) {
            throw new ConversaException(ConversaException.Codigo.NAO_ENCONTRADA,
                    ServicoConversas.MENSAGEM_NAO_ENCONTRADA);
        }
        return personagem.get();
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /**
     * Instruções do personagem e histórico recente só daquela conversa,
     * dentro dos limites padrão (usado pelos testes e como referência).
     */
    static PedidoIA montarPedido(Personagem personagem, String apelidoUsuario, List<Mensagem> recentes) {
        return MontadorPrompt.resposta(personagem, apelidoUsuario, "", recentes, ParametrosIA.REMOTO);
    }

    /** Divide respostas longas em mensagens de até {@code maximo} caracteres. */
    static List<String> dividir(String texto, int maximo) {

        List<String> partes = new ArrayList<>();
        String restante = texto.strip();

        while (restante.length() > maximo) {
            int corte = restante.lastIndexOf('\n', maximo);
            if (corte < maximo / 2) {
                corte = restante.lastIndexOf(". ", maximo);
                corte = corte < maximo / 2 ? -1 : corte + 1;
            }
            if (corte <= 0) {
                corte = restante.lastIndexOf(' ', maximo);
            }
            if (corte <= 0) {
                corte = maximo;
            }
            partes.add(restante.substring(0, corte).strip());
            restante = restante.substring(corte).strip();
        }

        if (!restante.isEmpty()) {
            partes.add(restante);
        }
        return partes;
    }

    /** Coloca a tarefa na fila limitada; {@code false} se não houver vaga. */
    private boolean agendar(Runnable tarefa) {
        synchronized (monitorTarefas) {
            tarefasPendentes++;
        }
        try {
            executor.execute(() -> {
                try {
                    tarefa.run();
                } finally {
                    concluirTarefa();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            concluirTarefa();
            return false;
        }
    }

    private void concluirTarefa() {
        synchronized (monitorTarefas) {
            tarefasPendentes--;
            monitorTarefas.notifyAll();
        }
    }

    private boolean isConversaIA(long conversaId) {
        if (conversaId == Conversa.ID_GERAL) {
            return false;
        }
        try {
            Optional<Conversa> conversa = servicoConversas.buscarConversa(conversaId);
            return conversa.isPresent() && conversa.get().getTipo() == Conversa.Tipo.IA;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean registrarUso(long contaId) {
        long agora = relogio.millis();
        Deque<Long> uso = usoPorConta.computeIfAbsent(contaId, id -> new ArrayDeque<>());
        synchronized (uso) {
            while (!uso.isEmpty() && agora - uso.peekFirst() > 60_000) {
                uso.pollFirst();
            }
            if (uso.size() >= GERACOES_POR_MINUTO) {
                return false;
            }
            uso.addLast(agora);
            return true;
        }
    }

    /** Aguarda todas as gerações e resumos aceitos terminarem (testes e encerramento). */
    public boolean aguardar(long milissegundos) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milissegundos);
        synchronized (monitorTarefas) {
            while (tarefasPendentes > 0) {
                long restante = limite - System.nanoTime();
                if (restante <= 0) {
                    return false;
                }
                monitorTarefas.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(restante)));
            }
        }
        return emGeracao.isEmpty();
    }

    private static ConversaException indisponivel() {
        return new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                "Não foi possível concluir a operação agora. Tente novamente.");
    }

    private static void registrarErro(String operacao, RuntimeException e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        System.err.println("Chat Sakura (IA): erro interno em " + operacao + ": "
                + e.getClass().getSimpleName() + " / " + causa.getClass().getSimpleName());
    }
}
