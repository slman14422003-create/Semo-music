package com.simomusic.player;

import android.content.Context;
import android.net.Uri;

import java.util.List;

/** "Sync with AI": decode -> transcribe -> align to the known lyrics -> cache. Run once per song. */
public final class AiSync {
    private AiSync() {
    }

    public static final int DONE = 0, NO_ENGINE = 1, WEAK = 2, FAILED = 3, CANCELLED = 4;

    public interface Cb {
        void done(LyricsRepo.Lyrics l, int status);
    }

    public static void run(Context c, String key, Uri audio, LyricsRepo.Lyrics plain, long durMs, String lang,
                           SpeechEngine eng, java.util.function.BooleanSupplier cancelled, Cb cb) {
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            LyricsRepo.Lyrics res = null;
            int st = FAILED;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
                if (eng == null || !eng.ready(app)) {
                    st = NO_ENGINE;
                } else {
                    short[] pcm = AudioPcm.decode16kMono(app, audio, cancelled);
                    if (pcm == null || cancelled.getAsBoolean()) {
                        st = CANCELLED;
                    } else {
                        List<LyricsAligner.Word> heard = eng.transcribe(app, pcm, lang);
                        LyricsRepo.Anchors an = LyricsAligner.align(plain.lines, heard);
                        if (an == null) {
                            st = WEAK;
                        } else {
                            res = LyricsRepo.aligned(plain, an, durMs);
                            LyricsRepo.store(app, key, res);
                            st = DONE;
                        }
                    }
                }
            } catch (Throwable e) {
                CrashGuard.nonFatal("ai sync", e);
            }
            final LyricsRepo.Lyrics fr = res;
            final int fs = st;
            Bg.main(() -> cb.done(fr, fs));
        }, "ai-sync");
        t.setDaemon(true);
        t.start();
    }
}
