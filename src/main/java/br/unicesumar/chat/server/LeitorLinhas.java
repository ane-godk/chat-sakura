package br.unicesumar.chat.server;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * Leitura de linhas com tamanho máximo, para que um cliente não consiga
 * esgotar a memória do servidor com uma linha sem fim. Aceita os mesmos
 * terminadores de {@link java.io.BufferedReader#readLine()} (\n, \r e \r\n).
 *
 * Uma linha longa demais é descartada até o terminador (sem guardá-la) e
 * sinalizada com {@link LinhaMuitoLongaException}; a próxima leitura
 * continua normalmente.
 */
final class LeitorLinhas {

    static final class LinhaMuitoLongaException extends IOException {

        private static final long serialVersionUID = 1L;

        LinhaMuitoLongaException(int maximo) {
            super("A linha excede o limite de " + maximo + " caracteres.");
        }
    }

    private final Reader leitor;
    private final int maximo;

    private boolean ignorarProximoLf;

    // Estado da linha em andamento: preservado se a leitura for interrompida
    // por tempo limite do socket (SocketTimeoutException), para continuar
    // de onde parou na próxima chamada.
    private final StringBuilder linha = new StringBuilder();
    private boolean leuAlgo;
    private boolean excedeu;

    LeitorLinhas(Reader leitor, int maximo) {
        this.leitor = Objects.requireNonNull(leitor);
        if (maximo < 1) {
            throw new IllegalArgumentException("Limite inválido.");
        }
        this.maximo = maximo;
    }

    /** @return a linha sem o terminador, ou {@code null} no fim do fluxo */
    String lerLinha() throws IOException {

        while (true) {
            int lido = leitor.read();

            if (lido == '\n' && ignorarProximoLf) {
                ignorarProximoLf = false;
                continue;
            }
            ignorarProximoLf = false;

            if (lido == -1) {
                if (excedeu) {
                    reiniciar();
                    throw new LinhaMuitoLongaException(maximo);
                }
                String resultado = leuAlgo ? linha.toString() : null;
                reiniciar();
                return resultado;
            }

            leuAlgo = true;

            if (lido == '\n' || lido == '\r') {
                ignorarProximoLf = lido == '\r';
                boolean longa = excedeu;
                String resultado = linha.toString();
                reiniciar();
                if (longa) {
                    throw new LinhaMuitoLongaException(maximo);
                }
                return resultado;
            }

            if (!excedeu) {
                if (linha.length() < maximo) {
                    linha.append((char) lido);
                } else {
                    excedeu = true;
                    linha.setLength(0);
                }
            }
        }
    }

    private void reiniciar() {
        linha.setLength(0);
        leuAlgo = false;
        excedeu = false;
    }
}
