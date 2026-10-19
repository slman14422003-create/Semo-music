package com.simomusic.player;

import android.content.Context;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class LyricsRepo {
    private LyricsRepo() {
    }

    public static final int OK = 0, MISS = 1, OFFLINE = 2;
    private static final String DEFAULT_HOST = "https://lrclib.net";
    private static final String OVH_HOST = "https://api.lyrics.ovh";
    private static final long MISS_TTL = 12L * 3600L * 1000L;

    public static final class Lyrics {
        public final boolean synced, instrumental;
        public final long[] times;
        public final String[] lines;
        public final String source;

        Lyrics(boolean synced, boolean instrumental, long[] times, String[] lines, String source) {
            this.synced = synced;
            this.instrumental = instrumental;
            this.times = times;
            this.lines = lines;
            this.source = source;
        }
    }

    public interface Cb {
        void done(Lyrics l, int status);
    }

    public static final class Query {
        public final String key, title, artist, album, rawTitle;
        public final long durMs;

        private Query(String key, String title, String artist, String album, String rawTitle, long durMs) {
            this.key = key;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.rawTitle = rawTitle;
            this.durMs = durMs;
        }

        public static Query of(String key, String title, String artist, String album, long durMs) {
            String t = title == null ? "" : title;
            String a = cleanArtist(artist);
            if (a.isEmpty()) {
                int i = t.indexOf(" - ");
                if (i > 0 && i < t.length() - 3) {
                    a = cleanArtist(t.substring(0, i));
                    t = t.substring(i + 3);
                }
            } else {
                String fa = Norm.fold(a);
                int i = t.indexOf(" - ");
                if (i > 0 && Norm.fold(t.substring(0, i)).trim().equals(fa.trim())) t = t.substring(i + 3);
            }
            String al = album == null ? "" : album.trim();
            if (al.equalsIgnoreCase("<unknown>") || al.equalsIgnoreCase("unknown")) al = "";
            return new Query(key, cleanTitle(t), a, al, t, durMs);
        }
    }

    private static final Pattern EXT = Pattern.compile("(?i)\\.(mp3|flac|wav|m4a|m4b|aac|ogg|oga|opus|amr|mka|mp4)$");
    private static final Pattern LEAD_NUM = Pattern.compile("^\\s*\\d{1,3}\\s*[-._)]\\s*");
    private static final Pattern BRACKETS = Pattern.compile("\\s*[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]");
    private static final Pattern FEAT = Pattern.compile("(?i)\\s+(feat\\.?|ft\\.?|featuring|with|prod\\.?)\\s+.*$");
    private static final Pattern JUNK_TAIL = Pattern.compile("(?i)\\s*[-|]\\s*(official.*|lyrics?.*|audio.*|video.*|remaster.*|live.*|topic|vevo)$");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    static String cleanTitle(String s) {
        if (s == null) return "";
        String t = EXT.matcher(s.trim()).replaceAll("");
        t = t.replace('_', ' ');
        t = LEAD_NUM.matcher(t).replaceFirst("");
        String stripped = BRACKETS.matcher(t).replaceAll("");
        if (!stripped.trim().isEmpty()) t = stripped;
        t = JUNK_TAIL.matcher(t).replaceAll("");
        return SPACES.matcher(t).replaceAll(" ").trim();
    }

    static String cleanArtist(String s) {
        if (s == null) return "";
        String a = s.trim();
        String f = a.toLowerCase(Locale.ROOT);
        if (f.isEmpty() || f.equals("<unknown>") || f.equals("unknown") || f.equals("unknown artist") || f.equals("various artists")) return "";
        a = a.replace('_', ' ');
        a = JUNK_TAIL.matcher(a).replaceAll("");
        a = BRACKETS.matcher(a).replaceAll("");
        return SPACES.matcher(a).replaceAll(" ").trim();
    }

    private static String primaryArtist(String a) {
        String p = FEAT.matcher(a).replaceAll("");
        int i = p.indexOf(',');
        if (i > 0) p = p.substring(0, i);
        i = p.indexOf(" & ");
        if (i > 0) p = p.substring(0, i);
        return p.trim();
    }

    private static String altTitle(String t) {
        String a = FEAT.matcher(t).replaceAll("");
        int i = a.indexOf(" - ");
        if (i > 0) a = a.substring(0, i);
        return a.trim();
    }

    private static final ThreadFactory TF = r -> {
        Thread t = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "lyrics");
        t.setDaemon(true);
        return t;
    };
    private static final ExecutorService EX = Executors.newFixedThreadPool(2, TF);
    private static final LruCache<String, Lyrics> MEM = new LruCache<>(16);
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public static void load(Context c, Query q, boolean force, BooleanSupplier stale, Cb cb) {
        final Context app = c.getApplicationContext();
        EX.execute(() -> {
            Lyrics hit = null;
            int status = MISS;
            try {
                if (!force) {
                    hit = MEM.get(q.key);
                    if (hit == null) hit = readCache(app, q.key);
                    if (hit == null && recentMiss(app, q.key)) {
                        post(cb, null, MISS);
                        return;
                    }
                }
                if (hit == null) {
                    if (stale.getAsBoolean()) return;
                    int[] st = {MISS};
                    hit = fetch(app, q, stale, st);
                    status = st[0];
                    if (hit != null) writeCache(app, q.key, hit);
                    else if (status == MISS) writeMiss(app, q.key);
                }
                if (hit != null) MEM.put(q.key, hit);
            } catch (Throwable t) {
                CrashGuard.nonFatal("lyrics load", t);
            }
            post(cb, hit, hit != null ? OK : status);
        });
    }

    private static void post(Cb cb, Lyrics l, int status) {
        Bg.main(() -> cb.done(l, status));
    }

    private static String host(Context c) {
        String h = Store.prefs(c).getString("lyrics_host", DEFAULT_HOST);
        if (h == null || h.trim().isEmpty()) return DEFAULT_HOST;
        h = h.trim();
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        return h;
    }

    private static Lyrics fetch(Context c, Query q, BooleanSupplier stale, int[] status) {
        String host = host(c);
        String title = q.title;
        String artist = q.artist;
        if (title.isEmpty()) return null;
        String main = primaryArtist(artist);
        boolean net = false, err = false;
        Set<String> tried = new LinkedHashSet<>();
        List<String> urls = new ArrayList<>();
        long sec = q.durMs > 0 ? Math.round(q.durMs / 1000.0) : 0;
        HttpUrl base = HttpUrl.parse(host + "/api/get");
        if (base != null && !artist.isEmpty() && sec > 0) {
            HttpUrl.Builder b = base.newBuilder().addQueryParameter("track_name", title)
                    .addQueryParameter("artist_name", main.isEmpty() ? artist : main)
                    .addQueryParameter("duration", String.valueOf(sec));
            if (!q.album.isEmpty()) b.addQueryParameter("album_name", q.album);
            urls.add("G" + b.build());
        }
        HttpUrl search = HttpUrl.parse(host + "/api/search");
        if (search != null) {
            if (!artist.isEmpty()) {
                urls.add("S" + search.newBuilder().addQueryParameter("track_name", title)
                        .addQueryParameter("artist_name", main.isEmpty() ? artist : main).build());
            }
            urls.add("S" + search.newBuilder().addQueryParameter("q", (title + " " + main).trim()).build());
            String alt = altTitle(title);
            if (!alt.isEmpty() && !alt.equals(title)) {
                urls.add("S" + search.newBuilder().addQueryParameter("q", (alt + " " + main).trim()).build());
            }
            if (!main.isEmpty()) urls.add("S" + search.newBuilder().addQueryParameter("q", title).build());
        }
        for (String u : urls) {
            if (stale.getAsBoolean()) return null;
            if (!tried.add(u)) continue;
            try {
                String body = get(u.substring(1));
                net = true;
                if (body == null) continue;
                JSONObject o = null;
                if (u.charAt(0) == 'G') {
                    o = new JSONObject(body);
                } else {
                    JSONArray a = new JSONArray(body);
                    o = best(a, q);
                }
                Lyrics l = o == null ? null : fromJson(o, "lrclib");
                if (l != null) return l;
            } catch (IOException e) {
                err = true;
            } catch (JSONException ignored) {
                net = true;
            }
        }
        if (!artist.isEmpty() && !stale.getAsBoolean()) {
            try {
                HttpUrl ovh = HttpUrl.parse(OVH_HOST + "/v1");
                if (ovh != null) {
                    HttpUrl u = ovh.newBuilder().addPathSegment(main.isEmpty() ? artist : main).addPathSegment(title).build();
                    String body = get(u.toString());
                    net = true;
                    if (body != null) {
                        String txt = new JSONObject(body).optString("lyrics", "");
                        String[] lines = LrcParser.plain(txt);
                        if (lines.length > 2) return new Lyrics(false, false, new long[0], lines, "ovh");
                    }
                }
            } catch (IOException e) {
                err = true;
            } catch (JSONException ignored) {
                net = true;
            }
        }
        status[0] = (err && !net) ? OFFLINE : MISS;
        return null;
    }

    private static String get(String url) throws IOException {
        Request r = new Request.Builder().url(url)
                .header("User-Agent", "SimoMusic/" + BuildConfig.VERSION_NAME + " (Android)")
                .header("Accept", "application/json").build();
        try (Response resp = HTTP.newCall(r).execute()) {
            int code = resp.code();
            if (code == 404) return null;
            if (!resp.isSuccessful()) throw new IOException("http " + code);
            ResponseBody b = resp.body();
            return b == null ? null : b.string();
        }
    }

    private static int sim(String a, String b) {
        String fa = Norm.fold(a).trim(), fb = Norm.fold(b).trim();
        if (fa.isEmpty() || fb.isEmpty()) return 0;
        if (fa.equals(fb)) return 3;
        if (Math.min(fa.length(), fb.length()) >= 3 && (fa.contains(fb) || fb.contains(fa))) return 2;
        String[] ta = fa.split("\\s+"), tb = fb.split("\\s+");
        int hit = 0;
        for (String x : ta) {
            for (String y : tb) {
                if (x.equals(y)) {
                    hit++;
                    break;
                }
            }
        }
        return hit * 10 >= Math.max(ta.length, tb.length) * 6 ? 1 : 0;
    }

    private static JSONObject best(JSONArray arr, Query q) {
        JSONObject pick = null;
        int top = -1;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            boolean has = !o.optString("syncedLyrics", "").isEmpty() || !o.optString("plainLyrics", "").isEmpty() || o.optBoolean("instrumental", false);
            if (!has) continue;
            int ts = Math.max(sim(q.title, o.optString("trackName", "")), sim(q.rawTitle, o.optString("trackName", "")));
            if (ts < 1) continue;
            int as = q.artist.isEmpty() ? 1 : sim(primaryArtist(q.artist), o.optString("artistName", ""));
            if (as < 1 && ts < 3) continue;
            double d = o.optDouble("duration", 0);
            long dd = q.durMs > 0 && d > 0 ? Math.abs(Math.round(d) - Math.round(q.durMs / 1000.0)) : 0;
            if (dd > 25) continue;
            int score = ts * 10 + as * 5 + (dd <= 2 ? 6 : dd <= 5 ? 4 : dd <= 10 ? 2 : 0)
                    + (o.optString("syncedLyrics", "").isEmpty() ? 0 : 3);
            if (score > top) {
                top = score;
                pick = o;
            }
        }
        return pick;
    }

    private static Lyrics fromJson(JSONObject o, String src) {
        String synced = o.optString("syncedLyrics", "");
        String plain = o.optString("plainLyrics", "");
        if (!synced.isEmpty()) {
            LrcParser.Result r = LrcParser.parse(synced);
            if (r != null && r.lines.length >= 2) return new Lyrics(true, false, r.times, r.lines, src);
        }
        if (!plain.isEmpty()) {
            String[] lines = LrcParser.plain(plain);
            if (lines.length >= 2) return new Lyrics(false, false, new long[0], lines, src);
        }
        if (o.optBoolean("instrumental", false)) return new Lyrics(false, true, new long[0], new String[0], src);
        return null;
    }

    private static String readFile(File f) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(File f, String s) throws IOException {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lyrics");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, String key) {
        return new File(dir(c), Integer.toHexString(key.hashCode()) + "_" + key.length() + ".json");
    }

    private static Lyrics readCache(Context c, String key) {
        File f = file(c, key);
        if (!f.exists()) return null;
        try {
            String s = readFile(f);
            JSONObject o = new JSONObject(s);
            if (o.optBoolean("miss", false)) return null;
            if (o.optBoolean("instrumental", false)) return new Lyrics(false, true, new long[0], new String[0], o.optString("src", ""));
            String raw = o.optString("raw", "");
            if (o.optBoolean("synced", false)) {
                LrcParser.Result r = LrcParser.parse(raw);
                if (r != null) return new Lyrics(true, false, r.times, r.lines, o.optString("src", ""));
            } else {
                String[] lines = LrcParser.plain(raw);
                if (lines.length > 0) return new Lyrics(false, false, new long[0], lines, o.optString("src", ""));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean recentMiss(Context c, String key) {
        File f = file(c, key);
        if (!f.exists()) return false;
        try {
            JSONObject o = new JSONObject(readFile(f));
            return o.optBoolean("miss", false) && System.currentTimeMillis() - o.optLong("at", 0) < MISS_TTL;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeCache(Context c, String key, Lyrics l) {
        try {
            JSONObject o = new JSONObject();
            o.put("synced", l.synced);
            o.put("instrumental", l.instrumental);
            o.put("src", l.source);
            o.put("at", System.currentTimeMillis());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < l.lines.length; i++) {
                if (l.synced) {
                    long t = l.times[i];
                    sb.append(String.format(Locale.US, "[%02d:%02d.%02d]", t / 60000, (t / 1000) % 60, (t % 1000) / 10));
                }
                sb.append(l.lines[i]).append('\n');
            }
            o.put("raw", sb.toString());
            writeFile(file(c, key), o.toString());
        } catch (Exception e) {
            CrashGuard.nonFatal("lyrics cache write", e);
        }
    }

    private static void writeMiss(Context c, String key) {
        try {
            JSONObject o = new JSONObject();
            o.put("miss", true);
            o.put("at", System.currentTimeMillis());
            writeFile(file(c, key), o.toString());
        } catch (Exception ignored) {
        }
    }

    public static void forget(Context c, String key) {
        MEM.remove(key);
        try {
            file(c, key).delete();
        } catch (Exception ignored) {
        }
    }
}
