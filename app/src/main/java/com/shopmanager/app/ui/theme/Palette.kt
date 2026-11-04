package com.shopmanager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UNIFIED ON CLAUDE'S DESIGN ("ازل الماتيريال يو، وازل الخلفية الديناميك،
 * ونسق كل الالوان والاشكال بتصميم Claude AI"): this file used to hold
 * Android 12+ wallpaper-driven "Material You" dynamic color
 * (dynamicLightColorScheme/dynamicDarkColorScheme), a whole
 * AppColorMode (يدوي/تلقائي/كلاسيكي) switch, and a 20-swatch
 * AppColorPalette picker with its own generic hue-derived tone() engine.
 * All of that is gone: the app now always renders with exactly one design —
 * the reference Claude.ai color scheme below — in both light and dark,
 * with no wallpaper dependency and nothing left to pick in Settings.
 */

/**
 * The app's single color scheme, built directly from the reference
 * Claude.ai design's literal color tokens (see the `Claude*` constants in
 * Color.kt, copied 1:1 from that design's values/colors.xml +
 * values-night/colors.xml). Every shared component that paints itself from
 * `MaterialTheme.colorScheme` — [com.shopmanager.app.ui.common.GlassCard],
 * [com.shopmanager.app.ui.common.GlassAlertDialog], settings rows, headers,
 * pill buttons — therefore matches the reference design exactly, since
 * `surfaceContainerHigh` here is the reference's literal `glass_fill_strong`
 * (the flat white/near-black fill its bg_glass_card / bg_settings_group /
 * bg_dialog_card drawables all use), not a computed tint.
 */
internal fun claudeLightScheme(): ColorScheme = lightColorScheme(
    // NEUTRALIZED ("الرموز والازرار كلها برتقالي لازم تكون ابيض متل كلاود"):
    // `primary` is read by every un-styled Material3 control — Switch's
    // checked track/thumb, the settings theme-picker's trailing checkmark
    // tint, TextButton's default label color (the dialog confirm/dismiss
    // buttons in GlassAlertDialog) — so as long as it pointed at the brand
    // terracotta, all of those read as orange even though the real Claude
    // app's own chrome for these same controls is neutral black/white/gray,
    // with color reserved for destructive labels (error) and the small
    // brand mark alone (splash, notification icon). Pointing primary at the
    // same near-black text tone as onSurface, instead of the orange, fixes
    // every one of those controls in this one place.
    primary = ClaudeTextPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = ClaudeBgLight3,
    onPrimaryContainer = ClaudeTextPrimaryLight,
    secondary = ClaudeSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = ClaudeM3SecondaryContainerLight,
    onSecondaryContainer = ClaudeM3OnSecondaryContainerLight,
    tertiary = ClaudeAccentGoldLight,
    onTertiary = Color.White,
    tertiaryContainer = ClaudeM3TertiaryContainerLight,
    onTertiaryContainer = ClaudeM3OnTertiaryContainerLight,
    error = ClaudeAccentRedLight,
    onError = Color.White,
    errorContainer = ClaudeM3ErrorContainerLight,
    onErrorContainer = ClaudeM3OnErrorContainerLight,
    background = ClaudeBgLight1,
    onBackground = ClaudeTextPrimaryLight,
    surface = ClaudeBgLight1,
    onSurface = ClaudeTextPrimaryLight,
    surfaceVariant = ClaudeM3SurfaceVariantLight,
    onSurfaceVariant = ClaudeTextSecondaryLight,
    surfaceTint = Color.Transparent,
    outline = ClaudeTextTertiaryLight,
    outlineVariant = ClaudeBorderLight,
    inverseSurface = ClaudeCardDark,
    inverseOnSurface = ClaudeTextPrimaryDark,
    inversePrimary = ClaudeTextPrimaryDark,
    // The exact "glass" ladder: cards/dialogs/settings-groups/headers all
    // read surfaceContainerHigh, which is the reference's literal
    // glass_fill_strong — pure white, with a hairline glass_border drawn
    // separately by each component's own .border() call.
    surfaceContainerLowest = ClaudeCardSoftLight,
    surfaceContainerLow = ClaudeBgLight2,
    surfaceContainer = ClaudeM3SurfaceContainerLight,
    surfaceContainerHigh = ClaudeRowFillLight,
    surfaceContainerHighest = ClaudeM3SurfaceContainerHighLight,
    // COMPLETENESS FIX: lightColorScheme()/darkColorScheme() fall back to
    // Material3's own default seed (a purple baseline) for any role left
    // unspecified. surfaceDim/surfaceBright were the two roles in this
    // family still missing here, so any Material3-internal component that
    // reads them directly (Slider track, NavigationBar, pull-to-refresh,
    // bottom-sheet scrim edges) could have rendered an off-palette purple
    // sliver instead of this app's own neutral tone. Filled from the same
    // background ladder as the rest of the surface family.
    surfaceDim = ClaudeBgLight3,
    surfaceBright = ClaudeCardLight,
)

internal fun claudeDarkScheme(): ColorScheme = darkColorScheme(
    // NEUTRALIZED — see claudeLightScheme's note above; same fix, mirrored
    // for dark mode (near-white instead of near-black text tone).
    primary = ClaudeTextPrimaryDark,
    onPrimary = ClaudeBgDark1,
    primaryContainer = ClaudeBgDark3,
    onPrimaryContainer = ClaudeTextPrimaryDark,
    secondary = ClaudeSecondaryDark,
    onSecondary = ClaudeBgDark1,
    secondaryContainer = ClaudeM3SecondaryContainerDark,
    onSecondaryContainer = ClaudeM3OnSecondaryContainerDark,
    tertiary = ClaudeAccentGoldDark,
    onTertiary = Color(0xFF241A0D),
    tertiaryContainer = ClaudeM3TertiaryContainerDark,
    onTertiaryContainer = ClaudeM3OnTertiaryContainerDark,
    error = ClaudeAccentRedDark,
    onError = Color(0xFF35110A),
    errorContainer = ClaudeM3ErrorContainerDark,
    onErrorContainer = ClaudeM3OnErrorContainerDark,
    background = ClaudeBgDark1,
    onBackground = ClaudeTextPrimaryDark,
    surface = ClaudeBgDark1,
    onSurface = ClaudeTextPrimaryDark,
    surfaceVariant = ClaudeM3SurfaceVariantDark,
    onSurfaceVariant = ClaudeTextSecondaryDark,
    surfaceTint = Color.Transparent,
    outline = ClaudeTextTertiaryDark,
    outlineVariant = ClaudeBorderDark,
    inverseSurface = ClaudeCardLight,
    inverseOnSurface = ClaudeTextPrimaryLight,
    inversePrimary = ClaudeTextPrimaryLight,
    surfaceContainerLowest = ClaudeBgDark1,
    surfaceContainerLow = ClaudeBgDark2,
    surfaceContainer = ClaudeM3SurfaceContainerDark,
    surfaceContainerHigh = ClaudeCardDark,
    surfaceContainerHighest = ClaudeM3SurfaceContainerHighDark,
    // See claudeLightScheme's note above — same fix, mirrored for dark.
    surfaceDim = ClaudeBgDark1,
    surfaceBright = ClaudeBorderDark,
)

/** Carries the app's single flat brand-gradient color (Claude's terracotta
 * accent) down to [com.shopmanager.app.ui.common.BrandGradient] without
 * threading a parameter through every screen that already calls
 * `BrandGradient.brush()`. Provided once near the root (see
 * ShopManagerTheme); the default here is also Claude's light-theme accent,
 * so anything outside the provider (previews) still renders correctly. */
val LocalBrandGradientColors = staticCompositionLocalOf { listOf(ClaudeOrangeLight, ClaudeOrangeLight) }

/**
 * Status-icon/badge colors (success ✓, warning ⚠, danger ✕, info) used
 * across DeleteIconButton, MaterialsScreen, NotesScreen,
 * PersonDetailScreen and SettingsScreen — always the reference Claude.ai
 * design's own light/dark accent_green/accent_gold/accent_red tokens
 * (accent_red mirrors colorScheme.error exactly, since claudeLightScheme/
 * claudeDarkScheme already set `error` from the same token); "info" has no
 * blue in the reference design at all, so it uses the same warm neutral
 * secondary accent the design uses for secondary chips instead of
 * introducing an off-palette blue.
 */
data class SemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
)

internal fun semanticColorsFor(useDark: Boolean): SemanticColors =
    if (useDark) SemanticColors(
        success = ClaudeAccentGreenDark,
        warning = ClaudeAccentGoldDark,
        danger = ClaudeAccentRedDark,
        info = ClaudeSecondaryDark,
    ) else SemanticColors(
        success = ClaudeAccentGreenLight,
        warning = ClaudeAccentGoldLight,
        danger = ClaudeAccentRedLight,
        info = ClaudeSecondaryLight,
    )

/** Provided once near the root (see ShopManagerTheme); defaults to the
 * light-theme semantic colors so anything outside the provider (previews)
 * still renders correctly. */
val LocalSemanticColors = staticCompositionLocalOf { semanticColorsFor(useDark = false) }

/**
 * The app's resolved dark/light state *after* [AppThemeMode]'s
 * LIGHT/DARK/SYSTEM override — provided once near the root (see
 * ShopManagerTheme) so any descendant that needs to branch on "are we
 * actually in dark mode right now" doesn't have to re-derive it by calling
 * `isSystemInDarkTheme()` directly, which would silently ignore an explicit
 * Settings → المظهر → light/dark override and just reflect the raw OS
 * setting instead (the same bug this avoids in WebViewScreen's force-dark
 * for the help/privacy pages).
 */
val LocalIsDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }
