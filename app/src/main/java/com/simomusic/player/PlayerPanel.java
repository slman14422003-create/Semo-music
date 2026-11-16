package com.simomusic.player;

import android.content.Context;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.media3.session.MediaController;

/**
 * The full player as a sheet that follows your finger. Drag the mini player up (or tap it) to fill the screen, drag the
 * player down to collapse it; swipe the cover left / right for next / previous. The same view is used full-screen by
 * PlayerActivity (opened from the notification or the lock screen), where "collapse" closes the activity.
 */
public final class PlayerPanel extends FrameLayout {
    public interface Host {
        /** Panel is fully hidden again. */
        void onCollapsed();

        /** 0 = collapsed ... 1 = fills the screen (use it to dim / scale what is behind). */
        void onProgress(float f);

        void onState(boolean expanded);
    }

    private final Activity act;
    private final Host host;
    private final NowPlaying np;
    private final ImageButton fav, timer, bt;
    private EqView eq;
    private Halo halo;
    private final LinearLayout col;
    private final int slop, maxRadius;
    private View parBg, parGlow;   // parallax layers
    private float collapsedY = 1000f, radius, downX, downY, startRaw;
    private boolean expanded, tracking, onSeek;
    private SpringAnimation anim;
    private float releaseVy;      // finger speed at the moment of letting go: the spring carries it on, no sudden stop
    private int barsState = -1;
    private VelocityTracker vt;

    @SuppressLint("ClickableViewAccessibility")
    public PlayerPanel(Activity a, Host h) {
        super(Ui.dark(a));
        final android.content.Context d = getContext();
        act = a;
        host = h;
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
        maxRadius = Ui.dp(a, 28);
        radius = maxRadius;
        setBackgroundResource(R.drawable.bg_default_backdrop);
        setVisibility(GONE);
        setClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                // extend below the view so only the top corners are rounded
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight() + (int) radius, radius);
            }
        });
        setClipToOutline(true);

        np = new NowPlaying(d, 320, false);
        final ImageView bg = Backdrop.view(d);
        parBg = bg;
        addView(bg, new LayoutParams(-1, -1));
        ArtGlow glow = new ArtGlow(d);
        addView(glow, new LayoutParams(-1, -1));
        np.onTint = tintColor -> {
            glow.setColor(tintColor);
            if (halo != null) halo.setColor(tintColor);
        };
        parGlow = glow;
        View scrim = new View(d);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        addView(scrim, new LayoutParams(-1, -1));

        col = new LinearLayout(d);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), Ui.dp(a, 18));

        // grab handle, like every One UI sheet: it says "pull me down", and the whole screen answers to that gesture
        View grab = new View(d);
        android.graphics.drawable.GradientDrawable gh = new android.graphics.drawable.GradientDrawable();
        gh.setColor(0x66FFFFFF);
        gh.setCornerRadius(Ui.dp(a, 3));
        grab.setBackground(gh);
        LinearLayout.LayoutParams ghp = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 5));
        ghp.gravity = Gravity.CENTER_HORIZONTAL;
        ghp.topMargin = Ui.dp(a, 2);
        col.addView(grab, ghp);

        LinearLayout top = new LinearLayout(d);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.icon(d, R.drawable.ic_arrow_down, R.string.close, v -> collapse(true)));
        LinearLayout chip = new LinearLayout(d);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_pill_glass);
        chip.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        eq = new EqView(d);
        chip.addView(eq, new LinearLayout.LayoutParams(Ui.dp(a, 16), Ui.dp(a, 14)));
        TextView cap = Ui.text(d, a.getString(R.string.now_playing), 13, R.color.text_primary);
        cap.setTypeface(Typeface.DEFAULT_BOLD);
        cap.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams capLp = Ui.lp(-2, -2);
        capLp.setMarginStart(Ui.dp(a, 8));
        chip.addView(cap, capLp);
        top.addView(Ui.space(d, 1f));
        ImageButton vol = Ui.icon(d, R.drawable.ic_volume, R.string.volume_title, v -> VolumeSheet.show(a));
        ((LinearLayout.LayoutParams) vol.getLayoutParams()).setMarginEnd(Ui.dp(a, 8));
        top.addView(vol);
        ImageButton lyr = Ui.icon(d, R.drawable.ic_lyrics, R.string.lyrics, v -> Ui.go(a, new android.content.Intent(a, LyricsActivity.class)));
        ((LinearLayout.LayoutParams) lyr.getLayoutParams()).setMarginEnd(Ui.dp(a, 8));
        top.addView(lyr);
        top.addView(Ui.icon(d, R.drawable.ic_more, R.string.more, v -> Menus.more(a, this::refreshExtras)));
        LinearLayout.LayoutParams topLp = Ui.lp(-1, -2);
        topLp.topMargin = Ui.dp(a, 8);
        col.addView(top, topLp);

        CoverBox cover = new CoverBox(d, 360);
        cover.setContentDescription(d.getString(R.string.cover_art));
        cover.setPadding(0, Ui.dp(a, 20), 0, Ui.dp(a, 14));
        halo = new Halo(d);
        cover.setHalo(halo);
        cover.setCover(np.art);
        col.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1f));
        attachCoverSwipe(np.art);
        // "Now playing" lives between the cover and the title now, centred, instead of crowding the top bar
        LinearLayout.LayoutParams chp = Ui.lp(-2, -2);
        chp.gravity = Gravity.CENTER_HORIZONTAL;
        chp.topMargin = Ui.dp(a, 2);
        chp.bottomMargin = Ui.dp(a, 4);
        col.addView(chip, chp);

        LinearLayout titleRow = new LinearLayout(d);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(np.info, Ui.weight(1));
        fav = Ui.icon(d, R.drawable.ic_heart, R.string.favorite, v -> {
            long id = Pb.currentId();
            if (id >= 0) Store.toggle(a, Store.FAV, id);
            heart(true);
        });
        titleRow.addView(fav);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.setMargins(Ui.dp(a, 4), Ui.dp(a, 6), 0, 0);
        col.addView(titleRow, tp);
        TextView un = np.upNextView();
        if (un != null && un.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams ul = (LinearLayout.LayoutParams) un.getLayoutParams();
            ul.width = -2;
            ul.topMargin = Ui.dp(a, 10);
            un.setLayoutParams(ul);
            un.setBackgroundResource(R.drawable.bg_pill_glass);
            un.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6));
        }

        // one glass deck: progress, transport and the six extra buttons, right under the thumb
        LinearLayout deck = new LinearLayout(d);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setBackgroundResource(R.drawable.bg_glass_deck);
        deck.setPadding(Ui.dp(a, 18), Ui.dp(a, 14), Ui.dp(a, 18), Ui.dp(a, 8));
        deck.addView(np.seekBlock, Ui.lp(-1, -2));
        LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
        cp.topMargin = Ui.dp(a, 2);
        deck.addView(np.controls, cp);
        View line = new View(d);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lnp = Ui.lp(-1, 1);
        lnp.setMargins(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 4));
        deck.addView(line, lnp);

        LinearLayout extras = new LinearLayout(d);
        extras.setGravity(Gravity.CENTER_VERTICAL);
        timer = Ui.flat(d, R.drawable.ic_timer, 46, R.string.sleep_timer, v -> Menus.sleep(a, this::refreshExtras));
        bt = Ui.flat(d, R.drawable.ic_bluetooth, 46, R.string.bt_title, v -> BtSheet.show(a));
        ImageButton[] ex = {
                np.shuffle,
                Ui.flat(d, R.drawable.ic_add, 46, R.string.add_to_playlist, v -> {
                    long id = Pb.currentId();
                    if (id >= 0) Menus.addToPlaylist(a, id, () -> heart(false));
                }),
                Ui.flat(d, R.drawable.ic_list, 46, R.string.up_next, v -> Menus.queue(a)),
                bt,
                timer,
                np.repeat};
        for (ImageButton b : ex) {
            b.setScaleType(ImageView.ScaleType.CENTER);
            b.setPadding(0, 0, 0, 0);
            extras.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f));
        }
        deck.addView(extras, Ui.lp(-1, -2));
        LinearLayout.LayoutParams dkp = Ui.lp(-1, -2);
        dkp.topMargin = Ui.dp(a, 12);
        col.addView(deck, dkp);
        col.setClipChildren(false);
        setClipChildren(false);

        addView(col, new LayoutParams(-1, -1));
        Ui.edgeToEdge(a, col);
        np.onRefresh = () -> {
            androidx.media3.session.MediaController mc = Pb.get();
            boolean live = mc != null && mc.isPlaying();
            eq.setAnimating(live);
            halo.setPlaying(live);
            long id = Pb.currentId();
            if (id == lastExtrasId) return;
            lastExtrasId = id;
            heart(false);
        };
        np.artCb = u -> Backdrop.set(a, bg, u, np.artPx());
    }

    // ------------------------------------------------------------------ public API

    public void setCollapsedY(float y) {
        collapsedY = Math.max(1f, y);
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Call from the activity's onStart / onStop. */
    public void onHostStart() {
        if (getVisibility() == VISIBLE) np.start();
    }

    public void onHostStop() {
        np.stop();
    }

    /** Mini player drag: panel appears at the mini player and follows the finger upward. */
    public void beginDrag() {
        if (anim != null) anim.cancel();
        resetKids();
        setVisibility(VISIBLE);
        layer(true);
        col.requestApplyInsets();
        setTranslationY(collapsedY);
        np.start();
        applyProgress();
    }

    public void dragUp(float dist) {
        setTranslationY(Math.max(0f, collapsedY - dist));
        applyProgress();
    }

    public void endDrag(float vy) {
        releaseVy = vy;
        float f = 1f - getTranslationY() / collapsedY;
        // short throw is enough: a thumb-length drag or a light flick opens the player
        float dragged = collapsedY - getTranslationY();
        if (vy < -Gest.fling(act, 200) || (vy < Gest.fling(act, 260) && (dragged > Ui.dp(act, 80) || f > 0.35f))) expand(true);
        else collapse(true);
    }

    private void expandFromLock(final int[] from) {
        setVisibility(VISIBLE);
        col.requestApplyInsets();
        setTranslationY(0f);
        applyProgress();
        expanded = true;
        host.onState(true);
        np.start();
        setAlpha(0f);
        animate().alpha(1f).setDuration(Motion.SHORT).setInterpolator(Motion.ENTER).start();
        final float rise = Ui.dp(act, 18);
        for (int i = 1; i < col.getChildCount(); i++) {
            final View v = col.getChildAt(i);
            if (v instanceof CoverBox) continue;
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate().alpha(1f).translationY(0f).setStartDelay(40L + (i - 1) * 28L).setDuration(340).setInterpolator(Motion.ENTER).start();
        }
        flipCover(from);
    }

    /** The player was already open when the phone was locked: only the cover glides down from the lock screen's position. */
    public void replayHandoff() {
        if (getVisibility() != VISIBLE) return;
        int[] from = LockHandoff.take();
        if (from != null) flipCover(from);
    }

    /** The cover starts exactly where the lock screen's cover was and settles into its place here (same size, same spot, one motion). */
    private void flipCover(final int[] from) {
        final android.widget.ImageView art = np.art;
        getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                try {
                    int tw = art.getWidth(), th = art.getHeight();
                    if (tw <= 0 || th <= 0) return true;
                    int[] loc = new int[2];
                    art.getLocationOnScreen(loc);
                    float endX = art.getScaleX(), endY = art.getScaleY();
                    float s = from[2] / (float) tw;
                    art.animate().cancel();
                    art.setTranslationX(from[0] + from[2] / 2f - (loc[0] + tw / 2f));
                    art.setTranslationY(from[1] + from[3] / 2f - (loc[1] + th / 2f));
                    art.setScaleX(s);
                    art.setScaleY(s);
                    art.animate().translationX(0f).translationY(0f).scaleX(endX).scaleY(endY)
                            .setDuration(Motion.LONG + 60).setInterpolator(Motion.ENTER).start();
                } catch (RuntimeException e) {
                    CrashGuard.nonFatal("lock handoff", e);
                }
                return true;
            }
        });
        invalidate();       // a draw pass is guaranteed even when nothing else changed
    }

    public void expand(boolean animate) {
        if (animate && getVisibility() != VISIBLE) {
            int[] from = LockHandoff.take();
            if (from != null) {
                expandFromLock(from);
                return;
            }
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            col.requestApplyInsets();
            setTranslationY(collapsedY);
            applyProgress();
        }
        expanded = true;
        host.onState(true);
        np.start();
        if (animate) {
            layer(true);
            if (getTranslationY() >= collapsedY - 2f) stagger();   // opened by a tap (not by a drag): content arrives one piece at a time
            animateTo(0f, null);
        } else {
            setTranslationY(0f);
            applyProgress();
            layer(false);
        }
    }

    /** Opening choreography: the cover swells in with a soft overshoot, the rest of the sheet rises behind it in sequence. */
    private void resetKids() {
        for (int i = 1; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setScaleX(1f);
            v.setScaleY(1f);
        }
    }

    private void stagger() {
        final float rise = Ui.dp(act, 34);
        for (int i = 1; i < col.getChildCount(); i++) {
            final View v = col.getChildAt(i);
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(rise + i * Ui.dp(act, 6));
            if (v instanceof CoverBox) {
                v.setScaleX(0.84f);
                v.setScaleY(0.84f);
            }
            long delay = 50L + (i - 1) * 38L;
            android.view.ViewPropertyAnimator pa = v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay).setDuration(380).setInterpolator(Motion.ENTER);
            if (v instanceof CoverBox) pa.scaleX(1f).scaleY(1f).setDuration(460).setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));
            pa.start();
        }
    }

    public void collapse(boolean animate) {
        expanded = false;
        host.onState(false);
        final Runnable end = () -> {
            resetKids();
            setVisibility(GONE);
            np.stop();
            host.onCollapsed();
        };
        if (animate && getVisibility() == VISIBLE) {
            layer(true);
            animateTo(collapsedY, end);
        } else {
            setTranslationY(collapsedY);
            applyProgress();
            layer(false);
            end.run();
        }
    }

    // ------------------------------------------------------------------ internals

    private void layer(boolean on) {
        if (getLayerType() != LAYER_TYPE_NONE) setLayerType(LAYER_TYPE_NONE, null);
        if (col.getLayerType() != LAYER_TYPE_NONE) col.setLayerType(LAYER_TYPE_NONE, null);
        if (Gest.busy != on) {
            Gest.busy = on;
            if (!on) Gest.kick(this);
        }
    }

    /** The sheet can be grabbed when open, and also while it is still animating (open or closed). */
    private boolean canGrab() {
        return expanded || (getVisibility() == VISIBLE && anim != null && anim.isRunning());
    }

    /**
     * Spring physics instead of a fixed-time curve: the sheet keeps the speed of the finger that threw it, can be caught
     * again at any moment without a jump, and settles the way One UI's own sheets do.
     */
    private void animateTo(final float target, final Runnable end) {
        if (anim != null) anim.cancel();
        final SpringAnimation sa = new SpringAnimation(this, DynamicAnimation.TRANSLATION_Y, target);
        sa.getSpring().setStiffness(target == 0f ? Motion.STIFF_OPEN : Motion.STIFF_CLOSE).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        sa.setStartVelocity(releaseVy);
        releaseVy = 0f;
        sa.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        sa.addUpdateListener((animation, value, velocity) -> applyProgress());
        sa.addEndListener((animation, canceled, value, velocity) -> {
            if (canceled) return;
            setTranslationY(target);
            applyProgress();
            layer(false);
            if (end != null) end.run();
        });
        anim = sa;
        sa.start();
    }

    /** One place derives everything from how far the sheet is open: fade, corner radius, what is behind. */
    private void applyProgress() {
        float f = 1f - getTranslationY() / collapsedY;
        f = Math.max(0f, Math.min(1f, f));
        setAlpha(Math.min(1f, f / 0.22f));
        col.setAlpha(Math.max(0f, Math.min(1f, (f - 0.18f) / 0.6f)));
        float nr = maxRadius * Math.min(1f, (1f - f) * 6f);
        if (nr != radius && (Math.abs(nr - radius) > 0.6f || nr < 0.01f || nr > maxRadius - 0.01f)) {
            radius = nr;
            invalidateOutline();
        }
        // parallax: the far layers drift slowly, the content catches up, so the sheet feels like it has depth
        float inv = 1f - f;
        if (parBg != null) parBg.setTranslationY(-inv * Ui.dp(act, 70));
        if (parGlow != null) parGlow.setTranslationY(-inv * Ui.dp(act, 40));
        col.setTranslationY(inv * Ui.dp(act, 26));
        float sc = 0.95f + 0.05f * f;
        col.setScaleX(sc);
        col.setScaleY(sc);
        float ps = 0.7f + 0.3f * f;
        setPivotX(getWidth() / 2f);
        setPivotY(getHeight());
        setScaleX(ps);
        setScaleY(ps);
        barsFor(f);
        host.onProgress(f);
    }

    /** The open player is always dark: light status-bar icons while it covers the screen, the theme's own when it is gone. */
    private void barsFor(float f) {
        int want = f > 0.45f ? 1 : 0;
        if (want == barsState) return;
        barsState = want;
        Ui.bars(act, want == 1 || Ui.isNight(act));
    }

    private void heart(boolean animate) {
        long id = Pb.currentId();
        boolean on = id >= 0 && Store.has(act, Store.FAV, id);
        fav.setImageResource(on ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        Ui.tint(fav, on ? R.color.accent_text : R.color.text_primary);
        if (animate && on) {
            Ui.pop(fav);
            Ui.confirm(fav);
        }
    }

    private long lastExtrasId = -2;

    private void refreshExtras() {
        try {
            refreshExtrasInner();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("player extras", e);
        }
    }

    private void refreshExtrasInner() {
        heart(false);
        Ui.tint(timer, Pb.sleepAt() > 0 ? R.color.accent_text : R.color.text_primary);
        final Context app = act.getApplicationContext();
        Bg.query("bluetooth state", () -> {
            final boolean on = BtAudio.connected(app);
            Bg.main(() -> {
                if (isAttachedToWindow()) Ui.tint(bt, on ? R.color.accent_text : R.color.text_primary);
            });
        });
    }

    private final android.media.AudioDeviceCallback btWatch = new android.media.AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] a) {
            refreshExtras();
        }

        @Override
        public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] r) {
            refreshExtras();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        android.media.AudioManager am = (android.media.AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.registerAudioDeviceCallback(btWatch, new android.os.Handler(android.os.Looper.getMainLooper()));
        refreshExtras();
    }

    private boolean hit(View v, MotionEvent e) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        int pad = Ui.dp(act, 14);
        return e.getRawX() >= l[0] && e.getRawX() <= l[0] + v.getWidth()
                && e.getRawY() >= l[1] - pad && e.getRawY() <= l[1] + v.getHeight() + pad;
    }

    // ---- drag the whole sheet down (collapse) ---------------------------------

    private long vtLast;
    private boolean swallow;

    private void vtReset(MotionEvent e) {
        if (vt != null) vt.recycle();
        vt = VelocityTracker.obtain();
        Gest.feed(vt, e);
        vtLast = e.getEventTime();
    }

    private void vtAdd(MotionEvent e) {
        if (vt == null || e.getEventTime() == vtLast) return;
        vtLast = e.getEventTime();
        Gest.feed(vt, e);
    }

    private void cancelChildren(MotionEvent e) {
        MotionEvent c = MotionEvent.obtain(e);
        c.setAction(MotionEvent.ACTION_CANCEL);
        swallow = true;
        try {
            super.dispatchTouchEvent(c);
        } finally {
            swallow = false;
            c.recycle();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = e.getRawX();
            downY = e.getRawY();
            onSeek = hit(np.seekBlock, e);
            vtReset(e);
        } else if (a == MotionEvent.ACTION_MOVE) {
            vtAdd(e);
            if (!tracking && canGrab() && !onSeek && Gest.downLoose(e.getRawX() - downX, e.getRawY() - downY, slop)) {
                cancelChildren(e);
                startTracking(e);
                setTranslationY(Math.max(0f, Math.min(collapsedY, e.getRawY() - startRaw)));
                applyProgress();
                return true;
            }
        } else if (a == MotionEvent.ACTION_UP && !tracking && canGrab() && !onSeek && vt != null) {
            vtAdd(e);
            vt.computeCurrentVelocity(1000);
            float vy = vt.getYVelocity();
            float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
            if (vy > Gest.fling(act, 600) && dy > Ui.dp(act, 20) && dy > Math.abs(dx) * 1.2f) {
                cancelChildren(e);
                releaseVy = vy;
                vt.recycle();
                vt = null;
                collapse(true);
                return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                onSeek = hit(np.seekBlock, e);
                vtReset(e);
                break;
            case MotionEvent.ACTION_MOVE:
                vtAdd(e);
                if (!tracking && canGrab() && !onSeek) {
                    if (Gest.downLoose(e.getRawX() - downX, e.getRawY() - downY, slop)) {
                        startTracking(e);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (vt != null && !tracking) {
                    vt.recycle();
                    vt = null;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void startTracking(MotionEvent e) {
        tracking = true;
        if (anim != null) anim.cancel();
        Ui.gestureStart(this);
        layer(true);
        // keep the sheet exactly under the finger: no jump when it is caught mid-animation
        startRaw = e.getRawY() - getTranslationY();
        if (vt == null) vtReset(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (swallow) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                onSeek = false;
                vtReset(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                vtAdd(e);
                if (!tracking) {
                    float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                    if (canGrab() && Gest.downLoose(dx, dy, slop)) startTracking(e);
                    return true;
                }
                setTranslationY(Math.max(0f, Math.min(collapsedY, e.getRawY() - startRaw)));
                applyProgress();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    vtAdd(e);
                    float vy = 0f;
                    if (vt != null) {
                        vt.computeCurrentVelocity(1000);
                        vy = vt.getYVelocity();
                    }
                    tracking = false;
                    releaseVy = vy;
                    Ui.gestureEnd(this);
                    boolean closing = e.getActionMasked() == MotionEvent.ACTION_UP
                            && (vy > Gest.fling(act, 180) || (vy > -Gest.fling(act, 260) && getTranslationY() > Math.min(collapsedY * 0.28f, Ui.dp(act, 90))));
                    if (closing) collapse(true);
                    else expand(true);
                }
                if (vt != null) {
                    vt.recycle();
                    vt = null;
                }
                return true;
            default:
                return true;
        }
    }

    // ---- swipe the cover: next / previous ---------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private void attachCoverSwipe(View cover) {
        cover.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            boolean drag, crossed;
            VelocityTracker t;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        drag = false;
                        crossed = false;
                        if (t != null) t.recycle();
                        if (android.os.SystemClock.uptimeMillis() < coverBusyUntil) {
                            t = null;               // the previous swipe is still flying: ignore this touch
                            return true;
                        }
                        v.animate().cancel();       // a spring-back in progress must not fight the finger
                        t = VelocityTracker.obtain();
                        Gest.feed(t, e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (t == null) return false;
                        Gest.feed(t, e);
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!drag && Gest.horizontal(dx, dy, slop)) {
                            drag = true;
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // the cover is drawn once while it flies around
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (drag) {
                            float w = v.getWidth();
                            float shown = canGo(dx < 0) ? dx : dx * 0.28f;   // rubber band when there is nothing there
                            boolean over = canGo(dx < 0) && Math.abs(dx) > Math.min(w * 0.28f, Ui.dp(act, 56));   // 56 dp is enough to change the song
                            if (over && !crossed) {          // a small tick when letting go would change the song
                                crossed = true;
                                Ui.tap(v);
                            } else if (!over && crossed) {
                                crossed = false;
                            }
                            v.setTranslationX(shown);
                            v.setRotation(shown / w * 10f);
                            v.setAlpha(1f - Math.min(0.55f, Math.abs(shown) / (w * 1.4f)));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (t == null) return false;
                        Gest.feed(t, e);
                        t.computeCurrentVelocity(1000);
                        float vx = t.getXVelocity();
                        t.recycle();
                        t = null;
                        float dx = e.getRawX() - x0;
                        boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                        if (!drag) {
                            if (up && Math.abs(dx) < slop && Math.abs(e.getRawY() - y0) < slop) onCoverTap(v, e.getX());
                            if (v.getTranslationX() != 0f || v.getRotation() != 0f || v.getAlpha() != 1f) springBack(v);
                            return true;
                        }
                        v.postDelayed(() -> v.setLayerType(View.LAYER_TYPE_NONE, null), 700);
                        float w = v.getWidth();
                        boolean go = up && (Math.abs(dx) > Math.min(w * 0.28f, Ui.dp(act, 56))
                                || (Math.abs(vx) > Gest.fling(act, 220) && Math.abs(dx) > Ui.dp(act, 14) && Math.signum(vx) == Math.signum(dx)));
                        boolean next = dx < 0;
                        MediaController mc = Pb.get();
                        if (go && !next && mc != null && mc.getCurrentPosition() > 3000) {
                            mc.seekTo(0);       // "previous" after 3 s only restarts the song, like every player
                            Ui.tap(v);
                            springBack(v);
                        } else if (go && canGo(next)) {
                            commitSwipe(v, next);
                        } else {
                            springBack(v);
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    private long lastTap, seekTime, coverBusyUntil;
    private int lastZone = 2, seekZone = 2;
    private final android.os.Handler tapH = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingToggle;

    /**
     * Middle of the cover: play / pause at once. Left / right third: double tap seeks 10 s back / forward (keep tapping
     * to keep seeking); a single tap there plays / pauses after a short wait for the second tap.
     */
    private void onCoverTap(final View v, float x) {
        float rel = x / Math.max(1f, v.getWidth());
        int zone = rel < 0.32f ? -1 : rel > 0.68f ? 1 : 0;
        long now = android.os.SystemClock.uptimeMillis();
        if (pendingToggle != null) {
            tapH.removeCallbacks(pendingToggle);
            pendingToggle = null;
        }
        if (zone == 0) {
            lastZone = 2;
            togglePlay(v);
            return;
        }
        boolean chain = zone == seekZone && now - seekTime < 700;
        boolean dbl = zone == lastZone && now - lastTap < 320;
        if (chain || dbl) {
            Ui.tap(v);
            seekBy(zone * 10000L);
            SeekBubble.show(v, zone, zone > 0 ? "+10s" : "-10s");
            seekZone = zone;
            seekTime = now;
            lastZone = 2;
            return;
        }
        lastZone = zone;
        lastTap = now;
        pendingToggle = () -> {
            pendingToggle = null;
            togglePlay(v);
        };
        tapH.postDelayed(pendingToggle, 320);
    }

    private void seekBy(long delta) {
        MediaController m = Pb.get();
        if (m == null) return;
        long d = m.getDuration(), to = m.getCurrentPosition() + delta;
        if (d != androidx.media3.common.C.TIME_UNSET && d > 0) to = Math.min(to, d - 500);
        m.seekTo(Math.max(0, to));
    }

    private boolean canGo(boolean next) {
        MediaController m = Pb.get();
        if (m == null) return false;
        return next ? m.hasNextMediaItem() : (m.hasPreviousMediaItem() || m.getCurrentPosition() > 3000);
    }

    private void togglePlay(View v) {
        MediaController m = Pb.get();
        if (m == null) return;
        Ui.tap(v);
        if (m.isPlaying()) m.pause();
        else m.play();
    }

    /** Throws the old cover off the side, switches track, and lets the new cover settle in from the opposite side. */
    private void commitSwipe(final View v, final boolean next) {
        final float w = v.getWidth(), dir = next ? -1f : 1f;
        coverBusyUntil = android.os.SystemClock.uptimeMillis() + Motion.SHORT + Motion.BASE + 80;
        np.quietArt = true;
        Ui.confirm(v);
        v.animate().translationX(dir * w * 1.15f).rotation(dir * 14f).alpha(0f).setDuration(Motion.SHORT)
                .setInterpolator(Motion.EXIT).withEndAction(() -> {
                    MediaController m = Pb.get();
                    if (m != null) {
                        int ti = next ? m.getNextMediaItemIndex() : m.getPreviousMediaItemIndex();
                        if (ti >= 0 && ti < m.getMediaItemCount()) {
                            Art.load(act, m.getMediaItemAt(ti).mediaMetadata.artworkUri, np.art, np.artPx(), true);
                        }
                        if (next) m.seekToNext();
                        else m.seekToPrevious();
                    }
                    v.setTranslationX(-dir * w * 0.7f);
                    v.setRotation(-dir * 10f);
                    v.animate().translationX(0f).rotation(0f).alpha(1f).setDuration(Motion.BASE)
                            .setInterpolator(Motion.SPRING).withEndAction(() -> np.quietArt = false).start();
                }).start();
    }

    private void springBack(View v) {
        v.animate().translationX(0f).rotation(0f).alpha(1f).setDuration(Motion.SHORT)
                .setInterpolator(Motion.SPRING).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        tapH.removeCallbacksAndMessages(null);
        try {
            android.media.AudioManager am = (android.media.AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.unregisterAudioDeviceCallback(btWatch);
        } catch (Exception ignored) {
        }
        np.stop();
        super.onDetachedFromWindow();
    }
}
