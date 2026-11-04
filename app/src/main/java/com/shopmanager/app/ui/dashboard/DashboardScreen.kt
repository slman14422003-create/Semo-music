package com.shopmanager.app.ui.dashboard

import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace20
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.data.learning.SuggestionKind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudOff
import com.shopmanager.app.data.backup.BackupManager
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.performance.listItemEntrance
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.sync.SyncConnectivityObserver
import com.shopmanager.app.data.sync.SyncRetry
import com.shopmanager.app.data.sync.SyncStatusStore
import com.shopmanager.app.widget.WidgetCache
import com.shopmanager.app.ui.common.AppCapsuleSearchField
import com.shopmanager.app.ui.common.PhoneUtils
import com.shopmanager.app.ui.common.SkeletonCard
import com.shopmanager.app.ui.common.rememberSmartSearch
import com.shopmanager.app.ui.notes.NotesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.LocalSemanticColors
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.shopmanager.app.data.performance.sheenSweep

private data class ActivityRow(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

/**
 * REDESIGN ("تصميم صباح الخير أكثر تناسق مع الواجهة"): this used to embed a
 * raw Unicode emoji (☀️🌙👋🌇) straight inside the greeting string. Every
 * other glyph in this exact header — the settings gear, the admin shield —
 * is a monochrome Material icon tinted to match the glass panel; an emoji
 * next to them renders as a small colorful, OS-drawn picture that doesn't
 * belong to that same visual language at all (and looks different
 * device-to-device/OS-to-OS, unlike a vector icon). [TimeGreeting] now
 * pairs the text with a matching outline icon instead, drawn the same
 * tinted-white way as the rest of the header (see its usage in
 * [DashboardHeader]) — so it reads as one cohesive design instead of an
 * app icon font colliding with an emoji font.
 */
private data class TimeGreeting(val text: String, val icon: ImageVector)

private fun timeBasedGreeting(): TimeGreeting {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> TimeGreeting("سهرانين لهلق؟", Icons.Filled.NightsStay)
        hour < 12 -> TimeGreeting("صباح الخير", Icons.Filled.WbSunny)
        hour < 17 -> TimeGreeting("أهلاً بك", Icons.Filled.WavingHand)
        hour < 21 -> TimeGreeting("مساء الخير", Icons.Filled.WbTwilight)
        else -> TimeGreeting("مساء النور", Icons.Filled.Bedtime)
    }
}

private data class BackupState(val loaded: Boolean = false, val at: Long? = null)

/** Unified list-item motion: placement/fade specs from [MotionSpecs] plus a staggered entrance. */
@Composable
private fun LazyItemScope.cardFx(index: Int): Modifier {
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    return Modifier
        .animateItem(
            fadeInSpec = if (low) null else MotionSpecs.enterSpec<Float>(),
            placementSpec = MotionSpecs.itemPlacement(),
            fadeOutSpec = if (low) null else MotionSpecs.listItemFadeOut()
        )
        .listItemEntrance(index)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    debtsViewModel: DebtsViewModel,
    materialsViewModel: MaterialsViewModel,
    onNavigateToDebts: () -> Unit = {},
    onNavigateToMaterials: () -> Unit = {},
    onOpenMissingFinder: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    notesViewModel: NotesViewModel? = null,
    onOpenPerson: (String) -> Unit = {},
    onOpenMaterial: (String) -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeSuggestions by materialsViewModel.homeSuggestions.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) materialsViewModel.onAppEntered()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val debtsState by debtsViewModel.uiState.collectAsStateWithLifecycle()
    val materialsState by materialsViewModel.uiState.collectAsStateWithLifecycle()
    val debtsRefreshing by debtsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val materialsRefreshing by materialsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    // 12-hour clock (was HH:mm/24h) — "a" renders as ص/م in Arabic locale.
    val df = remember { SimpleDateFormat("d MMM، h:mm a", Locale("ar")) }

    // Every material in the list is, by definition, a shortage the shop
    // needs to buy - it's a live shopping list, not a stock count.
    val shortages = remember(materialsState.materials) { materialsState.materials }
    // UNIFIED ON CLAUDE'S DESIGN: this used to be a fixed, theme-independent
    // generic amber (WarningAmber, #F59E0B) — not Claude's own warning tone
    // in either theme. LocalSemanticColors.current.warning already resolves
    // to Claude's own gold accent (ClaudeAccentGoldLight/Dark, correctly
    // matching each theme — see semanticColorsFor in Palette.kt), so the
    // market-shortage badges below now match every other warning-colored
    // element in the app.
    val marketAccent = LocalSemanticColors.current.warning
    val topDebtors = remember(debtsState.persons) {
        debtsState.persons.filter { it.amount > 0 }.sortedByDescending { it.amount }.take(5)
    }
    val isLoading = debtsState.isLoading || materialsState.isLoading
    val importantCount = remember(shortages) { shortages.count { it.important } }
    val currency = AppSettingsState.currencySymbol
    val semantic = LocalSemanticColors.current

    LaunchedEffect(isLoading, debtsState.totalAmount, shortages.size, currency) {
        if (!isLoading) WidgetCache.save(context, debtsState.totalAmount, shortages.size, currency)
    }

    // Heavy work (overdue scan, activity summaries, file timestamps) runs off the main thread.
    val overdueDays = remember { SettingsRepository(context).overdueDays }
    val personsNow = debtsState.persons
    val debtsNow = debtsState.debts
    val overdue by produceState(emptyList<OverdueEntry>(), personsNow, debtsNow) {
        value = withContext(Dispatchers.Default) {
            computeOverdue(personsNow, debtsNow, overdueDays, System.currentTimeMillis())
        }
    }

    val activityEntries by remember { DebtActivityLog.listen(context).catch { emit(emptyList()) } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val todaySummary by produceState(DaySummary(), activityEntries) {
        value = withContext(Dispatchers.Default) { summarizeToday(activityEntries, System.currentTimeMillis()) }
    }
    val weekTrend by produceState(WeekTrend(), activityEntries) {
        value = withContext(Dispatchers.Default) { computeWeekTrend(activityEntries, System.currentTimeMillis()) }
    }

    val backupState by produceState(BackupState(), debtsNow.size, materialsState.materials.size) {
        value = withContext(Dispatchers.IO) { BackupState(true, BackupManager.lastBackupAt(context)) }
    }
    var backupHidden by rememberSaveable { mutableStateOf(false) }
    val backupStale = backupState.loaded && !backupHidden &&
        (personsNow.isNotEmpty() || materialsState.materials.isNotEmpty()) &&
        (backupState.at == null || System.currentTimeMillis() - (backupState.at ?: 0L) > 3 * DAY_MS)
    val backupBody = remember(backupState) {
        val at = backupState.at
        if (at == null) "لا توجد نسخة احتياطية بعد"
        else "آخر نسخة قبل ${((System.currentTimeMillis() - at) / DAY_MS).toInt()} أيام"
    }

    val online by remember { SyncConnectivityObserver.observe(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val debtsSyncError by debtsViewModel.hasSyncError.collectAsStateWithLifecycle()
    val materialsSyncError by materialsViewModel.hasSyncError.collectAsStateWithLifecycle()
    val lastSyncedLabel = remember(online, debtsSyncError, materialsSyncError) {
        SyncStatusStore.lastSyncedAt(context)?.let { "آخر مزامنة: ${df.format(Date(it))}" } ?: "لم تتم أي مزامنة بعد"
    }

    val notesState = notesViewModel?.uiState?.collectAsStateWithLifecycle()
    val notes: List<ImportantNote> = notesState?.value?.notes ?: emptyList()
    val noteRows = remember(notes) {
        val now = System.currentTimeMillis()
        notes
            .filter { !it.isDone && (it.isPinned || it.reminderAt > now) }
            .sortedBy { if (it.reminderAt > now) it.reminderAt else Long.MAX_VALUE }
            .take(3)
            .map { n ->
                NoteRow(
                    id = n.id,
                    title = n.title.ifBlank { n.content.lineSequence().firstOrNull().orEmpty() }.ifBlank { "ملاحظة" },
                    whenLabel = if (n.reminderAt > now) df.format(Date(n.reminderAt)) else null
                )
            }
    }

    // Home search across customers, materials and notes.
    var homeQuery by rememberSaveable { mutableStateOf("") }
    val searchActive = homeQuery.isNotBlank()
    // Indexes are only built while a query is active, so idle home screens pay nothing for search.
    val personMatches = rememberSmartSearch(if (searchActive) personsNow else emptyList<Person>(), homeQuery) { it.name }
    val materialMatches = rememberSmartSearch(if (searchActive) materialsState.materials else emptyList<Material>(), homeQuery) { it.name }
    val noteMatches = rememberSmartSearch(if (searchActive) notes else emptyList<ImportantNote>(), homeQuery) { n -> n.title.ifBlank { n.content } }
    val searchHits = remember(searchActive, personMatches, materialMatches, noteMatches) {
        if (!searchActive) emptyList<SearchHit>()
        else buildList<SearchHit> {
            personMatches.take(4).forEach { add(SearchHit(SearchHit.PERSON, it.id, it.name, "")) }
            materialMatches.take(4).forEach { add(SearchHit(SearchHit.MATERIAL, it.id, it.name, it.quantityLabel())) }
            noteMatches.take(3).forEach { n ->
                add(SearchHit(SearchHit.NOTE, n.id, n.title.ifBlank { n.content.lineSequence().firstOrNull().orEmpty() }, ""))
            }
        }
    }

    val recentActivity = remember(debtsState.debts, debtsState.persons, materialsState.materials) {
        val personsById = debtsState.persons.associateBy { it.id }
        val debtRows = debtsState.debts.map { debt ->
            val personName = personsById[debt.personId]?.name ?: "عميل"
            ActivityRow(
                icon = Icons.Default.AttachMoney,
                color = avatarColorFor(personName),
                title = personName,
                subtitle = "دين جديد: ${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}",
                timestamp = debt.createdAt
            )
        }
        val materialRows = materialsState.materials.map { m ->
            ActivityRow(
                icon = Icons.Default.Spa,
                color = avatarColorFor(m.name),
                title = m.name,
                subtitle = "نقص مضاف: ${m.quantityLabel()}",
                timestamp = m.updatedAt
            )
        }
        (debtRows + materialRows).sortedByDescending { it.timestamp }.take(6)
    }

    Scaffold(
        // Edge-to-edge: the status bar is transparent (see
        // SetSystemBarsColor/MainActivity) and DashboardHeader below draws
        // its own liquid-glass panel all the way up to the true top of the
        // window, padding its *content* down manually — so this Scaffold
        // must not reserve top space itself, or the header would be pushed
        // down a second time, leaving a plain gap above it instead of one
        // continuous glass surface. Bottom/horizontal safe-area insets
        // (gesture nav bar, cutouts) are kept as-is.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = debtsRefreshing || materialsRefreshing,
            onRefresh = { debtsViewModel.refresh(); materialsViewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
        // BUG FIXED ("آخر عنصر بالقائمة بيصير تحت الشريط السفلي"): same as
        // the other pager tabs — the floating nav pill floats over this
        // screen too, so the last card needs to clear the pill's real
        // measured height, not just a flat gap. The flat gap on top of that
        // was only 16.dp, noticeably thinner than every other tab's own
        // clearance (Debts/Materials/Notes all reserve 32.dp+ beyond the
        // pill) — on the one tab with the longest list (the shortage/market
        // list below, which can run past a screen's worth of rows), that
        // thinner gap was exactly what let the last row or two sit close
        // enough to peek out from behind the pill's transparent side
        // margins instead of clearing it with a visible gap like everywhere
        // else. Matched to the same 32.dp the other tabs use.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp + LocalFloatingBottomNavHeight.current),
            verticalArrangement = Arrangement.spacedBy(AppSectionGap)
        ) {
            item(key = "header", contentType = "header") {
                DashboardHeader(onOpenDrawer = onOpenDrawer)
            }

            // Order: hero stats first (what do I owe / need), then connection warnings, the
            // learned suggestions, quick actions and search, then the data cards.
            item(key = "hero", contentType = "hero") {
                HeroStatsCard(
                    totalDebt = debtsState.totalAmount,
                    totalPersons = debtsState.totalPersons,
                    shortagesCount = shortages.size,
                    importantCount = importantCount,
                    debtsLoading = debtsState.isLoading,
                    materialsLoading = materialsState.isLoading,
                    hasShortages = shortages.isNotEmpty(),
                    marketAccent = marketAccent,
                    topDebtorName = topDebtors.firstOrNull()?.name,
                    nf = nf,
                    modifier = cardFx(0)
                )
            }

            if (!online || debtsSyncError || materialsSyncError) {
                item(key = "sync_status", contentType = "alert") {
                    HomeAlertCard(
                        icon = Icons.Default.CloudOff,
                        accent = semantic.danger,
                        title = if (!online) "لا يوجد اتصال بالإنترنت" else "تعذّرت المزامنة",
                        body = lastSyncedLabel,
                        actionLabel = "إعادة المحاولة",
                        onClick = { scope.launch { SyncRetry.forceReconnect() } },
                        modifier = cardFx(1)
                    )
                }
            }

            if (homeSuggestions.isNotEmpty()) {
                item(key = "home_suggestions", contentType = "suggestions") {
                    SuggestionPager(
                        suggestions = homeSuggestions,
                        accent = marketAccent,
                        onAccept = { s ->
                            materialsViewModel.onSuggestionAccepted(s)
                            if (s.kind == SuggestionKind.PRICES_REVIEW) onNavigateToMaterials() else onOpenMissingFinder()
                        },
                        onDismiss = { s -> materialsViewModel.onSuggestionDismissed(s) },
                        modifier = cardFx(2)
                    )
                }
            }

            item(key = "quick_actions", contentType = "quick_actions") {
                QuickActionsRow(
                    onAddPerson = onNavigateToDebts,
                    onAddMaterial = onNavigateToMaterials,
                    modifier = cardFx(3)
                )
            }

            item(key = "home_search", contentType = "search") {
                AppCapsuleSearchField(
                    query = homeQuery,
                    onQueryChange = { homeQuery = it },
                    placeholder = "ابحث عن عميل أو مادة أو ملاحظة",
                    modifier = Modifier.padding(horizontal = AppScreenPadding)
                )
            }

            if (searchActive) {
                item(key = "home_search_results", contentType = "search_results") {
                    HomeSearchResults(
                        hits = searchHits,
                        onHit = { hit ->
                            homeQuery = ""
                            when (hit.kind) {
                                SearchHit.PERSON -> onOpenPerson(hit.id)
                                SearchHit.MATERIAL -> onOpenMaterial(hit.title)
                                else -> onOpenNotes()
                            }
                        }
                    )
                }
            }

            // One skeleton per section while that section loads, so the layout never jumps.
            if (debtsState.isLoading) {
                item(key = "skeleton_debts", contentType = "skeleton") {
                    SkeletonCard(lines = 2, modifier = Modifier.padding(horizontal = AppScreenPadding))
                }
            }
            if (materialsState.isLoading) {
                item(key = "skeleton_materials", contentType = "skeleton") {
                    SkeletonCard(lines = 3, modifier = Modifier.padding(horizontal = AppScreenPadding))
                }
            }

            if (overdue.isNotEmpty()) {
                item(key = "overdue_debts", contentType = "section") {
                    OverdueDebtsCard(
                        entries = overdue,
                        accent = semantic.danger,
                        nf = nf,
                        currency = currency,
                        onOpenPerson = onOpenPerson,
                        onRemind = { e ->
                            PhoneUtils.whatsApp(
                                context,
                                e.phone,
                                "السلام عليكم ${e.name}، نذكّرك بوجود مبلغ ${nf.format(e.amount)} $currency مستحق منذ ${e.days} يوماً."
                            )
                        },
                        modifier = cardFx(4)
                    )
                }
            }

            if (!todaySummary.isEmpty) {
                item(key = "today_summary", contentType = "section") {
                    TodaySummaryCard(
                        summary = todaySummary,
                        nf = nf,
                        currency = currency,
                        successColor = semantic.success,
                        addedColor = semantic.warning,
                        modifier = cardFx(5)
                    )
                }
            }

            if (noteRows.isNotEmpty()) {
                item(key = "notes_reminders", contentType = "section") {
                    NotesRemindersCard(
                        rows = noteRows,
                        accent = semantic.info,
                        onOpenNotes = onOpenNotes,
                        modifier = cardFx(6)
                    )
                }
            }

            if (shortages.isNotEmpty()) {
                item(key = "market_list", contentType = "section") {
                    // BUG FIXED ("قائمة المشتريات شكلها مو مرتب"): a bare
                    // stack of name/quantity Rows with no separator between
                    // them read as one dense, undifferentiated block of
                    // text next to "آخر النشاطات" right below it (which has
                    // a proper icon, two-line text, and its own row
                    // height) — noticeably plainer than everywhere else in
                    // the app once this list runs past a few items. A thin
                    // divider between rows (not after the last one) gives
                    // each item its own visual line without adding a full
                    // bordered card per row, and the extra vertical padding
                    // gives every row a bit more room to breathe.
                    SectionCard(title = "قائمة مشتريات السوق", color = marketAccent, icon = Icons.Default.ShoppingCart, modifier = cardFx(7)) {
                        shortages.forEachIndexed { index, m ->
                            // REDESIGN (reference screenshot: each shortage
                            // row carries its own small colored icon circle
                            // on the lead side and a rounded, color-coded
                            // quantity badge on the trailing side, instead
                            // of two bare Text() values) — mirrors the same
                            // icon-circle language "آخر النشاطات" already
                            // uses below, so every row in this card family
                            // reads as one consistent pattern.
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = AppSpaceSm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(marketAccent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Spa, contentDescription = null, tint = marketAccent, modifier = Modifier.size(15.dp))
                                }
                                Spacer(Modifier.width(AppSpace10))
                                Text(
                                    m.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                com.shopmanager.app.ui.common.PillBadge(
                                    text = m.quantityLabel(),
                                    color = com.shopmanager.app.ui.common.pillColorForQuantity(m.quantity)
                                )
                            }
                            if (index != shortages.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }

            // أكبر الديون: صار فوق المشتريات (مرتبط مباشرة ببطاقة الإجمالي)، ومع كل عميل
            // شريط نسبي يوضّح حجم دينه مقارنةً بأكبر دين.
            if (topDebtors.isNotEmpty()) {
                item(key = "top_debtors", contentType = "section") {
                    SectionCard(title = "أكبر الديون", icon = Icons.Default.Groups, modifier = cardFx(8)) {
                        val maxAmount = topDebtors.maxOf { it.amount }.coerceAtLeast(1.0)
                        topDebtors.forEachIndexed { index, p ->
                            val barColor = avatarColorFor(p.name)
                            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape).background(barColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            p.name.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(AppSpace10))
                                    Text(
                                        p.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${nf.format(p.amount)} ${AppSettingsState.currencySymbol}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.height(7.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((p.amount / maxAmount).toFloat().coerceIn(0.04f, 1f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(barColor)
                                    )
                                }
                            }
                            if (index != topDebtors.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }

            if (!weekTrend.isEmpty) {
                item(key = "week_trend", contentType = "section") {
                    WeekTrendCard(
                        trend = weekTrend,
                        addedColor = semantic.warning,
                        paidColor = semantic.success,
                        modifier = cardFx(8)
                    )
                }
            }

            if (backupStale) {
                item(key = "backup_status", contentType = "alert") {
                    HomeAlertCard(
                        icon = Icons.Default.Backup,
                        accent = semantic.warning,
                        title = "النسخ الاحتياطي",
                        body = backupBody,
                        actionLabel = "فتح الإعدادات",
                        onClick = onOpenSettings,
                        onDismiss = { backupHidden = true },
                        modifier = cardFx(8)
                    )
                }
            }

            if (recentActivity.isNotEmpty()) {
                item(key = "recent_activity", contentType = "section") {
                    SectionCard(title = "آخر النشاطات", icon = Icons.Default.History, modifier = cardFx(8)) {
                        recentActivity.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = AppSpace6),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(row.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(row.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(AppSpace10))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        row.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                // BUG FIXED: this timestamp had no fixed
                                // width and no line/overflow limit, so a
                                // long title next to it (see row.title
                                // above) used to squeeze it and could wrap
                                // the time/date onto two lines, breaking
                                // this row's height versus every other row.
                                if (row.timestamp > 0) {
                                    Text(
                                        df.format(Date(row.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (debtsState.persons.isEmpty() && materialsState.materials.isEmpty() && !debtsState.isLoading && !materialsState.isLoading) {
                item(key = "empty_state", contentType = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp, start = AppSpaceLg, end = AppSpaceLg), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Storefront, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(AppSpaceMd))
                            Text(
                                "ابدأ بإضافة عملاء أو مواد من التبويبات بالأسفل",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

// REDESIGN ("افصل الشاشة الرئيسية عن بقية الشاشات" + "البار العلوي متل
// Claude AI"): every other tab (Debts/Materials/Notes) opens with the same
// boxed liquid-glass panel — a title sitting on a brand-gradient card with
// rounded bottom corners. Home used to share that exact same treatment
// ("إدارة المحل" on its own gradient card), which is exactly why it read as
// just one more tab instead of the app's actual home screen, and why the
// floating hamburger circle that used to sit on top of it visually
// collided with that card's own title text in the same top-right corner.
// Claude's own home screen ("Back at it, S") never puts its greeting on a
// boxed card at all — just a transparent top bar (a plain menu icon, no
// title) with the greeting sitting directly on the app's background right
// below it. This header now follows that same split: a slim, fully
// transparent row for the hamburger (a real layout element, not a floating
// overlay — see MainActivity, which no longer draws one at all), then the
// greeting + "إدارة المحل" underneath, also directly on the plain
// background. Nothing here can overlap the hamburger anymore because the
// hamburger now *is* part of this layout instead of a separate layer
// floating on top of it, and Home now visibly stands apart from the boxed-
// card look every other tab keeps.
//
// MOVED ("انقل ايقونة المسؤول الى المنيو الى جانب الاعدادات"): لوحة
// المسؤول's trigger button used to sit at the trailing end of this exact
// top bar. It now lives in the side drawer next to الإعدادات instead (see
// AppDrawerContent/MainActivity), so this header goes back to being just
// the hamburger — nothing trailing it anymore.
@Composable
private fun DashboardHeader(
    onOpenDrawer: () -> Unit = {}
) {
    // BUG FIXED ("صباح الخير" عالقة طول اليوم): `remember { timeBasedGreeting() }`
    // كان يُحسب مرة واحدة بس، أول ما هالهيدر يدخل التركيب — وبما إن تبويب
    // الرئيسية (صفحة بالـ HorizontalPager) يضل حي طول عمر التطبيق (ما
    // يُعاد إنشاؤه لما تبدّل تبويب)، فتح التطبيق الصبح وتركه شغّال للعصر/
    // المسا كان يخلي الترحيب عالق عالنص "صباح الخير" دايمًا، بدون ما
    // يتحدّث أبدًا. هلق بيتحقق من الوقت كل دقيقة عبر LaunchedEffect، فينتقل
    // فعليًا بين "صباح الخير"/"مساء الخير"/إلخ مع مرور اليوم، وبيرجع
    // يعكس الوقت الصحيح فورًا كل ما الهيدر يدخل التركيب من جديد (فتح
    // التطبيق من الصفر بعد إغلاقه بالكامل).
    var greeting by remember { mutableStateOf(timeBasedGreeting()) }
    val dateFormat = remember { SimpleDateFormat("EEEE، d MMMM", Locale("ar")) }
    var dateText by remember { mutableStateOf(dateFormat.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            greeting = timeBasedGreeting()
            dateText = dateFormat.format(Date())
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            // No liquidGlassSurface here on purpose (see the class-level
            // REDESIGN note above) — Home sits directly on the app's plain
            // background, unlike the boxed gradient card every other tab's
            // header uses. This only clears the transparent status bar.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = AppSpace20, vertical = 18.dp)
    ) {
        // "البار العلوي" (top bar): a slim, transparent row with just the
        // hamburger on the leading edge — laid out inline like Claude's
        // own top bar, never floating over anything below it. Settings and
        // لوحة المسؤول both now live one tap away in the drawer instead of
        // duplicating an entry point up here.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "القائمة",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.weight(1f))
            // تاريخ اليوم — شارة صغيرة بنفس لون بطاقات التطبيق.
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    dateText,
                    modifier = Modifier.padding(horizontal = AppScreenPadding, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(AppSpaceSm))
        // Greeting + title sit directly on the plain background, right
        // below the transparent top bar — matching Claude's own "Back at
        // it, S" home greeting instead of a boxed gradient card.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                greeting.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(AppSpace6))
            Text(
                greeting.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(AppSpaceXs))
        Text(
            "إدارة المحل",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 33.sp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickActionsRow(onAddPerson: () -> Unit, onAddMaterial: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = AppScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(AppSpace10)
    ) {
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonAdd,
            label = "عميل جديد",
            onClick = onAddPerson
        )
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Inventory2,
            label = "مادة جديدة",
            onClick = onAddMaterial
        )
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    AppRowSurface(shape = RoundedCornerShape(AppGroupLargeRadius), modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = AppScreenPadding, vertical = AppSpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AppSpace10))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * REDESIGN ("اعد تصميم الشاشة الرئيسية"): بطاقتان صغيرتان متساويتان صارتا بطاقة
 * واحدة كبيرة: إجمالي الديون برقم كبير في الأعلى (أهم رقم بالشاشة)، وتحته
 * إحصاءان مختصران (النواقص + أكبر عميل مدين) مفصولان بخط رفيع.
 */
@Composable
private fun HeroStatsCard(
    totalDebt: Double,
    totalPersons: Int,
    shortagesCount: Int,
    importantCount: Int,
    debtsLoading: Boolean,
    materialsLoading: Boolean,
    hasShortages: Boolean,
    marketAccent: Color,
    topDebtorName: String?,
    nf: NumberFormat,
    modifier: Modifier = Modifier
) {
    val success = LocalSemanticColors.current.success
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(modifier = modifier.padding(horizontal = AppScreenPadding)) {
        // الأداء القوي: لمعة ضوء ناعمة تمسح البطاقة الرئيسية (sheenSweep لا تفعل شيئاً في غيره).
        Column(Modifier.sheenSweep().padding(horizontal = AppSpace20, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(success.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = success, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(AppSpaceMd))
                Text(
                    "إجمالي الديون",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceVariant
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) {
                    Text(
                        "$totalPersons عميل",
                        modifier = Modifier.padding(horizontal = AppSpaceMd, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(AppSectionGap))
            AnimatedCounterText(
                targetValue = totalDebt,
                format = { "${nf.format(it)} ${AppSettingsState.currencySymbol}" },
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
                animate = !debtsLoading
            )
            Spacer(Modifier.height(AppSpaceLg))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(AppSectionGap))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                HeroMiniStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Inventory2,
                    accentColor = if (hasShortages) marketAccent else MaterialTheme.colorScheme.secondary,
                    title = "قائمة النواقص",
                    valueContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedCounterText(
                                targetValue = shortagesCount.toDouble(),
                                format = { it.toInt().toString() },
                                style = MaterialTheme.typography.titleMedium,
                                animate = !materialsLoading
                            )
                            Spacer(Modifier.width(AppSpace6))
                            Text(
                                if (hasShortages) "بانتظار الشراء" else "لا يوجد",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                )
                Box(
                    Modifier
                        .padding(horizontal = AppSpaceMd)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                HeroMiniStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    accentColor = LocalSemanticColors.current.warning,
                    title = "مواد هامة",
                    valueContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedCounterText(
                                targetValue = importantCount.toDouble(),
                                format = { it.toInt().toString() },
                                style = MaterialTheme.typography.titleMedium,
                                animate = !materialsLoading
                            )
                            Spacer(Modifier.width(AppSpace6))
                            Text(
                                if (importantCount > 0) "أولوية" else "لا يوجد",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroMiniStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accentColor: Color,
    title: String,
    valueContent: @Composable () -> Unit
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(AppSpace10))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            valueContent()
        }
    }
}

// REDESIGN ("اعد تصميم الالوان في كل التطبيق" + fixed unused `icon` param):
// same borderless `surfaceContainer` treatment as StatCard above, for the
// same reason — one card language shared with Settings instead of a
// hairline-outlined one just on Home. `icon` used to be accepted but never
// actually drawn anywhere in this composable, so every call site that
// passed one (or the leading emoji baked into a couple of titles, e.g.
// "🛒 قائمة مشتريات السوق") was really just decorating the title string by
// hand. Now rendered as a real leading glyph — plain, tinted with `color`,
// same size/spacing language as SettingsSection's own header row — so
// every call site can pass a proper Material icon instead of an emoji.
@Composable
internal fun SectionCard(
    title: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().padding(horizontal = AppScreenPadding)) {
        Row(
            Modifier.padding(horizontal = AppSpaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpaceSm))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(AppSpaceSm))
        AppCard {
            Column(Modifier.padding(horizontal = AppSpaceLg, vertical = AppSpaceSm), content = content)
        }
    }
}

@Composable
internal fun HomeSuggestionCard(
    suggestion: HomeSuggestion,
    accent: Color,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (suggestion.kind) {
        SuggestionKind.DUE_MATERIALS -> "\u0645\u0648\u0627\u062F \u0642\u062F \u062A\u062D\u062A\u0627\u062C\u0647\u0627"
        SuggestionKind.USUAL_CHECK -> "\u0648\u0642\u062A \u0641\u062D\u0635 \u0627\u0644\u0646\u0648\u0627\u0642\u0635"
        SuggestionKind.PRICES_REVIEW -> "\u0645\u0631\u0627\u062C\u0639\u0629 \u0627\u0644\u0623\u0633\u0639\u0627\u0631"
        SuggestionKind.STALE_MATERIALS -> "مواد غابت من فترة"
        SuggestionKind.FREQUENT_MATERIALS -> "الأكثر طلباً هذا الشهر"
        SuggestionKind.WITH_PENDING -> "غالباً بتنقص مع نواقصك"
        SuggestionKind.WEEKDAY_MATERIALS -> {
            val todayName = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")[
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
            ]
            "عادةً بتنقص يوم $todayName"
        }
    }
    val body = when (suggestion.kind) {
        SuggestionKind.DUE_MATERIALS -> suggestion.names.joinToString("\u060C ")
        SuggestionKind.USUAL_CHECK -> "\u0639\u0627\u062F\u0629 \u062A\u0631\u0627\u062C\u0639 \u0627\u0644\u0646\u0648\u0627\u0642\u0635 \u0641\u064A \u0647\u0630\u0627 \u0627\u0644\u0648\u0642\u062A"
        SuggestionKind.PRICES_REVIEW -> "\u062A\u0631\u0627\u062C\u0639 \u0627\u0644\u0623\u0633\u0639\u0627\u0631 \u0639\u0627\u062F\u0629 \u0641\u064A \u0647\u0630\u0627 \u0627\u0644\u0648\u0642\u062A"
        SuggestionKind.STALE_MATERIALS, SuggestionKind.FREQUENT_MATERIALS, SuggestionKind.WITH_PENDING,
        SuggestionKind.WEEKDAY_MATERIALS ->
            suggestion.names.joinToString("، ")
    }
    val action = when (suggestion.kind) {
        SuggestionKind.PRICES_REVIEW -> "\u0627\u0641\u062A\u062D \u0627\u0644\u0645\u0648\u0627\u062F"
        else -> "\u0627\u0641\u062D\u0635 \u0627\u0644\u0646\u0648\u0627\u0642\u0635"
    }
    val shape = RoundedCornerShape(AppGroupLargeRadius)
    AppCard(
        modifier = modifier
            .padding(horizontal = AppScreenPadding)
            .clip(shape)
            .clickable(onClick = onAccept)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppScreenPadding, end = AppSpaceXs, top = AppSpaceMd, bottom = AppSpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                val tw = com.shopmanager.app.ui.common.twinkleScale()
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp).graphicsLayer { scaleX = tw; scaleY = tw })
            }
            Spacer(Modifier.width(AppSpaceMd))
            Column(Modifier.weight(1f)) {
                Text(
                    "\u0627\u0642\u062A\u0631\u0627\u062D \u0630\u0643\u064A",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AppSpaceXs))
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(AppMinTouchTarget)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "\u0625\u062E\u0641\u0627\u0621",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
