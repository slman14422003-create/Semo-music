package com.shopmanager.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.backup.DailyBackupWorker
import com.shopmanager.app.data.notifications.OverdueDebtsWorker
import com.shopmanager.app.data.notifications.BackgroundSyncWorker
import com.shopmanager.app.data.notifications.BatteryOptimizationHelper
import com.shopmanager.app.data.notifications.NotificationAction
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.notifications.NotificationSync
import com.shopmanager.app.data.performance.DevicePerformance
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.FrameJankGuard
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.performance.resolvePerformanceTier
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.admin.AdminPanelScreen
import com.shopmanager.app.ui.lock.LockScreen
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.settings.SettingsScreen
import com.shopmanager.app.ui.splash.AppSplashScreen
import com.shopmanager.app.ui.theme.SetSystemBarsColor
import com.shopmanager.app.ui.theme.ShopManagerTheme
import com.shopmanager.app.ui.theme.rememberIsDarkTheme
import com.shopmanager.app.data.performance.LocalRefreshRateHz
import com.shopmanager.app.data.performance.RefreshRatePolicy
import com.shopmanager.app.data.performance.MotionSpecs

// The in-app liquid-glass splash (see AppSplashScreen) is a calm branded
// moment, not a progress readout — on a fast device real init can finish
// in well under 200ms, too quick to register as anything but a flicker.
// This is the one artificial delay in the whole startup path, just long
// enough for the splash to actually be seen before it crossfades away.
// PERF FIX: كان الحد الأدنى للهاتف السريع (1000ms) أطول من الهاتف الضعيف (650ms)،
// أي عكس المطلوب؛ الآن الهاتف السريع يكفيه أنيميشن السبلاش (~420ms) فقط.
private const val SPLASH_MIN_DISPLAY_MS = 600L

private const val SPLASH_MIN_DISPLAY_LOW_MS = 650L

private const val UI_SCALE = 1.08f

class MainActivity : FragmentActivity() {
    // Class-level (not inside setContent) so onNewIntent below - fired when
    // the app is already running and a *second* notification is tapped -
    // can update it too. A local `remember { mutableStateOf(...) }` created
    // inside setContent would only ever be initialized once, from the
    // Intent MainActivity happened to start with, and would never see a
    // later Intent onNewIntent hands us.
    private var pendingNotificationAction by mutableStateOf<NotificationAction?>(null)

    // True once the app has left the screen (home / app switch / screen off)
    // while a PIN is set — see onStop below and the lock overlay in setContent.
    private var lockedByLeave by mutableStateOf(false)
    private val lockSettings by lazy { SettingsRepository(applicationContext) }

    /**
     * onStop fires both when the person leaves the app and when the screen
     * turns off, which are exactly the two moments that should re-lock it.
     * (It also fires when another full-screen activity opens on top — e.g. the
     * share sheet or APK installer — so returning from those asks again too;
     * "القفل عند الخروج" in Settings turns this behavior off.)
     */
    /**
     * FEATURE ADDED (FLAG_SECURE): while a PIN is set (and "منع لقطات الشاشة"
     * is on in Settings) the window is marked secure — screenshots and screen
     * recording show black, and the app's thumbnail in the recent-apps list is
     * blank, so shop debts/prices can't leak from there. Re-evaluated on every
     * resume and called by SettingsScreen right after the PIN/toggle changes.
     */
    fun applySecureFlag() {
        val secure = lockSettings.hasPin && lockSettings.secureScreen
        if (secure) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var blurTarget: eightbitlab.com.blurview.BlurTarget? = null
        private set

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (blurTarget != null) return
        val content = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val inner = content.getChildAt(0) ?: return
        content.removeViewAt(0)
        val target = eightbitlab.com.blurview.BlurTarget(this)
        target.addView(
            inner,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        content.addView(
            target,
            0,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        blurTarget = target
    }

    override fun onResume() {
        super.onResume()
        systemMotionLimit = com.shopmanager.app.data.performance.SystemMotion.read(this)
        applySecureFlag()
    }

    override fun onStop() {
        com.shopmanager.app.data.learning.LearningHub.flush()
        if (!isFinishing && lockSettings.hasPin && lockSettings.autoLockOnLeave) {
            lockedByLeave = true
        }
        super.onStop()
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // PERF FIX (startup jitter): installSplashScreen() must run before
        // super.onCreate(). It puts a static app icon on a flat brand-color
        // background up immediately — nothing on that screen animates or
        // recomposes, so there is nothing to jitter while the phone is
        // still busy underneath.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // قياس التقطيع في نسخة debug فقط: الإطارات البطيئة تظهر في logcat بالوسم JankStats.
        if (BuildConfig.DEBUG) {
            androidx.metrics.performance.JankStats.createAndTrack(window) { fd ->
                if (fd.isJank) android.util.Log.w("JankStats", fd.toString())
            }
        }
        // Must be set before the first frame so even the splash/lock never lands in a screenshot.
        applySecureFlag()

        // "زجاجي بالكامل" (full glass, edge-to-edge): let this Activity's
        // window draw behind both system bars instead of the OS reserving
        // a solid, separately-colored strip for them. Combined with
        // transparent status/nav bar colors (see SetSystemBarsColor below),
        // this is what lets every liquid-glass header bleed all the way up
        // to the true top of the screen with no hard seam under the status
        // bar icons — previously the status bar was an opaque flat color
        // sitting directly above the gradient/glass header, and that flat
        // color meeting the header's glossy top edge was exactly the
        // visible dividing line.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val splashStartTime = System.currentTimeMillis()

        // Cold start via a notification tap (app wasn't running): the
        // Activity's very first Intent already carries the extras
        // NotificationHelper attached. Read it once, up front.
        pendingNotificationAction = NotificationAction.from(intent)

        var isReady by mutableStateOf(false)
        var detectedTier by mutableStateOf(PerformanceTier.BALANCED)
        // Tracks only whether Compose has produced its first frame — NOT
        // whether init is done. The static system splash now only needs to
        // bridge the gap until Compose can draw *something*; from that
        // first frame on, AppSplashScreen (rendered below, in Compose) is
        // what actually covers the screen and shows real init progress, so
        // there's no reason to keep the frozen system icon up any longer
        // than that. (Approximated via a LaunchedEffect(Unit) in setContent
        // — not pixel-exact to the true first draw, but close enough that
        // the handoff is invisible in practice.)
        var composeSplashAttached by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !composeSplashAttached }

        // معدل التحديث يُطبَّق حسب وضع الأداء داخل setContent (LaunchedEffect على performanceTier)،
        // ونتابع تغيّره الفعلي لتضبط الحركات سرعتها على 60Hz مقابل 90Hz+.
        refreshHz = currentDisplayHz()
        getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.registerDisplayListener(displayListener, null)

        // "حسن عمل التطبيق في الخلفية بشكل مخفي": one silent, at-most-once
        // system prompt so BackgroundSyncWorker/NoteReminderWorker keep
        // firing even on OEMs (Xiaomi/Huawei/Samsung, ...) that otherwise
        // freeze background work the moment the app isn't open - see
        // BatteryOptimizationHelper. Never shown more than once per
        // install and never blocks anything else in onCreate.
        BatteryOptimizationHelper.maybeRequest(this)

        // PERF FIX (startup jitter): all of Firebase init, notification
        // channel setup, device-tier detection (disk read), and scheduling
        // the background sync worker (which — see AndroidManifest.xml and
        // ShopManagerApplication.kt — is also where WorkManager's Room
        // database actually gets built the first time) used to run
        // synchronously on the main thread in onCreate, before Compose
        // ever got a chance to draw. That's real, measurable main-thread
        // work stacked right at cold start, which is what showed up as a
        // few seconds of visible jank/"shaking". None of it needs the main
        // thread, so it now all runs on a background dispatcher while the
        // splash screen (system, then in-app — see above) covers the UI.
        lifecycleScope.launch(Dispatchers.Default) {
            // STABILITY FIX: if any step below threw (Firebase init, channel
            // setup, a WorkManager scheduling error), the coroutine died
            // before `isReady = true` and the person was stuck on the splash
            // forever. Each step is now isolated, and the hand-off to the real
            // UI always happens in the finally block with a safe default tier.
            var tier = PerformanceTier.BALANCED
            try {
                runCatching { FirebaseModule.init(applicationContext) }
                runCatching { NotificationHelper.ensureChannels(applicationContext) }

                // "وضع لكل هاتف": classified once (cached after that), then
                // used below to switch off the heavier visual effects on
                // entry-level hardware — see DevicePerformance for the
                // detection signals.
                tier = runCatching { DevicePerformance.detectTier(applicationContext) }
                    .getOrDefault(PerformanceTier.BALANCED)

                // Weak/economic devices get a lighter, battery-guarded sync
                // schedule automatically — see BackgroundSyncWorker.schedule.
                runCatching { BackgroundSyncWorker.schedule(applicationContext, tier) }
                // Silent, fully local daily backup — no notification, ever
                // (see DailyBackupWorker/BackupManager).
                runCatching { DailyBackupWorker.schedule(applicationContext) }
                runCatching { OverdueDebtsWorker.schedule(applicationContext) }

                // "الإشعارات لا تأتي في الخلفية": يشغّل مراقب تغييرات الأجهزة الأخرى
                // (والخدمة الأمامية إذا كانت "المزامنة الفورية" مفعّلة) — مكان واحد
                // للكشف بدل منطق منفصل لكل شاشة. خارج الخيط الرئيسي، والسبلاش يغطي.
                runCatching { NotificationSync.apply(applicationContext) }

                // PERF: الحد الأدنى للسبلاش أقصر على الهاتف الضعيف؛ أنيميشن
                // السبلاش نفسه ~420ms فلا يُقطع.
                val minSplashMs = if (tier == PerformanceTier.LOW) SPLASH_MIN_DISPLAY_LOW_MS else SPLASH_MIN_DISPLAY_MS
                val elapsed = System.currentTimeMillis() - splashStartTime
                if (elapsed < minSplashMs) delay(minSplashMs - elapsed)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    detectedTier = tier
                    isReady = true
                }
            }
        }

        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var unlocked by remember { mutableStateOf(!settings.hasPin) }

            // FEATURE ADDED ("التحديثات اجبارية ... لما افتح التطبيق يتحقق
            // من التحديث"): a mandatory update check on every cold start,
            // separate from the optional "تحقق من التحديثات" button in
            // Settings (unchanged, still there). See ForceUpdateScreen's
            // own doc for the full rationale, including why this fails
            // OPEN (lets the person into the app) rather than closed
            // whenever the check itself can't complete — only a CONFIRMED
            // newer versionCode ever blocks entry.
            //
            // FEATURE ("والتحقق من التحديثات خليها منفصلة عن السبلاش"):
            // this check no longer gates the splash→app hand-off — see the
            // AnimatedContent block below, where the real app renders as
            // soon as `isReady` flips regardless of whether this check has
            // even finished yet. `forceUpdateManifest` becoming non-null is
            // now the ONLY thing that puts [ForceUpdateScreen] up, drawn as
            // an overlay on top of whatever the person is already looking
            // at, rather than a state the splash branch has to wait on.
            //
            // "تفعيل التحديث الاجباري" (لوحة المسؤول): a developer-only
            // on/off switch (settings.forceUpdateEnabled, default on) for
            // this whole automatic cold-start gate — see AdminPanelScreen.
            // Turning it off never touches the manual "تحقق من التحديثات"
            // button in Settings, only this silent startup check.
            var forceUpdateManifest by remember { mutableStateOf<com.shopmanager.app.data.updates.UpdateManifest?>(null) }
            // التحديث الاختياري (الحالة الطبيعية): مربع "تحديث الآن / لاحقاً".
            // شاشة الإجبار أعلاه لا تظهر إلا إذا وُضعت علامة [force] في إصدار GitHub.
            var optionalUpdateManifest by remember { mutableStateOf<com.shopmanager.app.data.updates.UpdateManifest?>(null) }

            // "تفضيل الأداء": loaded once here (not re-read from disk on
            // every recomposition), then kept in sync live when changed in
            // Settings via onPerformancePreferenceChanged below — same
            // pattern as themeMode/onThemeChanged just above.
            var performancePreference by remember { mutableStateOf(settings.performanceMode) }
            val performanceTier by remember {
                derivedStateOf { resolvePerformanceTier(detectedTier, performancePreference, systemMotionLimit) }
            }
            // الأداء القوي → ~90Hz، الاقتصادي → 60Hz؛ يُعاد التطبيق فور تغيّر الوضع من الإعدادات.
            LaunchedEffect(performanceTier) { applyRefreshRate(performanceTier) }
            // "تلقائي" فقط: إن رُصد تقطع فعلي في الإطارات على هذا الجهاز نزلنا بالمستوى خطوة
            // (ويُحفظ الحكم). المنخفض لا يحتاج رصداً، والاختيار اليدوي (مرتفع/منخفض) لا يُمَس.
            androidx.compose.runtime.DisposableEffect(isReady, performancePreference, detectedTier) {
                val stop = if (isReady && performancePreference == PerformanceMode.AUTO &&
                    detectedTier != PerformanceTier.LOW
                ) {
                    FrameJankGuard.start(refreshHz = { refreshHz }) {
                        detectedTier = DevicePerformance.downgradeCachedTier(applicationContext)
                    }
                } else null
                onDispose { stop?.invoke() }
            }
            // FEATURE ADDED ("إعادة فحص أداء الجهاز" in Settings → الأداء):
            // DevicePerformance.resetCachedTier existed already but had no
            // caller — wiring it up needs both steps done together (clear
            // the cached classification, then re-measure) and the result
            // written back into this same `detectedTier` state so the UI
            // actually picks up the new tier immediately, without asking
            // the person to restart the app.
            val onRecheckDevicePerformance: () -> Unit = {
                DevicePerformance.resetCachedTier(applicationContext)
                detectedTier = DevicePerformance.detectTier(applicationContext)
            }

            // Dismiss the static system splash screen as soon as Compose has
            // a frame ready to draw — see the composeSplashAttached comment
            // above. From here on AppSplashScreen below is what the person
            // actually sees while the background init finishes.
            LaunchedEffect(Unit) { composeSplashAttached = true }

            // "دعم كثافات الشاشة (DPI) بشكل أفضل": هذا التطبيق يستخدم dp/sp
            // في كل مكان أصلاً، لذا يتوسّع تلقائيًا بشكل سليم عبر كل
            // كثافات الشاشة العادية (mdpi..xxxhdpi). لكن عند تفعيل أكبر
            // إعداد نظام لحجم الخط (إعدادات إمكانية الوصول)، `fontScale`
            // قد يتجاوز 1.8-2x، وعندها تبدأ العناوين والأزرار الثابتة
            // الحجم بالتراكب فوق بعضها بدل التمرير بلطف. تحديد أعلى قيمة
            // معقولة (1.3x) يحافظ على استجابة التطبيق لتفضيل الشخص دون
            // كسر تخطيط الشاشات ذات العناصر الثابتة (الهيدر، الأزرار
            // العائمة)، نفس الأسلوب الذي توصي به وثائق Compose نفسها لدعم
            // مقاييس خط متطرفة بأمان.
            val baseDensity = LocalDensity.current
            val clampedDensity = remember(baseDensity) {
                Density(
                    // تكبير بسيط (8%) لكل العناصر والخطوط في كل الشاشات لأنها كانت
                    // صغيرة؛ غيّر UI_SCALE إذا بدك أكبر أو أصغر (1.0 = بدون تكبير).
                    density = baseDensity.density * UI_SCALE,
                    fontScale = baseDensity.fontScale.coerceIn(0.85f, 1.3f)
                )
            }

            CompositionLocalProvider(LocalDensity provides clampedDensity) {
            ShopManagerTheme(themeMode = themeMode) {
                // "زجاجي بالكامل" (fully glass): the status bar is now
                // always fully transparent (see the edge-to-edge window
                // setup in onCreate above and SetSystemBarsColor below) —
                // there's no separate OS-painted strip to keep color-synced
                // with the app any more, so whatever is actually behind it
                // (the splash gradient, or a screen's own liquid-glass
                // header) just shows straight through with no seam between
                // "system bar" and "app content". Only the icon *color*
                // still needs picking per screen: the splash and every
                // glass header (once unlocked) are dark enough for white
                // icons.
                //
                // BUG FIXED (LockScreen redesign — see LockScreen.kt): the
                // PIN lock screen no longer sits on the full-bleed
                // [BrandGradient] wash — Claude-style redesign made it a
                // plain neutral background like every ordinary screen (see
                // LockScreen.kt's own note). So it now needs the same
                // `!isDark`-based icon color a normal unlocked screen gets,
                // not the forced-white treatment that's still correct for
                // the splash (which stays full-bleed colored). Only the
                // splash (`!isReady`) keeps the old forced-white behavior.
                val isDark = rememberIsDarkTheme(themeMode)
                SetSystemBarsColor(
                    // "الشريط السفلي العائم بدون الخلفية السوداء": this used
                    // to switch to a solid MaterialTheme.colorScheme.background
                    // once the splash finished, which on the dark theme
                    // (near-black DarkBackground) painted a flat black strip
                    // across the whole gesture-bar area — sitting directly
                    // under FloatingBottomNav's transparent side margins, so
                    // the floating pill visibly had a black rectangle behind
                    // it instead of the page just continuing through. The nav
                    // bar now stays fully transparent always, exactly like
                    // the status bar already does, so FloatingBottomNav's
                    // margins show the real page content/background instead
                    // of a separately-painted solid color.
                    navigationBarColor = Color.Transparent,
                    // BUG FIXED ("في الوضع النهاري شريط الحالة يصبح ابيض كله"):
                    // this used to force white/light icons (`false`) for
                    // every *unlocked*, ready screen regardless of theme —
                    // only the lock screen actually read `!isDark`. In light
                    // mode that put white status-bar icons over the app's
                    // own white/cream background, so the icons themselves
                    // vanished and the whole status bar area read as a flat
                    // blank white strip. Only the splash (`!isReady`, which
                    // always sits on its own colored full-bleed background)
                    // still needs the forced-white treatment; every other
                    // ready screen — locked or not — now follows the actual
                    // theme like navigationBarDarkIcons already did.
                    statusBarDarkIcons = if (isReady) !isDark else false,
                    navigationBarDarkIcons = if (isReady) !isDark else false
                )

                Surface {
                    // A one-time transition from the in-app splash into the
                    // real UI once isReady flips true. On STANDARD/HIGH this
                    // is a soft iOS-style "zoom + fade" hand-off (the splash
                    // very slightly scales up and fades out while the real
                    // UI scales in from a touch smaller than full size) —
                    // the same "settling into place" feel as an iOS app's
                    // launch screen dissolving into its first real screen,
                    // rather than a flat opacity cut. LOW tier keeps this to
                    // a cheap, near-instant plain fade — same convention as
                    // every other animation in this file — since this only
                    // ever runs once per cold start either way, so it's not
                    // a recurring cost worth guarding further.
                    AnimatedContent(
                        targetState = isReady,
                        label = "splashToApp",
                        transitionSpec = { MotionSpecs.handoffTransition(performanceTier) }
                    ) { ready ->
                        if (!ready) {
                            AppSplashScreen(motion = com.shopmanager.app.ui.common.logoMotionFor(performanceTier))
                        } else {
                            // Runs exactly once, right as the splash hands
                            // off to the real UI. Uses `ready` as the key
                            // (not Unit) purely so this can never fire
                            // during the brief `!ready` frames above —
                            // functionally it only ever runs the one time
                            // `ready` flips true for the life of this
                            // Activity instance.
                            //
                            // FEATURE ("خليها منفصلة عن السبلاش"): this no
                            // longer blocks anything from rendering below —
                            // it just runs in the background and, if it
                            // ever confirms a newer versionCode, sets
                            // `forceUpdateManifest`, which the overlay right
                            // after this AnimatedContent block picks up. The
                            // person reaches the real app immediately either
                            // way; a slow network only delays the mandatory
                            // screen showing up, never the app opening.
                            LaunchedEffect(ready) {
                                when (val result = com.shopmanager.app.data.updates.UpdateChecker.check(
                                    applicationContext,
                                    settings.updateManifestUrl,
                                    timeoutMs = com.shopmanager.app.data.updates.UpdateChecker.STARTUP_TIMEOUT_MS
                                )) {
                                    is com.shopmanager.app.data.updates.UpdateCheckResult.UpdateAvailable -> {
                                        // إجباري فقط إذا طلبه إصدار GitHub (علامة [force]) ولم يُوقف المطوّر
                                        // الخيار من لوحة المسؤول؛ غير ذلك يظهر مربع اختياري.
                                        if (result.manifest.force && settings.forceUpdateEnabled) {
                                            forceUpdateManifest = result.manifest
                                        } else if (com.shopmanager.app.data.updates.RemoteUpdateConfig
                                                .isOptionalUpdateEnabled(applicationContext)
                                        ) {
                                            // مفتاح عام من Firebase (لوحة المسؤول): إيقافه يخفي
                                            // هذا المربع عند كل المستخدمين، لا جهازك وحده.
                                            optionalUpdateManifest = result.manifest
                                        }
                                    }
                                    else -> Unit // UpToDate or Failed: fail open.
                                }
                            }

                            CompositionLocalProvider(
                                LocalPerformanceTier provides performanceTier,
                                LocalRefreshRateHz provides refreshHz,
                                // LOW: بلا تأثير الشدّ/التوهج عند آخر القائمة (shader يُرسم كل إطار أثناء السحب).
                                androidx.compose.foundation.LocalOverscrollFactory provides
                                    (if (performanceTier == PerformanceTier.LOW) null
                                    else androidx.compose.foundation.LocalOverscrollFactory.current)
                            ) {
                                // Load the persisted currency symbol into the
                                // app-wide holder once, so every screen
                                // (dashboard, debts, materials, notifications)
                                // shows the right currency from the very
                                // first frame of the real app.
                                LaunchedEffect(Unit) { AppSettingsState.setCurrency(settings.currencySymbol) }

                                // Android 13+ requires explicit runtime
                                // permission to post notifications (needed
                                // for the low-stock shopping list and
                                // new-debt alerts). Requested once the real
                                // UI is up, not while the splash is showing.
                                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.RequestPermission()
                                ) { }
                                LaunchedEffect(Unit) {
                                    // Only when notifications are switched on in Settings and the
                                    // permission isn't already granted - it used to fire the system
                                    // request on every single cold start regardless.
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        settings.notificationsEnabled &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }

                                // BUG FIXED ("الخروج من الشاشات" لا يعمل —
                                // شاشة القفل تحديدًا): this was a plain
                                // `if (!unlocked) LockScreen else
                                // ShopManagerApp` — a hard, un-animated cut
                                // the instant the PIN is accepted. Every
                                // other hand-off in this file (splash→app
                                // right above, every screen push/pop in
                                // NavHost) got the full Claude-motion
                                // treatment; this one silently never did,
                                // so "unlocking" was the one exit in the
                                // whole app with literally zero animation
                                // — exactly the kind of gap that reads as
                                // "the exit animation doesn't work" even
                                // though every other screen's exit does.
                                // Reuses the exact same transition
                                // (MotionSpecs.handoffTransition) as the splash→app
                                // AnimatedContent just above, so unlocking
                                // now reads as one continuous motion
                                // language with the rest of the app instead
                                // of a one-off snap.
                                AnimatedContent(
                                    targetState = unlocked,
                                    label = "lockToApp",
                                    transitionSpec = { MotionSpecs.handoffTransition(performanceTier) }
                                ) { isUnlocked ->
                                    if (!isUnlocked) {
                                        LockScreen(settings = settings, onUnlocked = { unlocked = true; lockedByLeave = false })
                                    } else {
                                        ShopManagerApp(
                                            settings = settings,
                                            onThemeChanged = { themeMode = it },
                                            onPerformancePreferenceChanged = { performancePreference = it },
                                            onRecheckDevicePerformance = onRecheckDevicePerformance,
                                            pendingNotificationAction = pendingNotificationAction,
                                            onConsumeNotificationAction = { pendingNotificationAction = null }
                                        )
                                    }
                                }

                                // FEATURE ADDED ("عند الخروج او اغلاق الشاشة يطلب البصمة كل مرة"):
                                // onStop() below raises [lockedByLeave]; this overlay then
                                // covers the already-open app with the lock screen. It is an
                                // overlay (not a swap) on purpose: ShopManagerApp stays composed
                                // underneath, so after unlocking the person lands exactly where
                                // they were (same tab / same open screen) instead of at the
                                // dashboard. Touches and Back are swallowed so nothing under it
                                // can be reached.
                                if (lockedByLeave && unlocked) {
                                    BackHandler { moveTaskToBack(true) }
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        awaitPointerEvent().changes.forEach { it.consume() }
                                                    }
                                                }
                                            }
                                    ) {
                                        LockScreen(
                                            settings = settings,
                                            onUnlocked = { lockedByLeave = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // FEATURE ("والتحقق من التحديثات خليها منفصلة عن
                    // السبلاش"): the mandatory update check above no longer
                    // gates the splash→app hand-off — the person reaches
                    // the real app immediately once `isReady` flips, and
                    // this gate appears ON TOP of it as its own overlay,
                    // only if (and whenever) the background check actually
                    // confirms a newer versionCode. Drawn as a sibling of
                    // AnimatedContent rather than nested inside its `ready`
                    // branch, the same reasoning as the admin PIN dialog /
                    // notification dialogs further down this file — it has
                    // to be able to appear over whichever screen the person
                    // is already on.
                    forceUpdateManifest?.let { manifest ->
                        com.shopmanager.app.ui.update.ForceUpdateScreen(
                            manifest = manifest,
                            currentVersion = com.shopmanager.app.data.updates.AppVersionInfo.current(applicationContext)
                        )
                    }
                    if (forceUpdateManifest == null) {
                        optionalUpdateManifest?.let { manifest ->
                            com.shopmanager.app.ui.update.UpdateAvailableDialog(
                                manifest = manifest,
                                currentVersion = com.shopmanager.app.data.updates.AppVersionInfo.current(applicationContext),
                                onLater = { optionalUpdateManifest = null }
                            )
                        }
                    }
                }
            }
            } // CompositionLocalProvider(LocalDensity) — clamped fontScale
        }
    }

    override fun onDestroy() {
        // الخدمة الأمامية (إن وُجدت) تحتفظ بمالكها الخاص فتبقى المستمعات حيّة؛
        // وإلا نغلقها بعد أن يغادر المستخدم التطبيق فعلاً (لا عند تدوير الشاشة).
        if (isFinishing) NotificationSync.onAppClosed()
        getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    /**
     * Warm start: the app is already running (this Activity instance is
     * still alive) and a notification is tapped. `android:launchMode=
     * "singleTop"` on MainActivity (see AndroidManifest.xml) is what
     * routes the tap here instead of spinning up a whole new Activity
     * instance - without it, this override would simply never fire and
     * the tap would silently do nothing while the app was open/backgrounded.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationAction = NotificationAction.from(intent)
    }

    /** معدل تحديث الشاشة الفعلي الآن؛ حالة Compose حتى تتفاعل الحركات مع تغيّره. */
    private var refreshHz by androidx.compose.runtime.mutableFloatStateOf(60f)
    // قيد الحركة من النظام (إزالة الحركة / توفير الطاقة) — يُحدَّث في onResume.
    private var systemMotionLimit by androidx.compose.runtime.mutableStateOf(com.shopmanager.app.data.performance.SystemMotionLimit.NONE)

    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            refreshHz = currentDisplayHz()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayHz(): Float = windowManager.defaultDisplay?.refreshRate?.takeIf { it > 0f } ?: 60f

    /**
     * الأداء القوي → أقرب وضع إلى 90Hz، الاقتصادي → 60Hz (انظر [RefreshRatePolicy]).
     * على شاشة 60Hz فقط لا يتغير شيء هنا، والإحساس "كأنها 90" يأتي من MotionSpecs.
     */
    @Suppress("DEPRECATION")
    private fun applyRefreshRate(tier: PerformanceTier) {
        RefreshRatePolicy.apply(window, windowManager.defaultDisplay, tier)
        RefreshRatePolicy.applyFrameRateHint(window, tier)
        refreshHz = currentDisplayHz()
    }

}
