
package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;

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

        if (quantidade < 0) {
            throw new IllegalArgumentException(
                    "A quantidade não pode ser negativa."
            );
        }

        int inicio = Math.max(
                0,
                mensagens.size() - quantidade
        );

        return new ArrayList<>(
                mensagens.subList(inicio, mensagens.size())
        );
    }
}
