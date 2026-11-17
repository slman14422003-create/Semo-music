package com.simomusic.player;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

/**
 * Volume boost above the system maximum (up to 200%, like VLC): a software gain right before the AudioTrack with a
 * soft knee, so peaks bend smoothly instead of clipping hard. At 100% the audio is copied through untouched.
 */
@OptIn(markerClass = UnstableApi.class)
public final class BoostProcessor extends BaseAudioProcessor {
    public static final BoostProcessor INSTANCE = new BoostProcessor();

    private static final float KNEE = 0.6f;
    private volatile float gain = 1f;
    private boolean isFloat;

    private BoostProcessor() {
    }

    public void setGain(float g) {
        gain = Math.max(1f, Math.min(2f, g));
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat in) {
        if (in.encoding == C.ENCODING_PCM_16BIT) isFloat = false;
        else if (in.encoding == C.ENCODING_PCM_FLOAT) isFloat = true;
        else return AudioFormat.NOT_SET;       // anything unusual plays untouched
        return in;
    }

    @Override
    public void queueInput(ByteBuffer in) {
        int bytes = in.remaining();
        if (bytes == 0) return;
        ByteBuffer out = replaceOutputBuffer(bytes);
        final float g = gain;
        if (g <= 1.001f) {
            out.put(in);
        } else if (isFloat) {
            while (in.remaining() >= 4) out.putFloat(soft(in.getFloat() * g));
            out.put(in);
        } else {
            while (in.remaining() >= 2) {
                float y = soft(in.getShort() * (g / 32768f));
                int q = Math.round(y * 32767f);
                out.putShort((short) (q > 32767 ? 32767 : (q < -32768 ? -32768 : q)));
            }
            out.put(in);
        }
        out.flip();
    }

    /** Linear below the knee, then a smooth curve that never passes full scale. */
    private static float soft(float x) {
        float a = x < 0 ? -x : x;
        if (a <= KNEE) return x;
        float t = (a - KNEE) / (1f - KNEE);
        float th = t >= 3f ? 1f : t * (27f + t * t) / (27f + 9f * t * t);   // tanh approximation
        float y = KNEE + (1f - KNEE) * th;
        return x < 0 ? -y : y;
    }
}
