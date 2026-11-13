package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;

import java.util.List;

public interface MensagemRepository {

    Mensagem salvar(Mensagem mensagem);

    /** Últimas mensagens do Chat Geral, em ordem cronológica. */
    List<Mensagem> buscarUltimas(int quantidade);

    /**
     * Mensagens de uma conversa em ordem cronológica: as {@code quantidade}
     * mais recentes com id menor que {@code antesDeId} (ou as últimas, se nulo).
     */
    default List<Mensagem> buscarHistorico(long conversaId, Long antesDeId, int quantidade) {
        throw new UnsupportedOperationException("Histórico por conversa não suportado.");
    }

    /**
     * Mensagens escritas (participantes e IA, nunca avisos de presença) que
     * contêm o termo, da mais recente para a mais antiga, até {@code limite}.
     */
    default List<Mensagem> pesquisar(br.unicesumar.chat.model.FiltroPesquisa filtro) {
        throw new UnsupportedOperationException("Pesquisa não suportada.");
    }
}
