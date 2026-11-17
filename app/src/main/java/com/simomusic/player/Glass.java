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

    /**
     * Glass level (0 = off, 1..10) and style (0 = frosted glass, 1 = clear crystal), chosen in Settings.
     * The level sets how much blur and how little tint a surface gets; crystal is clearer and sharper than glass.
     */
    public static volatile boolean OFF;
    public static volatile int LEVEL = 6;
    public static volatile int STYLE;

    public static void load(Context c) {
        android.content.SharedPreferences p = Store.prefs(c);
        int lv = p.getInt("glass_level", p.getBoolean("glass_off", false) ? 0 : 6);
        LEVEL = Math.max(0, Math.min(10, lv));
        STYLE = p.getInt("glass_style", 0) == 1 ? 1 : 0;
        OFF = LEVEL == 0;
    }

    public static void save(Context c, int level, int style) {
        Store.prefs(c).edit().putInt("glass_level", level).putInt("glass_style", style).apply();
        load(c);
    }

    /** How much of the blur radius is used: 1 at the default level. */
    static float blurScale() {
        float f = LEVEL / 6f;
        return STYLE == 1 ? Math.max(0.25f, 0.4f * f) : Math.max(0.3f, f);
    }

    /** How much of the tint opacity is kept: more level = more see-through. */
    static float tintScale() {
        float f = LEVEL / 6f;
        float s = STYLE == 1 ? 1.3f - 0.75f * f : 1.5f - 0.5f * f;
        return Math.max(0.15f, Math.min(1.6f, s));
    }

    static int blur(int base) {
        return Math.max(1, Math.round(base * blurScale()));
    }

    /** The same color with its opacity scaled by the glass level. */
    public static int tint(int color) {
        int a = Math.min(255, Math.max(0, Math.round(((color >>> 24) & 0xFF) * tintScale())));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Sets one of the see-through backgrounds; at level 0 it is painted solid, otherwise its opacity follows the level. */
    public static void bg(View v, int res) {
        boolean glassy = res == R.drawable.bg_card || res == R.drawable.bg_segment || res == R.drawable.btn_secondary
                || res == R.drawable.bg_icon_btn || res == R.drawable.bg_input || res == R.drawable.bg_pill_glass
                || res == R.drawable.bg_glass_deck;
        boolean plain = !OFF && LEVEL == 6 && STYLE == 0;
        if (!glassy || plain) {
            v.setBackgroundResource(res);
            return;
        }
        Context c = v.getContext();
        android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(c, res);
        if (d == null) {
            v.setBackgroundResource(res);
            return;
        }
        d = d.mutate();
        int col = 0;   // 0 = keep the drawable's own color and only scale its opacity
        if (OFF) {
            col = res == R.drawable.bg_pill_glass || res == R.drawable.bg_glass_deck ? 0xFF26262D
                    : Ui.color(c, res == R.drawable.bg_card ? R.color.surface : R.color.surface_high);
        }
        paint(d, col);
        v.setBackground(d);
    }

    private static void paint(android.graphics.drawable.Drawable d, int col) {
        if (d instanceof android.graphics.drawable.GradientDrawable) {
            android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) d;
            if (col != 0) gd.setColor(col);
            else {
                int[] cs = gd.getColors();
                if (cs != null && cs.length > 0) {
                    int[] n = new int[cs.length];
                    for (int i = 0; i < cs.length; i++) n[i] = tint(cs[i]);
                    gd.setColors(n);
                } else if (gd.getColor() != null) {
                    gd.setColor(tint(gd.getColor().getDefaultColor()));
                }
            }
        } else if (d instanceof android.graphics.drawable.InsetDrawable) {
            paint(((android.graphics.drawable.InsetDrawable) d).getDrawable(), col);
        } else if (d instanceof android.graphics.drawable.LayerDrawable) {   // also covers the ripple
            android.graphics.drawable.LayerDrawable l = (android.graphics.drawable.LayerDrawable) d;
            for (int i = 0; i < l.getNumberOfLayers(); i++) paint(l.getDrawable(i), col);
        }
    }

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
        sheet(root, card, false);
    }

    public static void sheet(View root, final boolean card, boolean frozen) {
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
            g.setFrozen(frozen);
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
