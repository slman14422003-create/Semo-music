package com.shopmanager.app.ui.materials

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.theme.LocalSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
private fun TierVisibility(visible: Boolean, content: @Composable () -> Unit) {
    if (MotionSpecs.reduceMotion()) {
        if (visible) content()
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = MotionSpecs.expandEnter(),
            exit = MotionSpecs.expandExit()
        ) { content() }
    }
}

@Composable
internal fun PriceChangeBadge(
    history: List<PriceChange>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = history.firstOrNull()
    val recent = remember(history) { PriceHistoryStore.isRecent(latest, System.currentTimeMillis()) }
    TierVisibility(visible = latest != null) {
        val semantic = LocalSemanticColors.current
        val delta = latest?.delta
        val up = (delta ?: 0.0) > 0.0
        val accent = when {
            !recent -> MaterialTheme.colorScheme.onSurfaceVariant
            up -> semantic.danger
            else -> semantic.success
        }
        Box(
            modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = AppMinTouchTarget),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = AppSpaceSm, vertical = AppSpaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        !recent -> Icons.Default.History
                        up -> Icons.Default.ArrowUpward
                        else -> Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(AppSpaceMd)
                )
                Spacer(Modifier.width(AppSpaceXs))
                Text(
                    text = when {
                        !recent || delta == null -> "سجل الأسعار"
                        up -> "ارتفع ${Formatters.number(abs(delta))}"
                        else -> "انخفض ${Formatters.number(abs(delta))}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun PriceHistoryDialog(
    name: String,
    history: List<PriceChange>,
    onDismiss: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val currency = AppSettingsState.currencySymbol
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy، h:mm a", Locale("ar")) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpaceXs)) {
                if (history.isEmpty()) {
                    Text(
                        "لا يوجد سجل تغييرات لهذه المادة بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                history.forEachIndexed { index, change ->
                    val delta = change.delta
                    val color = when {
                        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        delta > 0 -> semantic.danger
                        else -> semantic.success
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppMinTouchTarget)
                            .padding(vertical = AppSpaceXs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                delta == null -> Icons.Default.History
                                delta > 0 -> Icons.Default.ArrowUpward
                                else -> Icons.Default.ArrowDownward
                            },
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(AppSpaceLg)
                        )
                        Spacer(Modifier.width(AppSpaceMd))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (change.oldPrice == null) {
                                    "${Formatters.number(change.newPrice)} $currency"
                                } else {
                                    "${Formatters.number(change.oldPrice)} ← ${Formatters.number(change.newPrice)} $currency"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                dateFormat.format(Date(change.at)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (delta != null) {
                            Text(
                                (if (delta > 0) "+" else "-") + Formatters.number(abs(delta)),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                    }
                    if (index < history.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)) { Text("إغلاق") }
        }
    )
}
