package com.shopmanager.app

import androidx.compose.foundation.layout.only
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost

/**
 * STABILITY FIX: a fast double-tap on a back arrow (or back arrow + system
 * back together) used to call popBackStack() twice. The second call popped
 * the start destination (the main pager) off the stack and left the NavHost
 * empty - a blank screen with no way out but killing the app. This only pops
 * while there is still a screen below the current one.
 */
internal fun androidx.navigation.NavController.safePopBackStack() {
    if (previousBackStackEntry != null) popBackStack()
}

// حاجز التنقل المزدوج (مأخوذ من تطبيق الملاحظات: SmoothActivity.isDuplicateNavigation): ضغطتان
// سريعتان على نفس الصف/الزر كانتا تدفعان الشاشة مرتين فتتراكم نسختان وينقطع الانتقال. الآن تُتجاهل
// الثانية إن جاءت خلال 550ms لنفس الوجهة أو أثناء تشغيل انتقال سابق (المدخل الحالي ليس RESUMED).
internal var lastNavRoute = ""

internal var lastNavAt = 0L

internal fun androidx.navigation.NavController.safeNavigate(route: String) {
    val now = android.os.SystemClock.uptimeMillis()
    if (route == lastNavRoute && now - lastNavAt < 550L) return
    val state = currentBackStackEntry?.lifecycle?.currentState
    if (state != null && state != androidx.lifecycle.Lifecycle.State.RESUMED) return
    lastNavRoute = route
    lastNavAt = now
    navigate(route)
}

internal fun navigateTopLevel(navController: androidx.navigation.NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
