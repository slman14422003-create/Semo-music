package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public final class FastScroll extends View {
    public interface Labeler {
        String label(int adapterPos);
    }

    private final RecyclerView rv;
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float d, thumbW, thumbH, bubbleH;
    private Labeler labeler;
    private boolean active, dragging;
    private float frac;
    private String label = "";

    private final Runnable hide = () -> {
        if (!dragging) animate().alpha(0f).setDuration(Motion.BASE).start();
    };

    public FastScroll(Context c, RecyclerView list) {
        super(c);
        rv = list;
        d = c.getResources().getDisplayMetrics().density;
        thumbW = 4 * d;
        thumbH = 52 * d;
        bubbleH = 56 * d;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Ui.titleFace(true));
        text.setTextSize(24 * d);
        setAlpha(0f);
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView r, int dx, int dy) {
                if (!active || dragging) return;
                follow();
                if (dy != 0) reveal();
            }
        });
    }

    public void setup(Labeler l, boolean on) {
        labeler = l;
        active = on && l != null;
        setVisibility(active ? VISIBLE : GONE);
        if (!active) {
            dragging = false;
            setAlpha(0f);
        }
    }

    private boolean rtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    private float track() {
        return Math.max(1f, getHeight() - getPaddingTop() - getPaddingBottom() - thumbH);
    }

    private void follow() {
        int range = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent();
        frac = range <= 0 ? 0f : Math.min(1f, Math.max(0f, rv.computeVerticalScrollOffset() / (float) range));
        invalidate();
    }

    private void reveal() {
        removeCallbacks(hide);
        if (getAlpha() < 1f) animate().alpha(1f).setDuration(Motion.SHORT).start();
        postDelayed(hide, 1400);
    }

    private void moveTo(float y) {
        androidx.recyclerview.widget.RecyclerView.Adapter<?> a = rv.getAdapter();
        if (a == null || a.getItemCount() == 0 || !(rv.getLayoutManager() instanceof LinearLayoutManager)) return;
        frac = Math.min(1f, Math.max(0f, (y - getPaddingTop() - thumbH / 2f) / track()));
        int pos = Math.round(frac * (a.getItemCount() - 1));
        ((LinearLayoutManager) rv.getLayoutManager()).scrollToPositionWithOffset(pos, 0);
        String l = labeler.label(pos);
        if (l == null) l = "";
        if (!l.equals(label)) {
            label = l;
            Ui.tick(this);
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (!active || getAlpha() < 0.3f) return false;
                float x = e.getX(), y = e.getY();
                float strip = 36 * d;
                boolean inX = rtl() ? x <= strip : x >= getWidth() - strip;
                float top = getPaddingTop() + frac * track();
                boolean inY = y >= top - 16 * d && y <= top + thumbH + 16 * d;
                if (!inX || !inY) return false;
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                removeCallbacks(hide);
                animate().cancel();
                setAlpha(1f);
                moveTo(y);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                moveTo(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                label = "";
                postDelayed(hide, 900);
                invalidate();
                if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!active) return;
        boolean rtl = rtl();
        float top = getPaddingTop() + frac * track();
        float w = dragging ? thumbW * 2f : thumbW;
        float edge = 6 * d;
        float left = rtl ? edge : getWidth() - edge - w;
        thumb.setColor(Ui.color(getContext(), dragging ? R.color.accent : R.color.text_hint));
        box.set(left, top, left + w, top + thumbH);
        c.drawRoundRect(box, w / 2f, w / 2f, thumb);
        if (!dragging || label.isEmpty()) return;
        float bw = Math.max(bubbleH, text.measureText(label) + 28 * d);
        float cy = Math.min(getHeight() - bubbleH / 2f, Math.max(bubbleH / 2f, top + thumbH / 2f));
        float gap = 14 * d;
        float bl = rtl ? left + w + gap : left - gap - bw;
        bubble.setColor(Ui.color(getContext(), R.color.accent));
        box.set(bl, cy - bubbleH / 2f, bl + bw, cy + bubbleH / 2f);
        c.drawRoundRect(box, bubbleH / 2f, bubbleH / 2f, bubble);
        text.setColor(Ui.color(getContext(), R.color.on_accent));
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(label, box.centerX(), cy - (fm.ascent + fm.descent) / 2f, text);
    }
}
