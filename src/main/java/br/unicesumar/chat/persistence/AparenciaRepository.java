package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Aparencia;

import java.time.LocalDateTime;
import java.util.Optional;

/** Preferências de aparência por conta (uma linha por conta). */
public interface AparenciaRepository {

    /** Aparência gravada da conta; valores antigos ou inválidos viram o padrão do campo. */
    Optional<Aparencia> buscar(long usuarioId);

    /** Grava (ou substitui) a aparência da conta. */
    void salvar(long usuarioId, Aparencia aparencia, LocalDateTime quando);
}
