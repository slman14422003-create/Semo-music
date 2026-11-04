package com.shopmanager.app.ui.dashboard

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.defaultMinSize
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.debts.OverdueRule
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.avatarColorFor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

internal const val DAY_MS = 86_400_000L

// ───────────────────────── data helpers (pure, run off the main thread) ─────────────────────────

internal data class OverdueEntry(
    val personId: String,
    val name: String,
    val phone: String,
    val amount: Double,
    val days: Int
)

internal data class DaySummary(
    val addedCount: Int = 0,
    val addedAmount: Double = 0.0,
    val paidCount: Int = 0,
    val paidAmount: Double = 0.0
) {
    val isEmpty: Boolean get() = addedCount == 0 && paidCount == 0
}

internal data class WeekTrend(
    val added: List<Double> = List(7) { 0.0 },
    val paid: List<Double> = List(7) { 0.0 }
) {
    val isEmpty: Boolean get() = added.all { it <= 0.0 } && paid.all { it <= 0.0 }
}

internal data class SearchHit(
    val kind: Int,
    val id: String,
    val title: String,
    val subtitle: String
) {
    companion object {
        const val PERSON = 0
        const val MATERIAL = 1
        const val NOTE = 2
    }
}

internal fun computeOverdue(
    persons: List<Person>,
    debts: List<Debt>,
    overdueDays: Int,
    now: Long
): List<OverdueEntry> =
    OverdueRule.compute(persons, debts, overdueDays, now).map {
        OverdueEntry(personId = it.personId, name = it.name, phone = it.phone, amount = it.amount, days = it.days)
    }

private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun summarizeToday(entries: List<DebtActivityLog.Entry>, now: Long): DaySummary {
    val start = startOfDay(now)
    var addedCount = 0
    var addedAmount = 0.0
    var paidCount = 0
    var paidAmount = 0.0
    for (e in entries) {
        if (e.time < start) continue
        when (e.kind) {
            DebtActivityLog.Kind.ADDED, DebtActivityLog.Kind.NEW_CUSTOMER -> {
                addedCount++
                addedAmount += abs(e.amount)
            }
            DebtActivityLog.Kind.PAID, DebtActivityLog.Kind.PAID_ALL -> {
                paidCount++
                paidAmount += abs(e.amount)
            }
            else -> Unit
        }
    }
    return DaySummary(addedCount, addedAmount, paidCount, paidAmount)
}

/** Daily totals for the last 7 days (oldest first, today last). */
internal fun computeWeekTrend(entries: List<DebtActivityLog.Entry>, now: Long): WeekTrend {
    val todayStart = startOfDay(now)
    val added = DoubleArray(7)
    val paid = DoubleArray(7)
    for (e in entries) {
        val daysAgo = if (e.time >= todayStart) 0 else ((todayStart - e.time - 1) / DAY_MS).toInt() + 1
        if (daysAgo > 6) continue
        val index = 6 - daysAgo
        when (e.kind) {
            DebtActivityLog.Kind.ADDED, DebtActivityLog.Kind.NEW_CUSTOMER -> added[index] += abs(e.amount)
            DebtActivityLog.Kind.PAID, DebtActivityLog.Kind.PAID_ALL -> paid[index] += abs(e.amount)
            else -> Unit
        }
    }
    return WeekTrend(added.toList(), paid.toList())
}

// ───────────────────────── shared card pieces ─────────────────────────

/** Compact status/alert card in the same visual language as the smart suggestion card. */
@Composable
internal fun HomeAlertCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppGroupLargeRadius)
    AppCard(
        modifier = modifier
            .padding(horizontal = AppScreenPadding)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppScreenPadding, end = AppSpaceSm, top = AppSpaceMd, bottom = AppSpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AppSpaceMd))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (actionLabel != null) {
                    Spacer(Modifier.height(AppSpaceXs))
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "إخفاء",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ───────────────────────── suggestion stack ─────────────────────────

/** Swipeable stack of every strong learned suggestion, with page dots. */
@Composable
internal fun SuggestionPager(
    suggestions: List<HomeSuggestion>,
    accent: Color,
    onAccept: (HomeSuggestion) -> Unit,
    onDismiss: (HomeSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { suggestions.size }
    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            key = { page -> suggestions.getOrNull(page)?.kind?.key ?: page.toString() },
            verticalAlignment = Alignment.Top
        ) { page ->
            val s = suggestions.getOrNull(page)
            if (s != null) {
                HomeSuggestionCard(
                    suggestion = s,
                    accent = accent,
                    onAccept = { onAccept(s) },
                    onDismiss = { onDismiss(s) }
                )
            }
        }
        if (suggestions.size > 1) {
            Spacer(Modifier.height(AppSpaceSm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(suggestions.size) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

// ───────────────────────── overdue debts ─────────────────────────

@Composable
internal fun OverdueDebtsCard(
    entries: List<OverdueEntry>,
    accent: Color,
    nf: NumberFormat,
    currency: String,
    onOpenPerson: (String) -> Unit,
    onRemind: (OverdueEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = "ديون متأخرة",
        color = accent,
        icon = Icons.Default.Warning
    ) {
        val shown = entries.take(3)
        shown.forEachIndexed { index, e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPerson(e.personId) }
                    .defaultMinSize(minHeight = AppMinTouchTarget)
                    .padding(vertical = AppSpace10),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(avatarColorFor(e.name)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        e.name.trim().take(1),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(AppSpaceMd))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "متأخر منذ ${nf.format(e.days)} يوم • ${nf.format(e.amount)} $currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (e.phone.isNotBlank()) {
                    Text(
                        "تذكير",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onRemind(e) }
                            .defaultMinSize(minWidth = AppMinTouchTarget, minHeight = AppMinTouchTarget)
                            .wrapContentSize(Alignment.Center)
                            .padding(horizontal = AppSpaceMd)
                    )
                }
            }
            if (index < shown.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        if (entries.size > shown.size) {
            Text(
                "و ${nf.format(entries.size - shown.size)} آخرين",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AppSpace10)
            )
        }
    }
}

// ───────────────────────── today summary ─────────────────────────

@Composable
internal fun TodaySummaryCard(
    summary: DaySummary,
    nf: NumberFormat,
    currency: String,
    successColor: Color,
    addedColor: Color,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "ملخص اليوم", icon = Icons.Default.Today, modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = AppSpace10)) {
            SummaryStat(
                modifier = Modifier.weight(1f),
                label = "مدفوعات مستلمة",
                value = "${nf.format(summary.paidAmount)} $currency",
                count = summary.paidCount,
                nf = nf,
                color = successColor
            )
            Spacer(Modifier.width(AppSpaceMd))
            SummaryStat(
                modifier = Modifier.weight(1f),
                label = "ديون مضافة",
                value = "${nf.format(summary.addedAmount)} $currency",
                count = summary.addedCount,
                nf = nf,
                color = addedColor
            )
        }
    }
}

@Composable
private fun SummaryStat(
    modifier: Modifier,
    label: String,
    value: String,
    count: Int,
    nf: NumberFormat,
    color: Color
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpace2))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${nf.format(count)} عملية",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ───────────────────────── notes & reminders ─────────────────────────

internal data class NoteRow(val id: String, val title: String, val whenLabel: String?)

@Composable
internal fun NotesRemindersCard(
    rows: List<NoteRow>,
    accent: Color,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "ملاحظات وتذكيرات", color = accent, icon = Icons.Default.StickyNote2, modifier = modifier) {
        rows.forEachIndexed { index, row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotes)
                    .defaultMinSize(minHeight = AppMinTouchTarget)
                    .padding(vertical = AppSpace10)
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.whenLabel != null) {
                    Text(
                        row.whenLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = accent
                    )
                }
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ───────────────────────── weekly trend ─────────────────────────

@Composable
internal fun WeekTrendCard(
    trend: WeekTrend,
    addedColor: Color,
    paidColor: Color,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "حركة الأسبوع", icon = Icons.Default.TrendingUp, modifier = modifier) {
        val maxValue = (trend.added + trend.paid).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        Box(Modifier.fillMaxWidth().padding(vertical = AppSectionGap)) {
            Sparkline(trend.added, addedColor, maxValue, Modifier.fillMaxWidth().height(64.dp))
            Sparkline(trend.paid, paidColor, maxValue, Modifier.fillMaxWidth().height(64.dp))
        }
        Row(Modifier.padding(bottom = AppSpaceMd), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(addedColor)
            Spacer(Modifier.width(AppSpace6))
            Text("ديون مضافة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AppSpaceLg))
            LegendDot(paidColor)
            Spacer(Modifier.width(AppSpace6))
            Text("مدفوعات", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun Sparkline(values: List<Double>, color: Color, maxValue: Double, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2 || maxValue <= 0.0) return@Canvas
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = size.height - (v / maxValue).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ───────────────────────── home search ─────────────────────────

@Composable
internal fun HomeSearchResults(
    hits: List<SearchHit>,
    onHit: (SearchHit) -> Unit
) {
    AppCard(modifier = Modifier.padding(horizontal = AppScreenPadding)) {
        Column(Modifier.padding(horizontal = AppSpaceLg, vertical = AppSpace6)) {
            if (hits.isEmpty()) {
                Text(
                    "لا توجد نتائج",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AppSectionGap)
                )
            }
            hits.forEachIndexed { index, hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onHit(hit) }
                        .defaultMinSize(minHeight = AppMinTouchTarget)
                        .padding(vertical = AppSpace10),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (hit.kind) {
                        SearchHit.PERSON -> Icons.Default.Person
                        SearchHit.MATERIAL -> Icons.Default.Inventory2
                        else -> Icons.Default.StickyNote2
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(AppSpaceMd))
                    Column(Modifier.weight(1f)) {
                        Text(
                            hit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hit.subtitle.isNotBlank()) {
                            Text(
                                hit.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (index < hits.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
