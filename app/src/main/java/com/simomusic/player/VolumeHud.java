package com.simomusic.player;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * The app's own volume bar. While the app is on screen the hardware volume keys are handled here: the system panel
 * stays hidden and this bar shows instead. One scale, 0-200%: up to 100% it moves the system music volume, past it the
 * software boost ({@link BoostProcessor}). Works on every Android version, Samsung One UI included, because it only
 * uses AudioManager and window key dispatch.
 */
public final class VolumeHud {
    private VolumeHud() {
    }

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Hud> HUDS = new WeakHashMap<>();

    // ------------------------------------------------------------------ level model

    public static int maxLevel(Context c) {
        return Store.boostOn(c) ? 200 : 100;
    }

    /** Current level 0..200. */
    public static int level(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return 0;
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (Store.boostOn(c) && cur >= max && Store.boostPct(c) > 100) return Store.boostPct(c);
        return Math.round(cur * 100f / max);
    }

    public static String label(Context c) {
        return level(c) + "%";
    }

    public static void setLevel(Context c, int level) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            level = Math.max(0, Math.min(maxLevel(c), level));
            if (level <= 100) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level / 100f * max), 0);   // flags 0: no system panel
                if (Store.boostPct(c) != 100) Store.setBoostPct(c, 100);
            } else {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
                Store.setBoostPct(c, Math.round(level / 5f) * 5);
            }
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("set volume", e);
        }
    }

    private static void step(Context c, AudioManager am, boolean up) {
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int pct = Store.boostPct(c);
        if (up) {
            if (cur >= max) {
                if (Store.boostOn(c) && pct < 200) Store.setBoostPct(c, Math.min(200, pct + 10));
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
            }
        } else if (pct > 100) {
            Store.setBoostPct(c, Math.max(100, pct - 10));
        } else {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
        }
    }

    // ------------------------------------------------------------------ key handling

    /** Called once per window (activities from App, sheets from Dlg). */
    public static void install(Window w, final Context c) {
        try {
            if (w == null) return;
            Window.Callback cb = w.getCallback();
            if (cb == null || cb instanceof KeyCatcher) return;
            w.setCallback(new KeyCatcher(cb, c));
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("volume keys", e);
        }
    }

    private static final class KeyCatcher extends androidx.appcompat.view.WindowCallbackWrapper {
        private final Context ctx;

        KeyCatcher(Window.Callback wrapped, Context c) {
            super(wrapped);
            ctx = c;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            int k = e.getKeyCode();
            if ((k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) && handle(ctx, e)) return true;
            return super.dispatchKeyEvent(e);
        }
    }

    private static Activity owner(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static boolean handle(Context c, KeyEvent e) {
        try {
            if (!Store.ownVolume(c)) return false;
            Activity a = owner(c);
            if (a == null || a.isFinishing() || a.isDestroyed()) return false;
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null || am.isVolumeFixed() || am.getMode() != AudioManager.MODE_NORMAL) return false;   // calls and fixed-volume devices stay with the system
            if (!Pb.hasMedia() && !am.isMusicActive()) return false;
            if (e.getAction() == KeyEvent.ACTION_UP) return true;
            step(c, am, e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP);
            show(a);
            return true;
        } catch (RuntimeException ex) {
            CrashGuard.nonFatal("volume key", ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ the bar

    public static void show(Activity a) {
        try {
            Hud h = HUDS.get(a);
            if (h == null) {
                h = new Hud(a);
                HUDS.put(a, h);
            }
            h.show();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("volume bar", e);
        }
    }

    private static final class Hud {
        final Activity act;
        final FrameLayout root;
        final Bar bar;
        final TextView pct;
        final Runnable hide = this::hideNow;
        boolean shown, touching;

        Hud(Activity a) {
            act = a;
            root = new FrameLayout(a);
            root.setVisibility(View.GONE);
            root.setElevation(Ui.dp(a, 12));
            LinearLayout pill = new LinearLayout(a);
            pill.setOrientation(LinearLayout.VERTICAL);
            pill.setGravity(Gravity.CENTER_HORIZONTAL);
            pill.setPadding(Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xEB1C1C1F);
            bg.setCornerRadius(Ui.dp(a, 30));
            bg.setStroke(Ui.dp(a, 1), 0x2AFFFFFF);
            pill.setBackground(bg);
            pct = new TextView(a);
            pct.setTextColor(0xFFFFFFFF);
            pct.setTextSize(13);
            pct.setTypeface(Typeface.DEFAULT_BOLD);
            pct.setGravity(Gravity.CENTER);
            pill.addView(pct, new LinearLayout.LayoutParams(-1, -2));
            bar = new Bar(a);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(a, 44), 0, 1f);
            bp.topMargin = Ui.dp(a, 10);
            bp.bottomMargin = Ui.dp(a, 10);
            pill.addView(bar, bp);
            ImageView ic = new ImageView(a);
            ic.setImageResource(R.drawable.ic_volume);
            ic.setColorFilter(0xFFFFFFFF);
            pill.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
            root.addView(pill, new FrameLayout.LayoutParams(Ui.dp(a, 60), Ui.dp(a, 250)));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
            lp.setMarginEnd(Ui.dp(a, 10));
            ((ViewGroup) a.getWindow().getDecorView()).addView(root, lp);
        }

        void show() {
            bar.sync();
            pct.setText(label(act));
            H.removeCallbacks(hide);
            if (!shown) {
                shown = true;
                root.animate().cancel();
                if (root.getVisibility() != View.VISIBLE) {
                    root.setVisibility(View.VISIBLE);
                    root.setAlpha(0f);
                    root.setTranslationX((Ui.isRtl(act) ? -1 : 1) * Ui.dp(act, 24));
                }
                root.bringToFront();
                root.animate().alpha(1f).translationX(0f).setDuration(Motion.SHORT).setInterpolator(Motion.ENTER).start();
            }
            if (!touching) H.postDelayed(hide, 1900);
        }

        void hideNow() {
            if (!shown || touching) return;
            shown = false;
            root.animate().cancel();
            root.animate().alpha(0f).translationX((Ui.isRtl(act) ? -1 : 1) * Ui.dp(act, 16)).setDuration(Motion.SHORT)
                    .setInterpolator(Motion.EXIT).withEndAction(() -> {
                        if (!shown) root.setVisibility(View.GONE);
                    }).start();
        }

        /** The vertical track. Drag it to set the level; the part past 100% is drawn in orange. */
        final class Bar extends View {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final RectF r = new RectF();
            final Path clip = new Path();
            final int accent;
            float frac;
            int lastTick = -1;

            Bar(Context c) {
                super(c);
                accent = Ui.color(c, R.color.accent_text);
            }

            void sync() {
                frac = level(act) / (float) maxLevel(act);
                invalidate();
            }

            @Override
            protected void onDraw(Canvas cv) {
                float w = Ui.dp(act, 16), cx = getWidth() / 2f;
                float top = 0, bottom = getHeight();
                r.set(cx - w / 2, top, cx + w / 2, bottom);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x33FFFFFF);
                cv.drawRoundRect(r, w / 2, w / 2, p);
                clip.reset();
                clip.addRoundRect(r, w / 2, w / 2, Path.Direction.CW);
                cv.save();
                cv.clipPath(clip);
                float fillTop = bottom - (bottom - top) * Math.max(0f, Math.min(1f, frac));
                boolean boost = maxLevel(act) > 100;
                float y100 = boost ? bottom - (bottom - top) * 0.5f : top;
                p.setColor(0xFFFFFFFF);
                if (!boost || fillTop >= y100) {
                    cv.drawRect(r.left, fillTop, r.right, bottom, p);
                } else {
                    cv.drawRect(r.left, y100, r.right, bottom, p);
                    p.setColor(0xFFFF9F0A);
                    cv.drawRect(r.left, fillTop, r.right, y100, p);
                }
                cv.restore();
                if (boost) {
                    p.setColor(0x99FFFFFF);
                    cv.drawRect(cx - w / 2 - Ui.dp(act, 3), y100 - 1, cx + w / 2 + Ui.dp(act, 3), y100 + 1, p);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        touching = true;
                        H.removeCallbacks(hide);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        Ui.gestureStart(this);
                        // fall through
                    case MotionEvent.ACTION_MOVE: {
                        float f = 1f - Math.max(0f, Math.min(1f, e.getY() / Math.max(1, getHeight())));
                        int lvl = Math.round(f * maxLevel(act));
                        setLevel(act, lvl);
                        frac = level(act) / (float) maxLevel(act);
                        pct.setText(label(act));
                        int tick = level(act) / 10;
                        if (tick != lastTick) {
                            lastTick = tick;
                            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                        }
                        invalidate();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        touching = false;
                        lastTick = -1;
                        H.postDelayed(hide, 1500);
                        return true;
                    default:
                        return true;
                }
            }
        }
    }
}
