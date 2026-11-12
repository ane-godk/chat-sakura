package br.unicesumar.chat.server;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Limites de recursos do servidor (conexões, tempos e taxas por sessão).
 *
 * Valores padrão pensados para uso humano em rede local; os principais podem
 * ser ajustados por variáveis de ambiente (ver {@link #doAmbiente(Map)}).
 *
 * @param maxConexoes        conexões simultâneas no servidor
 * @param maxConexoesPorIp   conexões simultâneas por endereço IP
 * @param capacidadeFila     linhas pendentes na fila de saída de cada sessão;
 *                           ao exceder, a sessão é encerrada (cliente lento)
 * @param prazoEntrada       tempo total para concluir o nome ou o login
 * @param intervaloPing      v2: inatividade antes de o servidor enviar PING;
 *                           sem resposta em mais um intervalo, a sessão cai
 * @param prazoEncerramento  espera máxima para esvaziar a fila ao encerrar
 * @param mensagens          envio de mensagens (Geral, privadas, grupos, IA)
 * @param criacoes           grupos, personagens, conversas privadas e avatar
 * @param consultas          listagens e históricos
 * @param leituras           confirmações de leitura (excesso é descartado em silêncio)
 * @param linhas             qualquer linha recebida (proteção geral contra inundação)
 * @param relogioNanos       fonte de tempo dos baldes (substituível nos testes)
 */
public record LimitesServidor(
        int maxConexoes,
        int maxConexoesPorIp,
        int capacidadeFila,
        Duration prazoEntrada,
        Duration intervaloPing,
        Duration prazoEncerramento,
        Taxa mensagens,
        Taxa criacoes,
        Taxa consultas,
        Taxa leituras,
        Taxa linhas,
        LongSupplier relogioNanos
) {

    /** Token bucket: {@code rajada} fichas, repostas a {@code porMinuto} por minuto. */
    public record Taxa(int rajada, int porMinuto) {
        public Taxa {
            if (rajada < 1 || porMinuto < 1) {
                throw new IllegalArgumentException("Taxa inválida.");
            }
        }

        BaldeTokens novoBalde(LongSupplier relogioNanos) {
            return new BaldeTokens(rajada, porMinuto / 60.0, relogioNanos);
        }
    }

    public static final int MAX_CONEXOES_PADRAO = 200;
    public static final int MAX_CONEXOES_POR_IP_PADRAO = 10;
    public static final int CAPACIDADE_FILA_PADRAO = 1_000;
    public static final int PRAZO_ENTRADA_PADRAO_S = 30;
    public static final int INTERVALO_PING_PADRAO_S = 30;
    public static final int MENSAGENS_POR_MINUTO_PADRAO = 120;

    public LimitesServidor {
        if (maxConexoes < 1 || maxConexoesPorIp < 1 || capacidadeFila < 1) {
            throw new IllegalArgumentException("Limites de conexão inválidos.");
        }
        Objects.requireNonNull(prazoEntrada);
        Objects.requireNonNull(intervaloPing);
        Objects.requireNonNull(prazoEncerramento);
        Objects.requireNonNull(mensagens);
        Objects.requireNonNull(criacoes);
        Objects.requireNonNull(consultas);
        Objects.requireNonNull(leituras);
        Objects.requireNonNull(linhas);
        Objects.requireNonNull(relogioNanos);
        if (prazoEntrada.isNegative() || prazoEntrada.isZero()
                || intervaloPing.isNegative() || intervaloPing.isZero()) {
            throw new IllegalArgumentException("Tempos devem ser positivos.");
        }
    }

    public static LimitesServidor padrao() {
        return new LimitesServidor(
                MAX_CONEXOES_PADRAO,
                MAX_CONEXOES_POR_IP_PADRAO,
                CAPACIDADE_FILA_PADRAO,
                Duration.ofSeconds(PRAZO_ENTRADA_PADRAO_S),
                Duration.ofSeconds(INTERVALO_PING_PADRAO_S),
                Duration.ofSeconds(2),
                new Taxa(20, MENSAGENS_POR_MINUTO_PADRAO),
                new Taxa(5, 10),
                new Taxa(20, 120),
                new Taxa(30, 300),
                new Taxa(60, 600),
                System::nanoTime
        );
    }

    /**
     * Padrões com ajustes opcionais do ambiente (inteiros positivos):
     * {@code CHAT_SAKURA_MAX_CONEXOES}, {@code CHAT_SAKURA_MAX_CONEXOES_IP},
     * {@code CHAT_SAKURA_FILA_SAIDA}, {@code CHAT_SAKURA_PRAZO_ENTRADA_S},
     * {@code CHAT_SAKURA_PING_S} e {@code CHAT_SAKURA_MENSAGENS_POR_MINUTO}.
     * Valores ausentes ou inválidos mantêm o padrão.
     */
    public static LimitesServidor doAmbiente(Map<String, String> ambiente) {
        LimitesServidor p = padrao();
        int mensagensPorMinuto = inteiro(ambiente, "CHAT_SAKURA_MENSAGENS_POR_MINUTO", MENSAGENS_POR_MINUTO_PADRAO);
        return new LimitesServidor(
                inteiro(ambiente, "CHAT_SAKURA_MAX_CONEXOES", p.maxConexoes()),
                inteiro(ambiente, "CHAT_SAKURA_MAX_CONEXOES_IP", p.maxConexoesPorIp()),
                inteiro(ambiente, "CHAT_SAKURA_FILA_SAIDA", p.capacidadeFila()),
                Duration.ofSeconds(inteiro(ambiente, "CHAT_SAKURA_PRAZO_ENTRADA_S", PRAZO_ENTRADA_PADRAO_S)),
                Duration.ofSeconds(inteiro(ambiente, "CHAT_SAKURA_PING_S", INTERVALO_PING_PADRAO_S)),
                p.prazoEncerramento(),
                new Taxa(p.mensagens().rajada(), mensagensPorMinuto),
                p.criacoes(), p.consultas(), p.leituras(), p.linhas(),
                p.relogioNanos()
        );
    }

    private static int inteiro(Map<String, String> ambiente, String nome, int padrao) {
        String valor = ambiente.get(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            int numero = Integer.parseInt(valor.strip());
            return numero > 0 ? numero : padrao;
        } catch (NumberFormatException e) {
            System.err.println("Chat Sakura: " + nome + " inválido; usando " + padrao + ".");
            return padrao;
        }
    }

    // Ajustes pontuais (testes e configurações específicas).

    public LimitesServidor comConexoes(int maximo, int porIp) {
        return new LimitesServidor(maximo, porIp, capacidadeFila, prazoEntrada, intervaloPing, prazoEncerramento,
                mensagens, criacoes, consultas, leituras, linhas, relogioNanos);
    }

    public LimitesServidor comCapacidadeFila(int capacidade) {
        return new LimitesServidor(maxConexoes, maxConexoesPorIp, capacidade, prazoEntrada, intervaloPing,
                prazoEncerramento, mensagens, criacoes, consultas, leituras, linhas, relogioNanos);
    }

    public LimitesServidor comTempos(Duration entrada, Duration ping) {
        return new LimitesServidor(maxConexoes, maxConexoesPorIp, capacidadeFila, entrada, ping, prazoEncerramento,
                mensagens, criacoes, consultas, leituras, linhas, relogioNanos);
    }

    public LimitesServidor comTaxas(Taxa mensagens, Taxa criacoes, Taxa consultas, LongSupplier relogio) {
        return new LimitesServidor(maxConexoes, maxConexoesPorIp, capacidadeFila, prazoEntrada, intervaloPing,
                prazoEncerramento, mensagens, criacoes, consultas, leituras, linhas, relogio);
    }
}
