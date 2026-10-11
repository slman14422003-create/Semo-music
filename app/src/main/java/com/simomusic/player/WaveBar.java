package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.ProgressBar;

public final class WaveBar extends ProgressBar {
    private final WavePainter wp;

    public WaveBar(Context c) {
        super(c, null, android.R.attr.progressBarStyleHorizontal);
        wp = new WavePainter(c, 2, 20, 3, 3, 12);
        wp.colors(Ui.color(c, R.color.accent), Ui.color(c, R.color.surface_high));
    }

    public void setWaving(boolean on) {
        wp.waving(on);
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        wp.draw(canvas, this, getProgress(), getMax());
    }
}
