package br.unicesumar.chat.protocolo;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoMensagem;
import br.unicesumar.chat.model.Usuario;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Protocolo v2: após {@code AUTH OK}, cada linha é um objeto JSON com o
 * campo {@code tipo}. Requisições podem levar {@code req} (inteiro
 * positivo), repetido na resposta correspondente.
 *
 * <pre>
 * Cliente → servidor
 *   LISTAR_CONVERSAS {req}
 *   LISTAR_USUARIOS  {req}
 *   ABRIR_PRIVADA    {req, usuarioId}
 *   CRIAR_GRUPO      {req, nome, participantes:[ids]}
 *   ENVIAR           {req, conversaId, texto}
 *   HISTORICO        {req, conversaId, antesDe?, limite?}
 *   MARCAR_LIDA      {conversaId, mensagemId}
 *
 * Servidor → cliente
 *   CONVERSAS {req?, conversas:[conversa]}   (o primeiro confirma a entrada)
 *   CONVERSA  {req?, conversa}               (aberta, criada ou recebida)
 *   USUARIOS  {req, usuarios:[{id, apelido, avatar, online}]}
 *   HISTORICO {req?, conversaId, mensagens:[mensagem]}
 *   MENSAGEM  {mensagem}                     (presença: tipo ENTRADA/SAIDA na Geral)
 *   ERRO      {req?, codigo, mensagem}
 * </pre>
 *
 * Nenhum evento contém login, senha, hash ou sal. O servidor ignora
 * qualquer identificador de remetente enviado pelo cliente.
 */
public final class ProtocoloJson {

    public enum TipoRequisicao {
        LISTAR_CONVERSAS, LISTAR_USUARIOS, ABRIR_PRIVADA, CRIAR_GRUPO, ENVIAR, HISTORICO, MARCAR_LIDA
    }

    public enum TipoEvento {
        CONVERSAS, CONVERSA, USUARIOS, HISTORICO, MENSAGEM, ERRO
    }

    /** Maior inteiro exato em JSON (2^53 - 1). */
    private static final long ID_MAXIMO = 9_007_199_254_740_991L;
    private static final int TEXTO_MAXIMO = 1_000;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ProtocoloJson() {
    }

    // ------------------------------------------------------------------
    // Objetos de dados (somente informações públicas)
    // ------------------------------------------------------------------

    public record MensagemJson(long id, long conversaId, Long remetenteId, String remetente,
                               String texto, String horario, String tipo) {

        public static MensagemJson de(Mensagem m) {
            return new MensagemJson(m.getId(), m.getConversaId(), m.getRemetente().getId(),
                    m.getRemetente().getNome(), m.getConteudo(), m.getHorario().toString(), m.getTipo().name());
        }

        /** Reconstrói (e valida) a mensagem de domínio. */
        public Mensagem paraMensagem() {
            TipoMensagem tipoMensagem;
            try {
                tipoMensagem = TipoMensagem.valueOf(Objects.requireNonNull(tipo));
            } catch (RuntimeException e) {
                tipoMensagem = TipoMensagem.MENSAGEM;
            }
            return new Mensagem(id, new Usuario(remetenteId, remetente), texto,
                    LocalDateTime.parse(horario), tipoMensagem, conversaId);
        }
    }

    public record ParticipanteJson(long id, String apelido, String avatar, String papel) {
    }

    public record ConversaJson(long id, String tipo, String nome, List<ParticipanteJson> participantes,
                               MensagemJson ultima, int naoLidas) {

        public static ConversaJson de(Conversa c, Mensagem ultima, int naoLidas) {
            List<ParticipanteJson> participantes = c.getParticipantes().stream()
                    .map(p -> new ParticipanteJson(p.id(), p.apelido(), p.avatarId(), p.papel().name()))
                    .toList();
            return new ConversaJson(c.getId(), c.getTipo().name(), c.getNome(), participantes,
                    ultima == null ? null : MensagemJson.de(ultima), naoLidas);
        }
    }

    public record UsuarioJson(long id, String apelido, String avatar, boolean online) {
    }

    // ------------------------------------------------------------------
    // Requisições (lidas pelo servidor)
    // ------------------------------------------------------------------

    /** Erro de formato; {@code req} é preenchido quando foi possível lê-lo. */
    public static final class ProtocoloInvalidoException extends Exception {

        private static final long serialVersionUID = 1L;

        private final Long req;

        ProtocoloInvalidoException(Long req, String mensagem) {
            super(mensagem, null, false, false);
            this.req = req;
        }

        public Long getReq() {
            return req;
        }
    }

    /** Requisição validada quanto ao formato; os campos são lidos sob demanda. */
    public static final class Requisicao {

        private final TipoRequisicao tipo;
        private final Long req;
        private final JsonObject dados;

        private Requisicao(TipoRequisicao tipo, Long req, JsonObject dados) {
            this.tipo = tipo;
            this.req = req;
            this.dados = dados;
        }

        public TipoRequisicao tipo() {
            return tipo;
        }

        public Long req() {
            return req;
        }

        public long id(String campo) throws ProtocoloInvalidoException {
            Long valor = idOpcional(campo);
            if (valor == null) {
                throw new ProtocoloInvalidoException(req, "Campo obrigatório: " + campo + ".");
            }
            return valor;
        }

        public Long idOpcional(String campo) throws ProtocoloInvalidoException {
            JsonElement valor = dados.get(campo);
            if (valor == null || valor.isJsonNull()) {
                return null;
            }
            Long id = inteiroPositivo(valor);
            if (id == null) {
                throw new ProtocoloInvalidoException(req, "Campo inválido: " + campo + ".");
            }
            return id;
        }

        public int inteiro(String campo, int minimo, int maximo, int padrao) throws ProtocoloInvalidoException {
            Long valor = idOpcional(campo);
            if (valor == null) {
                return padrao;
            }
            if (valor < minimo || valor > maximo) {
                throw new ProtocoloInvalidoException(req, "Campo fora do intervalo: " + campo + ".");
            }
            return valor.intValue();
        }

        public String texto(String campo) throws ProtocoloInvalidoException {
            JsonElement valor = dados.get(campo);
            if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()) {
                throw new ProtocoloInvalidoException(req, "Campo de texto obrigatório: " + campo + ".");
            }
            String texto = valor.getAsString();
            if (texto.length() > TEXTO_MAXIMO) {
                throw new ProtocoloInvalidoException(req, "Campo longo demais: " + campo + ".");
            }
            return texto;
        }

        public List<Long> ids(String campo, int maximo) throws ProtocoloInvalidoException {
            JsonElement valor = dados.get(campo);
            if (valor == null || !valor.isJsonArray() || valor.getAsJsonArray().size() > maximo) {
                throw new ProtocoloInvalidoException(req, "Lista inválida: " + campo + ".");
            }
            List<Long> ids = new ArrayList<>();
            for (JsonElement item : valor.getAsJsonArray()) {
                Long id = inteiroPositivo(item);
                if (id == null) {
                    throw new ProtocoloInvalidoException(req, "Lista inválida: " + campo + ".");
                }
                ids.add(id);
            }
            return ids;
        }
    }

    public static Requisicao lerRequisicao(String linha) throws ProtocoloInvalidoException {

        JsonObject objeto = lerObjeto(linha);
        Long req = null;

        JsonElement campoReq = objeto.get("req");
        if (campoReq != null && !campoReq.isJsonNull()) {
            req = inteiroPositivo(campoReq);
            if (req == null) {
                throw new ProtocoloInvalidoException(null, "Campo req inválido.");
            }
        }

        JsonElement campoTipo = objeto.get("tipo");
        if (campoTipo == null || !campoTipo.isJsonPrimitive() || !campoTipo.getAsJsonPrimitive().isString()) {
            throw new ProtocoloInvalidoException(req, "Campo tipo obrigatório.");
        }

        try {
            return new Requisicao(TipoRequisicao.valueOf(campoTipo.getAsString()), req, objeto);
        } catch (IllegalArgumentException e) {
            throw new ProtocoloInvalidoException(req, "Tipo de requisição desconhecido.");
        }
    }

    /** Requisição montada pelo cliente; {@code campos} em pares nome/valor. */
    public static String requisicao(TipoRequisicao tipo, Long req, Object... campos) {
        JsonObject objeto = new JsonObject();
        objeto.addProperty("tipo", tipo.name());
        if (req != null) {
            objeto.addProperty("req", req);
        }
        for (int i = 0; i + 1 < campos.length; i += 2) {
            objeto.add((String) campos[i], GSON.toJsonTree(campos[i + 1]));
        }
        return GSON.toJson(objeto);
    }

    // ------------------------------------------------------------------
    // Eventos (escritos pelo servidor, lidos pelo cliente)
    // ------------------------------------------------------------------

    public static String eventoConversas(Long req, List<ConversaJson> conversas) {
        return evento(TipoEvento.CONVERSAS, req, "conversas", conversas);
    }

    public static String eventoConversa(Long req, ConversaJson conversa) {
        return evento(TipoEvento.CONVERSA, req, "conversa", conversa);
    }

    public static String eventoUsuarios(Long req, List<UsuarioJson> usuarios) {
        return evento(TipoEvento.USUARIOS, req, "usuarios", usuarios);
    }

    public static String eventoHistorico(Long req, long conversaId, List<MensagemJson> mensagens) {
        JsonObject objeto = base(TipoEvento.HISTORICO, req);
        objeto.addProperty("conversaId", conversaId);
        objeto.add("mensagens", GSON.toJsonTree(mensagens));
        return GSON.toJson(objeto);
    }

    public static String eventoMensagem(MensagemJson mensagem) {
        return evento(TipoEvento.MENSAGEM, null, "mensagem", mensagem);
    }

    public static String eventoErro(Long req, String codigo, String mensagem) {
        JsonObject objeto = base(TipoEvento.ERRO, req);
        objeto.addProperty("codigo", codigo);
        objeto.addProperty("mensagem", mensagem);
        return GSON.toJson(objeto);
    }

    /** Evento recebido pelo cliente. */
    public static final class Evento {

        private final TipoEvento tipo;
        private final Long req;
        private final JsonObject dados;

        private Evento(TipoEvento tipo, Long req, JsonObject dados) {
            this.tipo = tipo;
            this.req = req;
            this.dados = dados;
        }

        public TipoEvento tipo() {
            return tipo;
        }

        public Long req() {
            return req;
        }

        public List<ConversaJson> conversas() {
            return lista("conversas", ConversaJson.class);
        }

        public ConversaJson conversa() {
            return GSON.fromJson(dados.get("conversa"), ConversaJson.class);
        }

        public List<UsuarioJson> usuarios() {
            return lista("usuarios", UsuarioJson.class);
        }

        public long conversaId() {
            return dados.get("conversaId").getAsLong();
        }

        public List<MensagemJson> mensagens() {
            return lista("mensagens", MensagemJson.class);
        }

        public MensagemJson mensagem() {
            return GSON.fromJson(dados.get("mensagem"), MensagemJson.class);
        }

        public String codigo() {
            return dados.get("codigo").getAsString();
        }

        public String mensagemErro() {
            return dados.get("mensagem").getAsString();
        }

        private <T> List<T> lista(String campo, Class<T> classe) {
            JsonArray itens = dados.getAsJsonArray(campo);
            List<T> lista = new ArrayList<>();
            for (JsonElement item : itens) {
                lista.add(GSON.fromJson(item, classe));
            }
            return lista;
        }
    }

    /** @throws ProtocoloInvalidoException se a linha não for um evento conhecido */
    public static Evento lerEvento(String linha) throws ProtocoloInvalidoException {
        JsonObject objeto = lerObjeto(linha);
        try {
            TipoEvento tipo = TipoEvento.valueOf(objeto.get("tipo").getAsString());
            JsonElement req = objeto.get("req");
            return new Evento(tipo, req == null || req.isJsonNull() ? null : inteiroPositivo(req), objeto);
        } catch (RuntimeException e) {
            throw new ProtocoloInvalidoException(null, "Evento desconhecido.");
        }
    }

    // ------------------------------------------------------------------

    private static JsonObject base(TipoEvento tipo, Long req) {
        JsonObject objeto = new JsonObject();
        objeto.addProperty("tipo", tipo.name());
        if (req != null) {
            objeto.addProperty("req", req);
        }
        return objeto;
    }

    private static String evento(TipoEvento tipo, Long req, String campo, Object valor) {
        JsonObject objeto = base(tipo, req);
        objeto.add(campo, GSON.toJsonTree(valor));
        return GSON.toJson(objeto);
    }

    /** JSON estrito: um único objeto por linha, sem conteúdo extra. */
    private static JsonObject lerObjeto(String linha) throws ProtocoloInvalidoException {

        if (linha == null || linha.isBlank()) {
            throw new ProtocoloInvalidoException(null, "Linha vazia.");
        }

        try (JsonReader leitor = new JsonReader(new StringReader(linha))) {
            leitor.setStrictness(Strictness.STRICT);

            if (leitor.peek() != JsonToken.BEGIN_OBJECT) {
                throw new ProtocoloInvalidoException(null, "Esperado um objeto JSON.");
            }

            JsonElement elemento = GSON.getAdapter(JsonElement.class).read(leitor);

            if (leitor.peek() != JsonToken.END_DOCUMENT) {
                throw new ProtocoloInvalidoException(null, "Conteúdo após o objeto JSON.");
            }

            return elemento.getAsJsonObject();

        } catch (IOException | JsonParseException | IllegalStateException e) {
            throw new ProtocoloInvalidoException(null, "JSON inválido.");
        }
    }

    /** Inteiro exato entre 1 e 2^53-1, ou {@code null}. */
    private static Long inteiroPositivo(JsonElement valor) {
        if (!valor.isJsonPrimitive() || !((JsonPrimitive) valor).isNumber()) {
            return null;
        }
        try {
            BigDecimal numero = new BigDecimal(valor.getAsString());
            long inteiro = numero.longValueExact();
            return inteiro >= 1 && inteiro <= ID_MAXIMO ? inteiro : null;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
