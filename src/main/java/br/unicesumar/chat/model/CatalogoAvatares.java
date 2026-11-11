package br.unicesumar.chat.model;

import java.util.List;

/**
 * Catálogo fechado de avatares de perfil. O servidor só aceita estes
 * identificadores: não há upload nem caminhos de arquivo. O cliente
 * desenha cada avatar com Java2D a partir do identificador.
 */
public final class CatalogoAvatares {

    public static final String PADRAO = "sakura";

    /** Avatar escolhível: identificador estável e nome exibido. */
    public record Avatar(String id, String nome) {
    }

    public static final List<Avatar> AVATARES = List.of(
            new Avatar("sakura", "Flor de cerejeira"),
            new Avatar("lua", "Lua crescente"),
            new Avatar("gato", "Gatinho"),
            new Avatar("estrela", "Estrela"),
            new Avatar("onda", "Onda"),
            new Avatar("cha", "Xícara de chá")
    );

    private CatalogoAvatares() {
    }

    public static boolean valido(String id) {
        return id != null && AVATARES.stream().anyMatch(a -> a.id().equals(id));
    }

    /** Identificador válido, ou o padrão para valores desconhecidos (dados antigos). */
    public static String normalizar(String id) {
        return valido(id) ? id : PADRAO;
    }
}
