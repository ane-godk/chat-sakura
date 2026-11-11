package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Conversa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Conversas privadas e grupos. Falhas internas: {@link IllegalStateException}. */
public interface ConversaRepository {

    /** Conversa privada do par (em qualquer ordem), se existir. */
    Optional<Long> buscarPrivada(long usuarioA, long usuarioB);

    /**
     * Cria a conversa privada do par com os dois participantes.
     *
     * @throws IllegalArgumentException se o par já tiver conversa (restrição UNIQUE)
     */
    long criarPrivada(long usuarioA, long usuarioB, LocalDateTime criadaEm);

    /** Cria o grupo: o criador como ADMIN e os convidados como MEMBRO. */
    long criarGrupo(String nome, long criadorId, Set<Long> convidados, LocalDateTime criadaEm);

    /** Conversa com os participantes (dados públicos). */
    Optional<Conversa> buscar(long conversaId);

    /** Conversas privadas e de grupo das quais o usuário participa. */
    List<Conversa> conversasDoUsuario(long usuarioId);

    boolean participa(long conversaId, long usuarioId);

    Set<Long> participantes(long conversaId);

    /** Avança (nunca recua) a última mensagem lida pelo participante. */
    void marcarLida(long conversaId, long usuarioId, long mensagemId);

    /** Mensagens de outros participantes após a última lida. */
    int naoLidas(long conversaId, long usuarioId);

    /**
     * Cria o personagem e a conversa de IA (somente o dono participa).
     *
     * @return id da conversa
     */
    default long criarConversaIA(long donoId, String nome, String personalidade, String contexto,
                                 LocalDateTime criadaEm) {
        throw new UnsupportedOperationException("Conversas de IA não suportadas.");
    }

    /** Personagem de uma conversa do tipo IA. */
    default Optional<br.unicesumar.chat.model.Personagem> personagemDaConversa(long conversaId) {
        return Optional.empty();
    }
}
