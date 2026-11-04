package com.shopmanager.app.data.updates

import android.content.Context
import com.google.firebase.firestore.SetOptions
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * مفتاح عام (لكل المستخدمين) لمربع "تحديث جديد متاح" الاختياري، مخزّن في
 * Firestore: المستند `app_config/update` والحقل `optionalUpdateEnabled`.
 *
 * - الحقل غير موجود أو تعذّرت القراءة (لا إنترنت/قواعد الأمان) ← يُعتبر مفعّلاً،
 *   أي نفس السلوك القديم؛ لا يمكن أن يمنع هذا المفتاح التحديث بالخطأ.
 * - عند إيقافه من لوحة المسؤول لا يظهر المربع عند فتح التطبيق لدى أي مستخدم.
 * - زر "تحقق من التحديثات" اليدوي في الإعدادات والتحديث الإجباري ([force])
 *   لا يتأثران به إطلاقاً.
 */
object RemoteUpdateConfig {

    private const val COLLECTION = "app_config"
    private const val DOC = "update"
    private const val FIELD_OPTIONAL = "optionalUpdateEnabled"
    private const val READ_TIMEOUT_MS = 4_000L
    private const val WRITE_TIMEOUT_MS = 10_000L

    private fun ref(context: Context) = FirebaseModule.run {
        init(context.applicationContext)
        debtsDb.collection(COLLECTION).document(DOC)
    }

    /** للفحص عند الفتح: يفشل مفتوحاً (true) عند أي خطأ. */
    suspend fun isOptionalUpdateEnabled(context: Context): Boolean = try {
        withTimeout(READ_TIMEOUT_MS) {
            ref(context).get().await().getBoolean(FIELD_OPTIONAL) ?: true
        }
    } catch (e: Exception) {
        true
    }

    /** للوحة المسؤول: يُرجع الخطأ بدل ابتلاعه كي يظهر السبب. */
    suspend fun read(context: Context): Result<Boolean> = try {
        Result.success(
            withTimeout(WRITE_TIMEOUT_MS) {
                ref(context).get().await().getBoolean(FIELD_OPTIONAL) ?: true
            }
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun write(context: Context, enabled: Boolean): Result<Unit> = try {
        withTimeout(WRITE_TIMEOUT_MS) {
            ref(context).set(mapOf(FIELD_OPTIONAL to enabled), SetOptions.merge()).await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
