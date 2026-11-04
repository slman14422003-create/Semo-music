package com.shopmanager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Claude-app-inspired type scale: big screen titles ("Settings"-style) use a
 * serif display face — the same touch the Claude Android app uses to give
 * its headers a calmer, editorial feel instead of the tech-default sans —
 * while every smaller/body role stays on the platform sans so paragraphs,
 * labels, and buttons remain fast to read. Large headings keep a touch of
 * negative letter-spacing so they stay dense rather than loose; small labels
 * get slightly *positive* spacing so they stay legible at 11–12sp.
 */
private val ClaudeSerif = FontFamily.Serif

private val ClaudeSans = FontFamily.Default

// COMPLETENESS FIX: only 9 of Material3's 15 type roles were defined here.
// Any screen (present or future) that reaches for displayLarge/Medium/Small,
// headlineLarge/Medium, or bodySmall would silently fall back to Compose's
// default Roboto scale — breaking the serif-headline / sans-body identity
// everywhere else in the app without any visible warning. The six roles
// below fill that gap, following the same rule already established here:
// large, prominent roles stay on the serif face with a touch of negative
// tracking; body/label roles stay on the platform sans with tracking
// trending positive as size shrinks. headlineSmall/titleLarge and everything
// smaller are untouched — those are already tuned and in active use.
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.4).sp),
    displayMedium = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 47.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = ClaudeSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)
