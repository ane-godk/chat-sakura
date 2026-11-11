package br.unicesumar.chat.protocolo;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static br.unicesumar.chat.protocolo.ProtocoloJson.*;
import static org.junit.jupiter.api.Assertions.*;

/** Codec JSON do protocolo v2: requisições válidas, inválidas e eventos. */
class ProtocoloJsonTest {

    @Test
    void requisicoesValidasDevemSerLidasComTiposELimites() throws Exception {
        Requisicao enviar = lerRequisicao("{\"tipo\":\"ENVIAR\",\"req\":7,\"conversaId\":12,\"texto\":\"Olá 🌸 \\\"x\\\"\"}");
        assertEquals(TipoRequisicao.ENVIAR, enviar.tipo());
        assertEquals(7L, enviar.req());
        assertEquals(12L, enviar.id("conversaId"));
        assertEquals("Olá 🌸 \"x\"", enviar.texto("texto"));

        Requisicao grupo = lerRequisicao("{\"tipo\":\"CRIAR_GRUPO\",\"nome\":\"Turma\",\"participantes\":[2,3]}");
        assertNull(grupo.req());
        assertEquals(List.of(2L, 3L), grupo.ids("participantes", 49));

        Requisicao historico = lerRequisicao("{\"tipo\":\"HISTORICO\",\"conversaId\":1}");
        assertNull(historico.idOpcional("antesDe"));
        assertEquals(50, historico.inteiro("limite", 1, 100, 50));
    }

    @Test
    void jsonInvalidoOuNaoEstritoDeveSerRecusado() {
        String[] linhas = {
                null, "", "   ", "texto puro", "[]", "\"ENVIAR\"", "{", "{\"tipo\":\"ENVIAR\"} {}",
                "{'tipo':'ENVIAR'}", "{tipo:\"ENVIAR\"}", "{\"tipo\":\"ENVIAR\",}", "{\"tipo\":\"ENVIAR\"} // x",
                "{\"tipo\":NaN}", "{\"req\":1}", "{\"tipo\":1}", "{\"tipo\":\"APAGAR_TUDO\"}"};

        for (String linha : linhas) {
            assertThrows(ProtocoloInvalidoException.class, () -> lerRequisicao(linha), String.valueOf(linha));
        }
    }

    @Test
    void camposComTipoOuLimiteInvalidoDevemSerRecusadosComOReq() throws Exception {
        String[] ids = {"0", "-1", "1.5", "\"1\"", "true", "null", "[1]", "9007199254740992", "1e400"};
        for (String id : ids) {
            Requisicao r = lerRequisicao("{\"tipo\":\"ABRIR_PRIVADA\",\"req\":3,\"usuarioId\":" + id + "}");
            ProtocoloInvalidoException e = assertThrows(ProtocoloInvalidoException.class, () -> r.id("usuarioId"), id);
            assertEquals(3L, e.getReq());
        }

        assertThrows(ProtocoloInvalidoException.class, () -> lerRequisicao("{\"tipo\":\"ENVIAR\",\"req\":-2}"));

        Requisicao longo = lerRequisicao("{\"tipo\":\"ENVIAR\",\"texto\":\"" + "a".repeat(1301) + "\"}");
        assertThrows(ProtocoloInvalidoException.class, () -> longo.texto("texto"));

        Requisicao semTexto = lerRequisicao("{\"tipo\":\"ENVIAR\",\"texto\":5}");
        assertThrows(ProtocoloInvalidoException.class, () -> semTexto.texto("texto"));

        Requisicao lista = lerRequisicao("{\"tipo\":\"CRIAR_GRUPO\",\"participantes\":[1,2,3]}");
        assertThrows(ProtocoloInvalidoException.class, () -> lista.ids("participantes", 2));
        Requisicao listaRuim = lerRequisicao("{\"tipo\":\"CRIAR_GRUPO\",\"participantes\":[1,\"x\"]}");
        assertThrows(ProtocoloInvalidoException.class, () -> listaRuim.ids("participantes", 49));

        Requisicao limite = lerRequisicao("{\"tipo\":\"HISTORICO\",\"conversaId\":1,\"limite\":500}");
        assertThrows(ProtocoloInvalidoException.class, () -> limite.inteiro("limite", 1, 100, 50));
    }

    @Test
    void eventosDevemIrEVoltarSemDadosSensiveis() throws Exception {
        Mensagem mensagem = new Mensagem(10L, new Usuario(2L, "Bia"), "Oi, Ana! 🌸 <b>&",
                LocalDateTime.of(2026, 11, 10, 14, 30), TipoMensagem.MENSAGEM, 5);
        Conversa grupo = new Conversa(5, Conversa.Tipo.GRUPO, "Turma", List.of(
                new Conversa.Participante(1, "Ana", "sakura", Conversa.Papel.ADMIN),
                new Conversa.Participante(2, "Bia", "sakura", Conversa.Papel.MEMBRO)));

        String linhaMensagem = eventoMensagem(MensagemJson.de(mensagem));
        assertFalse(linhaMensagem.contains("\\u003c"), "Sem escape HTML desnecessário");
        assertEquals(mensagem, lerEvento(linhaMensagem).mensagem().paraMensagem());

        String linhaConversa = eventoConversa(4L, ConversaJson.de(grupo, mensagem, 2));
        Evento evento = lerEvento(linhaConversa);
        assertEquals(TipoEvento.CONVERSA, evento.tipo());
        assertEquals(4L, evento.req());
        assertEquals("ADMIN", evento.conversa().participantes().get(0).papel());
        assertEquals(2, evento.conversa().naoLidas());

        for (String sensivel : new String[] {"login", "senha", "hash", "sal\""}) {
            assertFalse(linhaConversa.contains(sensivel), sensivel);
        }

        Evento erro = lerEvento(eventoErro(9L, "NAO_ENCONTRADA", "Conversa não encontrada."));
        assertEquals("NAO_ENCONTRADA", erro.codigo());
        assertEquals("Conversa não encontrada.", erro.mensagemErro());

        assertThrows(ProtocoloInvalidoException.class, () -> lerEvento("{\"tipo\":\"DESCONHECIDO\"}"));
    }

    @Test
    void requisicaoMontadaPeloClienteDeveSerAceitaPeloServidor() throws Exception {
        String linha = requisicao(TipoRequisicao.CRIAR_GRUPO, 2L, "nome", "Turma \"A\"", "participantes", List.of(2L, 3L));
        Requisicao r = lerRequisicao(linha);
        assertEquals("Turma \"A\"", r.texto("nome"));
        assertEquals(List.of(2L, 3L), r.ids("participantes", 49));
        assertTrue(linha.length() < br.unicesumar.chat.protocolo.ProtocoloSakura.TAMANHO_MAXIMO_LINHA);
    }
}
