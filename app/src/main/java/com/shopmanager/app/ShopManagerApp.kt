package com.shopmanager.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.shopmanager.app.data.notifications.NotificationAction
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.security.AdminPasswordRepository
import com.shopmanager.app.data.security.PinAttemptThrottle
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.admin.AdminPanelScreen
import com.shopmanager.app.ui.dashboard.DashboardScreen
import com.shopmanager.app.ui.debts.DebtsScreen
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.debts.PersonDetailScreen
import com.shopmanager.app.ui.lock.BiometricAuth
import com.shopmanager.app.ui.materials.MaterialCatalogScreen
import com.shopmanager.app.ui.materials.MaterialsScreen
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.notes.NotesScreen
import com.shopmanager.app.ui.notes.NotesViewModel
import com.shopmanager.app.ui.common.AppDrawerContent
import com.shopmanager.app.ui.common.FloatingQuickActions
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.QuickAction
import com.shopmanager.app.ui.common.WebViewScreen
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.settings.SettingsScreen
import com.shopmanager.app.ui.theme.AppThemeMode
import com.shopmanager.app.data.performance.MotionSpecs

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShopManagerApp(
    settings: SettingsRepository,
    onThemeChanged: (AppThemeMode) -> Unit,
    onPerformancePreferenceChanged: (PerformanceMode) -> Unit,
    onRecheckDevicePerformance: () -> Unit = {},
    pendingNotificationAction: NotificationAction? = null,
    onConsumeNotificationAction: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Shared across screens so everyone sees the same live data instead of
    // spinning up duplicate Firestore listeners per screen.
    val debtsViewModel: DebtsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val materialsViewModel: MaterialsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val notesViewModel: NotesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // Hoisted above the NavHost (rather than inside the pager's own
    // composable) so it survives navigating away to Settings/Help and back,
    // and so the bottom bar can read/drive the current page directly.
    val pagerState = rememberPagerState(initialPage = PAGE_DASHBOARD) { 4 }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            when (page) {
                PAGE_MATERIALS -> com.shopmanager.app.data.learning.UsageArea.MATERIALS
                PAGE_NOTES -> com.shopmanager.app.data.learning.UsageArea.NOTES
                else -> null
            }?.let { materialsViewModel.recordUsage(it) }
        }
    }
    // REDESIGN ("زر عميل جديد بجانب الشريط السفلي"): the "+" that used to
    // be each tab's own FloatingActionButton is now a single shared button
    // rendered as part of FloatingBottomNav itself (see its `quickAction`
    // param) so it always sits attached to the pill instead of floating
    // separately over the list. It can't reach into DebtsScreen's own
    // dialog state directly, so tapping it just raises this flag; DebtsScreen
    // watches it and opens its "عميل جديد" dialog when it turns true (see
    // DebtsScreen's `addPersonRequested` parameter).
    var addPersonRequested by remember { mutableStateOf(false) }
    // Same request/handled pattern as addPersonRequested just above, for
    // the ملاحظات هامة tab's own "+" quick action.
    var addNoteRequested by remember { mutableStateOf(false) }
    // "دمج زر حفظ الأسعار مع الشريط السفلي": mirrors addPersonRequested's
    // own request/handled pattern just above, but for the الأسعار tab's
    // save action instead — see MaterialsScreen's matching parameters for
    // the full explanation. `materialsPricesTabActive` and
    // `pricesChangedCount` are reported live by MaterialsScreen (it owns
    // the tab state and the in-progress price edits) so the secondary
    // pill button below only appears, and only shows a changed-count
    // description, while that specific tab is actually on screen.
    var materialsPricesTabActive by remember { mutableStateOf(false) }
    LaunchedEffect(materialsPricesTabActive) {
        if (materialsPricesTabActive) materialsViewModel.recordUsage(com.shopmanager.app.data.learning.UsageArea.PRICES)
    }
    var pricesChangedCount by remember { mutableStateOf(0) }
    var savePricesRequested by remember { mutableStateOf(false) }
    // BUG FIXED ("ترابط بين الديون والملاحظات" كان يغطي الأشخاص فقط):
    // tapping a material-linked note used to only switch to the Materials
    // tab in general (see the old onOpenMaterials below) — nothing pointed
    // at the specific item, unlike a person-linked note which opens that
    // exact customer's page. Same request/handled pattern as
    // addPersonRequested above: NotesScreen raises this with the linked
    // material's name, MaterialsScreen consumes it as its initial search.
    var pendingMaterialHighlight by remember { mutableStateOf<String?>(null) }
    val pagerScope = rememberCoroutineScope()

    // REDESIGN ("قائمة جانبية بدل الشريط السفلي"): tab switching no longer
    // happens through a floating pill glued to the screen's bottom edge —
    // a single hamburger button opens this Material3 side drawer instead,
    // laid out like Claude's own app drawer (see AppDrawerContent.kt: a
    // plain icon+label row per destination, الإعدادات pinned below a
    // divider at the bottom instead of sitting in that same list). The
    // pager/openPager() machinery underneath is unchanged — the drawer
    // just drives the same `pagerState` the old pill used to.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // لوحة المسؤول السرية: PIN-gate state, hoisted up here from
    // DashboardScreen now that the trigger button lives in the drawer
    // instead of Home's own header (see the ROUTE_ADMIN comment above).
    var showAdminPinDialog by remember { mutableStateOf(false) }
    val adminContext = LocalContext.current
    val adminThrottle = remember { PinAttemptThrottle(adminContext, "shop_manager_admin_throttle") }

    // BUG FIXED ("ترابط بين جميع الانميشن"): tapping a bottom-nav tab (or a
    // notification landing on a specific tab) drove the SAME pager as
    // swiping between Home/Debts/Materials/Notes, but through
    // `animateScrollToPage()`'s own default spec — a generic Compose
    // spring, completely unrelated to the deliberately-tuned iOS-style
    // curve (`MotionSpecs.claudeEasing`/`pushSlideSpec` below) every *pushed* screen
    // (Settings, Person Detail, ...) already animates with. The two kinds
    // of navigation in this same app were animating on two unrelated
    // curves/timings, which is exactly what reads as inconsistent even
    // though each one individually looked fine. This uses the identical
    // cubic-bezier shape (and the same LOW-tier "skip it" convention) so a
    // tab switch and a screen push now feel like the same design language
    // instead of two different ones stitched together.
    val isLowTierForPager = LocalPerformanceTier.current == PerformanceTier.LOW
    val pagerTabAnimationSpec: FiniteAnimationSpec<Float> =
        tween(MotionSpecs.pagerMillis().coerceAtLeast(150), easing = MotionSpecs.claudeEasing)

    @OptIn(ExperimentalFoundationApi::class)
    fun openPager(page: Int) {
        navigateTopLevel(navController, ROUTE_MAIN_PAGER)
        pagerScope.launch {
            // LOW: تبديل التبويب فوري — انزلاق صفحتين كاملتين (مع تركيب الصفحة الجديدة في أول
            // إطار) هو أثقل ما في التنقل على المعالج الرسومي القديم.
            if (isLowTierForPager) pagerState.scrollToPage(page)
            else pagerState.animateScrollToPage(page, animationSpec = pagerTabAnimationSpec)
        }
    }

    // Tapping a notification should land on the screen it's about, not just
    // pop a dialog over whatever tab happened to be open. Debts stay on the
    // الديون tab, the shopping-list one jumps to المواد والأسعار.
    LaunchedEffect(pendingNotificationAction) {
        when (pendingNotificationAction) {
            is NotificationAction.DebtPaid, is NotificationAction.NewDebt -> openPager(PAGE_DEBTS)
            is NotificationAction.ShoppingList -> openPager(PAGE_MATERIALS)
            is NotificationAction.NoteReminder -> openPager(PAGE_NOTES)
            null -> {}
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == ROUTE_MAIN_PAGER
    // BUG FIXED ("مساحة سوداء فوق الكيبورد بتغطي المادة والسعر"): the
    // floating pill is a fixed overlay (see the Box below) that never
    // moves when the keyboard opens — it just ends up sitting underneath
    // the keyboard, invisible. But every tab that reads
    // [LocalFloatingBottomNavHeight] (see PricesList/MaterialsList below)
    // was still reserving that exact same clearance at its own bottom
    // regardless, on the assumption the pill was still there to avoid.
    // With the keyboard up that clearance reserves for nothing — it's
    // dead space between the last visible row and the keyboard, which is
    // exactly the black gap being reported. `imeVisible` (real-time,
    // driven by the live `WindowInsets.ime` bottom inset — see the
    // `adjustResize` manifest fix alongside this one) collapses that
    // reservation to 0 the instant the keyboard opens, and the pill
    // itself fades out below rather than sitting there uselessly hidden
    // behind the keyboard. Kept as its own `pillVisible` flag rather than
    // folded into `showBottomBar` itself — NavHost's own bottom-inset
    // toggle just below still needs `showBottomBar` to mean "is this the
    // pager route", independent of the keyboard, or it would start
    // double-padding for the exact same reason `adjustResize` was added
    // for in the first place.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Still named `pillVisible` for the smallest possible diff against the
    // FAB-clearance machinery below ([LocalFloatingBottomNavHeight],
    // [FloatingQuickActions]) — it now also gates the hamburger menu
    // button instead of a floating tab pill, same "hide it on screens
    // that aren't the main pager, or while the keyboard covers it" rule
    // as before.
    val pillVisible = showBottomBar && !imeVisible

    // PERF (low-end tier): a fade still allocates a graphicsLayer and runs
    // a compositor pass every frame of the transition. That's cheap on a
    // Samsung A16-class device but is exactly the kind of per-frame cost
    // that shows up as stutter on a 2GB/quad-core phone like the Redmi
    // A10. On LOW tier screens simply swap with no transition at all.
    val performanceTier = LocalPerformanceTier.current
    val isLowTier = performanceTier == PerformanceTier.LOW
    val isBalancedTier = performanceTier == PerformanceTier.BALANCED

    // ROOT FIX ("الشريط العائم خلفيته لسه بيضاء/سوداء"): a Scaffold(bottomBar
    // = ...) was the actual root cause, not a styling detail inside
    // FloatingBottomNav. Two things about Scaffold made a flat white/black
    // rectangle unavoidable no matter what color/transparency was tried on
    // the bar itself:
    //  1) Scaffold paints its own `containerColor` (default
    //     colorScheme.background) behind whatever is in the bottomBar slot.
    //  2) Scaffold shrinks NavHost's content to stop short of that slot
    //     (via the `padding` it hands down), so there was never any real
    //     page content behind the bar's transparent side margins either —
    //     just empty space over that flat Scaffold color.
    // Both of those were true regardless of FloatingBottomNav's own
    // background, so nothing changed there could ever fix it. This now
    // uses a plain Box instead of Scaffold: NavHost fills the *entire*
    // screen (see the pager tabs' own contentWindowInsets below — Dashboard/
    // Debts/Materials already reserve their own bottom safe-area space, so
    // no double-padding), and FloatingBottomNav is layered on top as a true
    // overlay via Modifier.align(Alignment.BottomCenter). The margins
    // around the pill are now genuinely transparent over the live page
    // (list rows, cards) scrolling underneath — never a separately-painted
    // rectangle.
    // BUG FIXED ("زر عميل جديد/مادة جديدة صار تحت الشريط"): a direct side
    // effect of the overlay fix above. Previously the outer Scaffold's
    // `padding` shrank NavHost so screens never even extended into the
    // pill's area — a screen's own FloatingActionButton (bottom-end of ITS
    // OWN Scaffold) naturally landed above the pill with no extra work.
    // Now that NavHost fills the true screen height, each tab's FAB anchors
    // to the *real* bottom edge same as the pill does — same strip, so the
    // pill (drawn after, on top) covers most of it, leaving only the
    // sliver that peeks out past the pill's own width.
    // FIX: measure the pill's actual rendered height right where it's
    // drawn (`onSizeChanged` below — real layout size, not a guessed
    // constant) and thread it down via [LocalFloatingBottomNavHeight] so
    // DebtsScreen/MaterialsScreen (the two tabs with a FAB) can pad their
    // FAB, and every tab's list, clear of it by that exact amount.
    val density = LocalDensity.current
    var floatingNavHeight by remember { mutableStateOf(0.dp) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only swipe-openable from the main pager tabs — same screens the
        // old pill only ever showed on (`showBottomBar`) — so a swipe on
        // Settings/PersonDetail/etc. doesn't fight that screen's own back
        // gesture.
        gesturesEnabled = showBottomBar,
        drawerContent = {
            AppDrawerContent(
                selectedPage = pagerState.currentPage,
                onSelectPage = { page ->
                    openPager(page)
                    drawerScope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    navController.safeNavigate(ROUTE_SETTINGS)
                    drawerScope.launch { drawerState.close() }
                },
                onOpenMissingFinder = {
                    navController.safeNavigate(ROUTE_MISSING_FINDER)
                    drawerScope.launch { drawerState.close() }
                },
                onOpenAdmin = {
                    drawerScope.launch { drawerState.close() }
                    // البصمة/الوجه أولاً إن كانت متاحة على الجهاز؛ وإلا (أو عند
                    // اختيار "كلمة المرور" أو فشل النظام) يظهر مربع كلمة المرور
                    // القديم بنفس قفل المحاولات.
                    val bioActivity = BiometricAuth.findActivity(adminContext)
                    if (bioActivity != null && BiometricAuth.isAvailable(adminContext) && !adminThrottle.isLocked()) {
                        BiometricAuth.prompt(
                            activity = bioActivity,
                            title = "دخول لوحة المطوّر",
                            subtitle = "أكّد هويتك للمتابعة",
                            negativeText = "استخدام كلمة المرور",
                            onSuccess = {
                                adminThrottle.registerSuccess()
                                navController.safeNavigate(ROUTE_ADMIN)
                            },
                            onFailure = { showAdminPinDialog = true }
                        )
                    } else {
                        showAdminPinDialog = true
                    }
                }
            )
        }
    ) {
    Box(
        Modifier
            .fillMaxSize()
    ) {
        // See the BUG FIXED note below (inside NavHost's transition params)
        // for why these are shaped the way they are — declared here, above
        // NavHost, since a function-call argument list can only contain
        // `name = value` arguments, not local `val` statements.
        // CLAUDE.AI-STYLE MOTION: shares [MotionSpecs.claudeEasing] — the
        // same no-overshoot curve every other transition in the app now
        // uses — instead of a separately-tuned iOS curve.
        val navMs = MotionSpecs.navMillis()
        val pushSlideSpec: FiniteAnimationSpec<IntOffset> = tween(navMs, easing = MotionSpecs.claudeEasing)
        val navFadeSpec: FiniteAnimationSpec<Float> = tween(navMs, easing = MotionSpecs.claudeEasing)
        // REPLACED THE OLD ALPHA-DIM ("الانميشن غير جميل / بدي ياه متكامل
        // وبلا تقطيع"): the covered screen used to dim via fadeOut/fadeIn
        // down to 72% alpha while it translated. Animating the *alpha* of a
        // whole subtree full of Material3 ElevatedCards means every card's
        // shadow gets re-composited at a shifting opacity on top of
        // whatever's underneath it, frame by frame — on a weaker GPU that
        // shows up as visible banding/seams right where the shadows are,
        // reading as "تقطيع" (choppy) rather than one solid, cohesive
        // sheet of UI sliding as a unit. Scale reads as pure depth instead
        // (the same "recede a step back" cue iOS/Claude modals already use
        // elsewhere in this app) with nothing to re-blend, so it stays a
        // single flat layer moving — smooth on any GPU, and the *same*
        // slide+scale language as everything else (see MotionSpecs'
        // popInSpring/quickSpring), which is what makes it read as one
        // integrated motion system instead of a one-off for this screen.
        val pushScaleSpec: FiniteAnimationSpec<Float> = tween(navMs, easing = MotionSpecs.claudeEasing)
        CompositionLocalProvider(
            LocalFloatingBottomNavHeight provides if (pillVisible) floatingNavHeight else 0.dp
        ) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_MAIN_PAGER,
            // Horizontal safe-area (cutouts/rounded corners) always
            // applies. Bottom safe-area only applies here when there's no
            // floating nav on screen (Settings, person detail, etc.) —
            // when the pager IS showing, Dashboard/Debts/Materials each
            // already reserve their own bottom inset internally (see their
            // own `contentWindowInsets`), and reserving it a second time
            // here is exactly what used to create the empty flat-colored
            // strip behind the bar in the first place.
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .then(
                    if (!showBottomBar) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    } else {
                        Modifier
                    }
                ),
            // التنقل: NavHost نفسه يتولى الحركة (دفع وسحب) والرجوع التنبؤي — سحبة الحافة
            // وزر الرجوع يشغّلان نفس الانتقال تماماً (عكس حركة الدخول):
            //  - دفع (enter/exit): الشاشة الجديدة تنزلق بكامل العرض من اليمين، والشاشة
            //    المغطّاة تنزلق ثلث عرضها لليسار وتُصغَّر إلى 94% (انظر pushScaleSpec).
            //  - سحب (popEnter/popExit): العكس تماماً بنفس pushSlideSpec/pushScaleSpec
            //    (المدة والمنحنى نفسهما، فتتطابق السرعة وليس الاتجاه فقط).
            // ثلاثة مستويات (انظر MotionSpecs.navMillis):
            //  - LOW: تلاشٍ متقاطع قصير فقط، بلا انزلاق ولا scale.
            //  - BALANCED: انزلاق (transform فقط) بلا scale ولا تلاشٍ للشاشة المغطّاة
            //    — طبقة واحدة مسطّحة تتحرك، فلا تُعاد كتابة ظلال/ألوان كل إطار.
            //  - STANDARD: انزلاق + تصغير الشاشة المغطّاة إلى 94% (أغنى عمق بصري).
            enterTransition = {
                when {
                    isLowTier -> fadeIn(navFadeSpec)
                    else -> slideInHorizontally(pushSlideSpec) { fullWidth -> fullWidth }
                }
            },
            exitTransition = {
                when {
                    // LOW: طبقة واحدة فقط تتحرك (الشاشة الجديدة تتلاشى فوق القديمة الثابتة)،
                    // بدل تلاشٍ متقاطع لشاشتين كاملتين = نصف عمل المعالج الرسومي على 40Hz.
                    isLowTier -> ExitTransition.None
                    isBalancedTier -> slideOutHorizontally(pushSlideSpec) { fullWidth -> -fullWidth / 4 }
                    else -> slideOutHorizontally(pushSlideSpec) { fullWidth -> -fullWidth / 3 } +
                        scaleOut(pushScaleSpec, targetScale = 0.94f)
                }
            },
            popEnterTransition = {
                when {
                    isLowTier -> EnterTransition.None
                    isBalancedTier -> slideInHorizontally(pushSlideSpec) { fullWidth -> -fullWidth / 4 }
                    else -> slideInHorizontally(pushSlideSpec) { fullWidth -> -fullWidth / 3 } +
                        scaleIn(pushScaleSpec, initialScale = 0.94f)
                }
            },
            popExitTransition = {
                when {
                    isLowTier -> fadeOut(navFadeSpec)
                    else -> slideOutHorizontally(pushSlideSpec) { fullWidth -> fullWidth }
                }
            }
        ) {
            composable(ROUTE_MAIN_PAGER) {
                // One shared surface for Home, Debts, Materials, and Notes —
                // `userScrollEnabled = false` below means the user can never
                // drag between them directly; every switch goes through
                // `openPager()` (drawer tap / quick action / notification),
                // which still animates this exact pager with the same
                // tuned curve (`pagerTabAnimationSpec`). Each row's own
                // delete action stays a tap (see DeleteIconButton) rather
                // than a horizontal swipe either way.
                //
                // PERF: this used to wrap every page in a per-frame
                // graphicsLayer that scaled and faded it while dragging.
                // With list-heavy screens full of ElevatedCards (each one
                // already its own shadow-casting layer), animating a
                // scale/alpha transform across the whole subtree on every
                // scroll frame was expensive. Now that the pager only ever
                // moves via `animateScrollToPage()` (see `userScrollEnabled`
                // below) that per-frame drag transform has no reason to
                // exist at all — the programmatic tab-switch animation is
                // driven entirely by `pagerTabAnimationSpec` instead.
                HorizontalPager(
                    state = pagerState,
                    // PERF (تنقّل أنعم): compose الصفحة المجاورة (يمين/يسار)
                    // مسبقًا على الأجهزة العادية/القوية بدل انتظار أول سحبة
                    // إليها فعليًا - أول سحبة للصفحة التالية هيك ما عندها
                    // كلفة "compose لأول مرة" وبتبين أنعم فورًا، بنفس روح
                    // إزالة الـ graphicsLayer لكل صفحة فوق (توفير كلفة
                    // مكانها بمكان تاني). على أجهزة الأداء الضعيف نضل
                    // عالافتراضي (0) لتفادي أي عبء compose/ذاكرة إضافي مو
                    // ضروري - نفس منطق "دعم الوضعين" اللي يحدد isLowTierForPager
                    // نفسها فوق.
                    beyondViewportPageCount = if (isLowTierForPager) 0 else 1,
                    // REMOVED ("ميزة التنقل بين الشاشات الغيها"): no
                    // finger-drag between tabs anymore — see the class-level
                    // FIX/REMOVED comment above this composable() block.
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    // الأداء القوي فقط: عمق بصري أثناء تبديل التبويب — الصفحة المغادِرة/القادمة
                    // تصغر قليلاً وتخفت. كله داخل graphicsLayer (مرحلة الرسم)، وعند السكون
                    // offset = 0 فلا طبقة إضافية. المتوازن والمنخفض: انزلاق خام بلا أي طبقات.
                    val pageFx = if (performanceTier == PerformanceTier.STANDARD) {
                        Modifier.graphicsLayer {
                            val off = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                .let { if (it < 0f) -it else it }.coerceIn(0f, 1f)
                            val sc = 1f - 0.06f * off
                            scaleX = sc
                            scaleY = sc
                            alpha = 1f - 0.5f * off
                        }
                    } else Modifier
                    Box(pageFx.fillMaxSize()) {
                    when (page) {
                        PAGE_DASHBOARD -> DashboardScreen(
                            debtsViewModel = debtsViewModel,
                            materialsViewModel = materialsViewModel,
                            onNavigateToDebts = { openPager(PAGE_DEBTS) },
                            onNavigateToMaterials = { openPager(PAGE_MATERIALS) },
                            onOpenMissingFinder = { navController.safeNavigate(ROUTE_MISSING_FINDER) },
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                            notesViewModel = notesViewModel,
                            onOpenPerson = { personId -> openPager(PAGE_DEBTS); navController.safeNavigate("personDetail/$personId") },
                            onOpenMaterial = { materialName -> pendingMaterialHighlight = materialName; openPager(PAGE_MATERIALS) },
                            onOpenNotes = { openPager(PAGE_NOTES) },
                            onOpenSettings = { navController.safeNavigate(ROUTE_SETTINGS) }
                        )
                        PAGE_DEBTS -> DebtsScreen(
                            viewModel = debtsViewModel,
                            onOpenPerson = { personId -> navController.safeNavigate("personDetail/$personId") },
                            addPersonRequested = addPersonRequested,
                            onAddPersonRequestHandled = { addPersonRequested = false },
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                            onOpenActivityLog = { navController.safeNavigate(ROUTE_DEBT_ACTIVITY) }
                        )
                        PAGE_MATERIALS -> MaterialsScreen(
                            viewModel = materialsViewModel,
                            onAddNew = { navController.safeNavigate(ROUTE_MATERIAL_CATALOG) },
                            onPricesTabActiveChanged = { materialsPricesTabActive = it },
                            onPricesChangedCountChanged = { pricesChangedCount = it },
                            savePricesRequested = savePricesRequested,
                            onSavePricesRequestHandled = { savePricesRequested = false },
                            initialSearchQuery = pendingMaterialHighlight,
                            onInitialSearchConsumed = { pendingMaterialHighlight = null },
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                        )
                        else -> NotesScreen(
                            viewModel = notesViewModel,
                            persons = debtsViewModel.uiState.collectAsStateWithLifecycle().value.persons,
                            materials = materialsViewModel.uiState.collectAsStateWithLifecycle().value.materials,
                            onOpenPerson = { personId -> openPager(PAGE_DEBTS); navController.safeNavigate("personDetail/$personId") },
                            onOpenMaterials = { materialName -> pendingMaterialHighlight = materialName; openPager(PAGE_MATERIALS) },
                            addNoteRequested = addNoteRequested,
                            onAddNoteRequestHandled = { addNoteRequested = false },
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                        )
                    }
                    }
                }
            }
            composable(ROUTE_MATERIAL_CATALOG) {
                MaterialCatalogScreen(
                    viewModel = materialsViewModel,
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable(ROUTE_DEBT_ACTIVITY) {
                com.shopmanager.app.ui.debts.DebtActivityScreen(
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable(ROUTE_MISSING_FINDER) {
                com.shopmanager.app.ui.materials.MissingFinderScreen(
                    viewModel = materialsViewModel,
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onBack = { navController.safePopBackStack() },
                    onThemeChanged = onThemeChanged,
                    onPerformancePreferenceChanged = onPerformancePreferenceChanged,
                    onRecheckDevicePerformance = onRecheckDevicePerformance,
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel,
                    onOpenHelp = { navController.safeNavigate(ROUTE_HELP) },
                    onOpenPrivacyPolicy = { navController.safeNavigate(ROUTE_PRIVACY) },
                    onOpenAbout = { navController.safeNavigate(ROUTE_ABOUT) }
                )
            }
            composable(ROUTE_ABOUT) {
                com.shopmanager.app.ui.settings.AboutScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenPrivacyPolicy = { navController.safeNavigate(ROUTE_PRIVACY) },
                    onOpenHelp = { navController.safeNavigate(ROUTE_HELP) }
                )
            }
            composable(ROUTE_ADMIN) {
                AdminPanelScreen(
                    onBack = { navController.safePopBackStack() },
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel,
                    notesViewModel = notesViewModel
                )
            }
            composable(ROUTE_HELP) {
                WebViewScreen(
                    url = "file:///android_asset/help.html",
                    title = "دليل الاستخدام",
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable(ROUTE_PRIVACY) {
                WebViewScreen(
                    url = "file:///android_asset/privacy.html",
                    title = "سياسة الخصوصية",
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable(
                ROUTE_PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.StringType })
            ) { entry ->
                val personId = entry.arguments?.getString("personId")
                val personsState = debtsViewModel.uiState.collectAsStateWithLifecycle().value
                val person = personsState.persons.find { it.id == personId }
                // STABILITY FIX: if this customer disappears while their page
                // is open (deleted from another device, or an unknown id from
                // a stale notification) the screen used to render NOTHING - a
                // blank page. Once the first real data has loaded and the
                // customer still isn't there, leave the page instead.
                LaunchedEffect(person == null, personsState.isLoading) {
                    if (person == null && !personsState.isLoading) navController.safePopBackStack()
                }
                if (person != null) {
                    PersonDetailScreen(
                        person = person,
                        viewModel = debtsViewModel,
                        // "ترابط بين الديون والملاحظات": نفس نسخة NotesViewModel
                        // المشتركة اللي يستخدمها تبويب "ملاحظات هامة" (مو نسخة
                        // جديدة بـ viewModel() الافتراضي) - عشان الملاحظات
                        // المعروضة هون تضل نفس البيانات الحية، وإضافة/تعديل
                        // ملاحظة من هالشاشة ينعكس فورًا بتبويب الملاحظات وبالعكس.
                        notesViewModel = notesViewModel,
                        onBack = { navController.safePopBackStack() }
                    )
                }
            }
        }
        } // CompositionLocalProvider

        // Drawn AFTER (so visually on top of) NavHost above — a real
        // overlay, not a layout slot with its own painted background.
        if (pillVisible) {
            // BUG FIXED ("شكل الايقونة فوق الكلمة/التصميم متراكب"): the
            // hamburger used to be this exact floating glass circle,
            // pinned to Alignment.TopStart over the *entire* NavHost — in
            // this app's forced-RTL layout TopStart resolves to the
            // top-RIGHT corner, which is exactly where every tab's own
            // header already places its title text (DashboardHeader's
            // "إدارة المحل", MaterialsHeader's "المواد والأسعار", ...).
            // The floating circle and the header's own text were two
            // independent layers drawn in the same corner, so they
            // visually collided instead of one giving way to the other.
            // Claude's own top bar never floats a control over live
            // content like that — its hamburger is a real element laid
            // out *inside* the bar itself, taking up its own space so
            // nothing else can ever occupy that spot. Each screen now
            // draws its own leading hamburger button inline in its own
            // header/TopAppBar (see DashboardHeader, DebtsScreen's
            // TopAppBar `navigationIcon`, MaterialsHeader, NotesScreen's
            // TopAppBar `navigationIcon`) via the `onOpenDrawer` callback
            // threaded down from here — so this floating overlay button
            // is gone entirely; nothing replaces it at this layer.

            // REDESIGN ("زر ال+ لازم تشيلة لان في فوق زر"): الديون and
            // المواد both now have their own wide, on-screen "عميل جديد" /
            // "مادة جديدة" button right in the list (see DebtsScreen /
            // MaterialsScreen — wired to the same `addPersonRequested` /
            // ROUTE_MATERIAL_CATALOG actions this floating button used to
            // trigger), so this shared floating "+" would just be a second,
            // redundant control for the exact same action on those two
            // pages. It now only shows on الملاحظات, which has no on-screen
            // add button of its own yet.
            val quickAction = when (pagerState.currentPage) {
                PAGE_NOTES -> QuickAction(
                    icon = Icons.Default.Add,
                    contentDescription = "ملاحظة جديدة",
                    onClick = { addNoteRequested = true }
                )
                else -> null
            }
            // "من الجهة اليسرى حسب الصورة": rendered via `secondaryAction`,
            // the opposite end of the same pill row from `quickAction`
            // above (see FloatingBottomNav's own doc comment on that
            // param) — only while the المواد والأسعار page's الأسعار tab
            // is actually the one showing, same as `quickAction` itself is
            // page-scoped. Label mirrors the old full-width button's own
            // text ("حفظ N من الأسعار" once something's changed, "حفظ كل
            // الأسعار" otherwise); tapping it just raises
            // `savePricesRequested`, which MaterialsScreen watches and
            // clears once the save actually runs.
            val secondaryAction = if (pagerState.currentPage == PAGE_MATERIALS && materialsPricesTabActive) {
                QuickAction(
                    icon = Icons.Default.Save,
                    contentDescription = if (pricesChangedCount > 0) "حفظ $pricesChangedCount من الأسعار" else "حفظ كل الأسعار",
                    onClick = { savePricesRequested = true }
                )
            } else null
            FloatingQuickActions(
                quickAction = quickAction,
                secondaryAction = secondaryAction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Real measured layout height (margins included, since
                    // this size is taken at FloatingQuickActions' own root
                    // — see its Box in FloatingBottomNav.kt), converted
                    // from px to dp with the current screen density. This
                    // is what LocalFloatingBottomNavHeight above actually
                    // reflects.
                    .onSizeChanged { size ->
                        floatingNavHeight = with(density) { size.height.toDp() }
                    }
            )
        }
    }
    } // ModalNavigationDrawer

    // لوحة المسؤول السرية: PIN gate, triggered from the admin button in the
    // side drawer (see AppDrawerContent's onOpenAdmin above). Rendered as a
    // sibling of the drawer's Box, same reasoning as the notification
    // dialog just below — a dialog is its own layer, not part of that
    // layout, and this needs to be reachable regardless of which tab is
    // currently open underneath it.
    if (showAdminPinDialog) {
        AdminPinDialog(
            throttle = adminThrottle,
            onDismiss = { showAdminPinDialog = false },
            onVerify = { entered ->
                if (adminThrottle.isLocked()) {
                    AdminPasswordRepository.Outcome.Denied
                } else {
                    val outcome = AdminPasswordRepository.verify(entered)
                    when (outcome) {
                        is AdminPasswordRepository.Outcome.Granted -> {
                            adminThrottle.registerSuccess()
                            showAdminPinDialog = false
                            navController.safeNavigate(ROUTE_ADMIN)
                        }
                        is AdminPasswordRepository.Outcome.Denied -> adminThrottle.registerFailure()
                        // NotConfigured / Unavailable: ليست محاولة خاطئة — لا تُحتسب ضد المطوّر.
                        else -> Unit
                    }
                    outcome
                }
            }
        )
    }

    // The confirmation dialog a tapped notification opens the app to show
    // ("تم تسديد الدين", "عميل جديد", "قائمة المشتريات"). Rendered as a
    // sibling of the Box above (an AlertDialog is its own system window,
    // not part of that layout) so it appears on top regardless of which
    // tab openPager() just switched to.
    pendingNotificationAction?.let { action ->
        NotificationActionDialog(action = action, onConsume = onConsumeNotificationAction)
    }
}

@Composable
internal fun NotificationActionDialog(action: NotificationAction, onConsume: () -> Unit) {
    when (action) {
        is NotificationAction.DebtPaid -> GlassAlertDialog(
            onDismissRequest = onConsume,
            title = { Text("✅ تم سداد دين") },
            text = { Text("${action.personName} وفى ${action.amount} ${action.currency}") },
            // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
            // PersonEditDialog.kt's doc comment.
            confirmButton = { TextButton(shape = RectangleShape, onClick = onConsume) { Text("موافق") } }
        )
        is NotificationAction.NewDebt -> GlassAlertDialog(
            onDismissRequest = onConsume,
            title = { Text("💰 عميل جديد بالديون") },
            text = { Text("${action.personName} — ${action.amount} ${action.currency}") },
            confirmButton = { TextButton(shape = RectangleShape, onClick = onConsume) { Text("موافق") } }
        )
        is NotificationAction.ShoppingList -> GlassAlertDialog(
            onDismissRequest = onConsume,
            title = { Text("🛒 قائمة المشتريات") },
            text = {
                Text(
                    if (action.materialNames.isEmpty()) "لا توجد مواد ناقصة حالياً"
                    else action.materialNames.joinToString("، ")
                )
            },
            confirmButton = { TextButton(shape = RectangleShape, onClick = onConsume) { Text("موافق") } }
        )
        // Tapping a note's reminder just needs to land on the ملاحظات
        // هامة tab (already handled by the LaunchedEffect above) - the
        // note itself is right there in the list, so a confirmation
        // dialog on top of it would just be a redundant extra tap.
        is NotificationAction.NoteReminder -> {
            LaunchedEffect(action) { onConsume() }
        }
    }
}
