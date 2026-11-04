package com.shopmanager.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.security.PinAttemptThrottle
import com.shopmanager.app.ui.theme.AppThemeMode
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Replaces the old debt-app `auth.js` / `security.js` - those files existed
 * in the repo but were never loaded by index.html (dead code, no real
 * protection existed). This is a small but genuinely working local PIN lock,
 * useful for a shared shop device: nothing fancy, no Firebase account needed,
 * just a 4-6 digit PIN hashed and stored locally.
 *
 * SECURITY FIX: the PIN used to be stored as a single unsalted SHA-256
 * hash — fast to brute-force offline (no per-install salt means a
 * precomputed table works across every install) if the prefs file were
 * ever pulled off a rooted/backed-up device, and nothing stopped unlimited
 * guesses from the lock screen itself either. Now: a random per-install
 * salt is generated the moment a PIN is (re)set, and the stored hash is
 * PBKDF2 with many iterations (deliberately slow) instead of one plain
 * SHA-256 pass; [verifyPin] is also gated by [pinThrottle] so repeated
 * wrong guesses lock the screen out for a growing cooldown instead of
 * allowing instant unlimited retries. Anyone who already had a PIN set
 * under the old scheme is migrated transparently the moment they type it
 * correctly once (see [verifyPin]) — nobody has to re-set their PIN.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shop_manager_settings", Context.MODE_PRIVATE)

    private val pinThrottle = PinAttemptThrottle(context, "shop_manager_pin_throttle")

    /** Seconds left before the lock screen accepts another PIN attempt. */
    fun pinLockRemainingSeconds(): Long = pinThrottle.lockRemainingSeconds()

    var themeMode: AppThemeMode
        // BUG FIX: valueOf() threw IllegalArgumentException (app crash on
        // launch) if the stored string wasn't a current enum name; fall back
        // to SYSTEM like performanceMode below already does.
        get() = runCatching {
            AppThemeMode.valueOf(prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
        }.getOrDefault(AppThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** Currency label shown across the app (money amounts, share text, notifications). */
    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, "ل.س") ?: "ل.س"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value.ifBlank { "ل.س" }).apply()

    /** Master switch for the shortage-list / new-debt local notifications. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    /** صوت إشعار كل نوع (نغمات آيفون-ستايل / نغمة من الجهاز / صامت) — يُدار من الإعدادات. */
    fun soundFor(kind: com.shopmanager.app.data.notifications.NotifKind): com.shopmanager.app.data.notifications.NotifSound =
        com.shopmanager.app.data.notifications.NotifSound.fromKey(prefs.getString("notif_sound_${kind.prefKey}", null))
            ?: kind.defaultSound

    /** نغمة مختارة من الجهاز (تُستعمل عندما يكون الصوت = DEVICE). */
    fun customSoundUri(kind: com.shopmanager.app.data.notifications.NotifKind): String? =
        prefs.getString("notif_sound_uri_${kind.prefKey}", null)

    fun setCustomSoundUri(kind: com.shopmanager.app.data.notifications.NotifKind, uri: String?) =
        prefs.edit().putString("notif_sound_uri_${kind.prefKey}", uri).apply()

    fun setSoundFor(
        kind: com.shopmanager.app.data.notifications.NotifKind,
        sound: com.shopmanager.app.data.notifications.NotifSound
    ) = prefs.edit().putString("notif_sound_${kind.prefKey}", sound.key).apply()

    /**
     * "المزامنة الفورية بالخلفية": خدمة أمامية تبقي مستمعات Firestore حيّة والتطبيق
     * مغلق ليصل الإشعار لحظياً (انظر RealtimeSyncService). لكل جهاز اختياره —
     * يمكن إيقافها على الهاتف الضعيف ليعود للفحص الدوري فقط.
     */
    var realtimeSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_REALTIME_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_REALTIME_SYNC, value).apply()

    /** "تفضيل الأداء" — manual override of the auto-detected device tier
     * (see [PerformanceMode]). Defaults to AUTO so nobody's experience
     * changes unless they open Settings and pick something else. */
    var overdueReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERDUE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERDUE_ENABLED, value).apply()

    var overdueDays: Int
        get() = prefs.getInt(KEY_OVERDUE_DAYS, 30).coerceIn(1, 365)
        set(value) = prefs.edit().putInt(KEY_OVERDUE_DAYS, value.coerceIn(1, 365)).apply()

    var performanceMode: PerformanceMode
        // قيمة "BALANCED" المحفوظة من إصدار سابق (لم يعد خياراً يدوياً) أو أي قيمة غير معروفة ← AUTO.
        get() = runCatching {
            PerformanceMode.valueOf(prefs.getString(KEY_PERFORMANCE_MODE, PerformanceMode.AUTO.name)!!)
        }.getOrDefault(PerformanceMode.AUTO)
        set(value) = prefs.edit().putString(KEY_PERFORMANCE_MODE, value.name).apply()

    /** "رابط فحص التحديثات" — a JSON manifest URL (versionCode/versionName/
     * apkUrl/notes) the normal-user "تحقق من التحديثات" button in Settings
     * reads from. Set only from the hidden developer panel (لوحة المسؤول
     * السرية) — a regular user never sees or edits this, they just tap
     * "تحقق من التحديثات" and this URL is what gets checked. Empty by
     * default so a fresh install with no manifest configured yet fails
     * quietly/gracefully instead of hitting a placeholder URL. */
    /** "رابط فحص التحديثات" — the JSON/GitHub-release URL the normal-user
     * "تحقق من التحديثات" button in Settings reads from. Defaults to this
     * repo's own GitHub Releases API URL (see
     * UpdateChecker.defaultManifestUrl — built automatically from
     * BuildConfig.GITHUB_REPO at CI build time, no link ever needs typing
     * in by hand). A value explicitly saved from the developer panel
     * (لوحة المسؤول السرية) still overrides that default, for anyone who
     * wants to point updates somewhere else. */
    var updateManifestUrl: String
        get() = prefs.getString(KEY_UPDATE_MANIFEST_URL, null)
            ?: com.shopmanager.app.data.updates.UpdateChecker.defaultManifestUrl()
        set(value) = prefs.edit().putString(KEY_UPDATE_MANIFEST_URL, value.trim()).apply()

    /** Clears any manually-saved override so [updateManifestUrl] goes back
     * to auto-resolving this repo's GitHub Releases URL. */
    fun resetUpdateManifestUrlToDefault() {
        prefs.edit().remove(KEY_UPDATE_MANIFEST_URL).apply()
    }

    /** Timestamp (epoch millis) of the last time anyone — dev or user —
     * successfully reached the update manifest, shown in the admin panel
     * as a quick "is the update server even reachable" signal. */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    /** "تفعيل التحديث الاجباري" — master on/off switch for the automatic,
     * blocking update check MainActivity runs on every cold start (see
     * ForceUpdateScreen). Defaults to on so existing behavior doesn't
     * change for anyone until a developer opens لوحة المسؤول and turns it
     * off. Switching this off never touches the separate, always-available
     * manual "تحقق من التحديثات" button in Settings — that one is
     * unaffected either way. */
    var forceUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORCE_UPDATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_UPDATE_ENABLED, value).apply()

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    /** "فتح بالبصمة أو الوجه" on the lock screen. On by default; only has any
     * effect when a PIN exists and the phone has an enrolled biometric. */
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** FEATURE ADDED ("ميزة FLAG_SECURE"): while a PIN is set, block screenshots/screen
     * recording and hide the app's preview in the recent-apps list. See MainActivity.applySecureFlag. */
    var secureScreen: Boolean
        get() = prefs.getBoolean(KEY_SECURE_SCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_SECURE_SCREEN, value).apply()

    /** FEATURE ADDED ("يطلب البصمة كل مرة"): re-lock whenever the app leaves the
     * screen (home, app switch, screen off). On by default; only matters when a PIN exists. */
    var autoLockOnLeave: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOCK, value).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hashSalted(pin, salt, PIN_HASH_ITERATIONS))
            // عدد الدورات يُحفظ مع الـ hash: يسمح برفعه مستقبلاً دون كسر
            // الـ PINs المحفوظة، وترقية القديم تلقائيًا عند أول إدخال صحيح.
            .putInt(KEY_PIN_ITERATIONS, PIN_HASH_ITERATIONS)
            .apply()
        pinThrottle.registerSuccess()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).remove(KEY_PIN_ITERATIONS).apply()
        pinThrottle.registerSuccess()
    }

    /**
     * @return true if [pin] is correct. Locked out (see [pinLockRemainingSeconds])
     * always returns false without even comparing the PIN, so a lockout
     * can't be raced by spamming attempts while it's counting down.
     */
    fun verifyPin(pin: String): Boolean {
        if (pinThrottle.isLocked()) return false
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_PIN_SALT, null)

        val correct = if (saltHex != null) {
            // PINs المحفوظة قبل هذا التعديل ما لها عدد دورات مخزّن = القيمة
            // القديمة (12,000)، فيتحقق منها بنفس الطريقة ثم تُرقّى أدناه.
            val iterations = prefs.getInt(KEY_PIN_ITERATIONS, LEGACY_PIN_HASH_ITERATIONS)
            val computed = hashSalted(pin, saltHex.fromHex(), iterations)
            // SECURITY: مقارنة بزمن ثابت — == على النصوص تتوقف عند أول حرف
            // مختلف فيسرّب (نظريًا) كم حرف طابق.
            val match = MessageDigest.isEqual(computed.toByteArray(), storedHash.toByteArray())
            // ترقية صامتة إلى عدد الدورات الحالي بعد إدخال صحيح (نملك الـ PIN
            // الصريح هذه اللحظة فقط؛ لا يمكن ترقيته بأي وقت آخر).
            if (match && iterations < PIN_HASH_ITERATIONS) setPin(pin)
            match
        } else {
            // Legacy unsalted-SHA-256 PIN from before this fix. Verify it
            // the old way once, and if it matches, silently upgrade
            // storage to the salted scheme so this branch is never taken
            // again for this install.
            val legacyMatch = storedHash == legacyHash(pin)
            if (legacyMatch) setPin(pin)
            legacyMatch
        }

        if (correct) pinThrottle.registerSuccess() else pinThrottle.registerFailure()
        return correct
    }

    private fun hashSalted(value: String, salt: ByteArray, iterations: Int): String {
        val chars = value.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            return key.encoded.toHex()
        } finally {
            // لا نترك الـ PIN الصريح بالذاكرة أطول من اللازم.
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun legacyHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { i -> ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte() }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_OVERDUE_ENABLED = "overdue_reminder_enabled"
        private const val KEY_OVERDUE_DAYS = "overdue_days"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_BIOMETRIC = "biometric_unlock"
        private const val KEY_AUTO_LOCK = "auto_lock_on_leave"
        private const val KEY_SECURE_SCREEN = "secure_screen"
        // 60,000 (كانت 12,000): مضاعفة كلفة التخمين offline 5 مرات، وما زال
        // التحقق ~100ms أو أقل حتى على الهواتف الضعيفة. الأهم من الرقم نفسه:
        // الملف الذي يحوي الـ hash صار مستثنى من النسخ الاحتياطي (backup_rules).
        private const val PIN_HASH_ITERATIONS = 60_000
        private const val LEGACY_PIN_HASH_ITERATIONS = 12_000
        private const val KEY_PIN_ITERATIONS = "pin_iterations"
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_REALTIME_SYNC = "realtime_sync_enabled"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_UPDATE_MANIFEST_URL = "update_manifest_url"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check_at"
        private const val KEY_FORCE_UPDATE_ENABLED = "force_update_enabled"
    }
}
