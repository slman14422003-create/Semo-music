package com.shopmanager.app.ui.settings

import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceSm
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.lazy.LazyColumn
import androidx.lifecycle.repeatOnLifecycle
import com.shopmanager.app.data.cache.AppCacheManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shopmanager.app.data.backup.BackupManager
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.notifications.NotifKind
import com.shopmanager.app.data.notifications.NotifSound
import com.shopmanager.app.data.notifications.NotificationSounds
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.AppVersionInfo
import com.shopmanager.app.data.updates.UpdateCheckResult
import com.shopmanager.app.data.updates.UpdateDownloadPhase
import com.shopmanager.app.data.updates.UpdateDownloadService
import com.shopmanager.app.data.updates.UpdateDownloadState
import com.shopmanager.app.data.updates.UpdateChecker
import com.shopmanager.app.data.updates.UpdateManifest
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppInfoRow
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppSheet
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.GlassCard
import com.shopmanager.app.ui.common.ShareFormatDialog
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.notifications.NotificationSync
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.AppThemeMode
import com.shopmanager.app.ui.theme.LocalBrandGradientColors
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import com.shopmanager.app.data.performance.MotionSpecs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (AppThemeMode) -> Unit,
    onPerformancePreferenceChanged: (PerformanceMode) -> Unit = {},
    onRecheckDevicePerformance: () -> Unit = {},
    debtsViewModel: DebtsViewModel? = null,
    materialsViewModel: MaterialsViewModel? = null,
    onOpenHelp: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    // Re-applies FLAG_SECURE right after the PIN / screenshot toggle changes (see MainActivity.applySecureFlag).
    val applySecure: () -> Unit = {
        (com.shopmanager.app.ui.lock.BiometricAuth.findActivity(context) as? com.shopmanager.app.MainActivity)
            ?.applySecureFlag()
    }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(settings.currencySymbol) }
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    // "المزامنة الفورية بالخلفية": مفتاح مستقل لكل جهاز (انظر RealtimeSyncService).
    var realtimeSyncEnabled by remember { mutableStateOf(settings.realtimeSyncEnabled) }
    var overdueEnabled by remember { mutableStateOf(settings.overdueReminderEnabled) }
    var overdueDays by remember { mutableStateOf(settings.overdueDays) }
    var showHistory by remember { mutableStateOf(false) }
    var showBackupPicker by remember { mutableStateOf(false) }
    // BUG FIXED ("notifications sometimes never arrive at all", root
    // cause): the switch above only ever reflected this app's OWN saved
    // preference — it had no idea whether Android itself was actually
    // allowed to show a notification for this app. Denying the one-time
    // permission prompt on first launch (or turning notifications off for
    // this app later from the system Settings app, or a channel getting
    // silently disabled by the OS) all leave this switch showing "on" with
    // nothing in the UI hinting that nothing will actually arrive — every
    // notification call in NotificationHelper was already silently
    // no-op'ing in exactly that case (see its hasPermission check), just
    // with zero visibility into it from here. This now reads the real
    // system-level state directly (covers both the API 33+ runtime
    // permission and the general per-app notification toggle that exists
    // on every Android version) and shows a clear warning + a direct link
    // to the system notification settings for this app whenever the two
    // disagree, instead of the switch quietly lying.
    // "اصلح ميزات الاشعارات": beyond the single app-level flag, also check
    // each notification channel's own importance — see
    // NotificationHelper.blockedChannelLabels for why the app-level check
    // alone used to miss a channel the person disabled individually.
    var blockedChannelLabels by remember { mutableStateOf(emptyList<String>()) }
    fun checkSystemNotificationsAllowed(): Boolean {
        blockedChannelLabels = NotificationHelper.blockedChannelLabels(context)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && blockedChannelLabels.isEmpty()
    }
    var systemNotificationsAllowed by remember { mutableStateOf(checkSystemNotificationsAllowed()) }
    // ترابط الإعدادات بإذن النظام: تشغيل مفتاح "الإشعارات" كان يكتفي بحفظ
    // الخيار حتى لو رُفض إذن أندرويد 13+ سابقاً، فيبقى المفتاح "شغّال" بلا أي
    // إشعار فعلي. الآن يُطلب الإذن عند التشغيل إن لم يكن ممنوحاً، وتُحدَّث
    // حالة التحذير فور الرد.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { systemNotificationsAllowed = checkSystemNotificationsAllowed() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-check on every resume, not just once — this is exactly how
        // someone comes back after tapping the warning's "open settings"
        // button below and flipping the OS toggle there.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotificationsAllowed = checkSystemNotificationsAllowed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var performanceMode by remember { mutableStateOf(settings.performanceMode) }
    // مدير أصوات الإشعارات: الصوت المختار لكل نوع + نوع الحوار المفتوح حالياً.
    var notifSounds by remember { mutableStateOf(NotifKind.values().associateWith { settings.soundFor(it) }) }
    var soundDialogKind by remember { mutableStateOf<NotifKind?>(null) }
    // زر "منح الإذن": يطلب إذن أندرويد 13+، وإن رُفض نهائياً (النظام لا يعيد إظهار
    // النافذة) ننقل المستخدم مباشرة لإعدادات إشعارات التطبيق ليمنحه يدوياً.
    val permissionRowLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        systemNotificationsAllowed = checkSystemNotificationsAllowed()
        if (!granted) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
    fun isPostPermissionGranted(): Boolean = android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var recheckTick by remember { mutableStateOf(0) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showExportShareChoice by remember { mutableStateOf(false) }
    val brandColor = LocalBrandGradientColors.current.first().toArgb()

    // النسخ الاحتياطي التلقائي المحلي — silent daily local backup, restore
    // only from here or from the automatic "server unavailable" prompt.
    val scope = rememberCoroutineScope()
    val debtsRepoForBackup = remember { DebtsRepository() }
    val materialsRepoForBackup = remember { MaterialsRepository() }
    var backups by remember { mutableStateOf(emptyList<BackupManager.BackupInfo>()) }
    var pendingRestore by remember { mutableStateOf<BackupManager.BackupInfo?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }

    // FEATURE ADDED ("استعادة نسخة تم تصديرها"): export/import a REAL JSON
    // file (as opposed to the plain-text share above, which can't be read
    // back) via the system's own "Save as.../Open..." pickers — no storage
    // permission needed since the person themselves chooses the location
    // through Android's Storage Access Framework.
    var isExportingFile by remember { mutableStateOf(false) }
    var exportFileStatus by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && debtsViewModel != null && materialsViewModel != null) {
            isExportingFile = true
            exportFileStatus = null
            scope.launch {
                try {
                    val json = BackupManager.buildSnapshotJson(debtsRepoForBackup, materialsRepoForBackup)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
                        } ?: throw java.io.IOException("تعذر فتح الملف للكتابة")
                    }
                    exportFileStatus = "تم حفظ الملف بنجاح ✅ يمكنك نقله لجهاز آخر أو حفظه بمكان آمن."
                } catch (e: Exception) {
                    exportFileStatus = "تعذر التصدير: ${e.message ?: "حاول مرة أخرى"}"
                } finally {
                    isExportingFile = false
                }
            }
        }
    }
    // OpenDocument (not GetContent): keeps read access to the exact file
    // the person picked long enough for the confirmation dialog below to
    // actually read it, and doesn't require any broad storage permission.
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImportUri = uri }

    // التحديثات (Settings → check for update, in-app download+install):
    // see data/updates/ for the actual networking. The manifest URL itself
    // is only ever set from the hidden developer panel; a normal user just
    // taps the button.
    val appVersion = remember { AppVersionInfo.current(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    // BUG FIXED ("لازم التحميل ينحفظ ... مو كل ما بدي اطلع وفوت اعيد
    // تحميلة"، "والتحميل يكون في الخلفية"): pendingUpdate seeds itself from
    // whatever UpdateDownloadState already knows about — so if a download
    // was already running when this screen composes again (the person
    // left Settings mid-download and came back, or just rotated), the
    // dialog reappears showing the real in-progress state instead of
    // starting blank. The download itself no longer runs in this
    // composable's scope at all — see UpdateDownloadService/UpdateDownloadState.
    var pendingUpdate by remember { mutableStateOf(UpdateDownloadState.activeManifest) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    val downloadPhase by UpdateDownloadState.phase.collectAsStateWithLifecycle()
    val isDownloadingUpdate = downloadPhase is UpdateDownloadPhase.InProgress
    val downloadPercent = (downloadPhase as? UpdateDownloadPhase.InProgress)?.percent ?: 0

    // Reacts once per actual phase *change* (not per recomposition), so a
    // download finishing/failing while this screen isn't even the one that
    // started it (came back to Settings mid-download) still gets handled
    // exactly once.
    LaunchedEffect(Unit) {
        UpdateDownloadState.phase.collect { phase ->
            when (phase) {
                is UpdateDownloadPhase.Done -> {
                    downloadedApk = phase.file
                    if (ApkDownloader.canInstallPackages(context)) {
                        ApkDownloader.install(context, phase.file)
                        pendingUpdate = null
                        UpdateDownloadState.reset()
                    } else {
                        needsInstallPermission = true
                    }
                }
                is UpdateDownloadPhase.Error -> {
                    updateStatusMessage = phase.message
                    pendingUpdate = null
                    UpdateDownloadState.reset()
                }
                else -> Unit
            }
        }
    }

    fun checkForUpdate() {
        isCheckingUpdate = true
        updateStatusMessage = null
        scope.launch {
            when (val result = UpdateChecker.check(context, settings.updateManifestUrl)) {
                is UpdateCheckResult.UpToDate -> updateStatusMessage = "أنت تستخدم أحدث إصدار ✅"
                is UpdateCheckResult.UpdateAvailable -> {
                    settings.lastUpdateCheckAt = System.currentTimeMillis()
                    pendingUpdate = result.manifest
                }
                is UpdateCheckResult.Failed -> updateStatusMessage = result.reason
            }
            isCheckingUpdate = false
        }
    }

    fun startDownload(manifest: UpdateManifest) {
        UpdateDownloadService.start(context, manifest)
    }

    val debtsSyncError = debtsViewModel?.hasSyncError?.collectAsStateWithLifecycle(initialValue = false)?.value ?: false
    val materialsSyncError = materialsViewModel?.hasSyncError?.collectAsStateWithLifecycle(initialValue = false)?.value ?: false
    var dismissedServerErrorBanner by remember { mutableStateOf(false) }

    // المزامنة: real connectivity + "آخر مزامنة ناجحة" (see data/sync/SyncStatus.kt).
    // collectAsState(initial=...) reads the connectivity synchronously on
    // first composition instead of assuming "متصل" until the first
    // callback lands, which would otherwise flash the wrong state for a
    // frame on a device that opens this screen while already offline.
    // PERF FIX: observe() returns a new callbackFlow each call - without
    // remember it re-registered a system NetworkCallback on every recomposition.
    val connectivityFlow = remember(context) { com.shopmanager.app.data.sync.SyncConnectivityObserver.observe(context) }
    val isOnline by connectivityFlow.collectAsStateWithLifecycle(initialValue = true)
    var lastSyncedAt by remember { mutableStateOf(com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)) }
    var isManualSyncing by remember { mutableStateOf(false) }
    // Re-read the stored timestamp whenever a listener records a fresh
    // success (debtsSyncError/materialsSyncError flipping back to false is
    // the same signal DebtsViewModel/MaterialsViewModel already use to
    // call SyncStatusStore.recordSuccess) so this stays live without its
    // own polling loop.
    LaunchedEffect(debtsSyncError, materialsSyncError) {
        lastSyncedAt = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
    }
    // BUG FIXED (زر "مزامنة الآن" يبدو أنه لا يعمل): the effect above is
    // the ONLY thing that ever refreshed [lastSyncedAt] on this screen —
    // and it's keyed on the sync-error flags, which stay `false` the
    // entire time everything is working normally. So on the overwhelming
    // majority of taps (nothing was actually broken, the listener was just
    // stuck in a backoff or the connection briefly flapped) the value
    // shown never changed: forceReconnect() below genuinely reconnects and
    // the listeners genuinely record a new success, but this screen kept
    // displaying whatever timestamp it first loaded with, no matter how
    // many times the button was pressed - indistinguishable from the
    // button doing nothing at all. A light poll while this screen is open
    // picks up that same already-correct signal on a timer instead of
    // waiting for an error to flip first.
    LaunchedEffect(lifecycleOwner) {
        // PERF (سلاسة/بطارية): كانت حلقة الفحص تعمل كل ثانيتين طالما الشاشة
        // مركّبة — حتى والتطبيق في الخلفية. الآن تعمل فقط والشاشة ظاهرة فعلاً
        // (STARTED) وتتوقف تلقائياً عند الخروج، وقراءة الـ SharedPreferences
        // تتم على IO بدل الخيط الرئيسي.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(2000)
                val fresh = withContext(Dispatchers.IO) {
                    com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
                }
                if (fresh != lastSyncedAt) lastSyncedAt = fresh
            }
        }
    }

    fun runRestore(backup: BackupManager.BackupInfo) {
        isRestoring = true
        restoreStatus = null
        scope.launch {
            try {
                BackupManager.restore(backup, debtsRepoForBackup, materialsRepoForBackup)
                restoreStatus = "تمت الاستعادة بنجاح ✅"
                dismissedServerErrorBanner = true
            } catch (e: Exception) {
                restoreStatus = "تعذرت الاستعادة: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
            } finally {
                isRestoring = false
            }
        }
    }

    /** Same as [runRestore], reading the JSON from a person-picked file
     * (SAF Uri) instead of the on-device snapshot — see
     * [BackupManager.restoreFromJsonText]. */
    fun runRestoreFromUri(uri: Uri) {
        isRestoring = true
        restoreStatus = null
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw java.io.IOException("تعذر فتح الملف")
                }
                BackupManager.restoreFromJsonText(text, debtsRepoForBackup, materialsRepoForBackup)
                restoreStatus = "تمت الاستعادة من الملف بنجاح ✅"
                dismissedServerErrorBanner = true
            } catch (e: Exception) {
                restoreStatus = "تعذرت الاستعادة: ${e.message ?: "تأكد أن الملف نسخة احتياطية صالحة"}"
            } finally {
                isRestoring = false
            }
        }
    }

    // ======================================================================
    // حالة الواجهة الجديدة (أوراق الاختيار، الكاش، مفاتيح الحماية).
    // مفاتيح الحماية كانت معرّفة داخل أقسامها الشرطية القديمة؛ رُفعت هنا لأن
    // الصفوف الآن تُبنى داخل SettingsGroup.
    // ======================================================================
    var showThemeSheet by remember { mutableStateOf(false) }
    var showPerformanceSheet by remember { mutableStateOf(false) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    var biometricOn by remember { mutableStateOf(settings.biometricEnabled) }
    var autoLockOn by remember { mutableStateOf(settings.autoLockOnLeave) }
    var secureOn by remember { mutableStateOf(settings.secureScreen) }
    val biometricAvailable = remember(hasPin) {
        hasPin && com.shopmanager.app.ui.lock.BiometricAuth.isAvailable(context)
    }

    // PERF (سلاسة): قراءة ملفات النسخ الاحتياطي (تحليل JSON كامل) وحساب حجم
    // الكاش وتنظيف الملفات القديمة كلها عمليات قرص — كانت تتم على الخيط الرئيسي
    // أثناء أول رسم للشاشة (وهو ما يسبب تقطيعاً عند فتح الإعدادات). الآن تعمل
    // في الخلفية وتظهر نتائجها فور جاهزيتها.
    LaunchedEffect(Unit) {
        backups = withContext(Dispatchers.IO) { BackupManager.listBackups(context) }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { AppCacheManager.pruneStale(context) }
        cacheSizeBytes = withContext(Dispatchers.IO) { AppCacheManager.sizeBytes(context) }
    }

    val themeLabel = when (themeMode) {
        AppThemeMode.SYSTEM -> "حسب النظام"
        AppThemeMode.LIGHT -> "فاتح"
        AppThemeMode.DARK -> "داكن"
    }
    val performanceLabel = when (performanceMode) {
        PerformanceMode.AUTO -> "تلقائي (يضبط نفسه حسب الجهاز)"
        PerformanceMode.HIGH -> "مرتفع (كل التأثيرات)"
        PerformanceMode.LOW -> "منخفض (بدون تأثيرات، للأجهزة القديمة)"
    }

    fun syncNow() {
        if (isManualSyncing) return
        isManualSyncing = true
        scope.launch {
            val before = lastSyncedAt
            com.shopmanager.app.data.sync.SyncRetry.forceReconnect()
            // forceReconnect() يعيد فتح الاتصال فقط؛ ننتظر مهلة قصيرة ليبلغ أحد
            // المستمعين عن بيانات جديدة قبل إخفاء المؤشر (انظر الإصلاح السابق).
            var waited = 0L
            while (waited < 5000) {
                val fresh = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
                if (fresh != before) {
                    lastSyncedAt = fresh
                    break
                }
                kotlinx.coroutines.delay(250)
                waited += 250
            }
            lastSyncedAt = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
            isManualSyncing = false
        }
    }

    fun clearCacheNow() {
        if (isClearingCache) return
        isClearingCache = true
        cacheStatus = null
        scope.launch {
            val freed = withContext(Dispatchers.IO) { AppCacheManager.clear(context) }
            cacheSizeBytes = withContext(Dispatchers.IO) { AppCacheManager.sizeBytes(context) }
            cacheStatus = if (freed > 0) "تم تحرير ${AppCacheManager.format(freed)} ✅" else "لا يوجد شيء لمسحه"
            isClearingCache = false
        }
    }

    Scaffold(
        // الـ Scaffold الخارجي (NavHost) يحجز أصلاً مساحة الحواف السفلية/الجانبية،
        // فلا نكرر الـ insets هنا؛ الشريط العلوي يعالج حافة شريط الحالة بنفسه.
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            SettingsTopBar(
                title = "الإعدادات",
                onBack = onBack,
                onInfo = onOpenAbout
            )
        }
    ) { padding ->
        // REDESIGN + PERF: كانت الشاشة Column داخل verticalScroll تركّب كل
        // الأقسام (≈ 10 أقسام × AnimatedVisibility تدخل منفصل) دفعة واحدة عند
        // الفتح. LazyColumn يركّب فقط ما يظهر على الشاشة، وكل مجموعة عنصر
        // مستقل بمفتاح ثابت فلا تُعاد تركيبتها عند تغيّر حالة مجموعة أخرى.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = ScreenGap, end = ScreenGap, top = AppSpace6, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(ScreenGap)
        ) {
            // بطاقة الحساب العلوية (اسم التطبيق + شارة الإصدار)
            item(key = "account", contentType = "card") {
                SettingsAccountCard(name = "إدارة المحل", badge = "v${appVersion.name}")
            }

            // بطاقة بحدّ خارجي (مكان "Want more Claude?") — دليل الاستخدام
            item(key = "guide", contentType = "card") {
                SettingsPromoCard(
                    title = "دليل الاستخدام",
                    description = "تعرّف على كل ميزات التطبيق خطوة بخطوة: الديون والمواد والأسعار والنسخ الاحتياطي.",
                    buttonLabel = "فتح الدليل",
                    onClick = onOpenHelp
                )
            }

            // تنبيه تلقائي: يظهر فقط إذا تعذر تحميل البيانات من الخادم وتوجد
            // نسخة محلية يمكن العودة إليها (لا استرجاع صامت أبداً).
            if ((debtsSyncError || materialsSyncError) && backups.isNotEmpty() && !dismissedServerErrorBanner) {
                item(key = "serverBanner", contentType = "banner") {
                    GlassCard(
                        Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = MotionSpecs.contentTween(),
                                placementSpec = MotionSpecs.reorderSpring(),
                                fadeOutSpec = MotionSpecs.listItemFadeOut()
                            ),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(AppSpaceLg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(AppSpaceSm))
                                Text(
                                    "تعذّر الاتصال بالخادم",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(Modifier.height(AppSpace6))
                            Text(
                                "يمكنك استعادة آخر نسخة احتياطية محلية (${formatBackupDate(backups.first().createdAt)}) لحين عودة الاتصال.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(AppSpace10))
                            Row {
                                Button(onClick = { pendingRestore = backups.first() }) { Text("استعادة الآن") }
                                Spacer(Modifier.width(AppSpaceSm))
                                TextButton(onClick = { dismissedServerErrorBanner = true }) { Text("لاحقاً") }
                            }
                        }
                    }
                }
            }

            // عام: وضع الألوان / العملة / الأداء — كل واحد يفتح ورقة اختيار
            item(key = "general", contentType = "group") {
                SettingsGroup {
                    item(
                        icon = Icons.Outlined.Palette,
                        title = "وضع الألوان",
                        subtitle = themeLabel,
                        onClick = { showThemeSheet = true }
                    )
                    item(
                        icon = Icons.Outlined.AttachMoney,
                        title = "العملة",
                        subtitle = "$currency — تظهر في الديون والمواد والمشاركة",
                        onClick = { showCurrencyDialog = true }
                    )
                    item(
                        icon = Icons.Outlined.Speed,
                        title = "الأداء",
                        subtitle = performanceLabel,
                        onClick = { showPerformanceSheet = true }
                    )
                }
            }

            // الإشعارات
            item(key = "notifications", contentType = "group") {
                SettingsGroup {
                    switchItem(
                        icon = Icons.Outlined.Notifications,
                        title = "الإشعارات",
                        subtitle = "تنبيهات الديون وقائمة النواقص والملاحظات وتذكيراتها على هذا الجهاز",
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            notificationsEnabled = it
                            settings.notificationsEnabled = it
                            if (it && android.os.Build.VERSION.SDK_INT >= 33 &&
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            // يطابق المستمعات والخدمة الأمامية مع الإعداد فوراً.
                            NotificationSync.apply(context)
                        }
                    )
                    if (notificationsEnabled) {
                        switchItem(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "المزامنة الفورية بالخلفية",
                            subtitle = "تصلك إشعارات الأجهزة الأخرى لحظة حدوثها حتى والتطبيق مغلق (إشعار صامت صغير دائم). أوقفها على الهاتف الضعيف ليكتفي بفحص دوري.",
                            checked = realtimeSyncEnabled,
                            onCheckedChange = {
                                realtimeSyncEnabled = it
                                settings.realtimeSyncEnabled = it
                                NotificationSync.apply(context)
                            }
                        )
                        switchItem(
                            icon = Icons.Outlined.Timer,
                            title = "تذكير بالديون المتأخرة",
                            subtitle = "إشعار بالعملاء الذين مضى على دينهم أكثر من المدة المحددة",
                            checked = overdueEnabled,
                            onCheckedChange = {
                                overdueEnabled = it
                                settings.overdueReminderEnabled = it
                            }
                        )
                        if (overdueEnabled) {
                            item(
                                icon = Icons.Outlined.Timer,
                                title = "مدة اعتبار الدين متأخرًا",
                                subtitle = "$overdueDays يوم · اضغط للتغيير",
                                onClick = {
                                    val options = listOf(7, 14, 30, 60, 90)
                                    val next = options.firstOrNull { it > overdueDays } ?: options.first()
                                    overdueDays = next
                                    settings.overdueDays = next
                                }
                            )
                        }
                    }
                }
            }

            // مدير أصوات الإشعارات والأذونات
            item(key = "notificationSounds", contentType = "group") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpace6)) {
                val postPermissionGranted = systemNotificationsAllowed.let { isPostPermissionGranted() }
                SettingsGroup {
                    NotifKind.values().forEach { kind ->
                        item(
                            icon = Icons.Outlined.MusicNote,
                            title = kind.label,
                            subtitle = "الصوت: ${(notifSounds[kind] ?: kind.defaultSound).label}",
                            onClick = { soundDialogKind = kind }
                        )
                    }
                    item(
                        icon = Icons.Outlined.VerifiedUser,
                        title = "إذن الإشعارات",
                        subtitle = if (postPermissionGranted && systemNotificationsAllowed)
                            "ممنوح ✓ — اضغط لإدارة قنوات الإشعارات والاهتزاز في النظام"
                        else "غير ممنوح — اضغط لمنح الإذن وتفعيل الإشعارات",
                        onClick = {
                            if (!postPermissionGranted) {
                                permissionRowLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        }
                    )
                }
                SettingsFootnote("هذه الأصوات خاصة بهذا التطبيق فقط ولا تغيّر نغمة إشعارات الجهاز. يمكنك أيضاً اختيار أي نغمة موجودة على هاتفك.")
                }
            }

            // تحذير: المفتاح مفعّل لكن النظام يمنع الإشعارات فعلياً
            if (notificationsEnabled && !systemNotificationsAllowed) {
                item(key = "notificationsBlocked", contentType = "banner") {
                    GlassCard(
                        Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = MotionSpecs.contentTween(),
                                placementSpec = MotionSpecs.reorderSpring(),
                                fadeOutSpec = MotionSpecs.listItemFadeOut()
                            ),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(AppSpaceLg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(AppSpaceSm))
                                Text(
                                    "الإشعارات موقوفة من نظام الجهاز",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(Modifier.height(AppSpace6))
                            Text(
                                if (blockedChannelLabels.isNotEmpty())
                                    "المفتاح أعلاه مفعّل، لكن نظام أندرويد يمنع تحديداً هذه الإشعارات: ${blockedChannelLabels.joinToString("، ")} — لن تصلك حتى تُفعّلها من إعدادات إشعارات التطبيق."
                                else
                                    "المفتاح أعلاه مفعّل، لكن نظام أندرويد يمنع هذا التطبيق تحديداً من إظهار أي إشعار على هذا الجهاز — لن تصلك تنبيهات النواقص أو الديون الجديدة مهما حدث بالتطبيق حتى تُفعّلها من إعدادات الجهاز.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(AppSpace10))
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                context.startActivity(intent)
                            }) { Text("فتح إعدادات الإشعارات") }
                        }
                    }
                }
            }

            // الحماية
            item(key = "security", contentType = "group") {
                SettingsGroup {
                    switchItem(
                        icon = if (hasPin) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        title = "قفل برمز PIN",
                        subtitle = if (hasPin) "مفعّل — يحمي فتح التطبيق على هذا الجهاز فقط"
                        else "غير مفعّل — فعّله لحماية التطبيق برمز محلي",
                        checked = hasPin,
                        onCheckedChange = { on ->
                            if (on) {
                                showSetPinDialog = true
                            } else {
                                settings.clearPin()
                                hasPin = false
                                applySecure()
                            }
                        }
                    )
                    if (biometricAvailable) {
                        switchItem(
                            icon = Icons.Outlined.Fingerprint,
                            title = "فتح بالبصمة أو الوجه",
                            subtitle = "بديل أسرع عن كتابة الـ PIN، ويبقى الـ PIN متاحاً دائماً",
                            checked = biometricOn,
                            onCheckedChange = { biometricOn = it; settings.biometricEnabled = it }
                        )
                    }
                    if (hasPin) {
                        switchItem(
                            icon = Icons.Outlined.Timer,
                            title = "القفل عند الخروج",
                            subtitle = "يطلب الرمز أو البصمة كل مرة تخرج من التطبيق أو تُطفئ الشاشة",
                            checked = autoLockOn,
                            onCheckedChange = { autoLockOn = it; settings.autoLockOnLeave = it }
                        )
                        switchItem(
                            icon = Icons.Outlined.VisibilityOff,
                            title = "منع لقطات الشاشة",
                            subtitle = "يخفي التطبيق في لقطة الشاشة والتسجيل وفي قائمة التطبيقات الأخيرة",
                            checked = secureOn,
                            onCheckedChange = { secureOn = it; settings.secureScreen = it; applySecure() }
                        )
                    }
                }
            }

            // المزامنة
            item(key = "sync", contentType = "group") {
                SettingsGroup {
                    item(
                        icon = Icons.Outlined.Sync,
                        title = if (isOnline) "متصل" else "غير متصل بالإنترنت",
                        subtitle = when {
                            isManualSyncing -> "جاري إعادة المحاولة..."
                            else -> lastSyncedAt?.let { "آخر مزامنة ناجحة: ${formatBackupDate(it)} — اضغط للمزامنة الآن" }
                                ?: "لم تتم أي مزامنة على هذا الجهاز بعد — اضغط للمزامنة الآن"
                        },
                        enabled = !isManualSyncing,
                        onClick = { syncNow() },
                        trailing = {
                            if (isManualSyncing) {
                                SettingsSpinner()
                            } else {
                                SettingsStatusDot(
                                    if (isOnline) LocalSemanticColors.current.success else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }

            // النسخ الاحتياطي (تصدير) — يحتاج الـ ViewModels
            if (debtsViewModel != null && materialsViewModel != null) {
                item(key = "export", contentType = "group") {
                    SettingsGroup {
                        item(
                            icon = Icons.Outlined.Backup,
                            title = "تصدير نسخة احتياطية",
                            subtitle = "نص أو صورة بكل العملاء والديون والمواد والأسعار (واتساب، بريد، ملاحظات...)",
                            onClick = { showExportShareChoice = true }
                        )
                        item(
                            icon = Icons.Outlined.FileDownload,
                            title = "تصدير نسخة كملف (JSON)",
                            subtitle = exportFileStatus
                                ?: "ملف كامل يمكن استعادته لاحقًا على هذا الجهاز أو جهاز آخر",
                            enabled = !isExportingFile,
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
                                exportFileLauncher.launch("shop_manager_backup_$stamp.json")
                            },
                            trailing = if (isExportingFile) SpinnerTrailing else null
                        )
                    }
                }
            }

            // النسخ الاحتياطي التلقائي المحلي + الاستعادة
            item(key = "restore", contentType = "group") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpaceSm)) {
                    SettingsGroup {
                        item(
                            icon = Icons.Outlined.Restore,
                            title = "النسخة التلقائية على الجهاز",
                            subtitle = backups.firstOrNull()?.let {
                                "آخر نسخة: ${formatBackupDate(it.createdAt)} — ${it.personsCount} عميل، ${it.materialsCount} مادة · اضغط للاستعادة"
                            } ?: "لا توجد نسخة بعد — ستُنشأ تلقائيًا مع أول حفظ",
                            enabled = backups.isNotEmpty() && !isRestoring,
                            onClick = { backups.firstOrNull()?.let { pendingRestore = it } }
                        )
                        item(
                            icon = Icons.Outlined.UploadFile,
                            title = "استعادة من ملف",
                            subtitle = "اختر ملف JSON تم تصديره سابقًا (من هذا الجهاز أو جهاز آخر)",
                            enabled = !isRestoring,
                            onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                        )
                        item(
                            icon = Icons.Outlined.Backup,
                            title = "نسخ سابقة على الجهاز",
                            subtitle = "${backups.size} نسخة محفوظة · اضغط لاختيار نسخة أقدم",
                            enabled = backups.size > 1 && !isRestoring,
                            onClick = { showBackupPicker = true }
                        )
                        item(
                            icon = Icons.Outlined.History,
                            title = "سجل التعديلات",
                            subtitle = "آخر العمليات: إضافة وتعديل وحذف وسداد",
                            onClick = { showHistory = true }
                        )
                    }
                    SettingsFootnote(
                        "يحتفظ التطبيق بعدة نسخ حديثة على هذا الجهاز، وتُحدَّث بصمت بعد كل حفظ جديد. " +
                            "تُستعاد من هنا أو إذا تعذّر الاتصال بالخادم."
                    )
                    AnimatedVisibility(
                        visible = isRestoring,
                        enter = MotionSpecs.expandEnter(),
                        exit = MotionSpecs.expandExit()
                    ) {
                        LinearProgressIndicator(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpaceSm)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                    restoreStatus?.let { SettingsFootnote(it) }
                }
            }

            // التحديثات + الذاكرة المؤقتة
            item(key = "app", contentType = "group") {
                SettingsGroup {
                    item(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "التحديثات",
                        subtitle = when {
                            isCheckingUpdate -> "جارٍ التحقق..."
                            updateStatusMessage != null -> updateStatusMessage
                            else -> "الإصدار الحالي: ${appVersion.name} (${appVersion.code}) — اضغط للتحقق"
                        },
                        enabled = !isCheckingUpdate,
                        onClick = { checkForUpdate() },
                        trailing = if (isCheckingUpdate) SpinnerTrailing else null
                    )
                    item(
                        icon = Icons.Outlined.CleaningServices,
                        title = "مسح الذاكرة المؤقتة",
                        subtitle = cacheStatus
                            ?: cacheSizeBytes?.let { "الحجم الحالي: ${AppCacheManager.format(it)} — لا يمسح الديون أو المواد" }
                            ?: "جارٍ حساب الحجم...",
                        enabled = !isClearingCache,
                        onClick = { clearCacheNow() },
                        trailing = if (isClearingCache) SpinnerTrailing else null
                    )
                }
            }
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin ->
                settings.setPin(pin)
                hasPin = true
                applySecure()
                showSetPinDialog = false
            }
        )
    }

    if (showHistory) ChangeHistoryDialog(onDismiss = { showHistory = false })

    if (showBackupPicker) {
        BackupPickerDialog(
            backups = backups,
            formatDate = { formatBackupDate(it) },
            onPick = { showBackupPicker = false; pendingRestore = it },
            onDismiss = { showBackupPicker = false }
        )
    }

    pendingRestore?.let { backup ->
        GlassAlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("استعادة نسخة احتياطية؟") },
            text = {
                Text(
                    "سيتم استبدال كل الديون والعملاء والمواد والأسعار الحالية بمحتوى نسخة ${formatBackupDate(backup.createdAt)}. " +
                        "لا يمكن التراجع عن هذا بعد التنفيذ."
                )
            },
            confirmButton = {
                // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
                // PersonEditDialog.kt's doc comment.
                TextButton(shape = RectangleShape, onClick = {
                    val target = backup
                    pendingRestore = null
                    runRestore(target)
                }) { Text("استعادة") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { pendingRestore = null }) { Text("إلغاء") } }
        )
    }

    pendingImportUri?.let { uri ->
        GlassAlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("استعادة من ملف؟") },
            text = {
                Text(
                    "سيتم استبدال كل الديون والعملاء والمواد والأسعار الحالية بمحتوى الملف المختار. " +
                        "تأكد أنه ملف نسخة احتياطية صحيح تم تصديره من هذا التطبيق. لا يمكن التراجع عن هذا بعد التنفيذ."
                )
            },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    val target = uri
                    pendingImportUri = null
                    runRestoreFromUri(target)
                }) { Text("استعادة") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { pendingImportUri = null }) { Text("إلغاء") } }
        )
    }

    soundDialogKind?.let { kind ->
        NotificationSoundDialog(
            kind = kind,
            current = notifSounds[kind] ?: kind.defaultSound,
            onDismiss = { NotificationSounds.stopPreview(); soundDialogKind = null },
            initialUri = settings.customSoundUri(kind),
            onSelect = { sound, uri ->
                NotificationSounds.stopPreview()
                if (sound == NotifSound.DEVICE) settings.setCustomSoundUri(kind, uri)
                settings.setSoundFor(kind, sound)
                notifSounds = notifSounds + (kind to sound)
                // الصوت مرتبط بمعرّف القناة: إعادة إنشاء القنوات تُفعّل القناة الجديدة وتحذف القديمة.
                com.shopmanager.app.data.notifications.NotificationHelper.ensureChannels(context)
                checkSystemNotificationsAllowed()
                soundDialogKind = null
            }
        )
    }

    if (showCurrencyDialog) {
        CurrencyPickerDialog(
            current = currency,
            onDismiss = { showCurrencyDialog = false },
            onSelect = { selected ->
                currency = selected
                settings.currencySymbol = selected
                AppSettingsState.setCurrency(selected)
                showCurrencyDialog = false
            }
        )
    }

    if (showExportShareChoice && debtsViewModel != null && materialsViewModel != null) {
        ExportShareChoiceDialog(
            debtsViewModel = debtsViewModel,
            materialsViewModel = materialsViewModel,
            currency = currency,
            brandColor = brandColor,
            onDismiss = { showExportShareChoice = false }
        )
    }

    pendingUpdate?.let { manifest ->
        UpdateAvailableSheet(
            manifest = manifest,
            currentVersionName = appVersion.name,
            isDownloading = isDownloadingUpdate,
            percent = downloadPercent,
            onStartDownload = { startDownload(manifest) },
            onDismiss = { pendingUpdate = null }
        )
    }

    if (needsInstallPermission) {
        InstallPermissionSheet(downloadedApk = downloadedApk, onDismiss = { needsInstallPermission = false })
    }

    // ======================================================================
    // أوراق الاختيار السفلية (بدل سرد كل الخيارات داخل الصفحة)
    // ======================================================================
    if (showThemeSheet) {
        ThemeModeSheet(
            current = themeMode,
            onPick = {
                themeMode = it
                settings.themeMode = it
                onThemeChanged(it)
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showPerformanceSheet) {
        PerformanceModeSheet(
            current = performanceMode,
            recheckTick = recheckTick,
            onPick = {
                performanceMode = it
                settings.performanceMode = it
                onPerformancePreferenceChanged(it)
            },
            onRecheck = {
                onRecheckDevicePerformance()
                recheckTick++
            },
            onDismiss = { showPerformanceSheet = false }
        )
    }
}
