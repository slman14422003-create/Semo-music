package com.shopmanager.app.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.Source
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.performance.DevicePerformance
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.settings.SettingsRepository
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * الشبكة الاحتياطية للإشعارات: فحص دوري رخيص (15/30 دقيقة كحد أدنى يفرضه
 * WorkManager) يعمل حين لا تكون الخدمة الأمامية [RealtimeSyncService] حيّة —
 * مثلاً عند إيقاف "المزامنة الفورية" من الإعدادات، أو ريثما يعيد النظام تشغيلها
 * بعد أن يقتلها.
 *
 * ملاحظة صادقة: إشعار لحظي حقيقي والتطبيق مغلق تماماً بدون خدمة أمامية يحتاج
 * FCM + Cloud Function (خطة Blaze). الـ Worker وحده لا يضمن ذلك: أندرويد يؤجّله
 * أو يجمّده حسب Doze وحماية البطارية.
 *
 * ما تغيّر:
 * - كل منطق "هل هذا جديد وجاء من جهاز آخر؟" انتقل إلى [RemoteChangeProcessor]
 *   (خط أساس واحد + سجل ما كتبه هذا الجهاز [SelfChangeLedger]) — فلا يتعارض مع
 *   الخدمة/التطبيق، ولا يُشعِر الجهاز الذي أضاف العنصر بنفسه.
 * - قراءة أقل: نقرأ مجموعة الديون فقط، ولا نجلب اسم العميل إلا للديون الجديدة
 *   فعلاً (كنا نقرأ مجموعة العملاء كاملة في كل دورة حتى لو لم يتغيّر شيء).
 * - لا عمل إطلاقاً إذا كانت الخدمة الأمامية تعمل (تلتقط كل شيء أصلاً).
 */
class BackgroundSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (RealtimeSyncService.isRunning) return Result.success()

        // قد نعمل في عملية جديدة (التطبيق مقتول) لم تمرّ بـ MainActivity.
        FirebaseModule.init(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)

        val settings = SettingsRepository(applicationContext)
        if (!settings.notificationsEnabled) return Result.success()

        return try {
            checkNewDebts(settings)
            checkShortageList(settings)
            checkNewNotes(settings)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // انقطاع/شبكة — نعيد المحاولة في الدورة التالية بدل تكرار سريع.
            Result.retry()
        }
    }

    private suspend fun checkNewDebts(settings: SettingsRepository) {
        val db = FirebaseModule.debtsDb
        val snapshot = db.collection("debts").get(Source.SERVER).await()
        val records = snapshot.documents.map { doc ->
            DebtRecord(doc.id, doc.getString("personId") ?: "", doc.getDouble("amount") ?: 0.0)
        }
        val fresh = RemoteChangeProcessor.newDebts(applicationContext, records, true)
        if (fresh.isEmpty()) return

        val names = HashMap<String, String>()
        for (personId in fresh.map { it.personId }.distinct()) {
            if (personId.isBlank()) continue
            names[personId] = try {
                db.collection("persons").document(personId).get().await().getString("name") ?: "عميل"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "عميل"
            }
        }
        for (debt in fresh) {
            NotificationHelper.showNewDebtNotification(
                applicationContext,
                names[debt.personId] ?: "عميل",
                RemoteChangeProcessor.formatAmount(debt.amount),
                settings.currencySymbol,
                debt.id
            )
        }
    }

    private suspend fun checkShortageList(settings: SettingsRepository) {
        val snapshot = FirebaseModule.materialsDb.collection("spices_final_v12").get(Source.SERVER).await()
        val records = snapshot.documents.map { doc ->
            MaterialRecord(
                doc.id,
                doc.getString("name") ?: "",
                doc.getDouble("quantity") ?: 0.0,
                doc.getString("unit") ?: ""
            )
        }
        val diff = RemoteChangeProcessor.materialsChanged(applicationContext, records, true)
        RemoteChangeProcessor.deliverShoppingDiff(applicationContext, diff, settings.notificationsEnabled)
    }

    private suspend fun checkNewNotes(settings: SettingsRepository) {
        val snapshot = FirebaseModule.notesDb.collection("important_notes").get(Source.SERVER).await()
        val records = snapshot.documents.map { doc ->
            NoteRecord(doc.id, doc.getString("title") ?: "", doc.getString("content") ?: "")
        }
        val fresh = RemoteChangeProcessor.newNotes(applicationContext, records, true)
        if (!settings.notificationsEnabled) return
        for (note in fresh) {
            NotificationHelper.showNewNoteNotification(applicationContext, note.title, note.content, note.id)
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "shop_manager_background_sync"

        /**
         * كل 15 دقيقة (الحد الأدنى الفعلي في WorkManager) — أو 30 دقيقة مع شرط
         * "البطارية ليست منخفضة" على الأجهزة الضعيفة (PerformanceTier.LOW) حتى لا
         * يُوقظ الفحص الراديو/المعالج عند أسوأ لحظة. KEEP-like سلوك التحديث: استدعاؤها
         * عند كل فتح للتطبيق لا يُنشئ مهام مكررة.
         */
        fun schedule(context: Context, tier: PerformanceTier = DevicePerformance.detectTier(context)) {
            val isLowTier = tier == PerformanceTier.LOW
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .apply { if (isLowTier) setRequiresBatteryNotLow(true) }
                .build()

            val intervalMinutes = if (isLowTier) 30L else 15L
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
