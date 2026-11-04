package com.shopmanager.app.data.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shopmanager.app.data.settings.SettingsRepository

/**
 * "الإشعارات لا تأتي في الخلفية": الحل الوحيد المجاني (بلا Cloud Functions ولا
 * خطة Blaze) لإشعار لحظي والتطبيق مغلق هو إبقاء اتصال Firestore حيّاً داخل
 * خدمة أمامية (Foreground Service). WorkManager (انظر [BackgroundSyncWorker])
 * أدنى فاصل له 15 دقيقة، وأندرويد/الشركات المصنّعة تؤجّله ساعات أو تجمّده
 * تماماً عند "التوقف" (Doze، حماية البطارية، Xiaomi/Huawei/Oppo...) — لذلك لم
 * يكن يصل شيء فعلياً.
 *
 * الثمن الصادق: إشعار صغير صامت دائم (مطلوب من أندرويد لأي خدمة أمامية) +
 * استهلاك ذاكرة/بطارية بسيط. لذلك للمستخدم مفتاح مستقل بالإعدادات
 * ("المزامنة الفورية بالخلفية") لإيقافها على الأجهزة الضعيفة؛ عندها يعود
 * التطبيق للـ Worker فقط.
 *
 * النوع specialUse: لا يملك مهلة 6 ساعات مثل dataSync (أندرويد 15+). التطبيق
 * يُوزَّع كملف APK عبر GitHub لا عبر Play، فلا مشكلة سياسات.
 *
 * START_STICKY: يعيد النظام تشغيلها بعد قتلها لنقص الذاكرة. وبعد إعادة تشغيل
 * الجهاز أو تحديث التطبيق يعيد [BootReceiver] تشغيلها.
 */
class RealtimeSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        NotificationHelper.ensureChannels(this)
        if (!enterForeground()) return
        RemoteChangeWatcher.acquire(this, OWNER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() يفرض استدعاء startForeground خلال ثوانٍ
        // حتى لو كانت الخدمة تعمل أصلاً.
        if (!enterForeground()) return START_NOT_STICKY
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        RemoteChangeWatcher.release(OWNER)
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIF_ID_REALTIME,
                NotificationHelper.buildRealtimeServiceNotification(this),
                type
            )
            true
        } catch (_: Exception) {
            // مثلاً ForegroundServiceStartNotAllowedException عند البدء من الخلفية
            // بشكل غير مسموح، أو نقص صلاحية على بعض الأجهزة — نتوقف بهدوء
            // والـ Worker يبقى الاحتياط.
            stopSelf()
            false
        }
    }

    companion object {
        private const val OWNER = "service"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, RealtimeSyncService::class.java)
                )
            } catch (_: Exception) {
                // بدء من الخلفية غير مسموح الآن — نحاول عند فتح التطبيق التالي.
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, RealtimeSyncService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * نقطة واحدة تُطابق حالة "مراقبة التغييرات" مع إعدادات الجهاز الحالية — تُستدعى
 * عند فتح التطبيق وعند تغيير أي من مفتاحي الإشعارات/المزامنة الفورية في الإعدادات.
 *
 * - الإشعارات مطفأة → لا خدمة ولا مستمعات.
 * - الإشعارات مفعّلة → المستمعات تعمل ما دام التطبيق موجوداً، والخدمة الأمامية
 *   تبقيها حيّة بعد إغلاق التطبيق إذا كانت المزامنة الفورية مفعّلة.
 */
object NotificationSync {
    private const val OWNER_APP = "app"

    fun apply(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        if (!settings.notificationsEnabled) {
            RealtimeSyncService.stop(app)
            RemoteChangeWatcher.release(OWNER_APP)
            return
        }
        RemoteChangeWatcher.acquire(app, OWNER_APP)
        if (settings.realtimeSyncEnabled) {
            if (!RealtimeSyncService.isRunning) RealtimeSyncService.start(app)
        } else {
            RealtimeSyncService.stop(app)
        }
    }

    /** Called when the Activity is finishing for good — the service (if any) keeps its own owner. */
    fun onAppClosed() {
        RemoteChangeWatcher.release(OWNER_APP)
    }
}
