package com.shopmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * iOS-style continuous, generously-rounded corners (closer to SwiftUI's
 * default card/sheet/button radii than Material's usual tighter defaults)
 * so cards, dialogs, buttons and sheets across the whole app read as soft
 * and "squircle"-like instead of sharply cut. The same five-step Material
 * shape system every screen already pulls from via MaterialTheme.shapes,
 * so this single definition cascades app-wide — matching the reference
 * Claude.ai design's own rounded-corner language everywhere at once.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun rememberIsDarkTheme(themeMode: AppThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

/**
 * BUG FIXED (RTL layout): every string in this app is hardcoded Arabic —
 * there's no strings.xml localization, no values-ar folder, nothing that
 * makes layout direction depend on the device's language setting. Compose
 * only switches Row/Column/padding("start"/"end")/alignment to RTL when the
 * *system* locale itself is Arabic (or another RTL language). A very common
 * case on an economy phone: the device's system language is left on English
 * (or any LTR language) while the person just uses this Arabic app. This
 * app has no LTR content anywhere, so layout direction is forced to RTL
 * unconditionally instead of following the system locale.
 */
/**
 * UNIFIED ON CLAUDE'S DESIGN ("ازل الماتيريال يو، وازل الخلفية الديناميك،
 * ونسق كل الالوان والاشكال بتصميم Claude AI"): this composable used to
 * accept a `colorMode`/`colorPalette` pair and branch between Android 12+
 * wallpaper-driven Material You dynamic color, a true-grayscale "classic"
 * mode, and 20 hand-tuned hue palettes. All of that is removed — the app
 * now always paints itself with the single reference Claude.ai color
 * scheme (see [claudeLightScheme]/[claudeDarkScheme] in Palette.kt), in
 * whichever of light/dark [themeMode] resolves to. Nothing here depends on
 * the device wallpaper any more, and there is nothing left to pick.
 */
@Composable
fun ShopManagerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = rememberIsDarkTheme(themeMode)
    val colors = remember(useDark) { if (useDark) claudeDarkScheme() else claudeLightScheme() }

    // "مش شبه هيدر كلود، متل بلوك لون مختلف عن الخلفية": headers pull the
    // exact same [surfaceContainerHigh] tone every ordinary card already
    // uses, so it blends into the page as a subtle "slightly raised"
    // surface instead of standing out — matching Claude's own flush,
    // headerless top bars.
    val gradientColors = remember(colors) {
        listOf(colors.surfaceContainerHigh, colors.surfaceContainerHigh)
    }
    val semanticColors = remember(useDark) { semanticColorsFor(useDark) }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalBrandGradientColors provides gradientColors,
        LocalSemanticColors provides semanticColors,
        LocalIsDarkTheme provides useDark
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
