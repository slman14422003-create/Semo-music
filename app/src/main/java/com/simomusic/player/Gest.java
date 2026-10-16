package com.simomusic.player;

import android.content.Context;
import android.view.ViewConfiguration;

public final class Gest {
    private Gest() {
    }

    public static float fling(Context c, int dp) {
        return Math.min(Ui.dp(c, dp), ViewConfiguration.get(c).getScaledMaximumFlingVelocity() * 0.6f);
    }

    public static boolean horizontal(float dx, float dy, int slop) {
        return Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.15f;
    }

    public static volatile boolean busy;

    public static boolean downLoose(float dx, float dy, int slop) {
        return dy > slop && dy > Math.abs(dx) * 0.7f;
    }

    public static boolean down(float dx, float dy, int slop) {
        return dy > slop && dy > Math.abs(dx) * 1.1f;
    }

    public static boolean up(float dx, float dy, int slop) {
        return -dy > slop && -dy > Math.abs(dx) * 1.1f;
    }

    public static float rubber(float t, float range) {
        float a = Math.abs(t);
        if (a <= range) return t;
        float extra = a - range;
        float eased = range + range * 0.35f * (1f - 1f / (1f + extra / range));
        return Math.signum(t) * eased;
    }
}
