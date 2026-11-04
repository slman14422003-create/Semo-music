package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.collectDelayedPressedAsState

/**
 * Flat, opaque panel surface used for every header/top-bar/dialog/card in
 * the app. This used to be a translucent "liquid glass" effect that only
 * rendered that way when Settings -> المظهر had "زجاج" selected; that mode
 * has been removed entirely, so this is now just a plain solid panel with
 * a drop shadow and an optional hairline border — the same look every
 * caller already fell back to outside glass mode, kept as the one and
 * only look now so nothing needed to change at any call site.
 *
 * PERF (low-end tier, "اصلاحات للاجهزة اللي فيها معالج رسوميات ضعيف"): a
 * drop shadow isn't free — `Modifier.shadow` asks the GPU to rasterize a
 * soft penumbra behind the shape on every frame it's on screen, and this
 * one modifier sits behind essentially every header/card/dialog/nav-bar in
 * the app (see FloatingBottomNav, GlassCard, every GlassAlertDialog...),
 * so on a weak/old GPU those shadows add up to real, measurable frame
 * time. Same trade-off BrandGradient already makes for its per-pixel
 * gradient shader: LOW-tier devices skip the shadow outright instead of
 * drawing it at a reduced elevation, since a flat panel with no shadow
 * reads as an intentional, cohesive "flatter" look rather than a
 * half-broken one — never a jarring mix of some panels shadowed and
 * others not.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    baseBrush: Brush = BrandGradient.brush(),
    // "عائم" (floating): a soft drop shadow under the panel so it reads as
    // a distinct floating layer above the content behind it. 0.dp keeps a
    // flush look for callers that want the panel to sit flat against
    // whatever's behind it.
    //
    // Claude-app style ("والهيكل"): Claude's own UI barely uses shadow at
    // all — panels separate from the background mostly through a tonal
    // shift (a lighter surfaceContainer on a darker background), not a
    // drawn shadow. Dropped from 10.dp to 2.dp so headers/dialogs/the nav
    // bar keep just enough lift to read as a separate layer without the
    // heavier "floating card" look the old 10.dp had — every call site
    // that doesn't pass its own elevation picks this up automatically.
    elevation: Dp = 2.dp,
    // `null` (the default) means "no border"; any real Color — white
    // included — draws a 1.dp hairline at a fixed 12% alpha.
    rimColor: Color? = null
): Modifier {
    // الظل الحقيقي (blur رسومي لكل إطار) للأداء القوي فقط؛ المتوازن والاقتصادي
    // يعتمدان على الفصل بالتدرج اللوني + الحد الرفيع بلا أي ظل.
    val isHighTier = LocalPerformanceTier.current == PerformanceTier.STANDARD
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    return this
        .let {
            if (elevation > 0.dp && isHighTier) {
                it.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
            } else it
        }
        .let {
            // الأجهزة الضعيفة: القصّ بمسار غير مستطيل (clip) مكلف على معالج رسوميات قديم؛
            // الرسم المباشر بالشكل يعطي نفس النتيجة بلا قصّ.
            if (isLowTier) it.background(baseBrush, shape) else it.clip(shape).background(baseBrush)
        }
        .let {
            if (rimColor == null) it
            else it.border(1.dp, rimColor.copy(alpha = 0.12f), shape)
        }
}

/**
 * Circular icon button used on top of the header gradient and other
 * colored panels. Previously switched to a translucent "glass" look in
 * glass mode; that mode is gone, so this is now always the plain,
 * solidly-tinted circular icon button every other color mode already used.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = BrandOnGradient,
    size: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectDelayedPressedAsState()
    // LAG FIX: الـ scale يُقرأ داخل graphicsLayer (مرحلة الرسم) بدل Modifier.scale(value)
    // الذي كان يُعيد تركيب الزر في كل إطار من حركة الضغط.
    val scale = animateFloatAsState(
        targetValue = if (pressed) MotionSpecs.pressScale() else 1f,
        animationSpec = MotionSpecs.quickSpring(),
        label = "glassIconButtonScale"
    )
    // COLOR PRECISION FIX ("الوان التطبيق مانها بتشبة كلود ابدأ"): real
    // Claude header icons (hamburger, share, the "i" info icon) sit flat on
    // the background with no visible circle behind them at rest at all —
    // just the bare icon — and only pick up a faint highlight on press.
    // restingFillAlpha used to be 0.22f, a permanent faint white disc
    // behind every one of these icons even when idle; now 0f so the icon
    // is the only thing visible until touched.
    val restingFillAlpha = 0f
    val restingRimAlpha = 0f
    val fillAlpha by animateFloatAsState(
        targetValue = if (pressed) restingFillAlpha + 0.14f else restingFillAlpha,
        animationSpec = MotionSpecs.quickSpring(),
        label = "glassIconButtonFill"
    )
    val rimAlpha by animateFloatAsState(
        targetValue = if (pressed) restingRimAlpha + 0.14f else restingRimAlpha,
        animationSpec = MotionSpecs.quickSpring(),
        label = "glassIconButtonRim"
    )
    // LIGHT-MODE CONTRAST FIX ("اصلح تباين الوضع النهاري"): this press
    // feedback used to be literal Color.White regardless of theme. These
    // icons sit on the header, which reads `surfaceContainerHigh` — white
    // in light mode — so a white press-tint on a white header was
    // completely invisible: tapping a header icon showed no feedback at
    // all in light mode. `onSurface` is this theme's own ink color (still
    // near-white in dark mode, so dark mode's look is unchanged), giving a
    // visible tint in both.
    val pressTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(SolidColor(pressTint.copy(alpha = fillAlpha)))
            .border(
                1.dp,
                SolidColor(pressTint.copy(alpha = rimAlpha)),
                CircleShape
            )
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
