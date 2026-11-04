package com.shopmanager.app.data.security

import android.content.Context

/**
 * Shared brute-force throttle for any locally-stored PIN/password check —
 * used by both the main app-lock PIN ([com.shopmanager.app.data.settings.SettingsRepository])
 * and the separate hidden "لوحة المسؤول" developer-panel password
 * (DashboardScreen's AdminPinDialog).
 *
 * SECURITY FIX: neither of those had any limit on wrong guesses before.
 * A 4-6 digit numeric PIN is at most a million combinations — with no
 * delay and no lockout, all of them are guessable directly from the
 * on-screen keypad in a short sitting, no tools required. This adds the
 * same escalating cooldown to both: a few free wrong tries, then a short
 * lockout that grows the more it keeps failing. State is persisted (own
 * SharedPreferences slice per [prefsName]) so the lockout survives the
 * screen, the app process, or the device restarting — it can't be
 * bypassed by just relaunching the app.
 */
class PinAttemptThrottle(context: Context, prefsName: String) {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Seconds remaining before another attempt is allowed; 0 if not locked. */
    fun lockRemainingSeconds(): Long {
        val until = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        return ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    fun isLocked(): Boolean = lockRemainingSeconds() > 0

    /** Call after a wrong PIN/password entry. Escalates the lockout the
     * more consecutive failures pile up, instead of a single fixed delay
     * that's either too lenient early on or too harsh for one mistyped
     * digit. */
    fun registerFailure() {
        val attempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1
        val lockSeconds = when {
            attempts < 5 -> 0L
            attempts < 8 -> 30L
            attempts < 12 -> 120L
            else -> 300L
        }
        val editor = prefs.edit().putInt(KEY_ATTEMPTS, attempts)
        if (lockSeconds > 0) editor.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + lockSeconds * 1000)
        editor.apply()
    }

    /** Call after a correct entry — clears the failure count so the next
     * mistake (whenever it happens) starts counting from zero again. */
    fun registerSuccess() {
        prefs.edit().remove(KEY_ATTEMPTS).remove(KEY_LOCK_UNTIL).apply()
    }

    companion object {
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_LOCK_UNTIL = "lock_until"
    }
}
