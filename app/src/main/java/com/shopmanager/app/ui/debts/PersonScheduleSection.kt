package com.shopmanager.app.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.shopmanager.app.data.debts.Installment
import com.shopmanager.app.data.debts.OverdueRule
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.performance.tierAnimateContentSize
import com.shopmanager.app.ui.common.ActionIconButton
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXl
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.theme.LocalSemanticColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private fun utcDayToLocalStart(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun localStartToUtcDay(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initialLocalDay: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = if (initialLocalDay > 0L) localStartToUtcDay(initialLocalDay) else null
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(utcDayToLocalStart(it)) } },
                modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)
            ) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)) { Text("إلغاء") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun ScheduleAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = AppMinTouchTarget, minHeight = AppMinTouchTarget)
            .padding(horizontal = AppSpaceMd),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
internal fun PersonScheduleCard(
    person: Person,
    totalOwed: Double,
    currency: String,
    onSetDueDate: (Long) -> Unit,
    onAddInstallment: (Installment) -> Unit,
    onDeleteInstallment: (Installment) -> Unit,
    onPayInstallment: (Installment) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("ar")) }
    val today = OverdueRule.startOfDay(System.currentTimeMillis())
    var showDuePicker by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var payTarget by remember { mutableStateOf<Installment?>(null) }
    var deleteTarget by remember { mutableStateOf<Installment?>(null) }

    val sorted = remember(person.installments) { person.installments.sortedWith(compareBy({ it.paid }, { it.date })) }
    val plannedTotal = remember(person.installments) { person.installments.filter { !it.paid }.sumOf { it.amount } }

    Column(modifier.tierAnimateContentSize(), verticalArrangement = Arrangement.spacedBy(AppSpaceSm)) {
        AppSectionTitle(
            text = "موعد السداد والأقساط",
            trailing = {
                ActionIconButton(
                    icon = Icons.Default.Add,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "إضافة قسط",
                    onClick = { showAddDialog = true }
                )
            }
        )

        AppRowSurface(
            shape = RoundedCornerShape(AppGroupLargeRadius),
            onClick = { showDuePicker = true }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppMinTouchTarget)
                    .padding(start = AppSpaceLg, end = AppSpaceSm, top = AppSpaceSm, bottom = AppSpaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppSpaceXl)
                )
                Spacer(Modifier.width(AppSpaceMd))
                Column(Modifier.weight(1f)) {
                    Text(
                        "تاريخ الاستحقاق",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (person.dueDate > 0L) {
                        val lateDays = if (totalOwed > 0 && person.dueDate < today) {
                            ((today - person.dueDate) / OverdueRule.DAY_MS).toInt().coerceAtLeast(1)
                        } else 0
                        Text(
                            dateFormat.format(Date(person.dueDate)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (lateDays > 0) {
                            Text(
                                "متأخر منذ ${nf.format(lateDays)} يوم",
                                style = MaterialTheme.typography.labelMedium,
                                color = semantic.danger
                            )
                        }
                    } else {
                        Text(
                            "غير محدد — يُستخدم عدد أيام التأخر في الإعدادات",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (person.dueDate > 0L) {
                    ActionIconButton(
                        icon = Icons.Default.Close,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = "إزالة تاريخ الاستحقاق",
                        onClick = { onSetDueDate(0L) }
                    )
                }
            }
        }

        if (sorted.isEmpty()) {
            AppFootnote("لا توجد أقساط مجدولة لهذا العميل")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpaceXs)) {
                sorted.forEachIndexed { index, item ->
                    val late = !item.paid && item.date in 1 until today
                    AppRowSurface(shape = groupedRowShape(index, sorted.lastIndex)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppMinTouchTarget)
                                .padding(start = AppSpaceLg, end = AppSpaceSm, top = AppSpaceXs, bottom = AppSpaceXs)
                                .alpha(if (item.paid) 0.6f else 1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (item.paid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (item.paid) "مسدد" else "غير مسدد",
                                tint = if (item.paid) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(AppSpaceXl)
                            )
                            Spacer(Modifier.width(AppSpaceMd))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${nf.format(item.amount)} $currency",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (late) "${dateFormat.format(Date(item.date))} • متأخر" else dateFormat.format(Date(item.date)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (late) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!item.paid) {
                                ScheduleAction("تم السداد", semantic.success, onClick = { payTarget = item })
                                ActionIconButton(
                                    icon = Icons.Default.Delete,
                                    tint = semantic.danger,
                                    contentDescription = "حذف القسط",
                                    onClick = { deleteTarget = item }
                                )
                            }
                        }
                    }
                }
            }
            if (plannedTotal > 0) {
                AppFootnote("المتبقي من الأقساط: ${nf.format(plannedTotal)} $currency")
            }
        }
    }

    if (showDuePicker) {
        DayPickerDialog(
            initialLocalDay = if (person.dueDate > 0L) person.dueDate else today,
            onDismiss = { showDuePicker = false },
            onPick = {
                onSetDueDate(it)
                showDuePicker = false
            }
        )
    }

    if (showAddDialog) {
        AddInstallmentDialog(
            defaultDate = today + 7L * OverdueRule.DAY_MS,
            suggestedAmount = totalOwed,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, date ->
                onAddInstallment(Installment(UUID.randomUUID().toString().take(8), amount, date, false))
                showAddDialog = false
            }
        )
    }

    payTarget?.let { item ->
        GlassAlertDialog(
            onDismissRequest = { payTarget = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = semantic.success) },
            title = { Text("تأكيد سداد القسط") },
            text = {
                Text("هل وفى \"${person.name}\" قسط ${nf.format(item.amount)} $currency؟ سيُسجَّل كدفعة ويُخصم من أقدم الديون.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPayInstallment(item)
                        payTarget = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)
                ) { Text("تم السداد", color = semantic.success, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { payTarget = null }, modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)) { Text("إلغاء") }
            }
        )
    }

    deleteTarget?.let { item ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف القسط") },
            text = { Text("هل تريد حذف قسط ${nf.format(item.amount)} $currency من الجدول؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteInstallment(item)
                        deleteTarget = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)
                ) { Text("حذف", color = semantic.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun AddInstallmentDialog(
    defaultDate: Long,
    suggestedAmount: Double,
    onDismiss: () -> Unit,
    onAdd: (amount: Double, date: Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("ar")) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(defaultDate) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val amount = amountText.trim().toDoubleOrNull()
    val valid = amount != null && amount > 0.0 && date > 0L

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Event, contentDescription = null, tint = LocalSemanticColors.current.info) },
        title = { Text("إضافة قسط") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpaceSm)) {
                AppTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "مبلغ القسط",
                    placeholder = if (suggestedAmount > 0) suggestedAmount.toLong().toString() else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = groupedRowShape(0, 1)
                )
                AppRowSurface(
                    shape = groupedRowShape(1, 1),
                    onClick = { showPicker = true }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppMinTouchTarget)
                            .padding(horizontal = AppSpaceLg, vertical = AppSpaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppSpaceXl)
                        )
                        Spacer(Modifier.width(AppSpaceMd))
                        Column {
                            Text(
                                "تاريخ القسط",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                dateFormat.format(Date(date)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (amount != null) onAdd(amount, date) },
                modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)
            ) { Text("إضافة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = AppMinTouchTarget)) { Text("إلغاء") }
        }
    )

    if (showPicker) {
        DayPickerDialog(
            initialLocalDay = date,
            onDismiss = { showPicker = false },
            onPick = {
                date = it
                showPicker = false
            }
        )
    }
}
