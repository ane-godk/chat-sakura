package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static br.unicesumar.chat.client.SomSakura.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras dos sons, sem dispositivo de áudio: o reprodutor é substituído
 * por um que apenas registra os pedidos.
 */
class NotificacoesSonorasTest {

    private static final class ReprodutorFalso implements ReprodutorSom {
        final List<SomSakura> tocados = new ArrayList<>();
        boolean fechado;

        @Override
        public void tocar(SomSakura som) {
            tocados.add(som);
        }

        @Override
        public void fechar() {
            fechado = true;
        }
    }

    private final ReprodutorFalso reprodutor = new ReprodutorFalso();
    private final AtomicLong agora = new AtomicLong(100_000);
    private final NotificacoesSonoras sons = new NotificacoesSonoras(reprodutor, agora::get);

    private static final String DE_BIA = "[04/11 20:40] Bia: Oi, Ana!";
    private static final String DE_ANA = "[04/11 20:40] Ana: Oi, Bia!";

    /** Conexão confirmada e histórico já exibido. */
    private void sessaoAposHistorico() {
        sons.conexaoConfirmada();
        reprodutor.tocados.clear();
    }

    @Test
    void conexaoConfirmadaDeveTocarUmaVez() {
        sons.conexaoConfirmada();
        assertEquals(List.of(CONECTADO), reprodutor.tocados);
    }

    @Test
    void falhaDeConexaoNaoDeveTocar() {
        // Recusa ou erro: a sessão termina sem ter sido confirmada.
        sons.sessaoEncerrada();
        sons.mensagemEnviada();
        sons.linhaRecebida(DE_BIA, "Ana", true);

        assertTrue(reprodutor.tocados.isEmpty());
    }

    @Test
    void desconexaoDeveTocarUmaUnicaVez() {
        sons.conexaoConfirmada();
        sons.sessaoEncerrada();   // botão Sair ou queda
        sons.sessaoEncerrada();   // chamada repetida

        assertEquals(List.of(CONECTADO, DESCONECTADO), reprodutor.tocados);
    }

    @Test
    void reconexaoDeveTocarNovamente() {
        sons.conexaoConfirmada();
        sons.sessaoEncerrada();
        sons.conexaoConfirmada();
        sons.sessaoEncerrada();

        assertEquals(List.of(CONECTADO, DESCONECTADO, CONECTADO, DESCONECTADO), reprodutor.tocados);
    }

    @Test
    void mensagemEnviadaDeveTocarUmaVezESemRepetirNoEco() {
        sessaoAposHistorico();

        sons.mensagemEnviada();
        sons.linhaRecebida(DE_ANA, "Ana", true);    // eco do servidor
        sons.linhaRecebida(DE_ANA, "ana", true);    // mesmo nome, outra caixa

        assertEquals(List.of(MENSAGEM_ENVIADA), reprodutor.tocados);
    }

    @Test
    void mensagemDeOutroParticipanteDeveTocar() {
        sessaoAposHistorico();

        sons.linhaRecebida(DE_BIA, "Ana", true);

        assertEquals(List.of(MENSAGEM_RECEBIDA), reprodutor.tocados);
    }

    @Test
    void historicoNaoDeveTocar() {
        sessaoAposHistorico();

        sons.linhaRecebida("SISTEMA: ===== HISTÓRICO RECENTE =====", "Ana", false);
        sons.linhaRecebida(DE_BIA, "Ana", false);
        sons.linhaRecebida("SISTEMA: [04/11 20:30] Caio se conectou", "Ana", false);

        assertTrue(reprodutor.tocados.isEmpty());
    }

    @Test
    void avisosSeparadoresESistemaNaoDevemTocar() {
        sessaoAposHistorico();

        sons.linhaRecebida("SISTEMA: ===== FIM DO HISTÓRICO =====", "Ana", true);
        sons.linhaRecebida("SISTEMA: [04/11 20:41] Caio se conectou", "Ana", true);
        sons.linhaRecebida("SISTEMA: [04/11 20:42] Caio se desconectou", "Ana", true);
        sons.linhaRecebida("SISTEMA: Falha ao enviar: Não existe conexão ativa.", "Ana", true);
        sons.linhaRecebida("linha inesperada", "Ana", true);

        assertTrue(reprodutor.tocados.isEmpty());
    }

    @Test
    void rajadaDeveRespeitarOIntervaloMinimo() {
        sessaoAposHistorico();

        for (int i = 0; i < 10; i++) {
            sons.linhaRecebida("[04/11 20:40] Bia: rajada " + i, "Ana", true);
            agora.addAndGet(100);   // 10 mensagens em 1 segundo
        }
        assertEquals(1, reprodutor.tocados.size());

        agora.addAndGet(NotificacoesSonoras.INTERVALO_RECEBIMENTO_MS);
        sons.linhaRecebida("[04/11 20:41] Caio: depois da pausa", "Ana", true);

        assertEquals(List.of(MENSAGEM_RECEBIDA, MENSAGEM_RECEBIDA), reprodutor.tocados);
    }

    @Test
    void silenciarDeveImpedirEReativarDevePermitir() {
        sons.definirSilenciado(true);
        assertTrue(sons.silenciado());

        sons.conexaoConfirmada();
        sons.mensagemEnviada();
        sons.linhaRecebida(DE_BIA, "Ana", true);
        sons.sessaoEncerrada();
        assertTrue(reprodutor.tocados.isEmpty());

        sons.definirSilenciado(false);
        sons.conexaoConfirmada();
        sons.linhaRecebida(DE_BIA, "Ana", true);

        assertEquals(List.of(CONECTADO, MENSAGEM_RECEBIDA), reprodutor.tocados);
    }

    @Test
    void falhaDeAudioNaoDeveDerrubarOChat() {
        NotificacoesSonoras comFalha = new NotificacoesSonoras(new ReprodutorSom() {
            @Override
            public void tocar(SomSakura som) {
                throw new IllegalStateException("sem dispositivo de áudio");
            }

            @Override
            public void fechar() {
                throw new IllegalStateException("falha ao fechar");
            }
        }, agora::get);

        assertDoesNotThrow(() -> {
            comFalha.conexaoConfirmada();
            comFalha.mensagemEnviada();
            comFalha.linhaRecebida(DE_BIA, "Ana", true);
            comFalha.sessaoEncerrada();
            comFalha.fechar();
        });
    }

    @Test
    void fecharDeveLiberarOReprodutor() {
        sons.fechar();
        assertTrue(reprodutor.fechado);
    }

    // --- Reprodutor real e arquivos ---

    @Test
    void reprodutorRealNaoDeveFalharComArquivoAusenteNemSemDispositivo() {
        ReprodutorJavaSound real = new ReprodutorJavaSound(som -> "/sakura/sons/inexistente.wav");

        assertDoesNotThrow(() -> real.tocar(CONECTADO));
        assertFalse(real.disponivel(CONECTADO));
        assertDoesNotThrow(real::fechar);
        assertDoesNotThrow(() -> real.tocar(CONECTADO), "Pedidos após fechar são ignorados.");
    }

    @Test
    void arquivosAprovadosDevemTerOFormatoEsperado() throws Exception {
        for (SomSakura som : SomSakura.values()) {
            InputStream recurso = getClass().getResourceAsStream(som.recurso());
            assertNotNull(recurso, som.recurso());

            try (AudioInputStream audio = AudioSystem.getAudioInputStream(
                    new BufferedInputStream(recurso))) {
                AudioFormat formato = audio.getFormat();

                assertEquals(AudioFormat.Encoding.PCM_SIGNED, formato.getEncoding(), som.name());
                assertEquals(44_100f, formato.getSampleRate(), som.name());
                assertEquals(16, formato.getSampleSizeInBits(), som.name());
                assertEquals(1, formato.getChannels(), som.name());
                assertEquals(15_435, audio.getFrameLength(), som + ": 350 ms");
            }
        }
    }
}
