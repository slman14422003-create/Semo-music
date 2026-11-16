package com.simomusic.player;

import android.os.SystemClock;
import android.view.View;

public final class LockHandoff {
    private LockHandoff() {
    }

    private static int[] box;
    private static long at;

    public static void mark(View cover) {
        try {
            int[] l = new int[2];
            cover.getLocationOnScreen(l);
            int w = Math.round(cover.getWidth() * cover.getScaleX());
            int h = Math.round(cover.getHeight() * cover.getScaleY());
            if (w <= 0 || h <= 0) return;
            box = new int[]{l[0] + (cover.getWidth() - w) / 2, l[1] + (cover.getHeight() - h) / 2, w, h};
            at = SystemClock.uptimeMillis();
        } catch (RuntimeException e) {
            box = null;
        }
    }

    /**
     * Opens the full player from the lock screen. The animation is passed as launch options (not only as
     * overridePendingTransition), which is what One UI (Samsung) honors for a launch that comes from another task.
     */
    public static void launchPlayer(android.app.Activity a) {
        android.content.Intent i = MainActivity.openPlayerIntent(a);
        try {
            android.os.Bundle o = android.app.ActivityOptions.makeCustomAnimation(a, R.anim.fade_in, R.anim.fade_out).toBundle();
            a.startActivity(i, o);
        } catch (RuntimeException e) {
            Ui.go(a, i);
        }
    }

    public static int[] take() {
        int[] b = box;
        box = null;
        if (b == null || SystemClock.uptimeMillis() - at > 6000L) return null;
        return b;
    }
}
