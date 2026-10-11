package com.simomusic.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;

import androidx.appcompat.widget.AppCompatSeekBar;

public final class WaveSeekBar extends AppCompatSeekBar {
    private final WavePainter wp;
    private int fill = 0xFFFFFFFF;

    public WaveSeekBar(Context c) {
        super(c);
        wp = new WavePainter(c, 3, 26, 4, 4, 24);
        setBackground(null);
        setThumb(null);
        setSplitTrack(false);
        wp.colors(fill, (fill & 0x00FFFFFF) | 0x47000000);
    }

    public void setWaving(boolean on) {
        if (wp == null) return;
        wp.waving(on);
        invalidate();
    }

    @Override
    public void setProgressTintList(ColorStateList tint) {
        if (tint != null && wp != null) {
            fill = tint.getDefaultColor();
            wp.colors(fill, (fill & 0x00FFFFFF) | 0x47000000);
            invalidate();
        }
    }

    public void setDragging(boolean on) {
        if (wp == null) return;
        wp.dragging(on);
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (wp == null) return;
        wp.draw(canvas, this, getProgress(), getMax());
    }
}
