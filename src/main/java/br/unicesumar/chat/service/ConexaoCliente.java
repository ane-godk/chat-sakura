package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;

public interface ConexaoCliente {

    Usuario getUsuario();

    void enviar(Mensagem mensagem) throws Exception;
}