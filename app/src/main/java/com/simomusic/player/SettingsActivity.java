package com.simomusic.player;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Settings: audio formats, playback, lock screen, and the permission center (status + one tap to grant). */
public class SettingsActivity extends AppCompatActivity implements FullBleed {
    private LinearLayout perms;
    private boolean dirty;
    private int permSig = -1;
    private final TextView[] modeBtns = new TextView[3];

    /** Glass: style (glass / crystal) and a level bar from 0 (off, solid) to 10. */
    private View glassCard() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(IosUi.cardBg(this));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        card.setLayoutParams(cp);

        TextView t1 = Ui.text(this, getString(R.string.glass_style), 15, R.color.text_secondary);
        t1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(t1);
        final LinearLayout seg = new LinearLayout(this);
        Glass.bg(seg, R.drawable.bg_segment);
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        final TextView[] st = new TextView[2];
        int[] names = {R.string.glass_frost, R.string.glass_crystal};
        final Runnable paint = () -> {
            for (int i = 0; i < 2; i++) {
                boolean on = Glass.STYLE == i;
                st[i].setTextColor(Ui.color(this, on ? R.color.on_accent : R.color.text_secondary));
                st[i].setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                st[i].setBackgroundResource(on ? R.drawable.bg_segment_sel : 0);
            }
        };
        for (int i = 0; i < 2; i++) {
            final int k = i;
            TextView b = Ui.text(this, getString(names[i]), 15, R.color.text_secondary);
            b.setGravity(android.view.Gravity.CENTER);
            b.setMinHeight(Ui.dp(this, 44));
            b.setOnClickListener(v -> {
                if (Glass.STYLE == k) return;
                Ui.tap(v);
                Glass.save(this, Glass.LEVEL == 0 ? 6 : Glass.LEVEL, k);
                paint.run();
                App.recreateAll();
            });
            st[i] = b;
            seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        paint.run();
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Ui.dp(this, 10);
        card.addView(seg, sp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t2 = Ui.text(this, getString(R.string.glass_level), 15, R.color.text_secondary);
        t2.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(t2, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = Ui.text(this, levelLabel(Glass.LEVEL), 15, R.color.text_primary);
        head.addView(val);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = Ui.dp(this, 18);
        card.addView(head, hp);

        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(10);
        bar.setProgress(Glass.LEVEL);
        int acc = Ui.color(this, R.color.accent);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(acc));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(acc));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.stroke)));
        bar.setOnTouchListener((x, e) -> {
            if (x.getParent() != null) x.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                val.setText(levelLabel(p));
                if (user) Ui.tick(s);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
                Ui.tap(s);
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
                if (s.getProgress() == Glass.LEVEL) return;
                Glass.save(SettingsActivity.this, s.getProgress(), Glass.STYLE);
                App.recreateAll();
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
        bp.topMargin = Ui.dp(this, 4);
        card.addView(bar, bp);

        TextView hint = Ui.text(this, getString(R.string.glass_hint), 13, R.color.text_hint);
        card.addView(hint);
        return card;
    }

    private String levelLabel(int p) {
        return p == 0 ? getString(R.string.glass_none) : String.valueOf(p);
    }

    /** Theme mode (auto / light / dark) and the accent color swatches. */
    private View appearanceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(IosUi.cardBg(this));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        TextView t1 = Ui.text(this, getString(R.string.theme_mode), 15, R.color.text_secondary);
        t1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(t1);
        LinearLayout seg = new LinearLayout(this);
        Glass.bg(seg, R.drawable.bg_segment);
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        int[] labels = {R.string.theme_auto, R.string.theme_light, R.string.theme_dark};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView b = Ui.text(this, getString(labels[i]), 15, R.color.text_secondary);
            b.setGravity(android.view.Gravity.CENTER);
            b.setMinHeight(Ui.dp(this, 44));
            b.setOnClickListener(v -> {
                if (Accent.mode(this) == m) return;
                Ui.tap(v);
                Accent.setMode(this, m);   // AppCompat re-creates the screens when day / night really changes
                styleModes();
            });
            modeBtns[i] = b;
            seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Ui.dp(this, 10);
        card.addView(seg, sp);
        styleModes();

        TextView t2 = Ui.text(this, getString(R.string.accent_color), 15, R.color.text_secondary);
        t2.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = Ui.dp(this, 18);
        card.addView(t2, tp);

        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int sel = Accent.index(this);
        for (int row = 0; row < (Accent.count() + 3) / 4; row++) {
            LinearLayout r = new LinearLayout(this);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = Ui.dp(this, 12);
            for (int k = 0; k < 4; k++) {
                final int i = row * 4 + k;
                if (i >= Accent.count()) {      // last row is shorter: keep the dots aligned with the rows above
                    r.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
                    continue;
                }
                FrameLayout cell = new FrameLayout(this);
                View dot = new View(this);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(Accent.swatch(this, i, night));
                if (i == sel) g.setStroke(Ui.dp(this, 3), Ui.color(this, R.color.text_primary));
                else g.setStroke(Ui.dp(this, 1), Ui.color(this, R.color.stroke));
                dot.setBackground(g);
                int d = Ui.dp(this, 46);
                cell.addView(dot, new FrameLayout.LayoutParams(d, d, android.view.Gravity.CENTER));
                if (i == sel) {
                    ImageView ck = new ImageView(this);
                    ck.setImageResource(R.drawable.ic_check);
                    ck.setColorFilter(androidx.core.graphics.ColorUtils.calculateLuminance(Accent.swatch(this, i, night)) > 0.7 ? 0xFF000000 : 0xFFFFFFFF);
                    int p = Ui.dp(this, 12);
                    ck.setPadding(p, p, p, p);
                    cell.addView(ck, new FrameLayout.LayoutParams(d, d, android.view.Gravity.CENTER));
                }
                cell.setOnClickListener(v -> {
                    if (Accent.index(this) == i) return;
                    Ui.tap(v);
                    Accent.set(this, i);
                    App.recreateAll();
                });
                r.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
            }
            card.addView(r, rp);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        card.setLayoutParams(cp);
        return card;
    }

    /** Android has no switch that makes an app "the" music player; this is the closest the system allows. */
    private void pickDefault() {
        Sheet.show(this, getString(R.string.default_app), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            l.add(Sheet.item(R.drawable.ic_play, getString(R.string.default_pick_files), false, false, () -> {
                java.util.List<Track> all = Library.tracks;
                if (all == null || all.isEmpty()) {
                    Ui.toast(this, R.string.default_no_song);
                    return;
                }
                Ui.toast(this, R.string.default_hint);
                Ui.go(this, new android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setDataAndType(all.get(0).uri, "audio/*")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }));
            l.add(Sheet.item(R.drawable.ic_settings, getString(R.string.default_settings), false, false, () ->
                    Ui.go(this, new android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))));
            return l;
        });
    }

    private void styleModes() {
        int cur = Accent.mode(this);
        for (int i = 0; i < 3; i++) {
            TextView b = modeBtns[i];
            boolean on = i == cur;
            b.setTextColor(Ui.color(this, on ? R.color.on_accent : R.color.text_secondary));
            b.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            if (on) b.setBackgroundResource(R.drawable.bg_segment_sel);
            else b.setBackground(null);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout backRow = new LinearLayout(this);
        backRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), 0);
        backRow.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> finish()));
        root.addView(backRow);
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 120));
        sv.addView(col);
        sv.setClipToPadding(false);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        FrameLayout glassSrc = new FrameLayout(this);
        glassSrc.setBackground(Ui.glow(this));
        glassSrc.addView(root, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout stage = new FrameLayout(this);
        stage.addView(glassSrc, new FrameLayout.LayoutParams(-1, -1));
        FloatingNav nav = new FloatingNav(this, glassSrc,
                new int[]{R.drawable.ic_music, R.drawable.ic_list, R.drawable.ic_search, R.drawable.ic_settings},
                new int[]{R.string.tab_songs, R.string.tab_playlists, R.string.search, R.string.settings});
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, Ui.dp(this, 64), android.view.Gravity.BOTTOM);
        nlp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        stage.addView(nav, nlp);
        nav.select(3, false);
        nav.setListener((i, again) -> {
            if (i == 3) {
                sv.smoothScrollTo(0, 0);
                return;
            }
            startActivity(MainActivity.tabIntent(this, i));
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> Glass.boost());
        setContentView(stage);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.color(this, R.color.bg)));
        Ui.edgeToEdge(this, root, false, (bottom, ime) -> {
            nlp.bottomMargin = Ui.dp(this, 12) + bottom;
            nav.setLayoutParams(nlp);
            col.setPadding(0, 0, 0, Ui.dp(this, 120) + bottom);
        });
        if (b == null) {
            root.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
                for (int i = 0; i < Math.min(col.getChildCount(), 8); i++) Ui.enter(col.getChildAt(i), 30L * i);
            });
        }

        col.addView(IosUi.title(this, getString(R.string.settings)));
        col.addView(IosUi.section(this, R.string.set_appearance));
        col.addView(appearanceCard());
        col.addView(IosUi.section(this, R.string.set_glass));
        col.addView(glassCard());

        // ---- general
        col.addView(IosUi.section(this, R.string.set_general));
        LinearLayout gen = group(col);
        gen.addView(IosUi.row(this, R.drawable.ic_language, getString(R.string.language), langLabel(), null, false, v -> pickLang()));
        gen.addView(IosUi.row(this, R.drawable.ic_play, getString(R.string.default_app), getString(R.string.default_app_sub), null, false, v -> pickDefault()));
        gen.addView(IosUi.row(this, R.drawable.ic_music, getString(R.string.app_icon), getString(IconSwitcher.NAME[IconSwitcher.current(this)]), null, false,
                v -> Ui.go(this, new android.content.Intent(this, IconActivity.class))));
        gen.addView(IosUi.row(this, R.drawable.ic_refresh, getString(R.string.upd_title),
                getString(R.string.upd_row_sub, Updater.current(this)), null, false, v -> UpdateSheet.show(this)));
        IosUi.group(this, gen);

        final java.util.ArrayDeque<Runnable> steps = new java.util.ArrayDeque<>();

        // ---- formats
        steps.add(() -> {
        col.addView(IosUi.section(this, R.string.set_formats));
        LinearLayout fm = group(col);
        String[] names = getResources().getStringArray(R.array.fmt_names);
        String[] subs = getResources().getStringArray(R.array.fmt_subs);
        final boolean[] fmtOn = new boolean[Store.FORMATS.length];
        for (int i = 0; i < fmtOn.length; i++) fmtOn[i] = Store.formatOn(this, Store.FORMATS[i]);
        fm.addView(Ui.chipGrid(this, names, subs, fmtOn, (i, want, count) -> {
            if (!want && count <= 1) {
                Ui.toast(this, R.string.fmt_min_one);
                return false;
            }
            Store.setFormatOn(this, Store.FORMATS[i], want);
            dirty = true;
            return true;
        }));
        col.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        LinearLayout lib = group(col);
        lib.addView(IosUi.row(this, R.drawable.ic_timer, getString(R.string.set_min_dur), minLabel(), null, false, v -> pickMin()));
        lib.addView(IosUi.toggle(this, getString(R.string.smart_filter), getString(R.string.smart_filter_sub),
                Store.smartFilter(this), (v, on) -> {
                    Store.setFlag(this, "smart_filter", on);
                    Library.scan(this, null);
                }));
        lib.addView(IosUi.row(this, R.drawable.ic_folder, getString(R.string.hide_folders), hiddenLabel(), null, false, v -> pickHiddenFolders()));
        lib.addView(IosUi.row(this, R.drawable.ic_refresh, getString(R.string.rescan), getString(R.string.rescan_sub), null, false, v -> {
            Library.scan(this, () -> {
                if (isFinishing() || isDestroyed()) return;
                Ui.toast(this, getResources().getQuantityString(R.plurals.songs_n, Library.tracks.size(), Library.tracks.size()));
            });
            dirty = false;
        }));
        if (CrashGuard.has(this)) {
            lib.addView(IosUi.row(this, R.drawable.ic_info, getString(R.string.crash_row), getString(R.string.crash_row_sub), null, false,
                    v -> CrashGuard.share(this)));
        }
        IosUi.group(this, lib);

        });

        // ---- local AI
        steps.add(() -> {
        col.addView(IosUi.section(this, R.string.set_ai));
        LinearLayout ai = group(col);
        ai.addView(IosUi.toggle(this, getString(R.string.ai_enable), getString(R.string.ai_enable_sub),
                Store.aiEnabled(this), (v, on) -> {
                    Store.setAiEnabled(this, on);
                    if (on) AiEngine.generate(this);
                    recreate();
                }));
        if (Store.aiEnabled(this)) {
            ai.addView(IosUi.row(this, R.drawable.ic_refresh, getString(R.string.ai_regenerate), getString(R.string.ai_regenerate_sub),
                    null, false, v -> {
                        AiEngine.generate(this);
                        Ui.toast(this, R.string.ai_regenerated);
                    }));
        }
        IosUi.group(this, ai);

        });

        // ---- playback
        steps.add(() -> {
        col.addView(IosUi.section(this, R.string.set_playback));
        LinearLayout pb = group(col);
        pb.addView(IosUi.row(this, R.drawable.ic_volume, getString(R.string.volume_title), getString(R.string.volume_row_sub),
                VolumeHud.label(this), false, v -> VolumeSheet.show(this)));
        pb.addView(IosUi.toggle(this, getString(R.string.boost_title), getString(R.string.boost_sub),
                Store.boostOn(this), (v, on) -> Store.setFlag(this, "boost_on", on)));
        pb.addView(IosUi.toggle(this, getString(R.string.own_volume_title), getString(R.string.own_volume_sub),
                Store.ownVolume(this), (v, on) -> Store.setFlag(this, "own_volume", on)));
        pb.addView(IosUi.toggle(this, getString(R.string.skip_silence), getString(R.string.skip_silence_sub),
                Store.flag(this, "skip_silence", false), (v, on) -> Store.setFlag(this, "skip_silence", on)));
        pb.addView(IosUi.row(this, R.drawable.ic_timer, getString(R.string.crossfade_title), getString(R.string.crossfade_sub),
                crossfadeLabel(), false, v -> pickCrossfade()));
        pb.addView(IosUi.toggle(this, getString(R.string.sleep_shake_title), getString(R.string.sleep_shake_sub),
                Store.flag(this, "sleep_shake", true), (v, on) -> Store.setFlag(this, "sleep_shake", on)));
        pb.addView(IosUi.toggle(this, getString(R.string.autoplay_title), getString(R.string.autoplay_sub),
                Store.autoplay(this), (v, on) -> Store.setFlag(this, "autoplay", on)));
        pb.addView(IosUi.toggle(this, getString(R.string.resume_title), getString(R.string.resume_sub),
                Store.flag(this, "resume", true), (v, on) -> Store.setFlag(this, "resume", on)));
        pb.addView(IosUi.toggle(this, getString(R.string.stop_close_title), getString(R.string.stop_close_sub),
                Store.flag(this, "stop_on_close", true), (v, on) -> Store.setFlag(this, "stop_on_close", on)));
        IosUi.group(this, pb);

        });

        // ---- lock screen
        steps.add(() -> {
        col.addView(IosUi.section(this, R.string.set_lock));
        LinearLayout lk = group(col);
        lk.addView(IosUi.toggle(this, getString(R.string.lock_auto), getString(R.string.lock_auto_sub),
                Store.flag(this, "lock_auto", true), (v, on) -> Store.setFlag(this, "lock_auto", on)));
        lk.addView(IosUi.toggle(this, getString(R.string.lock_wake), getString(R.string.lock_wake_sub),
                Store.flag(this, "lock_wake", false), (v, on) -> Store.setFlag(this, "lock_wake", on)));
        lk.addView(IosUi.toggle(this, getString(R.string.lock_blur), getString(R.string.lock_blur_sub),
                Store.flag(this, "lock_blur", true), (v, on) -> Store.setFlag(this, "lock_blur", on)));
        lk.addView(IosUi.row(this, R.drawable.ic_timer, getString(R.string.lock_test), getString(R.string.lock_test_sub),
                null, false, v -> {
                    if (LockLauncher.ready(this)) {
                        LockLauncher.testIn(this, 6);
                        Ui.toast(this, R.string.lock_test_toast);
                    } else {
                        Ui.toast(this, R.string.lock_test_missing);
                    }
                }));
        lk.addView(IosUi.row(this, R.drawable.ic_lock, getString(R.string.lock_preview), getString(R.string.lock_preview_sub),
                null, false, v -> Menus.openLock(this)));
        IosUi.group(this, lk);

        });

        // ---- permissions
        steps.add(() -> {
            col.addView(IosUi.section(this, R.string.set_perms));
            perms = group(col);
            fillPerms();
        });

        final android.view.Choreographer ch = android.view.Choreographer.getInstance();
        final android.view.Choreographer.FrameCallback[] pump = new android.view.Choreographer.FrameCallback[1];
        pump[0] = t -> {
            if (isFinishing() || isDestroyed()) return;
            Runnable r = steps.poll();
            if (r == null) return;
            try {
                r.run();
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("settings section", e);
            }
            if (!steps.isEmpty()) ch.postFrameCallback(pump[0]);
        };
        ch.postFrameCallback(f -> ch.postFrameCallback(pump[0]));   // the first screenful is drawn before the rest is built
    }

    private LinearLayout group(LinearLayout parent) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        parent.addView(g, new LinearLayout.LayoutParams(-1, -2));
        return g;
    }

    private String langLabel() {
        String v = Lang.saved(this);
        return "ar".equals(v) ? "العربية" : "en".equals(v) ? "English" : getString(R.string.lang_system);
    }

    private void pickLang() {
        final String[] codes = {"", "ar", "en"};
        final String[] names = {getString(R.string.lang_system), "العربية", "English"};
        Sheet.show(this, getString(R.string.language), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            for (int i = 0; i < codes.length; i++) {
                final String code = codes[i];
                l.add(Sheet.item(R.drawable.ic_language, names[i], Lang.saved(this).equals(code), false, () -> Lang.set(this, code)));
            }
            return l;
        });
    }

    private String minLabel() {
        int s = Store.minDur(this);
        return s == 0 ? getString(R.string.min_none) : getString(R.string.min_sec, s);
    }

    private void pickMin() {
        final int[] secs = {0, 15, 30, 60, 120};
        Sheet.show(this, getString(R.string.set_min_dur), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            CharSequence[] labels = new CharSequence[secs.length];
            int sel = -1;
            for (int i = 0; i < secs.length; i++) {
                labels[i] = secs[i] == 0 ? getString(R.string.min_none) : getString(R.string.min_sec, secs[i]);
                if (Store.minDur(this) == secs[i]) sel = i;
            }
            l.add(Sheet.chips(labels, sel, 2, i -> {
                Store.setMinDur(this, secs[i]);
                dirty = true;
                recreate();
            }));
            return l;
        });
    }

    private String crossfadeLabel() {
        int s = Store.crossfade(this);
        return s == 0 ? getString(R.string.crossfade_off) : getString(R.string.crossfade_secs, s);
    }

    private void pickCrossfade() {
        final int[] secs = {0, 2, 4, 6, 8, 12};
        Sheet.show(this, getString(R.string.crossfade_title), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            CharSequence[] labels = new CharSequence[secs.length];
            int sel = -1;
            for (int i = 0; i < secs.length; i++) {
                labels[i] = secs[i] == 0 ? getString(R.string.crossfade_off) : getString(R.string.crossfade_secs, secs[i]);
                if (Store.crossfade(this) == secs[i]) sel = i;
            }
            l.add(Sheet.chips(labels, sel, 3, i -> {
                Store.setCrossfade(this, secs[i]);
                recreate();
            }));
            return l;
        });
    }

    private String hiddenLabel() {
        int n = Store.hiddenFolders(this).size();
        return n == 0 ? getString(R.string.no_folders_hidden) : getResources().getQuantityString(R.plurals.folders_hidden_n, n, n);
    }

    /** Checklist of every folder with songs in it; unchecking one hides its songs everywhere, including playlists. */
    private void pickHiddenFolders() {
        Sheet.show(this, getString(R.string.hide_folders), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            List<String> all = Library.allFolders;
            if (all.isEmpty()) {
                l.add(Sheet.item(R.drawable.ic_folder, getString(R.string.hide_folders_empty), false, false, () -> {
                }));
                return l;
            }
            java.util.Set<String> hidden = Store.hiddenFolders(this);
            for (final String f : all) {
                boolean shown = !hidden.contains(f);
                l.add(Sheet.item(R.drawable.ic_folder, f, shown, true, () -> {
                    java.util.Set<String> h = new java.util.HashSet<>(Store.hiddenFolders(this));
                    if (!h.add(f)) h.remove(f);
                    Store.setHiddenFolders(this, h);
                    dirty = true;
                }));
            }
            return l;
        });
    }

    private int permSignature() {
        boolean[] f = {Perms.hasAudio(this), Perms.hasNotif(this),
                Build.VERSION.SDK_INT >= 34 && Perms.hasFsi(this), Perms.hasOverlay(this), Perms.hasLockChannel(this),
                Perms.hasBattery(this), Perms.hasWriteSettings(this), Build.VERSION.SDK_INT >= 31 && Perms.hasBt(this)};
        int sig = 1;
        for (boolean v : f) sig = (sig << 1) | (v ? 1 : 0);
        return sig;
    }

    private void fillPerms() {
        int sig = permSignature();
        if (sig == permSig && perms.getChildCount() > 0) return;
        permSig = sig;
        perms.removeAllViews();
        addPerm(R.drawable.ic_folder, R.string.perm_audio, R.string.perm_audio_sub, Perms.hasAudio(this), v -> Perms.askAudio(this));
        addPerm(R.drawable.ic_star, R.string.perm_notif, R.string.perm_notif_sub, Perms.hasNotif(this), v -> Perms.askNotif(this));
        if (Build.VERSION.SDK_INT >= 34) {
            addPerm(R.drawable.ic_lock, R.string.perm_fsi, R.string.perm_fsi_sub, Perms.hasFsi(this), v -> Perms.askFsi(this));
        }
        addPerm(R.drawable.ic_list, R.string.perm_overlay, R.string.perm_overlay_sub, Perms.hasOverlay(this), v -> Perms.askOverlay(this));
        addPerm(R.drawable.ic_star, R.string.perm_chan, R.string.perm_chan_sub, Perms.hasLockChannel(this), v -> Perms.askLockChannel(this));
        addPerm(R.drawable.ic_shield, R.string.perm_battery, R.string.perm_battery_sub, Perms.hasBattery(this), v -> Perms.askBattery(this));
        addPerm(R.drawable.ic_ring, R.string.perm_write_settings, R.string.perm_write_settings_sub, Perms.hasWriteSettings(this), v -> Perms.askWriteSettings(this));
        if (Build.VERSION.SDK_INT >= 31) {
            addPerm(R.drawable.ic_music, R.string.perm_bt, R.string.perm_bt_sub, Perms.hasBt(this), v -> Perms.askBt(this));
        }
        // vendor switches that Android cannot report: shown as "Open" so the user can check them once
        if (Oem.xiaomi()) {
            addOem(R.drawable.ic_lock, R.string.perm_xiaomi, R.string.perm_xiaomi_sub, v -> Oem.xiaomiPermissions(this));
            addOem(R.drawable.ic_refresh, R.string.perm_autostart, R.string.perm_autostart_sub, v -> Oem.xiaomiAutostart(this));
        }
        if (Oem.xiaomi() || Oem.samsung()) {
            addOem(R.drawable.ic_shield, R.string.perm_battery_oem, R.string.perm_battery_oem_sub, v -> Oem.battery(this));
        }
        IosUi.group(this, perms);
    }

    private void addOem(int icon, int title, int sub, View.OnClickListener click) {
        perms.addView(IosUi.row(this, icon, getString(title), getString(sub), getString(R.string.perm_open), false, click));
    }

    private void addPerm(int icon, int title, int sub, boolean ok, View.OnClickListener click) {
        perms.addView(IosUi.row(this, icon, getString(title), getString(sub),
                getString(ok ? R.string.perm_on : R.string.perm_enable), ok, ok ? null : click));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (perms != null) fillPerms();
    }

    @Override
    protected void onPause() {
        if (dirty) {
            dirty = false;
            Library.scan(this, null);
        }
        super.onPause();
    }
}
