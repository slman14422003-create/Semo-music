package com.simomusic.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Details of the connected Bluetooth headset plus the two Bluetooth sound options. */
public final class BtSheet {
    private BtSheet() {
    }

    public static void show(final Context c) {
        if (Dlg.dead(c)) return;
        final Dialog d = new Dialog(c, R.style.AppSheet);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        Glass.sheet(root, false, true);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 24));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(c, 4);
        gp.bottomMargin = Ui.dp(c, 8);
        head.addView(grab, gp);
        TextView t = Ui.text(c, c.getString(R.string.bt_title), 17, R.color.text_primary);
        t.setTypeface(Ui.titleFace(true));
        t.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        head.addView(t);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.78f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        final LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        sv.addView(body);
        root.addView(sv);

        TextView loading = Ui.text(c, c.getString(R.string.bt_loading), 15, R.color.text_secondary);
        loading.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        body.addView(loading);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final boolean[] alive = {true};
        Dlg.bind(c, d, () -> alive[0] = false);
        d.show();

        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        hh.postDelayed(() -> {
            if (!alive[0] || !d.isShowing()) return;
            BtInfo.load(c, info -> {
                if (!alive[0] || !d.isShowing()) return;
                body.removeAllViews();
                fill(c, d, body, info, alive);
            });
        }, 140);
    }

    private static void steps(final View body, final boolean[] alive, final Runnable... list) {
        final int[] i = {0};
        final Runnable[] run = new Runnable[1];
        run[0] = () -> {
            if (!alive[0] || i[0] >= list.length) return;
            list[i[0]++].run();
            body.postOnAnimation(run[0]);
        };
        body.postOnAnimation(run[0]);
    }

    /** Adds a block with a soft fade-and-rise, each one a little after the one before. */
    private static void put(LinearLayout body, View v, int[] n) {
        body.addView(v);
        PressFx.deep(v);
        Ui.enter(v, 45L * n[0]++);
    }

    private static void fill(final Context c, final Dialog d, final LinearLayout body, final BtInfo i, final boolean[] alive) {
        final int[] n = {0};
        if (!i.hasDevice) {
            steps(body, alive,
                    () -> put(body, emptyHero(c), n),
                    () -> put(body, settingsButton(c, d), n),
                    () -> put(body, analysis(c, d, body), n),
                    () -> put(body, sound(c, body), n));
            return;
        }
        steps(body, alive,
                () -> {
                    put(body, hero(c, i), n);
                    if (i.needPerm) put(body, permButton(c, d), n);
                },
                () -> put(body, analysis(c, d, body), n),
                () -> put(body, sound(c, body), n),
                () -> put(body, details(c, body, i), n),
                () -> put(body, settingsButton(c, d), n));
    }

    private static View emptyHero(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), Ui.dp(c, 2));
        box.addView(badge(c, false, 56));
        TextView a = Ui.text(c, c.getString(R.string.bt_none), 18, R.color.text_primary);
        a.setTypeface(Ui.titleFace(true));
        a.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = Ui.lp(-1, -2);
        ap.topMargin = Ui.dp(c, 12);
        box.addView(a, ap);
        TextView b = Ui.text(c, c.getString(R.string.bt_none_sub), 14, R.color.text_secondary);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = Ui.lp(-1, -2);
        bp.topMargin = Ui.dp(c, 4);
        box.addView(b, bp);
        return box;
    }

    /** Connected headset: round badge, name and type on one line, battery as a small pill. */
    private static View hero(Context c, BtInfo i) {
        LinearLayout hd = new LinearLayout(c);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 12), Ui.dp(c, 12));
        hd.addView(badge(c, true, 52));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nm = Ui.text(c, i.name.isEmpty() ? c.getString(R.string.bt_t_other) : i.name, 19, R.color.text_primary);
        nm.setTypeface(Ui.titleFace(true));
        nm.setMaxLines(2);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(nm);
        TextView kind = Ui.text(c, kindLabel(c, i), 14, R.color.accent_text);
        kind.setPadding(0, Ui.dp(c, 2), 0, 0);
        col.addView(kind);
        LinearLayout.LayoutParams cp = Ui.weight(1);
        cp.setMarginStart(Ui.dp(c, 14));
        hd.addView(col, cp);
        if (i.battery >= 0) {
            TextView b = Ui.text(c, i.battery + "%", 14, R.color.accent_text);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.dp(c, 100));
            g.setColor(Ui.color(c, R.color.accent_soft));
            b.setBackground(g);
            LinearLayout.LayoutParams bp = Ui.lp(-2, -2);
            bp.setMarginStart(Ui.dp(c, 10));
            hd.addView(b, bp);
        }
        return hd;
    }

    // ------------------------------------------------------------------ collapsible card

    /** A card with a title, a one-line summary and a chevron; tapping the header folds the content in or out. */
    private static final class Section {
        final Context c;
        final ViewGroup anim;
        final LinearLayout card;
        final LinearLayout box;
        final TextView sum;
        final ImageView chev;
        boolean open;

        Section(final Context c, ViewGroup anim, CharSequence title, boolean open) {
            this.c = c;
            this.anim = anim;
            this.open = open;
            card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            Glass.bg(card, R.drawable.bg_card);
            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setMinimumHeight(Ui.dp(c, 54));
            head.setPaddingRelative(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
            head.setBackgroundResource(R.drawable.bg_ripple_rect);
            TextView t = Ui.text(c, title, 15, R.color.text_primary);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(t, Ui.lp(-2, -2));
            sum = Ui.text(c, "", 13, R.color.text_secondary);
            sum.setGravity(Gravity.END);
            sum.setSingleLine(true);
            sum.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp = Ui.weight(1);
            sp.setMarginStart(Ui.dp(c, 12));
            sp.setMarginEnd(Ui.dp(c, 8));
            head.addView(sum, sp);
            chev = new ImageView(c);
            chev.setImageResource(R.drawable.ic_chevron);
            Ui.tint(chev, R.color.text_hint);
            chev.setRotation(angle());
            head.addView(chev, Ui.lp(Ui.dp(c, 20), Ui.dp(c, 20)));
            card.addView(head);
            box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setVisibility(open ? View.VISIBLE : View.GONE);
            card.addView(box);
            head.setOnClickListener(v -> {
                Ui.tap(v);
                toggle();
            });
        }

        /** The chevron points down when folded and up when open, in both text directions. */
        private float angle() {
            float a = open ? -90f : 90f;
            return Ui.isRtl(c) ? -a : a;
        }

        void toggle() {
            open = !open;
            android.transition.AutoTransition t = new android.transition.AutoTransition();
            t.setDuration(Motion.BASE);
            t.setInterpolator(Motion.ENTER);
            android.transition.TransitionManager.beginDelayedTransition(anim, t);
            box.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.animate().rotation(angle()).setDuration(Motion.SHORT).setInterpolator(Motion.ENTER).start();
        }

        void summary(CharSequence s) {
            sum.setText(s);
        }

        View view() {
            LinearLayout.LayoutParams p = Ui.lp(-1, -2);
            p.bottomMargin = Ui.dp(c, 10);
            card.setLayoutParams(p);
            return card;
        }
    }

    // ------------------------------------------------------------------ live analysis

    /** Live readout from the sound engine, refreshed twice a second while the sheet is open. Shown as small tiles. */
    private static View analysis(final Context c, final Dialog d, ViewGroup anim) {
        final Section sec = new Section(c, anim, c.getString(R.string.bt_stats), true);
        int[] labels = {R.string.st_lufs, R.string.st_gain, R.string.st_comp, R.string.st_lim, R.string.st_peak};
        final TextView[] v = new TextView[labels.length];
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), Ui.dp(c, 10));
        LinearLayout row = null;
        for (int k = 0; k < labels.length; k++) {
            if (k % 2 == 0) {
                row = new LinearLayout(c);
                grid.addView(row, Ui.lp(-1, -2));
            }
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(c, 16));
            bg.setColor(Ui.color(c, R.color.neutral_soft));
            tile.setBackground(bg);
            TextView l = Ui.text(c, c.getString(labels[k]), 12, R.color.text_secondary);
            l.setMaxLines(2);
            tile.addView(l);
            v[k] = Ui.text(c, "-", 16, R.color.text_primary);
            v[k].setTypeface(Typeface.DEFAULT_BOLD);
            v[k].setPadding(0, Ui.dp(c, 3), 0, 0);
            tile.addView(v[k]);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
            row.addView(tile, tp);
        }
        // the fifth tile sits alone: keep it half width by adding an empty half
        row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        sec.box.addView(grid);
        sec.summary("");

        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] tick = new Runnable[1];
        final String[] last = new String[labels.length];
        tick[0] = () -> {
            if (!d.isShowing()) return;
            BtDsp.Stats s = BtDspProcessor.INSTANCE.stats();
            boolean live = System.nanoTime() - s.stampNs < 1_500_000_000L && s.stampNs != 0;
            String[] now = {
                    live && !Float.isNaN(s.lufsI) ? String.format(Locale.US, "%.1f LUFS", s.lufsI) : "-",
                    live ? String.format(Locale.US, "%+.1f dB", s.normDb) : "-",
                    live ? String.format(Locale.US, "-%.1f dB", s.compDb) : "-",
                    live ? String.format(Locale.US, "%.1f dB", s.limDb) : "-",
                    live ? String.format(Locale.US, "%.1f dBFS", s.peakDb) : "-"};
            for (int k = 0; k < now.length; k++) {
                if (!now[k].equals(last[k])) {
                    last[k] = now[k];
                    v[k].setText(now[k]);
                    if (k == 0) sec.summary(now[0]);
                }
            }
            h.postDelayed(tick[0], 500);
        };
        h.post(tick[0]);
        return sec.view();
    }

    // ------------------------------------------------------------------ sound tuning

    private static final int[] PROF_T = {R.string.bt_profile_0, R.string.bt_profile_1, R.string.bt_profile_2, R.string.bt_profile_3, R.string.bt_profile_4, R.string.bt_profile_5};
    private static final int[] PROF_S = {R.string.bt_profile_0_sub, R.string.bt_profile_1_sub, R.string.bt_profile_2_sub, R.string.bt_profile_3_sub, R.string.bt_profile_4_sub, R.string.bt_profile_5_sub};

    /** Sound tuning: profile (folded, shows the current one), loudness, and the switches (folded, shows how many are on). */
    private static View sound(final Context c, final ViewGroup anim) {
        final LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView sec = Ui.text(c, c.getString(R.string.bt_tune), 14, R.color.accent_text);
        sec.setTypeface(Typeface.DEFAULT_BOLD);
        sec.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 8));
        wrap.addView(sec);

        // profile
        final Section prof = new Section(c, anim, c.getString(R.string.bt_sec_profile), false);
        final Runnable[] fillProf = new Runnable[1];
        fillProf[0] = () -> {
            prof.box.removeAllViews();
            int cur = BtAudio.profile(c);
            prof.summary(c.getString(PROF_T[Math.max(0, Math.min(cur, PROF_T.length - 1))]));
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), Ui.dp(c, 6));
            for (int i = 0; i < PROF_T.length; i++) {
                final int p = i;
                list.addView(choice(c, PROF_T[i], PROF_S[i], i == cur, v -> {
                    Ui.tap(v);
                    boolean wasCustom = BtAudio.profile(c) == BtAudio.CUSTOM;
                    if (p == BtAudio.CUSTOM && !wasCustom && Store.prefs(c).getString(BtAudio.K_EQ, "").isEmpty()) {
                        BtAudio.saveCustom(c, BtAudio.customCurve(c));   // start from the profile you were on
                    }
                    BtAudio.setInt(c, BtAudio.K_PROFILE, p);
                    BtAudio.refresh();
                    fillProf[0].run();
                    if (p == BtAudio.CUSTOM) EqSheet.show(c);
                }));
            }
            prof.box.addView(list);
            PressFx.deep(list);
        };
        fillProf[0].run();
        wrap.addView(prof.view());

        // loudness
        final LinearLayout lc = new LinearLayout(c);
        lc.setOrientation(LinearLayout.VERTICAL);
        Glass.bg(lc, R.drawable.bg_card);
        lc.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        lc.addView(Ui.text(c, c.getString(R.string.bt_loud), 15, R.color.text_primary));
        TextView ls = Ui.text(c, c.getString(R.string.bt_loud_sub), 13, R.color.text_secondary);
        ls.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 10));
        lc.addView(ls);
        final LinearLayout seg = new LinearLayout(c);
        Glass.bg(seg, R.drawable.bg_segment);
        seg.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        lc.addView(seg);
        final String[] names = {c.getString(R.string.bt_loud_off), c.getString(R.string.bt_lvl_low), c.getString(R.string.bt_lvl_mid), c.getString(R.string.bt_lvl_high)};
        final Runnable[] fillSeg = new Runnable[1];
        fillSeg[0] = () -> {
            seg.removeAllViews();
            int bs = BtAudio.boost(c);
            for (int i = 0; i < names.length; i++) {
                final int k = i;
                TextView b = Ui.text(c, names[i], 14, i == bs ? R.color.on_accent : R.color.text_secondary);
                b.setGravity(Gravity.CENTER);
                b.setMinHeight(Ui.dp(c, 40));
                b.setTypeface(i == bs ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                if (i == bs) b.setBackgroundResource(R.drawable.bg_segment_sel);
                b.setOnClickListener(v -> {
                    if (BtAudio.boost(c) == k) return;
                    Ui.tap(v);
                    BtAudio.setInt(c, BtAudio.K_BOOST, k);
                    BtAudio.refresh();
                    fillSeg[0].run();
                    Ui.pop(seg.getChildAt(k));
                });
                Ui.press(c, b);
                seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
            }
        };
        fillSeg[0].run();
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(c, 10);
        wrap.addView(lc, lp);

        // switches
        final Section opts = new Section(c, anim, c.getString(R.string.bt_sec_options), false);
        final boolean ok = BtAudio.optSupported();
        final Runnable count = () -> {
            int on = 0, all = 0;
            all++; if (BtAudio.opt(c)) on++;
            if (ok) { all++; if (BtAudio.comp(c)) on++; }
            all++; if (BtAudio.pin(c)) on++;
            all++; if (BtAudio.vbass(c)) on++;
            all++; if (BtAudio.contour(c)) on++;
            all++; if (BtAudio.smooth(c)) on++;
            all++; if (BtAudio.all(c)) on++;
            all++; if (BtAudio.wide(c)) on++;
            all++; if (BtAudio.autoPlay(c)) on++;
            opts.summary(c.getString(R.string.bt_n_on, on, all));
        };
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(0, 0, 0, Ui.dp(c, 6));
        addSwitch(c, g, R.string.bt_opt, R.string.bt_opt_sub, BtAudio.opt(c), BtAudio.K_OPT, true, count);
        addSwitch(c, g, R.string.bt_comp, ok ? R.string.bt_comp_sub : R.string.bt_opt_old, ok && BtAudio.comp(c), BtAudio.K_COMP, ok, count);
        addSwitch(c, g, R.string.bt_pin, R.string.bt_pin_sub, BtAudio.pin(c), BtAudio.K_PIN, true, count);
        addSwitch(c, g, R.string.bt_vbass, R.string.bt_vbass_sub, BtAudio.vbass(c), BtAudio.K_VBASS, true, count);
        addSwitch(c, g, R.string.bt_contour, R.string.bt_contour_sub, BtAudio.contour(c), BtAudio.K_CONTOUR, true, count);
        addSwitch(c, g, R.string.bt_smooth, R.string.bt_smooth_sub, BtAudio.smooth(c), BtAudio.K_SMOOTH, true, count);
        addSwitch(c, g, R.string.bt_all, R.string.bt_all_sub, BtAudio.all(c), BtAudio.K_ALL, true, count);
        addSwitch(c, g, R.string.bt_wide, R.string.bt_wide_sub, BtAudio.wide(c), BtAudio.K_WIDE, true, count);
        addSwitch(c, g, R.string.bt_auto, R.string.bt_auto_sub, BtAudio.autoPlay(c), BtAudio.K_AUTO, true, count);
        opts.box.addView(g);
        count.run();
        wrap.addView(opts.view());
        return wrap;
    }

    /** One switch row inside the folded card, with a hairline between rows. */
    private static void addSwitch(final Context c, LinearLayout g, int titleRes, int subRes, boolean on, final String key,
                                  boolean enabled, final Runnable after) {
        View row = Ui.toggleRow(c, c.getString(titleRes), c.getString(subRes), on, (b, now) -> {
            Store.setFlag(c, key, now);
            BtAudio.refresh();
            after.run();
        });
        View card = row.findViewById(R.id.card);
        if (card != null) Ui.flatRow(c, card, false, true, 0, 0);
        if (!enabled) {
            row.setEnabled(false);
            row.setAlpha(0.5f);
        }
        g.addView(row);
    }

    // ------------------------------------------------------------------ device details

    private static View details(Context c, ViewGroup anim, BtInfo i) {
        Section sec = new Section(c, anim, c.getString(R.string.bt_sec_details), false);
        sec.summary(typeLabel(c, i.audioType));
        LinearLayout facts = new LinearLayout(c);
        facts.setOrientation(LinearLayout.VERTICAL);
        facts.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 4));
        addFact(c, facts, R.string.bt_row_type, typeLabel(c, i.audioType));
        if (!i.address.isEmpty()) addFact(c, facts, R.string.bt_row_addr, i.address.toUpperCase(Locale.ROOT));
        if (!i.needPerm) addFact(c, facts, R.string.bt_row_pair, c.getString(i.bonded ? R.string.bt_paired : R.string.bt_not_paired));
        String prof = profiles(c, i);
        if (!prof.isEmpty()) addFact(c, facts, R.string.bt_row_profiles, prof);
        if (i.a2dp || i.audioType == 8) addFact(c, facts, R.string.bt_row_stream, c.getString(i.playing ? R.string.bt_playing : R.string.bt_idle));
        if (i.battery >= 0) addFact(c, facts, R.string.bt_row_battery, i.battery + "%");
        addFact(c, facts, R.string.bt_row_rates, rates(c, i.rates));
        addFact(c, facts, R.string.bt_row_channels, channels(c, i.channels));
        String fm = formats(i.encodings);
        if (!fm.isEmpty()) addFact(c, facts, R.string.bt_row_formats, fm);
        sec.box.addView(facts);
        TextView note = Ui.text(c, c.getString(R.string.bt_codec), 13, R.color.text_hint);
        note.setLineSpacing(0, 1.1f);
        note.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 14));
        sec.box.addView(note);
        return sec.view();
    }

    private static View choice(Context c, int titleRes, int subRes, boolean on, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, 56));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackgroundResource(R.drawable.bg_ripple_rect);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(c, c.getString(titleRes), 16, on ? R.color.accent_text : R.color.text_primary);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        col.addView(t);
        TextView s = Ui.text(c, c.getString(subRes), 13, R.color.text_secondary);
        s.setPadding(0, Ui.dp(c, 2), 0, 0);
        col.addView(s);
        r.addView(col, Ui.weight(1));
        if (on) {
            ImageView ck = new ImageView(c);
            ck.setImageResource(R.drawable.ic_check);
            Ui.tint(ck, R.color.accent_text);
            LinearLayout.LayoutParams kp = Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22));
            kp.setMarginStart(Ui.dp(c, 10));
            r.addView(ck, kp);
        }
        r.setOnClickListener(l);
        return r;
    }

    private static View settingsButton(final Context c, final Dialog d) {
        Button b = Ui.pill(c, R.string.bt_settings, 0, false, v -> {
            d.dismiss();
            try {
                c.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(c, 52));
        p.setMargins(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 12));
        b.setLayoutParams(p);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private static View permButton(final Context c, final Dialog d) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView s = Ui.text(c, c.getString(R.string.bt_perm_sub), 14, R.color.text_secondary);
        s.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 8));
        box.addView(s);
        Button b = Ui.pill(c, R.string.bt_perm, 0, true, v -> {
            if (c instanceof Activity) Perms.askBt((Activity) c);
            d.dismiss();
        });
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(c, 52));
        p.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 12));
        b.setLayoutParams(p);
        b.setGravity(Gravity.CENTER);
        box.addView(b);
        return box;
    }

    private static View badge(Context c, boolean on, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(R.drawable.ic_bluetooth);
        Ui.tint(iv, on ? R.color.on_accent : R.color.text_secondary);
        int p = Ui.dp(c, sizeDp / 4);
        iv.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? Ui.color(c, R.color.accent) : Ui.color(c, R.color.surface_high));
        iv.setBackground(g);
        iv.setLayoutParams(Ui.lp(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return iv;
    }

    static void addFact(Context c, LinearLayout g, int labelRes, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        TextView l = Ui.text(c, c.getString(labelRes), 14, R.color.text_secondary);
        r.addView(l, Ui.lp(-2, -2));
        TextView v = Ui.text(c, value, 15, R.color.text_primary);
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vp = Ui.weight(1);
        vp.setMarginStart(Ui.dp(c, 16));
        r.addView(v, vp);
        if (g.getChildCount() > 0) {
            View line = new View(c);
            line.setBackgroundColor(Ui.color(c, R.color.stroke_soft));
            g.addView(line, Ui.lp(-1, 1));
        }
        g.addView(r);
    }

    // ------------------------------------------------------------------ labels

    private static String typeLabel(Context c, int t) {
        switch (t) {
            case 8:
                return c.getString(R.string.bt_t_a2dp);
            case 26:
            case 27:
            case 30:
                return c.getString(R.string.bt_t_le);
            case 7:
                return c.getString(R.string.bt_t_sco);
            case 23:
                return c.getString(R.string.bt_t_hearing);
            default:
                return c.getString(R.string.bt_t_other);
        }
    }

    private static String kindLabel(Context c, BtInfo i) {
        int k = i.btClass;
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) return c.getString(R.string.bt_c_headphones);
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                || k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE) return c.getString(R.string.bt_c_headset);
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
                || k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
                || k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO) return c.getString(R.string.bt_c_speaker);
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) return c.getString(R.string.bt_c_car);
        if (i.audioType == 23) return c.getString(R.string.bt_t_hearing);
        return c.getString(R.string.bt_c_other);
    }

    private static String profiles(Context c, BtInfo i) {
        StringBuilder sb = new StringBuilder();
        if (i.a2dp || i.audioType == 8) sb.append("A2DP");
        if (i.audioType == 26 || i.audioType == 27 || i.audioType == 30) sb.append("LE Audio");
        if (i.hfp || i.audioType == 7) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(c.getString(R.string.bt_hfp));
        }
        return sb.toString();
    }

    private static String rates(Context c, int[] r) {
        if (r == null || r.length == 0) return c.getString(R.string.bt_any);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(" · ");
            float k = r[i] / 1000f;
            sb.append(k == (int) k ? String.valueOf((int) k) : String.format(Locale.US, "%.1f", k));
        }
        return sb.append(" kHz").toString();
    }

    private static String channels(Context c, int[] ch) {
        if (ch == null || ch.length == 0) return c.getString(R.string.bt_stereo);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ch.length; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(ch[i] == 1 ? c.getString(R.string.bt_mono) : ch[i] == 2 ? c.getString(R.string.bt_stereo) : String.valueOf(ch[i]));
        }
        return sb.toString();
    }

    private static String formats(int[] enc) {
        if (enc == null || enc.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int e : enc) {
            String s = null;
            if (e == AudioFormat.ENCODING_PCM_16BIT) s = "PCM 16-bit";
            else if (e == AudioFormat.ENCODING_PCM_8BIT) s = "PCM 8-bit";
            else if (e == AudioFormat.ENCODING_PCM_FLOAT) s = "PCM float";
            else if (e == 21) s = "PCM 24-bit";
            else if (e == 22) s = "PCM 32-bit";
            if (s == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s);
        }
        return sb.toString();
    }
}
