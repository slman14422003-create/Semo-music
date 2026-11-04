package com.shopmanager.app.data.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * "التفريق بين هاتف وهاتف آخر": سجل دائم (على القرص) بما كتبه **هذا الجهاز
 * تحديداً** إلى Firestore.
 *
 * المشكلة السابقة: كل مستمع (ViewModel) كان يحتفظ بمجموعة ids "أنا أنشأتها"
 * في الذاكرة فقط، فكانت تضيع عند إغلاق التطبيق، ولا يراها الـ Worker ولا خدمة
 * الخلفية، وللمواد كانت تُستهلك فقط عند حدوث تغيير — فإذا حفظتَ تعديلاً
 * دون تغيير فعلي بقي الـ id "مُعلَّماً" ويبتلع لاحقاً تعديلاً حقيقياً جاء من
 * هاتف آخر (فلا يصلك إشعار به).
 *
 * الحل هنا بلا أي حقل إضافي في المستندات (لا خطر على قواعد Firestore):
 * - الديون والملاحظات: id فريد لا يتكرر، فيكفي تسجيل أنه أُنشئ من هنا.
 * - المواد: نسجّل "بصمة" الحالة التي كتبها هذا الجهاز (اسم|كمية|وحدة، أو
 *   محذوف). التغيير يُعتبر "مني" فقط إذا كانت الحالة الحالية تطابق بصمتي؛
 *   أي تعديل لاحق من هاتف آخر يعطي بصمة مختلفة فيُعامل كتغيير خارجي.
 *
 * الكتابة تتم بـ apply() لكن قيمتها تظهر فوراً لأي قارئ في نفس العملية
 * (SharedPreferences تحدّث ذاكرتها بشكل متزامن)، وهذا كافٍ لأن التسجيل يتم
 * قبل إرسال الكتابة نفسها.
 */
object SelfChangeLedger {

    const val KIND_DEBT = "debt"
    const val KIND_NOTE = "note"

    private const val PREFS = "shop_manager_self_changes"
    private const val CREATED_TTL_MS = 24L * 60L * 60L * 1000L
    private const val MATERIAL_TTL_MS = 5L * 60L * 1000L
    private const val PRUNE_THRESHOLD = 400
    private const val SEP = "\u0001"
    private const val DELETED = "\u0002deleted"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call right when a new debt/note id is generated (before the write is sent). */
    fun markCreated(context: Context, kind: String, id: String) {
        if (id.isBlank()) return
        prefs(context).edit().putLong("c:$kind:$id", System.currentTimeMillis()).apply()
        pruneIfNeeded(context)
    }

    fun isCreatedHere(context: Context, kind: String, id: String): Boolean {
        val at = prefs(context).getLong("c:$kind:$id", 0L)
        return at > 0L && System.currentTimeMillis() - at <= CREATED_TTL_MS
    }

    /** [signature] = the exact state this device is writing, or null when it is deleting the item. */
    fun markMaterialWritten(context: Context, id: String, signature: String?) {
        if (id.isBlank()) return
        val value = "${System.currentTimeMillis()}$SEP${signature ?: DELETED}"
        prefs(context).edit().putString("m:$id", value).apply()
        pruneIfNeeded(context)
    }

    fun markMaterialsDeleted(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val editor = prefs(context).edit()
        for (id in ids) {
            if (id.isNotBlank()) editor.putString("m:$id", "$now$SEP$DELETED")
        }
        editor.apply()
        pruneIfNeeded(context)
    }

    /**
     * True only when the material's CURRENT state (null = no longer exists)
     * is exactly what this device wrote a moment ago.
     */
    fun isMaterialChangeHere(context: Context, id: String, currentSignature: String?): Boolean {
        val raw = prefs(context).getString("m:$id", null) ?: return false
        val idx = raw.indexOf(SEP)
        if (idx <= 0) return false
        val at = raw.substring(0, idx).toLongOrNull() ?: return false
        if (System.currentTimeMillis() - at > MATERIAL_TTL_MS) return false
        return raw.substring(idx + 1) == (currentSignature ?: DELETED)
    }

    private fun pruneIfNeeded(context: Context) {
        val p = prefs(context)
        val all = p.all
        if (all.size < PRUNE_THRESHOLD) return
        val now = System.currentTimeMillis()
        val editor = p.edit()
        for (entry in all.entries) {
            val value = entry.value
            val expired = when (value) {
                is Long -> now - value > CREATED_TTL_MS
                is String -> {
                    val at = value.substringBefore(SEP).toLongOrNull() ?: 0L
                    now - at > MATERIAL_TTL_MS
                }
                else -> true
            }
            if (expired) editor.remove(entry.key)
        }
        editor.apply()
    }
}
