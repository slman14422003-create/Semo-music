package com.shopmanager.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * UPDATED: both features (الديون / debts and المواد / materials) now share a
 * single Firebase project - "shop-app-6d35e" - instead of the two separate
 * legacy projects ("slx23m" for debts, "abo-slman" for materials) that the
 * app previously juggled as two named FirebaseApp instances. There is no
 * collision risk between the two features: they already live in disjoint
 * Firestore collections (see [com.shopmanager.app.data.debts.DebtsRepository]
 * and [com.shopmanager.app.data.materials.MaterialsRepository]), so pointing
 * both at one project is a pure simplification - one app, one dashboard, one
 * set of security rules (see /firebase-rules/firestore.rules), one quota.
 *
 * `debtsDb` and `materialsDb` are both kept below (now returning the *same*
 * Firestore instance) purely so nothing else in the codebase has to change.
 *
 * The values here are the Web SDK config values translated into the native
 * FirebaseOptions builder - this app never used google-services.json /
 * the Google Services Gradle plugin, so this manual init is intentional,
 * not a workaround.
 */
object FirebaseModule {

    private const val APP_NAME = "shopApp"

    private var initialized = false

    // @Synchronized: MainActivity (خيط خلفي) وWorker والخدمة الأمامية وRemoteChangeWatcher
    // كلها قد تستدعيها معاً. قبل ذلك كان الثاني يرى initialized=true فيعود فوراً
    // قبل أن ينتهي الأول من إنشاء FirebaseApp، ثم يرمي IllegalStateException
    // عند أول استخدام لقاعدة البيانات. الآن ينتظر حتى تكتمل التهيئة.
    @Synchronized
    fun init(context: Context) {
        if (initialized) return

        // STABILITY FIX: `initialized` used to flip to true BEFORE the
        // FirebaseApp was actually built. If initializeApp() threw even
        // once (low memory, a transient provider error), every later call
        // returned early and the first database access then crashed with
        // "FirebaseApp with name shopApp doesn't exist" until the process
        // was killed. It now flips only after a fully successful init,
        // and an app instance that already exists is simply reused.
        val app = try {
            FirebaseApp.getInstance(APP_NAME)
        } catch (_: IllegalStateException) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAa0vKXHkoiA-odIKucgKXDLMqsbwhdMXw")
                .setApplicationId("1:583934573941:web:e5ba92706635f06945e73c")
                .setProjectId("shop-app-6d35e")
                .setStorageBucket("shop-app-6d35e.firebasestorage.app")
                .setGcmSenderId("583934573941") // messagingSenderId
                .build()
            FirebaseApp.initializeApp(context.applicationContext, options, APP_NAME)
        }

        // Configure the persistent cache exactly once for this app instance
        // (still guarding against the old "enableIndexedDbPersistence called
        // twice" bug class from the original web version). Settings can only
        // be changed before the first use of the instance, so a failure here
        // (already used) must not undo the successful app init above.
        try {
            configureFirestore(FirebaseFirestore.getInstance(app))
        } catch (_: IllegalStateException) {
        }
        initialized = true
    }

    private fun configureFirestore(db: FirebaseFirestore) {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    private val sharedDb: FirebaseFirestore
        get() = FirebaseFirestore.getInstance(FirebaseApp.getInstance(APP_NAME))

    /**
     * PERF: خيط خلفي واحد مشترك لاستدعاءات مستمعات لقطات Firestore التي تغذّي الشاشات.
     * بدونه تصل كل لقطة على الخيط الرئيسي، فيُحلَّل فيه كل مستند (وتُرتَّب الملاحظات) في
     * منتصف الرسم — وهذا أبرز مصدر تقطّع على الهواتف الضعيفة عند وصول بيانات كثيرة أو بعد
     * أي كتابة. الخيط الواحد (لا مجمّع خيوط) يحفظ ترتيب اللقطات: لقطة أقدم لا يمكن أن
     * تتجاوز أحدث وتعرض بيانات قديمة. مستمعو الخدمة الخلفية لهم منفذهم الخاص.
     */
    val snapshotExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "firestore-snapshots").apply { isDaemon = true } }
    }

    val debtsDb: FirebaseFirestore get() = sharedDb
    val materialsDb: FirebaseFirestore get() = sharedDb
    val notesDb: FirebaseFirestore get() = sharedDb
}
