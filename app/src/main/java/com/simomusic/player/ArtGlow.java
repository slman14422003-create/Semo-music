package com.simomusic.player;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** A soft glow in the colour of the current cover, behind the artwork (cross-fades between songs). */
public final class ArtGlow extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int shown = 0xFFFFFFFF;
    private ValueAnimator va;

    public ArtGlow(Context c) {
        super(c);
        setAlpha(0f);
        setClickable(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** color 0 = no glow (grey / missing cover). */
    public void setColor(final int color) {
        if (va != null) va.cancel();
        animate().cancel();
        if (color == 0) {
            animate().alpha(0f).setDuration(Motion.SHORT).start();
            return;
        }
        final Runnable apply = () -> {
            shown = color;
            p.setColorFilter(new PorterDuffColorFilter(shown, PorterDuff.Mode.SRC_IN));
            invalidate();
            animate().alpha(1f).setDuration(Motion.BASE).start();
        };
        if (getAlpha() > 0.02f && shown != color) animate().alpha(0f).setDuration(Motion.FAST).withEndAction(apply).start();
        else apply.run();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        p.setShader(new RadialGradient(w / 2f, h * 0.30f, Math.max(w, h) * 0.85f,
                new int[]{0x7AFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        p.setColorFilter(new PorterDuffColorFilter(shown, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), p);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (va != null) va.cancel();
        super.onDetachedFromWindow();
    }
}
