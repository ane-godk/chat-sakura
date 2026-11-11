package br.unicesumar.chat.service;

import br.unicesumar.chat.model.PerfilUsuario;
import br.unicesumar.chat.model.RegrasConta;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.persistence.UsuarioRepository.ContaArmazenada;
import br.unicesumar.chat.persistence.UsuarioRepository.ContaDuplicadaException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Cadastro e autenticação de contas.
 *
 * Os métodos recebem a senha como {@code char[]} e a apagam ao terminar,
 * com sucesso ou não. Só dados públicos ({@link PerfilUsuario}) são
 * devolvidos. Erros internos viram {@link AutenticacaoException.Codigo#INDISPONIVEL}
 * com mensagem genérica; o log registra apenas a operação e o tipo do erro.
 */
public final class ServicoAutenticacao {

    static final String MENSAGEM_CREDENCIAIS = "Login ou senha incorretos.";
    static final String MENSAGEM_INDISPONIVEL =
            "Não foi possível acessar as contas agora. Tente novamente.";

    private final UsuarioRepository repositorio;
    private final HashSenha hashSenha;
    private final Clock relogio;

    /** Serializa verificação e inserção de cadastros (o hash fica fora). */
    private final Object bloqueioCadastro = new Object();

    public ServicoAutenticacao(UsuarioRepository repositorio, HashSenha hashSenha, Clock relogio) {
        this.repositorio = Objects.requireNonNull(repositorio, "Repositório obrigatório.");
        this.hashSenha = Objects.requireNonNull(hashSenha, "Hash de senha obrigatório.");
        this.relogio = Objects.requireNonNull(relogio, "Relógio obrigatório.");
    }

    public PerfilUsuario cadastrar(String login, String apelido, char[] senha)
            throws AutenticacaoException {

        try {
            String loginNormalizado;
            String apelidoNormalizado;

            try {
                loginNormalizado = RegrasConta.normalizarLogin(login);
                apelidoNormalizado = RegrasConta.normalizarApelido(apelido);
                RegrasConta.validarSenha(senha);
            } catch (IllegalArgumentException e) {
                throw new AutenticacaoException(AutenticacaoException.Codigo.DADOS_INVALIDOS, e.getMessage());
            }

            HashSenha.Credenciais credenciais = hashSenha.gerar(senha);

            synchronized (bloqueioCadastro) {
                return repositorio.cadastrar(
                        loginNormalizado,
                        apelidoNormalizado,
                        credenciais.hash(),
                        credenciais.sal(),
                        RegrasConta.AVATAR_PADRAO,
                        LocalDateTime.now(relogio)
                );
            }

        } catch (ContaDuplicadaException e) {
            throw e.getCampo() == ContaDuplicadaException.Campo.LOGIN
                    ? new AutenticacaoException(AutenticacaoException.Codigo.LOGIN_EM_USO,
                            "Este login já está em uso.")
                    : new AutenticacaoException(AutenticacaoException.Codigo.APELIDO_EM_USO,
                            "Este apelido já está em uso.");

        } catch (RuntimeException e) {
            registrarErroInterno("cadastro", e);
            throw new AutenticacaoException(AutenticacaoException.Codigo.INDISPONIVEL, MENSAGEM_INDISPONIVEL);

        } finally {
            apagar(senha);
        }
    }

    /**
     * Login inexistente e senha incorreta têm a mesma resposta e custo
     * semelhante, para não revelar quais contas existem.
     */
    public PerfilUsuario autenticar(String login, char[] senha) throws AutenticacaoException {

        try {
            String loginNormalizado;

            try {
                loginNormalizado = RegrasConta.normalizarLogin(login);
                RegrasConta.validarSenha(senha);
            } catch (IllegalArgumentException e) {
                hashSenha.simularVerificacao(senha);
                throw credenciaisInvalidas();
            }

            Optional<ContaArmazenada> conta = repositorio.buscarPorLogin(loginNormalizado);

            if (conta.isEmpty()) {
                hashSenha.simularVerificacao(senha);
                throw credenciaisInvalidas();
            }

            if (!hashSenha.verificar(senha, conta.get().senhaHash(), conta.get().senhaSal())) {
                throw credenciaisInvalidas();
            }

            return conta.get().perfil();

        } catch (RuntimeException e) {
            registrarErroInterno("login", e);
            throw new AutenticacaoException(AutenticacaoException.Codigo.INDISPONIVEL, MENSAGEM_INDISPONIVEL);

        } finally {
            apagar(senha);
        }
    }

    /**
     * Troca o avatar da conta da sessão por um da galeria.
     *
     * @return o perfil público atualizado
     */
    public PerfilUsuario definirAvatar(br.unicesumar.chat.model.Usuario usuario, String avatarId)
            throws AutenticacaoException {

        if (usuario == null || usuario.getId() == null) {
            throw new AutenticacaoException(AutenticacaoException.Codigo.DADOS_INVALIDOS,
                    "Entre com uma conta para escolher um avatar.");
        }

        String avatar;
        try {
            avatar = RegrasConta.validarAvatar(avatarId);
        } catch (IllegalArgumentException e) {
            throw new AutenticacaoException(AutenticacaoException.Codigo.DADOS_INVALIDOS, e.getMessage());
        }

        try {
            if (!repositorio.atualizarAvatar(usuario.getId(), avatar)) {
                throw new AutenticacaoException(AutenticacaoException.Codigo.DADOS_INVALIDOS, "Conta não encontrada.");
            }
            return repositorio.buscarPorId(usuario.getId()).orElseThrow();
        } catch (RuntimeException e) {
            registrarErroInterno("avatar", e);
            throw new AutenticacaoException(AutenticacaoException.Codigo.INDISPONIVEL, MENSAGEM_INDISPONIVEL);
        }
    }

    /**
     * {@code true} se o nome pertence a uma conta (como login ou apelido).
     * Sessões sem conta não podem usá-lo. Em caso de falha interna responde
     * {@code true}: na dúvida, o nome não é liberado.
     */
    public boolean nomeReservado(String nome) {

        if (nome == null || nome.isBlank()) {
            return false;
        }

        try {
            return repositorio.nomeEmUso(nome.strip());
        } catch (RuntimeException e) {
            registrarErroInterno("verificação de nome", e);
            return true;
        }
    }

    private static AutenticacaoException credenciaisInvalidas() {
        return new AutenticacaoException(AutenticacaoException.Codigo.CREDENCIAIS_INVALIDAS, MENSAGEM_CREDENCIAIS);
    }

    private static void apagar(char[] senha) {
        if (senha != null) {
            Arrays.fill(senha, '\0');
        }
    }

    /** Só a operação e o tipo do erro: nenhuma mensagem com dados. */
    private static void registrarErroInterno(String operacao, RuntimeException e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        System.err.println(
                "Chat Sakura (contas): erro interno no " + operacao + ": "
                        + e.getClass().getSimpleName() + " / " + causa.getClass().getSimpleName()
        );
    }
}
