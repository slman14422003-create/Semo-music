package com.shopmanager.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.UpdateManifest
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppInfoRow
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppSheet
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.ui.common.ShareFormatDialog
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExportShareChoiceDialog(
    debtsViewModel: DebtsViewModel,
    materialsViewModel: MaterialsViewModel,
    currency: String,
    brandColor: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ShareFormatDialog(
        onDismiss = { onDismiss() },
        onPickImage = {
            val debtsState = debtsViewModel.uiState.value
            val materialsState = materialsViewModel.uiState.value
            scope.launch {
                val uri = withContext(Dispatchers.Default) {
                    WholeAppReportImage.generate(
                        context = context,
                        persons = debtsState.persons,
                        totalDebt = debtsState.totalAmount,
                        materials = materialsState.materials,
                        prices = materialsState.prices,
                        currency = currency,
                        brandColor = brandColor
                    )
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
            }
        },
        onPickText = {
            val text = buildBackupText(
                debtsState = debtsViewModel.uiState.value,
                materialsState = materialsViewModel.uiState.value,
                currency = currency
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - إدارة المحل")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
        }
    )

}

@Composable
internal fun UpdateAvailableSheet(
    manifest: UpdateManifest,
    currentVersionName: String,
    isDownloading: Boolean,
    percent: Int,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(
        title = "تحديث جديد متاح",
        onDismiss = { onDismiss() },
        dismissible = !isDownloading
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
            AppInfoRow("الإصدار الجديد", manifest.versionName, groupedRowShape(0, 1))
            AppInfoRow("إصدارك الحالي", currentVersionName, groupedRowShape(1, 1))
        }
        if (isDownloading) {
            AppCard {
                Column(Modifier.padding(AppSpaceLg)) {
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
                    Spacer(Modifier.height(AppSpaceMd))
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppSpaceSm)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
        AppPillButton(
            label = if (isDownloading) "جاري التنزيل..." else "تحميل وتثبيت",
            enabled = !isDownloading,
            onClick = { onStartDownload() },
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

@Composable
internal fun InstallPermissionSheet(
    downloadedApk: java.io.File?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AppSheet(title = "يلزم إذن التثبيت", onDismiss = { onDismiss() }) { _ ->
        Text(
            "لتثبيت التحديث من داخل التطبيق، فعّل \"السماح من هذا المصدر\" لهذا التطبيق ثم عد وحاول مجدداً.",
            modifier = Modifier.padding(horizontal = AppSpaceXs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppPillButton(
            label = "فتح الإعدادات",
            onClick = {
                onDismiss()
                context.startActivity(ApkDownloader.unknownSourcesSettingsIntent(context))
            },
            modifier = Modifier.fillMaxWidth()
        )
        AppPillButton(
            label = "حاول التثبيت الآن",
            tonal = true,
            onClick = {
                onDismiss()
                downloadedApk?.let { ApkDownloader.install(context, it) }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

}
