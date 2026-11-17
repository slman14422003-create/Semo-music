package com.simomusic.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class FloatingNav extends FrameLayout {
    public interface Listener {
        void onTab(int index, boolean reselected);
    }

    private final int n;
    private final View pill;
    private final LinearLayout[] items;
    private final ImageView[] icons;
    private final TextView[] labels;
    private final LiveGlass glass;
    private final View gap;
    private int gapW;
    private int gapTarget;
    private ValueAnimator gapAnim;
    private Listener listener;
    private int sel = -1;
    private boolean placed;

    public FloatingNav(Context c, View source, int[] iconRes, int[] labelRes) {
        super(c);
        n = iconRes.length;
        glass = Glass.live(this, source, 32);

        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(Ui.dp(c, 26));
        pg.setColor(Ui.color(c, R.color.glass_pill));
        pg.setStroke(Math.max(1, Ui.dp(c, 1) / 2), Ui.color(c, R.color.glass_pill_stroke));
        pill.setBackground(pg);
        addView(pill, new LayoutParams(Ui.dp(c, 60), Ui.dp(c, 52), Gravity.LEFT | Gravity.CENTER_VERTICAL));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        addView(row, new LayoutParams(-1, -1));

        items = new LinearLayout[n];
        icons = new ImageView[n];
        labels = new TextView[n];
        gap = new View(c);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            if (i == n / 2) row.addView(gap, new LinearLayout.LayoutParams(0, -1));
            LinearLayout it = new LinearLayout(c);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER);
            it.setClickable(true);
            it.setFocusable(true);
            it.setContentDescription(c.getString(labelRes[i]));
            ImageView ic = new ImageView(c);
            ic.setImageResource(iconRes[i]);
            ic.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            it.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
            TextView tv = Ui.text(c, c.getString(labelRes[i]), 11, R.color.text_secondary);
            tv.setSingleLine(true);
            tv.setGravity(Gravity.CENTER);
            tv.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.topMargin = Ui.dp(c, 2);
            it.addView(tv, tp);
            Ui.press(c, it);
            it.setOnClickListener(v -> {
                Ui.tap(v);
                if (listener != null) listener.onTab(idx, idx == sel);
            });
            items[i] = it;
            icons[i] = ic;
            labels[i] = tv;
            row.addView(it, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        for (int i = 0; i < n; i++) style(i, false);
    }

    public LiveGlass glass() {
        return glass;
    }

    /** Room in the middle of the bar for the mini player circle; the tabs slide apart to make it. */
    public void setGap(boolean on, boolean animate) {
        int target = on ? Ui.dp(getContext(), 64) : 0;
        if (target == gapTarget) return;
        gapTarget = target;
        if (gapAnim != null) gapAnim.cancel();
        if (!animate || getWidth() == 0) {
            applyGap(target);
            return;
        }
        gapAnim = ValueAnimator.ofInt(gapW, target);
        gapAnim.setDuration(Motion.BASE);
        gapAnim.setInterpolator(Motion.ENTER);
        gapAnim.addUpdateListener(a -> applyGap((int) a.getAnimatedValue()));
        gapAnim.start();
    }

    private void applyGap(int w) {
        gapW = w;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) gap.getLayoutParams();
        lp.width = w;
        gap.setLayoutParams(lp);
        if (sel >= 0 && getWidth() > 0) place(sel, false);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int selected() {
        return sel;
    }

    public void select(int i, boolean animate) {
        if (i == sel || i < 0 || i >= n) return;
        int old = sel;
        sel = i;
        for (int k = 0; k < n; k++) style(k, k == i);
        if (getWidth() > 0) place(i, animate && old >= 0);
        if (animate && old >= 0) Ui.pop(icons[i]);
    }

    private void style(int k, boolean on) {
        int col = on ? R.color.accent_text : R.color.text_secondary;
        Ui.tint(icons[k], col);
        labels[k].setTextColor(Ui.color(getContext(), col));
        labels[k].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        items[k].setSelected(on);
    }

    private void place(int i, boolean animate) {
        Context c = getContext();
        int inner = getWidth() - Ui.dp(c, 16) - gapW;
        if (inner <= 0 || n == 0) return;
        int itemW = inner / n;
        LayoutParams lp = (LayoutParams) pill.getLayoutParams();
        int pw = itemW - Ui.dp(c, 4);
        if (lp.width != pw) {
            lp.width = pw;
            pill.setLayoutParams(lp);
        }
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int slot = rtl ? n - 1 - i : i;
        float x = Ui.dp(c, 8) + slot * itemW + (slot >= n / 2 ? gapW : 0) + Ui.dp(c, 2);
        pill.animate().cancel();
        if (animate && placed) {
            pill.animate().translationX(x).setDuration(Motion.BASE).setInterpolator(Motion.ENTER).start();
        } else {
            pill.setTranslationX(x);
        }
        placed = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        post(() -> {
            if (sel >= 0) place(sel, false);
        });
    }
}
