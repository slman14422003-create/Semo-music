package com.shopmanager.app.ui.settings

import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.notifications.NotifKind
import com.shopmanager.app.data.notifications.NotifSound
import com.shopmanager.app.data.notifications.NotificationSounds
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.data.materials.quantityLabel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import com.shopmanager.app.data.performance.MotionSpecs

internal val CURRENCY_OPTIONS = listOf("ل.س", "$", "SAR", "AED", "TRY")

/**
 * iOS 26 REDESIGN: replaces a plain [RadioButton] + label row with the
 * way iOS itself shows a single-choice list — the whole row is tappable,
 * the selected option gets a trailing checkmark instead of a filled
 * circle on the leading edge, and there's no visible "control" at all on
 * the unselected rows (just the label) the way a Material RadioButton
 * always draws its empty ring.
 */
@Composable
internal fun IosOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .defaultMinSize(minHeight = AppMinTouchTarget)
            .padding(vertical = AppSpace10),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        AnimatedVisibility(
            visible = selected,
            enter = MotionSpecs.popEnter(),
            exit = MotionSpecs.popExit()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun NotificationSoundDialog(
    kind: NotifKind,
    current: NotifSound,
    initialUri: String?,
    onDismiss: () -> Unit,
    onSelect: (NotifSound, String?) -> Unit
) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf(current) }
    var pickedUri by remember { mutableStateOf(initialUri) }
    // منتقي نغمات النظام: يعرض كل نغمات الإشعارات الموجودة على الهاتف (بما فيها أي نغمات مثبّتة).
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri: Uri? = res.data?.let {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                it.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (uri != null) {
            pickedUri = uri.toString()
            picked = NotifSound.DEVICE
            NotificationSounds.preview(context, NotifSound.DEVICE, pickedUri)
        }
    }
    fun choose(sound: NotifSound) {
        picked = sound
        if (sound == NotifSound.DEVICE) {
            ringtonePicker.launch(
                Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                    .putExtra(
                        android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        pickedUri?.let { Uri.parse(it) }
                    )
            )
        } else NotificationSounds.preview(context, sound)
    }
    DisposableEffect(Unit) { onDispose { NotificationSounds.stopPreview() } }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صوت: ${kind.label}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                NotifSound.values().forEach { sound ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { choose(sound) }
                            .padding(vertical = AppSpaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = picked == sound, onClick = { choose(sound) })
                        Spacer(Modifier.width(AppSpaceXs))
                        Column(Modifier.weight(1f)) {
                            Text(
                                sound.label,
                                fontWeight = if (picked == sound) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                sound.hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (sound != NotifSound.SILENT && (sound != NotifSound.DEVICE || pickedUri != null)) {
                            IconButton(onClick = { NotificationSounds.preview(context, sound, pickedUri) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                shape = RectangleShape,
                enabled = picked != NotifSound.DEVICE || pickedUri != null,
                onClick = { onSelect(picked, pickedUri) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
internal fun CurrencyPickerDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var custom by remember { mutableStateOf(current.takeIf { it !in CURRENCY_OPTIONS } ?: "") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر العملة") },
        text = {
            Column {
                CURRENCY_OPTIONS.forEach { option ->
                    IosOptionRow(label = option, selected = current == option, onClick = { onSelect(option) })
                }
                Spacer(Modifier.height(AppSpaceSm))
                AppTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = "عملة أخرى",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = custom.isNotBlank(),
                shape = RectangleShape,
                onClick = { onSelect(custom.trim()) }
            ) { Text("استخدام") }
        },
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
internal fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز PIN") },
        text = {
            Column {
                AppTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = "رمز من 4 إلى 6 أرقام",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = "تأكيد الرمز",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = AppSpaceSm)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = AppSpaceSm)) }
            }
        },
        confirmButton = {
            TextButton(shape = RectangleShape, onClick = {
                when {
                    pin.length < 4 -> error = "الرمز لازم يكون 4 أرقام على الأقل"
                    pin != confirm -> error = "الرمزان غير متطابقين"
                    else -> onConfirm(pin)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}

internal fun formatBackupDate(timestampMillis: Long): String =
    runCatching {
        // 12-hour clock (was HH:mm/24h) — "a" renders as ص/م in Arabic locale.
        SimpleDateFormat("yyyy/MM/dd — h:mm a", Locale("ar")).format(java.util.Date(timestampMillis))
    }.getOrDefault("—")

internal fun buildBackupText(
    debtsState: com.shopmanager.app.ui.debts.DebtsUiState,
    materialsState: com.shopmanager.app.ui.materials.MaterialsUiState,
    currency: String
): String {
    val nf = NumberFormat.getNumberInstance(Locale("ar"))
    val sb = StringBuilder()
    sb.append("📋 نسخة احتياطية — إدارة المحل\n")
    sb.append("=".repeat(24)).append("\n\n")

    sb.append("💰 الديون (${debtsState.persons.size} عميل، الإجمالي ${nf.format(debtsState.totalAmount)} $currency)\n")
    debtsState.persons.sortedByDescending { it.amount }.forEach { p ->
        sb.append("• ${p.name}: ${nf.format(p.amount)} $currency\n")
    }

    sb.append("\n📦 المواد (${materialsState.materials.size})\n")
    materialsState.materials.sortedBy { it.name }.forEach { m ->
        val price = materialsState.prices[m.name]
        sb.append("• ${m.name}: ${m.quantityLabel()}")
        if (price != null) sb.append(" — ${nf.format(price)} $currency")
        sb.append("\n")
    }

    sb.append("\nتم الإنشاء تلقائيًا من التطبيق.")
    return sb.toString()
}
