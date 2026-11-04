package com.shopmanager.app.data.notifications

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.settings.SettingsRepository
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * مجموعة مستمعات Firestore **واحدة** لكل التطبيق تكتشف ما أضافته/عدّلته
 * الأجهزة الأخرى وتُطلق الإشعارات (ديون جديدة، تغيّر قائمة المواد، ملاحظات
 * جديدة).
 *
 * قبل: كل ViewModel كان يبني تشخيصه الخاص للتغييرات داخل تدفّق واجهته
 * (وبمجرد إغلاق التطبيق أو تجميد العملية يتوقف كل شيء، والـ Worker لا يعمل إلا
 * كل 15 دقيقة على الأقل — وأحياناً ساعات بسبب Doze/بطارية الشركات المصنّعة).
 * الآن هذا الكائن هو المصدر الوحيد؛ يعمل:
 *  - داخل [RealtimeSyncService] (خدمة أمامية → حي حتى والتطبيق مغلق)، و/أو
 *  - داخل التطبيق نفسه ما دام مفتوحاً (مالك "app").
 * ويبقى حياً ما دام له مالك واحد على الأقل (عدّاد مرجعي بسيط).
 *
 * كل المعالجة على خيط واحد مخصّص (لا نُثقل الخيط الرئيسي ولا نحتاج أقفالاً)،
 * وأي استثناء داخل المعالج يُبتلع — استثناء غير ملتقط على خيط Firestore كان
 * سيُسقط التطبيق بالكامل.
 */
object RemoteChangeWatcher {

    private const val UNKNOWN_PERSON = "عميل"

    // خيط واحد يعيش مع العملية (لا نغلقه أبداً): إغلاقه أثناء وصول لقطة متأخرة
    // يرمي RejectedExecutionException داخل Firestore نفسه.
    private val executor: Executor by lazy { Executors.newSingleThreadExecutor() }

    private val owners = HashSet<String>()
    private var registrations: List<ListenerRegistration> = emptyList()

    @Volatile private var settings: SettingsRepository? = null
    @Volatile private var personNames: Map<String, String> = emptyMap()
    @Volatile private var personsReady = false
    @Volatile private var pendingDebts: List<DebtRecord>? = null
    @Volatile private var pendingTrusted = false

    // BUG FIXED (الإشعارات الفورية تتوقف وحدها): when a Firestore snapshot
    // listener reports an error (rules/auth hiccup, backend restart) it is
    // TERMINATED - it never delivers another event. The handlers above just
    // returned on error, so from then on no notification could arrive until
    // the process happened to restart, while the foreground service kept
    // showing "المزامنة الفورية مفعّلة". The whole set is now re-registered
    // after a short delay (baselines live on disk, so anything that changed
    // meanwhile is still detected and notified once).
    private const val RESTART_DELAY_MS = 30_000L
    private val retryHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var retryPending = false
    private var appContext: Context? = null

    @Synchronized
    private fun scheduleRestart() {
        if (retryPending || owners.isEmpty()) return
        retryPending = true
        retryHandler.postDelayed({ restart() }, RESTART_DELAY_MS)
    }

    @Synchronized
    private fun restart() {
        retryPending = false
        val ctx = appContext ?: return
        val owner = owners.firstOrNull() ?: return
        for (registration in registrations) {
            try {
                registration.remove()
            } catch (_: Exception) {
            }
        }
        registrations = emptyList()
        acquire(ctx, owner)
    }

    @Synchronized
    fun acquire(context: Context, owner: String) {
        owners.add(owner)
        if (registrations.isNotEmpty()) return

        val ctx = context.applicationContext
        appContext = ctx
        FirebaseModule.init(ctx)
        NotificationHelper.ensureChannels(ctx)
        settings = SettingsRepository(ctx)
        personsReady = false
        pendingDebts = null

        val db = FirebaseModule.debtsDb
        val list = ArrayList<ListenerRegistration>(4)

        list.add(
            // نفس استعلام DebtsRepository.listenPersons بالضبط (مع الترتيب): Firestore
            // يدمج الاستعلامات المتطابقة في اشتراك واحد مع الخادم، فلا نقرأ مجموعة
            // العملاء مرتين والتطبيق مفتوح.
            db.collection("persons").orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(executor) { snap, err ->
                if (err != null) {
                    scheduleRestart()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                safely { handlePersons(ctx, snap) }
            }
        )
        list.add(
            db.collection("debts").addSnapshotListener(executor) { snap, err ->
                if (err != null) {
                    scheduleRestart()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                safely { handleDebts(ctx, snap) }
            }
        )
        list.add(
            FirebaseModule.materialsDb.collection("spices_final_v12").addSnapshotListener(executor) { snap, err ->
                if (err != null) {
                    scheduleRestart()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                safely { handleMaterials(ctx, snap) }
            }
        )
        list.add(
            FirebaseModule.notesDb.collection("important_notes").addSnapshotListener(executor) { snap, err ->
                if (err != null) {
                    scheduleRestart()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                safely { handleNotes(ctx, snap) }
            }
        )
        registrations = list
    }

    @Synchronized
    fun release(owner: String) {
        owners.remove(owner)
        if (owners.isNotEmpty()) return
        retryHandler.removeCallbacksAndMessages(null)
        retryPending = false
        for (registration in registrations) {
            try {
                registration.remove()
            } catch (_: Exception) {
            }
        }
        registrations = emptyList()
        pendingDebts = null
        personsReady = false
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }

    /** Cache-only snapshots (no local pending write) may be older than the stored baseline — never diff those. */
    private fun trusted(snap: QuerySnapshot): Boolean =
        !snap.metadata.isFromCache || snap.metadata.hasPendingWrites()

    private fun notificationsOn(): Boolean = settings?.notificationsEnabled ?: false

    private fun handlePersons(ctx: Context, snap: QuerySnapshot) {
        val names = HashMap<String, String>(snap.size() * 2)
        for (doc in snap.documents) names[doc.id] = doc.getString("name") ?: UNKNOWN_PERSON
        personNames = names
        personsReady = true

        // الديون وصلت قبل العملاء (ترتيب اللقطات الأولى غير مضمون) — كنا
        // ننتظر الأسماء حتى لا يظهر "عميل" بدل الاسم الحقيقي في الإشعار.
        val waiting = pendingDebts
        if (waiting != null) {
            pendingDebts = null
            processDebts(ctx, waiting, pendingTrusted)
        }
    }

    private fun handleDebts(ctx: Context, snap: QuerySnapshot) {
        val records = ArrayList<DebtRecord>(snap.size())
        for (doc in snap.documents) {
            records.add(DebtRecord(doc.id, doc.getString("personId") ?: "", doc.getDouble("amount") ?: 0.0))
        }
        val ok = trusted(snap)
        if (!personsReady) {
            pendingDebts = records
            pendingTrusted = ok
            return
        }
        processDebts(ctx, records, ok)
    }

    private fun processDebts(ctx: Context, records: List<DebtRecord>, trusted: Boolean) {
        val fresh = RemoteChangeProcessor.newDebts(ctx, records, trusted)
        if (fresh.isEmpty() || !notificationsOn()) return
        val currency = settings?.currencySymbol ?: "ل.س"
        for (debt in fresh) {
            NotificationHelper.showNewDebtNotification(
                ctx,
                resolvePersonName(debt.personId),
                RemoteChangeProcessor.formatAmount(debt.amount),
                currency,
                debt.id
            )
        }
    }

    /** Debt and its brand-new customer are written together, but the two listeners fire separately. */
    private fun resolvePersonName(personId: String): String {
        val known = personNames[personId]
        if (known != null) return known
        if (personId.isBlank()) return UNKNOWN_PERSON
        return try {
            val doc = Tasks.await(
                FirebaseModule.debtsDb.collection("persons").document(personId).get(),
                5,
                TimeUnit.SECONDS
            )
            doc.getString("name") ?: UNKNOWN_PERSON
        } catch (_: Exception) {
            UNKNOWN_PERSON
        }
    }

    private fun handleMaterials(ctx: Context, snap: QuerySnapshot) {
        val records = ArrayList<MaterialRecord>(snap.size())
        for (doc in snap.documents) {
            records.add(
                MaterialRecord(
                    doc.id,
                    doc.getString("name") ?: "",
                    doc.getDouble("quantity") ?: 0.0,
                    doc.getString("unit") ?: ""
                )
            )
        }
        val diff = RemoteChangeProcessor.materialsChanged(ctx, records, trusted(snap))
        RemoteChangeProcessor.deliverShoppingDiff(ctx, diff, notificationsOn())
    }

    private fun handleNotes(ctx: Context, snap: QuerySnapshot) {
        val records = ArrayList<NoteRecord>(snap.size())
        for (doc in snap.documents) {
            records.add(NoteRecord(doc.id, doc.getString("title") ?: "", doc.getString("content") ?: ""))
        }
        val fresh = RemoteChangeProcessor.newNotes(ctx, records, trusted(snap))
        if (fresh.isEmpty() || !notificationsOn()) return
        for (note in fresh) {
            NotificationHelper.showNewNoteNotification(ctx, note.title, note.content, note.id)
        }
    }
}
