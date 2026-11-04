package com.shopmanager.app.ui.materials

import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.data.performance.tierAnimateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialsWeightSummary
import com.shopmanager.app.data.materials.MaterialsPriceSummary
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.materials.weightSummary
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceBreakdown
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.theme.LocalSemanticColors
import com.shopmanager.app.ui.common.AppCard
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon

internal fun buildMaterialsShareText(materials: List<Material>, prices: Map<String, Double>): String {
    val currency = AppSettingsState.currencySymbol
    val sb = StringBuilder("📦 قائمة المواد والأسعار\n\n")
    materials.sortedBy { it.name }.forEach { m ->
        sb.append("• ${m.name}: ${m.quantityLabel()}")
        // سعر الصف = سعر الكيلو × الوزن (لا سعر الكيلو الخام).
        m.linePrice(prices)?.let { sb.append(" — ${Formatters.number(it)} $currency") }
        sb.append("\n")
    }
    sb.append("\nالإجمالي: ${materials.size} مادة")
    val w = materials.weightSummary()
    if (w.hasAnything) {
        val parts = buildList {
            if (w.kilos > 0.0) add("${Formatters.number(w.kilos)} كيلو")
            if (w.okes > 0.0) add("${Formatters.number(w.okes)} لوقية")
            if (w.pieces > 0.0) add("${Formatters.number(w.pieces)} بالعدد")
        }
        sb.append("\nالأوزان: ${parts.joinToString(" + ")}")
    }
    val pb = materials.priceBreakdown(prices)
    val ps = pb.all
    if (ps.total > 0.0) {
        sb.append("\n⭐ المواد الهامة: ${Formatters.number(pb.important.total)} $currency")
        sb.append("\nبقية المواد: ${Formatters.number(pb.regular.total)} $currency")
        sb.append("\nإجمالي السعر: ${Formatters.number(ps.total)} $currency")
        if (ps.unpriced > 0) sb.append(" (${ps.unpriced} مادة بلا سعر غير محسوبة)")
    }
    return sb.toString()
}

/**
 * ملخّص القائمة المضغوط (بطاقة واحدة بسطر واحد بدل بطاقة طويلة):
 *  - خلية الأوزان: الكيلو / اللوقية / بالعدد (لا يظهر منها إلا ما فيه قيمة)، بلا أي
 *    تحويل بين العائلتين في العرض.
 *  - خلية إجمالي السعر: مجموع (سعر الكيلو × الوزن) لكل مادة مسعّرة. وإن وُجدت مواد بلا
 *    سعر يُذكر عددها تحت الرقم حتى لا يُظنّ أنها داخلة في المجموع.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MaterialsSummaryCard(
    weights: MaterialsWeightSummary,
    prices: MaterialsPriceSummary,
    overallWeights: MaterialsWeightSummary?,
    overallPrices: MaterialsPriceSummary?,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val currency = AppSettingsState.currencySymbol
    val weightCells = remember(weights) {
        buildList {
            if (weights.kilos > 0.0) add(weights.kilos to "كيلو")
            if (weights.okes > 0.0) add(weights.okes to "لوقية")
            if (weights.pieces > 0.0) add(weights.pieces to "بالعدد")
        }
    }
    val showWeights = weightCells.isNotEmpty()
    val showPrice = prices.total > 0.0
    if (!showWeights && !showPrice) return
    val overallCells = remember(overallWeights, overallPrices) {
        if (overallWeights == null || overallPrices == null) emptyList<String>()
        else buildList {
            if (overallPrices.total > 0.0) add("${Formatters.number(overallPrices.total)} $currency")
            if (overallWeights.kilos > 0.0) add("${Formatters.number(overallWeights.kilos)} كيلو")
            if (overallWeights.okes > 0.0) add("${Formatters.number(overallWeights.okes)} لوقية")
            if (overallWeights.pieces > 0.0) add("${Formatters.number(overallWeights.pieces)} بالعدد")
        }
    }

    AppCard(modifier = modifier.tierAnimateContentSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppScreenPadding, vertical = AppSpaceMd)) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                if (showWeights) {
                    Column(Modifier.weight(1.25f)) {
                        Text("الأوزان المطلوبة", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(AppSpaceXs))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpaceMd),
                            verticalArrangement = Arrangement.spacedBy(AppSpace2)
                        ) {
                            weightCells.forEach { (value, label) ->
                                Row(verticalAlignment = Alignment.Bottom) {
                                    AnimatedCounterText(
                                        targetValue = value,
                                        format = { Formatters.number(it) },
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        animate = animate
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(bottom = AppSpace2)
                                    )
                                }
                            }
                        }
                    }
                }
                if (showWeights && showPrice) {
                    Box(
                        Modifier
                            .padding(horizontal = AppSpaceMd)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(cs.outlineVariant)
                    )
                }
                if (showPrice) {
                    Column(Modifier.weight(1f)) {
                        Text("إجمالي السعر", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(AppSpaceXs))
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedCounterText(
                                targetValue = prices.total,
                                format = { Formatters.number(it) },
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.SemiBold,
                                animate = animate
                            )
                            Spacer(Modifier.width(AppSpaceXs))
                            Text(
                                currency,
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = AppSpace2)
                            )
                        }
                        if (prices.unpriced > 0) {
                            Text(
                                "${prices.unpriced} بلا سعر",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (overallCells.isNotEmpty()) {
                Spacer(Modifier.height(AppSpace10))
                Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
                Spacer(Modifier.height(AppSpaceSm))
                Text("الإجمالي الكلي", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(AppSpace2))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSectionGap),
                    verticalArrangement = Arrangement.spacedBy(AppSpace2)
                ) {
                    overallCells.forEach {
                        Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MaterialsTabBar(
    selected: Int,
    importantCount: Int,
    regularCount: Int,
    allCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val gold = LocalSemanticColors.current.warning
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(AppSpaceXs),
        horizontalArrangement = Arrangement.spacedBy(AppSpaceXs)
    ) {
        MaterialsTabButton(
            label = "هامة ($importantCount)",
            selected = selected == 0,
            leadingStar = true,
            starTint = gold,
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f)
        )
        MaterialsTabButton(
            label = "الناقصة ($regularCount)",
            selected = selected == 1,
            leadingStar = false,
            starTint = gold,
            onClick = { onSelect(1) },
            modifier = Modifier.weight(1f)
        )
        MaterialsTabButton(
            label = "الكل ($allCount)",
            selected = selected == 2,
            leadingStar = false,
            starTint = gold,
            onClick = { onSelect(2) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun MaterialsTabButton(
    label: String,
    selected: Boolean,
    leadingStar: Boolean,
    starTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) cs.onSurface else androidx.compose.ui.graphics.Color.Transparent,
        label = "tabBg"
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) cs.surface else cs.onSurfaceVariant,
        label = "tabFg"
    )
    Row(
        modifier
            .height(AppMinTouchTarget)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingStar) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (selected) starTint else starTint.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(AppSpace6))
        }
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
