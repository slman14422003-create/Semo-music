package com.shopmanager.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.history.ChangeHistory
import com.shopmanager.app.ui.common.GlassAlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChangeHistoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(ChangeHistory.all(context)) }
    val format = remember { SimpleDateFormat("yyyy/MM/dd — HH:mm", Locale.US) }
    val clearButton: (@Composable () -> Unit)? = if (entries.isNotEmpty()) {
        {
            TextButton(
                shape = RectangleShape,
                onClick = {
                    ChangeHistory.clear(context)
                    entries = emptyList()
                }
            ) { Text("مسح السجل") }
        }
    } else null

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل التعديلات") },
        text = {
            if (entries.isEmpty()) {
                Text("لا توجد عمليات مسجلة بعد")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(entries) { entry ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                format.format(Date(entry.time)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إغلاق") } },
        dismissButton = clearButton
    )
}

@Composable
fun BackupPickerDialog(
    backups: List<com.shopmanager.app.data.backup.BackupManager.BackupInfo>,
    formatDate: (Long) -> String,
    onPick: (com.shopmanager.app.data.backup.BackupManager.BackupInfo) -> Unit,
    onDismiss: () -> Unit
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("النسخ المحفوظة على الجهاز") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(backups) { backup ->
                    TextButton(shape = RectangleShape, onClick = { onPick(backup) }) {
                        Text("${formatDate(backup.createdAt)} — ${backup.personsCount} عميل، ${backup.materialsCount} مادة")
                    }
                }
            }
        },
        confirmButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إغلاق") } }
    )
}
