package com.simomusic.player;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes the lyrics from the song itself (speech recognition) when no lyrics exist online. Cached per song. */
public final class AiLyrics {
    private AiLyrics() {
    }

    public static final int DONE = 0, NO_ENGINE = 1, WEAK = 2, FAILED = 3, CANCELLED = 4, BAD_KEY = 5, LIMIT = 6;

    public interface Cb {
        void done(LyricsRepo.Lyrics l, int status);
    }

    public static final int STAGE_DECODE = 0, STAGE_ONLINE = 1, STAGE_PHONE = 2;

    public interface Stage {
        void at(int stage);
    }

    /** Short description of the last failure, shown to the user so problems can be diagnosed. */
    public static volatile String lastError = null;

    /** apiKey != null -> online (Groq); apiKey == null -> on this phone (Whisper). */
    public static void run(Context c, String key, Uri audio, long durMs, String lang, String apiKey,
                           java.util.function.BooleanSupplier cancelled, Stage stage, Cb cb) {
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            LyricsRepo.Lyrics res = null;
            int st = FAILED;
            lastError = null;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
                WhisperEngine local = new WhisperEngine();
                if (apiKey == null && !local.ready(app)) {
                    st = NO_ENGINE;
                } else {
                    Bg.main(() -> stage.at(STAGE_DECODE));
                    short[] pcm = AudioPcm.decode16kMono(app, audio, cancelled);
                    if (pcm == null || cancelled.getAsBoolean()) {
                        st = CANCELLED;
                    } else {
                        final int next = apiKey != null ? STAGE_ONLINE : STAGE_PHONE;
                        Bg.main(() -> stage.at(next));
                        List<LyricsAligner.Word> heard = apiKey != null
                                ? GroqTranscriber.transcribe(apiKey, pcm, lang)
                                : local.transcribePhrases(app, pcm, lang);
                        res = build(heard);
                        if (res == null) {
                            st = WEAK;
                        } else {
                            LyricsRepo.store(app, key, res);
                            st = DONE;
                        }
                    }
                }
            } catch (GroqTranscriber.ApiException e) {
                st = e.code == 401 ? BAD_KEY : (e.code == 429 ? LIMIT : FAILED);
                lastError = e.getMessage() != null ? e.getMessage() : "HTTP " + e.code;
            } catch (Throwable e) {
                CrashGuard.nonFatal("ai lyrics", e);
                lastError = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            }
            final LyricsRepo.Lyrics fr = res;
            final int fs = st;
            Bg.main(() -> cb.done(fr, fs));
        }, "ai-lyrics");
        t.setDaemon(true);
        t.start();
    }

    /** Cleans the recognised phrases into lines; returns null when it looks like noise or a looping hallucination. */
    static LyricsRepo.Lyrics build(List<LyricsAligner.Word> heard) {
        List<String> lines = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        String prev = null;
        for (LyricsAligner.Word w : heard) {
            String t = w.text.trim();
            if (t.isEmpty() || t.equals(prev)) continue;   // models sometimes repeat one phrase over and over
            prev = t;
            lines.add(t);
            times.add(w.startMs);
        }
        if (lines.size() < 4) return null;
        Map<String, Integer> freq = new HashMap<>();
        int worst = 0;
        for (String l : lines) worst = Math.max(worst, freq.merge(l, 1, Integer::sum));
        if (worst * 2 > lines.size()) return null;
        long[] tm = new long[lines.size()];
        for (int i = 0; i < tm.length; i++) {
            tm[i] = times.get(i);
            if (i > 0 && tm[i] < tm[i - 1] + 300) tm[i] = tm[i - 1] + 300;
        }
        return new LyricsRepo.Lyrics(true, false, tm, lines.toArray(new String[0]), "ai-gen", false, null);
    }
}
