package com.shopmanager.app

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.shopmanager.app.data.security.AdminPasswordRepository
import com.shopmanager.app.data.security.PinAttemptThrottle
import com.shopmanager.app.ui.dashboard.DashboardScreen
import com.shopmanager.app.ui.common.AppDrawerContent

// FIX: Home/Debts/Materials/Notes all live as pages of one HorizontalPager
// so every destination shares the exact same page-transition animation and
// state-preservation machinery instead of each screen wiring its own.
// REMOVED ("ميزة التنقل بين الشاشات الغيها"): a flick right/left used to
// move between pages directly, the same as tapping a tab in the drawer —
// two different gestures doing the same navigation, and a stray drag over
// any tab's own content (a long list, a swipe-to-delete row, ...) could
// switch the whole screen by accident. Navigation between the four tabs
// now only ever happens through the drawer or a quick-action tap
// (`openPager()` below); the pager itself is scrolled purely
// programmatically, never by the user's finger — see `userScrollEnabled`
// on the HorizontalPager further down.
internal const val ROUTE_MAIN_PAGER = "mainPager"

internal const val PAGE_DASHBOARD = 0

internal const val PAGE_DEBTS = 1

internal const val PAGE_MATERIALS = 2

// ملاحظات هامة (Important Notes) - the 4th main tab, added alongside
// Home/Debts/Materials in the same HorizontalPager/FloatingBottomNav so it
// shares the exact same tab-switch animation described above for free.
internal const val PAGE_NOTES = 3

internal const val ROUTE_SETTINGS = "settings"

// لوحة المسؤول السرية: reached only via the small admin-panel button
// pinned next to الإعدادات at the bottom of the side drawer (see
// AppDrawerContent) + the PIN dialog it opens (AdminPinDialog below).
// MOVED ("انقل ايقونة المسؤول الى المنيو الى جانب الاعدادات"): this used
// to be a hidden button on DashboardScreen's own header, reachable only
// from the Home tab. The trigger — and the PIN-gate state/dialog that
// guards it — now live here at the same level as the drawer itself, so
// لوحة المسؤول is reachable from the drawer regardless of which tab is
// open, exactly like الإعدادات already is.
internal const val ROUTE_ADMIN = "adminPanel"

// SECURITY: كلمة مرور لوحة المسؤول لم تعد مكتوبة بالكود — محفوظة في Firebase فقط
// (Firestore ← config ← admin ← password)، وتُغيَّر وتُقرأ من Firebase Console.
// راجع AdminPasswordRepository. المحاولات تبقى مُقيَّدة عبر PinAttemptThrottle
// (انظر AdminPinDialog تحت) فلا يمكن تخمينها من لوحة المفاتيح.
internal const val ROUTE_MATERIAL_CATALOG = "materialCatalog"

internal const val ROUTE_MISSING_FINDER = "missingFinder"

internal const val ROUTE_HELP = "help"

internal const val ROUTE_DEBT_ACTIVITY = "debtActivity"

internal const val ROUTE_PRIVACY = "privacy"

internal const val ROUTE_ABOUT = "about"

internal const val ROUTE_PERSON_DETAIL = "personDetail/{personId}"
