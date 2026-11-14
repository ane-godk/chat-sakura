package br.unicesumar.chat.server;

import br.unicesumar.chat.ia.ParametrosIA;
import br.unicesumar.chat.ia.ProvedorIAFalso;
import br.unicesumar.chat.ia.ProvedorIndisponivel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Linha da Sakura IA no log de inicialização do servidor (sem rede e sem banco). */
class InicializacaoIATest {

    @Test
    void semQwenConfiguradoOLogDizOQueFaltaEOmiteLimitesQueNaoSeraoUsados() {
        String linha = Servidor.descricaoIA(ProvedorIndisponivel.doAmbiente(Map.of()), ParametrosIA.REMOTO);

        assertTrue(linha.startsWith("Sakura IA: Qwen (não configurado)."), linha);
        assertTrue(linha.contains("QWEN_API_KEY") && linha.contains("QWEN_BASE_URL"), linha);
        assertFalse(linha.contains("16384"), "Sem provedor, os limites do perfil remoto não se aplicam: " + linha);
    }

    @Test
    void configuracaoInvalidaMostraOMotivoSemExporAChave() {
        String linha = Servidor.descricaoIA(ProvedorIndisponivel.doAmbiente(
                Map.of("QWEN_API_KEY", "sk-segredo-123", "QWEN_MODEL", "llama3")), ParametrosIA.REMOTO);

        assertTrue(linha.contains("QWEN_MODEL deve ser um modelo da família Qwen"), linha);
        assertFalse(linha.contains("sk-segredo-123"), "A chave nunca aparece no log");
    }

    @Test
    void comProvedorConfiguradoOLogMostraOsLimitesEmUso() {
        String linha = Servidor.descricaoIA(new ProvedorIAFalso(), ParametrosIA.LOCAL);

        assertTrue(linha.contains("contexto 4096 tokens, resposta 320, 1 geração(ões) por vez, fila 4"), linha);
    }
}
