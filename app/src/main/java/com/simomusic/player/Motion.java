package com.simomusic.player;

import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

public final class Motion {
    private Motion() {
    }

    public static final long FAST = 120;
    public static final long SHORT = 200;
    public static final long BASE = 300;
    public static final long LONG = 400;
    public static final long AMBIENT = 600;
    public static final long PULSE = 900;

    public static final float STIFF_OPEN = 380f;
    public static final float STIFF_CLOSE = 520f;

    public static final Interpolator ENTER = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator EXIT = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator SPRING = new OvershootInterpolator(1.3f);
    public static final Interpolator POP = new OvershootInterpolator(2.2f);
}
