
package br.unicesumar.chat.domain;

import java.util.List;

public interface MensagemRepository {

    void salvar(Mensagem mensagem);

    List<Mensagem> buscarUltimas(int quantidade);
}
