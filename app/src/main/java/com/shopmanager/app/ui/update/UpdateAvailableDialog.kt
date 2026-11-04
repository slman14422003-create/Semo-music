package com.shopmanager.app.ui.update

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.AppVersion
import com.shopmanager.app.data.updates.UpdateDownloadPhase
import com.shopmanager.app.data.updates.UpdateDownloadService
import com.shopmanager.app.data.updates.UpdateDownloadState
import com.shopmanager.app.data.updates.UpdateManifest
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppInfoRow
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppSheet
import com.shopmanager.app.ui.common.groupedRowShape
import java.io.File

/**
 * مربع التحديث الاختياري (الحالة الطبيعية): "تحديث الآن" أو "لاحقاً".
 * شاشة [ForceUpdateScreen] لا تظهر إلا إذا وُضعت علامة الإجبار في إصدار GitHub.
 */
@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    currentVersion: AppVersion,
    onLater: () -> Unit
) {
    val context = LocalContext.current
    var startedHere by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val phase by UpdateDownloadState.phase.collectAsStateWithLifecycle()
    val isDownloading = phase is UpdateDownloadPhase.InProgress
    val percent = (phase as? UpdateDownloadPhase.InProgress)?.percent ?: 0

    fun tryInstall(file: File) {
        if (ApkDownloader.canInstallPackages(context)) {
            ApkDownloader.install(context, file)
            UpdateDownloadState.reset()
            onLater()
        } else {
            needsInstallPermission = true
        }
    }

    LaunchedEffect(Unit) {
        UpdateDownloadState.phase.collect { p ->
            // نتعامل فقط مع تنزيل بدأ من هذا المربع حتى لا يتكرر التثبيت مع شاشة الإعدادات.
            if (!startedHere) return@collect
            when (p) {
                is UpdateDownloadPhase.Done -> {
                    errorMessage = null
                    downloadedFile = p.file
                    tryInstall(p.file)
                }
                is UpdateDownloadPhase.Error -> errorMessage = p.message
                else -> Unit
            }
        }
    }

    AppSheet(
        title = "تحديث جديد متاح",
        onDismiss = onLater,
        dismissible = !isDownloading
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
            AppInfoRow("الإصدار الجديد", manifest.versionName, groupedRowShape(0, 1))
            AppInfoRow("إصدارك الحالي", currentVersion.name, groupedRowShape(1, 1))
        }

        val notes = manifest.notes.trim()
        if (notes.isNotBlank()) {
            AppCard {
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }

        if (isDownloading) {
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "جاري التنزيل...",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        errorMessage?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (needsInstallPermission) {
            Text(
                "لإتمام التثبيت، فعّل \"السماح من هذا المصدر\" لهذا التطبيق ثم عد إلى هنا.",
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppPillButton(
                label = "فتح الإعدادات",
                onClick = { context.startActivity(ApkDownloader.unknownSourcesSettingsIntent(context)) },
                modifier = Modifier.fillMaxWidth()
            )
            AppPillButton(
                label = "حاول مرة أخرى",
                tonal = true,
                onClick = {
                    needsInstallPermission = false
                    downloadedFile?.let { tryInstall(it) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            AppPillButton(
                label = if (isDownloading) "جاري التنزيل..." else "تحديث الآن",
                enabled = !isDownloading,
                onClick = {
                    errorMessage = null
                    startedHere = true
                    UpdateDownloadService.start(context, manifest)
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (!isDownloading) {
                AppPillButton(
                    label = "لاحقاً",
                    tonal = true,
                    onClick = close,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
