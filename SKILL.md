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
- Plain-only lyrics are turned into estimated sync (LyricsRepo.estimate: lines weighted by length over the song duration). Tapping the line being sung re-anchors the timings (LyricsRepo.anchored) and stores them in the cache as "manual"; long-press seeks to that line.
