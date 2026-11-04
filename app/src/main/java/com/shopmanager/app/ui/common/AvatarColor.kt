package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.Color
import com.shopmanager.app.ui.theme.AvatarPalette
import kotlin.math.absoluteValue

// REDESIGN ("بدي تصميم جميل اجمل من هيك"): every customer/material circle
// used to fall back to one flat neutral gray regardless of name, which read
// as flat and repetitive across a long debts/materials list (see the
// reference screenshots — every row's circle identical). Restored to a
// deterministic per-name color, but drawn only from [AvatarPalette]'s small
// curated warm ladder (terracotta/gold/sage/clay family) instead of a full
// generic hue wheel, so the app still reads as one cohesive warm palette —
// just with enough variety that a long list of names is easy to visually
// scan instead of a wall of identical circles. Same function signature as
// before (still deterministic, still callable from the non-Composable
// NotificationHelper), only the returned color varies by name now.
fun avatarColorFor(name: String): Color {
    if (name.isBlank()) return AvatarPalette.first()
    val index = name.trim().hashCode().absoluteValue % AvatarPalette.size
    return AvatarPalette[index]
}
