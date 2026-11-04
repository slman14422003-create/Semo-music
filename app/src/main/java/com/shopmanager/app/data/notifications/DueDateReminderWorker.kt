package com.shopmanager.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.settings.SettingsRepository
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DueDateReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        FirebaseModule.init(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)
        val key = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        if (!SettingsRepository(applicationContext).notificationsEnabled) return Result.success()
        NotificationHelper.showDueReminderNotification(applicationContext, key, title, body)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "reminder_key"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"

        fun uniqueName(key: String) = "due_reminder_$key"

        fun enqueue(context: Context, key: String, atMillis: Long, title: String, body: String) {
            val delay = atMillis - System.currentTimeMillis()
            if (delay <= 0) return
            val request = OneTimeWorkRequestBuilder<DueDateReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to key, KEY_TITLE to title, KEY_BODY to body))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(key), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, key: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(key))
        }
    }
}

object DueDateReminders {

    private const val PREFS = "shop_due_reminders"
    private const val KEY_SCHEDULED = "scheduled_v1"
    private const val REMINDER_HOUR_MS = 9L * 60L * 60L * 1000L

    private class Planned(val key: String, val at: Long, val title: String, val body: String) {
        val signature: String get() = "$at|$title|$body"
    }

    @Synchronized
    fun sync(context: Context, persons: List<Person>, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val currency = SettingsRepository(app).currencySymbol
        val nf = NumberFormat.getNumberInstance(Locale("ar"))
        val planned = ArrayList<Planned>()
        for (p in persons) {
            if (p.dueDate > 0L && p.amount > 0.0) {
                val at = p.dueDate + REMINDER_HOUR_MS
                if (at > now) {
                    planned += Planned(
                        key = "due_${p.id}",
                        at = at,
                        title = "⏰ موعد سداد اليوم",
                        body = "حان موعد سداد ${p.name}"
                    )
                }
            }
            for (i in p.installments) {
                if (i.paid || i.date <= 0L || i.id.isBlank()) continue
                val at = i.date + REMINDER_HOUR_MS
                if (at > now) {
                    planned += Planned(
                        key = "inst_${p.id}_${i.id}",
                        at = at,
                        title = "⏰ موعد قسط اليوم",
                        body = "قسط ${p.name}: ${nf.format(i.amount)} $currency"
                    )
                }
            }
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = runCatching { JSONObject(prefs.getString(KEY_SCHEDULED, null) ?: "{}") }
            .getOrDefault(JSONObject())
        val wanted = planned.associateBy { it.key }
        val keys = previous.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!wanted.containsKey(k)) DueDateReminderWorker.cancel(app, k)
        }
        val next = JSONObject()
        for (item in planned) {
            if (previous.optString(item.key) != item.signature) {
                DueDateReminderWorker.enqueue(app, item.key, item.at, item.title, item.body)
            }
            next.put(item.key, item.signature)
        }
        prefs.edit().putString(KEY_SCHEDULED, next.toString()).apply()
    }
}
