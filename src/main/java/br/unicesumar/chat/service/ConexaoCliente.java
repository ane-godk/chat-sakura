package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;

public interface ConexaoCliente {

    Usuario getUsuario();

    void enviar(Mensagem mensagem) throws Exception;

    /**
     * Uma conversa da qual o usuário participa foi criada por outra pessoa.
     * Somente sessões do protocolo v2 a recebem; as demais ignoram.
     */
    default void conversaDisponivel(ResumoConversa resumo) throws Exception {
    }
}
