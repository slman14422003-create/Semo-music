package com.shopmanager.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.shopmanager.app.data.security.AdminPasswordRepository
import com.shopmanager.app.data.security.PinAttemptThrottle
import com.shopmanager.app.ui.dashboard.DashboardScreen
import com.shopmanager.app.ui.common.AppDrawerContent
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.GlassAlertDialog

// لوحة المسؤول السرية: PIN entry dialog gating ROUTE_ADMIN. MOVED here
// from DashboardScreen ("انقل ايقونة المسؤول الى المنيو الى جانب
// الاعدادات") along with its trigger button, which now lives in the side
// drawer next to الإعدادات instead of on Home's own header — see
// showAdminPinDialog/adminThrottle above and AppDrawerContent's
// onOpenAdmin. Behavior is unchanged from before: same fixed password
// (now verified against Firebase, see AdminPasswordRepository) and the same shared PinAttemptThrottle lockout,
// just triggered from a different place now.
@Composable
internal fun AdminPinDialog(
    throttle: PinAttemptThrottle,
    onDismiss: () -> Unit,
    onVerify: suspend (String) -> AdminPasswordRepository.Outcome
) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockRemaining by remember { mutableLongStateOf(throttle.lockRemainingSeconds()) }
    val isLocked = lockRemaining > 0
    val verifyScope = rememberCoroutineScope()

    LaunchedEffect(isLocked) {
        while (lockRemaining > 0) {
            delay(1000)
            lockRemaining = throttle.lockRemainingSeconds()
        }
    }

    fun submit() {
        if (checking || isLocked || pin.isEmpty()) return
        checking = true
        message = null
        verifyScope.launch {
            val outcome = onVerify(pin)
            checking = false
            lockRemaining = throttle.lockRemainingSeconds()
            message = when (outcome) {
                is AdminPasswordRepository.Outcome.Granted -> null
                is AdminPasswordRepository.Outcome.Denied -> "كلمة المرور غير صحيحة"
                is AdminPasswordRepository.Outcome.NotConfigured ->
                    "كلمة المرور غير معيّنة في Firebase (config ← admin ← password)"
                is AdminPasswordRepository.Outcome.Unavailable -> outcome.reason
            }
        }
    }

    GlassAlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text("دخول لوحة المطوّر") },
        text = {
            Column {
                AppTextField(
                    value = pin,
                    onValueChange = { pin = it.take(40); message = null },
                    label = "كلمة المرور",
                    singleLine = true,
                    enabled = !isLocked && !checking,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    isError = message != null
                )
                if (isLocked) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "محاولات كثيرة خاطئة — حاول بعد $lockRemaining ثانية",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        message ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isLocked && !checking && pin.isNotEmpty(), shape = RectangleShape, onClick = { submit() }) {
                if (checking) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = androidx.compose.material3.LocalContentColor.current
                    )
                } else {
                    Text("دخول")
                }
            }
        },
        dismissButton = { TextButton(enabled = !checking, shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}
