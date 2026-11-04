package com.shopmanager.app.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.debts.OverdueRule
import com.shopmanager.app.data.settings.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class OverdueDebtsWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        if (!settings.notificationsEnabled || !settings.overdueReminderEnabled) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_NOTIFIED, 0L) < MIN_GAP_MS) return Result.success()

        FirebaseModule.init(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)

        return try {
            val (persons, debts) = DebtsRepository().fetchAllForBackup()
            val days = settings.overdueDays
            val names = OverdueRule.compute(persons, debts, days, now).map { it.name }.sorted()
            if (names.isNotEmpty()) {
                NotificationHelper.showOverdueDebtsNotification(applicationContext, names, days)
                prefs.edit().putLong(KEY_LAST_NOTIFIED, now).apply()
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "shop_manager_overdue_debts"
        private const val PREFS = "shop_manager_overdue"
        private const val KEY_LAST_NOTIFIED = "last_notified_at"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_GAP_MS = 3L * DAY_MS

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OverdueDebtsWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
