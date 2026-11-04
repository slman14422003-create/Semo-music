package com.shopmanager.app.data.debts

import android.content.Context
import com.google.firebase.firestore.Query
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * سجل عمليات الديون — يتزامن عبر Firestore (مجموعة "debt_activity") بين كل الأجهزة.
 * كل جهاز يكتب عملياته هو فقط، والجميع يقرأ نفس القائمة لحظياً؛ والكتابة تعمل
 * بدون إنترنت أيضاً (تُرسَل عند عودة الاتصال). [Entry.remote] = العملية من جهاز آخر.
 */
object DebtActivityLog {

    enum class Kind { ADDED, NEW_CUSTOMER, PAID, PAID_ALL, EDITED, DELETED, DELETED_CUSTOMER, UNDO }

    data class Entry(
        val id: String,
        val time: Long,
        val kind: Kind,
        val person: String,
        val amount: Double,
        val remote: Boolean
    )

    const val COLLECTION = "debt_activity"
    private const val PREFS = "shop_manager_debt_activity"
    private const val KEY_DEVICE = "device_id"
    private const val MAX_SHOWN = 300L

    private fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }
    }

    fun log(context: Context, kind: Kind, person: String, amount: Double = 0.0) {
        runCatching {
            FirebaseModule.init(context)
            FirebaseModule.debtsDb.collection(COLLECTION).add(
                mapOf(
                    "kind" to kind.name,
                    "person" to person,
                    "amount" to amount,
                    "device" to deviceId(context),
                    "createdAt" to System.currentTimeMillis()
                )
            )
        }
    }

    /** الأحدث أولاً، لحظي. يُغلق بخطأ إن رُفضت القراءة (مثلاً القواعد غير منشورة). */
    fun listen(context: Context): Flow<List<Entry>> = callbackFlow {
        FirebaseModule.init(context)
        val me = deviceId(context)
        val registration = FirebaseModule.debtsDb.collection(COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_SHOWN)
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val kind = runCatching { Kind.valueOf(doc.getString("kind") ?: "") }.getOrNull()
                        ?: return@mapNotNull null
                    Entry(
                        id = doc.id,
                        time = doc.getLong("createdAt") ?: 0L,
                        kind = kind,
                        person = doc.getString("person") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        remote = (doc.getString("device") ?: me) != me
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** يمسح السجل لكل الأجهزة. */
    suspend fun clear(context: Context) {
        FirebaseModule.init(context)
        val db = FirebaseModule.debtsDb
        while (true) {
            val docs = db.collection(COLLECTION).limit(400).get().await().documents
            if (docs.isEmpty()) break
            val batch = db.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }
}
