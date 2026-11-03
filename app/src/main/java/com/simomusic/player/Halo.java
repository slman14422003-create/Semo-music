package com.simomusic.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** A colored glow behind the cover that breathes while the music plays. */
public final class Halo extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;
    private boolean playing;
    private boolean shown;
    private ValueAnimator pulse;

    public Halo(Context c) {
        super(c);
        setClickable(false);
        setAlpha(0f);
    }

    public void setColor(int col) {
        color = col;
        rebuild();
        update();
        invalidate();
    }

    public void setPlaying(boolean on) {
        playing = on;
        update();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuild();
    }

    private void rebuild() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || color == 0) {
            p.setShader(null);
            return;
        }
        int rgb = color & 0x00FFFFFF;
        p.setShader(new RadialGradient(w / 2f, h / 2f, Math.min(w, h) / 2f,
                new int[]{0xB0000000 | rgb, 0x70000000 | rgb, rgb}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
    }

    private void update() {
        boolean run = playing && shown && color != 0 && !PowerSaver.on(getContext());
        if (!run) {
            if (pulse != null) {
                pulse.cancel();
                pulse = null;
            }
            setScaleX(1f);
            setScaleY(1f);
            setAlpha(color == 0 ? 0f : 0.8f);
            return;
        }
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2600);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            float s = 0.95f + 0.10f * v;
            setScaleX(s);
            setScaleY(s);
            setAlpha(0.6f + 0.4f * v);
        });
        pulse.start();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        shown = visible;
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        shown = false;
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (p.getShader() != null) c.drawRect(0, 0, getWidth(), getHeight(), p);
    }
}
