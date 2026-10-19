package com.simomusic.player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Online speech-to-text through Groq's hosted Whisper large-v3 (free plan with a personal API key). */
public final class GroqTranscriber {
    private GroqTranscriber() {
    }

    public static final String URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final long MAX_BYTES = 24L * 1024 * 1024;   // free plan upload limit is 25 MB

    public static final class ApiException extends IOException {
        public final int code;

        ApiException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build();

    static byte[] wav(short[] pcm) {
        int n = (int) Math.min(pcm.length, (MAX_BYTES - 44) / 2);
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + n * 2).put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        b.putInt(16).putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(n * 2);
        for (int i = 0; i < n; i++) b.putShort(pcm[i]);
        return b.array();
    }

    /** One entry per phrase the model heard, with its start time. lang may be null for auto-detect. */
    public static List<LyricsAligner.Word> transcribe(String apiKey, short[] pcm, String lang) throws IOException {
        MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "song.wav", RequestBody.create(wav(pcm), MediaType.parse("audio/wav")))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "segment")
                .addFormDataPart("temperature", "0");
        if (lang != null) mb.addFormDataPart("language", lang);
        Request req = new Request.Builder().url(URL)
                .header("Authorization", "Bearer " + apiKey.trim())
                .post(mb.build()).build();
        try (Response r = HTTP.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new ApiException(r.code(), "http " + r.code());
            List<LyricsAligner.Word> out = new ArrayList<>();
            JSONArray segs = new JSONObject(body).optJSONArray("segments");
            if (segs == null) return out;
            for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.getJSONObject(i);
                if (s.optDouble("no_speech_prob", 0) > 0.6) continue;
                String t = s.optString("text", "").trim();
                if (t.isEmpty()) continue;
                out.add(new LyricsAligner.Word(t, Math.round(s.optDouble("start", 0) * 1000)));
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("bad response", e);
        }
    }
}
