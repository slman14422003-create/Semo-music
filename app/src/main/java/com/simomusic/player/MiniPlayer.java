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
import android.view.View;
import android.view.ViewConfiguration;
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
    private final GradientDrawable card;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable gone = this::refresh;
    private final int baseColor;
    private ValueAnimator tintAnim;
    private int cardColor;
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

    public MiniPlayer(final Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        baseColor = Ui.color(c, R.color.surface_high);
        cardColor = baseColor;
        card = new GradientDrawable();
        card.setCornerRadius(Ui.dp(c, 30));
        card.setColor(baseColor);
        card.setStroke(Ui.dp(c, 1), Ui.color(c, R.color.stroke_soft));
        setBackground(card);
        Ui.round(this, Ui.dp(c, 30));
        setElevation(Ui.dp(c, 6));
        setPaddingRelative(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 10));
        setLayoutParams(lp);
        setVisibility(GONE);
        Ui.press(c, this);

        art = Ui.artView(c, 54, 18);
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
        cp.setMarginStart(Ui.dp(c, 12));
        cp.setMarginEnd(Ui.dp(c, 6));
        addView(col, cp);

        play = new RingPlay(c);
        play.setOnClickListener(v -> {
            Ui.confirm(v);
            MediaController m = Pb.get();
            if (m == null) return;
            if (m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED) m.pause();
            else m.play();
        });
        addView(play, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
        ImageButton next = Ui.flat(c, R.drawable.ic_next, 44, R.string.next, v -> {
            MediaController m = Pb.get();
            if (m != null) m.seekToNext();
        });
        addView(next);

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
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        vertical = horizontal = down = moved = armed = false;
                        longFired = false;
                        h.removeCallbacks(longPress);
                        h.postDelayed(longPress, 450);
                        if (vt != null) vt.recycle();
                        vt = VelocityTracker.obtain();
                        vt.addMovement(e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (vt == null || longFired) return true;
                        vt.addMovement(e);
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
                            } else if (Gest.horizontal(dx, dy, slop)) {
                                horizontal = true;
                                getParent().requestDisallowInterceptTouchEvent(true);
                                Ui.gestureStart(MiniPlayer.this);
                            } else if (Gest.down(dx, dy, slop)) {
                                down = true;
                                getParent().requestDisallowInterceptTouchEvent(true);
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
                        vt.addMovement(e);
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
                                    .setInterpolator(Motion.POP).start();
                        } else if (down) {
                            boolean go = up && (dy > Ui.dp(c, 56) || vy > Gest.fling(c, 900));
                            if (go) {
                                MediaController m = Pb.get();
                                if (m != null) m.pause();
                                Ui.confirm(MiniPlayer.this);
                            }
                            animate().translationY(0f).setDuration(Motion.BASE)
                                    .setInterpolator(Motion.POP).start();
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

    private void applyTint(int accent) {
        Context c = getContext();
        int target = accent != 0 ? ColorUtils.blendARGB(baseColor, accent, 0.24f) : baseColor;
        int main = accent != 0 ? accent : Ui.color(c, R.color.accent);
        int on = accent != 0 ? (ColorUtils.calculateLuminance(accent) > 0.6 ? 0xFF0A0A0F : 0xFFFFFFFF)
                : Ui.color(c, R.color.on_accent);
        play.accent(main, on);
        if (tintAnim != null) tintAnim.cancel();
        if (target == cardColor) return;
        tintAnim = ValueAnimator.ofArgb(cardColor, target);
        tintAnim.setDuration(Motion.AMBIENT);
        tintAnim.addUpdateListener(a -> {
            cardColor = (int) a.getAnimatedValue();
            card.setColor(cardColor);
        });
        tintAnim.start();
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
        Art.fetch(getContext(), uri, Ui.dp(getContext(), 54), b -> {
            if (!key.equals(lastArt)) return;
            int col = 0;
            if (b != null) {
                try {
                    col = dominant(b);
                } catch (RuntimeException ignored) {
                }
            }
            TINTS.put(key, col);
            applyTint(col);
        });
    }

    private void swapFeel(int dir) {
        float d = dir * Ui.dp(getContext(), 22) * (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1f : 1f);
        col.animate().cancel();
        col.setAlpha(0f);
        col.setTranslationX(d);
        col.animate().alpha(1f).translationX(0f).setDuration(Motion.BASE).setInterpolator(Motion.ENTER).start();
        art.animate().cancel();
        art.setScaleX(0.86f);
        art.setScaleY(0.86f);
        art.animate().scaleX(1f).scaleY(1f).setDuration(Motion.BASE).setInterpolator(Motion.POP).start();
    }

    private void refresh() {
        MediaController m = Pb.get();
        if (m == null || m.getMediaItemCount() == 0) {
            ticking = false;
            h.removeCallbacks(tick);
            setVisibility(GONE);
            lastIndex = -1;
            return;
        }
        boolean firstShow = getVisibility() != VISIBLE;
        if (firstShow) {
            setVisibility(VISIBLE);
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
            Art.load(getContext(), it.mediaMetadata.artworkUri, art, Ui.dp(getContext(), 54));
            tintFor(it.mediaMetadata.artworkUri, key);
            Prefetch.upcoming(getContext(), m, Ui.dp(getContext(), 54), Ui.dp(getContext(), 54));
        }
    }
}
