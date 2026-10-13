package app.ui;

import app.config.Settings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.Toolkit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two sounds the line can tell apart from a metre away without looking: OK is
 * one short high tone, ERROR is two low ones. Generated in memory with
 * javax.sound.sampled — no audio files. No sound card means silence, never an
 * exception; Toolkit.beep() stays only as the last fallback for the error.
 */
public final class Sounds {

    private static final float RATE = 22050f;
    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sounds");
        t.setDaemon(true);
        return t;
    });

    private Sounds() {}

    public static void ok() {
        play(new int[][] { { 1760, 90 } }, false);
    }

    public static void error() {
        play(new int[][] { { 330, 140 }, { 0, 70 }, { 330, 140 } }, true);
    }

    /** {frequency Hz (0 = silence), duration ms} segments, as 16-bit mono PCM. */
    static byte[] tone(int[][] segments, int volumePercent) {
        int total = 0;
        for (int[] s : segments) total += (int) (RATE * s[1] / 1000);
        byte[] pcm = new byte[total * 2];
        double amp = 32767 * Math.max(0, Math.min(100, volumePercent)) / 100.0 * 0.8;
        int i = 0;
        for (int[] s : segments) {
            int n = (int) (RATE * s[1] / 1000);
            int fade = Math.min(n / 4, (int) (RATE * 0.006));   // no clicks at the edges
            for (int k = 0; k < n; k++, i++) {
                double env = 1;
                if (k < fade) env = k / (double) fade;
                else if (k > n - fade) env = (n - k) / (double) fade;
                double v = s[0] == 0 ? 0 : Math.sin(2 * Math.PI * s[0] * k / RATE) * amp * env;
                short sv = (short) Math.round(v);
                pcm[2 * i] = (byte) (sv & 0xff);
                pcm[2 * i + 1] = (byte) ((sv >> 8) & 0xff);
            }
        }
        return pcm;
    }

    private static void play(int[][] segments, boolean beepFallback) {
        Settings cfg = Settings.getInstance();
        if (!cfg.getBool(Settings.SOUND_ON, true)) return;
        int volume = cfg.getInt(Settings.SOUND_VOLUME, 80);
        byte[] pcm = tone(segments, volume);
        PLAYER.execute(() -> {
            try {
                AudioFormat f = new AudioFormat(RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(f);
                line.open(f);
                line.start();
                line.write(pcm, 0, pcm.length);
                line.drain();
                line.close();
            } catch (Exception | LinkageError e) {
                if (beepFallback) {
                    try { Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
                }
            }
        });
    }
}
