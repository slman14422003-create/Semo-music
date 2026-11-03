package com.simomusic.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** The launcher icon is chosen by enabling exactly one of the activity-alias entries in the manifest. */
public final class IconSwitcher {
    private IconSwitcher() {
    }

    private static final String[] ALIAS = {
            "IconBlue", "IconViolet", "IconGreen", "IconPink", "IconRed", "IconOrange", "IconGold", "IconMidnight", "IconCharcoal"};

    public static final int[] NAME = {
            R.string.icon_blue, R.string.icon_violet, R.string.icon_green, R.string.icon_pink, R.string.icon_red,
            R.string.icon_orange, R.string.icon_gold, R.string.icon_midnight, R.string.icon_charcoal};

    public static final int[] BACK = {
            R.drawable.ic_launcher_back, R.drawable.ic_launcher_back_violet, R.drawable.ic_launcher_back_green,
            R.drawable.ic_launcher_back_pink, R.drawable.ic_launcher_back_red, R.drawable.ic_launcher_back_orange,
            R.drawable.ic_launcher_back_gold, R.drawable.ic_launcher_back_midnight, R.drawable.ic_launcher_back_charcoal};

    public static final int[] FRONT = {
            R.drawable.ic_launcher_fg, R.drawable.ic_launcher_fg_violet, R.drawable.ic_launcher_fg_green,
            R.drawable.ic_launcher_fg_pink, R.drawable.ic_launcher_fg_red, R.drawable.ic_launcher_fg_orange,
            R.drawable.ic_launcher_fg_gold, R.drawable.ic_launcher_fg_midnight, R.drawable.ic_launcher_fg_charcoal};

    public static int count() {
        return ALIAS.length;
    }

    private static ComponentName comp(Context c, int i) {
        return new ComponentName(c, "com.simomusic.player." + ALIAS[i]);
    }

    /** The enabled alias, read from the system so it is right even after a reinstall or a backup restore. */
    public static int current(Context c) {
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < ALIAS.length; i++) {
            try {
                int st = pm.getComponentEnabledSetting(comp(c, i));
                boolean on = st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0);
                if (on) return i;
            } catch (RuntimeException ignored) {
            }
        }
        return 0;
    }

    public static void apply(Context c, int index) {
        PackageManager pm = c.getPackageManager();
        try {
            pm.setComponentEnabledSetting(comp(c, index), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (int i = 0; i < ALIAS.length; i++) {
                if (i == index) continue;
                pm.setComponentEnabledSetting(comp(c, i), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("app icon", e);
        }
    }
}
