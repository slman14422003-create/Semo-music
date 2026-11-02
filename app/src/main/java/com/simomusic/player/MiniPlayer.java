package com.simomusic.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewGroup;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

public final class MiniPlayer extends LinearLayout implements Player.Listener {
    private static final LruCache<String, Integer> TINTS = new LruCache<>(64);

    private final ImageView art;
    private final TextView title, artist;
    private final RingPlay play;
    private final LinearLayout col;
    private final LiveGlass glass;
    private final ImageButton next;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable gone = this::refresh;
    private final int baseTint;
    private final Runnable collapseRun = () -> setExpanded(false, true);
    private ValueAnimator tintAnim;
    private ValueAnimator exAnim;
    private int tintNow;
    private boolean expanded;
    private float ex;
    private int fullW;
    private Runnable shownCb;
    private String lastArt = "\u0000", lastId = "";
    private CharSequence lastTitle, lastArtist;
    private int lastIcon, lastIndex = -1;
    private boolean lastPlaying, started, ticking, longFired;
    private PlayerPanel panel;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            longFired = true;
            setPressed(false);
            Ui.gestureStart(MiniPlayer.this);
            Menus.queue(getContext());
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            sync();
            schedule();
        }
    };

    public void setPanel(PlayerPanel p) {
        panel = p;
    }

    private void sync() {
        MediaController m = Pb.get();
        if (m == null) return;
        long d = m.getDuration();
        play.sync(m.getCurrentPosition(), d == C.TIME_UNSET || d < 0 ? 0 : d,
                m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED, m.getPlaybackParameters().speed);
    }

    private void schedule() {
        MediaController m = Pb.get();
        if (started && !ticking && m != null && m.isPlaying() && getVisibility() == VISIBLE) {
            ticking = true;
            h.postDelayed(tick, 1000);
        }
    }

    public MiniPlayer(final Context c, View source) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        glass = Glass.live(this, source, 28);
        baseTint = Ui.color(c, R.color.glass_nav_tint);
        tintNow = baseTint;
        setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 90));
        setLayoutParams(lp);
        setVisibility(GONE);
        Ui.press(c, this);

        art = Ui.artView(c, 48, 16);
        addView(art);

        col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        title = Ui.text(c, "", 15, R.color.text_primary);
        title.setTypeface(Ui.titleFace(true));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMarqueeRepeatLimit(-1);
        title.setHorizontalFadingEdgeEnabled(true);
        title.setFadingEdgeLength(Ui.dp(c, 14));
        artist = Ui.text(c, "", 12, R.color.text_secondary);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        artist.setPadding(0, Ui.dp(c, 2), 0, 0);
        col.addView(title);
        col.addView(artist);
        LinearLayout.LayoutParams cp = Ui.weight(1);
        cp.setMarginStart(Ui.dp(c, 10));
        cp.setMarginEnd(Ui.dp(c, 4));
        addView(col, cp);

        play = new RingPlay(c);
        play.setOnClickListener(v -> {
            if (!expanded) {
                setExpanded(true, true);
                return;
            }
            scheduleCollapse();
            Ui.confirm(v);
            MediaController m = Pb.get();
            if (m == null) return;
            if (m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED) m.pause();
            else m.play();
        });
        addView(play, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        next = Ui.flat(c, R.drawable.ic_next, 40, R.string.next, v -> {
            scheduleCollapse();
            MediaController m = Pb.get();
            if (m != null) m.seekToNext();
        });
        addView(next);
        applyEx(0f);

        setClickable(true);
        setContentDescription(c.getString(R.string.now_playing));
        final int slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setOnTouchListener(new OnTouchListener() {
            float x0, y0, base;
            boolean vertical, horizontal, down, moved, armed;
            VelocityTracker vt;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        animate().cancel();
                        setPressed(true);
                        if (expanded) scheduleCollapse();
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        vertical = horizontal = down = moved = armed = false;
                        longFired = false;
                        h.removeCallbacks(longPress);
                        h.postDelayed(longPress, 450);
                        if (vt != null) vt.recycle();
                        vt = VelocityTracker.obtain();
                        Gest.feed(vt, e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (vt == null || longFired) return true;
                        Gest.feed(vt, e);
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            moved = true;
                            h.removeCallbacks(longPress);
                            setPressed(false);
                        }
                        if (!vertical && !horizontal && !down) {
                            if (panel != null && Gest.up(dx, dy, slop)) {
                                vertical = true;
                                base = dy;
                                getParent().requestDisallowInterceptTouchEvent(true);
                                panel.setCollapsedY(getTop());
                                panel.beginDrag();
                            } else if (expanded && Gest.horizontal(dx, dy, slop)) {
                                horizontal = true;
                                getParent().requestDisallowInterceptTouchEvent(true);
                                Ui.gestureStart(MiniPlayer.this);
                                lift(true);
                            } else if (expanded && Gest.down(dx, dy, slop)) {
                                down = true;
                                getParent().requestDisallowInterceptTouchEvent(true);
                                lift(true);
                            }
                        }
                        if (vertical) {
                            panel.dragUp(-(dy - base));
                        } else if (horizontal) {
                            float tx = Gest.rubber(dx * 0.75f, Ui.dp(c, 110));
                            setTranslationX(tx);
                            setAlpha(1f - Math.min(0.35f, Math.abs(tx) / Ui.dp(c, 320)));
                            boolean past = Math.abs(dx) > Ui.dp(c, 44);
                            if (past != armed) {
                                armed = past;
                                Ui.tick(MiniPlayer.this);
                            }
                        } else if (down) {
                            setTranslationY(Gest.rubber(Math.max(0f, dy) * 0.6f, Ui.dp(c, 50)));
                            boolean past = dy > Ui.dp(c, 56);
                            if (past != armed) {
                                armed = past;
                                Ui.tick(MiniPlayer.this);
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        h.removeCallbacks(longPress);
                        setPressed(false);
                        if (longFired) {
                            longFired = false;
                            if (vt != null) {
                                vt.recycle();
                                vt = null;
                            }
                            return true;
                        }
                        if (vt == null) return false;
                        Gest.feed(vt, e);
                        vt.computeCurrentVelocity(1000);
                        float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                        vt.recycle();
                        vt = null;
                        boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (vertical) {
                            panel.endDrag(up ? vy : 0f);
                        } else if (horizontal) {
                            boolean go = up && (Math.abs(dx) > Ui.dp(c, 44)
                                    || (Math.abs(vx) > Gest.fling(c, 240) && Math.signum(vx) == Math.signum(dx)));
                            if (go) {
                                MediaController m = Pb.get();
                                boolean ltr = getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
                                if (m != null) {
                                    if ((dx < 0) == ltr) m.seekToNext();
                                    else m.seekToPrevious();
                                }
                                Ui.confirm(MiniPlayer.this);
                            }
                            animate().translationX(0f).alpha(1f).setDuration(Motion.BASE)
                                    .setInterpolator(Motion.POP).withEndAction(() -> lift(false)).start();
                        } else if (down) {
                            boolean go = up && (dy > Ui.dp(c, 56) || vy > Gest.fling(c, 900));
                            if (go) {
                                MediaController m = Pb.get();
                                if (m != null) m.pause();
                                Ui.confirm(MiniPlayer.this);
                            }
                            animate().translationY(0f).setDuration(Motion.BASE)
                                    .setInterpolator(Motion.POP).withEndAction(() -> lift(false)).start();
                        } else if (up && !moved && !expanded) {
                            setExpanded(true, true);
                        } else if (up && !moved && panel != null) {
                            panel.setCollapsedY(getTop());
                            panel.expand(true);
                        } else if (up && !moved) {
                            Ui.go(c, new Intent(c, PlayerActivity.class));
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    public void start() {
        started = true;
        Pb.onDisconnect(gone);
        Pb.add(this);
        Pb.whenReady(this::refresh);
    }

    public void stop() {
        started = false;
        ticking = false;
        h.removeCallbacks(longPress);
        h.removeCallbacks(collapseRun);
        Pb.remove(this);
        Pb.offDisconnect(gone);
        h.removeCallbacks(tick);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setSelected(false);
        lastPlaying = false;
    }

    @Override
    public void onEvents(Player p, Player.Events e) {
        refresh();
    }

    private boolean lifted;

    private void lift(boolean on) {
        if (on == lifted) return;
        lifted = on;
        setLayerType(on ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
        if (Gest.busy != on) {
            Gest.busy = on;
            if (!on) Gest.kick(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (lifted) {
            lifted = false;
            Gest.busy = false;
        }
        super.onDetachedFromWindow();
    }

    private void applyTint(int accent) {
        Context c = getContext();
        int target = baseTint;
        if (accent != 0) {
            int a = (accent & 0x00FFFFFF) | (baseTint & 0xFF000000);
            target = ColorUtils.blendARGB(baseTint, a, 0.34f);
        }
        int main = accent != 0 ? accent : Ui.color(c, R.color.accent);
        int on = accent != 0 ? (ColorUtils.calculateLuminance(accent) > 0.6 ? 0xFF0A0A0F : 0xFFFFFFFF)
                : Ui.color(c, R.color.on_accent);
        play.accent(main, on);
        if (tintAnim != null) tintAnim.cancel();
        if (target == tintNow) return;
        tintAnim = ValueAnimator.ofArgb(tintNow, target);
        tintAnim.setDuration(Motion.AMBIENT);
        tintAnim.addUpdateListener(a -> {
            tintNow = (int) a.getAnimatedValue();
            glass.setTint(tintNow);
        });
        tintAnim.start();
    }

    public LiveGlass glass() {
        return glass;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setOnShown(Runnable r) {
        shownCb = r;
    }

    public void setDock(int bottomMarginPx, int availW) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        fullW = availW;
        if (lp.bottomMargin != bottomMarginPx) {
            lp.bottomMargin = bottomMarginPx;
            setLayoutParams(lp);
        }
        if (ex > 0f) applyEx(ex);
    }

    public void collapse() {
        if (expanded) setExpanded(false, true);
    }

    private void scheduleCollapse() {
        h.removeCallbacks(collapseRun);
        h.postDelayed(collapseRun, 6000);
    }

    public void setExpanded(boolean on, boolean animate) {
        if (on == expanded) return;
        expanded = on;
        h.removeCallbacks(collapseRun);
        if (on) scheduleCollapse();
        if (exAnim != null) exAnim.cancel();
        if (!animate) {
            applyEx(on ? 1f : 0f);
            return;
        }
        if (on) Ui.tick(this);
        exAnim = ValueAnimator.ofFloat(ex, on ? 1f : 0f);
        exAnim.setDuration(on ? Motion.LONG : Motion.BASE);
        exAnim.setInterpolator(Motion.ENTER);
        exAnim.addUpdateListener(a -> applyEx((float) a.getAnimatedValue()));
        exAnim.start();
    }

    private void applyEx(float f) {
        ex = f;
        int small = Ui.dp(getContext(), 56);
        int target = Math.max(fullW, small);
        ViewGroup.LayoutParams lp = getLayoutParams();
        int w = Math.round(small + (target - small) * f);
        if (lp != null && lp.width != w) {
            lp.width = w;
            setLayoutParams(lp);
        }
        float a = Math.max(0f, Math.min(1f, (f - 0.3f) / 0.7f));
        int vis = f <= 0.01f ? GONE : VISIBLE;
        art.setVisibility(vis);
        col.setVisibility(vis);
        next.setVisibility(vis);
        art.setAlpha(a);
        col.setAlpha(a);
        next.setAlpha(a);
    }

    private static int dominant(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 24, 24, true);
        int[] px = new int[24 * 24];
        s.getPixels(px, 0, 24, 0, 0, 24, 24);
        if (s != b) s.recycle();
        return ArtColor.dominant(px, px.length);
    }

    private void tintFor(Uri uri, final String key) {
        Integer hit = TINTS.get(key);
        if (hit != null) {
            applyTint(hit);
            return;
        }
        if (uri == null) {
            applyTint(0);
            return;
        }
        Art.fetchUrgent(getContext(), uri, Ui.dp(getContext(), 54), b -> {
            if (!key.equals(lastArt)) return;
            if (b == null) {
                TINTS.put(key, 0);
                applyTint(0);
                return;
            }
            Art.FX.execute(() -> {
                int col = 0;
                try {
                    col = dominant(b);
                } catch (RuntimeException ignored) {
                }
                final int fc = col;
                post(() -> {
                    TINTS.put(key, fc);
                    if (key.equals(lastArt)) applyTint(fc);
                });
            });
        });
    }

    private void swapFeel(int dir) {
        float d = dir * Ui.dp(getContext(), 22) * (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1f : 1f);
        col.animate().cancel();
        col.setAlpha(0f);
        col.setTranslationX(d);
        col.animate().alpha(1f).translationX(0f).setDuration(Motion.BASE).setInterpolator(Motion.ENTER).withLayer().start();
        art.animate().cancel();
        art.setScaleX(0.86f);
        art.setScaleY(0.86f);
        art.animate().scaleX(1f).scaleY(1f).setDuration(Motion.BASE).setInterpolator(Motion.POP).withLayer().start();
    }

    private void refresh() {
        MediaController m = Pb.get();
        if (m == null || m.getMediaItemCount() == 0) {
            ticking = false;
            h.removeCallbacks(tick);
            setVisibility(GONE);
            lastIndex = -1;
            if (shownCb != null) shownCb.run();
            return;
        }
        boolean firstShow = getVisibility() != VISIBLE;
        if (firstShow) {
            setVisibility(VISIBLE);
            if (shownCb != null) shownCb.run();
            setAlpha(0f);
            setTranslationY(Ui.dp(getContext(), 24));
            animate().alpha(1f).translationY(0f).setDuration(Motion.LONG)
                    .setInterpolator(Motion.SPRING).start();
        }
        MediaItem it = m.getCurrentMediaItem();
        if (it == null) return;
        int index = m.getCurrentMediaItemIndex();
        if (!it.mediaId.equals(lastId)) {
            boolean animate = !firstShow && lastIndex >= 0;
            lastId = it.mediaId;
            if (animate) swapFeel(index >= lastIndex ? 1 : -1);
        }
        lastIndex = index;
        CharSequence tt = it.mediaMetadata.title;
        if (lastTitle == null || !lastTitle.toString().contentEquals(tt == null ? "" : tt)) {
            lastTitle = tt == null ? "" : tt;
            title.setText(tt);
        }
        String ar = it.mediaMetadata.artist == null ? null : it.mediaMetadata.artist.toString();
        CharSequence shownArtist = Fmt.artist(getContext(), ar);
        if (lastArtist == null || !lastArtist.toString().contentEquals(shownArtist)) {
            lastArtist = shownArtist;
            artist.setText(shownArtist);
        }
        boolean playing = m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED;
        if (playing != lastPlaying) {
            lastPlaying = playing;
            title.setEllipsize(playing ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
            title.setSelected(playing);
        }
        int icon = playing ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill;
        if (icon != lastIcon) {
            play.setIcon(icon, lastIcon != 0 && !firstShow);
            lastIcon = icon;
        }
        sync();
        schedule();
        String key = String.valueOf(it.mediaMetadata.artworkUri);
        if (!key.equals(lastArt)) {
            lastArt = key;
            Art.load(getContext(), it.mediaMetadata.artworkUri, art, Ui.dp(getContext(), 54), true);
            final Uri artUri = it.mediaMetadata.artworkUri;
            h.postDelayed(() -> {
                if (!started || !key.equals(lastArt)) return;
                tintFor(artUri, key);
                Prefetch.upcoming(getContext(), Pb.get(), Ui.dp(getContext(), 54), Ui.dp(getContext(), 320));
            }, 160);
        }
    }
}
