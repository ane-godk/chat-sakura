package br.unicesumar.chat.service;

import br.unicesumar.chat.ia.ProvedorIA;
import br.unicesumar.chat.ia.ProvedorIA.FalhaIA;
import br.unicesumar.chat.ia.ProvedorIA.Papel;
import br.unicesumar.chat.ia.ProvedorIA.PedidoIA;
import br.unicesumar.chat.ia.ProvedorIA.Turno;
import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Personagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.ConversaRepository;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Personagens de IA e geração das respostas.
 *
 * A chamada ao provedor (Qwen) roda num executor próprio, fora da thread
 * da conexão, fora da EDT e fora de qualquer bloqueio do chat: Geral,
 * privadas e grupos seguem funcionando mesmo com a IA lenta ou indisponível.
 * Somente o dono participa da conversa de IA; a autorização é a mesma das
 * demais conversas ({@link ServicoConversas}).
 */
public final class ServicoIA {

    /** Mensagens recentes enviadas como contexto e limite total de caracteres. */
    static final int HISTORICO_CONTEXTO = 20;
    static final int CARACTERES_CONTEXTO = 6_000;
    static final int TOKENS_RESPOSTA = 400;

    /** Limite de uso por conta: gerações por minuto. */
    static final int GERACOES_POR_MINUTO = 8;

    public static final String GERANDO = "GERANDO";
    public static final String CONCLUIDO = "CONCLUIDO";
    public static final String ERRO = "ERRO";

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final ServicoConversas servicoConversas;
    private final ProvedorIA provedor;
    private final Clock relogio;
    private final ExecutorService executor;

    private final Set<Long> emGeracao = ConcurrentHashMap.newKeySet();
    private final Map<Long, Deque<Long>> usoPorConta = new ConcurrentHashMap<>();

    public ServicoIA(ConversaRepository conversas, MensagemRepository mensagens,
                     ServicoConversas servicoConversas, ProvedorIA provedor, Clock relogio) {
        this.conversas = Objects.requireNonNull(conversas);
        this.mensagens = Objects.requireNonNull(mensagens);
        this.servicoConversas = Objects.requireNonNull(servicoConversas);
        this.provedor = Objects.requireNonNull(provedor);
        this.relogio = Objects.requireNonNull(relogio);

        AtomicInteger contador = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(2, tarefa -> {
            Thread thread = new Thread(tarefa, "sakura-ia-" + contador.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public ProvedorIA provedor() {
        return provedor;
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
     * resposta em andamento ou se o limite de uso foi atingido.
     */
    public void verificarEnvio(Usuario usuario, long conversaId) throws ConversaException {

        // Autorização primeiro: quem não participa recebe NAO_ENCONTRADA sem
        // saber se a conversa é de IA, se está gerando, nem gastar cota.
        servicoConversas.exigirAcesso(usuario, conversaId);

        if (!isConversaIA(conversaId)) {
            return;
        }

        if (emGeracao.contains(conversaId)) {
            throw new ConversaException(ConversaException.Codigo.LIMITE,
                    "Aguarde o personagem terminar de responder.");
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
        executor.execute(() -> gerar(usuario, conversaId));
    }

    private void gerar(Usuario usuario, long conversaId) {

        try {
            Personagem personagem = conversas.personagemDaConversa(conversaId).orElseThrow();
            PedidoIA pedido = montarPedido(personagem, usuario.getNome(),
                    mensagens.buscarHistorico(conversaId, null, HISTORICO_CONTEXTO));

            String resposta = provedor.gerar(pedido);

            for (String parte : dividir(resposta, Mensagem.TAMANHO_MAXIMO_CONTEUDO)) {
                servicoConversas.salvarRespostaIA(conversaId, personagem.getNome(), parte);
            }

            servicoConversas.notificarIA(conversaId, CONCLUIDO, provedor.descricao());

        } catch (FalhaIA e) {
            System.err.println("Chat Sakura (IA): falha " + e.getCodigo() + " na conversa " + conversaId);
            servicoConversas.notificarIA(conversaId, ERRO, e.getMessage());

        } catch (RuntimeException e) {
            registrarErro("geração", e);
            servicoConversas.notificarIA(conversaId, ERRO, "Não foi possível gerar a resposta agora.");

        } finally {
            emGeracao.remove(conversaId);
        }
    }

    /**
     * Instruções do personagem e histórico recente só daquela conversa,
     * dentro do limite de caracteres (as mais antigas saem primeiro).
     */
    static PedidoIA montarPedido(Personagem personagem, String apelidoUsuario, List<Mensagem> recentes) {

        String instrucoes = "Você é " + personagem.getNome() + ", um personagem fictício interpretado por uma"
                + " inteligência artificial no aplicativo de mensagens Chat Sakura.\n"
                + "Personalidade: " + personagem.getPersonalidade() + "\n"
                + (personagem.getContexto().isEmpty() ? "" : "Contexto: " + personagem.getContexto() + "\n")
                + "Você conversa com " + apelidoUsuario + ".\n"
                + "Regras: responda sempre em português do Brasil, em no máximo três parágrafos curtos;"
                + " mantenha o personagem; não afirme ser uma pessoa real; não peça dados pessoais nem"
                + " senhas; recuse com gentileza pedidos impróprios.";

        List<Turno> turnos = new ArrayList<>();
        int total = 0;

        for (int i = recentes.size() - 1; i >= 0; i--) {
            Mensagem m = recentes.get(i);
            if (m.isAvisoDePresenca()) {
                continue;
            }
            total += m.getConteudo().length();
            if (total > CARACTERES_CONTEXTO && !turnos.isEmpty()) {
                break;
            }
            turnos.add(0, new Turno(m.isDaIA() ? Papel.ASSISTENTE : Papel.USUARIO, m.getConteudo()));
        }

        return new PedidoIA(instrucoes, turnos, TOKENS_RESPOSTA);
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

    /** Aguarda as gerações em andamento (testes e encerramento). */
    public boolean aguardar(long milissegundos) throws InterruptedException {
        long limite = System.currentTimeMillis() + milissegundos;
        while (!emGeracao.isEmpty() && System.currentTimeMillis() < limite) {
            executor.awaitTermination(20, TimeUnit.MILLISECONDS);
        }
        return emGeracao.isEmpty();
    }

    private static void registrarErro(String operacao, RuntimeException e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        System.err.println("Chat Sakura (IA): erro interno em " + operacao + ": "
                + e.getClass().getSimpleName() + " / " + causa.getClass().getSimpleName());
    }
}
