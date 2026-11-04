
package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;

import java.util.List;

public interface MensagemRepository {

    Mensagem salvar(Mensagem mensagem);

    List<Mensagem> buscarUltimas(int quantidade);
}
