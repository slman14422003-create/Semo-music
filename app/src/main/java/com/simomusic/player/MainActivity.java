package com.simomusic.player;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Home: glass header, Songs / Playlists / Search pages, floating glass tab bar and the circular mini player. */
public class MainActivity extends AppCompatActivity implements TrackAdapter.Listener, FullBleed {
    /** Set by the media notification and by the lock screen: open the app with the full player already up. */
    public static final String ACTION_OPEN_PLAYER = "com.simomusic.player.OPEN_PLAYER";
    public static final String EXTRA_TAB = "tab";

    /** Opens the home screen on one of its pages (used by the tab bar of the other screens). */
    public static Intent tabIntent(android.content.Context c, int tab) {
        return new Intent(c, MainActivity.class).putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    /** An intent that brings the app to the front (reusing the running one) with the full player expanded. */
    public static Intent openPlayerIntent(android.content.Context c) {
        return new Intent(c, MainActivity.class).setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private TrackAdapter songs;
    private final ListState listState = new ListState();
    private String pendingShortcut;
    private final PlaylistAdapter lists = new PlaylistAdapter();
    private RecyclerView rv;
    private TextView titleView, subtitle, emptyText;
    private LinearLayout emptyBox, skeleton;
    private android.animation.ObjectAnimator pulse;
    private Button grant;
    private View actions, banner;
    private EditText search;
    private MiniPlayer mini;
    private PlayerPanel panel;
    private FrameLayout mainCol;
    private FloatingNav nav;
    private View sortBtn;
    private int insetBottom, imeBottom;
    private LinearLayout headerBar, selBar;
    private TextView selCount;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout srl;
    private androidx.activity.OnBackPressedCallback backCb;
    private int tab = 0;
    private boolean animateNext = true;
    private String query = "";
    private String[] queryTokens = new String[0];
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchRefresh = this::refresh;
    private List<Track> shown = new ArrayList<>();
    private FastScroll fast;
    private int listMode = -1;

    private final ActivityResultLauncher<String[]> askPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> rescan());

    /** The phone's songs changed (download, delete, copy…): redraw the list right away, no manual refresh. */
    private final Runnable libChanged = new Runnable() {
        @Override
        public void run() {
            if (srl != null && srl.isRefreshing()) srl.setRefreshing(false);
            int fresh = takeFresh();
            refresh();
            announce(fresh);
        }
    };

    private final java.util.Set<Long> known = new java.util.HashSet<>();
    private boolean knownInit;

    /** How many songs showed up since the last look that were added to the phone in the last 15 minutes. */
    private int takeFresh() {
        if (!Library.loaded) return 0;
        long cutoff = System.currentTimeMillis() / 1000 - 900;
        int fresh = 0;
        for (Track t : Library.tracks) {
            if (known.add(t.id) && knownInit && t.added > cutoff) fresh++;
        }
        knownInit = true;
        return fresh;
    }

    private void announce(int fresh) {
        if (fresh > 0 && !isFinishing() && !isDestroyed()) {
            Ui.toast(this, getResources().getQuantityString(R.plurals.songs_added, fresh, fresh));
        }
    }

    private final Player.Listener current = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem item, int reason) {
            songs.setCurrent(Pb.currentId());
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            songs.setPlaying(isPlaying);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setOnExitAnimationListener(p -> p.getView().animate().alpha(0f).scaleX(1.14f).scaleY(1.14f).setDuration(Motion.SHORT)
                .setInterpolator(Motion.EXIT).withEndAction(p::remove).start());
        super.onCreate(b);
        Pb.connect(this);
        build();
        if (!Perms.hasAudio(this) && !Store.flag(this, "first_done", false)) {
            Store.setFlag(this, "first_done", true);
            askPerms.launch(Perms.firstRun());
        } else {
            rescan();
        }
        handleIntent(getIntent());
        getWindow().getDecorView().post(() -> AppShortcuts.install(this));     // after the first frame, never in its way
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    /** Notification / lock-screen tap: show the home screen and slide the full player up over it. */
    private void handleIntent(Intent i) {
        if (i != null && i.hasExtra(EXTRA_TAB)) {
            int t = i.getIntExtra(EXTRA_TAB, 0);
            i.removeExtra(EXTRA_TAB);
            if (nav != null && t >= 0 && t < 3) setTab(t);
        }
        if (i != null && AppShortcuts.isShortcut(i.getAction())) {      // launcher long-press: Play all / Shuffle
            pendingShortcut = i.getAction();
            i.setAction(null);
            runShortcut();
            return;
        }
        if (i == null || !ACTION_OPEN_PLAYER.equals(i.getAction())) return;
        i.setAction(null);          // consumed: a rotation or theme change must not pop the player up again
        final android.view.View decor = getWindow().getDecorView();
        decor.post(() -> {
            try {
                if (panel == null || isFinishing() || isDestroyed()) return;
                Pb.connect(this);
                panel.setCollapsedY(Math.max(1, decor.getHeight()));
                // wait for the connection so an empty queue never shows a blank player
                Pb.whenReady(() -> {
                    try {
                        if (panel != null && Pb.hasMedia() && !panel.isExpanded()) panel.expand(true);
                    } catch (RuntimeException e) {
                        CrashGuard.nonFatal("open player", e);
                    }
                });
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("open player", e);
            }
        });
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- header: title + count, round buttons
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 4));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        final TextView title = Ui.text(this, getString(R.string.app_name), 32, R.color.text_primary);
        titleView = title;
        title.setTypeface(Ui.titleFace(true));
        title.setOnClickListener(v -> {
            if (rv != null) rv.smoothScrollToPosition(0);
        });
        subtitle = Ui.text(this, "", 13, R.color.text_secondary);
        titles.addView(title);
        titles.addView(subtitle);
        bar.addView(titles, Ui.weight(1));
        sortBtn = Ui.icon(this, R.drawable.ic_sort, R.string.sort_by, v -> Menus.sort(this, () -> {
            animateNext = true;
            refresh();
        }));
        View[] btns = {sortBtn};
        for (View v : btns) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(Ui.dp(this, 8));
            bar.addView(v);
        }
        headerBar = bar;
        root.addView(bar);
        selBar = buildSelectionBar();
        selBar.setVisibility(View.GONE);
        root.addView(selBar);

        search = Ui.searchEdit(this, getString(R.string.search_hint));
        search.setOnEditorActionListener((v, id, ev) -> {
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim();
                queryTokens = Norm.tokens(query);
                // debounced: filtering a big library on every keystroke was a real source of typing lag
                searchHandler.removeCallbacks(searchRefresh);
                searchHandler.postDelayed(searchRefresh, 150);
            }
        });
        root.addView(search);

        banner = Ui.settingRow(this, R.drawable.ic_shield, getString(R.string.finish_setup), getString(R.string.finish_setup_sub),
                null, false, v -> Ui.go(this, new Intent(this, SettingsActivity.class)));
        banner.setVisibility(View.GONE);
        Ui.shape(this, banner.findViewById(R.id.card), true, true, R.color.accent_soft);
        root.addView(banner);

        // ---- list (the play-all pills are the list header, so everything scrolls together)
        songs = new TrackAdapter(this, this);
        actions = Ui.pillRow(this,
                Ui.pill(this, R.string.play_all, R.drawable.ic_play, true, v -> playAll(false)),
                Ui.pill(this, R.string.shuffle_all, R.drawable.ic_shuffle, false, v -> playAll(true)));
        FrameLayout body = new FrameLayout(this);
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 150));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(12);
        rv.getRecycledViewPool().setMaxRecycledViews(0, 24);   // rows are costly to build: keep more of them ready while flinging
        rv.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall));
        RecyclerView.ItemAnimator ia = rv.getItemAnimator();
        if (ia instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) ia).setSupportsChangeAnimations(false);
        }
        ListTuning.apply(rv);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                Glass.boost();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView r, int state) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING && mini != null) mini.collapse();
            }
        });
        rv.addItemDecoration(new SectionDecoration(this, new SectionDecoration.Source() {
            @Override
            public List<Track> tracks() {
                return songs.data;
            }

            @Override
            public int mode() {
                return tab == 0 && rv.getAdapter() == songs ? listMode : -1;
            }

            @Override
            public int offset() {
                return songs.header == null ? 0 : 1;
            }
        }));
        // swipe a song to either side: play it next
        new androidx.recyclerview.widget.ItemTouchHelper(new SwipeToQueue(this, songs)).attachToRecyclerView(rv);
        srl = new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this);
        srl.addView(rv, new ViewGroup.LayoutParams(-1, -1));
        srl.setProgressBackgroundColorSchemeColor(Ui.color(this, R.color.surface_high));
        srl.setColorSchemeColors(Ui.color(this, R.color.accent_text));
        srl.setOnRefreshListener(() -> Library.scan(this, () -> {
            srl.setRefreshing(false);
            refresh();
        }));
        body.addView(srl, new FrameLayout.LayoutParams(-1, -1));
        fast = new FastScroll(this, rv);
        body.addView(fast, new FrameLayout.LayoutParams(-1, -1));
        skeleton = Ui.skeleton(this);
        skeleton.setVisibility(View.GONE);
        body.addView(skeleton, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        pulse = android.animation.ObjectAnimator.ofFloat(skeleton, "alpha", 0.45f, 1f);
        pulse.setDuration(Motion.PULSE);
        pulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), 0);
        ImageView ei = new ImageView(this);
        ei.setImageResource(R.drawable.ic_music);
        Ui.tint(ei, R.color.text_hint);
        emptyBox.addView(ei, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 56)));
        emptyText = Ui.text(this, "", 15, R.color.text_secondary);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.topMargin = Ui.dp(this, 14);
        emptyBox.addView(emptyText, tp);
        grant = Ui.pill(this, R.string.allow_access, 0, true, v -> Perms.askAudio(this));
        LinearLayout.LayoutParams gp = Ui.lp(-2, -2);
        gp.topMargin = Ui.dp(this, 18);
        emptyBox.addView(grant, gp);
        body.addView(emptyBox, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // everything the glass blurs lives in `main`; the tab bar and the mini player float above it
        FrameLayout main = new FrameLayout(this);
        main.setBackground(Ui.glow(this));
        main.addView(root, new FrameLayout.LayoutParams(-1, -1));
        mainCol = main;

        // the full player lives on top of the home screen and follows the finger (see PlayerPanel)
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Ui.color(this, R.color.bg));
        stage.addView(main, new FrameLayout.LayoutParams(-1, -1));

        nav = new FloatingNav(this, main,
                new int[]{R.drawable.ic_music, R.drawable.ic_list, R.drawable.ic_search, R.drawable.ic_settings},
                new int[]{R.string.tab_songs, R.string.tab_playlists, R.string.search, R.string.settings});
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, Ui.dp(this, 64), Gravity.BOTTOM);
        nlp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        stage.addView(nav, nlp);
        nav.setListener((i, again) -> {
            if (i == 3) {
                Ui.go(this, new Intent(this, SettingsActivity.class));
                return;
            }
            if (again) {
                if (rv != null) rv.smoothScrollToPosition(0);
                return;
            }
            setTab(i);
        });
        mini = new MiniPlayer(this, main);
        mini.setOnShown(this::applyDock);
        stage.addView(mini);
        panel = new PlayerPanel(this, new PlayerPanel.Host() {
            @Override
            public void onCollapsed() {
                nav.glass().setPaused(false);
                mini.glass().setPaused(false);
                setMainLayer(false);
                mainCol.setScaleX(1f);
                mainCol.setScaleY(1f);
                mainCol.setAlpha(1f);
            }

            @Override
            public void onProgress(float f) {
                setMainLayer(f > 0.001f && f < 0.999f);
                mainCol.setScaleX(1f - 0.05f * f);
                mainCol.setScaleY(1f - 0.05f * f);
                mainCol.setAlpha(1f - 0.5f * f);
            }

            @Override
            public void onState(boolean expanded) {
                nav.glass().setPaused(expanded);
                mini.glass().setPaused(expanded);
                updateBack();
            }
        });
        stage.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        mini.setPanel(panel);
        setContentView(stage);
        getWindow().setBackgroundDrawable(Ui.glow(this));
        Ui.edgeToEdge(this, root, false, (bottom, ime) -> {
            insetBottom = bottom;
            imeBottom = ime;
            applyDock();
        });
        backCb = new androidx.activity.OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (panel.isExpanded()) panel.collapse(true);
                else if (songs.select) exitSelect();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCb);
        nav.select(0, false);
        setTab(0);
        applyDock();
    }

    private void applyDock() {
        if (nav == null || mini == null || rv == null) return;
        int navH = Ui.dp(this, 64), gap = Ui.dp(this, 10);
        int nb = insetBottom + Ui.dp(this, 8);
        FrameLayout.LayoutParams nlp = (FrameLayout.LayoutParams) nav.getLayoutParams();
        if (nlp.bottomMargin != nb) {
            nlp.bottomMargin = nb;
            nav.setLayoutParams(nlp);
        }
        int miniBottom = nb + navH + gap;
        mini.setDock(miniBottom, getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24));
        boolean kb = imeBottom > insetBottom + Ui.dp(this, 80);
        boolean miniOn = mini.getVisibility() == View.VISIBLE;
        int pad = kb ? imeBottom + Ui.dp(this, 8) : miniBottom + (miniOn ? Ui.dp(this, 56) + gap : 0);
        if (rv.getPaddingBottom() != pad) rv.setPadding(0, rv.getPaddingTop(), 0, pad);
    }

    private boolean mainLayer;

    /** Scale + fade of the whole home screen is smooth only when it is drawn once into a layer. */
    private void setMainLayer(boolean on) {
        if (on == mainLayer || mainCol == null) return;
        mainLayer = on;
        mainCol.setLayerType(on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
    }

    private void setTab(int t) {
        if (t != tab && songs.select) exitSelect();
        int prev = tab;
        tab = t;
        if (nav != null) nav.select(t, true);
        titleView.setText(t == 0 ? R.string.app_name : t == 1 ? R.string.tab_playlists : R.string.search);
        sortBtn.setVisibility(t == 0 ? View.VISIBLE : View.INVISIBLE);
        android.view.inputmethod.InputMethodManager im =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (t == 2) {
            search.setVisibility(View.VISIBLE);
            if (prev != 2) {
                animateNext = true;
                search.requestFocus();
                search.postDelayed(() -> {
                    if (im != null && tab == 2) im.showSoftInput(search, 0);
                }, 160);
            }
        } else {
            if (prev == 2) {
                if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.setVisibility(View.GONE);
                search.setText("");
                query = "";
                queryTokens = new String[0];
                animateNext = true;
            }
        }
        if (t != prev && (t == 2 || prev == 2) && rv != null) rv.scrollToPosition(0);
        refresh();
    }

    private String scrollLabel(int adapterPos) {
        if (shown.isEmpty()) return "";
        int i = Math.max(0, Math.min(shown.size() - 1, adapterPos - (songs.header == null ? 0 : 1)));
        return SectionDecoration.scrollLabel(shown.get(i), listMode, i);
    }

    private void playAll(boolean shuffle) {
        if (shown.isEmpty()) return;
        if (shuffle && Store.aiEnabled(this)) {
            Pb.play(this, AiEngine.smartShuffle(this, shown), 0, false);
            return;
        }
        Pb.play(this, shown, shuffle ? -1 : 0, shuffle);
    }

    /** Starts what the launcher shortcut asked for once the library is known (right away if it already is). */
    private void runShortcut() {
        String a = pendingShortcut;
        if (a == null || !Library.loaded) return;
        pendingShortcut = null;
        if (Library.tracks.isEmpty()) return;
        boolean shuffle = AppShortcuts.ACTION_SHUFFLE.equals(a);
        Pb.play(this, Library.sorted(Store.sort(this)), shuffle ? -1 : 0, shuffle);
    }

    private void rescan() {
        CrashGuard.offer(this);
        Library.scan(this, () -> {
            Resume.restore(this);
            runShortcut();
            refresh();
        });
        refresh();
    }

    private int refreshGen;

    private void refresh() {
        final int gen = ++refreshGen;
        if (tab != 2) {
            applyList(Library.sorted(Store.sort(this)));
            return;
        }
        if (query.isEmpty()) {
            applyList(new ArrayList<>());
            return;
        }
        final int mode = Store.sort(this);
        final String[] tokens = queryTokens;
        Bg.query("search", () -> {
            List<Track> src = Library.sorted(mode);
            final List<Track> f = new ArrayList<>();
            for (Track t : src) if (Norm.matches(t.key, tokens)) f.add(t);
            Bg.main(() -> {
                if (gen == refreshGen && !isFinishing() && !isDestroyed()) applyList(f);
            });
        });
    }

    private void applyList(List<Track> all) {
        int total = Library.tracks.size();
        shown = all;
        int mode = Store.sortMode(this);
        if (mode != listMode) {
            listMode = mode;
            rv.invalidateItemDecorations();
        }
        songs.header = all.isEmpty() || tab == 2 ? null : actions;
        songs.setData(all, animateNext);
        songs.setCurrent(Pb.currentId());
        if (songs.select) syncSelection(all);

        if (tab == 1) {
            subtitle.setText("");
        } else if (tab == 2) {
            subtitle.setText(query.isEmpty() ? "" : getResources().getQuantityString(R.plurals.songs_n, all.size(), all.size()));
        } else {
            subtitle.setText(getResources().getQuantityString(R.plurals.songs_n, total, total));
        }
        RecyclerView.Adapter<?> target = tab == 1 ? lists : songs;
        boolean swapped = rv.getAdapter() != target;
        if (swapped) {
            RecyclerView.Adapter<?> old = rv.getAdapter();
            if (old != null) listState.save(old == songs ? 0 : 1, rv);      // each tab keeps its scroll position
            rv.setAdapter(target);
            listState.restore(tab, rv);
            float dir = (tab == 1 ? 1f : -1f) * (rv.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1f : 1f);
            rv.animate().cancel();
            rv.setTranslationX(dir * Ui.dp(this, 36));
            rv.setAlpha(0f);
            rv.animate().translationX(0f).alpha(1f).setDuration(Motion.BASE).setInterpolator(Motion.ENTER).start();
        }
        if (tab == 1) lists.notifyDataSetChanged();
        if ((swapped || animateNext) && target.getItemCount() > 0) {
            animateNext = false;
            rv.scheduleLayoutAnimation();
        }

        boolean audio = Perms.hasAudio(this);
        boolean loading = tab == 0 && audio && !Library.loaded;
        skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            if (!pulse.isStarted()) pulse.start();
        } else {
            pulse.cancel();
        }
        songs.setPlaying(Pb.get() != null && Pb.get().isPlaying());
        if (tab == 2 && all.isEmpty()) {
            emptyBox.setVisibility(View.VISIBLE);
            emptyText.setText(query.isEmpty() ? R.string.search_prompt : R.string.no_results);
            grant.setVisibility(View.GONE);
        } else if (tab == 0 && all.isEmpty() && !loading) {
            emptyBox.setVisibility(View.VISIBLE);
            emptyText.setText(!audio ? R.string.no_access : R.string.no_songs);
            grant.setVisibility(audio ? View.GONE : View.VISIBLE);
        } else {
            emptyBox.setVisibility(View.GONE);
        }
        banner.setVisibility(Perms.anyMissing(this) ? View.VISIBLE : View.GONE);
        srl.setEnabled(tab == 0 && audio);
        fast.setup(this::scrollLabel, tab == 0 && all.size() > 60);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pb.connect(this);
        Pb.add(current);
        Library.addListener(libChanged);
        mini.start();
        panel.onHostStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Perms.hasAudio(this) && (!Library.loaded || Library.tracks.isEmpty())) rescan();
        else {
            int fresh = takeFresh();
            refresh();
            announce(fresh);
            Library.syncIfChanged(this);   // picks up anything that changed while the app was away
        }
    }

    @Override
    protected void onStop() {
        Pb.remove(current);
        Library.removeListener(libChanged);
        searchHandler.removeCallbacks(searchRefresh);
        mini.stop();
        pulse.cancel();          // no animator keeps running while the app is in the background
        panel.onHostStop();
        super.onStop();
    }

    @Override
    public void onClick(Track t, int pos) {
        if (songs.select) {                  // the adapter already toggled the check mark
            if (songs.selected.isEmpty()) exitSelect();
            else updateSelBar();
            return;
        }
        Pb.play(this, shown, pos, false);
    }

    /** Long press starts selecting, with the pressed song already checked. */
    @Override
    public boolean onLong(Track t, int pos) {
        if (tab == 1) return false;
        songs.selected.add(t.id);
        enterSelect();
        return true;
    }

    @Override
    public void onMore(Track t, int pos) {
        Menus.song(this, t, this::refresh);
    }

    // ---- multi-select (long press a song): check many, delete them in one go ----------------------------------

    private LinearLayout buildSelectionBar() {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPaddingRelative(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 4));
        b.addView(Ui.icon(this, R.drawable.ic_close, R.string.cancel, v -> exitSelect()));
        selCount = Ui.text(this, "", 20, R.color.text_primary);
        selCount.setTypeface(Ui.titleFace(false));
        selCount.setSingleLine(true);
        selCount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cp = Ui.weight(1);
        cp.setMarginStart(Ui.dp(this, 14));
        cp.setMarginEnd(Ui.dp(this, 8));
        b.addView(selCount, cp);
        View all = Ui.icon(this, R.drawable.ic_check, R.string.select_all, v -> {
            songs.selectAll();
            updateSelBar();
        });
        View del = Ui.icon(this, R.drawable.ic_delete, R.string.delete, v -> askDeleteSelected());
        ((LinearLayout.LayoutParams) del.getLayoutParams()).setMarginStart(Ui.dp(this, 8));
        b.addView(all);
        b.addView(del);
        return b;
    }

    private void enterSelect() {
        swapBars(headerBar, selBar);
        songs.setSelectMode(true);
        updateSelBar();
        updateBack();
    }

    private void exitSelect() {
        if (selBar == null) return;
        swapBars(selBar, headerBar);
        songs.setSelectMode(false);
        updateBack();
    }

    /** The old bar goes away at once (no jump in the column), the new one fades and slides in. */
    private void swapBars(View out, View in) {
        out.animate().cancel();
        in.animate().cancel();
        out.setVisibility(View.GONE);
        out.setAlpha(1f);
        out.setTranslationY(0f);
        in.setAlpha(0f);
        in.setTranslationY(-Ui.dp(this, 10));
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).translationY(0f).setDuration(Motion.SHORT).setInterpolator(Motion.ENTER).start();
    }

    private void updateSelBar() {
        int n = songs.selected.size();
        selCount.setText(getResources().getQuantityString(R.plurals.selected_n, n, n));
    }

    private void updateBack() {
        if (backCb != null) backCb.setEnabled((panel != null && panel.isExpanded()) || (songs != null && songs.select));
    }

    /** After the list changed (songs deleted, search typed): only songs that are still shown stay checked. */
    private void syncSelection(List<Track> visible) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Track t : visible) ids.add(t.id);
        if (songs.selected.retainAll(ids)) songs.notifyItemRangeChanged(0, songs.getItemCount());
        if (songs.selected.isEmpty()) exitSelect();
        else updateSelBar();
    }

    private void askDeleteSelected() {
        final List<Track> pick = songs.selectedTracks();
        if (pick.isEmpty()) return;
        int n = pick.size();
        Dlg.confirm(this, getResources().getQuantityString(R.plurals.selected_n, n, n),
                getResources().getQuantityString(R.plurals.confirm_delete_songs, n, n),
                R.string.delete, true, () -> DeleteActivity.start(this, pick));
    }

    private int[] mixKinds() {
        return Store.aiEnabled(this)
                ? new int[]{AiEngine.MIX_NOW, AiEngine.MIX_TOP, AiEngine.MIX_RECENT, AiEngine.MIX_FORGOTTEN, AiEngine.MIX_NEW}
                : new int[]{AiEngine.MIX_NEW};
    }

    private static final int[] MIX_TITLE = {R.string.mix_now, R.string.mix_top, R.string.mix_recent, R.string.mix_forgotten, R.string.mix_new};
    private static final int[] MIX_SUB = {R.string.mix_now_sub, R.string.mix_top_sub, R.string.mix_recent_sub, R.string.mix_forgotten_sub, R.string.mix_new_sub};
    private static final int[] MIX_ICON = {R.drawable.ic_ai, R.drawable.ic_star, R.drawable.ic_timer, R.drawable.ic_heart, R.drawable.ic_refresh};

    private void playMix(int kind) {
        List<Track> l = AiEngine.mix(this, kind);
        if (l.isEmpty()) {
            Ui.toast(this, R.string.mix_empty);
            return;
        }
        Pb.play(this, l, 0, false);
    }

    /** Row 0 is "New playlist", then the smart mixes, then Favorites and the user's playlists. */
    private final class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            FrameLayout f = new FrameLayout(p.getContext());
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            ViewGroup f = (ViewGroup) h.itemView;
            f.removeAllViews();
            int n = getItemCount();
            int[] kinds = mixKinds();
            View row;
            if (pos == 0) {
                row = Ui.settingRow(MainActivity.this, R.drawable.ic_add, getString(R.string.new_playlist), null, null, false,
                        v -> Menus.ask(MainActivity.this, R.string.new_playlist, "", name -> {
                            Store.create(MainActivity.this, name);
                            refresh();
                        }));
                Ui.shape(MainActivity.this, row.findViewById(R.id.card), true, n == 1, R.color.accent_soft);
            } else if (pos <= kinds.length) {
                final int kind = kinds[pos - 1];
                row = Ui.settingRow(MainActivity.this, MIX_ICON[kind], getString(MIX_TITLE[kind]), getString(MIX_SUB[kind]),
                        null, false, v -> playMix(kind));
                Ui.shape(MainActivity.this, row.findViewById(R.id.card), false, pos == n - 1, R.color.accent_soft);
            } else {
                final Store.Playlist pl = Store.playlists(MainActivity.this).get(pos - 1 - kinds.length);
                int cnt = Library.count(pl.ids);
                int ic = Store.FAV.equals(pl.id) ? R.drawable.ic_heart_fill
                        : Store.AI.equals(pl.id) ? R.drawable.ic_ai : R.drawable.ic_list;
                row = Ui.settingRow(MainActivity.this, ic,
                        pl.name, getResources().getQuantityString(R.plurals.songs_n, cnt, cnt), null, false, v -> {
                            Intent i = new Intent(MainActivity.this, PlaylistActivity.class);
                            i.putExtra("pid", pl.id);
                            Ui.go(MainActivity.this, i);
                        });
                Ui.shape(MainActivity.this, row.findViewById(R.id.card), false, pos == n - 1, R.color.surface);
            }
            f.addView(row);
        }

        @Override
        public int getItemCount() {
            return Store.playlists(MainActivity.this).size() + 1 + mixKinds().length;
        }
    }
}
