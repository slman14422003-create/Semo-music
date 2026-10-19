package com.simomusic.player;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class Bg {
    private Bg() {
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static ThreadFactory named(final String name) {
        return r -> {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(named("bg-io"));
    private static final ExecutorService QUERY = Executors.newFixedThreadPool(2, named("bg-query"));

    private static Runnable guard(final String what, final Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                CrashGuard.nonFatal(what, t);
            }
        };
    }

    public static void io(String what, Runnable r) {
        IO.execute(guard(what, r));
    }

    public static void query(String what, Runnable r) {
        QUERY.execute(guard(what, r));
    }

    public static void main(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else MAIN.post(r);
    }
}
