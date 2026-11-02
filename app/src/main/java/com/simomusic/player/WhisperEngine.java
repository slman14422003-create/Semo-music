package com.simomusic.player;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** On-device Whisper "base" (multilingual). The model is downloaded once into app storage. */
public final class WhisperEngine implements SpeechEngine {
    public static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
    public static final long MODEL_MB = 142;
    private static final long MIN_BYTES = 100L * 1024 * 1024;
    private static final Object LOCK = new Object();

    public static File modelFile(Context c) {
        return new File(new File(c.getFilesDir(), "whisper"), "ggml-base.bin");
    }

    public static boolean supported() {
        return WhisperLib.LOADED;
    }

    public static boolean hasModel(Context c) {
        File f = modelFile(c);
        return f.isFile() && f.length() > MIN_BYTES;
    }

    @Override
    public boolean ready(Context c) {
        return WhisperLib.LOADED && hasModel(c);
    }

    public interface Progress {
        void percent(int p);
    }

    /** Blocking download of the model (run on a background thread). Resumes a partial file. Returns true on success. */
    public static boolean download(Context c, Progress cb, java.util.function.BooleanSupplier cancelled) {
        File dst = modelFile(c);
        File tmp = new File(dst.getPath() + ".part");
        HttpURLConnection con = null;
        try {
            dst.getParentFile().mkdirs();
            long have = tmp.isFile() ? tmp.length() : 0;
            con = (HttpURLConnection) new URL(MODEL_URL).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setInstanceFollowRedirects(true);
            if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
            int code = con.getResponseCode();
            boolean part = code == 206;
            if (code != 200 && !part) {
                if (code == 416) tmp.delete();
                return false;
            }
            if (!part) have = 0;
            long total = con.getContentLengthLong();
            if (total > 0) total += have;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp, part)) {
                byte[] buf = new byte[64 * 1024];
                long got = have;
                int last = -1, n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled.getAsBoolean()) return false;
                    out.write(buf, 0, n);
                    got += n;
                    if (total > 0) {
                        int p = (int) (got * 100 / total);
                        if (p != last) {
                            last = p;
                            cb.percent(p);
                        }
                    }
                }
            }
            if (tmp.length() < MIN_BYTES) {
                tmp.delete();
                return false;
            }
            if (dst.exists()) dst.delete();
            return tmp.renameTo(dst);
        } catch (Throwable e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** "ar" when the lyrics contain Arabic letters, otherwise let Whisper detect the language. */
    public static String guessLang(String[] lines) {
        int ar = 0, other = 0;
        for (String s : lines) {
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch >= 0x0600 && ch <= 0x06FF) ar++;
                else if (Character.isLetter(ch)) other++;
            }
        }
        return ar > other ? "ar" : null;
    }

    public static void cancel() {
        if (WhisperLib.LOADED) WhisperLib.nativeCancel();
    }

    @Override
    public List<LyricsAligner.Word> transcribe(Context c, short[] pcm, String lang) throws Exception {
        return run(c, pcm, lang, true);
    }

    /** Natural phrases with start times: used to write lyrics from scratch. */
    public List<LyricsAligner.Word> transcribePhrases(Context c, short[] pcm, String lang) throws Exception {
        return run(c, pcm, lang, false);
    }

    private List<LyricsAligner.Word> run(Context c, short[] pcm, String lang, boolean words) throws Exception {
        synchronized (LOCK) {
            float[] f = new float[pcm.length];
            for (int i = 0; i < f.length; i++) f[i] = pcm[i] / 32768f;
            long ctx = WhisperLib.nativeInit(modelFile(c).getAbsolutePath());
            if (ctx == 0) throw new IllegalStateException("model load failed");
            try {
                int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
                int rc = WhisperLib.nativeTranscribe(ctx, f, threads, lang, words);
                List<LyricsAligner.Word> out = new ArrayList<>();
                if (rc != 0) return out;
                int n = WhisperLib.nativeCount();
                for (int i = 0; i < n; i++) {
                    String t = WhisperLib.nativeText(i).trim();
                    if (t.isEmpty() || t.startsWith("[") || t.startsWith("(")) continue;
                    out.add(new LyricsAligner.Word(t, WhisperLib.nativeT0(i) * 10L));
                }
                return out;
            } finally {
                WhisperLib.nativeFree(ctx);
            }
        }
    }
}
