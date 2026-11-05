package br.unicesumar.chat.client;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Reprodução com {@code javax.sound.sampled}.
 *
 * Uma única thread (daemon) carrega cada som uma vez em um {@link Clip}
 * reutilizável e atende os pedidos de reprodução; quem chama nunca é
 * bloqueado (nem a EDT). Sem dispositivo de áudio ou com algum arquivo
 * ausente, os sons afetados ficam indisponíveis e o chat segue normalmente.
 */
final class ReprodutorJavaSound implements ReprodutorSom {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "sakura-sons");
        thread.setDaemon(true);
        return thread;
    });

    /** Acessado somente pela thread "sakura-sons". */
    private final Map<SomSakura, Clip> clips = new EnumMap<>(SomSakura.class);

    private final Function<SomSakura, String> recursos;
    private volatile boolean avisouFalha;

    ReprodutorJavaSound() {
        this(SomSakura::recurso);
    }

    /** @param recursos caminho do recurso de cada som (substituível nos testes) */
    ReprodutorJavaSound(Function<SomSakura, String> recursos) {
        this.recursos = recursos;
        enviar(this::carregarTodos);
    }

    private void carregarTodos() {
        for (SomSakura som : SomSakura.values()) {
            try {
                Clip clip = carregar(recursos.apply(som));
                if (clip != null) {
                    clips.put(som, clip);
                }
            } catch (Exception | LinkageError e) {
                avisar("não foi possível preparar " + som + ": " + e.getMessage());
            }
        }
    }

    private Clip carregar(String recurso) throws Exception {

        InputStream bruto = ReprodutorJavaSound.class.getResourceAsStream(recurso);

        if (bruto == null) {
            avisar("arquivo de som não encontrado: " + recurso);
            return null;
        }

        try (AudioInputStream audio = AudioSystem.getAudioInputStream(
                new BufferedInputStream(bruto))) {
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            return clip;
        }
    }

    @Override
    public void tocar(SomSakura som) {
        enviar(() -> {
            Clip clip = clips.get(som);
            if (clip == null) {
                return;
            }
            try {
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            } catch (RuntimeException e) {
                avisar("falha ao tocar " + som + ": " + e.getMessage());
            }
        });
    }

    /** Indica se o som foi preparado (aguarda o carregamento inicial). */
    boolean disponivel(SomSakura som) {
        try {
            return executor.submit(() -> clips.containsKey(som)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void fechar() {
        enviar(() -> {
            clips.values().forEach(Clip::close);
            clips.clear();
        });
        executor.shutdown();
    }

    private void enviar(Runnable tarefa) {
        try {
            executor.execute(tarefa);
        } catch (RejectedExecutionException e) {
            // Já encerrado: ignora pedidos tardios.
        }
    }

    private void avisar(String mensagem) {
        if (!avisouFalha) {
            avisouFalha = true;
            System.err.println("Chat Sakura (sons): " + mensagem
                    + " — o chat continua sem esse som.");
        }
    }
}
