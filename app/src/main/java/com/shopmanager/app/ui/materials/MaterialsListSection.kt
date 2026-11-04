package com.shopmanager.app.ui.materials

import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.materials.weightSummary
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceSummary
import androidx.compose.runtime.saveable.rememberSaveable
import com.shopmanager.app.ui.common.AppCapsuleSearchField
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.theme.LocalSemanticColors
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.shopmanager.app.ui.common.ActionSpec
import com.shopmanager.app.ui.common.sortedArabicBy
import com.shopmanager.app.ui.common.AppActionPill
import androidx.compose.material.icons.filled.Close
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.listItemEntrance

/**
 * Large-title header matching [com.shopmanager.app.ui.dashboard.DashboardScreen]'s
 * new look: bold oversized title + opaque circular share button on a flat
 * brand-gradient panel with rounded bottom corners, and a pill-shaped
 * segmented control for the المواد/الأسعار tabs instead of Material's
 * underlined [TabRow] - the underline style read as mismatched sitting
 * right below a solid gradient block. The whole thing is one continuous
 * panel so title, actions and tabs read as one cohesive header instead of
 * two stacked bars.
 */
@Composable
internal fun MaterialsHeader(
    tab: Int,
    onTogglePrices: () -> Unit,
    onShare: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        ScreenTopBar(
            title = if (tab == 0) "المواد والأسعار" else "الأسعار",
            reservedIcons = 2,
            navigation = {
                ScreenIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "القائمة",
                    onClick = onOpenDrawer
                )
            },
            actions = {
                AppActionPill(
                    height = 42.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    actions = listOf(
                        if (tab == 0) ActionSpec(Icons.Default.Sell, "الأسعار") { onTogglePrices() }
                        else ActionSpec(Icons.Default.Inventory2, "المواد") { onTogglePrices() },
                        ActionSpec(Icons.Rounded.Share, "مشاركة") { onShare() }
                    )
                )
            }
        )
        AppCapsuleSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = if (tab == 0) "ابحث عن مادة..." else "ابحث عن سعر مادة...",
            modifier = Modifier.padding(start = AppScreenPadding, end = AppScreenPadding, bottom = AppSpaceSm)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MaterialsList(
    materials: List<Material>,
    prices: Map<String, Double>,
    animateSummary: Boolean,
    searching: Boolean,
    onClearAll: () -> Unit,
    onEdit: (Material) -> Unit,
    onDelete: (Material) -> Unit,
    onToggleImportant: (Material) -> Unit,
    onReordered: (List<Material>) -> Unit
) {
    if (materials.isEmpty()) {
        EmptyState(
            icon = if (searching) Icons.Default.SearchOff else Icons.Default.Inventory2,
            text = if (searching) "لا توجد نتائج" else "لا توجد نواقص حالياً\nاضغط \"مادة جديدة\" لإضافة أول نقص"
        )
        return
    }
    // BUG FIXED: the trailing `Spacer(height = 72.dp)` used to be a guessed
    // stand-in for "roughly the FAB's height" so the last row could clear
    // it. It never accounted for the floating nav pill sitting below the
    // FAB too, and a fixed 72dp silently drifts wrong the moment either
    // one's real size changes. Replaced with real contentPadding sized off
    // the pill's actual measured height (LocalFloatingBottomNavHeight) plus
    // a fixed safety margin, computed once below, rather than a hardcoded
    // row. "مادة جديدة" has since moved off this screen too (same shared
    // quick-add "+" beside the pill as every other tab), so this margin is
    // no longer one specific FAB's height either — kept at the same size
    // regardless, as this list's general clearance against the pill's
    // transparent side margins, matching every other list in the app.
    val bottomSafetyMargin = 56.dp + 24.dp
    val bottomClearance = LocalFloatingBottomNavHeight.current + bottomSafetyMargin

    // FEATURE ADDED ("ترتيب المواد بالضغط المطول وتحريكها"): a local copy
    // that the drag gesture below reorders live, in real time, as the
    // finger moves — synced back to the real `materials` list on every
    // change from Firestore *except* while a drag is actually in progress,
    // so an incoming snapshot (e.g. from another device) never yanks the
    // row out from under the finger mid-drag. Reordering only makes sense
    // against the full, unfiltered list, so it's disabled entirely while
    // searching (see `canReorder` below) — dragging a filtered subset
    // would silently scramble the position of every hidden item too.
    var listTab by rememberSaveable { mutableStateOf(0) }
    val sortedMaterials = remember(materials, searching) {
        if (searching) materials else materials.sortedArabicBy { it.name }
    }
    val importantItems = remember(sortedMaterials) { sortedMaterials.filter { it.important } }
    val regularItems = remember(sortedMaterials) { sortedMaterials.filter { !it.important } }
    val visibleItems = when (listTab) {
        0 -> importantItems
        1 -> regularItems
        else -> sortedMaterials
    }
    LaunchedEffect(Unit) { if (importantItems.isEmpty()) listTab = 2 }
    var orderedItems by remember { mutableStateOf(visibleItems) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visibleItems) {
        if (draggingId == null) orderedItems = visibleItems
    }
    val canReorder = false
    // Real per-row height in px (row height + the 8.dp spacedBy gap),
    // measured live via onSizeChanged below — used to decide, as the
    // finger moves, when it's dragged far enough past a neighbor to swap
    // places with it.
    val itemHeightsPx = remember { mutableStateMapOf<String, Int>() }
    var dragOffset by remember { mutableStateOf(0f) }

    // تُحسب مرة لكل تغيّر فعلي في القائمة المعروضة أو في الأسعار.
    val weightSummary = remember(visibleItems) { visibleItems.weightSummary() }
    val priceSummary = remember(visibleItems, prices) { visibleItems.priceSummary(prices) }
    val allWeights = remember(materials) { materials.weightSummary() }
    val allPrices = remember(materials, prices) { materials.priceSummary(prices) }

    LazyColumn(
        Modifier.fillMaxSize(),
        // BUG FIXED ("السحب والافلات... كلشي يخفتي فوقها فما بقدر ارجع
        // للأعلى"): this screen sits inside PullToRefreshContent, whose
        // pull-to-refresh gesture and this LazyColumn's own scroll both
        // listen for the same vertical drag through Compose's nested-
        // scroll system. A row's reorder-drag only consumes pointer
        // *events* (`change.consume()` below), which stops the LazyColumn
        // from treating that drag as a scroll — but it doesn't stop the
        // list from being a live nested-scroll participant, so a drag that
        // starts right at the top of the list (dragging the very first
        // item) could still get read as a pull-to-refresh gesture at the
        // same time, leaving the refresh indicator dimming the top of the
        // list and stuck mid-gesture, unable to scroll, until released.
        // Disabling the list's own scrolling for the whole duration of a
        // row-drag removes it from nested scroll entirely, so no drag can
        // ever be mistaken for a pull-to-refresh again.
        userScrollEnabled = draggingId == null,
        contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(AppGroupGap)
    ) {
        // ملخّص القائمة (الأوزان + إجمالي السعر) وعنوانها: أول عنصرين في القائمة
        // نفسها فيتمرّران معها. يتبعان القائمة المعروضة (يتغيّران مع البحث).
        item(key = "summary") {
            MaterialsSummaryCard(
                weights = weightSummary,
                prices = priceSummary,
                overallWeights = if (listTab == 2) null else allWeights,
                overallPrices = if (listTab == 2) null else allPrices,
                animate = animateSummary,
                modifier = Modifier.padding(bottom = AppSpace6)
            )
        }
        item(key = "listTabs") {
            MaterialsTabBar(
                selected = listTab,
                importantCount = importantItems.size,
                regularCount = regularItems.size,
                allCount = materials.size,
                onSelect = { listTab = it },
                modifier = Modifier.padding(bottom = AppSpaceXs)
            )
        }
        item(key = "listTitle") {
            AppSectionTitle(
                text = when (listTab) {
                    0 -> "المواد الهامة (${visibleItems.size})"
                    1 -> "المواد الناقصة (${visibleItems.size})"
                    else -> "كل المواد (${visibleItems.size})"
                },
                modifier = Modifier.padding(horizontal = 0.dp),
                trailing = {
                    TextButton(onClick = onClearAll) {
                        Text(
                            "مسح الكل",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
        if (visibleItems.isEmpty()) {
            item(key = "noImportant") {
                Text(
                    if (listTab == 0) "لا توجد مواد هامة\nاضغط على النجمة بجانب أي مادة لتظهر هنا" else "لا توجد مواد هنا",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(
            orderedItems,
            key = { _, m -> m.id },
            // PERF: كل الصفوف من نوع واحد — يسمح لـ Compose بإعادة استخدام تركيبها بين الصفوف.
            contentType = { _, _ -> "materialRow" }
        ) { rowIndex, m ->
            val isDragging = m.id == draggingId
            Box(
                Modifier
                    .fillMaxWidth()
                    // Items not currently being dragged still animate into
                    // their new slot when a drag (or a delete/search
                    // change) shifts them — skipped for the dragged item
                    // itself so its own manual `dragOffset` below (which
                    // already tracks the finger exactly) isn't fought by a
                    // second, competing placement animation.
                    .then(if (!isDragging) Modifier.animateItem(
                        // السلاسة: المادة المضافة حديثاً تظهر بتلاشٍ قصير بدل القفز المفاجئ.
                        fadeInSpec = if (LocalPerformanceTier.current == PerformanceTier.LOW) null
                        else tween(durationMillis = MotionSpecs.fadeMillis(), easing = MotionSpecs.claudeEasing),
                        placementSpec = MotionSpecs.reorderSpring(),
                        fadeOutSpec = if (LocalPerformanceTier.current == PerformanceTier.LOW) null
                        else MotionSpecs.listItemFadeOut()
                    ) else Modifier)
                    .onSizeChanged { itemHeightsPx[m.id] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    // حركة دخول لأول ظهور للصف (تُعطَّل تلقائياً على الأجهزة الضعيفة).
                    .listItemEntrance(rowIndex)
                    .then(
                        if (canReorder) {
                            Modifier.pointerInput(m.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = m.id
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        if (draggingId != null) onReordered(orderedItems)
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    // BUG FIXED ("تقنية السحب والافلات... بدها تحسين وتطوير
                                    // بآلية العمل بشكل دقيق"): the swap threshold and the
                                    // amount `dragOffset` got corrected by on every swap used
                                    // to both be the *dragged* item's own height alone (`step`
                                    // above). That's only correct when every row is the same
                                    // height. Rows here aren't — a material whose name wraps
                                    // to two lines (e.g. "بذور القرع / اليقطين") is visibly
                                    // taller than a one-line row like "بيتزا" right next to it
                                    // (see the reference screenshot). Using only the dragged
                                    // row's own height meant swapping past a taller neighbor
                                    // triggered too early (threshold too small) and left the
                                    // dragged row's finger-relative offset wrong by the
                                    // difference in height (correction too small), so the row
                                    // under the finger visibly snapped to the wrong spot —
                                    // worse the more the two rows' heights differed.
                                    // Fixed by looking up the *actual* neighbor being swapped
                                    // past (not assuming it's the same height as the dragged
                                    // row): the swap now triggers at the midpoint between the
                                    // two rows' real heights, and the offset is corrected by
                                    // the neighbor's real height (the exact distance the
                                    // dragged row visually jumps as it trades places), so the
                                    // row tracks the finger precisely regardless of how the
                                    // heights differ.
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragOffset += delta.y
                                        val gap = AppGroupGap.toPx()
                                        val myHeight = (itemHeightsPx[m.id] ?: 0).toFloat()
                                        val currentIndex = orderedItems.indexOfFirst { it.id == m.id }
                                        // BUG FIXED (part of the same
                                        // "السحب... فما بقدر ارجع للأعلى" fix
                                        // as userScrollEnabled above): the
                                        // first item has no neighbor above it
                                        // to swap with, so nothing here ever
                                        // stopped `dragOffset` itself from
                                        // still climbing negative as the
                                        // finger kept moving up — the row
                                        // just kept sliding up past its own
                                        // resting slot with no floor,
                                        // visually detaching from the list
                                        // (and symmetrically for the last
                                        // item dragged past the bottom).
                                        // Clamped to 0 in whichever
                                        // direction has no neighbor to swap
                                        // into, so the dragged row never
                                        // moves further than the real list
                                        // actually allows.
                                        if (currentIndex == 0 && dragOffset < 0f) dragOffset = 0f
                                        if (currentIndex == orderedItems.lastIndex && dragOffset > 0f) dragOffset = 0f
                                        if (dragOffset > 0f && currentIndex < orderedItems.lastIndex) {
                                            val neighbor = orderedItems[currentIndex + 1]
                                            val neighborHeight = (itemHeightsPx[neighbor.id] ?: myHeight.toInt()).toFloat()
                                            val threshold = (myHeight + neighborHeight) / 2f + gap
                                            if (dragOffset > threshold) {
                                                orderedItems = orderedItems.toMutableList().apply {
                                                    add(currentIndex + 1, removeAt(currentIndex))
                                                }
                                                dragOffset -= (neighborHeight + gap)
                                            }
                                        } else if (dragOffset < 0f && currentIndex > 0) {
                                            val neighbor = orderedItems[currentIndex - 1]
                                            val neighborHeight = (itemHeightsPx[neighbor.id] ?: myHeight.toInt()).toFloat()
                                            val threshold = (myHeight + neighborHeight) / 2f + gap
                                            if (-dragOffset > threshold) {
                                                orderedItems = orderedItems.toMutableList().apply {
                                                    add(currentIndex - 1, removeAt(currentIndex))
                                                }
                                                dragOffset += (neighborHeight + gap)
                                            }
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                MaterialRow(
                    material = m,
                    shape = groupedRowShape(rowIndex, orderedItems.lastIndex),
                    onEdit = { onEdit(m) },
                    onDelete = { onDelete(m) },
                    onToggleImportant = { onToggleImportant(m) },
                    isDragging = isDragging,
                    linePrice = m.linePrice(prices)
                )
            }
        }
    }
}

@Composable
internal fun MaterialRow(
    material: Material,
    shape: Shape,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleImportant: () -> Unit,
    isDragging: Boolean = false,
    linePrice: Double? = null,
    modifier: Modifier = Modifier
) {
    val avatarColor = remember(material.name) { avatarColorFor(material.name) }
    // LAG FIX: قراءة الـ scale في مرحلة الرسم بدل إعادة تركيب الصف كاملاً في كل إطار.
    val scale = animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = MotionSpecs.quickSpring(),
        label = "materialRowScale"
    )
    val rowFill = MaterialTheme.colorScheme.surfaceContainerHigh
    // الصف "المهم جداً" يأخذ لمسة ذهبية خفيفة بدل الإطار الملوّن القديم.
    val fill = if (material.important) {
        LocalSemanticColors.current.warning.copy(alpha = 0.14f).compositeOver(rowFill)
    } else rowFill

    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        Surface(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = fill,
            shadowElevation = if (isDragging) 6.dp else 0.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(start = AppSpace2, end = AppSpace10, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleImportant, modifier = Modifier.size(AppMinTouchTarget)) {
                    Icon(
                        if (material.important) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (material.important) "إلغاء الأهمية" else "وضع كهامة جداً",
                        tint = if (material.important) LocalSemanticColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppIconCircle(color = avatarColor, size = 38.dp) {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(AppSpace10))
                Column(Modifier.weight(1f)) {
                    Text(
                        material.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    // سعر هذا الصف = سعر الكيلو × الوزن (يظهر فقط إذا كانت المادة مسعّرة).
                    if (linePrice != null) {
                        Text(
                            "${Formatters.number(linePrice)} ${AppSettingsState.currencySymbol}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (material.notes.isNotBlank()) {
                        Text(
                            material.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                com.shopmanager.app.ui.common.PillBadge(
                    text = material.quantityLabel(),
                    color = com.shopmanager.app.ui.common.pillColorForQuantity(material.quantity)
                )
                Spacer(Modifier.width(AppSpaceSm))
                AppActionPill(
                    height = 36.dp,
                    actions = listOf(
                        ActionSpec(Icons.Default.Edit, "تعديل", null, onEdit),
                        ActionSpec(Icons.Default.Close, "حذف المادة", LocalSemanticColors.current.danger, onDelete)
                    )
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    AppEmptyState(icon = icon, text = text)
}
