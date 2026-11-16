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

    /** One UI media-card style: thick rounded track, round ring thumb with a soft glow, a small drifting wave over the played part. */
    private void drawFlat(Canvas cv, View v, float shown, float dt) {
        float h = stroke * 1.9f;
        float rad = h * 0.95f * (dragging ? 1.15f : 1f);
        float left = v.getPaddingLeft() + h / 2f;
        float right = v.getWidth() - v.getPaddingRight() - h / 2f;
        float cy = v.getHeight() / 2f;
        float px = left + (right - left) * shown;
        played.setStrokeWidth(h);
        rest.setStrokeWidth(h);
        rest.setColor(0x66808890);
        if (right > px + rad * 0.6f) cv.drawLine(px, cy, right, cy, rest);
        if (px > left) cv.drawLine(left, cy, px, cy, played);
        else cv.drawPoint(left, cy, played);
        // soft wave above the played part (only while playing)
        if (amp > 0.05f && px - left > Ui.dp(v.getContext(), 24)) {
            float span = Math.min(px - left, Ui.dp(v.getContext(), 150));
            float x0 = px - span;
            float k = 6.2831855f / (span * 0.9f);
            for (int layer = 0; layer < 2; layer++) {
                humpPath.rewind();
                humpPath.moveTo(x0, cy - h / 2f);
                for (float x = x0; x <= px; x += 3f) {
                    float t = (x - x0) / span;
                    float env = (float) Math.sin(Math.PI * t);
                    float y = cy - h / 2f - amp * (layer == 0 ? 1.25f : 0.8f) * env
                            * (0.55f + 0.45f * (float) Math.sin((x - x0) * k * (layer + 1) - phase * (layer + 1)));
                    humpPath.lineTo(x, y);
                }
                humpPath.lineTo(px, cy - h / 2f);
                humpPath.close();
                hump.setColor(played.getColor());
                hump.setAlpha(layer == 0 ? 90 : 55);
                cv.drawPath(humpPath, hump);
            }
        }
        // thumb: glow, white ring, accent core
        glow.setColor(played.getColor());
        glow.setAlpha(dragging ? 80 : 40);
        cv.drawCircle(px, cy, rad * 1.45f, glow);
        boolean light = androidx.core.graphics.ColorUtils.calculateLuminance(played.getColor()) > 0.7;
        ring.setColor(light ? played.getColor() : 0xFFFFFFFF);
        cv.drawCircle(px, cy, rad, ring);
        cv.drawCircle(px, cy, rad - Math.max(Ui.dp(v.getContext(), 2), rad * 0.3f), light ? inner : thumb);
    }

    private int idleMs = 33;

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
        amp += (goal - amp) * Math.min(1f, dt / 160f);
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
                if (easingFlat || dragging) v.postInvalidateOnAnimation();
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
