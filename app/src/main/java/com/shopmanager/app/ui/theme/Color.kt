package com.shopmanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// REDESIGN ("صمم الالوان بتصميم ChatGPT بكل التطبيق بالوضعين"): every value
// below now comes from ChatGPT's own app palette instead of Claude.ai's warm
// cream/terracotta one — pure white / near-black neutral surfaces, ChatGPT's
// signature green as the one warm accent, and a clean link-blue for
// highlighted actions (matching the blue "الخطط المدفوعة"/"ترقية الخطة"
// labels in ChatGPT's own Settings screen). Every name below is kept
// unchanged from the previous "Claude" pass — only the hex values moved — so
// every screen already reading these tokens (or the ColorScheme built from
// them in Palette.kt) picks up the new look with nothing else to touch.
// ============================================================================

// Backgrounds — light: clean white/off-white ladder; dark: ChatGPT's true
// near-black ladder (pure black base, not a tinted charcoal).
val ClaudeBgLight1 = Color(0xFFFFFFFF)
val ClaudeBgLight2 = Color(0xFFF7F7F8)
val ClaudeBgLight3 = Color(0xFFECECF1)
val ClaudeBgDark1 = Color(0xFF000000)
val ClaudeBgDark2 = Color(0xFF0D0D0D)
val ClaudeBgDark3 = Color(0xFF171717)

// Brand accent — orange (was blue; switched back per "برتقالي مو أزرق") (used for the small brand
// mark, splash and notification icon only; ordinary buttons/switches stay
// neutral black/white via primary below, same as the reference app).
val ClaudeOrangeLight = Color(0xFFC96442)
val ClaudeOrangeDark = Color(0xFFD97757)

// Neutral secondary accent (settings icons, secondary chips).
val ClaudeSecondaryLight = Color(0xFF6E6E80)
val ClaudeSecondaryDark = Color(0xFFA6A6B2)

// Card / dialog / settings-group fill — the flat "glass_fill_strong".
val ClaudeCardLight = Color(0xFFFFFFFF)
val ClaudeCardDark = Color(0xFF171717)
val ClaudeCardSoftLight = Color(0xFFF7F7F8)

// Borders.
val ClaudeBorderLight = Color(0xFFE5E5E5)
val ClaudeBorderDark = Color(0xFF2D2D2D)

// Text.
val ClaudeTextPrimaryLight = Color(0xFF0D0D0D)
// ACCESSIBILITY: ChatGPT's own secondary-label gray, checked against both
// background_light (#FFFFFF) and surfaceVariant (#ECECF1) — clears the WCAG
// AA 4.5:1 minimum for normal-size text in both.
val ClaudeTextSecondaryLight = Color(0xFF676767)
val ClaudeTextTertiaryLight = Color(0xFF8E8EA0)
val ClaudeTextPrimaryDark = Color(0xFFECECEC)
val ClaudeTextSecondaryDark = Color(0xFFA6A6A6)
val ClaudeTextTertiaryDark = Color(0xFF6E6E80)

// Semantic accents (red/green/gold) — green is ChatGPT's own signature tone.
val ClaudeAccentRedLight = Color(0xFFD93025)
val ClaudeAccentGreenLight = Color(0xFF10A37F)
val ClaudeAccentGoldLight = Color(0xFFB5811B)
val ClaudeAccentRedDark = Color(0xFFF2685C)
val ClaudeAccentGreenDark = Color(0xFF19C37D)
val ClaudeAccentGoldDark = Color(0xFFE0AC4F)

// Material3 role fills, derived from the same neutral/blue ChatGPT ladder.
val ClaudeM3SecondaryContainerLight = Color(0xFFECECF1)
val ClaudeM3OnSecondaryContainerLight = Color(0xFF2D2D35)
val ClaudeM3TertiaryContainerLight = Color(0xFFF2E4C0)
val ClaudeM3OnTertiaryContainerLight = Color(0xFF47370E)
val ClaudeM3SurfaceVariantLight = Color(0xFFECECF1)
val ClaudeM3ErrorContainerLight = Color(0xFFFAD6D2)
val ClaudeM3OnErrorContainerLight = Color(0xFF5C160F)
val ClaudeM3SurfaceContainerLight = Color(0xFFF7F7F8)
val ClaudeM3SurfaceContainerHighLight = Color(0xFFECECF1)

// Light-mode fill of settings-style rows/cards. Was pure white (same as the
// page background), which made every row/card invisible in light mode; a very
// light neutral gray keeps the flat, borderless look while staying visible.
val ClaudeRowFillLight = Color(0xFFF3F3F5)

val ClaudeM3SecondaryContainerDark = Color(0xFF2A2A2A)
val ClaudeM3OnSecondaryContainerDark = Color(0xFFE4E4E4)
val ClaudeM3TertiaryContainerDark = Color(0xFF473A1C)
val ClaudeM3OnTertiaryContainerDark = Color(0xFFF6DFA9)
val ClaudeM3SurfaceVariantDark = Color(0xFF2B2B2B)
val ClaudeM3ErrorContainerDark = Color(0xFF5C231C)
val ClaudeM3OnErrorContainerDark = Color(0xFFFFDAD2)
val ClaudeM3SurfaceContainerDark = Color(0xFF141414)
val ClaudeM3SurfaceContainerHighDark = Color(0xFF232323)

// ============================================================================
// REDESIGN ("بدي تصميم جميل اجمل من هيك"): a small curated ladder of tones —
// ChatGPT's own green plus a handful of complementary neutrals/blues/warm
// accents — so avatar circles and per-item icon chips read as intentionally
// varied instead of every single one falling back to one flat gray. Fixed
// (not theme-dependent): callers like [avatarColorFor] are plain,
// non-Composable functions used from places with no access to the current
// color scheme (NotificationHelper), so each tone here is picked to sit
// comfortably on both the white light background and the pure-black dark
// one, with white avatar-initial text always staying comfortably legible on
// top.
// ============================================================================
val AvatarPalette = listOf(
    Color(0xFFC96442), // brand orange
    Color(0xFF10A37F), // ChatGPT green
    Color(0xFFB5811B), // gold / amber
    Color(0xFF7E7BB0), // dusty lavender-slate
    Color(0xFFA94E2E), // deep orange
    Color(0xFF3E8E8E), // muted teal
    Color(0xFF8B6F47), // warm clay
    Color(0xFFD93025), // signal red
)
