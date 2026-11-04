package com.shopmanager.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.shopmanager.app.MainActivity
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BlurMenuAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun BlurLayer(
    target: BlurTarget,
    radius: Float,
    scale: Float,
    noise: Boolean,
    tint: Color,
    backdrop: Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            BlurView(ctx).apply {
                val clear = ColorDrawable(backdrop.toArgb())
                clear.setBounds(0, 0, max(target.width, 1), max(target.height, 1))
                setupWith(target, scale, noise)
                    .setBlurRadius(radius)
                    .setFrameClearDrawable(clear)
                    .setOverlayColor(tint.toArgb())
            }
        },
        update = { it.setOverlayColor(tint.toArgb()) }
    )
}

@Composable
fun blurEnabled(strongOnly: Boolean = false): Boolean {
    val tier = LocalPerformanceTier.current
    val target = (LocalContext.current.findActivity() as? MainActivity)?.blurTarget
    return target != null && (tier == PerformanceTier.STANDARD || (!strongOnly && tier == PerformanceTier.BALANCED))
}

@Composable
fun BlurBackdrop(modifier: Modifier, tint: Color, radius: Float? = null) {
    val tier = LocalPerformanceTier.current
    val target = (LocalContext.current.findActivity() as? MainActivity)?.blurTarget ?: return
    val strong = tier == PerformanceTier.STANDARD
    BlurLayer(
        target = target,
        radius = radius ?: if (strong) 9f else 5f,
        scale = if (strong) 3f else 6f,
        noise = strong,
        tint = tint,
        backdrop = MaterialTheme.colorScheme.background,
        modifier = modifier
    )
}

@Composable
fun BlurContextMenu(
    anchor: Rect,
    actions: List<BlurMenuAction>,
    onDismiss: () -> Unit,
    preview: @Composable () -> Unit
) {
    val context = LocalContext.current
    val tier = LocalPerformanceTier.current
    val target = (context.findActivity() as? MainActivity)?.blurTarget
    val strong = target != null && tier == PerformanceTier.STANDARD
    val normal = target != null && tier == PerformanceTier.BALANCED
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var origin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var ready by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(ready) { if (ready) shown = true }

    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionSpecs.popInSpring(),
        label = "blurMenuProgress"
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionSpecs.contentTween(),
        label = "blurMenuFade"
    )

    val backdropTint = if (dark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.40f)
    val scrim = if (dark) Color.Black.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.32f)
    val glassTint = scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.70f else 0.78f)
    val menuShape = RoundedCornerShape(22.dp)
    val menuWidth = 236.dp
    val itemHeight = 56.dp
    val gap = 10.dp
    val margin = 16.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.localToScreen(Offset.Zero)
                    rootSize = it.size
                    ready = true
                }
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade }) {
                if (target != null && (strong || normal)) {
                    BlurLayer(
                        target = target,
                        radius = if (strong) 9f else 5f,
                        scale = if (strong) 3f else 6f,
                        noise = strong,
                        tint = backdropTint,
                        backdrop = scheme.background,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(scrim))
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )

            if (ready) {
                val left = anchor.left - origin.x
                val top = anchor.top - origin.y
                val menuHeightPx = with(density) { (itemHeight * actions.size).toPx() }
                val gapPx = with(density) { gap.toPx() }
                val marginPx = with(density) { margin.toPx() }
                val menuWidthPx = with(density) { menuWidth.toPx() }
                val bottomLimit = rootSize.height - marginPx
                val below = top + anchor.height + gapPx + menuHeightPx <= bottomLimit
                val menuTop = if (below) top + anchor.height + gapPx else max(marginPx, top - gapPx - menuHeightPx)
                val rawLeft = if (rtl) left + anchor.width - menuWidthPx - marginPx else left + marginPx
                val menuLeft = min(max(rawLeft, marginPx), max(marginPx, rootSize.width - menuWidthPx - marginPx))

                Box(
                    Modifier
                        .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                        .size(
                            with(density) { anchor.width.toDp() },
                            with(density) { anchor.height.toDp() }
                        )
                        .graphicsLayer {
                            val s = 1f + 0.025f * progress
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(18.dp * progress.coerceAtLeast(0f), RoundedCornerShape(20.dp), clip = false)
                ) {
                    preview()
                }

                Box(
                    Modifier
                        .offset { IntOffset(menuLeft.roundToInt(), menuTop.roundToInt()) }
                        .width(menuWidth)
                        .graphicsLayer {
                            alpha = fade
                            val s = 0.9f + 0.1f * progress
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, if (below) 0f else 1f)
                        }
                        .shadow(24.dp, menuShape, clip = false)
                        .clip(menuShape)
                        .background(if (strong) Color.Transparent else scheme.surfaceContainerHigh)
                ) {
                    if (strong && target != null) {
                        BlurLayer(
                            target = target,
                            radius = 16f,
                            scale = 4f,
                            noise = true,
                            tint = glassTint,
                            backdrop = scheme.background,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    Column(Modifier.fillMaxWidth()) {
                        actions.forEachIndexed { index, action ->
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(scheme.onSurface.copy(alpha = 0.12f))
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDismiss()
                                        action.onClick()
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    action.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (action.danger) Color(0xFFE5645A) else scheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    tint = if (action.danger) Color(0xFFE5645A) else scheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
