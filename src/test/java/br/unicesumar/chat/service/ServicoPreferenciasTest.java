package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Aparencia;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.AparenciaRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Regras do serviço de preferências, com repositório em memória (sem banco real). */
class ServicoPreferenciasTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"));
    private static final Usuario ANA = new Usuario(1L, "Ana");
    private static final Usuario BIA = new Usuario(2L, "Bia");
    private static final Aparencia FUJI = new Aparencia(Aparencia.Paleta.FUJI, Aparencia.Modo.ESCURO,
            Aparencia.Fundo.FLORAL);

    private static final class EmMemoria implements AparenciaRepository {
        final Map<Long, Aparencia> linhas = new HashMap<>();
        LocalDateTime ultimaGravacao;
        boolean falhar;

        @Override
        public Optional<Aparencia> buscar(long usuarioId) {
            if (falhar) {
                throw new IllegalStateException("banco fora");
            }
            return Optional.ofNullable(linhas.get(usuarioId));
        }

        @Override
        public void salvar(long usuarioId, Aparencia aparencia, LocalDateTime quando) {
            if (falhar) {
                throw new IllegalStateException("banco fora");
            }
            linhas.put(usuarioId, aparencia);
            ultimaGravacao = quando;
        }
    }

    @Test
    void contaSemEscolhaUsaOPadrao() {
        assertEquals(Aparencia.PADRAO, new ServicoPreferencias(new EmMemoria(), RELOGIO).aparencia(ANA));
    }

    @Test
    void gravaPorContaSemMisturar() throws Exception {
        EmMemoria repositorio = new EmMemoria();
        ServicoPreferencias servico = new ServicoPreferencias(repositorio, RELOGIO);

        assertEquals(FUJI, servico.definir(ANA, FUJI));
        assertEquals(FUJI, servico.aparencia(ANA));
        assertEquals(Aparencia.PADRAO, servico.aparencia(BIA));
        assertEquals(LocalDateTime.of(2026, 11, 13, 12, 0), repositorio.ultimaGravacao);
    }

    @Test
    void visitanteNaoGravaEVeOPadrao() {
        ServicoPreferencias servico = new ServicoPreferencias(new EmMemoria(), RELOGIO);
        Usuario visitante = new Usuario("Visitante");

        assertEquals(Aparencia.PADRAO, servico.aparencia(visitante));
        assertEquals(Aparencia.PADRAO, servico.aparencia(null));
        ConversaException erro = assertThrows(ConversaException.class, () -> servico.definir(visitante, FUJI));
        assertEquals(ConversaException.Codigo.CONTA_NECESSARIA, erro.getCodigo());
    }

    @Test
    void falhaNoBancoNaoImpedeOLoginENemVazaDetalhes() {
        EmMemoria repositorio = new EmMemoria();
        repositorio.falhar = true;
        ServicoPreferencias servico = new ServicoPreferencias(repositorio, RELOGIO);

        assertEquals(Aparencia.PADRAO, servico.aparencia(ANA));
        ConversaException erro = assertThrows(ConversaException.class, () -> servico.definir(ANA, FUJI));
        assertEquals(ConversaException.Codigo.INDISPONIVEL, erro.getCodigo());
        assertFalse(erro.getMessage().contains("banco fora"));
    }
}
