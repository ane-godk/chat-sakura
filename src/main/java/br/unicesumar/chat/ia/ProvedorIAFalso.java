package br.unicesumar.chat.ia;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provedor SIMULADO e determinístico: funciona sem internet, para testes e
 * contingência na demonstração. Suas respostas começam com "[SIMULADO]" e
 * nunca são apresentadas como geradas pelo Qwen.
 *
 * Pedidos de resumo ({@link Finalidade#RESUMO}) devolvem uma nota por
 * mensagem do usuário presente no texto recebido.
 */
public final class ProvedorIAFalso implements ProvedorIA {

    public static final String PREFIXO = "[SIMULADO] ";

    private final List<PedidoIA> pedidos = new CopyOnWriteArrayList<>();
    private final List<String> threads = new CopyOnWriteArrayList<>();
    private final ParametrosIA parametros;

    public ProvedorIAFalso() {
        this(ParametrosIA.REMOTO);
    }

    public ProvedorIAFalso(ParametrosIA parametros) {
        this.parametros = parametros;
    }

    @Override
    public String gerar(PedidoIA pedido) throws FalhaIA {
        pedidos.add(pedido);
        threads.add(Thread.currentThread().getName());

        String ultima = pedido.historico().isEmpty()
                ? ""
                : pedido.historico().get(pedido.historico().size() - 1).texto();

        if (pedido.finalidade() == Finalidade.RESUMO) {
            StringBuilder notas = new StringBuilder();
            for (String linha : ultima.split("\n")) {
                if (linha.startsWith("Usuário: ")) {
                    notas.append("- O usuário disse: ").append(linha.substring(9)).append('\n');
                }
            }
            return notas.length() == 0 ? "- Nada relevante." : notas.toString().strip();
        }

        return PREFIXO + "Resposta " + respostas() + " para: " + ultima;
    }

    private long respostas() {
        return pedidos.stream().filter(p -> p.finalidade() == Finalidade.RESPOSTA).count();
    }

    @Override
    public String descricao() {
        return "SIMULADO";
    }

    @Override
    public boolean simulado() {
        return true;
    }

    @Override
    public ParametrosIA parametros() {
        return parametros;
    }

    /** Pedidos recebidos (para inspeção nos testes). */
    public List<PedidoIA> pedidos() {
        return List.copyOf(pedidos);
    }

    /** Somente os pedidos de resposta (sem os de resumo). */
    public List<PedidoIA> pedidosDeResposta() {
        return pedidos.stream().filter(p -> p.finalidade() == Finalidade.RESPOSTA).toList();
    }

    /** Threads em que cada pedido foi atendido. */
    public List<String> threads() {
        return List.copyOf(threads);
    }
}
