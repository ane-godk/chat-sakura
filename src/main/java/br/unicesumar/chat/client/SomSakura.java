package br.unicesumar.chat.client;

/** Sons de notificação do Chat Sakura (WAV PCM 16 bits, 44,1 kHz, mono). */
enum SomSakura {

    CONECTADO("/sakura/sons/conectado.wav"),
    DESCONECTADO("/sakura/sons/desconectado.wav"),
    MENSAGEM_ENVIADA("/sakura/sons/mensagem-enviada.wav"),
    MENSAGEM_RECEBIDA("/sakura/sons/mensagem-recebida.wav");

    private final String recurso;

    SomSakura(String recurso) {
        this.recurso = recurso;
    }

    String recurso() {
        return recurso;
    }
}
