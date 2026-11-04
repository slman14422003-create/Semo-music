package com.shopmanager.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "زجاجي بالكامل" (full glass, edge-to-edge): the status bar is always
 * fully transparent — paired with
 * `WindowCompat.setDecorFitsSystemWindows(window, false)` in MainActivity,
 * this lets whatever the app itself draws behind it (a screen's own
 * liquid-glass header, or the splash gradient) show straight through,
 * instead of a separately-painted flat OS strip sitting on top of it.
 * That flat strip meeting the glass surface's glossy top edge was exactly
 * what used to read as a hard dividing line right under the status bar
 * icons. `navigationBarColor` is still a parameter so a caller can paint
 * the nav bar area a solid color if some future screen genuinely needs
 * that, but every caller in this app now passes [Color.Transparent] (see
 * MainActivity) for the same reason as the status bar — most visibly so
 * [com.shopmanager.app.ui.common.FloatingBottomNav]'s margins show real
 * page content instead of a separately-painted solid strip sitting behind
 * the floating glass pill. Icon contrast (light vs dark glyphs) is still
 * fully controllable per screen for both bars.
 */
@Composable
fun SetSystemBarsColor(
    navigationBarColor: Color,
    statusBarDarkIcons: Boolean,
    navigationBarDarkIcons: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return

    SideEffect {
        val window = activity.window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        // "الشريط السفلي العائم" fix: on API 29+ Android draws its own
        // semi-opaque black scrim UNDER the app-requested nav bar color
        // whenever that color doesn't have full alpha — this is exactly
        // the "black bar" that showed behind FloatingBottomNav's transparent
        // margins even after navigationBarColor itself was set to
        // Color.Transparent above. Disabling the OS's own contrast
        // enforcement removes that extra scrim entirely, so the true page
        // background (or nothing at all) is what actually shows through,
        // matching how the status bar has behaved the whole time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = statusBarDarkIcons
        controller.isAppearanceLightNavigationBars = navigationBarDarkIcons
    }
}
