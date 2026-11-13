package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.MensagemFixada;

import java.time.LocalDateTime;
import java.util.List;

/** Mensagens fixadas por conversa. */
public interface FixacaoRepository {

    /**
     * Fixa a mensagem se ela pertencer à conversa, for uma mensagem escrita
     * (não um aviso de presença) e a conversa tiver menos de {@code limite}
     * fixações. A verificação e a gravação são atômicas.
     */
    MensagemFixada.Resultado fixar(long conversaId, long mensagemId, long usuarioId, LocalDateTime quando,
                                   int limite);

    /** @return {@code false} se a mensagem não estava fixada nesta conversa */
    boolean desafixar(long conversaId, long mensagemId);

    /** Fixações da conversa, da mais antiga para a mais recente. */
    List<MensagemFixada> listar(long conversaId);
}
