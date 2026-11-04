package br.unicesumar.chat.client;

import java.awt.Dimension;

/** Elemento exibido no {@link PainelConversa}. */
interface ItemConversa {

    enum Alinhamento {
        ESQUERDA,
        DIREITA,
        LARGURA_TOTAL
    }

    Alinhamento alinhamento();

    /** Tamanho do item para a largura disponível na conversa. */
    Dimension tamanhoPara(int larguraDisponivel);
}
