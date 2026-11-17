package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Swipe a song row to either side to play it next. The swipe never "completes" inside ItemTouchHelper: the row
 * always springs back by the helper's own recover animation, and the action fires the moment the finger lets go
 * past the threshold. So the row can never stay stuck off to the side, and nothing is re-bound mid-animation.
 */
public final class SwipeToQueue extends ItemTouchHelper.SimpleCallback {
    private static final float TRIGGER = 0.30f;     // fraction of the row width the finger must travel
    private static final float MAX_SHOWN = 0.42f;   // the row never follows the finger further than this

    private final Context c;
    private final TrackAdapter adapter;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Drawable icon;
    private final int radius, inset, iconSize, hairline;
    private boolean crossed, fired;

    public SwipeToQueue(Context c, TrackAdapter adapter) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.c = c;
        this.adapter = adapter;
        bg.setColor(Ui.color(c, R.color.accent));
        Drawable d = ContextCompat.getDrawable(c, R.drawable.ic_queue_add);
        if (d != null) {
            d = d.mutate();
            d.setTint(Ui.color(c, R.color.on_accent));
        }
        icon = d;
        radius = Ui.dp(c, 18);
        inset = Ui.dp(c, 14);
        iconSize = Ui.dp(c, 24);
        hairline = Ui.dp(c, 1);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
        return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        if (rv.getAdapter() != adapter || adapter.select || adapter.reorder) return 0;
        if (vh.getItemViewType() == 1) return 0;      // the header that scrolls with the list
        return super.getSwipeDirs(rv, vh);
    }

    // a swipe is never completed by the helper (neither by distance nor by a fling): it always recovers
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) {
        return 10f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return Float.MAX_VALUE;
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        return Float.MAX_VALUE;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        // unreachable by design
    }

    @Override
    public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        crossed = false;
        fired = false;
    }

    private void queue(RecyclerView.ViewHolder vh) {
        int adapterPos = vh.getBindingAdapterPosition();
        if (adapterPos == RecyclerView.NO_POSITION) return;
        int p = adapterPos - (adapter.header == null ? 0 : 1);
        if (p < 0 || p >= adapter.data.size()) return;
        Pb.next(c, adapter.data.get(p));
        Ui.confirm(vh.itemView);
        Ui.toast(c, R.string.added_queue);
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY, int state, boolean active) {
        if (state != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(canvas, rv, vh, dX, dY, state, active);
            return;
        }
        View v = vh.itemView;
        float w = Math.max(1f, v.getWidth());
        boolean over = Math.abs(dX) > w * TRIGGER;
        if (active) {
            if (over && !crossed) Ui.tap(v);                 // a tick when letting go would add the song
            if (!over && crossed) Ui.tap(v);                 // and another one when it is taken back
            crossed = over;
        } else if (crossed && !fired) {
            fired = true;                                    // first frame after release: do it once
            crossed = false;
            queue(vh);
        }
        dX = Gest.rubber(dX, w * TRIGGER);
        dX = Math.signum(dX) * Math.min(Math.abs(dX), w * MAX_SHOWN);
        if (dX != 0f) {
            float top = v.getTop() + hairline, bottom = v.getBottom() - hairline;
            if (dX > 0) rect.set(v.getLeft() + inset, top, v.getLeft() + inset + dX, bottom);
            else rect.set(v.getRight() - inset + dX, top, v.getRight() - inset, bottom);
            if (rect.width() > 2f) {
                canvas.drawRoundRect(rect, radius, radius, bg);
                if (icon != null && rect.width() > iconSize + inset) {
                    float prog = Math.min(1f, Math.abs(dX) / (w * TRIGGER));
                    float sc = over ? 1.25f : 0.7f + 0.3f * prog;
                    int cy = (int) ((top + bottom) / 2f);
                    int cx = dX > 0 ? (int) rect.left + inset + iconSize / 2 : (int) rect.right - inset - iconSize / 2;
                    icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2);
                    icon.setAlpha((int) (255 * (0.55f + 0.45f * prog)));
                    canvas.save();
                    canvas.scale(sc, sc, cx, cy);
                    icon.draw(canvas);
                    canvas.restore();
                }
            }
        }
        super.onChildDraw(canvas, rv, vh, dX, dY, state, active);
    }
}
