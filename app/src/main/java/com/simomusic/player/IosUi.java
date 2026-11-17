package com.simomusic.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class IosUi {
    private IosUi() {
    }

    private static final int[] PALETTE = {
            0xFF007AFF, 0xFF34C759, 0xFFFF9500, 0xFFFF3B30, 0xFF5856D6,
            0xFFFF2D55, 0xFF30B0C7, 0xFFAF52DE, 0xFF8E8E93, 0xFFFFCC00
    };
    private static final String DIV = "ios_div";

    static GradientDrawable cardBg(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.color(c, R.color.surface));
        g.setCornerRadius(Ui.dp(c, 12));
        return g;
    }

    static TextView title(Context c, CharSequence t) {
        TextView v = Ui.text(c, t, 34, R.color.text_primary);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), Ui.dp(c, 6));
        return v;
    }

    static TextView section(Context c, int textRes) {
        TextView t = Ui.text(c, c.getString(textRes), 13, R.color.text_hint);
        t.setAllCaps(true);
        t.setPaddingRelative(Ui.dp(c, 36), Ui.dp(c, 26), Ui.dp(c, 36), Ui.dp(c, 7));
        return t;
    }

    private static FrameLayout shell(Context c, LinearLayout content) {
        FrameLayout w = new FrameLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setMinimumHeight(Ui.dp(c, 52));
        content.setPaddingRelative(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 14), Ui.dp(c, 9));
        w.addView(content, new FrameLayout.LayoutParams(-1, -2));
        return w;
    }

    private static void addDivider(Context c, FrameLayout w, int startDp) {
        View d = new View(c);
        d.setTag(DIV);
        d.setBackgroundColor(Ui.color(c, R.color.row_stroke));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 1) / 2), Gravity.BOTTOM);
        lp.setMarginStart(Ui.dp(c, startDp));
        w.addView(d, lp);
    }

    private static void press(Context c, View w) {
        w.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.color(c, R.color.ripple)), null, new ColorDrawable(0xFFFFFFFF)));
    }

    private static View iconBox(Context c, int iconRes, CharSequence key) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(c, 7));
        g.setColor(PALETTE[Math.abs(key.toString().hashCode()) % PALETTE.length]);
        f.setBackground(g);
        ImageView i = new ImageView(c);
        i.setImageResource(iconRes);
        i.setColorFilter(0xFFFFFFFF);
        f.addView(i, new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER));
        return f;
    }

    private static LinearLayout texts(Context c, CharSequence title, CharSequence sub) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView tt = Ui.text(c, title, 17, R.color.text_primary);
        col.addView(tt);
        if (sub != null && sub.length() > 0) {
            TextView s = Ui.text(c, sub, 13, R.color.text_hint);
            s.setPadding(0, Ui.dp(c, 2), 0, 0);
            col.addView(s);
        }
        return col;
    }

    static View row(Context c, int iconRes, CharSequence title, CharSequence sub,
                    CharSequence trailing, boolean trailingOk, View.OnClickListener click) {
        LinearLayout content = new LinearLayout(c);
        FrameLayout w = shell(c, content);
        if (iconRes != 0) content.addView(iconBox(c, iconRes, title), new LinearLayout.LayoutParams(Ui.dp(c, 29), Ui.dp(c, 29)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(Ui.dp(c, iconRes != 0 ? 14 : 0));
        lp.setMarginEnd(Ui.dp(c, 8));
        content.addView(texts(c, title, sub), lp);
        if (trailing != null) {
            TextView b = Ui.text(c, trailing, 16, trailingOk ? R.color.ok : R.color.text_hint);
            b.setMaxWidth(Ui.dp(c, 140));
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.addView(b);
        }
        if (click != null) {
            ImageView ch = new ImageView(c);
            ch.setImageResource(R.drawable.ic_chevron);
            ch.setColorFilter(Ui.color(c, R.color.text_hint));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
            cp.setMarginStart(Ui.dp(c, 4));
            content.addView(ch, cp);
            w.setOnClickListener(click);
            press(c, w);
        }
        addDivider(c, w, iconRes != 0 ? 59 : 16);
        return w;
    }

    static View toggle(Context c, CharSequence title, CharSequence sub, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout content = new LinearLayout(c);
        final FrameLayout w = shell(c, content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginEnd(Ui.dp(c, 12));
        content.addView(texts(c, title, sub), lp);
        final Toggle sw = new Toggle(c);
        sw.setChecked(on);
        content.addView(sw);
        press(c, w);
        w.setOnClickListener(v -> {
            Ui.tap(v);
            sw.toggle();
            l.onCheckedChanged(sw, sw.isChecked());
            androidx.core.view.ViewCompat.setStateDescription(w, c.getString(sw.isChecked() ? R.string.perm_on : R.string.state_off));
        });
        androidx.core.view.ViewCompat.setStateDescription(w, c.getString(on ? R.string.perm_on : R.string.state_off));
        androidx.core.view.ViewCompat.setAccessibilityDelegate(w, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(sw.isChecked());
            }
        });
        addDivider(c, w, 16);
        return w;
    }

    static void group(Context c, ViewGroup g) {
        g.setBackground(cardBg(c));
        g.setClipToOutline(true);
        ViewGroup.LayoutParams p = g.getLayoutParams();
        if (p instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) p).setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
            g.setLayoutParams(p);
        }
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            View d = g.getChildAt(i).findViewWithTag(DIV);
            if (d != null) d.setVisibility(i == n - 1 ? View.INVISIBLE : View.VISIBLE);
        }
    }
}
