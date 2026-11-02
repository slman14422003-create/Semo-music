#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include <vector>
#include "whisper.h"

#define TAG "SemoWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::atomic<bool> g_cancel(false);

static bool abort_cb(void *) { return g_cancel.load(); }

// Words of the last transcription, one entry per whisper segment (max_len = 1 gives ~one word each).
static std::vector<std::string> g_text;
static std::vector<jlong> g_t0;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_simomusic_player_WhisperLib_nativeInit(JNIEnv *env, jclass, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_simomusic_player_WhisperLib_nativeFree(JNIEnv *, jclass, jlong ptr) {
    if (ptr) whisper_free((whisper_context *) ptr);
}

JNIEXPORT void JNICALL
Java_com_simomusic_player_WhisperLib_nativeCancel(JNIEnv *, jclass) {
    g_cancel.store(true);
}

// returns 0 on success
JNIEXPORT jint JNICALL
Java_com_simomusic_player_WhisperLib_nativeTranscribe(JNIEnv *env, jclass, jlong ptr, jfloatArray pcm,
                                                      jint threads, jstring lang, jboolean words) {
    auto *ctx = (whisper_context *) ptr;
    g_cancel.store(false);
    g_text.clear();
    g_t0.clear();

    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    jsize n = env->GetArrayLength(pcm);
    const char *l = lang ? env->GetStringUTFChars(lang, nullptr) : nullptr;
    std::string language = (l && *l) ? l : "auto";
    if (l) env->ReleaseStringUTFChars(lang, l);

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.print_realtime = false;
    wp.print_progress = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    wp.translate = false;
    wp.language = language.c_str();
    wp.detect_language = false;
    wp.n_threads = threads;
    wp.no_context = true;
    wp.single_segment = false;
    wp.token_timestamps = words;  // word-level times (for syncing known lyrics)
    wp.max_len = words ? 1 : 0;   // one word per segment, or natural phrases (for generating lyrics)
    wp.split_on_word = words;
    wp.abort_callback = abort_cb;
    wp.abort_callback_user_data = nullptr;

    int rc = whisper_full(ctx, wp, data, n);
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    if (rc != 0) {
        LOGI("whisper_full failed: %d", rc);
        return rc;
    }
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        const char *t = whisper_full_get_segment_text(ctx, i);
        g_text.emplace_back(t ? t : "");
        g_t0.push_back((jlong) whisper_full_get_segment_t0(ctx, i));
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_simomusic_player_WhisperLib_nativeCount(JNIEnv *, jclass) { return (jint) g_text.size(); }

JNIEXPORT jstring JNICALL
Java_com_simomusic_player_WhisperLib_nativeText(JNIEnv *env, jclass, jint i) {
    return env->NewStringUTF(g_text[i].c_str());
}

// centiseconds (10 ms units)
JNIEXPORT jlong JNICALL
Java_com_simomusic_player_WhisperLib_nativeT0(JNIEnv *, jclass, jint i) { return g_t0[i]; }

}
