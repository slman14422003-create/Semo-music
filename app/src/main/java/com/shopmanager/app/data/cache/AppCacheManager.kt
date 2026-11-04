package com.shopmanager.app.data.cache

import android.content.Context
import com.shopmanager.app.data.updates.UpdateDownloadPhase
import com.shopmanager.app.data.updates.UpdateDownloadState
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * إدارة الذاكرة المؤقتة (cacheDir) للتطبيق — صور المشاركة المؤقتة، ملفات التحديث
 * المحمّلة، كاش WebView.
 *
 * لا تلمس هذه الأداة أبداً بيانات الديون أو المواد (مخزّنة في Firestore ونسخ
 * filesDir/backups)، ولا تمسح ملف التحديث أثناء تحميله أو قبل تثبيته (على عكس
 * deleteRecursively() على cacheDir كاملاً التي قد تقطع تحميلاً جارياً).
 *
 * كل الدوال هنا تقوم بعمليات قرص — استدعها من Dispatchers.IO فقط.
 */
object AppCacheManager {

    private const val UPDATES_DIR = "updates"
    private const val SHARED_IMAGES_DIR = "shared_images"

    private const val SHARED_IMAGE_MAX_AGE_MS = 24L * 60 * 60 * 1000       // يوم
    private const val UPDATE_APK_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000    // 3 أيام

    /** الحجم الكلي للذاكرة المؤقتة بالبايت. */
    fun sizeBytes(context: Context): Long = dirSize(context.cacheDir)

    /**
     * يمسح كل شيء في cacheDir. ملف التحديث يُحفظ إذا كان تحميله جارياً أو
     * اكتمل ولم يُثبَّت بعد. يعيد عدد البايتات المحرَّرة فعلياً.
     */
    fun clear(context: Context): Long {
        val root = context.cacheDir ?: return 0L
        val before = dirSize(root)
        val phase = UpdateDownloadState.phase.value
        val keepUpdates = phase is UpdateDownloadPhase.InProgress || phase is UpdateDownloadPhase.Done
        root.listFiles()?.forEach { child ->
            if (keepUpdates && child.name == UPDATES_DIR) return@forEach
            runCatching { child.deleteRecursively() }
        }
        return (before - dirSize(root)).coerceAtLeast(0L)
    }

    /**
     * تنظيف صامت للملفات القديمة التي لم تعد مفيدة: صور المشاركة بعد يوم،
     * وملف APK المحمَّل بعد 3 أيام (إلا لو كان تحميل جديد جارياً). يمنع نمو
     * الكاش بصمت مع الوقت على الهواتف قليلة المساحة.
     */
    fun pruneStale(context: Context) {
        val now = System.currentTimeMillis()
        val root = context.cacheDir ?: return
        pruneOlderThan(File(root, SHARED_IMAGES_DIR), now - SHARED_IMAGE_MAX_AGE_MS)
        val phase = UpdateDownloadState.phase.value
        if (phase !is UpdateDownloadPhase.InProgress && phase !is UpdateDownloadPhase.Done) {
            pruneOlderThan(File(root, UPDATES_DIR), now - UPDATE_APK_MAX_AGE_MS)
        }
    }

    fun format(bytes: Long): String {
        val nf = NumberFormat.getNumberInstance(Locale("ar")).apply { maximumFractionDigits = 1 }
        return when {
            bytes < 1024L -> "${nf.format(bytes)} بايت"
            bytes < 1024L * 1024 -> "${nf.format(bytes / 1024.0)} ك.ب"
            else -> "${nf.format(bytes / (1024.0 * 1024.0))} م.ب"
        }
    }

    private fun pruneOlderThan(dir: File, cutoffMillis: Long) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoffMillis) runCatching { f.deleteRecursively() }
        }
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { total += dirSize(it) }
        return total
    }
}
