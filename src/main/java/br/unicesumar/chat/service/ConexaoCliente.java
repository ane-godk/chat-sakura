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

    /** Perfil público alterado (avatar). Somente sessões v2. */
    default void perfilAtualizado(br.unicesumar.chat.model.PerfilUsuario perfil) throws Exception {
    }

    /** Estado da geração de resposta da IA (GERANDO, CONCLUIDO ou ERRO). Somente sessões v2. */
    default void estadoIA(long conversaId, String estado, String mensagem) throws Exception {
    }

    /**
     * A sessão foi substituída por um novo login da mesma conta e já saiu do
     * registro: deve avisar o cliente e fechar a conexão. Chamado sob os
     * bloqueios do chat, portanto não pode bloquear nem fazer I/O direto.
     */
    default void encerrar(String motivo) {
    }
}
