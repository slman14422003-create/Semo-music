package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AnimationUtils;

final class WavePainter {
    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float ampMax, wave, stroke, thumbW, thumbH, gap, edge;
    private float amp, phase, shown = -1f, thumbScale = 1f;
    private long last;
    private boolean waving, dragging, flat;

    WavePainter(Context c, int ampDp, int waveDp, int strokeDp, int thumbWDp, int thumbHDp) {
        ampMax = Ui.dp(c, ampDp);
        wave = Ui.dp(c, waveDp);
        stroke = Ui.dp(c, strokeDp);
        thumbW = Ui.dp(c, thumbWDp);
        thumbH = Ui.dp(c, thumbHDp);
        gap = thumbH * 0.5f + Ui.dp(c, 3);
        edge = Ui.dp(c, 10);
        played.setStyle(Paint.Style.STROKE);
        played.setStrokeCap(Paint.Cap.ROUND);
        played.setStrokeJoin(Paint.Join.ROUND);
        played.setStrokeWidth(stroke);
        rest.setStyle(Paint.Style.STROKE);
        rest.setStrokeCap(Paint.Cap.ROUND);
        rest.setStrokeWidth(stroke);
    }

    void colors(int fill, int track) {
        played.setColor(fill);
        thumb.setColor(fill);
        rest.setColor(track);
        inner.setColor(androidx.core.graphics.ColorUtils.calculateLuminance(fill) > 0.7 ? 0xFF101014 : 0xFFFFFFFF);
    }

    /** One UI style: a thick rounded track with a slim vertical handle instead of the wave. */
    void flat(boolean on) {
        flat = on;
    }

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hump = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path humpPath = new Path();

    private void drawFlat(Canvas cv, View v, float shown, float dt) {
        Context c = v.getContext();
        float sw = Math.max(Ui.dp(c, 4), stroke);
        float pillW = Ui.dp(c, dragging ? 3 : 5);
        float pillH = Ui.dp(c, 26);
        float gapPx = Ui.dp(c, 6);
        float left = v.getPaddingLeft() + sw / 2f;
        float right = v.getWidth() - v.getPaddingRight() - sw / 2f;
        float cy = v.getHeight() / 2f;
        float px = left + (right - left) * shown;
        boolean night = (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        rest.setStrokeWidth(sw);
        rest.setColor(night ? 0x59FFFFFF : 0x33000000);
        float tStart = px + gapPx + pillW / 2f;
        if (right > tStart) cv.drawLine(tStart, cy, right, cy, rest);
        played.setStrokeWidth(sw);
        float a = amp * 1.0f;
        float len = Ui.dp(c, 24);
        float shift = (AnimationUtils.currentAnimationTimeMillis() % 100000L) / 1000f * Ui.dp(c, 18);
        float end = Math.max(left, px - gapPx - pillW / 2f);
        path.rewind();
        if (a < 0.3f || end - left < 1f) {
            path.moveTo(left, cy);
            path.lineTo(Math.max(end, left + 0.1f), cy);
        } else {
            float step = Math.max(1.5f, Ui.dp(c, 1));
            path.moveTo(left, cy + a * (float) Math.sin(shift / len * 6.2831855f));
            for (float x = left + step; x < end; x += step) {
                path.lineTo(x, cy + a * (float) Math.sin((x - left + shift) / len * 6.2831855f));
            }
            path.lineTo(end, cy + a * (float) Math.sin((end - left + shift) / len * 6.2831855f));
        }
        cv.drawPath(path, played);
        ring.setColor(0xFFFFFFFF);
        float rr = pillW / 2f;
        r.set(px - rr, cy - pillH / 2f, px + rr, cy + pillH / 2f);
        cv.drawRoundRect(r, rr, rr, ring);
    }

    private final Paint thumbAccent = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint thumbAccent(int color) {
        thumbAccent.setColor(color);
        return thumbAccent;
    }

    private int idleMs = 16;

    void idleMs(int ms) {
        idleMs = Math.max(16, Math.min(100, ms));
    }

    void waving(boolean on) {
        waving = on;
    }

    void dragging(boolean on) {
        dragging = on;
    }

    void draw(Canvas cv, View v, int progress, int max) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        float dt = last == 0 ? 16f : Math.min(50f, now - last);
        last = now;
        float target = max > 0 ? Math.min(1f, progress / (float) max) : 0f;
        if (shown < 0f || dragging) shown = target;
        else shown += (target - shown) * Math.min(1f, dt / 140f);
        float goal = waving && !dragging ? ampMax : 0f;
        amp += (goal - amp) * Math.min(1f, dt / 110f);
        if (Math.abs(goal - amp) < 0.02f) amp = goal;
        phase += dt * 0.0055f;
        if (phase > 6.2831855f) phase -= 6.2831855f;
        thumbScale += ((dragging ? 1.3f : 1f) - thumbScale) * Math.min(1f, dt / 100f);

        if (flat) {
            drawFlat(cv, v, shown, dt);
            boolean easingFlat = Math.abs(target - shown) > 0.0004f || Math.abs(thumbScale - (dragging ? 1.3f : 1f)) > 0.01f
                    || Math.abs(goal - amp) > 0.02f;
            boolean busy = easingFlat || amp > 0.02f || waving;
            if (busy && !Gest.busy) {
                if (easingFlat || dragging || idleMs <= 33) v.postInvalidateOnAnimation();
                else v.postInvalidateDelayed(idleMs);
            } else last = 0;
            return;
        }
        float left = v.getPaddingLeft() + stroke / 2f;
        float right = v.getWidth() - v.getPaddingRight() - stroke / 2f;
        float cy = v.getHeight() / 2f;
        float px = left + (right - left) * shown;

        if (right - px > gap) {
            cv.drawLine(px + gap, cy, right, cy, rest);
        }
        float end = Math.max(left, px - gap * 0.6f);
        path.rewind();
        path.moveTo(left, cy);
        if (amp < 0.05f) {
            path.lineTo(end, cy);
        } else {
            float k = 6.2831855f / wave;
            for (float x = left; x <= end; x += 3f) {
                float in = Math.min(1f, (x - left) / edge);
                float out = Math.min(1f, (end - x) / edge);
                float env = Math.min(in, out);
                env = env * env * (3f - 2f * env);
                path.lineTo(x, cy + amp * env * (float) Math.sin((x - left) * k - phase));
            }
            path.lineTo(end, cy);
        }
        cv.drawPath(path, played);
        float rad = thumbH * 0.5f * thumbScale;
        cv.drawCircle(px, cy, rad, thumb);
        cv.drawCircle(px, cy, Math.max(1f, rad - Math.max(Ui.dp(v.getContext(), 2), rad * 0.32f)), inner);

        boolean moving = Math.abs(target - shown) > 0.0004f || amp > 0.02f || waving
                || Math.abs(thumbScale - (dragging ? 1.3f : 1f)) > 0.01f;
        boolean easing = Math.abs(target - shown) > 0.0004f || Math.abs(thumbScale - (dragging ? 1.3f : 1f)) > 0.01f
                || Math.abs(goal - amp) > 0.02f;
        if (moving && !Gest.busy) {
            if (easing || dragging) v.postInvalidateOnAnimation();
            else v.postInvalidateDelayed(idleMs);
        } else last = 0;
    }
}
