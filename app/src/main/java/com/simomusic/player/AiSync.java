package com.simomusic.player;

import android.content.Context;
import android.net.Uri;

import java.util.List;

public final class AiSync {
    private AiSync() {
    }

    public static final int DONE = 0, NO_ENGINE = 1, WEAK = 2, FAILED = 3, CANCELLED = 4, BAD_KEY = 5, LIMIT = 6;

    public interface Cb {
        void done(LyricsRepo.Lyrics l, int status);
    }

    static String prompt(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (String s : lines) {
            String t = s == null ? "" : s.trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(t);
            if (sb.length() >= 180) break;
        }
        return sb.length() > 240 ? sb.substring(0, 240) : sb.toString();
    }

    public static void run(Context c, String key, Uri audio, LyricsRepo.Lyrics plain, long durMs, String lang,
                           SpeechEngine eng, String apiKey, java.util.function.BooleanSupplier cancelled, Cb cb) {
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            LyricsRepo.Lyrics res = null;
            int st = FAILED;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
                boolean online = apiKey != null && !apiKey.trim().isEmpty();
                boolean phone = eng != null && eng.ready(app);
                if (!online && !phone) {
                    st = NO_ENGINE;
                } else {
                    short[] pcm = AudioPcm.decode16kMono(app, audio, cancelled);
                    if (pcm == null || cancelled.getAsBoolean()) {
                        st = CANCELLED;
                    } else {
                        String prompt = prompt(plain.lines);
                        LyricsRepo.Anchors an = null;
                        int fail = 0;
                        if (online) {
                            try {
                                List<LyricsAligner.Word> heard = GroqTranscriber.transcribeWords(apiKey.trim(), pcm, lang, prompt);
                                an = LyricsAligner.align(plain.lines, heard);
                                if (an == null) fail = WEAK;
                            } catch (GroqTranscriber.ApiException e) {
                                fail = e.code == 401 ? BAD_KEY : (e.code == 429 ? LIMIT : FAILED);
                            } catch (java.io.IOException e) {
                                fail = FAILED;
                            }
                        }
                        if (an == null && phone && !cancelled.getAsBoolean() && fail != BAD_KEY) {
                            List<LyricsAligner.Word> heard = eng.transcribe(app, pcm, lang, prompt);
                            an = LyricsAligner.align(plain.lines, heard);
                            if (an == null) {
                                List<LyricsAligner.Word> bare = eng.transcribe(app, pcm, lang);
                                an = LyricsAligner.align(plain.lines, bare);
                            }
                            fail = an == null ? WEAK : 0;
                        }
                        if (an == null) {
                            st = cancelled.getAsBoolean() ? CANCELLED : (fail == 0 ? WEAK : fail);
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
