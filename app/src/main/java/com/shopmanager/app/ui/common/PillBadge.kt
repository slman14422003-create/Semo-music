package com.shopmanager.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.LocalSemanticColors

/**
 * REDESIGN ("نسق كل واجهات التطبيق متل هيك تصميم جميل" — reference
 * screenshot showing the shortage list with colored rounded quantity
 * badges instead of plain text): a small rounded "pill" — tinted fill,
 * matching-color text — used anywhere a short value needs to stand out at
 * a glance next to a row (a shortage's quantity, a material's amount
 * needed). Shared by the dashboard shortage list and المواد's own list so
 * both read as one consistent badge language instead of each screen
 * showing the same kind of value as plain text in its own way.
 */
@Composable
fun PillBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/**
 * Color-codes a shortage quantity by how much is needed, matching the
 * reference design (a light ask reads green, a heavier one reads gold) so
 * the badge itself carries meaning instead of always being the same
 * neutral tone regardless of amount.
 */
@Composable
fun pillColorForQuantity(quantity: Double): Color {
    val semantic = LocalSemanticColors.current
    return if (quantity > 1.0) semantic.warning else semantic.success
}
