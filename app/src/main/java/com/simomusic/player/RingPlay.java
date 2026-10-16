package com.simomusic.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

public final class RingPlay extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float stroke;
    private Drawable icon;
    private int iconRes, iconColor, fillColor;
    private long pos, dur, anchor;
    private boolean playing;
    private float speed = 1f, shown = -1f, iconScale = 1f;
    private ValueAnimator pop;

    public RingPlay(Context c) {
        super(c);
        stroke = Ui.dp(c, 3);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setBackgroundResource(R.drawable.bg_ripple_round);
        setClickable(true);
        setContentDescription(c.getString(R.string.play_pause));
        Ui.press(c, this);
        accent(Ui.color(c, R.color.accent), Ui.color(c, R.color.on_accent));
    }

    public void accent(int fillCol, int iconCol) {
        fillColor = fillCol;
        iconColor = iconCol;
        fill.setColor(fillCol);
        arc.setColor(fillCol);
        track.setColor((fillCol & 0x00FFFFFF) | 0x3A000000);
        if (icon != null) icon.setTint(iconCol);
        invalidate();
    }

    public void setIcon(int res, boolean animate) {
        if (res == iconRes) return;
        iconRes = res;
        Drawable d = ContextCompat.getDrawable(getContext(), res);
        if (d == null) return;
        icon = d.mutate();
        icon.setTint(iconColor);
        if (pop != null) pop.cancel();
        if (!animate) {
            iconScale = 1f;
            invalidate();
            return;
        }
        pop = ValueAnimator.ofFloat(0.55f, 1f);
        pop.setDuration(Motion.BASE);
        pop.setInterpolator(new OvershootInterpolator(2.4f));
        pop.addUpdateListener(a -> {
            iconScale = (float) a.getAnimatedValue();
            invalidate();
        });
        pop.start();
    }

    public void sync(long position, long duration, boolean isPlaying, float playbackSpeed) {
        pos = Math.max(0, position);
        dur = duration;
        playing = isPlaying;
        speed = playbackSpeed <= 0 ? 1f : playbackSpeed;
        anchor = SystemClock.elapsedRealtime();
        invalidate();
    }

    private float fraction() {
        if (dur <= 0) return 0f;
        long cur = pos + (playing ? (long) ((SystemClock.elapsedRealtime() - anchor) * speed) : 0L);
        return Math.max(0f, Math.min(1f, cur / (float) dur));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pop != null) pop.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), half = stroke / 2f;
        box.set(half, half, w - half, h - half);
        cv.drawOval(box, track);
        float target = fraction();
        boolean easing = false;
        if (shown < 0f || Math.abs(target - shown) < 0.004f) {
            shown = target;
        } else {
            shown += (target - shown) * 0.3f;
            easing = true;
        }
        if (shown > 0.002f) cv.drawArc(box, -90f, 360f * shown, false, arc);
        cv.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - stroke * 2.2f, fill);
        if (icon != null) {
            float s = Math.min(w, h) * 0.34f;
            int cx = (int) (w / 2f), cy = (int) (h / 2f);
            icon.setBounds((int) (cx - s), (int) (cy - s), (int) (cx + s), (int) (cy + s));
            cv.save();
            cv.scale(iconScale, iconScale, cx, cy);
            icon.draw(cv);
            cv.restore();
        }
        if (playing || easing) postInvalidateOnAnimation();
    }
}
