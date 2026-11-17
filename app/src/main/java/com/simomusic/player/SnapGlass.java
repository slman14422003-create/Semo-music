package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

public final class SnapGlass extends Drawable {
    private final Glass.Snap snap;
    private final float radius;
    private final float stroke;
    private final boolean card;
    private final int hi;
    private final int lo;
    private final int sheenTop;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint();
    private final Paint solidPaint = new Paint();
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final int[] loc = new int[2];

    public SnapGlass(Context c, Glass.Snap snap, int radiusPx, boolean card) {
        this.snap = snap;
        this.radius = radiusPx;
        this.card = card;
        stroke = Math.max(1f, c.getResources().getDisplayMetrics().density);
        hi = Ui.color(c, R.color.glass_hi);
        lo = Ui.color(c, R.color.glass_lo);
        sheenTop = Ui.color(c, R.color.glass_sheen);
        tintPaint.setColor(Ui.color(c, R.color.glass_snap_tint));
        solidPaint.setColor(Ui.color(c, R.color.glass_sheet));
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.5f);
        bmpPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
    }

    @Override
    protected void onBoundsChange(Rect r) {
        strokePaint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, hi, lo, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, r.top, 0, r.top + Math.min(r.height() * 0.5f, radius * 3f), sheenTop, 0, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        if (r.isEmpty()) return;
        boolean drawn = false;
        Callback cb = getCallback();
        if (Glass.OFF) solidPaint.setAlpha(255);
        if (!Glass.OFF && snap != null && snap.bmp != null && cb instanceof View) {
            ((View) cb).getLocationOnScreen(loc);
            float fx = snap.bmp.getWidth() / (float) snap.w;
            float fy = snap.bmp.getHeight() / (float) snap.h;
            src.set(Math.round(loc[0] * fx), Math.round(loc[1] * fy),
                    Math.round((loc[0] + r.width()) * fx), Math.round((loc[1] + r.height()) * fy));
            if (src.intersect(0, 0, snap.bmp.getWidth(), snap.bmp.getHeight()) && src.width() > 0 && src.height() > 0) {
                rect.set(r);
                canvas.drawBitmap(snap.bmp, src, rect, bmpPaint);
                canvas.drawRect(r, tintPaint);
                drawn = true;
            }
        }
        if (!drawn) canvas.drawRect(r, solidPaint);
        canvas.drawRect(r.left, r.top, r.right, r.top + Math.min(r.height() * 0.5f, radius * 3f), sheenPaint);
        float in = stroke / 2f;
        rect.set(r.left + in, r.top + in, r.right - in, r.bottom + (card ? -in : radius));
        float rr = Math.max(0f, radius - in);
        canvas.drawRoundRect(rect, rr, rr, strokePaint);
    }

    @Override
    public void setAlpha(int a) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
