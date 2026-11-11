package br.unicesumar.chat.ia;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provedor SIMULADO e determinístico: funciona sem internet, para testes e
 * contingência na demonstração. Suas respostas começam com "[SIMULADO]" e
 * nunca são apresentadas como geradas pelo Qwen.
 */
public final class ProvedorIAFalso implements ProvedorIA {

    public static final String PREFIXO = "[SIMULADO] ";

    private final List<PedidoIA> pedidos = new CopyOnWriteArrayList<>();
    private final List<String> threads = new CopyOnWriteArrayList<>();

    @Override
    public String gerar(PedidoIA pedido) throws FalhaIA {
        pedidos.add(pedido);
        threads.add(Thread.currentThread().getName());

        String ultima = pedido.historico().isEmpty()
                ? ""
                : pedido.historico().get(pedido.historico().size() - 1).texto();

        return PREFIXO + "Resposta " + pedidos.size() + " para: " + ultima;
    }

    @Override
    public String descricao() {
        return "SIMULADO";
    }

    @Override
    public boolean simulado() {
        return true;
    }

    /** Pedidos recebidos (para inspeção nos testes). */
    public List<PedidoIA> pedidos() {
        return List.copyOf(pedidos);
    }

    /** Threads em que cada pedido foi atendido. */
    public List<String> threads() {
        return List.copyOf(threads);
    }
}
