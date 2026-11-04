package com.shopmanager.app.data.updates

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shopmanager.app.data.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * FEATURE ADDED ("والتحميل يكون في الخلفية" — see [UpdateDownloadState] for
 * the other half of this fix): the in-app update APK download now runs
 * inside a real foreground service — the same pattern this codebase
 * already uses for [com.shopmanager.app.data.notifications.RealtimeSyncService] —
 * instead of a coroutine tied to whichever screen's composition happened
 * to start it. `dataSync` is the correct foreground-service type here (this
 * genuinely is downloading data), unlike RealtimeSyncService's `specialUse`.
 *
 * A small persistent progress notification is required by Android for any
 * foreground service and doubles as exactly the visible "downloading in
 * the background" affordance the request asked for — tapping it just
 * reopens the app. The service stops itself the moment the download
 * finishes, fails, or never got a usable URL; it never lingers.
 */
class UpdateDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL)
        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME).orEmpty()

        NotificationHelper.ensureChannels(this)

        if (apkUrl.isNullOrBlank() || !enterForeground(versionName)) {
            if (apkUrl.isNullOrBlank()) UpdateDownloadState.error("رابط التحديث غير صالح")
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            when (val result = ApkDownloader.download(applicationContext, apkUrl) { percent ->
                UpdateDownloadState.progress(percent)
                notify(buildNotification(percent, versionName))
            }) {
                is DownloadState.Done -> {
                    // Posted BEFORE the state flips to Done: if a dialog is on screen
                    // it installs immediately and cancels this notification (see
                    // ApkDownloader.install); the other order could leave it stale.
                    runCatching {
                        NotificationHelper.showUpdateReadyNotification(applicationContext, result.file, versionName)
                    }
                    UpdateDownloadState.done(result.file)
                }
                is DownloadState.Error -> UpdateDownloadState.error(result.message)
                is DownloadState.InProgress -> Unit
            }
            stopSelf()
        }
        // Not START_STICKY: a killed-and-restarted service would have no
        // URL to resume with (onStartCommand's intent wouldn't redeliver
        // reliably) and no partial-resume support in ApkDownloader anyway
        // — restarting empty-handed would be worse than just stopping and
        // letting the person tap "تحديث الآن" again.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(versionName: String): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIF_ID_UPDATE_DOWNLOAD,
            buildNotification(0, versionName),
            type
        )
        true
    } catch (_: Exception) {
        // ForegroundServiceStartNotAllowedException وما شابه — نتوقف بهدوء
        // ونترك للشاشة زر إعادة المحاولة.
        false
    }

    private fun buildNotification(percent: Int, versionName: String) =
        NotificationHelper.buildUpdateDownloadNotification(this, percent, versionName)

    private fun notify(notification: android.app.Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NotificationHelper.NOTIF_ID_UPDATE_DOWNLOAD, notification)
    }

    companion object {
        private const val EXTRA_APK_URL = "apk_url"
        private const val EXTRA_VERSION_NAME = "version_name"

        /** Starts the background download. Safe to call again while one is
         * already running (e.g. the person re-taps the button) — a fresh
         * intent just carries the same URL to the already-running/next
         * service instance and [ApkDownloader] always overwrites the same
         * target file, so this never runs two downloads at once. */
        fun start(context: Context, manifest: UpdateManifest) {
            UpdateDownloadState.start(manifest)
            val intent = Intent(context.applicationContext, UpdateDownloadService::class.java)
                .putExtra(EXTRA_APK_URL, manifest.apkUrl)
                .putExtra(EXTRA_VERSION_NAME, manifest.versionName)
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                UpdateDownloadState.error(e.message ?: "تعذر بدء التحميل")
            }
        }
    }
}
