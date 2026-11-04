package com.shopmanager.app.ui.materials

import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceOf
import androidx.compose.ui.text.input.KeyboardType
import com.shopmanager.app.ui.common.rememberSmartSearch
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import androidx.compose.ui.graphics.Shape
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon

/**
 * REDESIGN ("لوحة الأسعار... تصميم كامل ومحسّن"): rebuilt from a bare
 * name+field list into an actual priced overview:
 * - A summary card up top totals how many catalog items already have a
 *   price set out of the total, plus the running sum of every priced item
 *   (edited, unsaved values included) — the same "total at a glance" idea
 *   the shortage tab already has, brought over to this tab too.
 * - A search field to filter the catalog by name, matching the pattern
 *   already used on the المواد tab, since a real catalog can run long.
 * - Each row now carries a small colored tag-icon avatar (the same avatar
 *   pattern MaterialRow uses) instead of bare text, a currency suffix
 *   directly in the price field, and a "priced"/"unpriced" visual state so
 *   it's obvious at a glance which items still need a price.
 * - The "حفظ الأسعار" action itself now lives beside the floating nav pill
 *   at the bottom of the screen (see MaterialsScreen's `editedPrices`/
 *   `savePricesRequested` and FloatingBottomNav's `secondaryAction`,
 *   wired up in MainActivity) instead of a full-width button drawn here —
 *   it reads as one attached unit with the pill, the same way "مادة
 *   جديدة" already does, rather than a separate button with its own
 *   reserved strip of empty space below the list.
 */
@Composable
internal fun PricesList(
    catalogItems: List<MaterialCatalogItem>,
    materials: List<Material>,
    prices: Map<String, Double>,
    history: Map<String, List<PriceChange>>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    search: String
) {
    if (catalogItems.isEmpty()) {
        EmptyState(icon = Icons.Default.Inventory2, text = "أضف مواد للقائمة الثابتة أولاً لتسعيرها")
        return
    }

    val currency = AppSettingsState.currencySymbol
    val filtered = rememberSmartSearch(catalogItems, search) { it.name }

    // صفوف النواقص مجمّعة باسم المادة (مقصوص) لربطها بعنصر الكتالوج نفسه.
    val shortageByName = remember(materials) { materials.groupBy { it.name.trim() } }

    // إعادة ترتيب: المواد الموجودة حالياً في قائمة النواقص أولاً (هي التي تحتاج سعراً الآن
    // ويظهر لها حساب الإجمالي)، ثم بقية الكتالوج بترتيبها الأصلي.
    val inShortage = remember(filtered, shortageByName) { filtered.filter { shortageByName.containsKey(it.name.trim()) } }
    val others = remember(filtered, shortageByName) { filtered.filter { !shortageByName.containsKey(it.name.trim()) } }

    var historyTarget by remember { mutableStateOf<String?>(null) }
    historyTarget?.let { target ->
        PriceHistoryDialog(
            name = target,
            history = history[target.trim()].orEmpty(),
            onDismiss = { historyTarget = null }
        )
    }

    val bottomClearance = LocalFloatingBottomNavHeight.current + 32.dp
    Column(Modifier.fillMaxSize()) {
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Default.SearchOff, text = "لا توجد نتائج")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(AppGroupGap)
            ) {
                item(key = "priceHint") {
                    AppFootnote(
                        "السعر المكتوب هو سعر الكيلو، ويُضرب تلقائياً بوزن المادة في قائمة النواقص (نص كيلو، ربع كيلو، لوقية…)",
                        modifier = Modifier.padding(bottom = AppSpaceSm)
                    )
                }
                if (inShortage.isNotEmpty()) {
                    item(key = "titleShortage") {
                        AppSectionTitle(text = "في قائمة النواقص (${inShortage.size})", modifier = Modifier.padding(bottom = AppSpace2))
                    }
                    itemsIndexed(inShortage, key = { _, c -> c.id }, contentType = { _, _ -> "priceRow" }) { rowIndex, item ->
                        val effective = edited[item.name]?.toPriceOrNull() ?: prices.priceOf(item.name)
                        PriceRow(
                            shape = groupedRowShape(rowIndex, inShortage.lastIndex),
                            item = item,
                            currency = currency,
                            // قيمة الحقل نص قابل للتحليل مجدداً (بلا ".0" ولا 1.1E7).
                            value = edited[item.name] ?: prices[item.name]?.toEditText() ?: "",
                            effectivePrice = effective,
                            history = history[item.name.trim()].orEmpty(),
                            onShowHistory = { historyTarget = item.name },
                            shortageRows = shortageByName[item.name.trim()].orEmpty(),
                            onValueChange = { edited[item.name] = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '٫' } }
                        )
                    }
                }
                if (others.isNotEmpty()) {
                    item(key = "titleOthers") {
                        AppSectionTitle(
                            text = if (inShortage.isEmpty()) "القائمة الثابتة (${others.size})" else "بقية القائمة (${others.size})",
                            modifier = Modifier.padding(top = if (inShortage.isEmpty()) 0.dp else 10.dp, bottom = AppSpace2)
                        )
                    }
                    itemsIndexed(others, key = { _, c -> c.id }, contentType = { _, _ -> "priceRow" }) { rowIndex, item ->
                        val effective = edited[item.name]?.toPriceOrNull() ?: prices.priceOf(item.name)
                        PriceRow(
                            shape = groupedRowShape(rowIndex, others.lastIndex),
                            item = item,
                            currency = currency,
                            value = edited[item.name] ?: prices[item.name]?.toEditText() ?: "",
                            effectivePrice = effective,
                            history = history[item.name.trim()].orEmpty(),
                            onShowHistory = { historyTarget = item.name },
                            shortageRows = emptyList(),
                            onValueChange = { edited[item.name] = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '٫' } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * صف كتالوج في تبويب الأسعار: أيقونة، الاسم، (إن كانت المادة في النواقص) سطر الحساب
 * "كميتها = سعرها"، وحقل سعر الكيلو مضغوط الارتفاع.
 */
@Composable
internal fun PriceRow(
    shape: Shape,
    item: MaterialCatalogItem,
    currency: String,
    value: String,
    effectivePrice: Double?,
    history: List<PriceChange>,
    onShowHistory: () -> Unit,
    shortageRows: List<Material>,
    onValueChange: (String) -> Unit
) {
    val avatarColor = remember(item.name) { avatarColorFor(item.name) }
    val hasPrice = effectivePrice != null
    val cs = MaterialTheme.colorScheme
    AppRowSurface(shape = shape) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppSpaceMd, vertical = AppSpace6),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconCircle(
                color = if (hasPrice) avatarColor else avatarColor.copy(alpha = 0.35f),
                size = 36.dp
            ) {
                Icon(
                    Icons.Default.Sell,
                    contentDescription = null,
                    tint = if (hasPrice) Color.White else avatarColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(AppSpace10))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (history.isNotEmpty()) {
                    PriceChangeBadge(history = history, onClick = onShowHistory)
                }
                if (shortageRows.isNotEmpty()) {
                    val qty = shortageRows.joinToString(" + ") { it.quantityLabel() }
                    val total = if (effectivePrice == null) null
                    else shortageRows.sumOf { it.linePrice(effectivePrice) ?: 0.0 }
                    Text(
                        if (total == null) "$qty — بدون سعر" else "$qty = ${Formatters.number(total)} $currency",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(AppSpaceSm))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(138.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(cs.onSurface),
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceContainerHighest)
                            .padding(horizontal = AppSpaceMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text("0", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant.copy(alpha = 0.9f))
                            }
                            inner()
                        }
                        Spacer(Modifier.width(AppSpaceXs))
                        Text(currency, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

/** تنظيف رقم السعر المكتوب: أرقام عربية→لاتينية، فاصلة عشرية عربية→نقطة، حذف فواصل الآلاف والمسافات. */
internal fun String.toPriceOrNull(): Double? {
    val cleaned = buildString {
        for (c in this@toPriceOrNull) {
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠'))
                in '۰'..'۹' -> append('0' + (c - '۰'))
                '٫' -> append('.')
                ',', '٬', ' ' -> Unit
                else -> append(c)
            }
        }
    }
    val v = cleaned.toDoubleOrNull() ?: return null
    return if (v.isFinite() && v >= 0.0) v else null
}

/** نص قابل للتحرير لسعر محفوظ: بلا ".0" ولا صيغة علمية (1.1E7). */
internal fun Double.toEditText(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else java.math.BigDecimal(this).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
