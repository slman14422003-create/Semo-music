package com.shopmanager.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.LocalSemanticColors

@Composable
fun ImportantStarButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 52.dp
) {
    val cs = MaterialTheme.colorScheme
    val gold = LocalSemanticColors.current.warning
    val bg by animateColorAsState(
        targetValue = if (checked) gold.copy(alpha = 0.22f) else cs.surfaceContainerHighest,
        label = "starBg"
    )
    val tint by animateColorAsState(
        targetValue = if (checked) gold else cs.onSurfaceVariant,
        label = "starTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "starScale"
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (checked) "مادة هامة" else "وضع كمادة هامة",
            tint = tint,
            modifier = Modifier.size(size * 0.5f).scale(scale)
        )
    }
}
