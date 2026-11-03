---
name: semo-music
description: Progress log and working notes for the SimoMusic Android app (package com.simomusic.player, Java + Views, media3). Read before continuing any work on this project.
---

# Project
- Android music player, Java with programmatic Views, media3 (ExoPlayer + MediaSession), no Compose, no README by request.
- Source root: app/src/main/java/com/simomusic/player, strings in res/values and res/values-ar.
- No compiler is available in the assistant sandbox: every change must be re-read carefully for compile errors.

# Done in this session
- Mini player rewritten (MiniPlayer.java, new RingPlay.java): floating rounded card tinted from cover color (ArtColor), circular progress ring around play/pause with pop animation, marquee title while playing, slide/scale feel on track change.
- Mini player gestures: tap opens full player, swipe up drags the sheet, swipe left/right next/previous with rubber band and haptic tick at threshold, swipe down pauses, long press opens the queue.
- Library cleanup: Junk.java hides keyboard/system sounds (very short files, known name prefixes, UI/keyboard folders); toggle "smart_filter" in Settings (default on).
- Sorting: modes 0 title, 1 artist, 2 recent, 3 duration, 4 album, 5 most played, 6 for right now; bit 16 of Store.sort means reversed (Store.sortMode / Store.sortReverse). Modes 5 and 6 are never cached and need AI Mix enabled to have data. Sort sheet has a direction row. List animates on sort change.
- Norm.java: Arabic/Latin folding (diacritics, hamza forms, taa marbuta, digits) used by Track.key and multi-word search in the home list.
- Local AI (AiEngine.java, PlayStats.java): time-of-day buckets per song (Row.part), time affinity in AI Mix, mix(kind) for now / top / recent / forgotten / new, similar(seed) for radio and autoplay, smartShuffle (weighted, spreads same artist).
- Playlists tab shows smart mixes under "New playlist" (only "Just added" when AI Mix is off).
- Song menu has "Start radio from this song".
- PlayerService.extendQueue: autoplay similar songs near the end of the queue (setting "autoplay", default on, skipped for shuffle/repeat).
- Home: tapping the title scrolls to top; Shuffle uses smartShuffle when AI Mix is on.

# Drag smoothness fix (full player)
- Gest.busy is true while PlayerPanel moves (drag or spring). WavePainter, EqView and RingPlay stop their per-frame invalidation while it is true, so the panel layer and home layer are not re-rendered every frame. PlayerPanel.layer(false) clears it and calls Gest.kick, which wakes only WaveSeekBar, EqView and RingPlay (no full-tree invalidate).
- PlayerPanel: nested col layer removed, corner radius only animates in the first part of the drag, drag-down detection uses Gest.downLoose (diagonal drags count).

# Ideas not done yet
- Fast-scroll letter bubble on the song list.
- Crossfade between songs.
- Sleep timer shake-to-extend, sections/headers per artist or album.
- OpenActivity voice search still uses plain lowercase matching, could use Norm.
- Replace notifyDataSetChanged in TrackAdapter.setData with a diff for smoother sort changes.

# Conventions
- No comments in new code, no Arabic text inside Java files (Arabic only in values-ar/strings.xml).
- Do not create README.md.

# Seek time label
- NowPlaying: elapsed-time label no longer changes color while scrubbing and scales from its start edge (pivot) so it is never clipped at the screen edge.

# Smoothness + lock screen + design pass
- Cover loading (Art.java): Art.HERO (2 threads, normal priority) serves the cover that is on screen (Art.fetchUrgent / Art.load with keepOld): NowPlaying cover and tint, Backdrop, MiniPlayer, ArtLoader (notification / system lock screen). List thumbnails stay on Art.EX (background priority, newest first).
- Art.fetch shares one decode per uri+size between screens (PENDING, main thread only; an urgent request takes over a slow prefetch) and Art.decode downsizes the last big decode (MASTER) instead of reading the file again for the backdrop (same px as the cover) and the 512px notification art.
- Art.load(..., keepOld=true) keeps the previous cover until the new one is decoded (no placeholder flash on skip). Backdrop.set(c, bg, uri, px) cross-fades with a TransitionDrawable instead of fading to nothing and back.
- PlayerService: Resume.saveSoon coalesces the 3 saves a skip fires (id list cached until Resume.queueChanged), PlayStats.save is batched 4 s and serialised on a background thread (PlayStats.flush on stop), extendQueue runs 1.5 s after the transition.
- WavePainter: idle wave redraws at ~30 fps (postInvalidateDelayed(32)); dragging / easing keep full speed. NowPlaying.seek.setProgress(p, false) (the painter eases by itself).
- Design: palette tuned (light theme now grey canvas + white cards), radius_card 24dp / inner 8dp / thumb 16dp, Ui.shape adds a hairline row_stroke (playing row gets an accent outline), row spacing 16dp x 2dp, medium weight titles (Ui.medium), 54dp row covers, taller pills (50dp) and segmented control (44dp), bold 32sp home title, Ui.flatPlain for per-row buttons (no press animator per row).

# Lock screen swipe, full player drag, cover change lag
- LockActivity: while a finger is down, clock, cover, glass panel, hint and top row each get their own hardware layer (dragBegin / dragEnd), alpha is set per layer (fade) instead of on the whole content, Gest.busy is true (wave bar stops redrawing) and the arrow pulse is cancelled. Layers are released when the reset animation ends (dragGen guards a re-grab) or in onStop / onDestroy.
- Unlock thresholds lowered (70 dp, or fling 250 with 24 dp). ACTION_CANCEL (system gesture area steals a fast flick) now also unlocks when the movement qualifies; closing by pull down still needs ACTION_UP.
- PlayerPanel: the full-screen hardware layer on the sheet is gone (parallax children invalidated it every frame, and creating and dropping it hitched at both ends). The poke of the whole window at the end of a gesture is replaced by Gest.kick. col has setHasOverlappingRendering(false).
- Cover change: Art.fetch calls Bitmap.prepareToDraw on the worker thread (texture upload off the UI thread). Art.FX is a single thread pool for colour pick and blurred backdrop so they never queue behind cover decoding. Art.load has an overload with a shown callback; NowPlaying starts the cover entrance animation from it, after the new picture is on the view. Prefetch also loads the previous song. PlayerPanel.commitSwipe puts the incoming cover on the view before it slides in.
- Not verified on a device (no compiler in the sandbox): test the lock screen swipe up fast and slow, drag down the full player from the notification, skip songs by button and by cover swipe.

# Other animation fixes + Bluetooth sound for cheap earbuds
- MainActivity: song list and loading skeleton use setHasOverlappingRendering(false), so the tab-swap fade and the skeleton pulse no longer copy the whole list into an offscreen buffer every frame.
- MiniPlayer: horizontal swipe and swipe-down lift the card into a hardware layer and set Gest.busy while it is dragged (lift(on)); released when the spring-back ends or the view detaches. The cover colour pick runs on Art.FX, not on the UI thread.
- BtDsp new stages (all optional, params in BtDsp.Params): contour (low shelf 110 Hz up to +4 dB and high shelf 6.5 kHz up to +1.5 dB, scaled 0..1) and smooth (dynamic harshness softener). Chain order: normalize, EQ, contour, virtual bass, compressor, softener, widener, limiter.
- Softener: band-pass at 6.5 kHz (Q 1) subtracted with factor k (max 0.55) when the band peak exceeds 30 percent of the full-signal peak; x - k*bp(x) can never boost any frequency (checked numerically), music that is not harsh stays at k near 0. Mono path writes r = l afterwards.
- Contour level comes from STREAM_MUSIC volume: 0 at 55 percent volume and above, 1 at 10 percent and below, rounded to 0.05. BtAudio.volPoll checks the volume once a second and re-applies params when it changes. Settings keys bt_contour and bt_smooth, both default on, toggles in BtSheet, strings in values and values-ar.
- Not verified on a device (no compiler in the sandbox).

# Build fix: setHasOverlappingRendering
- View has no setHasOverlappingRendering method (only the protected-style override hasOverlappingRendering()), so the release compile failed with 5 errors in MainActivity (rv, skeleton), LockActivity, NowPlaying, PlayerPanel. All five calls removed; nothing else changed.
- If the offscreen-buffer optimisation is wanted again, override hasOverlappingRendering() to return false in a small View subclass instead of calling a setter.
- Not verified on a device or with a compiler (none in the sandbox): rerun the GitHub Actions build.

# Song change lag + shrink while dragging the player down
- PlayerPanel.applyProgress: the whole sheet now scales from 0.84 to 1 (pivot bottom centre) with the drag progress, so pulling the player down fast shrinks it like a card; collapse / expand keep the same spring.
- PlayerPanel: np.onRefresh only refreshes the heart on a song change; the Bluetooth and timer tints are refreshed on attach, on audio device changes and from the menus (AudioManager.getDevices was a binder call on every song change).
- NowPlaying and MiniPlayer: on a song change the cover swap and its entrance animation run first; Prefetch.upcoming, colour pick and backdrop (NowPlaying) and colour pick and prefetch (MiniPlayer) start 160 ms later and are skipped if the song changed again meanwhile.
- Entrance animations of the cover and of the mini player text use withLayer() so alpha and scale run on a hardware layer for the length of the animation.
- Not verified on a device (no compiler in the sandbox): test fast skips by button and by cover swipe, and a fast drag down of the full player.

# Cover change lag in the full player (second pass)
- Backdrop.set: no more TransitionDrawable on the full-screen backdrop (it redrew the whole screen every frame for 600 ms). The old picture goes into a temporary ghost ImageView on a hardware layer that only fades its alpha (Motion.BASE) and is removed at the end; the new picture is set on the backdrop at once. Falls back to the old fade-in when the parent is not a FrameLayout.
- ArtGlow: the colour is no longer animated per frame (full-screen radial gradient redraw). It fades out (FAST), takes the new colour, fades in (BASE); the view has a hardware layer.
- Prefetch.upcoming: for large covers (size >= 400 px) only the next and previous songs are prefetched, not the one after next, to cut decode threads and GC pressure while a song starts.
- Not verified on a device (no compiler in the sandbox).

# Next button cover lag + stronger shrink
- Cause of the delay on the first Next after opening the full player: the big cover (320 dp) of the next songs was only prefetched when NowPlaying saw a cover change, and NowPlaying is stopped while the player is closed, so the next cover was decoded on demand. MiniPlayer now prefetches the 54 dp and 320 dp covers of the next / previous songs (160 ms after a song change), and NowPlaying prefetches once per song id (300 ms after refresh) even when the cover did not change, e.g. when the player is opened.
- Prefetch.upcoming skips the song after next whenever a size >= 400 px is requested.
- PlayerPanel.applyProgress: sheet scale is now 0.70 to 1 (was 0.84 to 1), pivot bottom centre, so the shrink is clearly visible while dragging down.
- Not verified on a device (no compiler in the sandbox).

# Performance + stability: work moved off the UI thread
- New Bg.java: Bg.io (single thread, ordered, disk writes), Bg.query (2 threads, system queries), Bg.main (post to the UI thread). Every task is wrapped so an exception is logged with CrashGuard.nonFatal instead of killing the thread silently. Threads run at background priority.
- Store.save: the playlists are copied on the calling thread (cheap) and the JSON is built and written on Bg.io, in order, so the last save always wins. Adding to favourites or a playlist no longer builds the whole JSON on the UI thread.
- Resume.save: the values are read from the player on the UI thread (the controller needs it) and the SharedPreferences write runs on Bg.io.
- PlayerPanel.refreshExtrasInner: the Bluetooth check (AudioManager binder call) runs on Bg.query and the tint is applied back on the UI thread, only if the panel is still attached.
- App.onCreate: settings and playlists are loaded on Bg.io at start, so the first screen does not wait for the SharedPreferences file or the JSON parse.
- Already off the UI thread before: cover decoding (Art.EX / HERO / FX), library scan, play stats save, media watcher, queue building (Pb.BUILD).
- Not verified on a device (no compiler in the sandbox).

# Swipe down anywhere + more background threads
- PlayerPanel.dispatchTouchEvent: the downward gesture is now caught before the children (cover, buttons, seek area excluded only when the finger starts on the seek bar) and no longer depends on onInterceptTouchEvent, so requestDisallowInterceptTouchEvent from a child cannot block it. The children get a cancel (swallow flag keeps onTouchEvent from reacting to it) and the sheet follows the finger and shrinks (scale 0.70 to 1).
- A fast flick closes the player even without a long drag: on ACTION_UP, vertical velocity above 600 dp/s, at least 20 dp of travel and mostly vertical triggers collapse(true) with the finger speed as spring start velocity. The normal release threshold is now 180 dp/s (was 260).
- VelocityTracker input is de-duplicated by event time (intercept, dispatch and onTouchEvent all feed it).
- MainActivity.refresh is split: with a search query the sort and filter run on Bg.query and applyList(...) runs on the UI thread only if no newer refresh started (refreshGen); without a query it stays synchronous (cached sort).
- Resume.restore: the id list is built on Bg.query and Pb.restore (which needs a looper) is called back on the UI thread.
- Art.FX has 2 threads (colour pick and backdrop blur no longer wait for each other).
- Not verified on a device (no compiler in the sandbox).

# Player sheet refused to close (wrong finger speed)
- Cause: every VelocityTracker was fed with event.getX/getY, which are in the local coordinates of the view that receives the event. The sheet, the cover and the mini player move with the finger while they are dragged, so their local coordinates barely change and the measured speed was close to zero (or jumped on the last event): a fast swipe was judged as a slow short one and the sheet sprang back open.
- Gest.feed(tracker, event) copies the event, sets its location to getRawX/getRawY and feeds the tracker. All 14 addMovement calls (PlayerPanel sheet and cover swipe, MiniPlayer, LockActivity, Ui) now use it.
- Effect: fast swipe down closes the player, fast cover swipe changes the song with a small distance, mini player flicks and lock screen flings use the real finger speed.
- Not verified on a device (no compiler in the sandbox).

# Lyrics (new)
- LyricsActivity: own screen opened from the lyrics icon in the full player top row. Backdrop from cover, centered lines, active line full opacity with neighbours fading by distance, smooth centering scroller, tap a line to seek, manual scroll pauses follow and shows a "follow" pill that resumes after 3.6 s, draggable progress bar, prev / play / next, text size chips and manual search in the more sheet.
- LyricsRepo: runs on its own 2-thread executor ("lyrics"). Source 1 LRCLIB (/api/get then /api/search variants, best match by title / artist / duration), source 2 lyrics.ovh (plain text). Results cached in files/lyrics (json) plus an in-memory LRU, misses are remembered for 12 h. Host can be swapped with pref "lyrics_host" (for a proxy).
- LrcParser: multi-timestamp lines, offset tag, enhanced word tags stripped, empty lines become a note symbol.
- Title / artist cleaning (brackets, feat., track numbers, "Artist - Title" in the title) happens in LyricsRepo.Query.of.
- Plain-only lyrics are turned into estimated sync (LyricsRepo.estimate: lines weighted by length over the song duration). Each tap on the line being sung adds or replaces an anchor (line index, position); build() interpolates between anchors by line weight, learns the singing rate from the anchors and slows the tail estimate when the same line is tapped again later. Anchors are stored in the cache json (anch, mul); long-press seeks to that line.

# Audit fixes applied 2026-10-23 (not compiled, not run on a device)
- Groq key moved to its own prefs file "simomusic_secret" (Store.groqKey / setGroqKey, old value migrated and removed). backup_rules.xml and data_extraction_rules.xml exclude it, simomusic_stats and the whisper folder; manifest fullBackupContent points to @xml/backup_rules.
- Stable playlist keys: Store keeps id -> "title|artist|seconds" in prefs "idkeys" (rebuilt on every save) and Store.remap(c), called at the end of Library.read, re-points playlist ids that no longer exist to the song with the same key.
- Library.byId is now a volatile map that is built aside and swapped (no empty window). Track has sortTitle / sortArtist / sortAlbum precomputed and the comparators use them. Extension lower-casing uses Locale.ROOT.
- Store: playlists and their ids are CopyOnWriteArrayList (safe to iterate while changed), load parses item by item and keeps the raw text in "playlists_corrupt" if the array itself is broken, addAll uses a HashSet.
- Sleep timer runs in PlayerService (pref "sleep_wall" = wall clock end time, 0 = off; armSleep, sleepRun, sleepFade with 15 s fade). Pb.sleep only writes the pref and keeps its own UI task.
- MediaWatcher sweep: every 60 s (was 15), skipped while something is playing.
- Resume saves a 1000-id window around the current song and only restores the saved position when the current song was found.
- DeleteActivity finishes itself after 1.5 s when recreated without a pending result.
- OpenActivity: file: paths under /data/ and /proc/ or the app's own dir are refused, copies capped at 400 MB, cache keeps 12 files and only deletes the ones older than a day; voice search uses Norm.
- Updater: refuses an apk with a lower version code, verifies the GitHub asset "sha256:" digest when present, caps size at 400 MB.
- WhisperEngine.download resumes the .part file with a Range request and keeps it when cancelled or interrupted.
- PickSongsActivity search uses Norm tokens; Menus / Oem upper/lower-case with Locale.ROOT; Perms.anyMissing only counts audio, notifications and Bluetooth (lock-screen specials stay in Settings).

# Still open after the audit
- Model SHA-256 pin for Whisper (needs the real hash) and a cleartext policy (usesCleartextTraffic stays true because http links opened from other apps are supported).
- DiffUtil in TrackAdapter (rows keep their bound position for click callbacks, needs care), accessibility labels on the remaining custom views, performClick in touch listeners.
- All feature and design ideas listed in the audit section above.
- First thing to do next: run the GitHub Actions build and fix any compile error reported.

# Features + design pass (not compiled, not run on a device)
- Android Auto / MediaLibraryService: not wanted, not added.
- FastScroll.java: thumb on the end edge of the song list (appears while scrolling, grab it to drag), bubble shows the letter of the current sort key (title / artist / album) or the row number for other sorts; only for lists above 60 songs on the Songs tab. Touch only starts on the thumb so the row menu buttons are never hijacked.
- SectionDecoration.java: group headers inside the song list for sort modes title (letter), artist and album, drawn by an ItemDecoration so adapter positions and click callbacks are unchanged. MainActivity.listMode feeds it and calls invalidateItemDecorations when the sort mode changes.
- TrackAdapter.setData(list, reshuffled): DiffUtil (moves on) for lists up to 2500 rows when at least 60 percent of the songs are shared; a sort change (animateNext) keeps the old full refresh plus layout animation. After a diff rows are re-bound with payload "shape" so first / last corners stay right.
- Fade between songs: pref "crossfade" (0, 2, 4, 6, 8, 12 s, Settings > Playback). Single ExoPlayer, so it is a fade out of the song end and a fade in of the next one (volume dip), not an overlap. PlayerService.xfTick drives fadeGain, sleep timer drives sleepGain, applyVolume sets min of both so they never fight.
- Sleep timer is owned by PlayerService only: Pb.sleep just writes "sleep_wall", Pb.sleepAt() derives the remaining time from it, SleepFade.java removed (it duplicated the service and would have paused at the old time after an extension).
- Shake to extend: pref "sleep_shake" (default on). Accelerometer is registered only in the last 90 s of the timer; 4 jolts above 2.5 g within 0.9 s add 10 minutes from now (writes sleep_wall, vibrates, toast).
- Design alignment: sheets (BtSheet, GroupSheet, SongInfo) body inset 14 -> 8 dp so their rows line up at 24 dp like Sheet.java text; Settings appearance card margin 14 -> 16 dp like every row card; strings unknown_album, seek_bar, cover_art added in values and values-ar. Seek bar and cover now have content descriptions.

# Still open
- Whisper model SHA-256 pin (needs the real hash), cleartext policy (kept on on purpose).
- performClick in the custom touch listeners (MiniPlayer, LockActivity, PlayerPanel cover, QueueSheet, EqSheet): lint only, changing them risks double taps.
- True overlapping crossfade needs a second player.
- First thing next: run the GitHub Actions build and fix any compile error reported.

# Build fix after the features
- PlayerService: prefs listener lambda used xfTick before its declaration (illegal forward reference); now this.xfTick.
- LyricsActivity: KEY_PREF no longer exists since the key moved to the secret prefs; bad key now clears it with Store.setGroqKey(this, "").

# Glass redesign (floating tab bar, live blur, circular mini player) - not compiled, not run on a device
- Goal: iPhone-like glass everywhere; plan approved: floating glass bar with 4 tabs (Songs, Playlists, Search, Settings), Songs/Playlists/Search are pages inside MainActivity, Settings stays an activity with the same bar. No external blur library was added (the sandbox has no network, a wrong JitPack version would break the CI build); the BlurView technique is implemented in-app instead (JitPack stays in settings.gradle, unused).
- StackBlur.java: stack blur on an int[] (small bitmaps only). LiveGlass.java: Drawable that every PreDraw draws the `source` view into a bitmap 1/ (2.6 x density) scale, blurs it and paints bitmap + saturation 1.55 + tint + sheen + gradient rim. Refresh is throttled to 50 ms, 8 ms while Glass.boost() (called on scroll); skipped while Gest.busy, while the source has a layer type, while paused, or when hidden. A RuntimeException while drawing the source (for example a hardware bitmap) switches that glass to a solid fill.
- Glass.live(owner, source, radiusDp): sets LiveGlass as background + rounded outline clip. Glass.sheet(root, card): static blurred snapshot of the activity (SnapGlass.java) as the background of bottom sheets / dialogs (Sheet, BtSheet, EqSheet, GroupSheet, QueueSheet, SongInfo, UpdateSheet, Dlg); dim lowered 0.62 -> 0.42 in themes.
- FloatingNav.java: 64 dp glass capsule, sliding pill behind the selected tab, RTL aware. Used by MainActivity (live blur of `main`) and SettingsActivity (tab 3 selected; other tabs start MainActivity.tabIntent and finish).
- MainActivity: `main` FrameLayout (window glow as background) is the blur source and the scaled/faded layer of the player panel; header has only the sort button (Songs tab); tab 2 is the search page (field + results, empty state strings search_prompt / no_results); list bottom padding follows the dock (nav + mini) or the keyboard; Ui.edgeToEdge got an overload (padBottom, onBottom) so the bottom inset is not padded.
- MiniPlayer: 56 dp glass circle with only the progress ring; tap expands (width animation) to the full capsule with cover, title, ring, next; collapses after 6 s, on list drag, or by tapping outside gestures; tap on the expanded body opens the full player; swipe gestures only when expanded; tint follows the cover color through LiveGlass.setTint.
- Ui.shape maps R.color.surface to glass_row (translucent rows), Ui.glow got a second glow at the bottom, toast margin 150 dp, PlayerPanel wraps seek bar + transport buttons in a glass card (bg_glass gradient).
- First thing next: run the GitHub Actions build and fix any compile error reported.

# Mini player in the tab bar, new player screen, white accent, app icon switcher - not compiled, not run on a device
- Mini player is now part of the bar: while something is loaded the tab bar opens a 64 dp gap in the middle (FloatingNav.setGap, tabs slide apart) and MiniPlayer (collapsed = the progress ring circle, no glass of its own) sits in that gap. Tap -> the tabs close the gap and the circle grows (width + bottom margin animation, glass alpha fades in) into a full-width glass capsule floating right above the bar; tap the capsule body to open the full player; it collapses after 6 s, on list drag or on tab change. MiniPlayer.setDock(collapsedBottom, expandedBottom, availW) / setOnExpand; MainActivity.applyDock drives gap + padding.
- Full player (PlayerPanel): glass pill with live EqView on top, cover up to 360 dp with Halo.java (cover-colored glow that breathes while playing, off in battery saver / when hidden), 28 sp title, glass Up-next chip, and ONE glass deck (bg_glass_deck) holding the wave seek bar, prev/play/next and the six extra buttons (flat icons). CoverBox.setHalo draws the glow 1.5x the cover size (clipChildren off up the tree).
- Accent: AccentWhite (index 8, light = black accent, dark = AMOLED black screen with white highlights, no glow). SYSTEM moved from 8 to 9; pref key is now "accent2" and the old "accent" value 8 is migrated to 9 in Accent.index. The check mark on the white swatch turns black.
- App icon: IconActivity (Settings > General > App icon, 3-column grid) + IconSwitcher. Manifest: MainActivity lost its LAUNCHER filter; nine activity-alias entries (IconBlue default enabled, IconViolet, IconGreen, IconPink, IconRed, IconOrange, IconGold, IconMidnight, IconCharcoal) each with its own mipmap (adaptive icon in mipmap-anydpi-v26, layer-list in mipmap-anydpi); foregrounds are tinted copies of ic_launcher_fg, backgrounds copies of ic_launcher_back. apply() enables the chosen alias first, then disables the others with DONT_KILL_APP; current() reads the real state from PackageManager.
- First thing next: run the GitHub Actions build and fix any compile error reported.

# Lock screen redesign + glass header capsule - not compiled, not run on a device
- LockActivity: system status bar hidden (WindowInsetsControllerCompat.hide(statusBars), re-applied in onWindowFocusChanged; transient swipe still works); thin 76 sp clock (weight 200), spaced date, live EqView in the app chip, cover with the breathing Halo glow (CoverBox.setHalo), glass deck panel (bg_glass_deck), np.onRefresh drives eq + halo.
- MainActivity header is now a glass capsule (Glass.live over `main`, radius 28) inside `topOverlay` floating over the list: title 24 sp + count + sort button, the selection bar swaps in the same capsule, the search field (Search tab) lives inside it and makes it grow, the permission banner sits right under it. The list scrolls under the capsule: rv top padding = overlay height + 8 dp (applyTop, on every overlay layout change), root top padding is zeroed after the insets listener read it (insetTop), SwipeRefresh offset and skeleton margin follow. topGlass is paused with the tab bar while the full player is open.
- First thing next: run the GitHub Actions build and fix any compile error reported.

# Flat list rows, live sheet blur, ring seek thumb - not compiled, not run on a device
- Rows: Ui.flatRow(c, card, highlight, divider, insetStartDp, strokeCol) replaces the grouped "first/last corner" cards in TrackAdapter, QueueSheet and the playlists list (MainActivity): transparent row, soft ripple, hairline under the text only; the playing / selected row alone gets a tinted rounded card; "New playlist" keeps the tinted card. Settings groups still use Ui.shape cards.
- Bottom sheets / dialogs (Glass.sheet): LiveGlass over the activity decor (cross-window: owner and source measured with getLocationOnScreen), blur radius 6, saturation 1.15, tint glass_sheet_tint, setSheet(true) extends the rim below the window, setTicking(true) re-invalidates every 160 ms so it stays live; SnapGlass is only the fallback when there is no activity.
- WavePainter: the thumb is a round ring (accent outer, white inner; dark inner when the accent is white) like One UI, wave unchanged. The media notification in the shade is drawn by the system (One UI), the app only supplies title / artist / artwork / actions.
- First thing next: run the GitHub Actions build and fix any compile error reported.
