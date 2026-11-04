package com.shopmanager.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.ui.theme.AppThemeMode

@Composable
internal fun ThemeModeSheet(
    current: AppThemeMode,
    onPick: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsSheet(title = "وضع الألوان", onDismiss = onDismiss) { close ->
        SettingsGroup {
            AppThemeMode.entries.forEach { mode ->
                selectItem(
                    icon = when (mode) {
                        AppThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                        AppThemeMode.LIGHT -> Icons.Outlined.LightMode
                        AppThemeMode.DARK -> Icons.Outlined.DarkMode
                    },
                    title = when (mode) {
                        AppThemeMode.SYSTEM -> "حسب النظام"
                        AppThemeMode.LIGHT -> "فاتح"
                        AppThemeMode.DARK -> "داكن"
                    },
                    selected = current == mode,
                    onClick = {
                        onPick(mode)
                        close()
                    }
                )
            }
        }
    }
}

@Composable
internal fun PerformanceModeSheet(
    current: PerformanceMode,
    recheckTick: Int,
    onPick: (PerformanceMode) -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SettingsSheet(title = "الأداء", onDismiss = onDismiss) { close ->
        SettingsFootnote(
            "مرتفع: أنيميشن أكثر وظلال ورموز متحركة. منخفض: إيقاف التأثيرات والاكتفاء بانتقالات بسيطة. تلقائي: يختار التطبيق وحده ما يناسب جهازك (قوي، متوسط، أو ضعيف)، وينزل بالتأثيرات تلقائياً عند توفير الطاقة أو إذا لاحظ تقطعاً في الحركة."
        )
        SettingsGroup {
            listOf(
                PerformanceMode.AUTO to "تلقائي (يضبط نفسه حسب الجهاز)",
                PerformanceMode.HIGH to "مرتفع (كل التأثيرات)",
                PerformanceMode.LOW to "منخفض (بدون تأثيرات، للأجهزة القديمة)"
            ).forEach { (mode, label) ->
                selectItem(
                    title = label,
                    selected = current == mode,
                    onClick = {
                        onPick(mode)
                            close()
                    }
                )
            }
        }
        if (current == PerformanceMode.AUTO) {
            val deviceInfo = remember(recheckTick) {
                com.shopmanager.app.data.performance.DevicePerformance.currentDeviceInfo(context)
            }
            val deviceText = "الجهاز الحالي: ${deviceInfo.totalRamMb} MB رام، ${deviceInfo.cores} أنوية" +
                (if (deviceInfo.osFlaggedLowRam) " — مصنّف من النظام كجهاز منخفض الموارد" else "") +
                (if (deviceInfo.weakGpu) " — معالج رسوميات ضعيف/قديم" else "") +
                (if (deviceInfo.weakMemoryClass) " — ذاكرة تطبيق محدودة (${deviceInfo.memoryClassMb}MB)" else "")
            SettingsGroup {
                item(
                    icon = Icons.Outlined.Refresh,
                    title = "إعادة فحص أداء الجهاز",
                    subtitle = deviceText,
                    onClick = {
                        onRecheck()
                    }
                )
            }
        }
    }
}
