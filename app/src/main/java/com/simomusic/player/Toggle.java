package com.simomusic.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.CompoundButton;

public final class Toggle extends CompoundButton {
    private static final int ON = 0xFF34C759;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int offTrack, w, h, pad, thumb;
    private float pos;
    private ValueAnimator anim;

    public Toggle(Context c) {
        super(c);
        offTrack = Ui.color(c, R.color.switch_off);
        w = Ui.dp(c, 51);
        h = Ui.dp(c, 31);
        pad = Ui.dp(c, 2);
        thumb = h - pad * 2;
        setBackground(null);
        setButtonDrawable(null);
        setClickable(false);
        setFocusable(false);
        setLayerType(View.LAYER_TYPE_NONE, null);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(w, h);
    }

    @Override
    public void setChecked(boolean checked) {
        boolean was = isChecked();
        super.setChecked(checked);
        float to = checked ? 1f : 0f;
        if (anim != null) anim.cancel();
        if (was == checked || !isLaidOut() || getWindowVisibility() != View.VISIBLE) {
            pos = to;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, to);
        anim.setDuration(Motion.SHORT + 40);
        anim.setInterpolator(Motion.ENTER);
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pos = isChecked() ? 1f : 0f;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(offTrack, ON, pos));
        float r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);
        float cx = pad + thumb / 2f + (w - pad * 2 - thumb) * pos;
        float cy = h / 2f;
        paint.setColor(0x33000000);
        canvas.drawCircle(cx, cy + Ui.dp(getContext(), 1), thumb / 2f + getResources().getDisplayMetrics().density * 0.5f, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, thumb / 2f, paint);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
