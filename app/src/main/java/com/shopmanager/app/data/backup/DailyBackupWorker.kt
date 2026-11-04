package com.shopmanager.app.data.backup

import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.materials.MaterialsRepository
import java.util.concurrent.TimeUnit

/**
 * Runs once a day in the background and writes one local JSON snapshot of
 * everything (customers, debts, materials, prices, catalog) to the app's
 * own private storage — see [BackupManager]. Deliberately silent end to
 * end: this worker never posts a notification, on success or failure. If
 * it fails (no network yet, nothing synced at all so far, etc.) it just
 * retries on WorkManager's own backoff and tries again on the next
 * scheduled run; nothing is ever shown to the person either way.
 *
 * Scheduled with KEEP (not UPDATE, unlike BackgroundSyncWorker): a daily
 * backup's exact anchor time doesn't matter, so re-launching the app
 * should never reset an already-running schedule — only the very first
 * launch after install actually enqueues it.
 */
class DailyBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Same defensive re-init as BackgroundSyncWorker: this can run in a
        // fresh process where MainActivity never got the chance to call
        // FirebaseModule.init() first.
        FirebaseModule.init(applicationContext)

        return try {
            BackupManager.performBackup(applicationContext, DebtsRepository(), MaterialsRepository(), BackupKind.DAILY)
            Result.success()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // A network timeout (withTimeout inside the repositories) is a
            // real failure, not WorkManager cancelling us - retry a few times.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // PERF/STABILITY: genuine cancellation must propagate; it used to
            // be swallowed into Result.retry() by the broad catch below.
            throw e
        } catch (e: Exception) {
            // Capped: an endlessly failing backup used to re-run with growing
            // backoff forever. The next daily period tries again anyway.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "shop_manager_daily_backup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/**
 * Runs a single silent backup right after the person actually changes
 * something (added/edited a customer, a debt, a material, a price, a
 * catalog entry). Previously the only backup was [DailyBackupWorker],
 * which meant up to 24 hours of new debts/materials existed nowhere but
 * the live Firestore data — if that ever needed restoring from a local
 * snapshot, everything added "today" was gone. This makes every change
 * durable within seconds instead of waiting for the next scheduled day.
 *
 * Still completely silent (no notification, ever) and still local-only
 * (writes to the same `filesDir/backups` directory as the daily worker —
 * see [BackupManager]), so nothing about the "never surfaced to the
 * person" contract changes, only how often it runs.
 *
 * Implemented as WorkManager (not a raw coroutine launched straight from
 * a ViewModel) so it: (a) still finishes even if the screen is closed or
 * the ViewModel is cleared right after the add, and (b) is safe to call
 * on every single add/edit without spamming disk writes — [requestNow]
 * enqueues with `REPLACE` on a short delay, so five rapid-fire adds in a
 * row collapse into exactly one backup a moment after the last one,
 * instead of five redundant ones.
 */
class InstantBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        FirebaseModule.init(applicationContext)
        return try {
            BackupManager.performBackup(applicationContext, DebtsRepository(), MaterialsRepository(), BackupKind.INSTANT)
            applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_INSTANT, System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // BUG FIXED (instant backup silently never happening): every
            // read inside performBackup (persons/debts/materials/prices/
            // catalog) is wrapped in withTimeout(15s) — see
            // DebtsRepository/MaterialsRepository. On a slow/flaky
            // connection (exactly the case an on-device backup exists
            // for), one of those timing out throws
            // TimeoutCancellationException, which IS-A CancellationException
            // — so it used to match the `catch (CancellationException)`
            // branch below and get rethrown as if WorkManager itself had
            // cancelled this job on purpose (see that branch's comment).
            // WorkManager then marks the work CANCELLED, not FAILED, so
            // the retry logic never ran and the backup was simply lost —
            // no retry, nothing recorded, nothing surfaced. A plain
            // network timeout must retry like any other real failure, so
            // this now has to be caught ahead of the general
            // CancellationException branch (it's a subtype, so ordering
            // matters) and treated the same as any other exception below.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A newer edit replaced this one (see requestNow's REPLACE
            // policy below) — this is WorkManager cancelling us on
            // purpose, not a real failure, so it must propagate rather
            // than be swallowed into a retry/failure Result.
            throw e
        } catch (e: Exception) {
            // A single missed instant backup is not worth surfacing or
            // even retrying aggressively — the next add (or the daily
            // worker) will cover it. A light, bounded retry is enough.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "shop_manager_instant_backup"
        private const val META_PREFS = "shop_manager_backup_meta"
        private const val KEY_LAST_INSTANT = "last_instant_backup_at"
        private const val AUTO_MIN_INTERVAL_MS = 15L * 60_000L
        private const val AUTO_MIN_DELAY_MS = 2L * 60_000L
        private const val AUTO_ENQUEUE_GUARD_MS = 60_000L

        @Volatile
        private var lastDeferredEnqueueAt = -AUTO_ENQUEUE_GUARD_MS

        /**
         * PERF: للتحديثات التي **نراها** من المستمعات (أي جهاز كان مصدرها، بما فيها
         * التحميل الأولي عند كل فتح للتطبيق). كان كل لقطة تُنشئ طلب نسخ احتياطي
         * جديداً (REPLACE) — أي كتابة/إلغاء في قاعدة WorkManager عدة مرات في الثانية
         * الأولى، ثم نسخة كاملة تقرأ كل المجموعات من Firestore عند كل فتح.
         *
         * الآن: طلب واحد مؤجَّل يُحفظ بـ KEEP (الطلبات المتتالية لا تفعل شيئاً)، ولا
         * تُنشأ نسخة جديدة قبل مرور 15 دقيقة على آخر نسخة ناجحة. لا يضيع أي تغيير:
         * النسخة المؤجَّلة تقرأ الحالة الحالية وقت تشغيلها. أما الحفظ المحلي من هذا
         * الجهاز فيبقى عبر [requestNow] فوراً كما كان.
         */
        fun requestDeferred(context: Context) {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastDeferredEnqueueAt < AUTO_ENQUEUE_GUARD_MS) return
            lastDeferredEnqueueAt = nowElapsed

            val last = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_INSTANT, 0L)
            val sinceLast = System.currentTimeMillis() - last
            val delayMs = (AUTO_MIN_INTERVAL_MS - sinceLast)
                .coerceAtLeast(AUTO_MIN_DELAY_MS)
                .coerceAtMost(AUTO_MIN_INTERVAL_MS)

            val request = OneTimeWorkRequestBuilder<InstantBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Call after any successful add/edit/delete of a person, debt,
         * material, price, or catalog item. Debounced by a couple of
         * seconds via REPLACE so a burst of edits (e.g. importing several
         * debts back to back) doesn't trigger a Firestore re-read and
         * disk write for every single one.
         */
        fun requestNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<InstantBackupWorker>()
                .setConstraints(constraints)
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
