package com.simomusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.core.widget.ImageViewCompat;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Cover-art loading (embedded art / MediaStore thumbnails) with a small memory cache. */
public final class Art {
    private Art() {
    }

    public interface Cb {
        void got(Bitmap b);
    }

    /**
     * Newest request first: while a list is being flung the covers of the rows that are on screen NOW are loaded
     * before the ones that scrolled past (a plain FIFO queue made every cover wait for all the old rows), and the
     * threads run below the UI thread's priority so decoding never steals frames from scrolling.
     */
    private static final class Lifo extends LinkedBlockingDeque<Runnable> {
        @Override
        public boolean offer(Runnable r) {
            return offerFirst(r);
        }
    }

    public static final ExecutorService EX = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new Lifo(), new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, "cover-art");
            t.setDaemon(true);
            return t;
        }
    });
    public static final ExecutorService HERO = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
                r.run();
            }, "cover-hero");
            t.setDaemon(true);
            return t;
        }
    });

    public static final ExecutorService FX = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
                r.run();
            }, "cover-fx");
            t.setDaemon(true);
            return t;
        }
    });

    private static final LruCache<String, Bitmap> MASTER = new LruCache<String, Bitmap>((int) Math.min(12L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    private static final class Job {
        final java.util.ArrayList<Cb> cbs = new java.util.ArrayList<>();
        boolean urgent;
    }

    private static final java.util.HashMap<String, Job> PENDING = new java.util.HashMap<>();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> MISSING = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>((int) Math.min(48L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    /** Memory pressure: drop half of the cached covers, or all of them. */
    public static void trim(boolean all) {
        if (all) {
            CACHE.evictAll();
            MASTER.evictAll();
            MISSING.clear();
        } else {
            CACHE.trimToSize(CACHE.maxSize() / 2);
            MASTER.evictAll();
        }
    }

    /** Blocking: call from a background thread. */
    public static Bitmap loadSync(Context c, Uri uri, int px) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return c.getContentResolver().loadThumbnail(uri, new Size(px, px), null);
            } catch (Exception ignored) {
            }
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            byte[] b = r.getEmbeddedPicture();
            if (b == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(b, 0, b.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            return BitmapFactory.decodeByteArray(b, 0, b.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static Bitmap decode(Context c, Uri uri, int px) {
        String uk = String.valueOf(uri);
        if (px >= 200) {
            Bitmap m = MASTER.get(uk);
            if (m != null && !m.isRecycled() && Math.max(m.getWidth(), m.getHeight()) >= px) {
                int big = Math.max(m.getWidth(), m.getHeight());
                if (big <= px + px / 20) return m;
                try {
                    float k = px / (float) big;
                    return Bitmap.createScaledBitmap(m, Math.max(1, Math.round(m.getWidth() * k)), Math.max(1, Math.round(m.getHeight() * k)), true);
                } catch (RuntimeException ignored) {
                }
            }
        }
        Bitmap r = loadSync(c, uri, px);
        if (r != null && px >= 200) MASTER.put(uk, r);
        return r;
    }

    /** Async, cached; the callback runs on the main thread with null when the file has no art. */
    public static void fetch(Context c, final Uri uri, final int px, final Cb cb) {
        fetch(c, uri, px, cb, null, false);
    }

    /** `wanted` is asked again just before the work starts: a row that was scrolled away meanwhile costs nothing. */
    public static void fetch(Context c, final Uri uri, final int px, final Cb cb, final BooleanSupplier wanted) {
        fetch(c, uri, px, cb, wanted, false);
    }

    public static void fetchUrgent(Context c, final Uri uri, final int px, final Cb cb) {
        fetch(c, uri, px, cb, null, true);
    }

    private static void fetch(Context c, final Uri uri, final int px, final Cb cb, final BooleanSupplier wanted, final boolean urgent) {
        final Context app = c.getApplicationContext();
        final String key = uri + "@" + px;
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            cb.got(hit);
            return;
        }
        if (MISSING.contains(key)) {
            cb.got(null);
            return;
        }
        final boolean share = wanted == null && Looper.myLooper() == Looper.getMainLooper();
        final Job job;
        if (share) {
            Job old = PENDING.get(key);
            if (old != null && (old.urgent || !urgent)) {
                old.cbs.add(cb);
                return;
            }
            job = new Job();
            job.urgent = urgent;
            if (old != null) job.cbs.addAll(old.cbs);
            job.cbs.add(cb);
            PENDING.put(key, job);
        } else {
            job = null;
        }
        (urgent ? HERO : EX).execute(() -> {
            if (wanted != null && !wanted.getAsBoolean()) return;
            Bitmap rr = CACHE.get(key);
            if (rr == null) {
                try {
                    rr = decode(app, uri, px);
                } catch (Throwable t) {
                    CrashGuard.nonFatal("cover art", t);
                }
            }
            final Bitmap r = rr;
            if (r != null) {
                try {
                    r.prepareToDraw();
                } catch (RuntimeException ignored) {
                }
                CACHE.put(key, r);
            }
            else MISSING.add(key);
            MAIN.post(() -> {
                if (job == null) {
                    cb.got(r);
                    return;
                }
                if (PENDING.get(key) != job) return;
                PENDING.remove(key);
                for (Cb x : job.cbs) {
                    try {
                        x.got(r);
                    } catch (RuntimeException e) {
                        CrashGuard.nonFatal("cover callback", e);
                    }
                }
            });
        });
    }

    /** Loads art into an ImageView, guarding against recycled rows. */
    public static void load(Context c, final Uri uri, final ImageView iv, final int px) {
        load(c, uri, iv, px, false);
    }

    public static void load(Context c, final Uri uri, final ImageView iv, final int px, final boolean keepOld) {
        load(c, uri, iv, px, keepOld, null);
    }

    public static void load(Context c, final Uri uri, final ImageView iv, final int px, final boolean keepOld, final Runnable shown) {
        final String key = String.valueOf(uri);
        if (key.equals(iv.getTag(R.id.tag_art)) && iv.getTag(R.id.tag_art_ok) != null) {
            if (shown != null) shown.run();
            return;
        }
        iv.setTag(R.id.tag_art, key);
        iv.setTag(R.id.tag_art_ok, null);
        if (uri != null) {
            Bitmap cached = CACHE.get(key + "@" + px);
            if (cached != null) {
                show(iv, cached, px);
                iv.setTag(R.id.tag_art_ok, Boolean.TRUE);
                if (shown != null) shown.run();
                return;
            }
        }
        boolean hasPicture = keepOld && iv.getDrawable() instanceof android.graphics.drawable.BitmapDrawable;
        if (!hasPicture || uri == null) show(iv, null, px);
        if (uri == null) {
            if (shown != null) shown.run();
            return;
        }
        Cb done = b -> {
            if (b == null && hasPicture && key.equals(iv.getTag(R.id.tag_art))) show(iv, null, px);
            if (b != null && key.equals(iv.getTag(R.id.tag_art))) {
                show(iv, b, px);
                iv.setTag(R.id.tag_art_ok, Boolean.TRUE);
            }
            if (shown != null && key.equals(iv.getTag(R.id.tag_art))) shown.run();
        };
        if (keepOld) fetchUrgent(c, uri, px, done);
        else fetch(c, uri, px, done, () -> key.equals(iv.getTag(R.id.tag_art)));
    }

    private static final int[][] GRAD = {
            {0xFF4A3590, 0xFF16112E}, {0xFF1F5E85, 0xFF0B1C2C}, {0xFF8A3550, 0xFF2B1019}, {0xFF2F7A57, 0xFF0F2B1F},
            {0xFF8A702F, 0xFF2B2210}, {0xFF2F4E8A, 0xFF0F172B}, {0xFF6E358A, 0xFF1F102B}, {0xFF2F8A82, 0xFF0F2B29}};

    /** Every song without cover gets its own deterministic gradient, so a list of "no cover" rows still looks designed. */
    private static void tintBg(ImageView iv) {
        android.graphics.drawable.Drawable d = iv.getBackground();
        if (!(d instanceof android.graphics.drawable.GradientDrawable)) return;
        Object k = iv.getTag(R.id.tag_art);
        int[] g = GRAD[((k == null ? 0 : k.hashCode()) & 0x7fffffff) % GRAD.length];
        android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) d.mutate();
        gd.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        gd.setColors(g);
    }

    public static void show(ImageView iv, Bitmap b, int px) {
        if (b == null) {
            tintBg(iv);
            iv.setImageResource(R.drawable.ic_music);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = px / 4;
            iv.setPadding(p, p, p, p);
            androidx.core.widget.ImageViewCompat.setImageTintList(iv, android.content.res.ColorStateList.valueOf(0x99FFFFFF));
        } else {
            ImageViewCompat.setImageTintList(iv, null);
            iv.setPadding(0, 0, 0, 0);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
        }
    }
}
