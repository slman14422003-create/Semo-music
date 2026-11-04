package com.simomusic.player;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;

public final class Glass {
    private Glass() {
    }

    static final class Snap {
        final Bitmap bmp;
        final int w;
        final int h;

        Snap(Bitmap bmp, int w, int h) {
            this.bmp = bmp;
            this.w = w;
            this.h = h;
        }
    }

    static volatile long hotUntil;

    public static void boost() {
        hotUntil = SystemClock.uptimeMillis() + 300L;
    }

    public static LiveGlass live(View owner, View source, int radiusDp) {
        Context c = owner.getContext();
        LiveGlass g = new LiveGlass(c, owner, source, radiusDp, Ui.color(c, R.color.glass_nav_tint));
        g.setGpu(true);
        owner.setBackground(g);
        Ui.round(owner, Ui.dp(c, radiusDp));
        return g;
    }

    public static void sheet(View root, final boolean card) {
        Context c = root.getContext();
        final int r = Ui.dp(c, 32);
        Activity a = activity(c);
        View decor = a != null && !a.isFinishing() ? a.getWindow().getDecorView() : null;
        if (decor != null && decor.getWidth() > 0 && decor.getHeight() > 0) {
            LiveGlass g = new LiveGlass(c, root, decor, 32, Ui.color(c, R.color.glass_sheet_tint));
            g.setBlur(6);
            g.setSaturation(1.15f);
            g.setSheet(!card);
            g.setGpu(true);
            g.setTicking(true);
            root.setBackground(g);
        } else {
            root.setBackground(new SnapGlass(c, snapshot(c), r, card));
        }
        root.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight() + (card ? 0 : r), r);
            }
        });
        root.setClipToOutline(true);
    }

    static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    static Snap snapshot(Context c) {
        try {
            Activity a = activity(c);
            if (a == null || a.isFinishing()) return null;
            View decor = a.getWindow().getDecorView();
            int w = decor.getWidth();
            int h = decor.getHeight();
            if (w <= 0 || h <= 0) return null;
            int ds = Math.max(6, Math.round(c.getResources().getDisplayMetrics().density * 3f));
            int bw = Math.max(2, w / ds);
            int bh = Math.max(2, h / ds);
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.scale(bw / (float) w, bh / (float) h);
            cv.drawColor(Ui.color(c, R.color.bg));
            decor.draw(cv);
            int[] px = new int[bw * bh];
            b.getPixels(px, 0, bw, 0, 0, bw, bh);
            StackBlur.blur(px, bw, bh, 5);
            b.setPixels(px, 0, bw, 0, 0, bw, bh);
            return new Snap(b, w, h);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
