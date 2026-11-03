
package br.unicesumar.chat.persistence;

import br.unicesumar.chat.domain.Mensagem;
import br.unicesumar.chat.domain.MensagemRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MensagemRepositoryEmMemoria
        implements MensagemRepository {

    private final List<Mensagem> mensagens =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void salvar(Mensagem mensagem) {

        if (mensagem == null) {
            throw new IllegalArgumentException(
                    "A mensagem não pode ser nula."
            );
        }

        mensagens.add(mensagem);
    }

    @Override
    public List<Mensagem> buscarUltimas(int quantidade) {

        if (quantidade < 0) {
            throw new IllegalArgumentException(
                    "A quantidade não pode ser negativa."
            );
        }

        synchronized (mensagens) {

            int total = mensagens.size();

            int inicio = Math.max(
                    0,
                    total - quantidade
            );

            return new ArrayList<>(
                    mensagens.subList(inicio, total)
            );
        }
    }

    public int contarMensagens() {

        synchronized (mensagens) {
            return mensagens.size();
        }
    }

    public void limpar() {

        synchronized (mensagens) {
            mensagens.clear();
        }
    }
}
