package com.simomusic.player;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Safety net: every crash is written to a report file before the process dies (so it can be shared and fixed),
 * non-fatal errors caught by the app's own guards are logged to the same file, and the next start offers to share
 * the report. Nothing is swallowed silently.
 */
public final class CrashGuard {
    private CrashGuard() {
    }

    private static final String FILE = "crash_report.txt";
    private static final int MAX = 48 * 1024;
    private static Application app;

    public static void install(Application a) {
        app = a;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                append("CRASH on thread " + t.getName(), e);
                Store.prefs(a).edit().putBoolean("crash_unseen", true).commit();
            } catch (Throwable ignored) {
            }
            // a failed background worker (covers, lyrics, scans, network) is logged and survived: only the main thread
            // and the player's own threads are fatal, so the app no longer closes by itself because of a side task
            if (e instanceof OutOfMemoryError) {
                try {
                    Art.trim(true);
                } catch (Throwable ignored) {
                }
            }
            if (!fatalThread(t)) {
                Log.w("SimoMusic", "survived crash on " + t.getName(), e);
                return;
            }
            if (prev != null) prev.uncaughtException(t, e);
            else System.exit(2);
        });
    }

    private static boolean fatalThread(Thread t) {
        if (t == null || t == android.os.Looper.getMainLooper().getThread()) return true;
        String n = t.getName() == null ? "" : t.getName();
        return n.contains("ExoPlayer") || n.contains("Playback") || n.contains("Media") || n.contains("main");
    }

    private static String reasonName(int r) {
        switch (r) {
            case 1: return "EXIT_SELF";
            case 2: return "SIGNALED (killed by the system or a task killer)";
            case 3: return "LOW_MEMORY (the system needed the memory)";
            case 4: return "CRASH (java)";
            case 5: return "CRASH_NATIVE";
            case 6: return "ANR (app not responding)";
            case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED";
            case 13: return "OTHER";
            case 14: return "FREEZER";
            case 15: return "PACKAGE_STATE_CHANGE";
            case 16: return "PACKAGE_UPDATED";
            default: return "REASON_" + r;
        }
    }

    /**
     * Android 11+: asks the system why the previous run of the app ended and writes it to the report. Closes that never
     * pass through the java crash handler (killed for memory, native crash, not responding, killed while in the
     * background) show up here, so a sudden close always leaves a visible cause.
     */
    public static void logExits(final Application a) {
        if (Build.VERSION.SDK_INT < 30) return;
        Bg.io("exit reasons", () -> {
            android.app.ActivityManager am = (android.app.ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            java.util.List<android.app.ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(a.getPackageName(), 0, 8);
            android.content.SharedPreferences sp = Store.prefs(a);
            long seen = sp.getLong("exit_seen", 0L), newest = seen;
            boolean alarming = false;
            for (android.app.ApplicationExitInfo x : list) {
                long ts = x.getTimestamp();
                if (ts <= seen) continue;
                newest = Math.max(newest, ts);
                int r = x.getReason();
                String desc = x.getDescription();
                String body = "process=" + x.getProcessName() + " importance=" + x.getImportance()
                        + " rssKb=" + x.getRss() + " pssKb=" + x.getPss()
                        + (desc == null || desc.isEmpty() ? "" : " description=" + desc);
                try {
                    appendRaw("previous run ended: " + reasonName(r), body);
                } catch (Exception ignored) {
                }
                if (r == 5 || r == 6 || r == 7 || r == 9) alarming = true;
            }
            if (newest != seen) sp.edit().putLong("exit_seen", newest).apply();
            if (alarming) sp.edit().putBoolean("crash_unseen", true).apply();
        });
    }

    /** A problem the app recovered from by itself: logged, the app keeps running. */
    public static void nonFatal(String where, Throwable t) {
        Log.w("SimoMusic", where, t);
        try {
            append("recovered: " + where, t);
        } catch (Throwable ignored) {
        }
    }

    private static void append(String title, Throwable t) throws Exception {
        appendRaw(title, Log.getStackTraceString(t));
    }

    private static synchronized void appendRaw(String title, String body) throws Exception {
        if (app == null) return;
        File f = new File(app.getFilesDir(), FILE);
        StringBuilder sb = new StringBuilder();
        sb.append("==== ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("  ").append(title).append('\n');
        sb.append("Music ").append(version()).append(" | Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append(") | ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append(body).append('\n');
        try (FileOutputStream o = new FileOutputStream(f, true)) {
            o.write(sb.toString().getBytes("UTF-8"));
        }
        if (f.length() > MAX) {                       // keep the newest part
            byte[] all = new byte[(int) f.length()];
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                r.readFully(all);
            }
            try (FileOutputStream o = new FileOutputStream(f, false)) {
                o.write(all, all.length - MAX / 2, MAX / 2);
            }
        }
    }

    private static String version() {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static String report(Context c) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), FILE);
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                r.readFully(b);
            }
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean has(Context c) {
        File f = new File(c.getApplicationContext().getFilesDir(), FILE);
        return f.exists() && f.length() > 0;
    }

    public static void share(Context c) {
        String r = report(c);
        if (r.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Music crash report").putExtra(Intent.EXTRA_TEXT, r);
        Ui.go(c, Intent.createChooser(i, c.getString(R.string.crash_share)));
    }

    /** After a crash, the next start asks once whether to share the report. */
    public static void offer(final Activity a) {
        if (!Store.prefs(a).getBoolean("crash_unseen", false) || !has(a)) return;
        Store.prefs(a).edit().putBoolean("crash_unseen", false).apply();
        Dlg.confirm(a, a.getString(R.string.crash_title), a.getString(R.string.crash_msg), R.string.crash_share, false, () -> share(a));
    }
}
