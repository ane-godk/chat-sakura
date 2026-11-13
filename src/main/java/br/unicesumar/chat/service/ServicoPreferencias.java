package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.AparenciaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Preferências de aparência por conta. A conta vem sempre da sessão
 * autenticada: ninguém lê nem altera as preferências de outra pessoa.
 */
public final class ServicoPreferencias {

    private final AparenciaRepository repositorio;
    private final Clock relogio;

    public ServicoPreferencias(AparenciaRepository repositorio, Clock relogio) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.relogio = Objects.requireNonNull(relogio);
    }

    /** Aparência da conta; padrão se nunca escolhida ou se o banco falhar (nunca impede o login). */
    public Aparencia aparencia(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return Aparencia.PADRAO;
        }
        try {
            return repositorio.buscar(usuario.getId()).orElse(Aparencia.PADRAO);
        } catch (RuntimeException e) {
            System.err.println("Chat Sakura (preferências): aparência indisponível; usando o padrão ("
                    + e.getClass().getSimpleName() + ").");
            return Aparencia.PADRAO;
        }
    }

    /** Grava a aparência da própria conta. */
    public Aparencia definir(Usuario usuario, Aparencia aparencia) throws ConversaException {
        if (usuario == null || usuario.getId() == null) {
            throw new ConversaException(ConversaException.Codigo.CONTA_NECESSARIA,
                    "Entre com uma conta para salvar a aparência.");
        }
        Objects.requireNonNull(aparencia);
        try {
            repositorio.salvar(usuario.getId(), aparencia, LocalDateTime.now(relogio));
            return aparencia;
        } catch (RuntimeException e) {
            System.err.println("Chat Sakura (preferências): falha ao gravar a aparência ("
                    + e.getClass().getSimpleName() + ").");
            throw new ConversaException(ConversaException.Codigo.INDISPONIVEL,
                    "Não foi possível salvar a aparência agora. Ela vale só nesta sessão.");
        }
    }
}
