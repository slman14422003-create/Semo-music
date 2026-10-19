package com.simomusic.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Local, on-device listening history that powers the AI Mix. Only ever written while the AI feature is turned on
 * in Settings; turning it off wipes it (see {@link Store#setAiEnabled}). Nothing here ever leaves the phone -
 * it is plain SharedPreferences, read only by {@link AiEngine}.
 */
public final class PlayStats {
    private PlayStats() {
    }

    public static final class Row {
        public int plays, skips;
        public long lastPlayed; // epoch seconds
        public final int[] part = new int[4];
    }

    public static int bucket(int hour) {
        return hour < 6 ? 0 : hour < 12 ? 1 : hour < 18 ? 2 : 3;
    }

    private static Map<Long, Row> cache;
    private static int[] hours; // 24 buckets: how often music was started around each hour of day

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("simomusic_stats", Context.MODE_PRIVATE);
    }

    private static synchronized void load(Context c) {
        if (cache != null) return;
        cache = new HashMap<>();
        hours = new int[24];
        SharedPreferences p = prefs(c);
        try {
            JSONObject root = new JSONObject(p.getString("rows", "{}"));
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = root.getJSONArray(k);
                Row r = new Row();
                r.plays = a.getInt(0);
                r.skips = a.getInt(1);
                r.lastPlayed = a.getLong(2);
                for (int i = 0; i < 4 && a.length() > 3 + i; i++) r.part[i] = a.getInt(3 + i);
                cache.put(Long.parseLong(k), r);
            }
        } catch (JSONException | NumberFormatException ignored) {
        }
        try {
            JSONArray h = new JSONArray(p.getString("hours", "[]"));
            for (int i = 0; i < h.length() && i < 24; i++) hours[i] = h.getInt(i);
        } catch (JSONException ignored) {
        }
    }

    private static final android.os.Handler H = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stats-save");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static boolean saveQueued;

    private static synchronized void save(final Context c) {
        if (saveQueued) return;
        saveQueued = true;
        final Context app = c.getApplicationContext();
        H.postDelayed(() -> flush(app), 4000);
    }

    public static void flush(final Context c) {
        final Context app = c.getApplicationContext();
        final java.util.List<long[]> rows = new java.util.ArrayList<>();
        final int[] hs;
        synchronized (PlayStats.class) {
            saveQueued = false;
            if (cache == null) return;
            for (Map.Entry<Long, Row> e : cache.entrySet()) {
                Row r = e.getValue();
                rows.add(new long[]{e.getKey(), r.plays, r.skips, r.lastPlayed, r.part[0], r.part[1], r.part[2], r.part[3]});
            }
            hs = hours.clone();
        }
        IO.execute(() -> {
            JSONObject root = new JSONObject();
            try {
                for (long[] v : rows) {
                    JSONArray a = new JSONArray();
                    for (int i = 1; i < v.length; i++) a.put(v[i]);
                    root.put(String.valueOf(v[0]), a);
                }
            } catch (JSONException ignored) {
            }
            JSONArray h = new JSONArray();
            for (int v : hs) h.put(v);
            prefs(app).edit().putString("rows", root.toString()).putString("hours", h.toString()).apply();
        });
    }

    private static Row row(long id) {
        Row r = cache.get(id);
        if (r == null) {
            r = new Row();
            cache.put(id, r);
        }
        return r;
    }

    /** Called whenever a track becomes the one actually playing. */
    public static synchronized void started(Context c, long id) {
        if (id < 0 || !Store.aiEnabled(c)) return;
        load(c);
        Row r = row(id);
        r.lastPlayed = System.currentTimeMillis() / 1000;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        hours[hour]++;
        r.part[bucket(hour)]++;
        save(c);
    }

    /** Called right as a track stops being current, saying whether it played through or was skipped away from. */
    public static synchronized void ended(Context c, long id, boolean finishedNaturally) {
        if (id < 0 || !Store.aiEnabled(c)) return;
        load(c);
        Row r = row(id);
        if (finishedNaturally) r.plays++;
        else r.skips++;
        save(c);
    }

    public static synchronized Map<Long, Row> all(Context c) {
        load(c);
        return cache;
    }

    /** Wipes every bit of stored listening history. */
    public static synchronized void clear(Context c) {
        cache = new HashMap<>();
        hours = new int[24];
        prefs(c).edit().clear().apply();
    }
}
