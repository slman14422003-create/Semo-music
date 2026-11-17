package com.simomusic.player;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Settings > App icon: a grid of launcher icon variants; tapping one switches the manifest alias. */
public class IconActivity extends AppCompatActivity {
    private static final int COLS = 3;
    private LinearLayout grid;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.topBar(this, getString(R.string.app_icon), R.drawable.ic_back));

        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 32));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        Glass.bg(grid, R.drawable.bg_card);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        col.addView(grid, gp);

        TextView hint = Ui.text(this, getString(R.string.app_icon_hint), 14, R.color.text_secondary);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(Ui.dp(this, 28), Ui.dp(this, 14), Ui.dp(this, 28), 0);
        col.addView(hint, hp);

        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        getWindow().setBackgroundDrawable(Ui.glow(this));
        render();
    }

    private void render() {
        grid.removeAllViews();
        int cur = IconSwitcher.current(this);
        int n = IconSwitcher.count();
        for (int r = 0; r < (n + COLS - 1) / COLS; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int k = 0; k < COLS; k++) {
                int i = r * COLS + k;
                if (i >= n) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                    continue;
                }
                row.addView(cell(i, i == cur), new LinearLayout.LayoutParams(0, -2, 1f));
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = Ui.dp(this, r == 0 ? 0 : 14);
            grid.addView(row, rp);
        }
    }

    private View cell(final int i, boolean sel) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));

        int size = Ui.dp(this, 64);
        FrameLayout ring = new FrameLayout(this);
        int pad = Ui.dp(this, 6);
        ring.setPadding(pad, pad, pad, pad);
        GradientDrawable rg = new GradientDrawable();
        rg.setCornerRadius(Ui.dp(this, 26));
        if (sel) rg.setStroke(Ui.dp(this, 3), Ui.color(this, R.color.accent));
        ring.setBackground(rg);

        FrameLayout box = new FrameLayout(this);
        Ui.round(box, Ui.dp(this, 20));
        FrameLayout inner = new FrameLayout(this);
        ImageView back = new ImageView(this);
        back.setImageResource(IconSwitcher.BACK[i]);
        ImageView front = new ImageView(this);
        front.setImageResource(IconSwitcher.FRONT[i]);
        inner.addView(back, new FrameLayout.LayoutParams(size, size));
        inner.addView(front, new FrameLayout.LayoutParams(size, size));
        inner.setScaleX(1.5f);
        inner.setScaleY(1.5f);
        box.addView(inner, new FrameLayout.LayoutParams(size, size));
        ring.addView(box, new FrameLayout.LayoutParams(size, size));
        c.addView(ring, new LinearLayout.LayoutParams(size + 2 * pad, size + 2 * pad));

        TextView label = Ui.text(this, getString(IconSwitcher.NAME[i]), 13, sel ? R.color.accent_text : R.color.text_secondary);
        label.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(this, 6);
        c.addView(label, lp);

        c.setContentDescription(getString(IconSwitcher.NAME[i]));
        c.setOnClickListener(v -> {
            if (IconSwitcher.current(this) == i) return;
            Ui.tap(v);
            IconSwitcher.apply(this, i);
            Ui.toast(this, R.string.app_icon_hint);
            render();
        });
        Ui.press(this, c);
        return c;
    }
}
