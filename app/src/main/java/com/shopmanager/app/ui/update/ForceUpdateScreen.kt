package com.shopmanager.app.ui.update

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.AppVersion
import com.shopmanager.app.data.updates.UpdateDownloadPhase
import com.shopmanager.app.data.updates.UpdateDownloadService
import com.shopmanager.app.data.updates.UpdateDownloadState
import com.shopmanager.app.data.updates.UpdateManifest
import com.shopmanager.app.ui.common.AppPillButton
import kotlinx.coroutines.delay
import java.io.File
import com.shopmanager.app.data.performance.MotionSpecs
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.graphicsLayer
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import androidx.compose.animation.core.animateFloat

/**
 * FEATURE ADDED ("التحديثات اجبارية ... لا يمكن تجاهلها"): a full-screen,
 * non-dismissible gate shown once at app start whenever [UpdateChecker]
 * reports a newer versionCode is available — see MainActivity, which
 * inserts this between the splash and the real app content (before
 * LockScreen/ShopManagerApp) instead of the old flow where an update was
 * only ever a dismissible dialog reachable from Settings. That optional
 * "تحقق من التحديثات" flow in Settings is UNCHANGED and still there for a
 * manual check — this is the automatic, blocking one that runs on every
 * cold start.
 *
 * Deliberately fails OPEN, not closed: this screen only ever appears when
 * the check already came back with a confirmed newer versionCode (see
 * MainActivity's UpdatePhase.REQUIRED). If the check itself fails — no
 * internet, the manifest host unreachable, GitHub/the proxy down — the
 * person is never trapped here; they just reach the app normally and can
 * still update manually later from Settings. A hard requirement to
 * install before using the app makes sense; a hard requirement to have a
 * working internet connection just to *open* the app at all does not.
 *
 * The system/gesture back action is swallowed entirely ([BackHandler] with
 * an empty body, `enabled = true`) so there is no way to skip past this
 * screen back into the app — tapping the device back button or swiping
 * back just does nothing, same as it would on a real system update-lock
 * screen.
 */
@Composable
fun ForceUpdateScreen(
    manifest: UpdateManifest,
    currentVersion: AppVersion,
    onUpdateInstalled: () -> Unit = {}
) {
    // Swallow every back action — gesture and button alike — for as long
    // as this screen is on top. Nothing else in the app can render behind
    // it (MainActivity shows this INSTEAD OF LockScreen/ShopManagerApp),
    // so there is nothing meaningful to pop back to anyway.
    BackHandler(enabled = true) {}

    val context = LocalContext.current

    // BUG FIXED / FEATURE ADDED ("لازم التحميل ينحفظ"، "والتحميل يكون في
    // الخلفية"): the download itself now runs inside [UpdateDownloadService]
    // (a real foreground service) instead of a coroutine scoped to this
    // Composable — see [UpdateDownloadState] for the full reasoning. This
    // screen just renders whatever the shared state currently says, so it
    // survives this screen recomposing, the app backgrounding, or the
    // person having started the same download from the optional dialog in
    // Settings before this mandatory screen ever appeared.
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val downloadPhase by UpdateDownloadState.phase.collectAsStateWithLifecycle()
    val isDownloading = downloadPhase is UpdateDownloadPhase.InProgress
    val downloadPercent = (downloadPhase as? UpdateDownloadPhase.InProgress)?.percent ?: 0

    fun tryInstall(file: File) {
        if (ApkDownloader.canInstallPackages(context)) {
            ApkDownloader.install(context, file)
            UpdateDownloadState.reset()
            onUpdateInstalled()
        } else {
            needsInstallPermission = true
        }
    }

    LaunchedEffect(Unit) {
        UpdateDownloadState.phase.collect { phase ->
            when (phase) {
                is UpdateDownloadPhase.Done -> {
                    errorMessage = null
                    downloadedFile = phase.file
                    tryInstall(phase.file)
                }
                is UpdateDownloadPhase.Error -> errorMessage = phase.message
                else -> Unit
            }
        }
    }

    fun startDownload() {
        errorMessage = null
        UpdateDownloadService.start(context, manifest)
    }

    // Gentle up/down pulse on the update glyph — same motion language as
    // the optional-update dialog in SettingsScreen, so the mandatory and
    // optional update flows still read as one visual family.
    // حركة لانهائية في مرحلة الرسم فقط (بلا while/delay ولا إعادة تركيب)، وتُعطَّل في الوضع الاقتصادي.
    val arrowShift: State<Float> =
        if (LocalPerformanceTier.current == PerformanceTier.LOW) {
            remember { mutableStateOf(0f) }
        } else {
            rememberInfiniteTransition(label = "forceUpdateArrow").animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    tween(MotionSpecs.arrowPulseMs, easing = MotionSpecs.claudeEasing),
                    RepeatMode.Reverse
                ),
                label = "forceUpdateArrowShift"
            )
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { translationY = arrowShift.value.dp.toPx() }
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "تحديث ضروري",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "هذا التحديث ضروري لأمان التطبيق وسلاسة عمله، ولا يمكن تجاهله أو المتابعة قبل تثبيته.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "الإصدار الجديد: ${manifest.versionName}   •   إصدارك الحالي: ${currentVersion.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Only shown for a short, plain-text note — the same "raw
            // GitHub markdown isn't a real changelog" concern the optional
            // dialog in SettingsScreen already documents, so this never
            // dumps unrendered Markdown onto the screen.
            if (manifest.notes.isNotBlank() && !manifest.notes.trimStart().startsWith("#") && manifest.notes.length < 240) {
                Spacer(Modifier.height(8.dp))
                Text(
                    manifest.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(visible = isDownloading, enter = fadeIn(MotionSpecs.contentTween()), exit = fadeOut(MotionSpecs.contentTween())) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("$downloadPercent%", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(20.dp))
                }
            }

            errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (needsInstallPermission) {
                Text(
                    "لإتمام التثبيت، فعّل \"السماح من هذا المصدر\" لهذا التطبيق ثم عد إلى هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                AppPillButton(
                    label = "فتح الإعدادات",
                    onClick = { context.startActivity(ApkDownloader.unknownSourcesSettingsIntent(context)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                AppPillButton(
                    label = "حاول التثبيت مرة أخرى",
                    tonal = true,
                    onClick = {
                        needsInstallPermission = false
                        downloadedFile?.let { file -> tryInstall(file) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AppPillButton(
                    label = if (isDownloading) "جاري التنزيل..." else "تحديث الآن",
                    enabled = !isDownloading,
                    onClick = { startDownload() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "  لا يمكن المتابعة إلى التطبيق قبل إتمام التحديث",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
