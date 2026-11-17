package com.simomusic.player;

import java.util.Locale;

public final class Junk {
    private Junk() {
    }

    private static final String[] NAMES = {"key_press", "keypress", "key press", "key_click", "keyclick", "keystroke",
            "voice_start", "voice_end", "voice_cancel", "voice_stop", "voice_error", "keyboard_", "typing_", "sfx_",
            "beep", "notification_", "notify_", "shutter", "charging_", "low_battery"};

    private static final String[] FOLDERS = {"keyboard", "android/", "/ui/", "ui sounds", "notifications", "alarms", "ringtones", "system sounds"};

    public static boolean is(String fileName, String folder, long durMs) {
        if (durMs < 2000) return true;
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        for (String p : NAMES) if (n.startsWith(p) && durMs < 20000) return true;
        if (durMs < 30000 && folder != null) {
            String f = folder.toLowerCase(Locale.ROOT);
            for (String p : FOLDERS) if (f.contains(p)) return true;
        }
        return false;
    }
}
