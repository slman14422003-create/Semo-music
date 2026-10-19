package com.simomusic.player;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.Player;

import java.util.ArrayList;
import java.util.List;

/** Remembers the queue, current song and position, and puts them back (paused) when the app is opened again. */
public final class Resume {
    private Resume() {
    }

    private static boolean restored;

    /** The player service went away: the next app open may put the saved queue back again. */
    public static void reset() {
        restored = false;
        idsDirty = true;
    }
    private static final int MAX = 1000;

    private static String idsCache = "";
    private static boolean idsDirty = true;
    private static final android.os.Handler H = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable pending;

    public static void queueChanged() {
        idsDirty = true;
    }

    public static void saveSoon(final Context c, final Player p) {
        if (pending != null) H.removeCallbacks(pending);
        final Context app = c.getApplicationContext();
        pending = () -> {
            pending = null;
            try {
                save(app, p);
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("resume save", e);
            }
        };
        H.postDelayed(pending, 600);
    }

    public static void save(Context c, Player p) {
        if (pending != null) {
            H.removeCallbacks(pending);
            pending = null;
        }
        int n = p.getMediaItemCount();
        if (n == 0) return;
        if (idsDirty) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(n, MAX); i++) sb.append(p.getMediaItemAt(i).mediaId).append(',');
            idsCache = sb.toString();
            idsDirty = false;
        }
        androidx.media3.common.MediaItem cur = p.getCurrentMediaItem();
        final Context app = c.getApplicationContext();
        final String ids = idsCache, curId = cur == null ? "" : cur.mediaId;
        final long pos = Math.max(0, p.getCurrentPosition());
        final boolean shuffle = p.getShuffleModeEnabled();
        final int repeat = p.getRepeatMode();
        Bg.io("resume write", () -> Store.prefs(app).edit()
                .putString("rs_ids", ids)
                .putString("rs_cur", curId)
                .putLong("rs_pos", pos)
                .putBoolean("rs_shuffle", shuffle)
                .putInt("rs_repeat", repeat)
                .apply());
    }

    /** Once per app start, after the library is loaded. Does nothing if something is already queued. */
    public static void restore(Context c) {
        if (restored || !Store.flag(c, "resume", true)) return;
        restored = true;
        SharedPreferences sp = Store.prefs(c);
        String ids = sp.getString("rs_ids", "");
        if (ids == null || ids.isEmpty()) return;
        String cur = sp.getString("rs_cur", "");
        List<Track> list = new ArrayList<>();
        int idx = 0;
        for (String s : ids.split(",")) {
            if (s.isEmpty()) continue;
            try {
                Track t = Library.byId.get(Long.parseLong(s));
                if (t == null) continue;
                if (s.equals(cur)) idx = list.size();
                list.add(t);
            } catch (NumberFormatException ignored) {
            }
        }
        if (list.isEmpty()) return;
        Pb.restore(c, list, idx, sp.getLong("rs_pos", 0), sp.getBoolean("rs_shuffle", false), sp.getInt("rs_repeat", 0));
    }
}
