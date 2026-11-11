package br.unicesumar.chat.model;

/** Tipo de um registro da conversa. */
public enum TipoMensagem {

    /** Mensagem escrita por um participante. */
    MENSAGEM,

    /** Aviso do servidor: um participante se conectou. */
    ENTRADA,

    /** Aviso do servidor: um participante se desconectou. */
    SAIDA,

    /** Resposta gerada por IA para um personagem (não é uma pessoa real). */
    IA
}
