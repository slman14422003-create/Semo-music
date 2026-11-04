package com.simomusic.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Set;

/** Settings + playlists, kept in SharedPreferences (playlists as JSON). */
public final class Store {
    private Store() {
    }

    /** Format keys shown in Settings, and the file extensions each one covers. */
    public static final String[] FORMATS = {"mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "amr", "mka"};
    private static final String[][] EXT = {{"mp3"}, {"flac"}, {"wav", "wave"}, {"m4a", "m4b", "mp4"},
            {"aac", "adts"}, {"ogg", "oga"}, {"opus"}, {"amr", "awb"}, {"mka"}};

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("simomusic", Context.MODE_PRIVATE);
    }

    public static SharedPreferences secrets(Context c) {
        return c.getApplicationContext().getSharedPreferences("simomusic_secret", Context.MODE_PRIVATE);
    }

    public static String groqKey(Context c) {
        SharedPreferences s = secrets(c);
        String k = s.getString("groq_key", "");
        if (k == null || k.isEmpty()) {
            SharedPreferences m = prefs(c);
            String old = m.getString("groq_key", "");
            if (old != null && !old.isEmpty()) {
                s.edit().putString("groq_key", old).apply();
                m.edit().remove("groq_key").apply();
                k = old;
            }
        }
        return k == null ? "" : k;
    }

    public static void setGroqKey(Context c, String k) {
        secrets(c).edit().putString("groq_key", k).apply();
        prefs(c).edit().remove("groq_key").apply();
    }

    public static boolean flag(Context c, String k, boolean def) {
        return prefs(c).getBoolean(k, def);
    }

    public static void setFlag(Context c, String k, boolean v) {
        prefs(c).edit().putBoolean(k, v).apply();
    }

    public static boolean formatOn(Context c, String f) {
        return prefs(c).getBoolean("fmt_" + f, true);
    }

    public static void setFormatOn(Context c, String f, boolean on) {
        prefs(c).edit().putBoolean("fmt_" + f, on).apply();
    }

    public static Set<String> enabledExts(Context c) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < FORMATS.length; i++) {
            if (formatOn(c, FORMATS[i])) for (String e : EXT[i]) out.add(e);
        }
        return out;
    }

    /** Files shorter than this many seconds are hidden (0 = show everything). */
    public static int minDur(Context c) {
        return prefs(c).getInt("min_dur", 0);
    }

    public static void setMinDur(Context c, int s) {
        prefs(c).edit().putInt("min_dur", s).apply();
    }

    /** Folders whose songs are left out of the library (and therefore out of every playlist too). */
    public static Set<String> hiddenFolders(Context c) {
        Set<String> out = new HashSet<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("hidden_folders", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void setHiddenFolders(Context c, Set<String> folders) {
        JSONArray a = new JSONArray();
        for (String f : folders) a.put(f);
        prefs(c).edit().putString("hidden_folders", a.toString()).apply();
    }

    public static int sort(Context c) {
        return prefs(c).getInt("sort", 0);
    }

    public static int sortMode(Context c) {
        return sort(c) & 15;
    }

    public static boolean sortReverse(Context c) {
        return (sort(c) & 16) != 0;
    }

    public static int crossfade(Context c) {
        return prefs(c).getInt("crossfade", 0);
    }

    public static void setCrossfade(Context c, int secs) {
        prefs(c).edit().putInt("crossfade", secs).apply();
    }

    public static boolean smartFilter(Context c) {
        return flag(c, "smart_filter", true);
    }

    public static boolean autoplay(Context c) {
        return flag(c, "autoplay", true);
    }

    public static void setSort(Context c, int s) {
        prefs(c).edit().putInt("sort", s).apply();
    }

    // ------------------------------------------------------------------ playlists

    public static final String FAV = "fav";
    public static final String AI = "ai";

    public static final class Playlist {
        public String id, name;
        public final List<Long> ids = new CopyOnWriteArrayList<>();
    }

    private static List<Playlist> cache;
    private static final Map<Long, String> keys = new HashMap<>();

    static String stableKey(Track t) {
        return Norm.fold(t.title) + "|" + Norm.fold(t.artist) + "|" + (t.duration / 1000);
    }

    public static synchronized List<Playlist> playlists(Context c) {
        if (cache == null) load(c);
        return cache;
    }

    private static void load(Context c) {
        cache = new CopyOnWriteArrayList<>();
        keys.clear();
        String s = prefs(c).getString("playlists", null);
        JSONArray arr;
        try {
            arr = s == null ? new JSONArray() : new JSONArray(s);
        } catch (JSONException e) {
            prefs(c).edit().putString("playlists_corrupt", s).apply();
            arr = new JSONArray();
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                Playlist p = new Playlist();
                p.id = o.getString("id");
                p.name = o.getString("name");
                JSONArray ids = o.getJSONArray("ids");
                for (int j = 0; j < ids.length(); j++) p.ids.add(ids.getLong(j));
                cache.add(p);
            } catch (JSONException ignored) {
            }
        }
        try {
            JSONObject km = new JSONObject(prefs(c).getString("idkeys", "{}"));
            Iterator<String> it = km.keys();
            while (it.hasNext()) {
                String k = it.next();
                keys.put(Long.parseLong(k), km.getString(k));
            }
        } catch (JSONException | NumberFormatException ignored) {
        }
        // built-in lists are always named in the CURRENT language (the stored name was frozen in whatever language was active when it was created)
        for (Playlist p : cache) {
            if (FAV.equals(p.id)) p.name = c.getString(R.string.favorites);
            else if (AI.equals(p.id)) p.name = c.getString(R.string.ai_mix);
        }
        boolean hasFav = false, hasAi = false;
        for (Playlist p : cache) {
            if (FAV.equals(p.id)) hasFav = true;
            if (AI.equals(p.id)) hasAi = true;
        }
        if (!hasFav) {
            Playlist f = new Playlist();
            f.id = FAV;
            f.name = c.getString(R.string.favorites);
            cache.add(0, f);
        }
        if (!hasAi && flag(c, "ai_enabled", false)) {
            Playlist a = new Playlist();
            a.id = AI;
            a.name = c.getString(R.string.ai_mix);
            cache.add(Math.min(1, cache.size()), a);
        }
    }

    private static void save(Context c) {
        final Context app = c.getApplicationContext();
        final List<Playlist> snap = new ArrayList<>(cache.size());
        for (Playlist p : cache) {
            Playlist q = new Playlist();
            q.id = p.id;
            q.name = p.name;
            q.ids.addAll(p.ids);
            snap.add(q);
        }
        Map<Long, String> nk = new HashMap<>();
        for (Playlist p : cache) {
            for (long id : p.ids) {
                Track t = Library.byId.get(id);
                String k = t != null ? stableKey(t) : keys.get(id);
                if (k != null) nk.put(id, k);
            }
        }
        keys.clear();
        keys.putAll(nk);
        final Map<Long, String> keySnap = nk;
        Bg.io("save playlists", () -> {
            JSONObject km = new JSONObject();
            try {
                for (Map.Entry<Long, String> e : keySnap.entrySet()) km.put(String.valueOf(e.getKey()), e.getValue());
            } catch (JSONException ignored) {
            }
            JSONArray a = new JSONArray();
            try {
                for (Playlist p : snap) {
                    JSONObject o = new JSONObject();
                    o.put("id", p.id);
                    o.put("name", p.name);
                    JSONArray ids = new JSONArray();
                    for (long id : p.ids) ids.put(id);
                    o.put("ids", ids);
                    a.put(o);
                }
            } catch (JSONException ignored) {
            }
            prefs(app).edit().putString("playlists", a.toString()).putString("idkeys", km.toString()).apply();
        });
    }

    public static synchronized void remap(Context c) {
        Map<String, Long> byKey = null;
        boolean changed = keys.isEmpty() && !Library.byId.isEmpty();
        for (Playlist p : playlists(c)) {
            for (int i = 0; i < p.ids.size(); i++) {
                long id = p.ids.get(i);
                if (Library.byId.containsKey(id)) continue;
                String k = keys.get(id);
                if (k == null) continue;
                if (byKey == null) {
                    byKey = new HashMap<>();
                    for (Track t : Library.tracks) {
                        String tk = stableKey(t);
                        if (!byKey.containsKey(tk)) byKey.put(tk, t.id);
                    }
                }
                Long nid = byKey.get(k);
                if (nid != null && !p.ids.contains(nid)) {
                    p.ids.set(i, nid);
                    changed = true;
                }
            }
        }
        if (changed) save(c);
    }

    public static synchronized Playlist playlist(Context c, String id) {
        for (Playlist p : playlists(c)) if (p.id.equals(id)) return p;
        return null;
    }

    public static synchronized Playlist create(Context c, String name) {
        Playlist p = new Playlist();
        p.id = "p" + System.currentTimeMillis();
        p.name = name;
        playlists(c).add(p);
        save(c);
        return p;
    }

    public static synchronized void rename(Context c, String id, String name) {
        Playlist p = playlist(c, id);
        if (p != null && !FAV.equals(id) && !AI.equals(id)) {
            p.name = name;
            save(c);
        }
    }

    public static boolean aiEnabled(Context c) {
        return flag(c, "ai_enabled", false);
    }

    /** Turns the local AI Mix on or off. Off also wipes the listening history it was learning from. */
    public static synchronized void setAiEnabled(Context c, boolean on) {
        setFlag(c, "ai_enabled", on);
        if (on) {
            if (playlist(c, AI) == null) {
                Playlist p = new Playlist();
                p.id = AI;
                p.name = c.getString(R.string.ai_mix);
                playlists(c).add(Math.min(1, playlists(c).size()), p);
                save(c);
            }
        } else {
            Playlist p = playlist(c, AI);
            if (p != null) {
                cache.remove(p);
                save(c);
            }
            PlayStats.clear(c);
        }
    }

    /** Removes deleted songs from favourites and every playlist. */
    public static synchronized void forget(Context c, java.util.Collection<Long> gone) {
        boolean any = false;
        for (Playlist p : playlists(c)) any |= p.ids.removeAll(gone);
        if (any) save(c);
    }

    public static synchronized void delete(Context c, String id) {
        if (FAV.equals(id) || AI.equals(id)) return;
        Playlist p = playlist(c, id);
        if (p != null) {
            cache.remove(p);
            save(c);
        }
    }

    public static synchronized boolean has(Context c, String pid, long tid) {
        Playlist p = playlist(c, pid);
        return p != null && p.ids.contains(tid);
    }

    /** Adds or removes the track; returns true when it is in the playlist afterwards. */
    public static synchronized boolean toggle(Context c, String pid, long tid) {
        Playlist p = playlist(c, pid);
        if (p == null) return false;
        boolean in = p.ids.contains(tid);
        if (in) p.ids.remove(Long.valueOf(tid));
        else p.ids.add(tid);
        save(c);
        return !in;
    }

    public static synchronized int addAll(Context c, String pid, Collection<Long> ids) {
        Playlist p = playlist(c, pid);
        if (p == null) return 0;
        Set<Long> have = new HashSet<>(p.ids);
        List<Long> add = new ArrayList<>();
        for (long id : ids) if (have.add(id)) add.add(id);
        p.ids.addAll(add);
        save(c);
        return add.size();
    }

    public static synchronized void setOrder(Context c, String pid, List<Long> ids) {
        Playlist p = playlist(c, pid);
        if (p == null) return;
        p.ids.clear();
        p.ids.addAll(ids);
        save(c);
    }
}
