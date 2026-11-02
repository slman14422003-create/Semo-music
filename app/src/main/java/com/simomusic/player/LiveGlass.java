package com.simomusic.player;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

public final class LiveGlass extends Drawable implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private final View owner;
    private final View source;
    private final float radius;
    private final float stroke;
    private final int ds;
    private final int blurR;
    private final int bgColor;
    private final int hi;
    private final int lo;
    private final int sheenTop;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint();
    private final Paint solidPaint = new Paint();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint();
    private final RectF rect = new RectF();
    private final int[] a = new int[2];
    private final int[] b = new int[2];
    private Bitmap bmp;
    private Canvas cv;
    private int[] px;
    private int tint;
    private int alpha = 255;
    private long last;
    private boolean paused;
    private boolean failed;
    private ViewTreeObserver vto;

    public LiveGlass(Context c, View owner, View source, int radiusDp, int tint) {
        this.owner = owner;
        this.source = source;
        this.tint = tint;
        float d = c.getResources().getDisplayMetrics().density;
        radius = radiusDp * d;
        stroke = Math.max(1f, d);
        ds = Math.max(4, Math.round(d * 2.6f));
        blurR = 4;
        bgColor = Ui.color(c, R.color.bg);
        hi = Ui.color(c, R.color.glass_hi);
        lo = Ui.color(c, R.color.glass_lo);
        sheenTop = Ui.color(c, R.color.glass_sheen);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.55f);
        bmpPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
        solidPaint.setColor(ColorUtils.compositeColors(tint, bgColor));
        owner.addOnAttachStateChangeListener(this);
        if (owner.isAttachedToWindow()) onViewAttachedToWindow(owner);
    }

    public void setTint(int color) {
        tint = color;
        solidPaint.setColor(ColorUtils.compositeColors(color, bgColor));
        invalidateSelf();
    }

    public void setPaused(boolean p) {
        paused = p;
        if (!p) last = 0;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View v) {
        vto = owner.getViewTreeObserver();
        if (vto.isAlive()) vto.addOnPreDrawListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View v) {
        if (vto != null && vto.isAlive()) vto.removeOnPreDrawListener(this);
        vto = null;
    }

    @Override
    public boolean onPreDraw() {
        update();
        return true;
    }

    private void update() {
        if (failed || paused || Gest.busy) return;
        if (!owner.isShown() || owner.getAlpha() < 0.02f) return;
        if (source.getLayerType() != View.LAYER_TYPE_NONE) return;
        int w = owner.getWidth();
        int h = owner.getHeight();
        if (w <= 0 || h <= 0 || source.getWidth() <= 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - last < (now < Glass.hotUntil ? 8 : 50)) return;
        last = now;
        try {
            int bw = Math.max(2, (w + ds - 1) / ds);
            int bh = Math.max(2, (h + ds - 1) / ds);
            if (bmp == null || bmp.getWidth() != bw || bmp.getHeight() != bh) {
                bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                cv = new Canvas(bmp);
                px = new int[bw * bh];
            }
            owner.getLocationInWindow(a);
            source.getLocationInWindow(b);
            cv.save();
            cv.scale(1f / ds, 1f / ds);
            cv.drawColor(bgColor);
            cv.translate(-(a[0] - b[0]), -(a[1] - b[1]));
            source.draw(cv);
            cv.restore();
            bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
            StackBlur.blur(px, bw, bh, blurR);
            bmp.setPixels(px, 0, bw, 0, 0, bw, bh);
        } catch (RuntimeException | OutOfMemoryError e) {
            failed = true;
            bmp = null;
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect r) {
        strokePaint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, hi, lo, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.55f, sheenTop, 0, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        if (r.isEmpty()) return;
        if (bmp != null && !failed) {
            rect.set(r);
            bmpPaint.setAlpha(alpha);
            canvas.drawBitmap(bmp, null, rect, bmpPaint);
            tintPaint.setColor(tint);
            tintPaint.setAlpha(tintPaint.getAlpha() * alpha / 255);
            canvas.drawRect(r, tintPaint);
        } else {
            solidPaint.setAlpha(alpha);
            canvas.drawRect(r, solidPaint);
        }
        canvas.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.55f, sheenPaint);
        float in = stroke / 2f;
        rect.set(r.left + in, r.top + in, r.right - in, r.bottom - in);
        float rr = Math.max(0f, radius - in);
        canvas.drawRoundRect(rect, rr, rr, strokePaint);
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
