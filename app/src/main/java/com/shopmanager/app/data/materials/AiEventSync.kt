package com.shopmanager.app.data.materials

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * حدث تعلّم واحد (إضافة نقص، صار متوفراً، "موجودة"، تخطّي، أهمية، إعادة تسمية).
 *
 * الذكاء المحلي لا يخزّن إحصائيات جاهزة بعد الآن؛ بل يخزّن **أحداثاً** في فايربيس
 * يراها كل المستخدمين، وكل جهاز يعيد بناء نفس الإحصائيات منها بنفس الخوارزمية
 * فتتطابق الاقتراحات عند الجميع تلقائياً ولا يحدث أي تعارض بين الأجهزة.
 *
 * [id] حتمي (وقت + نوع + مفتاح المادة) فتكرار كتابة نفس الحدث من جهازين لا يضاعفه.
 */
data class AiEvent(
    val id: String,
    val key: String,
    val name: String,
    val type: String,
    val ts: Long,
    val qty: Int = 1,
    val unit: String = "",
    val important: Boolean = false,
    /** معرّف دفعة الإضافة (مواد أُضيفت معاً) لتعلّم "ما ينقص مع ما". */
    val batch: String = "",
    /** للإعادة تسمية: المفتاح الجديد. */
    val to: String = "",
    /** لأحداث مجمّعة (ترحيل البيانات القديمة): عدد المرات. */
    val count: Int = 1
) {
    companion object {
        const val ADDED = "a"
        const val RESOLVED = "r"
        const val EXISTS = "e"
        const val SKIPPED = "s"
        const val IMPORTANT = "i"
        const val RENAMED = "m"
    }
}

object AiEventSync {
    const val COLLECTION = "ai_events"

    /** آخر كم يوماً من الأحداث تدخل في الحساب. */
    const val WINDOW_DAYS = 240L

    /** الأحداث الأقدم من هذا تُحذف من فايربيس (مرة أسبوعياً من أي جهاز). */
    const val PURGE_AFTER_DAYS = 400L

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val BATCH_LIMIT = 400

    private val db: FirebaseFirestore get() = FirebaseModule.materialsDb

    fun idFor(type: String, key: String, ts: Long): String =
        "${ts}_${type}_${Integer.toHexString(key.hashCode())}_${key.length}"

    private fun toMap(e: AiEvent): Map<String, Any> = hashMapOf(
        "t" to e.type, "k" to e.key, "n" to e.name, "ts" to e.ts,
        "q" to e.qty, "u" to e.unit, "i" to e.important,
        "b" to e.batch, "to" to e.to, "c" to e.count
    )

    /** كتابة بلا انتظار: تُصَفّ محلياً وتُرفع عند توفر الاتصال. */
    fun write(e: AiEvent) {
        runCatching {
            db.collection(COLLECTION).document(e.id).set(toMap(e)).addOnFailureListener { }
        }
    }

    fun writeAll(events: List<AiEvent>) {
        runCatching {
            events.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.set(db.collection(COLLECTION).document(it.id), toMap(it)) }
                batch.commit().addOnFailureListener { }
            }
        }
    }

    /** يبث الأحداث الجديدة/المعدّلة فقط (أول لقطة تحمل كل المحفوظ). */
    fun listen(): Flow<List<AiEvent>> = callbackFlow {
        val cutoff = System.currentTimeMillis() - WINDOW_DAYS * DAY_MS
        val reg = db.collection(COLLECTION)
            .whereGreaterThan("ts", cutoff)
            .orderBy("ts")
            .addSnapshotListener(Dispatchers.Default.asExecutor()) { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val list = ArrayList<AiEvent>(snap.documentChanges.size)
                for (ch in snap.documentChanges) {
                    if (ch.type == DocumentChange.Type.REMOVED) continue
                    val d = ch.document
                    val key = d.getString("k") ?: continue
                    val type = d.getString("t") ?: continue
                    val ts = d.getLong("ts") ?: continue
                    list += AiEvent(
                        id = d.id,
                        key = key,
                        name = d.getString("n") ?: key,
                        type = type,
                        ts = ts,
                        qty = (d.getLong("q") ?: 1L).toInt(),
                        unit = d.getString("u") ?: "",
                        important = d.getBoolean("i") ?: false,
                        batch = d.getString("b") ?: "",
                        to = d.getString("to") ?: "",
                        count = (d.getLong("c") ?: 1L).toInt().coerceAtLeast(1)
                    )
                }
                if (list.isNotEmpty()) trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** حذف الأحداث الأقدم من [PURGE_AFTER_DAYS] (دفعة واحدة كحد أقصى لكل استدعاء). */
    suspend fun purgeOld() {
        val cutoff = System.currentTimeMillis() - PURGE_AFTER_DAYS * DAY_MS
        val snap = db.collection(COLLECTION).whereLessThan("ts", cutoff).limit(200).get().await()
        if (snap.isEmpty) return
        val batch = db.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
