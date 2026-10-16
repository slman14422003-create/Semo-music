package com.simomusic.player;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds the "AI Mix" playlist entirely on the phone: no network call, no model file, just arithmetic over
 * {@link PlayStats}. Every song gets a score from five signals and the best ones become the playlist:
 *   - how often you've let it play through (frequency)
 *   - how recently you played it, decaying over about a month (recency)
 *   - how often you skip away from it (a penalty)
 *   - whether you starred it (favorites bonus)
 *   - whether its artist is one you lean on, even for songs of theirs you haven't played yet (discovery)
 * A touch of randomness keeps a regenerate from looking identical to the last one.
 */
public final class AiEngine {
    private AiEngine() {
    }

    private static final int TARGET = 60;

    public static synchronized void generate(Context c) {
        if (!Store.aiEnabled(c)) return;
        List<Track> lib = Library.tracks;
        if (lib.isEmpty()) {
            Store.setOrder(c, Store.AI, new ArrayList<>());
            return;
        }
        Map<Long, PlayStats.Row> stats = PlayStats.all(c);

        // how much you lean on each artist overall: plays count for it, skips count a little against it
        Map<String, Double> artistWeight = new HashMap<>();
        for (Track t : lib) {
            if (t.artist.isEmpty()) continue;
            PlayStats.Row r = stats.get(t.id);
            if (r == null) continue;
            double w = r.plays - r.skips * 0.5;
            if (w <= 0) continue;
            Double prev = artistWeight.get(t.artist);
            artistWeight.put(t.artist, prev == null ? w : prev + w);
        }

        long now = System.currentTimeMillis() / 1000;
        int bucketNow = nowBucket();
        Random rnd = new Random();
        final Map<Long, Double> score = new HashMap<>();
        for (Track t : lib) {
            PlayStats.Row r = stats.get(t.id);
            int plays = r == null ? 0 : r.plays;
            int skips = r == null ? 0 : r.skips;
            long lastPlayed = r == null ? 0 : r.lastPlayed;

            double frequency = Math.log(1 + plays) * 1.6;
            double recency = 0;
            if (lastPlayed > 0) {
                double days = Math.max(0, (now - lastPlayed) / 86400.0);
                recency = Math.exp(-days / 30.0) * 1.3;
            }
            double skipPenalty = (plays + skips) > 0 ? (skips / (double) (plays + skips + 1)) * 1.4 : 0;
            double favBonus = Store.has(c, Store.FAV, t.id) ? 0.9 : 0;
            double artistBoost = 0;
            Double aw = artistWeight.get(t.artist);
            if (aw != null) artistBoost = Math.min(1.0, Math.log(1 + aw) / 6.0) * (plays == 0 ? 0.7 : 0.3);
            double discovery = (plays == 0 && skips == 0) ? rnd.nextDouble() * 0.25 : 0;
            double jitter = rnd.nextDouble() * 0.08;

            score.put(t.id, frequency + recency + favBonus + artistBoost + discovery + jitter - skipPenalty
                    + affinity(r, bucketNow) * 0.7);
        }

        List<Track> ranked = new ArrayList<>(lib);
        ranked.sort((a, b) -> Double.compare(score.get(b.id), score.get(a.id)));
        int n = Math.min(TARGET, ranked.size());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(ranked.get(i).id);
        Store.setOrder(c, Store.AI, ids);
    }

    public static final int MIX_NOW = 0, MIX_TOP = 1, MIX_RECENT = 2, MIX_FORGOTTEN = 3, MIX_NEW = 4;

    private static int nowBucket() {
        return PlayStats.bucket(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    private static double affinity(PlayStats.Row r, int bucket) {
        if (r == null) return 0;
        int total = 0;
        for (int v : r.part) total += v;
        if (total == 0) return 0;
        return (r.part[bucket] / (double) total) * Math.min(1.0, Math.log(1 + total) / 2.5);
    }

    private static double base(Track t, PlayStats.Row r, long now, boolean fav) {
        int plays = r == null ? 0 : r.plays;
        int skips = r == null ? 0 : r.skips;
        long last = r == null ? 0 : r.lastPlayed;
        double s = Math.log(1 + plays) * 1.6;
        if (last > 0) s += Math.exp(-Math.max(0, (now - last) / 86400.0) / 30.0) * 1.3;
        if (plays + skips > 0) s -= (skips / (double) (plays + skips + 1)) * 1.4;
        if (fav) s += 0.9;
        return s;
    }

    private static Set<Long> favSet(Context c) {
        Store.Playlist p = Store.playlist(c, Store.FAV);
        Set<Long> out = new HashSet<>();
        if (p != null) out.addAll(p.ids);
        return out;
    }

    private static Map<Long, PlayStats.Row> stats(Context c) {
        return Store.aiEnabled(c) ? PlayStats.all(c) : new HashMap<Long, PlayStats.Row>();
    }

    private static List<Track> order(List<Track> src, final Map<Long, Double> key, int limit) {
        List<Track> out = new ArrayList<>(src);
        out.sort((a, b) -> {
            int r = Double.compare(key.get(b.id), key.get(a.id));
            return r != 0 ? r : a.title.compareToIgnoreCase(b.title);
        });
        return limit > 0 && out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    public static List<Track> rank(Context c, List<Track> src, int mode) {
        Map<Long, PlayStats.Row> st = stats(c);
        Set<Long> fav = favSet(c);
        long now = System.currentTimeMillis() / 1000;
        int bucket = nowBucket();
        Map<Long, Double> key = new HashMap<>();
        for (Track t : src) {
            PlayStats.Row r = st.get(t.id);
            double v;
            if (mode == 5) v = r == null ? 0 : r.plays * 1e10 + r.lastPlayed;
            else v = base(t, r, now, fav.contains(t.id)) + affinity(r, bucket) * 1.5;
            key.put(t.id, v);
        }
        return order(src, key, 0);
    }

    public static List<Track> mix(Context c, int kind) {
        List<Track> lib = Library.tracks;
        if (lib.isEmpty()) return new ArrayList<>();
        Map<Long, Double> key = new HashMap<>();
        if (kind == MIX_NEW) {
            for (Track t : lib) key.put(t.id, (double) t.added);
            return order(lib, key, 50);
        }
        if (!Store.aiEnabled(c)) return new ArrayList<>();
        Map<Long, PlayStats.Row> st = PlayStats.all(c);
        Set<Long> fav = favSet(c);
        long now = System.currentTimeMillis() / 1000;
        int bucket = nowBucket();
        Random rnd = new Random();
        List<Track> pool = new ArrayList<>();
        for (Track t : lib) {
            PlayStats.Row r = st.get(t.id);
            boolean isFav = fav.contains(t.id);
            double v;
            switch (kind) {
                case MIX_TOP:
                    if (r == null || r.plays <= 0) continue;
                    v = r.plays * 1e6 + r.lastPlayed;
                    break;
                case MIX_RECENT:
                    if (r == null || r.lastPlayed <= 0) continue;
                    v = r.lastPlayed;
                    break;
                case MIX_FORGOTTEN:
                    int plays = r == null ? 0 : r.plays;
                    long last = r == null ? 0 : r.lastPlayed;
                    if (!(plays >= 2 || isFav)) continue;
                    if (last > 0 && now - last < 21L * 86400L) continue;
                    v = plays + (isFav ? 3 : 0) + rnd.nextDouble() * 0.5;
                    break;
                default:
                    if (!isFav && (r == null || r.plays + r.skips == 0)) continue;
                    v = base(t, r, now, isFav) + affinity(r, bucket) * 2.0 + rnd.nextDouble() * 0.15;
            }
            key.put(t.id, v);
            pool.add(t);
        }
        return order(pool, key, 50);
    }

    public static List<Track> similar(Context c, Track seed, int n) {
        List<Track> lib = Library.tracks;
        Map<Long, PlayStats.Row> st = stats(c);
        Set<Long> fav = favSet(c);
        long now = System.currentTimeMillis() / 1000;
        int bucket = nowBucket();
        Random rnd = new Random();
        Map<Long, Double> key = new HashMap<>();
        List<Track> pool = new ArrayList<>();
        for (Track t : lib) {
            if (t.id == seed.id) continue;
            double s = 0;
            if (!seed.artist.isEmpty() && seed.artist.equals(t.artist)) s += 3;
            if (!seed.album.isEmpty() && seed.album.equals(t.album)) s += 1.5;
            if (seed.folder.equals(t.folder)) s += 0.4;
            if (Math.abs(seed.added - t.added) < 3600) s += 0.6;
            s += Math.exp(-Math.abs(seed.duration - t.duration) / 90000.0) * 0.6;
            PlayStats.Row r = st.get(t.id);
            s += base(t, r, now, fav.contains(t.id)) * 0.5 + affinity(r, bucket) * 0.8 + rnd.nextDouble() * 0.5;
            key.put(t.id, s);
            pool.add(t);
        }
        return order(pool, key, n);
    }

    public static List<Track> smartShuffle(Context c, List<Track> src) {
        List<Track> in = new ArrayList<>(src);
        if (!Store.aiEnabled(c)) {
            Collections.shuffle(in);
            return spread(in);
        }
        Map<Long, PlayStats.Row> st = PlayStats.all(c);
        Set<Long> fav = favSet(c);
        long now = System.currentTimeMillis() / 1000;
        int bucket = nowBucket();
        Random rnd = new Random();
        Map<Long, Double> key = new HashMap<>();
        for (Track t : in) {
            PlayStats.Row r = st.get(t.id);
            double w = 0.4 + Math.max(0, base(t, r, now, fav.contains(t.id)) + affinity(r, bucket) * 1.2);
            key.put(t.id, Math.pow(rnd.nextDouble(), 1.0 / w));
        }
        return spread(order(in, key, 0));
    }

    private static List<Track> spread(List<Track> l) {
        for (int i = 1; i < l.size(); i++) {
            String a = l.get(i).artist;
            if (a.isEmpty() || !a.equals(l.get(i - 1).artist)) continue;
            for (int j = i + 1; j < l.size() && j < i + 9; j++) {
                if (!a.equals(l.get(j).artist)) {
                    Collections.swap(l, i, j);
                    break;
                }
            }
        }
        return l;
    }
}
