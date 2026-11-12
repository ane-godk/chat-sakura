package br.unicesumar.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio de teste que só anda quando mandado (testes determinísticos). */
final class RelogioAjustavel extends Clock {

    private volatile Instant agora;

    RelogioAjustavel(Instant inicio) {
        this.agora = inicio;
    }

    void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
