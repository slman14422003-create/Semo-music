package com.shopmanager.app.data.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Progress of an in-app APK download — "من التطبيق نفسه بدون متصفح
 * خارجي" (like Telegram's in-chat file download): a plain 0..100 percent
 * the caller renders as a LinearProgressIndicator, no external app ever
 * opens for the download step. */
sealed class DownloadState {
    data class InProgress(val percent: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object ApkDownloader {

    /** سقف حجم معقول لـ APK هذا التطبيق (الحالي أقل من 30MB). أي ملف أكبر
     * بكثير يعني رابطًا خاطئًا/خبيثًا، فنرفضه قبل أن يملأ تخزين الجهاز. */
    private const val MAX_APK_BYTES = 150L * 1024 * 1024
    private const val BUFFER_SIZE = 64 * 1024
    private const val TIMEOUT_MS = 20_000

    /** Streams the APK at [apkUrl] into this app's own cache dir (exposed
     * to the system installer only through FileProvider — see
     * file_paths.xml — never through a world-readable path), reporting
     * percent complete via [onProgress].
     *
     * SECURITY (إضافة): قبل تسليم الملف للمثبّت يُتحقق منه محليًا:
     *  - هو فعلاً APK (ZIP) سليم وغير مبتور (الحجم المُستلم = الحجم المعلن).
     *  - اسم حزمته نفس اسم هذا التطبيق. بدون هذا الفحص، رابط تحديث مُعدَّل
     *    (لوحة المسؤول تسمح بتغيير رابط التحديثات) كان يقدر يدفع المستخدم
     *    لتثبيت تطبيق مختلف تمامًا.
     *  - ليس أقدم من النسخة المثبّتة (منع downgrade).
     *  - توقيعه يطابق توقيع التطبيق المثبّت؛ وإلا سيرفضه أندرويد عند التثبيت
     *    برسالة غامضة، فنعرض سببًا واضحًا من الآن.
     * الكتابة تتم على ملف مؤقت ".part" ولا يُعاد تسميته إلى update.apk إلا بعد
     * نجاح كل الفحوصات، فلا يبقى ملف نصف محمّل يشبه تحديثًا جاهزًا. */
    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): DownloadState = withContext(Dispatchers.IO) {
        if (!apkUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext DownloadState.Error("رابط التحديث غير آمن (يجب أن يبدأ بـ https)")
        }
        var connection: HttpURLConnection? = null
        var partFile: File? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updatesDir, "update.apk")
            val part = File(updatesDir, "update.apk.part").also { partFile = it }
            if (outFile.exists()) outFile.delete()
            if (part.exists()) part.delete()

            connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                // بدون ضغط transparent: يضمن أن Content-Length = حجم الملف
                // الحقيقي، فيشتغل شريط التقدم والتحقق من اكتمال التحميل.
                setRequestProperty("Accept-Encoding", "identity")
            }
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext DownloadState.Error("تعذر تحميل الملف — رمز الحالة ${connection.responseCode}")
            }
            // بعد اتباع أي redirect: لازم نبقى على HTTPS (HttpURLConnection
            // أصلاً لا يتبع https→http، هذا فحص إضافي صريح).
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                return@withContext DownloadState.Error("رابط التحديث غير آمن (تحويل إلى اتصال غير مشفّر)")
            }

            val totalBytes = connection.contentLengthLong
            if (totalBytes > MAX_APK_BYTES) {
                return@withContext DownloadState.Error("حجم ملف التحديث غير معقول — تم إيقاف التحميل")
            }
            val needed = if (totalBytes > 0) totalBytes else 0L
            if (needed > 0 && updatesDir.usableSpace < needed + 16L * 1024 * 1024) {
                return@withContext DownloadState.Error("مساحة التخزين غير كافية لتحميل التحديث")
            }

            var readBytes = 0L
            var lastReportedPercent = -1

            connection.inputStream.use { input ->
                part.outputStream().buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        // يسمح بإلغاء التحميل فورًا (إيقاف الخدمة) بدل
                        // إكماله بالخلفية بلا فائدة.
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        readBytes += read
                        if (readBytes > MAX_APK_BYTES) {
                            return@withContext DownloadState.Error("حجم ملف التحديث غير معقول — تم إيقاف التحميل")
                        }
                        output.write(buffer, 0, read)
                        if (totalBytes > 0) {
                            val percent = ((readBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            if (totalBytes > 0 && readBytes != totalBytes) {
                return@withContext DownloadState.Error("التحميل لم يكتمل — أعد المحاولة")
            }

            verifyApk(context, part)?.let { reason ->
                return@withContext DownloadState.Error(reason)
            }

            if (!part.renameTo(outFile)) {
                return@withContext DownloadState.Error("تعذر حفظ ملف التحديث")
            }
            partFile = null // نجح: لا شيء للتنظيف
            // Unknown content-length: report 100 now that it's finished.
            if (totalBytes <= 0) onProgress(100)

            DownloadState.Done(outFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DownloadState.Error(e.message ?: "فشل التحميل")
        } finally {
            connection?.disconnect()
            // أي فشل/إلغاء: لا نترك ملفًا جزئيًا يستهلك مساحة الكاش.
            partFile?.let { if (it.exists()) it.delete() }
        }
    }

    // ── التحقق المحلي من ملف التحديث ─────────────────────────────────────

    /** @return null إذا الملف سليم، وإلا رسالة عربية بالسبب. */
    private fun verifyApk(context: Context, file: File): String? {
        // 1) توقيع ZIP: "PK\u0003\u0004" — يستبعد صفحات HTML/JSON خطأ (مثلاً
        //    صفحة خطأ من وكيل) اللي قد تُحمَّل كأنها APK بنجاح HTTP 200.
        val header = ByteArray(4)
        val headerRead = file.inputStream().use { it.read(header) }
        val isZip = headerRead == 4 &&
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        if (!isZip) return "الملف المُحمَّل ليس ملف تطبيق صالحًا"

        val pm = context.packageManager
        val archive = archiveInfo(pm, file.absolutePath)
            ?: return "ملف التحديث تالف ولا يمكن قراءته"

        // 2) نفس الحزمة — الفحص الأهم أمنيًا.
        if (archive.packageName != context.packageName) {
            return "ملف التحديث ليس لهذا التطبيق"
        }

        val installed = try {
            installedInfo(pm, context.packageName)
        } catch (e: Exception) {
            null
        }
        if (installed != null) {
            // 3) منع الرجوع لنسخة أقدم.
            if (PackageInfoCompat.getLongVersionCode(archive) < PackageInfoCompat.getLongVersionCode(installed)) {
                return "ملف التحديث أقدم من النسخة المثبّتة حاليًا"
            }
            // 4) تطابق التوقيع. إذا تعذّر على الجهاز قراءة توقيع الملف نترك
            //    القرار لمثبّت أندرويد نفسه (يفرض نفس الشرط) بدل رفض تحديث
            //    سليم بسبب اختلاف سلوك بعض الأجهزة.
            val newSigners = signerDigests(archive)
            val oldSigners = signerDigests(installed)
            if (newSigners != null && oldSigners != null && newSigners != oldSigners) {
                return "توقيع ملف التحديث لا يطابق توقيع التطبيق المثبّت (ملف معدَّل أو موقَّع بمفتاح آخر)"
            }
        }
        return null
    }

    // الواجهات القديمة (GET_SIGNATURES / getPackageArchiveInfo(int)) مطلوبة
    // لأجهزة API 24-32 (minSdk = 24)، فالتحذير مقصود ومكتوم على مستوى الدالة.
    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            pm.getPackageArchiveInfo(path, signingFlags())
        }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            pm.getPackageInfo(packageName, signingFlags())
        }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String>? {
        val signatures: Array<Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            }
        if (signatures.isNullOrEmpty()) return null
        return signatures.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** True once Android will actually let this app trigger a package
     * install — API 26+ requires the person to have flipped the "install
     * unknown apps" toggle for this app specifically first. Below API 26
     * the classic install-time permission covers this and no runtime
     * check is needed. */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Intent to send the person to the "السماح من هذا المصدر" system
     * settings screen for this app, when [canInstallPackages] is false. */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    /** Fires the system package installer for a previously-downloaded APK.
     * Uses the app's existing FileProvider authority (already declared in
     * the manifest for the materials-report share flow) so the installer
     * gets a content:// URI instead of a raw file:// path, which Android
     * 7+ blocks between apps (StrictMode FileUriExposedException). */
    fun install(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        // Installing from inside the app makes the "تم تحميل التحديث" notification redundant.
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context)
                .cancel(com.shopmanager.app.data.notifications.NotificationHelper.NOTIF_ID_UPDATE_READY)
        }
    }
}
