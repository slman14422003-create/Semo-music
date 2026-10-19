package com.simomusic.player;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LyricsActivity extends AppCompatActivity implements FullBleed {
    private static final long LOOKAHEAD = 140;
    private static final long RESUME_MS = 3600;
    private static final int S_LOAD = 0, S_MISS = 1, S_OFFLINE = 2, S_INSTR = 3, S_EMPTY = 4;
    private static final int[] SIZES = {22, 26, 31};

    private Context d;
    private ImageView bg;
    private TextView titleView, artistView, badge, curView, totalView, syncPill;
    private RecyclerView rv;
    private LinearLayoutManager lm;
    private LineAdapter ad;
    private LinearLayout stateBox;
    private ImageButton playBtn;
    private TrackBar bar;
    private ObjectAnimator pulse;
    private LyricsRepo.Lyrics lyrics;
    private LyricsRepo.Query lastQuery;
    private volatile String currentKey = "";
    private android.net.Uri audioUri;
    private volatile boolean aiBusy;
    private TextView loadCap;
    private int stageRes;
    private long stageSince;
    private final Runnable stageTick = new Runnable() {
        @Override
        public void run() {
            if (!aiBusy || loadCap == null || isDestroyed() || stageRes == 0) return;
            long s = (android.os.SystemClock.elapsedRealtime() - stageSince) / 1000;
            loadCap.setText(getString(stageRes) + String.format(java.util.Locale.US, "  %d:%02d", s / 60, s % 60));
            h.postDelayed(this, 1000);
        }
    };

    private void setStage(int res) {
        stageRes = res;
        stageSince = android.os.SystemClock.elapsedRealtime();
        h.removeCallbacks(stageTick);
        stageTick.run();
    }
    private long durMs, lastSec = -1, lastTotal = -1;
    private int active = -1;
    private boolean following = true, started, ticking;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable resume = new Runnable() {
        @Override
        public void run() {
            following = true;
            showPill(false);
            centerOn(active, true);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            update();
            schedule();
        }
    };

    private final Player.Listener pl = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem item, int reason) {
            bind(false);
        }

        @Override
        public void onIsPlayingChanged(boolean playing) {
            syncPlayIcon();
            schedule();
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo o, Player.PositionInfo n, int reason) {
            update();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.transitions(this, R.anim.act_open_enter, R.anim.act_open_exit, R.anim.act_close_enter, R.anim.act_close_exit);
        Pb.connect(this);
        d = Ui.dark(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF000000));

        FrameLayout root = new FrameLayout(d);
        root.setBackgroundResource(R.drawable.bg_default_backdrop);
        bg = Backdrop.view(d);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(d);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(d);
        dim.setBackgroundColor(0x40000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(d);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(d);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(d, 12), Ui.dp(d, 8), Ui.dp(d, 12), Ui.dp(d, 4));
        top.addView(Ui.icon(d, R.drawable.ic_arrow_down, R.string.close, v -> finish()));
        LinearLayout titles = new LinearLayout(d);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER);
        titleView = Ui.text(d, "", 17, R.color.text_primary);
        titleView.setTypeface(Ui.titleFace(true));
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        artistView = Ui.text(d, "", 13, R.color.text_secondary);
        artistView.setSingleLine(true);
        artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artistView.setGravity(Gravity.CENTER);
        titles.addView(titleView, Ui.lp(-1, -2));
        titles.addView(artistView, Ui.lp(-1, -2));
        LinearLayout.LayoutParams tl = Ui.weight(1);
        tl.setMargins(Ui.dp(d, 10), 0, Ui.dp(d, 10), 0);
        top.addView(titles, tl);
        top.addView(Ui.icon(d, R.drawable.ic_more, R.string.more, v -> menu()));
        col.addView(top, Ui.lp(-1, -2));

        FrameLayout stage = new FrameLayout(d);
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        rv = new RecyclerView(d);
        lm = new LinearLayoutManager(d);
        rv.setLayoutManager(lm);
        ad = new LineAdapter();
        rv.setAdapter(ad);
        rv.setItemAnimator(null);
        rv.setClipToPadding(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setItemViewCacheSize(0);
        rv.setVerticalFadingEdgeEnabled(true);
        rv.setFadingEdgeLength(Ui.dp(d, 64));
        rv.setAlpha(0f);
        rv.addOnLayoutChangeListener((v, l, t, r, bt, oL, oT, oR, oB) -> {
            int hh = bt - t;
            int pt = (int) (hh * 0.30f), pb = (int) (hh * 0.50f);
            if (rv.getPaddingTop() != pt || rv.getPaddingBottom() != pb) rv.setPadding(0, pt, 0, pb);
        });
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView r, int s) {
                if (lyrics == null || !lyrics.synced) return;
                if (s == RecyclerView.SCROLL_STATE_DRAGGING) {
                    following = false;
                    h.removeCallbacks(resume);
                    showPill(true);
                } else if (s == RecyclerView.SCROLL_STATE_IDLE && !following) {
                    h.removeCallbacks(resume);
                    h.postDelayed(resume, RESUME_MS);
                }
            }
        });
        stage.addView(rv, new FrameLayout.LayoutParams(-1, -1));

        badge = Ui.text(d, getString(R.string.lyrics_not_synced), 12, R.color.text_secondary);
        badge.setPadding(Ui.dp(d, 12), Ui.dp(d, 5), Ui.dp(d, 12), Ui.dp(d, 5));
        badge.setBackgroundResource(R.drawable.bg_pill_glass);
        badge.setGravity(Gravity.CENTER);
        badge.setMaxWidth(Ui.dp(d, 320));
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = Ui.dp(d, 6);
        stage.addView(badge, bp);
        badge.setOnClickListener(v -> startAiSync());

        stateBox = new LinearLayout(d);
        stateBox.setOrientation(LinearLayout.VERTICAL);
        stateBox.setGravity(Gravity.CENTER);
        stateBox.setPadding(Ui.dp(d, 32), 0, Ui.dp(d, 32), Ui.dp(d, 40));
        stateBox.setVisibility(View.GONE);
        stage.addView(stateBox, new FrameLayout.LayoutParams(-1, -1));

        syncPill = Ui.text(d, getString(R.string.lyrics_sync), 14, R.color.text_primary);
        syncPill.setTypeface(Ui.medium());
        syncPill.setGravity(Gravity.CENTER);
        syncPill.setPadding(Ui.dp(d, 18), Ui.dp(d, 10), Ui.dp(d, 18), Ui.dp(d, 10));
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0x66000000);
        pg.setStroke(Ui.dp(d, 1), 0x33FFFFFF);
        pg.setCornerRadius(Ui.dp(d, 100));
        syncPill.setBackground(pg);
        syncPill.setAlpha(0f);
        syncPill.setVisibility(View.GONE);
        syncPill.setOnClickListener(v -> {
            Ui.tap(v);
            h.removeCallbacks(resume);
            resume.run();
        });
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = Ui.dp(d, 14);
        stage.addView(syncPill, sp);

        LinearLayout bottom = new LinearLayout(d);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(Ui.dp(d, 24), Ui.dp(d, 2), Ui.dp(d, 24), Ui.dp(d, 14));
        LinearLayout timeRow = new LinearLayout(d);
        timeRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        curView = Ui.text(d, "0:00", 12, R.color.text_secondary);
        totalView = Ui.text(d, "0:00", 12, R.color.text_secondary);
        bar = new TrackBar(d);
        timeRow.addView(curView);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, Ui.dp(d, 36), 1f);
        barLp.setMargins(Ui.dp(d, 10), 0, Ui.dp(d, 10), 0);
        timeRow.addView(bar, barLp);
        timeRow.addView(totalView);
        bottom.addView(timeRow, Ui.lp(-1, -2));

        LinearLayout ctl = new LinearLayout(d);
        ctl.setGravity(Gravity.CENTER);
        ImageButton prev = Ui.flat(d, R.drawable.ic_prev, 60, R.string.previous, v -> {
            MediaController m = Pb.get();
            if (m != null) m.seekToPrevious();
        });
        playBtn = Ui.bigPlay(d, v -> {
            MediaController m = Pb.get();
            if (m == null) return;
            if (m.isPlaying()) m.pause();
            else m.play();
        });
        ImageButton next = Ui.flat(d, R.drawable.ic_next, 60, R.string.next, v -> {
            MediaController m = Pb.get();
            if (m != null) m.seekToNext();
        });
        View[] set = {prev, playBtn, next};
        for (int i = 0; i < set.length; i++) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) set[i].getLayoutParams();
            if (i > 0) p.setMarginStart(Ui.dp(d, 18));
            ctl.addView(set[i], p);
        }
        LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
        cp.topMargin = Ui.dp(d, 2);
        bottom.addView(ctl, cp);
        col.addView(bottom, Ui.lp(-1, -2));

        setContentView(root);
        Ui.edgeToEdge(this, col);
        Ui.bars(this, true);
    }

    @Override
    public void finish() {
        super.finish();
        Ui.legacyClose(this, R.anim.act_close_enter, R.anim.act_close_exit);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        Pb.connect(this);
        Pb.add(pl);
        Pb.whenReady(() -> {
            if (!started) return;
            bind(false);
            syncPlayIcon();
            update();
            schedule();
        });
    }

    @Override
    protected void onStop() {
        started = false;
        Pb.remove(pl);
        h.removeCallbacks(tick);
        h.removeCallbacks(resume);
        ticking = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        if (aiBusy) WhisperEngine.cancel();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ data

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString();
    }

    private void bind(boolean force) {
        MediaController m = Pb.get();
        if (m == null) return;
        MediaItem it = m.getCurrentMediaItem();
        if (it == null) {
            currentKey = "";
            lyrics = null;
            ad.set(new String[0], false);
            titleView.setText("");
            artistView.setText("");
            showState(S_EMPTY);
            return;
        }
        long id = Pb.currentId();
        Track t = id >= 0 ? Library.byId.get(id) : null;
        String title = t != null ? t.title : Fmt.fix(str(it.mediaMetadata.title));
        String artist = t != null ? t.artist : Fmt.fix(str(it.mediaMetadata.artist));
        String album = t != null ? t.album : str(it.mediaMetadata.albumTitle);
        long dur = t != null && t.duration > 0 ? t.duration : m.getDuration();
        String key = t != null ? "t" + id : "m" + Integer.toHexString((title + "|" + artist).hashCode());
        if (!force && key.equals(currentKey)) return;
        currentKey = key;
        durMs = dur > 0 ? dur : 0;
        audioUri = t != null ? t.uri : (it.localConfiguration != null ? it.localConfiguration.uri : null);
        titleView.setText(title);
        artistView.setText(Fmt.artist(d, artist));
        android.net.Uri art = it.mediaMetadata.artworkUri != null ? it.mediaMetadata.artworkUri : (t != null ? t.uri : null);
        Backdrop.set(this, bg, art, 256);
        lastQuery = LyricsRepo.Query.of(key, title, artist, album, durMs);
        fetch(lastQuery, force);
    }

    private void fetch(LyricsRepo.Query q, boolean force) {
        final String k = q.key;
        lyrics = null;
        active = -1;
        h.removeCallbacks(resume);
        following = true;
        showPill(false);
        badge.setVisibility(View.GONE);
        rv.animate().cancel();
        rv.setAlpha(0f);
        ad.set(new String[0], false);
        showState(S_LOAD);
        LyricsRepo.load(getApplicationContext(), q, force, () -> !k.equals(currentKey) || isDestroyed(), (l, status) -> {
            if (!k.equals(currentKey) || isDestroyed()) return;
            apply(l, status);
        });
    }

    private void apply(LyricsRepo.Lyrics l, int status) {
        if (l != null && !l.synced && !l.instrumental && l.lines.length > 0) l = LyricsRepo.estimate(l, durMs);
        lyrics = l;
        if (l != null && l.instrumental) {
            showState(S_INSTR);
            return;
        }
        if (l == null || l.lines.length == 0) {
            showState(status == LyricsRepo.OFFLINE ? S_OFFLINE : S_MISS);
            return;
        }
        hideState();
        MediaController m = Pb.get();
        long pos = m != null ? Math.max(0, m.getCurrentPosition()) : 0;
        int idx = l.synced ? indexFor(pos + LOOKAHEAD) : -1;
        active = idx;
        following = true;
        boolean aiOk = WhisperEngine.supported() && !aiBusy && audioUri != null && l.lines.length >= 4
                && (l.estimated || !l.synced) && !"ai".equals(l.source);
        String base = getString(l.estimated ? R.string.lyrics_estimated : R.string.lyrics_not_synced);
        boolean gen = "ai-gen".equals(l.source);
        badge.setText(gen ? getString(R.string.ai_gen_note) : (aiOk ? base + "\n" + getString(R.string.ai_sync_tap) : base));
        badge.setClickable(aiOk);
        badge.setVisibility(gen || l.estimated || !l.synced ? View.VISIBLE : View.GONE);
        ad.set(l.lines, l.synced);
        rv.setAlpha(0f);
        if (idx >= 0) {
            jump(idx, this::fadeIn);
        } else {
            rv.scrollToPosition(0);
            fadeIn();
        }
        schedule();
    }

    // ------------------------------------------------------------------ AI sync

    private void startAiSync() {
        final LyricsRepo.Lyrics cur = lyrics;
        if (cur == null || aiBusy || audioUri == null || !WhisperEngine.supported()) return;
        if (WhisperEngine.hasModel(this)) {
            runAiSync(cur);
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(getString(R.string.ai_sync_download_q, WhisperEngine.MODEL_MB))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ai_sync_download, (dlg, w) -> downloadThenRun(() -> runAiSync(cur)))
                .show();
    }

    private void downloadThenRun(Runnable next) {
        aiBusy = true;
        final String k = currentKey;
        badge.setClickable(false);
        badge.setVisibility(View.VISIBLE);
        badge.setText(getString(R.string.ai_sync_downloading, 0));
        Thread t = new Thread(() -> {
            boolean ok = WhisperEngine.download(getApplicationContext(),
                    p -> h.post(() -> {
                        if (!isDestroyed()) badge.setText(getString(R.string.ai_sync_downloading, p));
                    }),
                    () -> isDestroyed());
            h.post(() -> {
                aiBusy = false;
                if (isDestroyed()) return;
                if (!ok) {
                    Ui.toast(this, R.string.ai_sync_dl_failed);
                    if (lyrics != null) apply(lyrics, LyricsRepo.OK);
                    else {
                        badge.setVisibility(View.GONE);
                        showState(S_MISS);
                    }
                } else if (k.equals(currentKey)) {
                    next.run();
                }
            });
        }, "whisper-dl");
        t.setDaemon(true);
        t.start();
    }

    private void runAiSync(LyricsRepo.Lyrics cur) {
        aiBusy = true;
        final String k = currentKey;
        badge.setClickable(false);
        badge.setText(R.string.ai_sync_working);
        Ui.toast(this, R.string.ai_sync_working);
        AiSync.run(getApplicationContext(), k, audioUri, cur, durMs, WhisperEngine.guessLang(cur.lines),
                new WhisperEngine(), () -> isDestroyed() || !k.equals(currentKey), (l, status) -> {
                    aiBusy = false;
                    if (isDestroyed() || !k.equals(currentKey)) return;
                    if (status == AiSync.DONE && l != null) {
                        Ui.toast(this, R.string.ai_sync_done);
                        apply(l, LyricsRepo.OK);
                    } else {
                        Ui.toast(this, status == AiSync.WEAK ? R.string.ai_sync_weak : R.string.ai_sync_failed);
                        if (lyrics != null) apply(lyrics, LyricsRepo.OK);
                    }
                });
    }

    // ------------------------------------------------------------------ AI lyrics (no lyrics found online)

    private static final String KEY_PREF = "groq_key";

    private void startGenerate() {
        if (aiBusy || audioUri == null) return;
        CharSequence[] items = {getString(R.string.ai_gen_online), getString(R.string.ai_gen_phone)};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.ai_gen_choose)
                .setItems(items, (dlg, which) -> {
                    if (which == 0) generateOnline();
                    else generateOnPhone();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void generateOnline() {
        String key = Store.prefs(this).getString(KEY_PREF, "");
        if (key != null && !key.trim().isEmpty()) {
            runGenerate(key.trim());
            return;
        }
        askKey();
    }

    private void askKey() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(R.string.ai_gen_key_hint);
        et.setSingleLine(true);
        et.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.ai_gen_key_title)
                .setMessage(R.string.ai_gen_key_msg)
                .setView(et)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    String k = et.getText().toString().trim();
                    if (k.isEmpty()) return;
                    Store.prefs(this).edit().putString(KEY_PREF, k).apply();
                    runGenerate(k);
                })
                .show();
    }

    private void generateOnPhone() {
        if (!WhisperEngine.supported()) {
            Ui.toast(this, R.string.ai_sync_failed);
            return;
        }
        if (WhisperEngine.hasModel(this)) {
            runGenerate(null);
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(getString(R.string.ai_sync_download_q, WhisperEngine.MODEL_MB))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ai_sync_download, (dlg, w) -> downloadThenRun(() -> runGenerate(null)))
                .show();
    }

    /** apiKey null = on this phone. Writes lyrics from the song itself and syncs them. */
    private void runGenerate(String apiKey) {
        aiBusy = true;
        final String k = currentKey;
        badge.setVisibility(View.GONE);
        showState(S_LOAD);
        Ui.toast(this, R.string.ai_gen_working);
        stageRes = 0;
        AiLyrics.run(getApplicationContext(), k, audioUri, durMs, null, apiKey,
                () -> isDestroyed() || !k.equals(currentKey),
                st -> {
                    if (isDestroyed() || !aiBusy) return;
                    setStage(st == AiLyrics.STAGE_DECODE ? R.string.ai_stage_decode
                            : st == AiLyrics.STAGE_ONLINE ? R.string.ai_stage_online : R.string.ai_stage_phone);
                },
                (l, status) -> {
                    aiBusy = false;
                    stageRes = 0;
                    h.removeCallbacks(stageTick);
                    if (isDestroyed() || !k.equals(currentKey)) return;
                    if (status == AiLyrics.DONE && l != null) {
                        Ui.toast(this, R.string.ai_gen_done);
                        apply(l, LyricsRepo.OK);
                        return;
                    }
                    int msg = status == AiLyrics.BAD_KEY ? R.string.ai_gen_bad_key
                            : status == AiLyrics.LIMIT ? R.string.ai_gen_limit
                            : status == AiLyrics.WEAK ? R.string.ai_gen_weak : R.string.ai_sync_failed;
                    if (status == AiLyrics.BAD_KEY) Store.prefs(this).edit().remove(KEY_PREF).apply();
                    String why = AiLyrics.lastError;
                    Ui.toast(this, msg == R.string.ai_sync_failed && why != null ? getString(msg) + " (" + why + ")" : getString(msg));
                    showState(S_MISS);
                });
    }

    private void fadeIn() {
        rv.animate().alpha(1f).setDuration(Motion.LONG).setInterpolator(Motion.ENTER).start();
    }

    private int indexFor(long t) {
        LyricsRepo.Lyrics l = lyrics;
        if (l == null || !l.synced || l.times.length == 0) return -1;
        int lo = 0, hi = l.times.length - 1, res = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (l.times[mid] <= t) {
                res = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return res;
    }

    // ------------------------------------------------------------------ tracking

    private void schedule() {
        MediaController m = Pb.get();
        if (!started || ticking || m == null) return;
        ticking = true;
        h.postDelayed(tick, m.isPlaying() ? 60 : 400);
    }

    private void update() {
        MediaController m = Pb.get();
        if (m == null) return;
        long pos = Math.max(0, m.getCurrentPosition());
        long dur = m.getDuration();
        if (dur > 0) durMs = dur;
        bar.set(pos, durMs);
        if (lyrics == null || !lyrics.synced) return;
        int idx = indexFor(pos + LOOKAHEAD);
        if (idx != active) {
            setActive(idx);
            if (following && idx >= 0) centerOn(idx, true);
        }
    }

    private void setActive(int idx) {
        active = idx;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View v = rv.getChildAt(i);
            int p = rv.getChildAdapterPosition(v);
            if (p == RecyclerView.NO_POSITION) continue;
            float s = ad.scaleFor(p);
            v.animate().alpha(ad.alphaFor(p)).scaleX(s).scaleY(s).setDuration(Motion.BASE).setInterpolator(Motion.ENTER).start();
        }
    }

    private void centerOn(int pos, boolean smooth) {
        if (pos < 0 || pos >= ad.getItemCount()) return;
        int first = lm.findFirstVisibleItemPosition(), last = lm.findLastVisibleItemPosition();
        if (!smooth || first == RecyclerView.NO_POSITION || pos < first - 10 || pos > last + 10) {
            jump(pos, null);
            return;
        }
        CenterScroller s = new CenterScroller(d);
        s.setTargetPosition(pos);
        lm.startSmoothScroll(s);
    }

    private void jump(final int pos, final Runnable after) {
        lm.scrollToPositionWithOffset(pos, 0);
        rv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                rv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                View v = lm.findViewByPosition(pos);
                if (v != null) {
                    int box = rv.getHeight() - rv.getPaddingTop() - rv.getPaddingBottom();
                    int dy = v.getHeight() / 2 - box / 2;
                    if (dy != 0) rv.scrollBy(0, dy);
                }
                if (after != null) after.run();
            }
        });
        rv.requestLayout();
    }

    private void seekToLine(int pos, boolean forceSeek) {
        MediaController m = Pb.get();
        LyricsRepo.Lyrics l = lyrics;
        if (m == null || l == null || !l.synced || pos < 0 || pos >= l.times.length) return;
        Ui.confirm(rv);
        h.removeCallbacks(resume);
        following = true;
        showPill(false);
        if (l.estimated && !forceSeek) {
            long now = Math.max(0, m.getCurrentPosition());
            LyricsRepo.Lyrics n = LyricsRepo.anchored(l, pos, now, durMs);
            lyrics = n;
            LyricsRepo.store(getApplicationContext(), currentKey, n);
            setActive(pos);
            if (l.anchors == null) Ui.toast(this, R.string.lyrics_adjusted);
        } else {
            m.seekTo(l.times[pos]);
            setActive(pos);
        }
        centerOn(pos, true);
    }

    private void syncPlayIcon() {
        MediaController m = Pb.get();
        boolean playing = m != null && m.isPlaying();
        playBtn.setImageResource(playing ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
    }

    private void showPill(boolean show) {
        if (show) {
            if (syncPill.getVisibility() == View.VISIBLE && syncPill.getAlpha() >= 1f) return;
            syncPill.setVisibility(View.VISIBLE);
            syncPill.animate().cancel();
            syncPill.animate().alpha(1f).setDuration(Motion.SHORT).start();
        } else {
            if (syncPill.getVisibility() != View.VISIBLE) return;
            syncPill.animate().cancel();
            syncPill.animate().alpha(0f).setDuration(Motion.SHORT).withEndAction(() -> {
                if (syncPill.getAlpha() == 0f) syncPill.setVisibility(View.GONE);
            }).start();
        }
    }

    // ------------------------------------------------------------------ states

    private void hideState() {
        if (pulse != null) pulse.cancel();
        stateBox.animate().cancel();
        stateBox.setVisibility(View.GONE);
        stateBox.removeAllViews();
    }

    private View skeletonBar(int wDp) {
        View v = new View(d);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFFFFF);
        g.setCornerRadius(Ui.dp(d, 10));
        v.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(d, wDp), Ui.dp(d, 20));
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = Ui.dp(d, 14);
        v.setLayoutParams(p);
        return v;
    }

    private void showState(int type) {
        hideState();
        rv.animate().cancel();
        rv.setAlpha(0f);
        badge.setVisibility(View.GONE);
        if (type == S_LOAD) {
            LinearLayout sk = new LinearLayout(d);
            sk.setOrientation(LinearLayout.VERTICAL);
            int[] w = {220, 280, 180, 250, 200};
            for (int x : w) sk.addView(skeletonBar(x));
            stateBox.addView(sk, Ui.lp(-2, -2));
            TextView cap = Ui.text(d, getString(R.string.lyrics_loading), 14, R.color.text_secondary);
            cap.setGravity(Gravity.CENTER);
            loadCap = cap;
            LinearLayout.LayoutParams cl = Ui.lp(-2, -2);
            cl.gravity = Gravity.CENTER_HORIZONTAL;
            cl.topMargin = Ui.dp(d, 26);
            stateBox.addView(cap, cl);
            pulse = ObjectAnimator.ofFloat(stateBox, View.ALPHA, 0.45f, 1f);
            pulse.setDuration(Motion.PULSE);
            pulse.setRepeatCount(ObjectAnimator.INFINITE);
            pulse.setRepeatMode(ObjectAnimator.REVERSE);
            stateBox.setVisibility(View.VISIBLE);
            pulse.start();
            return;
        }
        int titleRes, subRes;
        if (type == S_OFFLINE) {
            titleRes = R.string.lyrics_offline;
            subRes = R.string.lyrics_offline_sub;
        } else if (type == S_INSTR) {
            titleRes = R.string.lyrics_instrumental;
            subRes = R.string.lyrics_instrumental_sub;
        } else if (type == S_EMPTY) {
            titleRes = R.string.lyrics_empty;
            subRes = R.string.lyrics_empty_sub;
        } else {
            titleRes = R.string.lyrics_none;
            subRes = R.string.lyrics_none_sub;
        }
        ImageView ic = new ImageView(d);
        ic.setImageResource(R.drawable.ic_lyrics);
        Ui.tint(ic, R.color.text_secondary);
        LinearLayout.LayoutParams ip = Ui.lp(Ui.dp(d, 52), Ui.dp(d, 52));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        stateBox.addView(ic, ip);
        TextView t = Ui.text(d, getString(titleRes), 19, R.color.text_primary);
        t.setTypeface(Ui.titleFace(true));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.topMargin = Ui.dp(d, 16);
        stateBox.addView(t, tp);
        TextView s = Ui.text(d, getString(subRes), 14, R.color.text_secondary);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(d, 6);
        stateBox.addView(s, sp);
        if (type == S_MISS || type == S_OFFLINE) {
            LinearLayout row = new LinearLayout(d);
            row.setGravity(Gravity.CENTER);
            Button retry = Ui.pill(d, R.string.lyrics_retry, R.drawable.ic_refresh, true, v -> bind(true));
            row.addView(retry, Ui.lp(-2, -2));
            if (type == S_MISS) {
                Button manual = Ui.pill(d, R.string.lyrics_search_manual, R.drawable.ic_search, false, v -> manualSearch());
                LinearLayout.LayoutParams mp = Ui.lp(-2, -2);
                mp.setMarginStart(Ui.dp(d, 10));
                row.addView(manual, mp);
            }
            LinearLayout.LayoutParams rp = Ui.lp(-2, -2);
            rp.gravity = Gravity.CENTER_HORIZONTAL;
            rp.topMargin = Ui.dp(d, 22);
            stateBox.addView(row, rp);
            if (type == S_MISS && audioUri != null) {
                Button gen = Ui.pill(d, R.string.ai_gen_action, R.drawable.ic_ai, true, v -> startGenerate());
                LinearLayout.LayoutParams gp = Ui.lp(-2, -2);
                gp.gravity = Gravity.CENTER_HORIZONTAL;
                gp.topMargin = Ui.dp(d, 12);
                stateBox.addView(gen, gp);
            }
        }
        stateBox.setAlpha(0f);
        stateBox.setVisibility(View.VISIBLE);
        stateBox.animate().alpha(1f).setDuration(Motion.BASE).start();
    }

    // ------------------------------------------------------------------ menu

    private void menu() {
        Sheet.show(this, getString(R.string.lyrics), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            l.add(Sheet.item(R.drawable.ic_search, getString(R.string.lyrics_search_manual), false, false, this::manualSearch));
            l.add(Sheet.item(R.drawable.ic_refresh, getString(R.string.lyrics_reload), false, false, () -> {
                if (lastQuery != null) LyricsRepo.forget(getApplicationContext(), lastQuery.key);
                bind(true);
            }));
            l.add(Sheet.divider());
            CharSequence[] labels = {getString(R.string.lyrics_size_s), getString(R.string.lyrics_size_m), getString(R.string.lyrics_size_l)};
            l.add(Sheet.chips(labels, sizeIndex(), 3, i -> {
                Store.prefs(this).edit().putInt("lyrics_size", i).apply();
                ad.notifyDataSetChanged();
                if (active >= 0) jump(active, null);
            }));
            return l;
        });
    }

    private int sizeIndex() {
        return Math.max(0, Math.min(2, Store.prefs(this).getInt("lyrics_size", 1)));
    }

    private void manualSearch() {
        String initial = lastQuery == null ? "" : (lastQuery.artist.isEmpty() ? lastQuery.title : lastQuery.artist + " - " + lastQuery.title);
        Dlg.input(this, R.string.lyrics_search_manual, R.string.lyrics_search_hint, initial, R.string.search, text -> {
            String artist = "", title = text.trim();
            int i = title.indexOf(" - ");
            if (i > 0) {
                artist = title.substring(0, i).trim();
                title = title.substring(i + 3).trim();
            }
            if (title.isEmpty()) return;
            lastQuery = LyricsRepo.Query.of(currentKey, title, artist, "", durMs);
            fetch(lastQuery, true);
        });
    }

    // ------------------------------------------------------------------ views

    private final class LineAdapter extends RecyclerView.Adapter<LineAdapter.VH> {
        private String[] lines = new String[0];
        private boolean synced;

        void set(String[] l, boolean s) {
            lines = l;
            synced = s;
            notifyDataSetChanged();
        }

        float alphaFor(int pos) {
            if (!synced) return 0.92f;
            if (active < 0) return 0.45f;
            int dist = Math.abs(pos - active);
            return dist == 0 ? 1f : dist == 1 ? 0.52f : dist == 2 ? 0.38f : 0.28f;
        }

        float scaleFor(int pos) {
            return synced && pos != active ? 0.94f : 1f;
        }

        @Override
        public int getItemCount() {
            return lines.length;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int type) {
            TextView tv = new TextView(d);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTypeface(Ui.titleFace(true));
            tv.setLineSpacing(0, 1.1f);
            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            tv.setPadding(Ui.dp(d, 28), Ui.dp(d, 11), Ui.dp(d, 28), Ui.dp(d, 11));
            tv.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            tv.addOnLayoutChangeListener((v, l, t, r, b, oL, oT, oR, oB) -> {
                v.setPivotX((r - l) / 2f);
                v.setPivotY((b - t) / 2f);
            });
            final VH vh = new VH(tv);
            tv.setOnClickListener(v -> {
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && synced) seekToLine(pos, false);
            });
            tv.setOnLongClickListener(v -> {
                int pos = vh.getBindingAdapterPosition();
                LyricsRepo.Lyrics cur = lyrics;
                if (pos == RecyclerView.NO_POSITION || cur == null || !cur.estimated) return false;
                seekToLine(pos, true);
                return true;
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH vh, int pos) {
            TextView tv = vh.tv;
            String s = lines[pos];
            boolean blank = s.isEmpty();
            tv.setTextSize(blank ? 18 : SIZES[sizeIndex()]);
            tv.setText(blank ? (synced ? "\u266A" : " ") : s);
            tv.animate().cancel();
            tv.setAlpha(alphaFor(pos));
            float sc = scaleFor(pos);
            tv.setScaleX(sc);
            tv.setScaleY(sc);
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView tv;

            VH(TextView v) {
                super(v);
                tv = v;
            }
        }
    }

    private static final class CenterScroller extends LinearSmoothScroller {
        CenterScroller(Context c) {
            super(c);
        }

        @Override
        public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
        }

        @Override
        protected int getVerticalSnapPreference() {
            return SNAP_TO_ANY;
        }

        @Override
        protected float calculateSpeedPerPixel(DisplayMetrics dm) {
            return 140f / dm.densityDpi;
        }

        @Override
        protected void onTargetFound(View target, RecyclerView.State state, Action action) {
            int dy = calculateDyToMakeVisible(target, getVerticalSnapPreference());
            if (dy == 0) return;
            int ms = (int) Math.max(280, Math.min(620, 240 + Math.sqrt(Math.abs(dy)) * 11));
            action.update(0, -dy, ms, Motion.ENTER);
        }
    }

    private final class TrackBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float frac;
        private boolean drag;

        TrackBar(Context c) {
            super(c);
        }

        void set(long pos, long dur) {
            if (drag) return;
            float f = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
            if (Math.abs(f - frac) > 0.0004f) {
                frac = f;
                invalidate();
            }
            long sec = pos / 1000;
            if (sec != lastSec) {
                lastSec = sec;
                curView.setText(Fmt.time(pos));
            }
            if (dur != lastTotal) {
                lastTotal = dur;
                totalView.setText(Fmt.time(dur));
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), hh = Ui.dp(getContext(), drag ? 8 : 5), cy = getHeight() / 2f;
            float rad = hh / 2f;
            p.setColor(0x33FFFFFF);
            r.set(0, cy - hh / 2f, w, cy + hh / 2f);
            c.drawRoundRect(r, rad, rad, p);
            p.setColor(0xFFFFFFFF);
            r.set(0, cy - hh / 2f, Math.max(hh, w * frac), cy + hh / 2f);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                drag = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                Ui.gestureStart(this);
            }
            if (!drag) return false;
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                frac = Math.max(0f, Math.min(1f, e.getX() / Math.max(1f, getWidth())));
                curView.setText(Fmt.time((long) (frac * durMs)));
                invalidate();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                drag = false;
                if (a == MotionEvent.ACTION_UP) {
                    MediaController m = Pb.get();
                    if (m != null && durMs > 0) m.seekTo((long) (frac * durMs));
                    Ui.gestureEnd(this);
                }
                lastSec = -1;
                invalidate();
                update();
            }
            return true;
        }
    }
}
