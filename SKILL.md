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
- Gest.busy is true while PlayerPanel moves (drag or spring). WavePainter, EqView and RingPlay stop their per-frame invalidation while it is true, so the panel layer and home layer are not re-rendered every frame. PlayerPanel.layer(false) clears it and pokes all views to restart animations.
- PlayerPanel: nested col layer removed, corner radius only animates in the first part of the drag, drag-down detection uses Gest.downLoose (diagonal drags count).

# Ideas not done yet
- Fast-scroll letter bubble on the song list.
- Crossfade between songs.
- Synced lyrics, sleep timer shake-to-extend, sections/headers per artist or album.
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
