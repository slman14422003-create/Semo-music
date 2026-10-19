package com.simomusic.player;

/** JNI bridge to whisper.cpp (libsemowhisper.so, built from app/src/main/cpp). */
final class WhisperLib {
    private WhisperLib() {
    }

    static final boolean LOADED;

    static {
        boolean ok;
        try {
            System.loadLibrary("semowhisper");
            ok = true;
        } catch (Throwable e) {
            ok = false; // unsupported ABI: the feature simply stays hidden
        }
        LOADED = ok;
    }

    static native long nativeInit(String modelPath);

    static native void nativeFree(long ctx);

    static native void nativeCancel();

    static native int nativeTranscribe(long ctx, float[] pcm, int threads, String lang);

    static native int nativeCount();

    static native String nativeText(int i);

    static native long nativeT0(int i);
}
