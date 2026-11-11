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

    /** Descrição curta exibida ao usuário (ex.: "Qwen qwen-plus" ou "SIMULADO"). */
    String descricao();

    /** {@code true} para respostas simuladas (testes e contingência). */
    default boolean simulado() {
        return false;
    }

    /** Papel de cada turno, no formato das APIs de chat. */
    enum Papel { USUARIO, ASSISTENTE }

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
    record PedidoIA(String instrucoes, List<Turno> historico, int maximoTokens) {
        public PedidoIA {
            Objects.requireNonNull(instrucoes);
            historico = List.copyOf(historico);
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
