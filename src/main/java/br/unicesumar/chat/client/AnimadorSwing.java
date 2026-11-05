package br.unicesumar.chat.client;

import javax.swing.Timer;
import java.util.function.BooleanSupplier;

/**
 * Executa um passo de animação periodicamente na EDT, por meio de um
 * {@link javax.swing.Timer}, somente enquanto o passo pedir continuidade.
 * Parado, não consome CPU.
 */
final class AnimadorSwing {

    private final Timer timer;

    /** @param passo executado na EDT; devolve {@code true} para continuar */
    AnimadorSwing(int intervaloMs, BooleanSupplier passo) {
        timer = new Timer(intervaloMs, null);
        timer.setCoalesce(true);
        timer.addActionListener(e -> {
            if (!passo.getAsBoolean()) {
                timer.stop();
            }
        });
    }

    void iniciar() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    void parar() {
        timer.stop();
    }

    boolean rodando() {
        return timer.isRunning();
    }
}
