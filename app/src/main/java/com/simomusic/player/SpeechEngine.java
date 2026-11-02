package com.simomusic.player;

import android.content.Context;

import java.util.List;

/** Offline speech-to-text with word times. Implemented by the Whisper (whisper.cpp) wrapper. */
public interface SpeechEngine {
    /** True when the model file is on the device and the native library loaded. */
    boolean ready(Context c);

    /** pcm = 16 kHz mono 16-bit. lang may be null for auto-detect. Runs on the calling (background) thread. */
    List<LyricsAligner.Word> transcribe(Context c, short[] pcm, String lang) throws Exception;
}
