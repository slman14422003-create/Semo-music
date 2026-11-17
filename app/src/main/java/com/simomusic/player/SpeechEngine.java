package com.simomusic.player;

import android.content.Context;

import java.util.List;

public interface SpeechEngine {
    boolean ready(Context c);

    List<LyricsAligner.Word> transcribe(Context c, short[] pcm, String lang) throws Exception;

    default List<LyricsAligner.Word> transcribe(Context c, short[] pcm, String lang, String prompt) throws Exception {
        return transcribe(c, pcm, lang);
    }
}
