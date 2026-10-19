package com.simomusic.player;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Decodes a song to 16 kHz mono 16-bit PCM, the input speech engines expect. */
public final class AudioPcm {
    private AudioPcm() {
    }

    public static short[] decode16kMono(Context c, Uri uri, java.util.function.BooleanSupplier cancelled) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0) throw new IllegalStateException("no audio track");
            ex.selectTrack(track);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int ch = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            short[] out = new short[16000 * 60];
            int n = 0;
            double step = 0, acc = 0;
            double src = 0; // fractional source-frame position for resampling
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false, outDone = false;
            while (!outDone) {
                if (cancelled != null && cancelled.getAsBoolean()) return null;
                if (!inDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int sz = ex.readSampleData(ib, 0);
                        if (sz < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    ob.position(info.offset);
                    ob.limit(info.offset + info.size);
                    java.nio.ShortBuffer sb = ob.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
                    int frames = sb.remaining() / Math.max(1, ch);
                    step = rate / 16000.0;
                    for (int f = 0; f < frames; f++) {
                        // average channels of this frame, then keep one frame per `step` (box-ish downsample)
                        int s = 0;
                        for (int k = 0; k < ch; k++) s += sb.get(f * ch + k);
                        acc += s / (double) ch;
                        src += 1;
                        if (src >= step) {
                            double cnt = src;
                            short v = (short) Math.max(-32768, Math.min(32767, Math.round(acc / cnt)));
                            if (n == out.length) out = Arrays.copyOf(out, out.length * 2);
                            out[n++] = v;
                            acc = 0;
                            src -= step;
                        }
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                }
            }
            return Arrays.copyOf(out, n);
        } finally {
            try {
                if (codec != null) codec.stop();
            } catch (Throwable ignored) {
            }
            if (codec != null) codec.release();
            ex.release();
        }
    }
}
