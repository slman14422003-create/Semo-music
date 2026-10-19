package com.simomusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.widget.ImageView;

/** Soft, colorful full-screen backdrop made from the cover (tiny bitmap scaled up = free blur, works on every Android). */
public final class Backdrop {
    private Backdrop() {
    }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    public static ImageView view(Context c) {
        ImageView bg = new ImageView(c);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(0f);
        return bg;
    }

    static Bitmap soft(Bitmap src) {
        Bitmap b = src;
        while (b.getWidth() / 2 >= 24 && b.getHeight() / 2 >= 24) {
            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
        }
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.45f);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(b, 0f, 0f, p);
        out.prepareToDraw();
        return out;
    }

    /** Shows the cover of the given track as backdrop (cross-fades); null hides it so the default gradient shows. */
    public static void set(Context c, final ImageView bg, final Uri u) {
        set(c, bg, u, 256);
    }

    public static void set(final Context c, final ImageView bg, final Uri u, final int px) {
        final String key = String.valueOf(u);
        bg.setTag(R.id.tag_art, key);
        if (u == null) {
            bg.animate().alpha(0f).setDuration(Motion.SHORT).start();
            return;
        }
        Art.fetchUrgent(c, u, px, bmp -> {
            if (!key.equals(bg.getTag(R.id.tag_art))) return;
            if (bmp == null) {
                bg.animate().alpha(0f).setDuration(Motion.SHORT).start();
                return;
            }
            Art.FX.execute(() -> {
                final Bitmap small = soft(bmp);
                MAIN.post(() -> {
                    if (!key.equals(bg.getTag(R.id.tag_art))) return;
                    android.graphics.drawable.Drawable now = new android.graphics.drawable.BitmapDrawable(c.getResources(), small);
                    android.graphics.drawable.Drawable before = bg.getDrawable();
                    if (before instanceof android.graphics.drawable.TransitionDrawable) {
                        before = ((android.graphics.drawable.TransitionDrawable) before).getDrawable(1);
                    }
                    if (before != null && bg.getAlpha() > 0.05f) {
                        android.graphics.drawable.TransitionDrawable td =
                                new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{before, now});
                        td.setCrossFadeEnabled(true);
                        bg.setImageDrawable(td);
                        td.startTransition((int) Motion.AMBIENT);
                    } else {
                        bg.setImageDrawable(now);
                        bg.setAlpha(0f);
                        bg.animate().alpha(1f).setDuration(Motion.AMBIENT).start();
                    }
                });
            });
        });
    }
}
