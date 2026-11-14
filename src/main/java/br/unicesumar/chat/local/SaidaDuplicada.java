package br.unicesumar.chat.local;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Recebe texto em UTF-8 e o entrega a dois destinos: o arquivo de log (bytes
 * UTF-8, legíveis no Bloco de Notas) e a janela do console (como caracteres,
 * para o console usar a própria página de código e manter os acentos).
 *
 * Falhas no arquivo não interrompem a saída na janela.
 */
final class SaidaDuplicada extends OutputStream {

    private final PrintStream console;
    private final OutputStream arquivo;
    private final CharsetDecoder decodificador = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    // Bytes de um caractere que chegou dividido entre duas escritas.
    private final ByteBuffer pendente = ByteBuffer.allocate(8);
    private boolean arquivoAtivo = true;

    SaidaDuplicada(PrintStream console, OutputStream arquivo) {
        this.console = Objects.requireNonNull(console);
        this.arquivo = Objects.requireNonNull(arquivo);
    }

    @Override
    public synchronized void write(int b) {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public synchronized void write(byte[] dados, int inicio, int tamanho) {
        if (arquivoAtivo) {
            try {
                arquivo.write(dados, inicio, tamanho);
            } catch (IOException e) {
                arquivoAtivo = false;
            }
        }

        ByteBuffer entrada = ByteBuffer.allocate(pendente.position() + tamanho);
        pendente.flip();
        entrada.put(pendente);
        pendente.clear();
        entrada.put(dados, inicio, tamanho);
        entrada.flip();

        CharBuffer texto = CharBuffer.allocate(entrada.remaining() + 1);
        decodificador.decode(entrada, texto, false);
        texto.flip();
        console.print(texto);

        // Até 3 bytes de um caractere incompleto ficam para a próxima escrita.
        while (entrada.hasRemaining() && pendente.hasRemaining()) {
            pendente.put(entrada.get());
        }
    }

    @Override
    public synchronized void flush() {
        console.flush();
        if (arquivoAtivo) {
            try {
                arquivo.flush();
            } catch (IOException e) {
                arquivoAtivo = false;
            }
        }
    }
}
