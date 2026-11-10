package br.unicesumar.chat.protocolo;

import br.unicesumar.chat.model.PerfilUsuario;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Handshake de autenticação: codificação, limites e dados inválidos. */
class ProtocoloSakuraTest {

    private static final String SENHA = "minha senha çã 🌸 \"x\"";

    @Test
    void pedidosDevemIrEVoltarSemPerderCaracteres() {
        ProtocoloSakura.PedidoAutenticacao entrar =
                ProtocoloSakura.lerPedido(ProtocoloSakura.comandoEntrar("ana.souza", SENHA.toCharArray()));
        assertFalse(entrar.isCadastro());
        assertEquals("ana.souza", entrar.getLogin());
        assertArrayEquals(SENHA.toCharArray(), entrar.getSenha());

        ProtocoloSakura.PedidoAutenticacao cadastro = ProtocoloSakura.lerPedido(
                ProtocoloSakura.comandoCadastrar("ana.souza", "Ágata", SENHA.toCharArray()));
        assertTrue(cadastro.isCadastro());
        assertEquals("Ágata", cadastro.getApelido());

        cadastro.limpar();
        assertArrayEquals(new char[SENHA.length()], cadastro.getSenha(), "limpar() apaga a senha");
    }

    @Test
    void comandoNaoDeveConterASenhaLegivelNemExporNoToString() {
        String comando = ProtocoloSakura.comandoEntrar("ana", "segredo123".toCharArray());
        assertFalse(comando.contains("segredo123"));
        assertEquals(3, comando.split(" ").length, "Espaços da senha não quebram os campos");

        ProtocoloSakura.PedidoAutenticacao pedido = ProtocoloSakura.lerPedido(comando);
        assertFalse(pedido.toString().contains("segredo123"));
    }

    @Test
    void pedidosMalformadosDevemSerRecusados() {
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString("ana".getBytes());
        String invalidoUtf8 = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xC3, 0x28});
        String longo = Base64.getUrlEncoder().withoutPadding().encodeToString("x".repeat(65).getBytes());

        for (String linha : new String[] {
                null, "", "ENTRAR", "ENTRAR " + b64, "ENTRAR " + b64 + " " + b64 + " 1",
                "entrar " + b64 + " " + b64, "ENTRAR  " + b64, "ENTRAR @@ " + b64,
                "ENTRAR " + invalidoUtf8 + " " + b64, "ENTRAR " + longo + " " + b64,
                "CADASTRAR " + b64 + " " + b64, "SAIR " + b64 + " " + b64}) {
            assertThrows(IllegalArgumentException.class, () -> ProtocoloSakura.lerPedido(linha), String.valueOf(linha));
        }
    }

    @Test
    void respostasDevemSerLidasPeloCliente() {
        PerfilUsuario perfil = new PerfilUsuario(7, "ana.souza", "Ágata", "sakura");

        ProtocoloSakura.RespostaAutenticacao ok =
                ProtocoloSakura.lerResposta(ProtocoloSakura.respostaSucesso(perfil));
        assertTrue(ok.sucesso());
        assertEquals(perfil, ok.perfil());

        ProtocoloSakura.RespostaAutenticacao erro = ProtocoloSakura.lerResposta(
                ProtocoloSakura.respostaErro("CREDENCIAIS_INVALIDAS", "Login ou senha incorretos."));
        assertFalse(erro.sucesso());
        assertEquals("CREDENCIAIS_INVALIDAS", erro.codigo());
        assertEquals("Login ou senha incorretos.", erro.mensagem());

        for (String linha : new String[] {null, "AUTH OK", "AUTH OK x a b c", "AUTH OK -1 YQ YQ YQ",
                "AUTH ERRO minusculo YQ", "SISTEMA: Conectado como Ana"}) {
            assertThrows(IllegalArgumentException.class, () -> ProtocoloSakura.lerResposta(linha));
        }
    }

    @Test
    void negociacaoNaoPodeSerConfundidaComApelidoLegado() {
        assertTrue(ProtocoloSakura.NEGOCIACAO.contains(" "),
                "Apelidos não têm espaços: um cliente legado nunca envia a negociação");
        assertEquals("Sem contas", ProtocoloSakura.lerNegociacaoRecusada(
                ProtocoloSakura.negociacaoRecusada("Sem contas")));
        assertNull(ProtocoloSakura.lerNegociacaoRecusada("SAKURA 2 OK"));
    }
}
