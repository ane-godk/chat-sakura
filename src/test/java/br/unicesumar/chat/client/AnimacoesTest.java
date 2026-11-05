package br.unicesumar.chat.client;

import br.unicesumar.chat.client.ConfiguracaoAnimacoes.Modo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras das animações: ativação, pausa, desativação e rajadas.
 * O tempo é controlado pelo teste; nada depende de timers reais.
 */
class AnimacoesTest {

    @AfterEach
    void restaurarModo() {
        ConfiguracaoAnimacoes.definirModo(Modo.COMPLETAS);
    }

    private static void naEdt(Runnable acao) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                acao.run();
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw new AssertionError(erro.get());
        }
    }

    // --- Configuração ---

    @Test
    void deveInterpretarAPropriedadeDeInicializacao() {

        assertEquals(Modo.COMPLETAS, ConfiguracaoAnimacoes.interpretar(null));
        assertEquals(Modo.COMPLETAS, ConfiguracaoAnimacoes.interpretar("completas"));
        assertEquals(Modo.REDUZIDAS, ConfiguracaoAnimacoes.interpretar(" Reduzidas "));
        assertEquals(Modo.DESATIVADAS, ConfiguracaoAnimacoes.interpretar("desativadas"));
        assertEquals(Modo.DESATIVADAS, ConfiguracaoAnimacoes.interpretar("false"));
        assertEquals(Modo.COMPLETAS, ConfiguracaoAnimacoes.interpretar("qualquer"));
    }

    @Test
    void cadaModoDeveLiberarApenasAsAnimacoesCorrespondentes() {

        ConfiguracaoAnimacoes.definirModo(Modo.COMPLETAS);
        assertTrue(ConfiguracaoAnimacoes.transicoesAtivas());
        assertTrue(ConfiguracaoAnimacoes.petalasAtivas());

        ConfiguracaoAnimacoes.definirModo(Modo.REDUZIDAS);
        assertTrue(ConfiguracaoAnimacoes.transicoesAtivas());
        assertFalse(ConfiguracaoAnimacoes.petalasAtivas());

        ConfiguracaoAnimacoes.definirModo(Modo.DESATIVADAS);
        assertFalse(ConfiguracaoAnimacoes.transicoesAtivas());
        assertFalse(ConfiguracaoAnimacoes.petalasAtivas());
    }

    @Test
    void mudancaDeModoDeveAvisarOsOuvintes() {

        AtomicInteger avisos = new AtomicInteger();
        Runnable ouvinte = avisos::incrementAndGet;

        ConfiguracaoAnimacoes.aoMudar(ouvinte);
        ConfiguracaoAnimacoes.definirModo(Modo.REDUZIDAS);
        ConfiguracaoAnimacoes.removerOuvinte(ouvinte);
        ConfiguracaoAnimacoes.definirModo(Modo.COMPLETAS);

        assertEquals(1, avisos.get());
    }

    // --- Pausa das pétalas ---

    @Test
    void petalasSoDevemRodarNoModoCompletoComJanelaAtivaEVisivel() {

        assertTrue(FundoConversa.petalasDevemRodar(Modo.COMPLETAS, true, true));

        assertFalse(FundoConversa.petalasDevemRodar(Modo.COMPLETAS, false, true), "janela inativa/minimizada");
        assertFalse(FundoConversa.petalasDevemRodar(Modo.COMPLETAS, true, false), "janela invisível");
        assertFalse(FundoConversa.petalasDevemRodar(Modo.REDUZIDAS, true, true));
        assertFalse(FundoConversa.petalasDevemRodar(Modo.DESATIVADAS, true, true));
    }

    @Test
    void fundoForaDaTelaNaoDeveIniciarOTimerDasPetalas() throws Exception {

        naEdt(() -> {
            FundoConversa fundo = new FundoConversa(new java.awt.BorderLayout());
            fundo.definirJanelaAtiva(true);
            assertFalse(fundo.petalasRodando(), "Sem estar na tela, não há animação.");
        });
    }

    // --- Transições ---

    @Test
    void transicaoDeveAvancarSuavementeEConcluirNoTempo() {

        Transicao t = new Transicao(0, 100, () -> true);
        t.irPara(1, 1000);

        assertEquals(0, t.valor(1000), 1e-9);
        assertEquals(0.5, t.valor(1050), 1e-9);
        assertTrue(t.valor(1025) < 0.25, "Começa devagar.");
        assertTrue(t.emAndamento(1099));
        assertEquals(1, t.valor(1100), 1e-9);
        assertFalse(t.emAndamento(1100));
    }

    @Test
    void transicaoInterrompidaDeveContinuarDoValorAtualSemSaltos() {

        Transicao t = new Transicao(0, 100, () -> true);
        t.irPara(1, 0);
        double meio = t.valor(50);

        t.irPara(0, 50);

        assertEquals(meio, t.valor(50), 1e-9);
        assertEquals(0, t.valor(150), 1e-9);
    }

    @Test
    void comAnimacoesDesativadasATransicaoDeveSerImediata() {

        Transicao t = new Transicao(0, 100, () -> false);
        t.irPara(1, 0);

        assertEquals(1, t.valor(0), 1e-9);
        assertFalse(t.emAndamento(0));
    }

    // --- Rajadas ---

    @Test
    void limitadorDevePermitirPoucasEntradasPorJanelaDeTempo() {

        LimitadorRajada limitador = new LimitadorRajada(3, 600);

        assertTrue(limitador.permitir(0));
        assertTrue(limitador.permitir(10));
        assertTrue(limitador.permitir(20));
        assertFalse(limitador.permitir(30));
        assertFalse(limitador.permitir(500));
        assertTrue(limitador.permitir(600));
    }

    // --- Pétalas ---

    @Test
    void petalasDevemPermanecerNaAreaEReaparecerNoTopo() {

        CampoPetalas campo = new CampoPetalas(6, 42);
        campo.ajustarArea(700, 300);

        assertEquals(6, campo.petalas().size());

        for (int passo = 0; passo < 600; passo++) {
            campo.avancar(100);   // 60 segundos de queda

            for (CampoPetalas.Petala p : campo.petalas()) {
                assertTrue(p.x() > -40 && p.x() < 740, "x fora da área: " + p.x());
                assertTrue(p.y() >= -20 && p.y() <= 320, "y fora da área: " + p.y());
            }
        }
    }

    @Test
    void campoComMesmaSementeDeveSerReproduzivel() {

        CampoPetalas a = new CampoPetalas(4, 7);
        CampoPetalas b = new CampoPetalas(4, 7);
        a.ajustarArea(500, 200);
        b.ajustarArea(500, 200);
        a.avancar(1234);
        b.avancar(1234);

        assertEquals(a.petalas(), b.petalas());
    }

    // --- Entrada das mensagens ---

    private static final List<String> HISTORICO = List.of(
            "SISTEMA: Bem-vindo ao Chat Sakura!",
            "SISTEMA: Conectado como Ana",
            "SISTEMA: ===== HISTÓRICO RECENTE =====",
            "[04/11 20:30] Bia: mensagem antiga",
            "SISTEMA: [04/11 20:31] Caio se conectou",
            "SISTEMA: ===== FIM DO HISTÓRICO ====="
    );

    private static PainelConversa painelComRelogio(AtomicLong agora) {
        PainelConversa painel = new PainelConversa();
        painel.definirRelogio(agora::get);
        painel.definirUsuarioAtual("Ana");
        return painel;
    }

    @Test
    void historicoDeveAparecerImediatamenteEMensagensNovasComAnimacao() throws Exception {

        naEdt(() -> {
            AtomicLong agora = new AtomicLong(10_000);
            PainelConversa painel = painelComRelogio(agora);

            HISTORICO.forEach(painel::adicionarLinha);
            for (Component item : painel.getComponents()) {
                assertFalse(painel.animandoEntrada(item), "Histórico não é animado.");
            }
            assertTrue(painel.historicoConcluido());

            agora.addAndGet(1000);
            painel.adicionarLinha("[04/11 20:40] Bia: nova");
            painel.adicionarLinha("SISTEMA: [04/11 20:41] Caio se desconectou");
            painel.adicionarLinha("SISTEMA: Falha ao enviar: teste");

            Component[] itens = painel.getComponents();
            assertTrue(painel.animandoEntrada(itens[6]), "Balão novo animado.");
            assertTrue(painel.animandoEntrada(itens[7]), "Aviso novo animado.");
            assertFalse(painel.animandoEntrada(itens[8]), "Linha do sistema sem animação.");

            assertEquals(0, painel.progressoEntrada(itens[6], agora.get()), 1e-9);
            assertEquals(1, painel.progressoEntrada(itens[6],
                    agora.get() + PainelConversa.DURACAO_ENTRADA_MS), 1e-9);
        });
    }

    @Test
    void rajadaDeveAnimarApenasAsPrimeirasMensagens() throws Exception {

        naEdt(() -> {
            AtomicLong agora = new AtomicLong(0);
            PainelConversa painel = painelComRelogio(agora);
            HISTORICO.forEach(painel::adicionarLinha);

            List<Component> novos = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                agora.addAndGet(20);
                painel.adicionarLinha("[04/11 20:40] Bia: rajada " + i);
                novos.add(painel.getComponent(painel.getComponentCount() - 1));
            }

            long animados = novos.stream().filter(painel::animandoEntrada).count();
            assertEquals(PainelConversa.MAXIMO_ANIMADAS, animados);
        });
    }

    @Test
    void comAnimacoesDesativadasNadaDeveSerAnimado() throws Exception {

        ConfiguracaoAnimacoes.definirModo(Modo.DESATIVADAS);

        naEdt(() -> {
            PainelConversa painel = painelComRelogio(new AtomicLong(0));
            HISTORICO.forEach(painel::adicionarLinha);
            painel.adicionarLinha("[04/11 20:40] Bia: nova");

            Component ultimo = painel.getComponent(painel.getComponentCount() - 1);
            assertFalse(painel.animandoEntrada(ultimo));
        });
    }

    @Test
    void limparDeveReiniciarAFronteiraDoHistorico() throws Exception {

        naEdt(() -> {
            PainelConversa painel = painelComRelogio(new AtomicLong(0));
            HISTORICO.forEach(painel::adicionarLinha);
            assertTrue(painel.historicoConcluido());

            painel.limpar();
            assertFalse(painel.historicoConcluido());

            // Nova conexão: o histórico recebido de novo não é animado.
            painel.adicionarLinha("SISTEMA: ===== HISTÓRICO RECENTE =====");
            painel.adicionarLinha("[04/11 20:30] Bia: mensagem antiga");
            assertFalse(painel.animandoEntrada(painel.getComponent(1)));
        });
    }

    @Test
    void animacaoNaoDeveAlterarLayoutNemTextoExportado() throws Exception {

        naEdt(() -> {
            List<String> linhas = new ArrayList<>(HISTORICO);
            linhas.add("[04/11 20:40] Bia: nova mensagem animada");

            PainelConversa animado = painelComRelogio(new AtomicLong(0));
            linhas.forEach(animado::adicionarLinha);

            ConfiguracaoAnimacoes.definirModo(Modo.DESATIVADAS);
            PainelConversa estatico = painelComRelogio(new AtomicLong(0));
            linhas.forEach(estatico::adicionarLinha);

            for (PainelConversa p : List.of(animado, estatico)) {
                p.setSize(600, 10);
                p.setSize(600, p.getPreferredSize().height);
                p.doLayout();
            }

            for (int i = 0; i < animado.getComponentCount(); i++) {
                Rectangle a = animado.getComponent(i).getBounds();
                Rectangle b = estatico.getComponent(i).getBounds();
                assertEquals(b, a, "Posição do item " + i);
            }

            assertEquals(estatico.texto(), animado.texto());
            assertEquals(String.join("\n", linhas) + "\n", animado.texto());
        });
    }

    // --- Botões ---

    @Test
    void botoesDevemMudarDeEstadoImediatamenteComAnimacoesDesativadas() throws Exception {

        naEdt(() -> {
            BotaoSakura comTransicao = new BotaoSakura("Enviar", BotaoSakura.Estilo.PRINCIPAL);
            comTransicao.getModel().setRollover(true);
            assertTrue(comTransicao.progressoHover() < 1, "Transição em andamento.");

            ConfiguracaoAnimacoes.definirModo(Modo.DESATIVADAS);
            BotaoSakura imediato = new BotaoSakura("Enviar", BotaoSakura.Estilo.PRINCIPAL);
            imediato.getModel().setRollover(true);
            assertEquals(1, imediato.progressoHover(), 1e-9);

            imediato.definirIcone(IconesDesenhados::aviaoDePapel);
            imediato.animarEnvio();
            assertEquals(1, imediato.progressoEnvio(), 1e-9, "Sem voo do avião.");
        });
    }
}
