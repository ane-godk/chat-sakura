package br.unicesumar.chat.ia;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Qwen pela API do OpenRouter (compatível com OpenAI), lida somente de
 * variáveis de ambiente:
 *
 * <ul>
 *   <li>{@code OPENROUTER_API_KEY}: chave (obrigatória; ativa o OpenRouter);</li>
 *   <li>{@code OPENROUTER_MODEL}: identificador do modelo (opcional). Sem ele,
 *       o servidor procura, na lista pública de modelos, um Qwen gratuito
 *       ({@code qwen/...:free}) disponível no momento;</li>
 *   <li>{@code OPENROUTER_BASE_URL}: base da API (padrão {@link #URL_PADRAO};
 *       precisa ser https);</li>
 *   <li>{@code OPENROUTER_TIMEOUT_SEGUNDOS}: tempo limite da resposta (padrão 60).</li>
 * </ul>
 *
 * Os ajustes opcionais de geração ({@code QWEN_TEMPERATURA},
 * {@code QWEN_RESPOSTA_TOKENS} etc.) continuam valendo. A chave vai somente
 * no cabeçalho {@code Authorization} dos pedidos de geração: nunca no código,
 * no banco, nos logs ou na consulta à lista de modelos (que é pública).
 */
public final class ConfiguracaoOpenRouter {

    public static final String URL_PADRAO = "https://openrouter.ai/api/v1";
    public static final int TEMPO_PADRAO_S = 60;
    static final Duration PRAZO_CATALOGO = Duration.ofSeconds(8);
    static final String PAGINA_MODELOS = "https://openrouter.ai/models?q=qwen";

    static final String SEM_GRATUITO = "Nenhum modelo Qwen gratuito (com sufixo :free) está disponível no"
            + " OpenRouter agora. Escolha um modelo em " + PAGINA_MODELOS + " e defina OPENROUTER_MODEL"
            + " (modelos sem :free consomem créditos da sua conta) e reinicie o servidor.";
    static final String SEM_CATALOGO = "Não foi possível consultar a lista de modelos do OpenRouter (sem"
            + " internet?). Confira a conexão, ou defina OPENROUTER_MODEL, e reinicie o servidor.";
    static final String SEM_MODELO = "Defina OPENROUTER_MODEL (por exemplo, um modelo qwen/...:free listado em "
            + PAGINA_MODELOS + ") e reinicie o servidor.";

    /** Lê o corpo de {@code GET <base>/models} (substituível nos testes). */
    public interface Catalogo {
        String modelos(URI base) throws IOException;
    }

    /** Um modelo da lista pública: identificador e se é gratuito. */
    record Modelo(String id, boolean gratuito) {
    }

    private ConfiguracaoOpenRouter() {
    }

    /** {@code true} se {@code OPENROUTER_API_KEY} foi definida (o OpenRouter tem prioridade). */
    public static boolean configurado(Map<String, String> ambiente) {
        return !isVazio(ambiente.get("OPENROUTER_API_KEY"));
    }

    /** Consulta HTTP real e sem chave à lista pública de modelos, com prazo curto. */
    public static Catalogo http() {
        return base -> {
            HttpClient cliente = HttpClient.newBuilder().connectTimeout(PRAZO_CATALOGO).build();
            URI endereco = URI.create(base.toString().replaceAll("/+$", "") + "/models");
            HttpRequest pedido = HttpRequest.newBuilder(endereco).timeout(PRAZO_CATALOGO).GET().build();
            try {
                HttpResponse<String> resposta = cliente.send(pedido,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resposta.statusCode() != 200) {
                    throw new IOException("HTTP " + resposta.statusCode());
                }
                return resposta.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Consulta interrompida.");
            }
        };
    }

    /**
     * Provedor do OpenRouter, ou {@link ProvedorIndisponivel} com o motivo.
     *
     * @param catalogo lista de modelos ({@code null}: sem consulta, então
     *                 {@code OPENROUTER_MODEL} passa a ser obrigatório)
     */
    static ProvedorIA provedor(Map<String, String> ambiente, Catalogo catalogo) {

        URI base;
        try {
            base = base(ambiente);
        } catch (IllegalArgumentException e) {
            return new ProvedorIndisponivel("Configuração do OpenRouter inválida: " + e.getMessage());
        }

        String modelo = ambiente.get("OPENROUTER_MODEL");

        if (isVazio(modelo)) {
            if (catalogo == null) {
                return new ProvedorIndisponivel(SEM_MODELO);
            }
            List<Modelo> modelos;
            try {
                modelos = lerModelos(catalogo.modelos(base));
            } catch (IOException | RuntimeException e) {
                return new ProvedorIndisponivel(SEM_CATALOGO);
            }
            Optional<String> gratuito = qwenGratuito(modelos);
            if (gratuito.isEmpty()) {
                return new ProvedorIndisponivel(SEM_GRATUITO);
            }
            modelo = gratuito.get();
            System.out.println("Sakura IA: OPENROUTER_MODEL não definido; usando o Qwen gratuito " + modelo + ".");
        } else {
            modelo = modelo.strip();
            if (catalogo != null) {
                avisarSobre(modelo, catalogo, base);
            }
        }

        try {
            return new ProvedorQwen(criar(ambiente, base, modelo));
        } catch (IllegalArgumentException e) {
            return new ProvedorIndisponivel("Configuração do OpenRouter inválida: " + e.getMessage());
        }
    }

    /** Configuração já com o modelo escolhido. */
    static ConfiguracaoQwen criar(Map<String, String> ambiente, URI base, String modelo) {
        int segundos = TEMPO_PADRAO_S;
        String tempo = ambiente.get("OPENROUTER_TIMEOUT_SEGUNDOS");
        if (!isVazio(tempo)) {
            try {
                segundos = Math.max(5, Math.min(180, Integer.parseInt(tempo.strip())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("OPENROUTER_TIMEOUT_SEGUNDOS deve ser um número.");
            }
        }
        return new ConfiguracaoQwen(base, modelo, ambiente.get("OPENROUTER_API_KEY").strip(),
                Duration.ofSeconds(segundos),
                ConfiguracaoQwen.Ajustes.doAmbiente(ambiente, ConfiguracaoQwen.Api.OPENAI, false),
                ConfiguracaoQwen.Servico.OPENROUTER);
    }

    static URI base(Map<String, String> ambiente) {
        String url = ambiente.get("OPENROUTER_BASE_URL");
        try {
            return URI.create(isVazio(url) ? URL_PADRAO : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OPENROUTER_BASE_URL inválida.");
        }
    }

    /** Avisa no log (sem bloquear) se o modelo escolhido não aparece na lista ou se é pago. */
    private static void avisarSobre(String modelo, Catalogo catalogo, URI base) {
        List<Modelo> modelos;
        try {
            modelos = lerModelos(catalogo.modelos(base));
        } catch (IOException | RuntimeException e) {
            return; // Sem a lista, o próprio pedido dirá se o modelo existe.
        }
        Optional<Modelo> encontrado = modelos.stream().filter(m -> m.id().equals(modelo)).findFirst();
        if (encontrado.isEmpty()) {
            System.err.println("Sakura IA: aviso: o modelo " + modelo + " não aparece na lista do OpenRouter."
                    + " Confira OPENROUTER_MODEL em " + PAGINA_MODELOS + ".");
        } else if (!encontrado.get().gratuito()) {
            System.err.println("Sakura IA: aviso: o modelo " + modelo + " não é gratuito e consome créditos"
                    + " da conta do OpenRouter.");
        }
    }

    /** Lê {@code data[].id} e o preço da lista do OpenRouter; itens estranhos são ignorados. */
    static List<Modelo> lerModelos(String corpo) {
        List<Modelo> modelos = new ArrayList<>();
        JsonElement raiz = JsonParser.parseString(corpo);
        if (!raiz.isJsonObject() || !raiz.getAsJsonObject().has("data")
                || !raiz.getAsJsonObject().get("data").isJsonArray()) {
            throw new IllegalArgumentException("Lista de modelos em formato inesperado.");
        }
        for (JsonElement item : raiz.getAsJsonObject().getAsJsonArray("data")) {
            if (!item.isJsonObject() || !item.getAsJsonObject().has("id")
                    || !item.getAsJsonObject().get("id").isJsonPrimitive()) {
                continue;
            }
            JsonObject objeto = item.getAsJsonObject();
            String id = objeto.get("id").getAsString();
            modelos.add(new Modelo(id, id.endsWith(":free") && precoZero(objeto)));
        }
        return modelos;
    }

    /**
     * Um Qwen gratuito: identificador {@code qwen/...:free} com preço zero.
     * Prefere modelos de conversa (sem visão, código ou só raciocínio) e,
     * entre eles, a ordem alfabética, para a escolha ser previsível.
     */
    static Optional<String> qwenGratuito(List<Modelo> modelos) {
        return modelos.stream()
                .filter(Modelo::gratuito)
                .map(Modelo::id)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("qwen/"))
                .min(Comparator.comparing(ConfiguracaoOpenRouter::especializado).thenComparing(id -> id));
    }

    private static boolean especializado(String id) {
        String nome = id.toLowerCase(Locale.ROOT);
        return nome.contains("-vl") || nome.contains("coder") || nome.contains("thinking") || nome.contains("omni");
    }

    /** Sem a seção {@code pricing}, vale o sufixo {@code :free}; com ela, entrada e saída precisam ser 0. */
    private static boolean precoZero(JsonObject modelo) {
        if (!modelo.has("pricing") || !modelo.get("pricing").isJsonObject()) {
            return true;
        }
        JsonObject preco = modelo.getAsJsonObject("pricing");
        return zero(preco, "prompt") && zero(preco, "completion");
    }

    private static boolean zero(JsonObject preco, String campo) {
        if (!preco.has(campo) || !preco.get(campo).isJsonPrimitive()) {
            return true;
        }
        try {
            return Double.parseDouble(preco.get(campo).getAsString()) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isVazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
