package com.shopmanager.app.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

suspend fun SnackbarHostState.showWithOptionalUndo(message: String, hasUndo: Boolean): Boolean {
    if (!hasUndo) {
        showSnackbar(message)
        return false
    }
    val result = showSnackbar(
        message = message,
        actionLabel = "تراجع",
        duration = SnackbarDuration.Long
    )
    return result == SnackbarResult.ActionPerformed
}
