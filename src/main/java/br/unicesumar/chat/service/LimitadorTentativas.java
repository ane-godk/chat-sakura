package br.unicesumar.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Contador de falhas por chave em janela deslizante, com bloqueio temporário.
 *
 * Ao atingir {@code maximo} falhas dentro de {@code janela}, a chave fica
 * bloqueada por {@code bloqueio}; depois disso volta ao normal (nunca há
 * bloqueio permanente). Entradas vencidas são removidas periodicamente e o
 * número de chaves acompanhadas é limitado, para que a memória não cresça
 * sem limite sob ataque.
 */
final class LimitadorTentativas {

    static final int CHAVES_MAXIMAS_PADRAO = 10_000;
    private static final int OPERACOES_ENTRE_LIMPEZAS = 256;

    private static final class Registro {
        final Deque<Long> falhas = new ArrayDeque<>();
        long bloqueadoAte;
    }

    private final int maximo;
    private final long janelaMs;
    private final long bloqueioMs;
    private final int chavesMaximas;
    private final Clock relogio;

    private final Map<String, Registro> registros = new HashMap<>();
    private int operacoes;

    LimitadorTentativas(int maximo, Duration janela, Duration bloqueio, Clock relogio) {
        this(maximo, janela, bloqueio, CHAVES_MAXIMAS_PADRAO, relogio);
    }

    LimitadorTentativas(int maximo, Duration janela, Duration bloqueio, int chavesMaximas, Clock relogio) {
        if (maximo < 1 || chavesMaximas < 1) {
            throw new IllegalArgumentException("Limite inválido.");
        }
        this.maximo = maximo;
        this.janelaMs = janela.toMillis();
        this.bloqueioMs = bloqueio.toMillis();
        this.chavesMaximas = chavesMaximas;
        this.relogio = Objects.requireNonNull(relogio);
    }

    synchronized boolean bloqueado(String chave) {
        if (chave == null) {
            return false;
        }
        long agora = relogio.millis();
        manutencao(agora);
        Registro registro = registros.get(chave);
        return registro != null && registro.bloqueadoAte > agora;
    }

    /** Registra uma falha; ao atingir o máximo, inicia o bloqueio temporário. */
    synchronized void registrarFalha(String chave) {
        if (chave == null) {
            return;
        }
        long agora = relogio.millis();
        manutencao(agora);

        Registro registro = registros.get(chave);
        if (registro == null) {
            if (registros.size() >= chavesMaximas) {
                liberarEspaco(agora);
            }
            registro = new Registro();
            registros.put(chave, registro);
        }

        descartarAntigas(registro, agora);
        registro.falhas.addLast(agora);

        if (registro.falhas.size() >= maximo) {
            registro.bloqueadoAte = agora + bloqueioMs;
            registro.falhas.clear();
        }
    }

    /** Sucesso: esquece as falhas da chave (não encurta um bloqueio em vigor). */
    synchronized void registrarSucesso(String chave) {
        if (chave == null) {
            return;
        }
        Registro registro = registros.get(chave);
        if (registro != null && registro.bloqueadoAte <= relogio.millis()) {
            registros.remove(chave);
        }
    }

    synchronized int chavesAcompanhadas() {
        return registros.size();
    }

    /** Remove entradas vencidas (também chamada automaticamente). */
    synchronized void limpar() {
        limparVencidas(relogio.millis());
    }

    private void manutencao(long agora) {
        if (++operacoes >= OPERACOES_ENTRE_LIMPEZAS) {
            operacoes = 0;
            limparVencidas(agora);
        }
    }

    private void descartarAntigas(Registro registro, long agora) {
        while (!registro.falhas.isEmpty() && agora - registro.falhas.peekFirst() >= janelaMs) {
            registro.falhas.pollFirst();
        }
    }

    private void limparVencidas(long agora) {
        Iterator<Registro> it = registros.values().iterator();
        while (it.hasNext()) {
            Registro registro = it.next();
            descartarAntigas(registro, agora);
            if (registro.falhas.isEmpty() && registro.bloqueadoAte <= agora) {
                it.remove();
            }
        }
    }

    /** Sem espaço: remove vencidas e, se preciso, as chaves não bloqueadas. */
    private void liberarEspaco(long agora) {
        limparVencidas(agora);
        if (registros.size() < chavesMaximas) {
            return;
        }
        Iterator<Registro> it = registros.values().iterator();
        while (it.hasNext() && registros.size() >= chavesMaximas * 9 / 10) {
            if (it.next().bloqueadoAte <= agora) {
                it.remove();
            }
        }
    }
}
