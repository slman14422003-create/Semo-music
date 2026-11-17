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
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Choreographer;
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
    private int blurR;
    private boolean sheet;
    private boolean ticking;
    private boolean frozen;
    private boolean recorded;
    private final ColorMatrix sat = new ColorMatrix();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (vto == null) return;
            owner.invalidate();
            owner.postDelayed(this, 400);
        }
    };
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
    private final int offColor;
    private int alpha = 255;
    private long last;
    private boolean paused;
    private boolean failed;
    private ViewTreeObserver vto;
    private boolean gpu;
    private boolean recording;
    private RenderNode node;
    private int nodeW;
    private int nodeH;
    private long cost;
    private boolean looping;
    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long t) {
            if (vto == null || !gpu || !ticking || settled()) {
                looping = false;
                return;
            }
            if (live()) owner.invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public LiveGlass(Context c, View owner, View source, int radiusDp, int tint) {
        this.owner = owner;
        this.source = source;
        this.tint = tint;
        float d = c.getResources().getDisplayMetrics().density;
        radius = radiusDp * d;
        stroke = Math.max(1f, d);
        ds = Math.max(6, Math.round(d * 3.4f));
        blurR = 4;
        bgColor = Ui.color(c, R.color.bg);
        offColor = Ui.color(c, R.color.surface_high);
        hi = Ui.color(c, R.color.glass_hi);
        lo = Ui.color(c, R.color.glass_lo);
        sheenTop = Ui.color(c, R.color.glass_sheen);
        setSaturation(1.55f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
        solidPaint.setColor(ColorUtils.compositeColors(tint, bgColor));
        owner.addOnAttachStateChangeListener(this);
        if (owner.isAttachedToWindow()) onViewAttachedToWindow(owner);
    }

    public void setSaturation(float s) {
        sat.setSaturation(s);
        bmpPaint.setColorFilter(new ColorMatrixColorFilter(sat));
    }

    public void setBlur(int radius) {
        blurR = radius;
    }

    /** Bottom sheet: the rim and the corners are drawn as if the shape continued below the window. */
    public void setSheet(boolean on) {
        sheet = on;
    }

    public void setGpu(boolean on) {
        gpu = on && Build.VERSION.SDK_INT >= 31;
    }

    /** Keeps refreshing while attached (a sheet over a screen that keeps changing). */
    public void setTicking(boolean on) {
        ticking = on;
    }

    public void setFrozen(boolean on) {
        frozen = on;
        if (!on) recorded = false;
    }

    private boolean settled() {
        return frozen && recorded && owner.getWidth() == nodeW && owner.getHeight() == nodeH;
    }

    private void wake() {
        if (frozen && ticking && gpu && !looping && vto != null) {
            looping = true;
            Choreographer.getInstance().postFrameCallback(frame);
        }
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
        if (gpu && ticking) {
            if (!looping) {
                looping = true;
                Choreographer.getInstance().postFrameCallback(frame);
            }
        } else if (ticking) owner.postDelayed(ticker, 400);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View v) {
        if (vto != null && vto.isAlive()) vto.removeOnPreDrawListener(this);
        vto = null;
        owner.removeCallbacks(ticker);
        Choreographer.getInstance().removeFrameCallback(frame);
        looping = false;
        recorded = false;
        if (node != null) {
            node.discardDisplayList();
            nodeW = 0;
        }
    }

    @Override
    public boolean onPreDraw() {
        if (gpu) requestGpuFrame();
        else update();
        return true;
    }

    private boolean live() {
        if (Glass.OFF) return false;
        if (failed || paused || alpha < 8 || Gest.busy) return false;
        if (!owner.isShown() || owner.getAlpha() < 0.02f) return false;
        if (source.getLayerType() != View.LAYER_TYPE_NONE) return false;
        return owner.getWidth() > 0 && owner.getHeight() > 0 && source.getWidth() > 0;
    }

    private void requestGpuFrame() {
        if (settled() || !live()) return;
        long now = SystemClock.uptimeMillis();
        if (now - last < (now < Glass.hotUntil ? 8 : 110)) return;
        last = now;
        owner.invalidate();
    }

    private void recordGpu(Rect r) {
        int w = r.width();
        int h = r.height();
        if (node == null) {
            node = new RenderNode("glass");
            nodeW = 0;
        }
        if (nodeW != w || nodeH != h) {
            nodeW = w;
            nodeH = h;
            node.setPosition(0, 0, w, h);
            float sigma = Math.max(1f, Glass.blur(blurR) * ds * 0.5f);
            RenderEffect blur = RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP);
            node.setRenderEffect(RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(sat), blur));
        }
        owner.getLocationOnScreen(a);
        source.getLocationOnScreen(b);
        Canvas rc = node.beginRecording(w, h);
        try {
            rc.drawColor(bgColor);
            rc.translate(-(a[0] - b[0]), -(a[1] - b[1]));
            recording = true;
            source.draw(rc);
        } finally {
            recording = false;
            node.endRecording();
        }
        recorded = true;
    }

    private void update() {
        if (!live()) return;
        int w = owner.getWidth();
        int h = owner.getHeight();
        long now = SystemClock.uptimeMillis();
        long gap = now < Glass.hotUntil ? 33 : 110;
        if (now - last < Math.max(gap, cost * 6)) return;
        last = now;
        try {
            int bw = Math.max(2, (w + ds - 1) / ds);
            int bh = Math.max(2, (h + ds - 1) / ds);
            if (bmp == null || bmp.getWidth() != bw || bmp.getHeight() != bh) {
                bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                cv = new Canvas(bmp);
                px = new int[bw * bh];
            }
            owner.getLocationOnScreen(a);
            source.getLocationOnScreen(b);
            cv.save();
            cv.scale(1f / ds, 1f / ds);
            cv.drawColor(bgColor);
            cv.translate(-(a[0] - b[0]), -(a[1] - b[1]));
            source.draw(cv);
            cv.restore();
            bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
            StackBlur.blur(px, bw, bh, Glass.blur(blurR));
            bmp.setPixels(px, 0, bw, 0, 0, bw, bh);
        } catch (RuntimeException | OutOfMemoryError e) {
            failed = true;
            bmp = null;
            invalidateSelf();
        }
        cost = Math.min(60L, SystemClock.uptimeMillis() - now);
    }

    @Override
    protected void onBoundsChange(Rect r) {
        strokePaint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, hi, lo, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.55f, sheenTop, 0, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        if (r.isEmpty() || alpha < 4) return;
        if (Glass.OFF) {                       // glass turned off: a plain solid surface, no blur work at all
            solidPaint.setColor(offColor);
            solidPaint.setAlpha(alpha);
            canvas.drawRect(r, solidPaint);
            return;
        }
        solidPaint.setColor(ColorUtils.compositeColors(tint, bgColor));
        strokePaint.setAlpha(alpha);
        sheenPaint.setAlpha(alpha);
        if (gpu && !failed && !recording && canvas.isHardwareAccelerated() && live()) {
            try {
                if (!frozen || !recorded || nodeW != r.width() || nodeH != r.height()) recordGpu(r);
                node.setAlpha(alpha / 255f);
                canvas.drawRenderNode(node);
                tintPaint.setColor(Glass.tint(tint));
                tintPaint.setAlpha(tintPaint.getAlpha() * alpha / 255);
                canvas.drawRect(r, tintPaint);
            } catch (RuntimeException e) {
                failed = true;
                solidPaint.setAlpha(alpha);
                canvas.drawRect(r, solidPaint);
            }
        } else if (gpu && recording) {
            solidPaint.setAlpha(alpha);
            canvas.drawRect(r, solidPaint);
        } else if (gpu && frozen && !failed && node != null && recorded) {
            canvas.drawRenderNode(node);
            tintPaint.setColor(Glass.tint(tint));
            tintPaint.setAlpha(tintPaint.getAlpha() * alpha / 255);
            canvas.drawRect(r, tintPaint);
            wake();
        } else if (bmp != null && !failed) {
            rect.set(r);
            bmpPaint.setAlpha(alpha);
            canvas.drawBitmap(bmp, null, rect, bmpPaint);
            tintPaint.setColor(Glass.tint(tint));
            tintPaint.setAlpha(tintPaint.getAlpha() * alpha / 255);
            canvas.drawRect(r, tintPaint);
        } else {
            solidPaint.setAlpha(alpha);
            canvas.drawRect(r, solidPaint);
        }
        canvas.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.55f, sheenPaint);
        float in = stroke / 2f;
        rect.set(r.left + in, r.top + in, r.right - in, r.bottom + (sheet ? radius : 0) - in);
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
