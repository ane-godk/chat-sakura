
package br.unicesumar.chat.model;

/** Tipo de um registro da conversa. */
public enum TipoMensagem {

    /** Mensagem escrita por um participante. */
    MENSAGEM,

    /** Aviso do servidor: um participante se conectou. */
    ENTRADA,

    /** Aviso do servidor: um participante se desconectou. */
    SAIDA
}
