package com.shopmanager.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.theme.LocalBrandGradientColors

/**
 * The single brand gradient used everywhere a "header" appears: dashboard
 * header, top app bars, person-detail header. One definition means the
 * whole app reads as one cohesive product instead of each screen picking
 * its own tint, and it stays deep enough in both themes to always pair with
 * white text/icons (see the status bar fix in MainActivity).
 *
 * PERF: the brush is `remember`-ed instead of being rebuilt every
 * recomposition. Every screen that has a live Firestore listener
 * recomposes often (any list update anywhere), and reallocating a
 * `Brush.verticalGradient` on each of those passes was pure waste — this
 * was one of a few small contributors to the sluggish screen-to-screen
 * feel reported after the last redesign.
 *
 * PERF (low-end tier): a gradient shader still has to be evaluated per
 * pixel by the GPU on every draw, and cheap SoCs (the Mali/PowerVR parts
 * in entry-level phones) with older/thinner GPU driver stacks are exactly
 * where that shows up as extra frame time on every header. STANDARD-tier
 * devices keep the real gradient; LOW-tier devices get a flat SolidColor
 * of the same brand start color instead — same brand look, no per-pixel
 * shader.
 */
object BrandGradient {
    @Composable
    fun brush(): Brush {
        val colors = LocalBrandGradientColors.current
        val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
        return remember(isLowTier, colors) {
            if (isLowTier) SolidColor(colors.first()) else Brush.verticalGradient(colors)
        }
    }
}

// "شيل الألوان، خليه بس ليلي/نهاري" + "ما شكلها متل كلود": this used to be a
// fixed Color.White — safe back when every header/nav panel underneath it
// was always a deep, hardcoded brand color (dark enough for white text in
// either theme by construction). Headers are now toned from the ordinary
// [MaterialTheme.colorScheme.surfaceContainerHigh] role instead (see
// Theme.kt's `gradientColors`) — the same subtle "slightly raised card"
// tone every other surface in the app uses, so the header blends into the
// page instead of standing out as its own separate colored block, the way
// Claude's own headers do. That tone is light in light mode / dark in dark
// mode, so the text/icon color drawn on top of it has to follow the theme
// too: this is now a computed property reading the live color scheme's own
// [onSurface] role instead of a fixed constant, and every existing call
// site (Icon/Text `tint`/`color = BrandOnGradient`) keeps working unchanged
// since property-read syntax is identical to a plain val.
val BrandOnGradient: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
