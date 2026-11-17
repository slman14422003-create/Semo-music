package com.simomusic.player;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Volume and boost on one 0-200% slider (up to 100%: the system volume, past it: the software boost). */
public final class VolumeSheet {
    private VolumeSheet() {
    }

    public static void show(final Context c) {
        if (Dlg.dead(c)) return;
        final Dialog d = new Dialog(c, R.style.AppSheet);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        Glass.sheet(root, false);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 22));
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(c, 4);
        gp.bottomMargin = Ui.dp(c, 8);
        head.addView(grab, gp);
        TextView title = Ui.text(c, c.getString(R.string.volume_title), 17, R.color.text_primary);
        title.setTypeface(Ui.titleFace(true));
        title.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 6));
        head.addView(title);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final TextView big = Ui.text(c, "", 34, R.color.text_primary);
        big.setGravity(Gravity.CENTER);
        big.setTypeface(Ui.titleFace(true));
        root.addView(big, Ui.lp(-1, -2));

        final SeekBar sb = new SeekBar(c);
        sb.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        final int acc = Ui.color(c, R.color.accent);
        sb.setProgressTintList(ColorStateList.valueOf(acc));
        sb.setThumbTintList(ColorStateList.valueOf(acc));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.color(c, R.color.stroke)));
        LinearLayout.LayoutParams sp = Ui.lp(-1, Ui.dp(c, 48));
        sp.setMargins(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0);
        root.addView(sb, sp);

        final TextView hint = Ui.text(c, c.getString(R.string.volume_boost_hint), 13, R.color.text_secondary);
        hint.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 6), Ui.dp(c, 24), 0);
        root.addView(hint, Ui.lp(-1, -2));

        final View boostRow = Ui.toggleRow(c, c.getString(R.string.boost_title), c.getString(R.string.boost_sub),
                Store.boostOn(c), (v, on) -> {
                    Store.setFlag(c, "boost_on", on);
                    sb.setMax(VolumeHud.maxLevel(c));
                    sb.setProgress(VolumeHud.level(c));
                    sync(c, big, hint);
                });
        LinearLayout.LayoutParams bp = Ui.lp(-1, -2);
        bp.topMargin = Ui.dp(c, 10);
        root.addView(boostRow, bp);

        sb.setMax(VolumeHud.maxLevel(c));
        sb.setProgress(VolumeHud.level(c));
        sync(c, big, hint);
        sb.setOnTouchListener((x, e) -> {
            if (x.getParent() != null) x.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int last = -1;

            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                VolumeHud.setLevel(c, p);
                sync(c, big, hint);
                if (p / 10 != last) {
                    last = p / 10;
                    s.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                Ui.tap(s);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                last = -1;
            }
        });

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        Dlg.bind(c, d, null);
        d.show();
    }

    private static void sync(Context c, TextView big, TextView hint) {
        int lvl = VolumeHud.level(c);
        big.setText(lvl + "%");
        big.setTextColor(lvl > 100 ? 0xFFFF9F0A : Ui.color(c, R.color.text_primary));
        hint.setVisibility(lvl > 100 ? View.VISIBLE : View.INVISIBLE);
    }
}
