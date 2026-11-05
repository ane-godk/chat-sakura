package br.unicesumar.chat.client;

import java.util.function.LongSupplier;

/**
 * Regras de quando tocar cada som (sem dependência de áudio).
 *
 * - Conectado: somente após a confirmação do servidor.
 * - Desconectado: uma única vez por sessão confirmada (Sair ou queda).
 * - Mensagem enviada: após a escrita bem-sucedida no socket; o eco do
 *   servidor não toca de novo.
 * - Mensagem recebida: somente mensagens novas de outros participantes,
 *   após o histórico, com intervalo mínimo entre notificações em rajadas.
 * - Histórico, separadores, avisos de presença e linhas do sistema: nunca.
 *
 * Métodos sincronizados: chamados pela thread de rede e pela EDT.
 * Falhas do reprodutor nunca se propagam.
 */
final class NotificacoesSonoras {

    /** Intervalo mínimo entre sons de mensagem recebida. */
    static final long INTERVALO_RECEBIMENTO_MS = 1200;

    static final String PROPRIEDADE = "chat.sakura.sons";

    private final ReprodutorSom reprodutor;
    private final LongSupplier relogio;

    private boolean silenciado;
    private boolean sessaoAtiva;
    private long ultimoRecebimento = Long.MIN_VALUE;

    NotificacoesSonoras(ReprodutorSom reprodutor, LongSupplier relogio) {
        this.reprodutor = reprodutor;
        this.relogio = relogio;
        String valor = System.getProperty(PROPRIEDADE);
        this.silenciado = valor != null
                && (valor.equalsIgnoreCase("silenciados") || valor.equalsIgnoreCase("false")
                || valor.equalsIgnoreCase("off"));
    }

    synchronized boolean silenciado() {
        return silenciado;
    }

    synchronized void definirSilenciado(boolean valor) {
        this.silenciado = valor;
    }

    synchronized void conexaoConfirmada() {
        sessaoAtiva = true;
        ultimoRecebimento = Long.MIN_VALUE;
        tocar(SomSakura.CONECTADO);
    }

    /** Fim da sessão; toca somente se havia uma sessão confirmada. */
    synchronized void sessaoEncerrada() {
        if (sessaoAtiva) {
            sessaoAtiva = false;
            tocar(SomSakura.DESCONECTADO);
        }
    }

    synchronized void mensagemEnviada() {
        if (sessaoAtiva) {
            tocar(SomSakura.MENSAGEM_ENVIADA);
        }
    }

    /**
     * Linha exibida na conversa.
     *
     * @param historicoConcluido {@code true} somente após o separador de
     *                           fim do histórico da sessão atual
     */
    synchronized void linhaRecebida(
            String linha,
            String usuarioAtual,
            boolean historicoConcluido
    ) {

        if (!sessaoAtiva || !historicoConcluido) {
            return;
        }

        FormatoLinhaChat.Linha analisada = FormatoLinhaChat.analisar(linha);

        if (analisada.tipo() != FormatoLinhaChat.Tipo.MENSAGEM
                || analisada.enviadaPor(usuarioAtual)) {
            return;
        }

        long agora = relogio.getAsLong();

        if (ultimoRecebimento != Long.MIN_VALUE
                && agora - ultimoRecebimento < INTERVALO_RECEBIMENTO_MS) {
            return;   // rajada: a mensagem aparece, o som não se repete
        }

        ultimoRecebimento = agora;
        tocar(SomSakura.MENSAGEM_RECEBIDA);
    }

    synchronized void fechar() {
        try {
            reprodutor.fechar();
        } catch (RuntimeException e) {
            System.err.println("Chat Sakura (sons): falha ao liberar o áudio: " + e.getMessage());
        }
    }

    private void tocar(SomSakura som) {
        if (silenciado) {
            return;
        }
        try {
            reprodutor.tocar(som);
        } catch (RuntimeException | LinkageError e) {
            System.err.println("Chat Sakura (sons): falha ao tocar " + som + ": " + e.getMessage());
        }
    }
}
