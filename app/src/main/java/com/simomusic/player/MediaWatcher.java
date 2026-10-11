package com.simomusic.player;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaWatcher {
    private MediaWatcher() {
    }

    private static final long DELAY_MS = 800;
    private static final long TICK_MS = 15000;
    private static final long WALK_BUDGET_MS = 4000;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_DIRS = 6000;
    private static boolean started;
    private static ContentObserver observer;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean walking = new AtomicBoolean();
    private static final java.util.Map<String, Integer> tries = new java.util.HashMap<>();

    public static synchronized void start(Context c) {
        if (started) return;
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable scan = () -> {
            if (Perms.hasAudio(app)) Library.scan(app, null);
        };
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (App.visibleCount() > 0 && Perms.hasAudio(app)) sweep(app);
                h.postDelayed(this, TICK_MS);
            }
        };
        observer = new ContentObserver(h) {
            @Override
            public void onChange(boolean selfChange) {
                h.removeCallbacks(scan);
                h.postDelayed(scan, DELAY_MS);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                onChange(selfChange);
            }
        };
        try {
            app.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
            if (Build.VERSION.SDK_INT >= 29) {
                app.getContentResolver().registerContentObserver(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), true, observer);
            }
            started = true;
            h.postDelayed(tick, TICK_MS);
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("media watcher", e);
        }
    }

    public static void sweep(final Context c) {
        if (!walking.compareAndSet(false, true)) return;
        IO.execute(() -> {
            try {
                List<String> missing = findUnindexed(c);
                if (missing.isEmpty()) {
                    if (Library.needsRetry()) Library.scan(c, null);
                    else Library.syncIfChanged(c);
                } else {
                    MediaScannerConnection.scanFile(c, missing.toArray(new String[0]), null,
                            (path, uri) -> Library.scan(c, null));
                }
            } catch (Exception e) {
                CrashGuard.nonFatal("media sweep", e);
            } finally {
                walking.set(false);
            }
        });
    }

    private static List<String> findUnindexed(Context c) {
        Set<String> known = indexedPaths(c);
        Set<String> exts = Store.enabledExts(c);
        List<String> out = new ArrayList<>();
        long end = SystemClock.elapsedRealtime() + WALK_BUDGET_MS;
        int dirs = 0;
        ArrayDeque<File> stack = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        for (File r : roots()) {
            stack.push(r);
            depth.push(0);
        }
        while (!stack.isEmpty() && dirs < MAX_DIRS && SystemClock.elapsedRealtime() < end) {
            File d = stack.pop();
            int lvl = depth.pop();
            dirs++;
            File[] kids = d.listFiles();
            if (kids == null) continue;
            boolean noMedia = false;
            for (File k : kids) if (k.getName().equals(".nomedia")) noMedia = true;
            if (noMedia) continue;
            for (File k : kids) {
                String n = k.getName();
                if (k.isDirectory()) {
                    if (lvl < MAX_DEPTH && !n.startsWith(".") && !(lvl == 0 && n.equals("Android"))) {
                        stack.push(k);
                        depth.push(lvl + 1);
                    }
                } else {
                    int dot = n.lastIndexOf('.');
                    if (dot <= 0 || n.startsWith(".")) continue;
                    if (!exts.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT))) continue;
                    if (k.length() == 0) continue;
                    String p = k.getAbsolutePath();
                    if (known.contains(p.toLowerCase(Locale.ROOT))) {
                        tries.remove(p);
                        continue;
                    }
                    Integer t = tries.get(p);
                    if (t != null && t >= 3) continue;
                    tries.put(p, t == null ? 1 : t + 1);
                    out.add(p);
                }
            }
        }
        if (out.size() > 200) return new ArrayList<>(out.subList(0, 200));
        return out;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> indexedPaths(Context c) {
        Set<String> s = new HashSet<>();
        try (Cursor q = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.DATA}, null, null, null)) {
            if (q != null) while (q.moveToNext()) {
                String p = q.getString(0);
                if (p != null) s.add(p.toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            CrashGuard.nonFatal("indexed paths", e);
        }
        return s;
    }

    private static List<File> roots() {
        List<File> r = new ArrayList<>();
        File prim = Environment.getExternalStorageDirectory();
        if (prim != null && prim.isDirectory()) r.add(prim);
        File[] vols = new File("/storage").listFiles();
        if (vols != null) {
            for (File v : vols) {
                String n = v.getName();
                if (n.equals("emulated") || n.equals("self") || !v.isDirectory()) continue;
                r.add(v);
            }
        }
        return r;
    }
}
