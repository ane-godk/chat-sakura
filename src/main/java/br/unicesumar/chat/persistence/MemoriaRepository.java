package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.MemoriaPersonagem;

import java.time.LocalDateTime;
import java.util.Optional;

/** Memória de personagens, sempre por conversa (nunca compartilhada entre contas). */
public interface MemoriaRepository {

    Optional<MemoriaPersonagem> buscar(long conversaId);

    /**
     * Grava o resumo. Só tem efeito se a memória não tiver sido apagada desde
     * que o resumo começou a ser feito ({@code esquecerAteEsperado} igual ao
     * atual), para que um resumo atrasado não ressuscite memória apagada.
     *
     * @return {@code true} se gravou
     */
    boolean salvarResumo(long conversaId, long donoId, long personagemId, String resumo,
                         long ateMensagemId, long esquecerAteEsperado, LocalDateTime quando);

    /**
     * Apaga o resumo e passa a ignorar, no contexto do modelo, as mensagens
     * até {@code ultimaMensagemId}. O histórico da conversa não é alterado.
     */
    void apagar(long conversaId, long donoId, long personagemId, long ultimaMensagemId, LocalDateTime quando);
}
