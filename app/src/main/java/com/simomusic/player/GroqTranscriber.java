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

public final class GroqTranscriber {
    private GroqTranscriber() {
    }

    public static final String URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final int CHUNK = 16000 * 600;
    private static final int OVERLAP = 16000 * 4;

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

    static byte[] wav(short[] pcm, int from, int to) {
        int n = to - from;
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + n * 2).put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        b.putInt(16).putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(n * 2);
        for (int i = from; i < to; i++) b.putShort(pcm[i]);
        return b.array();
    }

    private static final String[] MODELS = {"whisper-large-v3-turbo", "whisper-large-v3"};
    private static volatile int modelIdx = 0;

    private static JSONObject call(String apiKey, short[] pcm, int from, int to, String lang, String prompt, boolean words) throws IOException {
        ApiException miss = null;
        for (int m = 0; m < MODELS.length; m++) {
            int idx = (modelIdx + m) % MODELS.length;
            try {
                JSONObject o = callModel(MODELS[idx], apiKey, pcm, from, to, lang, prompt, words);
                modelIdx = idx;
                return o;
            } catch (ApiException e) {
                if (e.code != 404 && e.code != 400) throw e;
                miss = e;
            }
        }
        throw miss != null ? miss : new IOException("failed");
    }

    private static JSONObject callModel(String model, String apiKey, short[] pcm, int from, int to, String lang, String prompt, boolean words) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "song.wav", RequestBody.create(wav(pcm, from, to), MediaType.parse("audio/wav")))
                    .addFormDataPart("model", model)
                    .addFormDataPart("response_format", "verbose_json")
                    .addFormDataPart("timestamp_granularities[]", words ? "word" : "segment")
                    .addFormDataPart("temperature", "0");
            if (lang != null) mb.addFormDataPart("language", lang);
            if (prompt != null && !prompt.isEmpty()) mb.addFormDataPart("prompt", prompt);
            Request req = new Request.Builder().url(URL)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .post(mb.build()).build();
            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "";
                if (r.isSuccessful()) return new JSONObject(body);
                int code = r.code();
                if (code == 429 || code >= 500) {
                    long wait = 2000L * (attempt + 1);
                    String ra = r.header("retry-after");
                    if (ra != null) {
                        try {
                            wait = Math.min(15000L, (long) (Double.parseDouble(ra) * 1000));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    last = new ApiException(code, "http " + code);
                    if (attempt < 2) {
                        try {
                            Thread.sleep(wait);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw last;
                        }
                        continue;
                    }
                }
                String detail = "";
                try {
                    detail = new JSONObject(body).getJSONObject("error").optString("message", "");
                } catch (Exception ignored) {
                }
                throw new ApiException(code, detail.isEmpty() ? "http " + code : "http " + code + ": " + detail);
            } catch (org.json.JSONException e) {
                throw new IOException("bad response", e);
            } catch (ApiException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                if (attempt == 2) throw e;
            }
        }
        throw last != null ? last : new IOException("failed");
    }

    public static List<LyricsAligner.Word> transcribe(String apiKey, short[] pcm, String lang) throws IOException {
        return run(apiKey, pcm, lang, null, false);
    }

    public static List<LyricsAligner.Word> transcribeWords(String apiKey, short[] pcm, String lang, String prompt) throws IOException {
        return run(apiKey, pcm, lang, prompt, true);
    }

    private static List<LyricsAligner.Word> run(String apiKey, short[] pcm, String lang, String prompt, boolean words) throws IOException {
        List<LyricsAligner.Word> out = new ArrayList<>();
        int start = 0;
        long lastEnd = -1;
        while (start < pcm.length) {
            int end = Math.min(pcm.length, start + CHUNK);
            if (pcm.length - end < OVERLAP * 2) end = pcm.length;
            long offMs = start * 1000L / 16000L;
            JSONObject o = call(apiKey, pcm, start, end, lang, start == 0 ? prompt : null, words);
            JSONArray arr = o.optJSONArray(words ? "words" : "segments");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s == null) continue;
                    if (!words && (s.optDouble("no_speech_prob", 0) > 0.6 || s.optDouble("compression_ratio", 0) > 2.6 || s.optDouble("avg_logprob", 0) < -1.4)) continue;
                    String t = s.optString(words ? "word" : "text", "").trim();
                    if (t.isEmpty()) continue;
                    long ms = offMs + Math.round(s.optDouble("start", 0) * 1000);
                    if (start > 0 && ms <= lastEnd) continue;
                    out.add(new LyricsAligner.Word(t, ms));
                }
            }
            if (!out.isEmpty()) lastEnd = out.get(out.size() - 1).startMs;
            if (end >= pcm.length) break;
            start = end - OVERLAP;
        }
        return out;
    }
}
