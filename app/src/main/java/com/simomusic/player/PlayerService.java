package com.simomusic.player;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Foreground media-playback service. Media3 posts the media notification (also shown on the lock screen) and
 * keeps the service in the foreground while playing. On top of that, when the screen turns on while music is
 * playing and the device is locked, the custom lock-screen player is shown over the keyguard.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerService extends MediaSessionService {

    private MediaSession session;
    private ExoPlayer player;
    private final Handler h = new Handler(Looper.getMainLooper());

    private int tries;

    private boolean wantsLock() {
        return player != null && player.getMediaItemCount() > 0 && player.getPlayWhenReady()
                && player.getPlaybackState() != Player.STATE_ENDED
                // a call or another app that holds the audio focus suspends us: no lock screen over it
                && player.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    }

    /** Right after the screen wakes the keyguard flag can lag a moment (Xiaomi / Samsung), so retry briefly. */
    private final Runnable showLockRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!wantsLock()) return;
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) {
                    LockLauncher.show(PlayerService.this);
                } else if (++tries < 30) {
                    h.postDelayed(this, 50);
                }
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("show lock player", e);
            }
        }
    };

    /** Optional mode: a moment after you lock the phone, light the screen again with the player. */
    private final Runnable wakeAfterLock = new Runnable() {
        @Override
        public void run() {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (wantsLock() && pm != null && !pm.isInteractive()) LockLauncher.show(PlayerService.this);
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("wake after lock", e);
            }
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          try {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a)) {
                h.removeCallbacks(wakeAfterLock);
                h.removeCallbacks(showLockRunnable);
                tries = 0;
                if (Store.flag(c, "lock_auto", true)) h.post(showLockRunnable);
            } else if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                h.removeCallbacks(showLockRunnable);
                LockLauncher.clear(c);
                boolean justClosedLock = android.os.SystemClock.elapsedRealtime() - LockLauncher.lastSeen < 4000;
                if (Store.flag(c, "lock_wake", false) && !justClosedLock) h.postDelayed(wakeAfterLock, 1500);
            } else {
                LockLauncher.clear(c);
            }
          } catch (RuntimeException e) {
              CrashGuard.nonFatal("screen event", e);
          }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefs = (sp, key) -> {
        if (BtAudio.isKey(key)) BtAudio.refresh();
        if ("boost_on".equals(key) || "boost_pct".equals(key)) BoostProcessor.INSTANCE.setGain(Store.boostGain(this));
        if ("skip_silence".equals(key) && player != null) player.setSkipSilenceEnabled(sp.getBoolean(key, false));
        if ("sleep_wall".equals(key)) armSleep(sp.getLong(key, 0));
        if ("crossfade".equals(key) && player != null) {
            fadeInStart = 0;
            h.removeCallbacks(this.xfTick);
            h.post(this.xfTick);
        }
        if ("lock_auto".equals(key) && player != null) OverlayAnchor.sync(this, player.getPlayWhenReady() && sp.getBoolean(key, true));
    };

    private long sleepEnd, fadeInStart, lastShake;
    private float sleepGain = 1f, fadeGain = 1f;
    private android.hardware.SensorManager sensors;
    private boolean shaking;

    private void applyVolume() {
        if (player == null) return;
        float g = Math.min(sleepGain, fadeGain);
        if (player.getVolume() != g) player.setVolume(g);
    }

    private final android.hardware.SensorEventListener shake = new android.hardware.SensorEventListener() {
        private long first;
        private int hits;

        @Override
        public void onSensorChanged(android.hardware.SensorEvent e) {
            float g = (float) Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                    / android.hardware.SensorManager.GRAVITY_EARTH;
            if (g < 2.5f) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastShake < 2000) return;
            if (now - first > 900) {
                first = now;
                hits = 0;
            }
            if (++hits >= 4) {
                hits = 0;
                lastShake = now;
                extendSleep();
            }
        }

        @Override
        public void onAccuracyChanged(android.hardware.Sensor s, int accuracy) {
        }
    };

    private void startShake() {
        if (shaking || !Store.flag(this, "sleep_shake", true)) return;
        if (sensors == null) sensors = (android.hardware.SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensors == null) return;
        android.hardware.Sensor s = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        if (s == null) return;
        shaking = sensors.registerListener(shake, s, android.hardware.SensorManager.SENSOR_DELAY_UI);
    }

    private void stopShake() {
        if (!shaking || sensors == null) return;
        sensors.unregisterListener(shake);
        shaking = false;
    }

    @SuppressWarnings("deprecation")
    private void extendSleep() {
        try {
            Store.prefs(this).edit().putLong("sleep_wall", System.currentTimeMillis() + 10 * 60000L).apply();
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(70, 200));
                else v.vibrate(70);
            }
            android.widget.Toast.makeText(this, R.string.sleep_extended, android.widget.Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("sleep extend", e);
        }
    }

    private final Runnable xfTick = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            int secs = Store.crossfade(PlayerService.this);
            float g = 1f;
            boolean again = false;
            if (secs > 0 && player.isPlaying()) {
                again = true;
                long ms = secs * 1000L, dur = player.getDuration();
                if (dur != C.TIME_UNSET && dur > ms * 3) {
                    if (player.getRepeatMode() != Player.REPEAT_MODE_ONE && player.hasNextMediaItem()) {
                        long left = dur - player.getCurrentPosition();
                        if (left < ms) g = Math.max(0f, left / (float) ms);
                    }
                    if (fadeInStart != 0) {
                        long into = android.os.SystemClock.elapsedRealtime() - fadeInStart;
                        if (into >= ms) fadeInStart = 0;
                        else g = Math.min(g, into / (float) ms);
                    }
                } else {
                    fadeInStart = 0;
                }
            } else {
                fadeInStart = 0;
            }
            fadeGain = g * g;
            applyVolume();
            if (again) h.postDelayed(this, 100);
        }
    };

    private final Runnable sleepRun = new Runnable() {
        @Override
        public void run() {
            sleepEnd = 0;
            stopShake();
            try {
                if (player != null) {
                    player.pause();
                    h.postDelayed(() -> {
                        sleepGain = 1f;
                        applyVolume();
                    }, 700);
                }
                Store.prefs(PlayerService.this).edit().putLong("sleep_wall", 0).apply();
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("sleep timer", e);
            }
        }
    };

    private final Runnable sleepFade = new Runnable() {
        @Override
        public void run() {
            if (player == null || sleepEnd == 0) return;
            long now = android.os.SystemClock.elapsedRealtime();
            long into = now - (sleepEnd - 15000);
            if (into >= 0) {
                sleepGain = Math.max(0f, 1f - into / 15000f);
                applyVolume();
            }
            if (sleepEnd - now <= 90000) startShake();
            h.postDelayed(this, into < 0 ? Math.min(30000L, -into) : 400L);
        }
    };

    private void armSleep(long wall) {
        h.removeCallbacks(sleepRun);
        h.removeCallbacks(sleepFade);
        sleepEnd = 0;
        stopShake();
        sleepGain = 1f;
        if (player == null) return;
        applyVolume();
        long left = wall - System.currentTimeMillis();
        if (wall <= 0 || left <= 0) return;
        sleepEnd = android.os.SystemClock.elapsedRealtime() + left;
        h.postDelayed(sleepRun, left);
        h.post(sleepFade);
    }

    private final android.os.Handler saveTick = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable saveRun = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                Resume.save(PlayerService.this, player);
                saveTick.postDelayed(this, 15000);
            }
        }
    };

    private int failStreak, retries;

    /**
     * One bad file, a dropped connection or a busy audio device must never leave the player dead. Short hiccups are
     * retried on the same song; a song that cannot play is skipped; if several in a row fail, playback stops quietly.
     */
    private void recover(PlaybackException e) {
        CrashGuard.nonFatal("playback error " + e.getErrorCodeName(), e);
        final ExoPlayer p = player;
        if (p == null || p.getMediaItemCount() == 0) return;
        int code = e.errorCode;
        if (code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition();
            p.prepare();
            return;
        }
        boolean transientError = code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || code == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                || code == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED;
        if (transientError && retries < 2) {
            retries++;
            h.postDelayed(() -> {
                if (player != null && player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
            }, 1200L * retries);
            return;
        }
        retries = 0;
        if (++failStreak > Math.min(p.getMediaItemCount(), 8)) {
            failStreak = 0;     // nothing in the queue plays: stop here instead of looping forever
            return;
        }
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem();
            p.prepare();
            p.play();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        androidx.media3.exoplayer.DefaultRenderersFactory renderers = new androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            @Override
            protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                                                               boolean enableAudioTrackPlaybackParams) {
                // the sound engine runs right before the AudioTrack, so everything Android sends to the headset has passed it
                return new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{BtDspProcessor.INSTANCE, BoostProcessor.INSTANCE})
                        .build();
            }
        };
        // if a device's preferred decoder refuses a file, try the next one instead of failing the song
        renderers.setEnableDecoderFallback(true);
        // OkHttp for links opened from other apps: timeouts, retries, redirects and HTTP/2 done properly
        okhttp3.OkHttpClient http = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        androidx.media3.datasource.DefaultDataSource.Factory dataSources = new androidx.media3.datasource.DefaultDataSource.Factory(
                this, new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(http).setUserAgent("SimoMusic/" + BuildConfig.VERSION_NAME));
        androidx.media3.exoplayer.DefaultLoadControl load = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 40000, 800, 2000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player = new ExoPlayer.Builder(this, renderers)
                .setLoadControl(load)
                .setUsePlatformDiagnostics(false)
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSources))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.setSkipSilenceEnabled(Store.flag(this, "skip_silence", false));
        BoostProcessor.INSTANCE.setGain(Store.boostGain(this));
        BtAudio.attach(this, player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                recover(error);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    failStreak = 0;
                    retries = 0;
                }
            }
        });
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                OverlayAnchor.sync(PlayerService.this, playWhenReady && Store.flag(PlayerService.this, "lock_auto", true));
            }

            @Override
            public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
                Resume.saveSoon(PlayerService.this, player);
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Store.crossfade(PlayerService.this) > 0) {
                    fadeInStart = android.os.SystemClock.elapsedRealtime();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                h.removeCallbacks(xfTick);
                h.post(xfTick);
                Resume.saveSoon(PlayerService.this, player);
                saveTick.removeCallbacksAndMessages(null);
                if (playing) saveTick.postDelayed(saveRun, 15000);
            }

            @Override
            public void onTimelineChanged(androidx.media3.common.Timeline t, int reason) {
                Resume.queueChanged();
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) Resume.saveSoon(PlayerService.this, player);
            }
        });
        // home-screen widget: title, artist and play state (only does work when a widget is placed)
        player.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player p, Player.Events events) {
                PlayerWidget.sync(PlayerService.this, p);
            }
        });
        // local listening history for the AI Mix: a no-op unless the feature is turned on in Settings
        player.addListener(new Player.Listener() {
            private long curId = -1;

            @Override
            public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
                if (curId >= 0 && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    boolean finished = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT;
                    PlayStats.ended(PlayerService.this, curId, finished);
                }
                long id = -1;
                if (item != null) {
                    try {
                        id = Long.parseLong(item.mediaId);
                    } catch (NumberFormatException ignored) {
                    }
                }
                curId = id;
                if (id >= 0) PlayStats.started(PlayerService.this, id);
            }
        });
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
                h.removeCallbacks(extendTask);
                h.postDelayed(extendTask, 1500);
            }
        });
        // tapping the media notification opens the app itself with the full player up, so back / close land in the app
        PendingIntent open = PendingIntent.getActivity(this, 0, MainActivity.openPlayerIntent(this),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(open).setBitmapLoader(new ArtLoader(this)).build();
        DefaultMediaNotificationProvider np = new DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.ch_playback).build();
        np.setSmallIcon(R.drawable.ic_notif);
        setMediaNotificationProvider(np);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(this, screen, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefs);
        armSleep(Store.prefs(this).getLong("sleep_wall", 0));
    }

    private final Runnable extendTask = new Runnable() {
        @Override
        public void run() {
            if (player != null && !closing) extendQueue();
        }
    };

    private void extendQueue() {
        try {
            if (!Store.autoplay(this) || player.getRepeatMode() != Player.REPEAT_MODE_OFF || player.getShuffleModeEnabled()) return;
            int count = player.getMediaItemCount(), index = player.getCurrentMediaItemIndex();
            if (count < 2 || count - index > 2) return;
            androidx.media3.common.MediaItem cur = player.getCurrentMediaItem();
            if (cur == null) return;
            Track seed = Library.byId.get(Long.parseLong(cur.mediaId));
            if (seed == null) return;
            java.util.Set<Long> have = new java.util.HashSet<>();
            for (int i = 0; i < count; i++) {
                try {
                    have.add(Long.parseLong(player.getMediaItemAt(i).mediaId));
                } catch (NumberFormatException ignored) {
                }
            }
            java.util.List<androidx.media3.common.MediaItem> add = new java.util.ArrayList<>();
            for (Track t : AiEngine.similar(this, seed, 80)) {
                if (have.contains(t.id)) continue;
                add.add(Pb.item(t));
                if (add.size() >= 10) break;
            }
            if (!add.isEmpty()) player.addMediaItems(add);
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("autoplay", e);
        }
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo info) {
        return session;
    }

    private boolean closing;

    // ---------------------------------------------------------------- media buttons

    /** Same id Media3 uses for its media notification, so its real notification simply replaces ours. */
    private static final int FG_ID = 1001;
    private static final String FG_CHANNEL = "semo_start";

    /**
     * A media button (quick-settings media card, Bluetooth, lock screen) reaches us through startForegroundService().
     * Android kills the app if startForeground() is not called within seconds, and Media3 only does that once something
     * is actually playing. So we always satisfy the rule first with a small placeholder notification.
     */
    private void ensureForeground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(FG_CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(FG_CHANNEL, getString(R.string.ch_playback),
                        NotificationManager.IMPORTANCE_LOW));
            }
            Notification n = new NotificationCompat.Builder(this, FG_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(getString(R.string.app_name))
                    .setOngoing(true)
                    .setSilent(true)
                    .build();
            ServiceCompat.startForeground(this, FG_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("ensure foreground", e);
        }
    }

    /** A moment after a media button: if nothing ended up playing, drop the placeholder instead of leaving it behind. */
    private final Runnable settleForeground = new Runnable() {
        @Override
        public void run() {
            try {
                if (player == null || session == null || player.getMediaItemCount() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                } else if (!player.isPlaying() && !player.getPlayWhenReady()) {
                    stopForeground(STOP_FOREGROUND_DETACH);
                }
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("settle foreground", e);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final boolean mediaButton = intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction());
        if (mediaButton) {
            ensureForeground();
            h.removeCallbacks(settleForeground);
            h.postDelayed(settleForeground, 2500);
        }
        try {
            return super.onStartCommand(intent, flags, startId);
        } catch (RuntimeException e) {
            // e.g. "session is already released": a stale button from a session we closed. Never crash for that.
            CrashGuard.nonFatal("start command", e);
            boolean playing = player != null && player.getPlayWhenReady();
            if (mediaButton && !playing) {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } catch (RuntimeException ignored) {
                }
            }
            if (!playing) stopSelf();
            return START_NOT_STICKY;
        }
    }

    /**
     * Swiping the app away from recents closes it completely (setting "Close fully when removed", on by default):
     * playback stops, the position is saved for "Resume last session", notification, lock player and overlay go away,
     * the service stops (the system then drops the idle process by itself), so nothing keeps running in the background or fights another music app.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            boolean idle = session == null || player == null || !player.getPlayWhenReady()
                    || player.getMediaItemCount() == 0 || player.getPlaybackState() == Player.STATE_ENDED;
            if (idle || Store.flag(this, "stop_on_close", true)) {
                closing = true;
                h.removeCallbacksAndMessages(null);
                LockLauncher.clear(this);
                OverlayAnchor.sync(this, false);
                if (player != null) {
                    Resume.save(this, player);
                    player.pause();
                }
                PlayStats.flush(this);
                stopSelf();
                // the app's own controller keeps the service bound; releasing the session lets go of it so the stop completes
                h.postDelayed(() -> {
                    try {
                        if (App.visibleCount() > 0) {     // reopened in the meantime: keep this service alive for the new screen
                            closing = false;
                            return;
                        }
                        if (session != null) session.release();
                    } catch (RuntimeException e) {
                        CrashGuard.nonFatal("close session", e);
                    }
                }, 400);
            }
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("task removed", e);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screen);
        } catch (Exception ignored) {
        }
        OverlayAnchor.sync(this, false);
        PlayerWidget.idle(this);
        saveTick.removeCallbacksAndMessages(null);
        stopShake();
        if (player != null) Resume.save(this, player);
        PlayStats.flush(this);
        BtAudio.detach();
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefs);
        h.removeCallbacksAndMessages(null);
        try {
            if (session != null) session.release();
        } catch (RuntimeException ignored) {
        }
        try {
            if (player != null) player.release();
        } catch (RuntimeException ignored) {
        }
        session = null;
        player = null;
        super.onDestroy();
    }
}
