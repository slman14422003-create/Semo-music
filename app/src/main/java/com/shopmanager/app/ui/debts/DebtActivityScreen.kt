package com.shopmanager.app.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.debts.DebtActivityLog.Kind
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.PillBadge
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة مستقلة لسجل الديون (آخر العمليات: سداد / إضافة دين / عميل جديد ...)
 * مع التاريخ والوقت. تتزامن بين كل الأجهزة (انظر DebtActivityLog).
 */
@Composable
fun DebtActivityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd — HH:mm", Locale.US) }
    var confirmClear by remember { mutableStateOf(false) }

    // Result: نجاح = القائمة، فشل = رفض القراءة (غالباً قواعد Firestore لم تُنشر بعد).
    val flow = remember {
        DebtActivityLog.listen(context)
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
    }
    val state by flow.collectAsState(initial = null)
    val entries = state?.getOrNull() ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar(
                title = "سجل الديون",
                reservedIcons = 1,
                navigation = {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        ScreenIconButton(
                            icon = Icons.Default.DeleteSweep,
                            contentDescription = "مسح السجل",
                            onClick = { confirmClear = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state == null -> AppEmptyState(icon = Icons.Default.History, text = "جارِ التحميل...")
                state?.isFailure == true -> AppEmptyState(
                    icon = Icons.Default.Close,
                    text = "تعذر تحميل السجل\nتأكد من الاتصال ومن نشر قواعد Firestore لمجموعة debt_activity"
                )
                entries.isEmpty() -> AppEmptyState(icon = Icons.Default.History, text = "لا توجد عمليات مسجلة بعد")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppScreenPadding, end = AppScreenPadding, top = 4.dp, bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppGroupGap)
                ) {
                    itemsIndexed(entries) { index, entry ->
                        val (label, icon, color) = when (entry.kind) {
                            Kind.PAID -> Triple("سداد دين", Icons.Default.Check, semantic.success)
                            Kind.PAID_ALL -> Triple("سداد كامل", Icons.Default.Check, semantic.success)
                            Kind.ADDED -> Triple("إضافة دين", Icons.Default.Add, semantic.warning)
                            Kind.NEW_CUSTOMER -> Triple("عميل جديد", Icons.Default.PersonAdd, semantic.info)
                            Kind.EDITED -> Triple("تعديل", Icons.Default.Edit, semantic.info)
                            Kind.DELETED -> Triple("حذف دين", Icons.Default.Delete, semantic.danger)
                            Kind.DELETED_CUSTOMER -> Triple("حذف عميل", Icons.Default.Close, semantic.danger)
                            Kind.UNDO -> Triple("تراجع", Icons.AutoMirrored.Filled.Undo, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AppRowSurface(shape = groupedRowShape(index, entries.lastIndex)) {
                            ActivityRow(
                                label = label,
                                icon = icon,
                                color = color,
                                person = entry.person,
                                amount = entry.amount,
                                time = dateFormat.format(Date(entry.time)) + if (entry.remote) " • من جهاز آخر" else ""
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        GlassAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل") },
            text = { Text("سيُمسح السجل من كل الأجهزة. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    confirmClear = false
                    scope.launch { runCatching { DebtActivityLog.clear(context) } }
                }) { Text("مسح") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ActivityRow(
    label: String,
    icon: ImageVector,
    color: Color,
    person: String,
    amount: Double,
    time: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (person.isBlank()) label else "$label — $person",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (amount > 0) {
            Spacer(Modifier.width(8.dp))
            PillBadge(text = "${Formatters.number(amount)} ${AppSettingsState.currencySymbol}", color = color)
        }
    }
}
