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
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float ampMax, wave, stroke, thumbW, thumbH, gap, edge;
    private float amp, phase, shown = -1f, thumbScale = 1f;
    private long last;
    private boolean waving, dragging;

    WavePainter(Context c, int ampDp, int waveDp, int strokeDp, int thumbWDp, int thumbHDp) {
        ampMax = Ui.dp(c, ampDp);
        wave = Ui.dp(c, waveDp);
        stroke = Ui.dp(c, strokeDp);
        thumbW = Ui.dp(c, thumbWDp);
        thumbH = Ui.dp(c, thumbHDp);
        gap = thumbW * 2.2f;
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
        float h = thumbH * thumbScale;
        r.set(px - thumbW / 2f, cy - h / 2f, px + thumbW / 2f, cy + h / 2f);
        cv.drawRoundRect(r, thumbW / 2f, thumbW / 2f, thumb);

        boolean moving = Math.abs(target - shown) > 0.0004f || amp > 0.02f || waving
                || Math.abs(thumbScale - (dragging ? 1.3f : 1f)) > 0.01f;
        if (moving) v.postInvalidateOnAnimation();
        else last = 0;
    }
}
