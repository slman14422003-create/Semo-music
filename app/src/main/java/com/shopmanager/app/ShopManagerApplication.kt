package com.shopmanager.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration

/**
 * Paired with the `tools:node="remove"` on WorkManagerInitializer in
 * AndroidManifest.xml. Implementing Configuration.Provider is the
 * officially documented way to get WorkManager's "on-demand
 * initialization": instead of WorkManager building its Room database via
 * a ContentProvider that runs unconditionally before this class's
 * onCreate on every single cold start, it now only initializes itself the
 * first time something actually calls WorkManager.getInstance(...) — in
 * this app, that's BackgroundSyncWorker.schedule(), which MainActivity
 * calls from a background coroutine (see MainActivity.kt), not on the
 * main thread during startup.
 */
class ShopManagerApplication : Application(), Configuration.Provider {

    // PERF/السلاسة: WorkManager بسجلّه الافتراضي (INFO) يكتب سطر لوج لكل
    // job يبدأ/ينتهي. بنسخة release ما له فايدة (Log.i أصلاً محذوف بـ R8 هناك
    // لكن بناء الرسالة نفسه يتم)، فنرفع المستوى لـ ERROR هناك ونبقيه INFO
    // بنسخة debug للتشخيص.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
    }

    /**
     * أداة اكتشاف التقطيع (jank): بنسخة debug فقط، يطبع في logcat (فلتر
     * "StrictMode") أي قراءة/كتابة ملف أو شبكة على الخيط الرئيسي، وأي
     * Closeable أو Cursor تُرك مفتوحًا — وهي أكثر أسباب التقطيع والتسريب
     * شيوعًا. penaltyLog فقط (بدون crash أو dialog) فلا يغيّر سلوك التطبيق،
     * ولا يشتغل أصلاً بنسخة release.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectCleartextNetwork()
                .penaltyLog()
                .build()
        )
    }
}
