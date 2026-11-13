
package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.FiltroPesquisa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TextoBusca;
import br.unicesumar.chat.model.TipoMensagem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MensagemRepositoryEmMemoria
        implements MensagemRepository {

    private final List<Mensagem> mensagens = new ArrayList<>();

    private long proximoId = 1L;

    @Override
    public synchronized Mensagem salvar(Mensagem mensagem) {

        Objects.requireNonNull(
                mensagem,
                "A mensagem é obrigatória."
        );

        if (mensagem.getId() != null) {
            throw new IllegalArgumentException(
                    "A mensagem já possui um identificador."
            );
        }

        Mensagem persistida = mensagem.comId(proximoId++);

        mensagens.add(persistida);

        return persistida;
    }

    @Override
    public synchronized List<Mensagem> buscarUltimas(int quantidade) {
        return buscarHistorico(Conversa.ID_GERAL, null, quantidade);
    }

    @Override
    public synchronized List<Mensagem> buscarHistorico(long conversaId, Long antesDeId, int quantidade) {

        if (quantidade < 0) {
            throw new IllegalArgumentException(
                    "A quantidade não pode ser negativa."
            );
        }

        List<Mensagem> daConversa = mensagens.stream()
                .filter(m -> m.getConversaId() == conversaId)
                .filter(m -> antesDeId == null || m.getId() < antesDeId)
                .toList();

        int inicio = Math.max(
                0,
                daConversa.size() - quantidade
        );

        return new ArrayList<>(
                daConversa.subList(inicio, daConversa.size())
        );
    }

    @Override
    public synchronized List<Mensagem> pesquisar(FiltroPesquisa filtro) {
        List<Mensagem> encontradas = new ArrayList<>();
        for (int i = mensagens.size() - 1; i >= 0 && encontradas.size() < filtro.limite(); i--) {
            Mensagem m = mensagens.get(i);
            if (m.getConversaId() == filtro.conversaId()
                    && (filtro.antesDeId() == null || m.getId() < filtro.antesDeId())
                    && (m.getTipo() == TipoMensagem.MENSAGEM || m.getTipo() == TipoMensagem.IA)
                    && TextoBusca.normalizar(m.getConteudo()).contains(filtro.termo())
                    && (filtro.remetenteId() == null || filtro.remetenteId().equals(m.getRemetente().getId()))
                    && (filtro.inicio() == null || !m.getHorario().isBefore(filtro.inicio()))
                    && (filtro.fimExclusivo() == null || m.getHorario().isBefore(filtro.fimExclusivo()))) {
                encontradas.add(m);
            }
        }
        return encontradas;
    }
}
