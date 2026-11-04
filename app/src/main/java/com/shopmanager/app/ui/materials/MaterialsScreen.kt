package com.shopmanager.app.ui.materials

import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpaceSm
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.ui.common.rememberSmartSearch
import androidx.activity.compose.BackHandler
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.showWithOptionalUndo
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.ShareFormatDialog
import com.shopmanager.app.ui.common.GlassAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shopmanager.app.ui.theme.LocalBrandGradientColors
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppScreenPadding
import androidx.compose.material3.TextButton
import com.shopmanager.app.data.performance.MotionSpecs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    viewModel: MaterialsViewModel = viewModel(),
    onAddNew: () -> Unit = {},
    // "دمج زر حفظ الأسعار مع الشريط السفلي": these four let MainActivity
    // show/drive a "حفظ الأسعار" action beside the floating nav pill
    // (FloatingBottomNav's `secondaryAction`, opposite side from the
    // existing "+" — see MainActivity) instead of this screen drawing its
    // own full-width save button. Same request/handled pattern already
    // used for "عميل جديد" (see DebtsScreen's addPersonRequested): tapping
    // the pill's button just raises `savePricesRequested`, this screen
    // watches it and performs the actual save, then reports it handled.
    onPricesTabActiveChanged: (Boolean) -> Unit = {},
    onPricesChangedCountChanged: (Int) -> Unit = {},
    savePricesRequested: Boolean = false,
    onSavePricesRequestHandled: () -> Unit = {},
    // BUG FIXED ("ترابط بين الديون والملاحظات" كان يغطي الأشخاص فقط): see
    // MainActivity's pendingMaterialHighlight and NotesScreen's onOpenLink —
    // a material-linked note now hands its linked material's name through
    // here instead of just opening this screen with an empty search, so
    // tapping the note lands directly on that one item instead of the full
    // list.
    initialSearchQuery: String? = null,
    onInitialSearchConsumed: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    // REDESIGN ("شريط البحث لازم يكون زر في الشريط العلوي يتوسع بواجهة لحالة
    // اثناء البحث"): the search field used to sit permanently above the
    // list; it now lives as a small icon button in MaterialsHeader that
    // expands the header's own title row into a focused search field when
    // tapped, instead of taking up its own row on the page at all times.
    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var deleteTarget by remember { mutableStateOf<Material?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brandColor = LocalBrandGradientColors.current.first().toArgb()

    // Consumed once (onInitialSearchConsumed clears it back to null in
    // MainActivity) so navigating away and back to this tab later, or a
    // recomposition for an unrelated reason, doesn't keep re-forcing the
    // search box back to this value if the person has since cleared it
    // themselves. Also lands on the main list tab (0), not الأسعار — a
    // material-linked note is always about the shortage-list entry, never
    // its price.
    LaunchedEffect(initialSearchQuery) {
        if (!initialSearchQuery.isNullOrBlank()) {
            search = initialSearchQuery
            tab = 0
            onInitialSearchConsumed()
        }
    }

    // REDESIGN: the "مادة جديدة" quick-add action moved out to
    // FloatingBottomNav's shared `quickAction` slot beside the nav pill
    // (wired up in MainActivity), so this screen no longer needs its own
    // FAB or a clearance value just for it.

    LaunchedEffect(message) {
        message?.let {
            val hasUndo = viewModel.undo.value != null
            val undone = snackbarHost.showWithOptionalUndo(it, hasUndo)
            if (undone) viewModel.performUndo() else viewModel.clearUndo()
            viewModel.clearMessage()
        }
    }

    // PERF: this used to re-run the .filter{} scan over the whole
    // materials list on *every* recomposition of this screen — including
    // ones triggered by completely unrelated state (a dialog opening, the
    // snackbar message clearing, etc.), not just an actual `search` or
    // `state.materials` change. `remember` keyed on the two inputs this
    // computation actually depends on makes it skip that rescan unless
    // one of them genuinely changed.
    val filtered = rememberSmartSearch(state.materials, search) { it.name }

    BackHandler(enabled = tab == 1) { tab = 0 }

    // MOVED UP from PricesList: this in-progress price-edits buffer used to
    // live entirely inside PricesList, private to that tab, since only its
    // own full-width save button ever read it. Now that saving is
    // triggered externally (from the floating pill's merged button — see
    // `savePricesRequested` above), this screen needs to reach the same
    // buffer to actually perform the save, so it's hoisted here and passed
    // down to PricesList instead of PricesList creating its own.
    val editedPrices = remember(catalog) { mutableStateMapOf<String, String>() }
    // PERF FIX (تقطيع أثناء كتابة السعر): كان العدّ يتم هنا مباشرة داخل جسم الشاشة،
    // فكل حرف يُكتب بأي حقل سعر (كتابة في editedPrices) كان يعيد تركيب الشاشة كلها
    // (الهيدر + التبويبات + القائمة). الآن القراءة داخل snapshotFlow فقط، فلا يُعاد
    // تركيب الشاشة إلا إذا تغيّر الرقم فعلاً، ويُبلَّغ به مرة واحدة عند كل تغيّر.
    val latestPrices by rememberUpdatedState(state.prices)
    val latestOnChangedCount by rememberUpdatedState(onPricesChangedCountChanged)
    LaunchedEffect(editedPrices) {
        snapshotFlow {
            editedPrices.count { (name, value) ->
                val parsed = value.toPriceOrNull()
                parsed != null && parsed != latestPrices[name]
            }
        }.distinctUntilChanged().collect { latestOnChangedCount(it) }
    }
    LaunchedEffect(tab) { onPricesTabActiveChanged(tab == 1) }
    LaunchedEffect(savePricesRequested) {
        if (savePricesRequested) {
            val toSave = editedPrices.mapNotNull { (name, value) -> value.toPriceOrNull()?.let { name to it } }.toMap()
            viewModel.setPrices(toSave)
            editedPrices.clear()
            onSavePricesRequestHandled()
        }
    }

    Scaffold(
        // Edge-to-edge: the status bar is transparent (see
        // SetSystemBarsColor/MainActivity) and this screen's own
        // MaterialsHeader below draws its liquid-glass panel all the way
        // up to the true top of the window and pads its *content* down
        // manually — so this Scaffold must not also reserve top space
        // itself, or the header would get pushed down a second time
        // leaving a plain gap above it. Bottom/horizontal safe-area insets
        // (gesture nav bar, cutouts) are kept as-is.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            MaterialsHeader(
                tab = tab,
                onTogglePrices = { tab = if (tab == 1) 0 else 1 },
                onShare = { showShareChoice = true },
                onOpenDrawer = onOpenDrawer,
                searchQuery = search,
                onSearchQueryChange = { search = it }
            )
        }
        // REDESIGN: no `floatingActionButton` slot here anymore — "مادة
        // جديدة" now lives beside the floating nav pill (see
        // FloatingBottomNav's quickAction, wired up in MainActivity) so it
        // reads as one attached unit with the pill instead of a separate
        // button floating off on its own over the list.
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
        // الأجهزة القوية: التبديل بين المواد/الأسعار ينزلق أفقياً بمسافة صغيرة مع تلاشٍ
        // بدل تلاشٍ فقط؛ الأجهزة الضعيفة تبقى على تلاشٍ سريع جداً بلا انزلاق.
        val strongDevice = LocalPerformanceTier.current != PerformanceTier.LOW
        val tabFadeMs = MotionSpecs.contentTween<Float>()
        val tabSlideSpec = MotionSpecs.contentTween<IntOffset>()
        val tabFadeOutSpec = MotionSpecs.listItemFadeOut()
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (strongDevice) {
                    val dir = if (targetState > initialState) -1 else 1
                    (fadeIn(tabFadeMs) + slideInHorizontally(
                        animationSpec = tabSlideSpec
                    ) { full -> dir * full / 10 }) togetherWith fadeOut(tabFadeOutSpec)
                } else {
                    fadeIn(tabFadeMs) togetherWith fadeOut(tabFadeOutSpec)
                }
            },
            label = "materialsTab"
        ) { selectedTab ->
            if (selectedTab == 0) {
                // Kept deliberately minimal: just the search field above the
                // list, and materials below it - no extra banners competing
                // for attention. The "مادة جديدة" action lives only in the
                // floating button at the bottom of the screen.
                // REDESIGN ("زر اضافة مادة جديدة يجب ان يكون زر عريض مكان
                // شريط البحث للي شلته"): the permanent search field that
                // used to sit here moved into MaterialsHeader's own search
                // icon (see topBar above); this space is now a wide,
                // prominent "مادة جديدة" button instead, using the
                // `onAddNew` callback MainActivity already wires to the
                // catalog-picker screen.
                Column(Modifier.fillMaxSize()) {
                    // زر الإضافة نحيف (44dp بدل 52dp)، أما بطاقة الملخّص وعنوان
                    // القائمة فصارا أول عناصر القائمة نفسها (انظر MaterialsList) فيرتفعان
                    // مع التمرير وتأخذ المواد كامل الشاشة بدل أن تُدفع للأسفل.
                    AppPillButton(
                        label = "مادة جديدة",
                        icon = Icons.Default.Add,
                        onClick = onAddNew,
                        height = 44.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = AppSpaceSm)
                    )
                    if (state.isLoading && filtered.isEmpty()) {
                        com.shopmanager.app.ui.common.SkeletonRowList(
                            kind = com.shopmanager.app.ui.common.SkeletonRowKind.MATERIAL,
                            count = 7
                        )
                    } else MaterialsList(
                        materials = filtered,
                        prices = state.prices,
                        animateSummary = !state.isLoading,
                        searching = search.isNotBlank(),
                        onClearAll = { showClearAllConfirm = true },
                        onEdit = { editingMaterial = it },
                        onDelete = { deleteTarget = it },
                        onToggleImportant = {
                            if (!it.important) viewModel.recordImportant(it.name)
                            viewModel.setImportant(it, !it.important)
                        },
                        onReordered = { viewModel.reorderMaterials(it.map(Material::id)) }
                    )
                }
            } else {
                PricesList(
                    catalogItems = catalog,
                    materials = state.materials,
                    prices = state.prices,
                    history = priceHistory,
                    edited = editedPrices,
                    search = search
                )
            }
        }
        }
    }

    editingMaterial?.let { m ->
        var isSavingMaterial by remember { mutableStateOf(false) }
        MaterialEditDialog(
            initial = m,
            isSaving = isSavingMaterial,
            onDismiss = { if (!isSavingMaterial) editingMaterial = null },
            onSave = { name, qty, unit, notes, important ->
                isSavingMaterial = true
                viewModel.updateMaterial(m.id, name, qty, unit, notes) { success ->
                    isSavingMaterial = false
                    if (success) {
                        if (important != m.important) {
                            if (important) viewModel.recordImportant(name)
                            viewModel.setImportant(m, important)
                        }
                        editingMaterial = null
                    }
                }
            }
        )
    }

    deleteTarget?.let { m ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${m.name}\"؟") },
            confirmButton = {
                // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
                // PersonEditDialog.kt's doc comment.
                TextButton(shape = RectangleShape, onClick = { viewModel.deleteMaterial(m.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }

    if (showShareChoice) {
        ShareFormatDialog(
            onDismiss = { showShareChoice = false },
            onPickImage = {
                // PERF: bitmap/Canvas drawing moved off the main thread —
                // with a long materials list this Canvas work is real,
                // measurable CPU time, and running it straight in the
                // onClick previously blocked the UI thread and dropped
                // frames right as the share sheet was trying to animate
                // in. Generation runs on Dispatchers.Default; only the
                // final startActivity hop is back on Main.
                scope.launch {
                    val uri = withContext(Dispatchers.Default) {
                        MaterialsReportImage.generate(context, state.materials, state.prices, brandColor)
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة قائمة المواد"))
                }
            },
            onPickText = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, buildMaterialsShareText(state.materials, state.prices))
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة قائمة المواد"))
            }
        )
    }

    if (showClearAllConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("مسح كل المواد") },
            text = { Text("هل أنت متأكد من حذف كل المواد المضافة (${state.materials.size})؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    viewModel.deleteAllMaterials()
                    showClearAllConfirm = false
                }) { Text("حذف الكل") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { showClearAllConfirm = false }) { Text("إلغاء") } }
        )
    }
}
