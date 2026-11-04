package com.shopmanager.app.data.notifications

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * FEATURE ADDED ("حسن عمل التطبيق في الخلفية بشكل مخفي"): [BackgroundSyncWorker]
 * and [NoteReminderWorker] are both plain WorkManager jobs, which on stock
 * Android is enough - but on the aggressive battery managers most people's
 * phones actually ship with (Xiaomi/MIUI, Huawei, Oppo/ColorOS, Samsung's
 * stricter modes, ...) the OS silently freezes or drops any app's
 * background work the moment it decides the app is "idle", regardless of
 * what WorkManager itself asked for. The visible symptom is exactly what
 * was reported: the debts/shortage-list sync notification and note
 * reminders quietly stop firing once the app has been closed for a while,
 * with nothing in the UI to explain why.
 *
 * The one real fix for that is asking the OS to exempt this specific app
 * from battery optimization (`isIgnoringBatteryOptimizations`) - Android
 * still requires one explicit system-dialog tap for this (no permission
 * lets an app grant this to itself silently), but everything *around* that
 * one unavoidable tap is kept invisible: no in-app banner, no repeated
 * nagging - [maybeRequest] fires the system dialog itself at most once
 * ever per install (see [KEY_ASKED]), the moment the app is opened, and
 * never asks again whether the person allows or dismisses it.
 */
object BatteryOptimizationHelper {

    private const val PREFS = "shop_manager_battery_opt"
    private const val KEY_ASKED = "asked_once"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isIgnoringOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Shows the system "تجاهل تحسينات البطارية" prompt at most once ever
     * for this install, and only if the app isn't already exempt. Safe to
     * call unconditionally on every cold start - after the first call
     * (accepted or dismissed, doesn't matter which) this becomes a no-op
     * forever, so it never turns into a repeated interruption.
     */
    @SuppressLint("BatteryLife")
    fun maybeRequest(activity: Activity) {
        val prefs = prefs(activity)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()

        if (isIgnoringOptimizations(activity)) return

        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}")
        )
        runCatching { activity.startActivity(intent) }
    }
}
