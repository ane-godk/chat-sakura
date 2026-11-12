package br.unicesumar.chat.ia;

import java.util.List;
import java.util.Objects;

/**
 * Gerador de respostas de personagens, independente do serviço concreto.
 * Chamado apenas pelo servidor, fora da EDT e fora dos bloqueios do chat.
 */
public interface ProvedorIA {

    /** Gera a resposta; nunca retorna texto vazio. */
    String gerar(PedidoIA pedido) throws FalhaIA;

    /**
     * Gera a resposta informando se ela foi cortada pelo limite de tokens
     * ({@code finish_reason=length} / {@code done_reason=length}).
     */
    default Resposta gerarResposta(PedidoIA pedido) throws FalhaIA {
        return new Resposta(gerar(pedido), false);
    }

    /** Descrição curta exibida ao usuário (ex.: "Qwen qwen-plus" ou "SIMULADO"). */
    String descricao();

    /** {@code true} para respostas simuladas (testes e contingência). */
    default boolean simulado() {
        return false;
    }

    /** Limites de contexto e de concorrência adequados a este provedor. */
    default ParametrosIA parametros() {
        return ParametrosIA.REMOTO;
    }

    /**
     * Carrega o modelo antes do primeiro uso, se o provedor oferecer isso e
     * estiver configurado para tanto. Opcional: chamado em segundo plano e
     * sem impedir a inicialização; falhas apenas são registradas.
     */
    default void aquecer() {
    }

    /** Papel de cada turno, no formato das APIs de chat. */
    enum Papel { USUARIO, ASSISTENTE }

    /** Para que serve o pedido: responder como o personagem ou resumir a conversa. */
    enum Finalidade { RESPOSTA, RESUMO }

    record Turno(Papel papel, String texto) {
        public Turno {
            Objects.requireNonNull(papel);
            Objects.requireNonNull(texto);
        }
    }

    /**
     * Instruções do personagem e histórico recente daquela conversa (o
     * último turno é a mensagem do usuário). Nunca contém credenciais.
     */
    record PedidoIA(String instrucoes, List<Turno> historico, int maximoTokens, Finalidade finalidade) {
        public PedidoIA {
            Objects.requireNonNull(instrucoes);
            Objects.requireNonNull(finalidade);
            historico = List.copyOf(historico);
        }

        public PedidoIA(String instrucoes, List<Turno> historico, int maximoTokens) {
            this(instrucoes, historico, maximoTokens, Finalidade.RESPOSTA);
        }
    }

    /** Texto gerado e se ele foi interrompido pelo limite de tamanho. */
    record Resposta(String texto, boolean truncada) {
        public Resposta {
            Objects.requireNonNull(texto);
        }
    }

    /** Falha com mensagem pública (sem chave, sem detalhes internos). */
    final class FalhaIA extends Exception {

        private static final long serialVersionUID = 1L;

        public enum Codigo {
            NAO_CONFIGURADO, AUTENTICACAO, LIMITE, TEMPO_ESGOTADO, INDISPONIVEL, RESPOSTA_INVALIDA
        }

        private final Codigo codigo;

        public FalhaIA(Codigo codigo, String mensagemPublica) {
            super(mensagemPublica, null, false, false);
            this.codigo = Objects.requireNonNull(codigo);
        }

        public Codigo getCodigo() {
            return codigo;
        }
    }
}
