package com.shopmanager.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Fires the local notification for one "ملاحظة هامة" reminder at (roughly)
 * the time the person picked. Scheduled as a uniquely-named ONE-TIME
 * WorkManager request per note id (not a repeating one - a reminder fires
 * once) with an initial delay computed from "now" to the reminder's
 * timestamp - the same no-server-component, no-billing-change approach
 * already used for [BackgroundSyncWorker], just a one-shot instead of
 * periodic.
 *
 * Exact-alarm precision (AlarmManager + SCHEDULE_EXACT_ALARM) isn't used
 * here on purpose: that needs an extra manifest permission and (on API 31+)
 * a special one-time grant flow, for a feature that's fundamentally a
 * reminder, not a time-critical alert - WorkManager's normal "fires within
 * a short window of the requested delay" behavior is an acceptable
 * trade-off for the simpler, permission-free setup, consistent with how
 * [BackgroundSyncWorker] already favors WorkManager over AlarmManager for
 * the same reason.
 */
class NoteReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        FirebaseModule.init(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)

        val noteId = inputData.getString(KEY_NOTE_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "تذكير"
        val content = inputData.getString(KEY_CONTENT) ?: ""

        // ترابط مع مفتاح "الإشعارات" الرئيسي بالإعدادات: كان التذكير يظهر حتى لو
        // أوقف الشخص الإشعارات من التطبيق، بعكس كل أنواع الإشعارات الأخرى.
        if (!SettingsRepository(applicationContext).notificationsEnabled) return Result.success()

        NotificationHelper.showNoteReminderNotification(applicationContext, noteId, title, content)
        return Result.success()
    }

    companion object {
        private const val KEY_NOTE_ID = "note_id"
        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private fun uniqueName(noteId: String) = "note_reminder_$noteId"

        /** (Re)schedules the reminder for [noteId] at [reminderAtMillis]. A
         * reminder in the past is skipped rather than fired immediately -
         * most commonly hit by editing a note whose reminder already
         * passed without changing the time. */
        fun schedule(context: Context, noteId: String, title: String, content: String, reminderAtMillis: Long) {
            cancel(context, noteId)
            val delay = reminderAtMillis - System.currentTimeMillis()
            if (delay <= 0) return

            val request = OneTimeWorkRequestBuilder<NoteReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_NOTE_ID to noteId,
                        KEY_TITLE to title,
                        KEY_CONTENT to content
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(noteId), ExistingWorkPolicy.REPLACE, request
            )
        }

        /** Called when a note is deleted, marked done, or its reminder is
         * removed/changed - so an old reminder time can never fire after
         * the note itself no longer calls for it. */
        fun cancel(context: Context, noteId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(noteId))
        }
    }
}
