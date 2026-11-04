package com.shopmanager.app.data.notifications

import android.content.Context
import android.content.SharedPreferences

data class DebtRecord(val id: String, val personId: String, val amount: Double)
data class MaterialRecord(val id: String, val name: String, val quantity: Double, val unit: String)
data class NoteRecord(val id: String, val title: String, val content: String)

data class MaterialChange(val record: MaterialRecord, val isNew: Boolean)

data class ShoppingDiff(val changed: List<MaterialChange>, val removedIds: List<String>)

/**
 * نقطة واحدة فقط تقرّر "هل يوجد شيء جديد جاء من جهاز آخر؟" — يستخدمها كل من
 * [BackgroundSyncWorker] و[RemoteChangeWatcher] (الخدمة الحيّة + المستمع داخل
 * التطبيق). قبل هذا كان لكل واحد منطق مقارنة وخط أساس (baseline) خاص به،
 * والـ ViewModel كان يكتب الـ baseline على كل تحديث فيُفسده على الـ Worker.
 *
 * أهم إصلاحين:
 *
 * 1) تعارض خط الأساس للمواد: الـ ViewModel كان يراقب **قسماً واحداً فقط**
 *    (whereEqualTo("section", ...)) ويكتب بصمته كخط أساس، بينما الـ Worker
 *    يقارن **كل الأقسام** — فيرى "فرقاً" وهمياً كل 15–30 دقيقة ويرسل إشعار
 *    "قائمة مشتريات" لا علاقة له بأي جهاز. الآن كل المراقبين يستخدمون نفس
 *    المجموعة الكاملة ونفس التنسيق.
 * 2) لا تكرار: الدوال هنا `@Synchronized` وتحدّث خط الأساس قبل أن يرسل
 *    المستدعي الإشعار، فلو رأى الـ Worker والخدمة نفس الدين الجديد في نفس
 *    اللحظة فأولهما فقط يُشعِر.
 *
 * [trustworthy] = false يعني أن اللقطة قادمة من الكاش المحلي فقط (لا كتابة
 * معلّقة)، فقد تكون أقدم من خط الأساس — تُستخدم فقط لتهيئة خط الأساس أول مرة
 * ولا تُقارن أبداً حتى لا تُنتج إشعارات كاذبة.
 */
object RemoteChangeProcessor {

    private const val PREFS = "shop_manager_sync"
    private const val KEY_KNOWN_DEBTS = "known_debt_ids"
    private const val KEY_KNOWN_NOTES = "known_note_ids"

    // مفتاح جديد بصيغة مختلفة (مجموعة "id|بصمة") — القديم كان نصاً واحداً
    // مفصولاً بـ ; و= فيتكسّر إذا احتوى اسم المادة على أحدهما.
    private const val KEY_KNOWN_MATERIALS = "known_material_sigs_v2"
    private const val SEP = "\u0001"

    fun materialSignature(name: String, quantity: Double, unit: String): String =
        "$name|$quantity|$unit"

    /**
     * Same Arabic-locale grouping the in-app lists use, but a fresh NumberFormat
     * per call: the shared `Formatters` instance is main-thread-only (NumberFormat
     * isn't thread-safe) and these notifications are built on background threads.
     */
    fun formatAmount(amount: Double): String =
        java.text.NumberFormat.getNumberInstance(java.util.Locale("ar")).format(amount)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Debts that are new since the stored baseline and were NOT created on this device. */
    @Synchronized
    fun newDebts(context: Context, debts: List<DebtRecord>, trustworthy: Boolean): List<DebtRecord> {
        val p = prefs(context)
        val previous = p.getStringSet(KEY_KNOWN_DEBTS, null)
        // خط الأساس لا يُكتب إلا من لقطة موثوقة (من الخادم أو فيها كتابة محلية).
        // لقطة كاش فارغة عند أول تشغيل بلا إنترنت كانت تزرع خط أساس "فارغاً"،
        // فإذا عادت الشبكة بدا كل دين موجود "جديداً" وانهالت الإشعارات.
        if (trustworthy) {
            val ids = HashSet<String>(debts.size * 2)
            for (d in debts) ids.add(d.id)
            p.edit().putStringSet(KEY_KNOWN_DEBTS, ids).apply()
        }
        if (previous == null || !trustworthy) return emptyList()
        return debts.filter { d ->
            d.id !in previous && d.amount > 0 &&
                !SelfChangeLedger.isCreatedHere(context, SelfChangeLedger.KIND_DEBT, d.id)
        }
    }

    /** Notes that are new since the stored baseline and were NOT created on this device. */
    @Synchronized
    fun newNotes(context: Context, notes: List<NoteRecord>, trustworthy: Boolean): List<NoteRecord> {
        val p = prefs(context)
        val previous = p.getStringSet(KEY_KNOWN_NOTES, null)
        if (trustworthy) {
            val ids = HashSet<String>(notes.size * 2)
            for (n in notes) ids.add(n.id)
            p.edit().putStringSet(KEY_KNOWN_NOTES, ids).apply()
        }
        if (previous == null || !trustworthy) return emptyList()
        return notes.filter { n ->
            n.id !in previous && !SelfChangeLedger.isCreatedHere(context, SelfChangeLedger.KIND_NOTE, n.id)
        }
    }

    /** Null = nothing changed (or first seed / untrusted snapshot). */
    @Synchronized
    fun materialsChanged(context: Context, materials: List<MaterialRecord>, trustworthy: Boolean): ShoppingDiff? {
        val p = prefs(context)
        val previous = readMaterialBaseline(p)
        val current = LinkedHashMap<String, String>()
        for (m in materials) current[m.id] = materialSignature(m.name, m.quantity, m.unit)

        if (trustworthy && previous != current) writeMaterialBaseline(p, current)
        if (previous == null || !trustworthy || previous == current) return null

        val changedIds = HashSet<String>()
        for (id in current.keys) if (current[id] != previous[id]) changedIds.add(id)
        for (id in previous.keys) if (!current.containsKey(id)) changedIds.add(id)

        val byId = HashMap<String, MaterialRecord>(materials.size * 2)
        for (m in materials) byId[m.id] = m

        val changed = ArrayList<MaterialChange>()
        val removed = ArrayList<String>()
        for (id in changedIds) {
            val record = byId[id]
            if (record == null) {
                removed.add(id)
                continue
            }
            if (!SelfChangeLedger.isMaterialChangeHere(context, id, current[id])) {
                changed.add(MaterialChange(record, !previous.containsKey(id)))
            }
        }
        if (changed.isEmpty() && removed.isEmpty()) return null
        return ShoppingDiff(changed, removed)
    }

    private const val MAX_INDIVIDUAL_NOTIFICATIONS = 15

    fun deliverShoppingDiff(context: Context, diff: ShoppingDiff?, notificationsEnabled: Boolean) {
        if (diff == null) return
        for (id in diff.removedIds) NotificationHelper.cancelMaterialNotification(context, id)
        if (!notificationsEnabled || diff.changed.isEmpty()) return
        if (diff.changed.size > MAX_INDIVIDUAL_NOTIFICATIONS) {
            NotificationHelper.showShoppingListNotification(context, diff.changed.map { it.record.name }.distinct())
            return
        }
        for (change in diff.changed) {
            NotificationHelper.showMaterialNotification(context, change.record, change.isNew)
        }
    }

    private fun readMaterialBaseline(p: SharedPreferences): Map<String, String>? {
        val set = p.getStringSet(KEY_KNOWN_MATERIALS, null) ?: return null
        val map = HashMap<String, String>(set.size * 2)
        for (entry in set) {
            val idx = entry.indexOf(SEP)
            if (idx > 0) map[entry.substring(0, idx)] = entry.substring(idx + 1)
        }
        return map
    }

    private fun writeMaterialBaseline(p: SharedPreferences, map: Map<String, String>) {
        val set = HashSet<String>(map.size * 2)
        for ((id, signature) in map) set.add("$id$SEP$signature")
        p.edit().putStringSet(KEY_KNOWN_MATERIALS, set).apply()
    }
}
