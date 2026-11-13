package br.unicesumar.chat.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pesquisa de mensagens numa conversa.
 *
 * @param termo      já na forma de pesquisa ({@link TextoBusca#termo(String)})
 * @param remetenteId somente mensagens desta conta (opcional)
 * @param desde      data inicial, inclusive (opcional)
 * @param ate        data final, inclusive (opcional)
 * @param antesDeId  paginação: somente mensagens com id menor (opcional)
 * @param limite     quantidade máxima consultada (a página pedida e, no
 *                   máximo, um item a mais, que só indica se há outra página)
 */
public record FiltroPesquisa(long conversaId, String termo, Long remetenteId, LocalDate desde, LocalDate ate,
                             Long antesDeId, int limite) {

    public static final int LIMITE_PADRAO = 20;
    public static final int LIMITE_MAXIMO = 50;

    public FiltroPesquisa {
        Objects.requireNonNull(termo, "Termo obrigatório.");
        if (limite < 1 || limite > LIMITE_MAXIMO + 1) {
            throw new IllegalArgumentException("Quantidade de resultados inválida.");
        }
        if (desde != null && ate != null && desde.isAfter(ate)) {
            throw new IllegalArgumentException("A data inicial deve ser anterior à final.");
        }
    }

    /** Início do período (inclusive), ou nulo. */
    public LocalDateTime inicio() {
        return desde == null ? null : desde.atStartOfDay();
    }

    /** Fim do período (exclusivo: o dia seguinte à data final), ou nulo. */
    public LocalDateTime fimExclusivo() {
        return ate == null ? null : ate.plusDays(1).atStartOfDay();
    }
}
