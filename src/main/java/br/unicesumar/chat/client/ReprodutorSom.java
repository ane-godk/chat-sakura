package br.unicesumar.chat.client;

/** Reprodução dos sons; implementações não devem bloquear quem chama. */
interface ReprodutorSom {

    void tocar(SomSakura som);

    /** Libera os recursos de áudio. */
    void fechar();
}
