package com.shopmanager.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shopmanager.app.data.settings.SettingsRepository

/**
 * يعيد تشغيل [RealtimeSyncService] بعد إعادة تشغيل الجهاز أو بعد تحديث
 * التطبيق (تحديث الـ APK يقتل الخدمة) — وإلا ستبقى الإشعارات الفورية متوقفة
 * إلى أن يفتح المستخدم التطبيق بنفسه. البدء من هذين البثّين مسموح لخدمة أمامية
 * من نوع specialUse على أندرويد 12–15.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // STABILITY: a receiver that throws at boot/update kills the process
        // before the person even opens the app - never let that happen.
        try {
            val settings = SettingsRepository(context)
            if (settings.notificationsEnabled && settings.realtimeSyncEnabled) {
                RealtimeSyncService.start(context)
            }
        } catch (_: Exception) {
        }
    }
}
