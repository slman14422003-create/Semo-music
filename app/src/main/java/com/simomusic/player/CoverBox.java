package com.simomusic.player;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Holds the cover and makes it the biggest square that fits the space left on the screen (capped), so the
 * player and the lock screen adapt to any phone: small, tall, punch-hole, foldable, split-screen.
 */
public final class CoverBox extends FrameLayout {
    private final int maxPx;
    private View halo;

    public CoverBox(Context c, int maxDp) {
        super(c);
        maxPx = Ui.dp(c, maxDp);
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** A glow drawn behind the cover, 1.5 times its size, centred on it. */
    public void setHalo(View v) {
        halo = v;
        addView(v, 0, new LayoutParams(-2, -2, Gravity.CENTER));
    }

    public void setCover(View v) {
        v.setLayoutParams(new LayoutParams(-2, -2, Gravity.CENTER));
        addView(v);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        int w = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int h = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int s = Math.max(0, Math.min(Math.min(w, h), maxPx));
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            int cs = ch == halo ? Math.round(s * 1.5f) : s;
            ch.measure(MeasureSpec.makeMeasureSpec(cs, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cs, MeasureSpec.EXACTLY));
        }
    }
}
