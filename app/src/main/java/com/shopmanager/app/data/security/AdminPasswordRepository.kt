package com.shopmanager.app.data.security

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

/**
 * كلمة مرور لوحة المطوّر صارت محفوظة في Firebase فقط (لا شيء مكتوب داخل الكود):
 *
 *   Firestore  →  collection: config  →  document: admin
 *       password        (string)   ← كلمة المرور نفسها، تُغيَّر وتُقرأ من لوحة Firebase
 *       passwordSha256  (string)   ← اختياري بديل: بصمة SHA-256 (hex) إن لم ترد تخزينها نصاً
 *
 * إذا وُجد الحقلان يُقبل أيٌّ منهما. التطبيق لا يكتب في هذا المستند أبداً — التغيير
 * الوحيد يتم من Firebase Console (ويُنصح بقاعدة `allow write: if false` على config).
 *
 * بلا اتصال: يُستخدم آخر نسخة محفوظة في كاش Firestore (إن وُجدت) كي لا ينقفل الباب
 * على المطوّر عند انقطاع الشبكة بعد أول دخول ناجح.
 */
object AdminPasswordRepository {

    private const val COLLECTION = "config"
    private const val DOCUMENT = "admin"
    private const val FIELD_PASSWORD = "password"
    private const val FIELD_HASH = "passwordSha256"
    private const val TIMEOUT_MS = 8_000L

    sealed class Outcome {
        /** كلمة المرور صحيحة. */
        data object Granted : Outcome()

        /** كلمة المرور خاطئة. */
        data object Denied : Outcome()

        /** المستند config/admin غير موجود أو الحقل فارغ. */
        data object NotConfigured : Outcome()

        /** تعذّرت القراءة (لا إنترنت ولا كاش، أو قواعد Firestore ترفض القراءة). */
        data class Unavailable(val reason: String) : Outcome()
    }

    /** نتيجة اختبار اتصال لوحة المطوّر بـ Firebase. */
    data class Probe(
        val reachable: Boolean,
        val latencyMs: Long,
        val passwordConfigured: Boolean,
        val detail: String
    )

    private sealed class Fetched {
        data class Found(val snapshot: DocumentSnapshot, val fromServer: Boolean) : Fetched()
        data class Failed(val reason: String) : Fetched()
    }

    private fun describe(e: Exception): String {
        val code = (e as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "قواعد Firestore ترفض قراءة config/admin — اسمح بالقراءة لهذا المستند فقط"
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "تعذر الوصول لـ Firebase — تحقق من الإنترنت"
            else -> e.message ?: "خطأ غير متوقع"
        }
    }

    /** السيرفر أولاً (قيمة حديثة)، ثم الكاش إن فشل السيرفر. */
    private suspend fun fetch(): Fetched {
        val ref = FirebaseModule.debtsDb.collection(COLLECTION).document(DOCUMENT)
        val serverError: Exception = try {
            val snap = withTimeout(TIMEOUT_MS) { ref.get(Source.SERVER).await() }
            return Fetched.Found(snap, fromServer = true)
        } catch (e: Exception) {
            e
        }
        // قواعد ترفض القراءة: الكاش لن ينفع، والرسالة الأوضح للمطوّر هي الرفض نفسه.
        if ((serverError as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            return Fetched.Failed(describe(serverError))
        }
        return try {
            val snap = ref.get(Source.CACHE).await()
            Fetched.Found(snap, fromServer = false)
        } catch (_: Exception) {
            Fetched.Failed(describe(serverError))
        }
    }

    private fun storedPassword(snapshot: DocumentSnapshot): String? {
        val raw = snapshot.get(FIELD_PASSWORD)
        val text = when (raw) {
            is String -> raw
            // لو كتبها المطوّر كرقم في Firebase (مثل 1442) نحوّله لنص بلا ".0".
            is Number -> {
                val d = raw.toDouble()
                if (d == d.toLong().toDouble()) d.toLong().toString() else raw.toString()
            }
            else -> null
        }
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun storedHash(snapshot: DocumentSnapshot): String? =
        snapshot.getString(FIELD_HASH)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    suspend fun verify(entered: String): Outcome = withContext(Dispatchers.IO) {
        when (val fetched = fetch()) {
            is Fetched.Failed -> Outcome.Unavailable(fetched.reason)
            is Fetched.Found -> {
                val snapshot = fetched.snapshot
                val password = if (snapshot.exists()) storedPassword(snapshot) else null
                val hash = if (snapshot.exists()) storedHash(snapshot) else null
                when {
                    password == null && hash == null ->
                        if (!fetched.fromServer && !snapshot.exists()) {
                            Outcome.Unavailable("لا اتصال بالإنترنت ولا توجد نسخة محفوظة من كلمة المرور")
                        } else {
                            Outcome.NotConfigured
                        }
                    (password != null && constantTimeEquals(entered, password)) ||
                        (hash != null && constantTimeEquals(sha256Hex(entered), hash)) -> Outcome.Granted
                    else -> Outcome.Denied
                }
            }
        }
    }

    /** يقيس زمن الاستجابة ويتأكد أن المستند معيّن — يُستعمل من أدوات لوحة المطوّر. */
    suspend fun probe(): Probe = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val ref = FirebaseModule.debtsDb.collection(COLLECTION).document(DOCUMENT)
        try {
            val snap = withTimeout(TIMEOUT_MS) { ref.get(Source.SERVER).await() }
            val elapsed = System.currentTimeMillis() - start
            val configured = snap.exists() && (storedPassword(snap) != null || storedHash(snap) != null)
            Probe(
                reachable = true,
                latencyMs = elapsed,
                passwordConfigured = configured,
                detail = if (configured) "كلمة المرور معيّنة في config/admin"
                else "المستند config/admin غير موجود أو الحقل password فارغ"
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            Probe(reachable = false, latencyMs = elapsed, passwordConfigured = false, detail = describe(e))
        }
    }
}
