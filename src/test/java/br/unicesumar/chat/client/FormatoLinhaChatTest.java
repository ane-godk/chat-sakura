package br.unicesumar.chat.client;

import br.unicesumar.chat.client.FormatoLinhaChat.Linha;
import br.unicesumar.chat.client.FormatoLinhaChat.Tipo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FormatoLinhaChatTest {

    @Test
    void deveReconhecerMensagemDeUsuarioEmTrechos() {

        String texto = "[04/11 15:02] Bia: Oi, Ana! Tudo bem?";

        Linha linha = FormatoLinhaChat.analisar(texto);

        assertEquals(Tipo.MENSAGEM, linha.tipo());
        assertEquals("[04/11 15:02]", linha.horario());
        assertEquals("Bia", linha.remetente());
        assertEquals("Oi, Ana! Tudo bem?", linha.conteudo());

        assertEquals(
                texto,
                linha.horario() + " " + linha.remetente() + ": " + linha.conteudo(),
                "Os trechos devem reconstruir a linha original."
        );
    }

    @Test
    void devePreservarDoisPontosECaracteresEspeciaisNoConteudo() {

        String texto = "[04/11 15:02] Ana: hora: 10:30 <b>&amp;</b> \"ok\" ção 🌸\tfim";

        Linha linha = FormatoLinhaChat.analisar(texto);

        assertEquals(Tipo.MENSAGEM, linha.tipo());
        assertEquals("Ana", linha.remetente());
        assertEquals("hora: 10:30 <b>&amp;</b> \"ok\" ção 🌸\tfim", linha.conteudo());
    }

    @Test
    void deveReconhecerLinhasDoSistema() {

        Linha linha = FormatoLinhaChat.analisar("SISTEMA: Conectado como Ana");

        assertEquals(Tipo.SISTEMA, linha.tipo());
        assertEquals("SISTEMA: Conectado como Ana", linha.texto());
    }

    @Test
    void deveReconhecerSeparadoresDoHistorico() {

        assertEquals(
                Tipo.SEPARADOR_HISTORICO,
                FormatoLinhaChat.analisar(
                        "SISTEMA: ===== HISTÓRICO RECENTE ====="
                ).tipo()
        );

        assertEquals(
                Tipo.SEPARADOR_HISTORICO,
                FormatoLinhaChat.analisar(
                        "SISTEMA: ===== FIM DO HISTÓRICO ====="
                ).tipo()
        );
    }

    @Test
    void mensagemDeUsuarioComPalavraSistemaContinuaSendoMensagem() {

        Linha conteudo = FormatoLinhaChat.analisar(
                "[04/11 15:02] Ana: SISTEMA: ===== FIM DO HISTÓRICO ====="
        );

        Linha nome = FormatoLinhaChat.analisar(
                "[04/11 15:02] SISTEMA: Conectado como Ana"
        );

        assertEquals(Tipo.MENSAGEM, conteudo.tipo());
        assertEquals("Ana", conteudo.remetente());

        assertEquals(Tipo.MENSAGEM, nome.tipo());
        assertEquals("SISTEMA", nome.remetente());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "texto solto",
            "sistema: minúsculo não é do servidor",
            " SISTEMA: com espaço inicial",
            "[04/11 15:02]Ana: sem espaço após o horário",
            "[4/11 15:02] Ana: dia com um dígito",
            "[04/11 15h02] Ana: horário inválido",
            "[04/11 15:02] Ana sem dois pontos",
            "[04/11 15:02] Ana Maria: nome com espaço",
            "[04/11 15:02] NomeComMaisDeTrintaCaracteresXYZ: longo",
            "04/11 15:02 Ana: sem colchetes",
            "SISTEMA: ===== HISTÓRICO RECENTE ===== extra"
    })
    void linhasForaDosFormatosConhecidosNaoSaoMensagens(String texto) {

        Linha linha = assertDoesNotThrow(() -> FormatoLinhaChat.analisar(texto));

        assertNotEquals(Tipo.MENSAGEM, linha.tipo());
        assertNotEquals(Tipo.SEPARADOR_HISTORICO, linha.tipo());
        assertEquals(texto, linha.texto());
    }

    @Test
    void deveTratarLinhaNulaComoTextoVazio() {

        Linha linha = FormatoLinhaChat.analisar(null);

        assertEquals(Tipo.OUTRO, linha.tipo());
        assertEquals("", linha.texto());
    }

    @Test
    void deveIdentificarMensagemPropriaSemDiferenciarMaiusculas() {

        Linha linha = FormatoLinhaChat.analisar("[04/11 15:02] Ana: Oi");

        assertTrue(linha.enviadaPor("Ana"));
        assertTrue(linha.enviadaPor("ana"));
        assertFalse(linha.enviadaPor("Bia"));
        assertFalse(linha.enviadaPor(null));

        assertFalse(
                FormatoLinhaChat.analisar("SISTEMA: Ana").enviadaPor("Ana")
        );
    }

    @Test
    void indiceDeCorDeveSerEstavelEDentroDoIntervalo() {

        int cores = 5;

        int ana = FormatoLinhaChat.indiceCor("Ana", cores);

        assertEquals(ana, FormatoLinhaChat.indiceCor("ANA", cores));
        assertEquals(ana, FormatoLinhaChat.indiceCor("Ana", cores));

        for (String nome : new String[] {"Bia", "Caio", "x", "", null}) {
            int indice = FormatoLinhaChat.indiceCor(nome, cores);
            assertTrue(indice >= 0 && indice < cores);
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> FormatoLinhaChat.indiceCor("Ana", 0)
        );
    }
}
