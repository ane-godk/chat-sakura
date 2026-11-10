package br.unicesumar.chat.protocolo;

import br.unicesumar.chat.model.PerfilUsuario;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Handshake de autenticação do Chat Sakura, compartilhado por servidor e
 * cliente. Fica antes do fluxo do chat e será mantido pelo protocolo v2.
 *
 * <pre>
 * S: SISTEMA: Bem-vindo ao Chat Sakura!
 * S: SISTEMA: Informe seu nome de usuário:
 * C: SAKURA 2                       (o cliente legado envia aqui o apelido)
 * S: SAKURA 2 OK                    ou  SAKURA ERRO &lt;mensagem&gt;
 * C: ENTRAR &lt;login&gt; &lt;senha&gt;
 * C: CADASTRAR &lt;login&gt; &lt;apelido&gt; &lt;senha&gt;
 * S: AUTH OK &lt;id&gt; &lt;login&gt; &lt;apelido&gt; &lt;avatar&gt;
 * S: AUTH ERRO &lt;CODIGO&gt; &lt;mensagem&gt;  (nova tentativa permitida, até o limite)
 * S: SISTEMA: Conectado como &lt;apelido&gt;  (segue o fluxo atual do Chat Geral)
 * </pre>
 *
 * Campos de texto vão em Base64 URL sem preenchimento, sobre UTF-8, e são
 * separados por um espaço. Como apelidos não têm espaços, um cliente
 * legado nunca envia {@code SAKURA 2}. Base64 não é criptografia: sem TLS,
 * a senha trafega legível na rede.
 */
public final class ProtocoloSakura {

    public static final String NEGOCIACAO = "SAKURA 2";
    public static final String NEGOCIACAO_ACEITA = "SAKURA 2 OK";
    public static final String PREFIXO_NEGOCIACAO_RECUSADA = "SAKURA ERRO ";

    public static final String COMANDO_ENTRAR = "ENTRAR";
    public static final String COMANDO_CADASTRAR = "CADASTRAR";

    public static final String PREFIXO_AUTH_OK = "AUTH OK ";
    public static final String PREFIXO_AUTH_ERRO = "AUTH ERRO ";

    public static final String PREFIXO_CONFIRMACAO = "SISTEMA: Conectado como ";

    /** Limite de caracteres por linha recebida pelo servidor. */
    public static final int TAMANHO_MAXIMO_LINHA = 4096;

    /** Tentativas de autenticação por conexão. */
    public static final int TENTATIVAS_MAXIMAS = 5;

    /** Limites dos campos decodificados, antes da validação de negócio. */
    private static final int MAXIMO_CAMPO_TEXTO = 64;
    private static final int MAXIMO_CAMPO_SENHA = 256;
    private static final int MAXIMO_MENSAGEM = 300;

    private ProtocoloSakura() {
    }

    // ------------------------------------------------------------------
    // Pedido do cliente
    // ------------------------------------------------------------------

    /** Pedido de autenticação; {@link #limpar()} apaga a senha da memória. */
    public static final class PedidoAutenticacao {

        private final boolean cadastro;
        private final String login;
        private final String apelido;
        private final char[] senha;

        private PedidoAutenticacao(boolean cadastro, String login, String apelido, char[] senha) {
            this.cadastro = cadastro;
            this.login = login;
            this.apelido = apelido;
            this.senha = senha;
        }

        public boolean isCadastro() {
            return cadastro;
        }

        public String getLogin() {
            return login;
        }

        /** Somente no cadastro; {@code null} no login. */
        public String getApelido() {
            return apelido;
        }

        /** A própria senha (não uma cópia); apagada por {@link #limpar()}. */
        public char[] getSenha() {
            return senha;
        }

        public void limpar() {
            Arrays.fill(senha, '\0');
        }

        @Override
        public String toString() {
            return (cadastro ? COMANDO_CADASTRAR : COMANDO_ENTRAR) + " " + login + " ****";
        }
    }

    public static String comandoEntrar(String login, char[] senha) {
        return COMANDO_ENTRAR + " " + codificar(login) + " " + codificarSenha(senha);
    }

    public static String comandoCadastrar(String login, String apelido, char[] senha) {
        return COMANDO_CADASTRAR + " " + codificar(login) + " " + codificar(apelido)
                + " " + codificarSenha(senha);
    }

    /**
     * @throws IllegalArgumentException se o comando for desconhecido ou
     *                                  malformado (mensagem sem dados recebidos)
     */
    public static PedidoAutenticacao lerPedido(String linha) {

        String[] partes = Objects.requireNonNullElse(linha, "").split(" ", -1);

        if (partes.length == 3 && COMANDO_ENTRAR.equals(partes[0])) {
            String login = decodificar(partes[1], MAXIMO_CAMPO_TEXTO);
            return new PedidoAutenticacao(false, login, null, decodificarSenha(partes[2]));
        }

        if (partes.length == 4 && COMANDO_CADASTRAR.equals(partes[0])) {
            String login = decodificar(partes[1], MAXIMO_CAMPO_TEXTO);
            String apelido = decodificar(partes[2], MAXIMO_CAMPO_TEXTO);
            return new PedidoAutenticacao(true, login, apelido, decodificarSenha(partes[3]));
        }

        throw new IllegalArgumentException("Comando de autenticação inválido.");
    }

    // ------------------------------------------------------------------
    // Resposta do servidor
    // ------------------------------------------------------------------

    /** Resultado lido pelo cliente: perfil (sucesso) ou código e mensagem. */
    public record RespostaAutenticacao(PerfilUsuario perfil, String codigo, String mensagem) {

        public boolean sucesso() {
            return perfil != null;
        }
    }

    public static String respostaSucesso(PerfilUsuario perfil) {
        return PREFIXO_AUTH_OK + perfil.getId() + " " + codificar(perfil.getLogin()) + " "
                + codificar(perfil.getApelido()) + " " + codificar(perfil.getAvatarId());
    }

    public static String respostaErro(String codigo, String mensagem) {
        if (!codigo.matches("[A-Z_]{1,40}")) {
            throw new IllegalArgumentException("Código de erro inválido.");
        }
        return PREFIXO_AUTH_ERRO + codigo + " " + codificar(mensagem);
    }

    public static String negociacaoRecusada(String mensagem) {
        return PREFIXO_NEGOCIACAO_RECUSADA + codificar(mensagem);
    }

    /** Mensagem de uma recusa de negociação, ou {@code null} se a linha não for uma. */
    public static String lerNegociacaoRecusada(String linha) {
        if (linha == null || !linha.startsWith(PREFIXO_NEGOCIACAO_RECUSADA)) {
            return null;
        }
        return decodificar(linha.substring(PREFIXO_NEGOCIACAO_RECUSADA.length()), MAXIMO_MENSAGEM);
    }

    /** @throws IllegalArgumentException se a resposta for malformada */
    public static RespostaAutenticacao lerResposta(String linha) {

        String texto = Objects.requireNonNullElse(linha, "");

        if (texto.startsWith(PREFIXO_AUTH_OK)) {
            String[] partes = texto.substring(PREFIXO_AUTH_OK.length()).split(" ", -1);
            if (partes.length == 4) {
                PerfilUsuario perfil = new PerfilUsuario(
                        Long.parseLong(partes[0]),
                        decodificar(partes[1], MAXIMO_CAMPO_TEXTO),
                        decodificar(partes[2], MAXIMO_CAMPO_TEXTO),
                        decodificar(partes[3], MAXIMO_CAMPO_TEXTO)
                );
                return new RespostaAutenticacao(perfil, null, null);
            }
        }

        if (texto.startsWith(PREFIXO_AUTH_ERRO)) {
            String[] partes = texto.substring(PREFIXO_AUTH_ERRO.length()).split(" ", -1);
            if (partes.length == 2 && partes[0].matches("[A-Z_]{1,40}")) {
                return new RespostaAutenticacao(null, partes[0], decodificar(partes[1], MAXIMO_MENSAGEM));
            }
        }

        throw new IllegalArgumentException("Resposta de autenticação inválida.");
    }

    // ------------------------------------------------------------------
    // Codificação
    // ------------------------------------------------------------------

    static String codificar(String texto) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    static String decodificar(String campo, int maximoCaracteres) {

        if (campo.isEmpty() || campo.length() > maximoCaracteres * 6) {
            throw new IllegalArgumentException("Campo vazio ou longo demais.");
        }

        byte[] bytes = Base64.getUrlDecoder().decode(campo);

        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();

            if (texto.length() > maximoCaracteres) {
                throw new IllegalArgumentException("Campo longo demais.");
            }

            return texto;

        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Campo com UTF-8 inválido.");
        }
    }

    /** Codifica sem criar uma String com a senha; apaga os bytes intermediários. */
    static String codificarSenha(char[] senha) {

        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(senha));
        byte[] copia = new byte[bytes.remaining()];
        bytes.get(copia);

        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(copia);
        } finally {
            Arrays.fill(copia, (byte) 0);
            if (bytes.hasArray()) {
                Arrays.fill(bytes.array(), (byte) 0);
            }
        }
    }

    /** Decodifica direto para char[], apagando os buffers intermediários. */
    static char[] decodificarSenha(String campo) {

        if (campo.isEmpty() || campo.length() > MAXIMO_CAMPO_SENHA * 6) {
            throw new IllegalArgumentException("Campo vazio ou longo demais.");
        }

        byte[] bytes = Base64.getUrlDecoder().decode(campo);
        CharBuffer caracteres = null;

        try {
            caracteres = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));

            if (caracteres.remaining() > MAXIMO_CAMPO_SENHA) {
                throw new IllegalArgumentException("Campo longo demais.");
            }

            char[] senha = new char[caracteres.remaining()];
            caracteres.get(senha);
            return senha;

        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Campo com UTF-8 inválido.");

        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (caracteres != null && caracteres.hasArray()) {
                Arrays.fill(caracteres.array(), '\0');
            }
        }
    }
}
