package com.shopmanager.app.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Small chooser shown by every "مشاركة" button that can produce either a
 * formatted PNG report image or a plain text report (materials, debts,
 * and the whole-app export in Settings). One shared dialog so the choice
 * looks and behaves identically everywhere instead of three near-duplicate
 * dialogs quietly drifting apart over time.
 */
@Composable
fun ShareFormatDialog(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickText: () -> Unit
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشاركة") },
        text = { Text("اختر طريقة المشاركة") },
        confirmButton = {
            // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
            // PersonEditDialog.kt's doc comment.
            TextButton(shape = RectangleShape, onClick = {
                onDismiss()
                onPickImage()
            }) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("صورة")
            }
        },
        dismissButton = {
            TextButton(shape = RectangleShape, onClick = {
                onDismiss()
                onPickText()
            }) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("نص")
            }
        }
    )
}
