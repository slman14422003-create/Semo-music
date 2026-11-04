package com.shopmanager.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.notes.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * "إشعارات أكثر تطورًا وكفاءة": handles the action buttons attached to
 * [NotificationHelper]'s notifications directly — snoozing a note
 * reminder, settling a debt, or dismissing the shopping-list card happens
 * right here, without ever launching [com.shopmanager.app.MainActivity]
 * first, the same "quick reply / mark as done" pattern messaging and task
 * apps use for their own notification action buttons.
 *
 * Registered with `android:exported="false"` in the manifest — every
 * `PendingIntent` that targets this receiver is built inside this app
 * itself (see [NotificationHelper.buildActionIntent]), so nothing
 * outside the app can ever trigger these actions directly.
 *
 * WorkManager's on-demand init (see ShopManagerApplication) means its
 * first-ever `getInstance()` call can touch disk to build its Room
 * database — `goAsync()` + a background-dispatcher coroutine keeps that
 * off this receiver's `onReceive` (which the platform expects back
 * quickly) instead of risking it blocking the main thread.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // STABILITY: goAsync() only grants ~10s before the system
                // flags the receiver as ANR, but the Firestore writes below
                // can each wait up to 15s on a bad connection. Cap the whole
                // action well inside that budget; on timeout the notification
                // stays visible so the person can retry.
                kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    when (intent.action) {
                        ACTION_SNOOZE_NOTE -> handleSnoozeNote(context, intent)
                        ACTION_DISMISS_SHOPPING -> {
                            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                            if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)
                            else NotificationHelper.cancelShoppingListNotification(context)
                        }
                        ACTION_MARK_DEBT_PAID -> handleMarkDebtPaid(context, intent)
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                // never let an uncaught exception kill the process from a receiver
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSnoozeNote(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "تذكير"
        val content = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)

        val snoozeUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        NoteReminderWorker.schedule(context, noteId, title, content, snoozeUntil)

        // BUG FIXED (راجع NotesRepository.updateReminderAt للتفصيل الكامل):
        // بدون هاد السطر، حقل reminderAt بمستند الملاحظة يضل عالوقت الأصلي
        // (الفائت) رغم إنو WorkManager فعليًا انجدول عالوقت الجديد فوق.
        try {
            FirebaseModule.init(context)
            NotesRepository().updateReminderAt(noteId, snoozeUntil)
        } catch (_: Exception) {
            // أفضل جهد فقط: التأجيل المحلي (WorkManager) صار فعلاً بغض
            // النظر عن نجاح هاد التحديث - فشل الاتصال هون (مثلاً بلا
            // إنترنت وقتها) ما لازم يلغي التأجيل نفسه.
        }
    }

    /**
     * FEATURE ADDED ("تحسينات بالإشعارات"): إشعار "عميل جديد بالديون" ما
     * كان إله أي زر إجراء سريع - عكس قائمة النواقص ("تم الشراء") وتذكيرات
     * الملاحظات ("تأجيل ساعة")، اللي الاثنين فيهم تقدر تنجز الإجراء من
     * شريط الإشعار مباشرة بلمسة وحدة بدون فتح التطبيق. هالإجراء يسدّ نفس
     * الفجوة للديون: زر "تسديد" يسجل سداد هالدين بالضبط ويلغي الإشعار،
     * بنفس منطق DebtsViewModel.markDebtAsPaid.
     */
    private suspend fun handleMarkDebtPaid(context: Context, intent: Intent) {
        val debtId = intent.getStringExtra(EXTRA_DEBT_ID) ?: return
        val personName = intent.getStringExtra(EXTRA_PERSON_NAME) ?: ""
        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""
        val currency = intent.getStringExtra(EXTRA_CURRENCY) ?: "ل.س"
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        try {
            FirebaseModule.init(context)
            DebtsRepository().markDebtAsPaid(debtId)
            com.shopmanager.app.data.debts.DebtActivityLog.log(
                context, com.shopmanager.app.data.debts.DebtActivityLog.Kind.PAID, personName,
                amount.mapNotNull { c -> Character.digit(c, 10).takeIf { it >= 0 } }.joinToString("").toDoubleOrNull() ?: 0.0
            )
            // ترابط بالنسخ الاحتياطي: السداد من الإشعار (والتطبيق مغلق) لا يمرّ
            // بأي ViewModel، فلم يكن يطلب نسخة احتياطية محلية كبقية التغييرات.
            runCatching { InstantBackupWorker.requestNow(context) }
            if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)
            NotificationHelper.showDebtPaidNotification(context, personName, amount, currency, debtId)
        } catch (_: Exception) {
            // فشل السداد من الإشعار (غالبًا لا إنترنت وقتها) - نترك الإشعار
            // الأصلي ظاهر بدل ما ينلغي بدون ما يصير السداد فعليًا، عشان
            // الشخص يقدر يعيد المحاولة أو يفتح التطبيق.
        }
    }

    companion object {
        const val ACTION_SNOOZE_NOTE = "com.shopmanager.app.action.SNOOZE_NOTE"
        const val ACTION_DISMISS_SHOPPING = "com.shopmanager.app.action.DISMISS_SHOPPING"
        const val ACTION_MARK_DEBT_PAID = "com.shopmanager.app.action.MARK_DEBT_PAID"

        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_NOTE_CONTENT = "extra_note_content"
        const val EXTRA_NOTIF_ID = "extra_notif_id"

        const val EXTRA_DEBT_ID = "extra_debt_id"
        const val EXTRA_PERSON_NAME = "extra_person_name"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_CURRENCY = "extra_currency"
    }
}
