package com.shopmanager.app.ui.materials

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.data.performance.listItemEntrance
import com.shopmanager.app.ui.common.AppCapsuleSearchField
import com.shopmanager.app.ui.common.AppChip
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.BlurContextMenu
import com.shopmanager.app.ui.common.BlurMenuAction
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.common.rememberSmartSearch
import com.shopmanager.app.ui.common.sortedArabicBy

private class ItemMenu(val item: MaterialCatalogItem, val hint: String?, val shape: Shape, val anchor: Rect)

private class GroupMenu(val name: String, val count: Int, val expanded: Boolean, val anchor: Rect)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialCatalogScreen(viewModel: MaterialsViewModel, onBack: () -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var isAddingCatalogItem by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pickedItem by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    var editTarget by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    var itemMenu by remember { mutableStateOf<ItemMenu?>(null) }
    var groupMenu by remember { mutableStateOf<GroupMenu?>(null) }
    var renameGroupTarget by remember { mutableStateOf<String?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateListOf<String>() }
    var suggestedOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val suggested by viewModel.suggestions.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    val filtered = rememberSmartSearch(catalog, search) { it.name }
    val searching = search.isNotBlank()
    val showSuggested = !searching && suggested.isNotEmpty()
    val orderedFiltered = remember(filtered, searching) {
        if (searching) filtered else filtered.sortedArabicBy { it.name }
    }
    val sortedSuggested = remember(suggested) { suggested.sortedArabicBy { it.name } }
    val rest = orderedFiltered
    val groups = remember(catalog) {
        catalog.map { it.group.trim() }.filter { it.isNotEmpty() }.distinct().sortedArabicBy { it }
    }
    val sections = remember(rest, groups) {
        val byGroup = rest.groupBy { it.group.trim() }
        val out = ArrayList<Pair<String, List<MaterialCatalogItem>>>()
        groups.forEach { g -> byGroup[g]?.let { out.add(g to it) } }
        byGroup[""]?.let { out.add("" to it) }
        out
    }

    val onPick: (MaterialCatalogItem) -> Unit = { pickedItem = it }
    val onItemLong: (MaterialCatalogItem, String?, Shape, Rect) -> Unit = { item, hint, shape, anchor ->
        itemMenu = ItemMenu(item, hint, shape, anchor)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            ScreenTopBar(
                title = "اختر مادة",
                navigation = {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                }
            )
        },
        floatingActionButton = {
            Surface(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.height(56.dp)
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("مادة جديدة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppCapsuleSearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "ابحث عن مادة...",
                modifier = Modifier.padding(start = AppScreenPadding, end = AppScreenPadding, top = 2.dp, bottom = 8.dp)
            )
            if (filtered.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Default.Inventory2,
                    text = if (catalog.isEmpty()) "القائمة فاضية، أضف أول مادة من الزر بالأسفل" else "لا توجد نتائج"
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = 2.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(AppGroupGap)
                ) {
                    if (showSuggested) {
                        item(key = "suggestedCard") {
                            SuggestedCard(
                                count = suggested.size,
                                onClick = { suggestedOpen = true },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    if (groups.isEmpty()) {
                        if (rest.isNotEmpty()) {
                            item(key = "catalogTitle") {
                                AppSectionTitle(
                                    text = when {
                                        searching -> "النتائج (${rest.size})"
                                        else -> "كل المواد (${rest.size})"
                                    },
                                    modifier = Modifier.padding(top = if (showSuggested) 10.dp else 0.dp, bottom = 4.dp)
                                )
                            }
                            catalogRows(rest, "a_", false, viewModel, onPick, onItemLong)
                        }
                    } else {
                        if (searching) {
                            item(key = "resultsTitle") {
                                AppSectionTitle(text = "النتائج (${filtered.size})", modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        sections.forEach { (g, list) ->
                            val open = searching || g in expanded
                            item(key = "h_$g", contentType = "groupHeader") {
                                GroupHeader(
                                    name = g,
                                    count = list.size,
                                    expanded = open,
                                    onToggle = {
                                        if (g in expanded) expanded.remove(g) else expanded.add(g)
                                    },
                                    onLongPress = { anchor ->
                                        if (g.isNotEmpty()) groupMenu = GroupMenu(g, list.size, open, anchor)
                                    },
                                    modifier = Modifier.padding(top = if (showSuggested || g != sections.first().first) 12.dp else 4.dp, bottom = 4.dp)
                                )
                            }
                            if (open) {
                                catalogRows(list, "g_${g}_", false, viewModel, onPick, onItemLong)
                            }
                        }
                    }
                    item(key = "catalogHint") {
                        AppFootnote(
                            "اضغط على المادة لإدخال الكمية • اضغط مطولاً للتعديل أو الحذف",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }

    BackHandler(enabled = suggestedOpen) { suggestedOpen = false }
    AnimatedVisibility(
        visible = suggestedOpen && sortedSuggested.isNotEmpty(),
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        SuggestedFullScreen(
            items = sortedSuggested,
            viewModel = viewModel,
            onBack = { suggestedOpen = false },
            onPick = onPick,
            onItemLong = onItemLong
        )
    }
    }

    itemMenu?.let { m ->
        BlurContextMenu(
            anchor = m.anchor,
            actions = listOf(
                BlurMenuAction("تعديل", Icons.Default.Edit) { editTarget = m.item },
                BlurMenuAction("حذف", Icons.Default.Delete, danger = true) { deleteTarget = m.item }
            ),
            onDismiss = { itemMenu = null },
            preview = { CatalogRowCard(m.item, m.hint, m.shape, Modifier, Modifier) }
        )
    }

    groupMenu?.let { m ->
        BlurContextMenu(
            anchor = m.anchor,
            actions = listOf(
                BlurMenuAction("تعديل", Icons.Default.Edit) { renameGroupTarget = m.name },
                BlurMenuAction("حذف", Icons.Default.Delete, danger = true) { deleteGroupTarget = m.name }
            ),
            onDismiss = { groupMenu = null },
            preview = { GroupHeaderCard(m.name, m.count, m.expanded, Modifier, Modifier) }
        )
    }

    if (showAddDialog) {
        CatalogItemDialog(
            title = "مادة جديدة للقائمة الثابتة",
            confirmLabel = "إضافة",
            initialName = "",
            initialGroup = "",
            groups = groups,
            isSaving = isAddingCatalogItem,
            onDismiss = { showAddDialog = false },
            onSave = { name, group ->
                isAddingCatalogItem = true
                viewModel.addCatalogItem(name, group) { success ->
                    isAddingCatalogItem = false
                    if (success) showAddDialog = false
                }
            }
        )
    }

    editTarget?.let { item ->
        var isSavingEdit by remember { mutableStateOf(false) }
        CatalogItemDialog(
            title = "تعديل المادة",
            confirmLabel = "حفظ",
            initialName = item.name,
            initialGroup = item.group.trim(),
            groups = groups,
            isSaving = isSavingEdit,
            onDismiss = { if (!isSavingEdit) editTarget = null },
            onSave = { newName, group ->
                isSavingEdit = true
                viewModel.updateCatalogItem(item.id, item.name, newName, group) { success ->
                    isSavingEdit = false
                    if (success) editTarget = null
                }
            }
        )
    }

    renameGroupTarget?.let { name ->
        var isSavingGroup by remember { mutableStateOf(false) }
        GroupNameDialog(
            initialName = name,
            isSaving = isSavingGroup,
            onDismiss = { if (!isSavingGroup) renameGroupTarget = null },
            onSave = { newName ->
                isSavingGroup = true
                viewModel.renameCatalogGroup(name, newName) { success ->
                    isSavingGroup = false
                    if (success) renameGroupTarget = null
                }
            }
        )
    }

    pickedItem?.let { item ->
        var isAddingMaterial by remember { mutableStateOf(false) }
        val learned = remember(item.name) { viewModel.suggestionFor(item.name) }
        QuantityEntryDialog(
            materialName = item.name,
            initialQuantity = learned?.first ?: 1,
            initialUnit = learned?.second ?: MaterialUnit.KG,
            isSaving = isAddingMaterial,
            onDismiss = { if (!isAddingMaterial) pickedItem = null },
            onSave = { quantity, unit, notes, important ->
                isAddingMaterial = true
                viewModel.addMaterial(item.name, quantity, unit, notes, important) { success ->
                    isAddingMaterial = false
                    if (success) pickedItem = null
                }
            }
        )
    }

    deleteTarget?.let { item ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف من القائمة الثابتة") },
            text = { Text("هل تريد حذف \"${item.name}\" من القائمة الثابتة؟ (هذا لا يحذف أي كمية مخزنة سابقًا)") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { viewModel.deleteCatalogItem(item.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }

    deleteGroupTarget?.let { name ->
        GlassAlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text("حذف المجموعة") },
            text = { Text("هل تريد حذف مجموعة \"$name\"؟ ستنتقل موادها إلى \"بدون مجموعة\" ولن تُحذف.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { viewModel.deleteCatalogGroup(name); deleteGroupTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteGroupTarget = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SuggestedFullScreen(
    items: List<MaterialCatalogItem>,
    viewModel: MaterialsViewModel,
    onBack: () -> Unit,
    onPick: (MaterialCatalogItem) -> Unit,
    onItemLong: (MaterialCatalogItem, String?, Shape, Rect) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar(
                title = "مقترحة لك (${items.size})",
                navigation = {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = 6.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(AppGroupGap)
        ) {
            catalogRows(items, "s_", true, viewModel, onPick, onItemLong)
            item(key = "suggestedHint") {
                AppFootnote(
                    "اضغط على المادة لإدخال الكمية • اضغط مطولاً للتعديل أو الحذف",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SuggestedCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("مقترحة لك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$count مادة حسب استخدامك",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun LazyListScope.catalogRows(
    list: List<MaterialCatalogItem>,
    keyPrefix: String,
    learned: Boolean,
    viewModel: MaterialsViewModel,
    onPick: (MaterialCatalogItem) -> Unit,
    onLongPress: (MaterialCatalogItem, String?, Shape, Rect) -> Unit
) {
    itemsIndexed(list, key = { _, c -> keyPrefix + c.id }, contentType = { _, _ -> "catalogItem" }) { index, item ->
        val hint = if (learned) remember(item.name) { learnedHint(viewModel, item.name) } else null
        val shape = groupedRowShape(index, list.lastIndex)
        Box(Modifier.listItemEntrance(index)) {
            CatalogRow(
                item = item,
                hint = hint,
                shape = shape,
                onClick = { onPick(item) },
                onLongPress = { anchor -> onLongPress(item, hint, shape, anchor) }
            )
        }
    }
}

private fun learnedHint(viewModel: MaterialsViewModel, name: String): String? {
    val insight = viewModel.insightFor(name) ?: return null
    if (insight.added == 0) return null
    val parts = ArrayList<String>(3)
    parts.add(if (insight.added == 1) "أضفتها مرة" else "أضفتها ${insight.added} مرات")
    insight.daysSinceLast?.let { parts.add(if (it == 0) "اليوم" else "منذ $it يوم") }
    val unit = insight.unit.label
    parts.add(if (unit.isBlank()) "${insight.qty}" else "${insight.qty} $unit")
    return parts.joinToString(" • ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogRow(
    item: MaterialCatalogItem,
    hint: String?,
    shape: Shape,
    onClick: () -> Unit,
    onLongPress: (Rect) -> Unit
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    CatalogRowCard(
        item = item,
        hint = hint,
        shape = shape,
        surfaceModifier = Modifier.onGloballyPositioned { coords = it },
        rowModifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                val c = coords
                if (c != null && c.isAttached) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(Rect(c.localToScreen(Offset.Zero), c.size.toSize()))
                }
            }
        )
    )
}

@Composable
private fun CatalogRowCard(
    item: MaterialCatalogItem,
    hint: String?,
    shape: Shape,
    surfaceModifier: Modifier,
    rowModifier: Modifier
) {
    val color = remember(item.name) { avatarColorFor(item.name) }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = surfaceModifier.fillMaxWidth()
    ) {
        Row(
            rowModifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconCircle(color = color, size = 40.dp) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    GroupHeaderCard(
        name = name,
        count = count,
        expanded = expanded,
        surfaceModifier = modifier.onGloballyPositioned { coords = it },
        rowModifier = Modifier.combinedClickable(
            onClick = onToggle,
            onLongClick = {
                val c = coords
                if (c != null && c.isAttached && name.isNotEmpty()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(Rect(c.localToScreen(Offset.Zero), c.size.toSize()))
                }
            }
        )
    )
}

@Composable
private fun GroupHeaderCard(
    name: String,
    count: Int,
    expanded: Boolean,
    surfaceModifier: Modifier,
    rowModifier: Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val ungrouped = name.isEmpty()
    val accent = remember(name) { if (ungrouped) Color(0xFF8A8A8A) else avatarColorFor(name) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 0f else 90f, label = "groupChevron")
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = surfaceModifier.fillMaxWidth()
    ) {
        Row(
            rowModifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ungrouped) Icons.Default.Inventory2 else Icons.Default.Folder,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (ungrouped) "بدون مجموعة" else name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun CatalogItemDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialGroup: String,
    groups: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, group: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var group by remember { mutableStateOf(initialGroup) }
    var creating by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val groupFocus = remember { FocusRequester() }
    val finalGroup = if (creating) newGroup.trim() else group
    val canSave = name.isNotBlank() && (!creating || newGroup.isNotBlank()) && !isSaving

    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    LaunchedEffect(creating) { if (creating) groupFocus.requestFocus() }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "اسم المادة",
                    placeholder = "اسم المادة...",
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { if (canSave) onSave(name.trim(), finalGroup) }
                    )
                )
                AppSectionTitle("المجموعة", Modifier.padding(top = 18.dp, bottom = 8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppChip("بدون", selected = !creating && group.isEmpty(), onClick = { creating = false; group = "" })
                    groups.forEach { g ->
                        AppChip(g, selected = !creating && group == g, onClick = { creating = false; group = g })
                    }
                    AppChip("+ مجموعة جديدة", selected = creating, onClick = { creating = true })
                }
                if (creating) {
                    AppTextField(
                        value = newGroup,
                        onValueChange = { newGroup = it },
                        label = "اسم المجموعة",
                        placeholder = "اسم المجموعة...",
                        enabled = !isSaving,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(groupFocus),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { if (canSave) onSave(name.trim(), finalGroup) }
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                shape = RectangleShape,
                onClick = { onSave(name.trim(), finalGroup) }
            ) {
                if (isSaving) {
                    com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving, shape = RectangleShape) { Text("إلغاء") } }
    )
}

@Composable
private fun GroupNameDialog(initialName: String, isSaving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("تعديل اسم المجموعة") },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "اسم المجموعة",
                placeholder = "اسم المجموعة...",
                enabled = !isSaving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { if (name.isNotBlank() && !isSaving) onSave(name.trim()) }
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !isSaving,
                shape = RectangleShape,
                onClick = { onSave(name.trim()) }
            ) {
                if (isSaving) {
                    com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                } else {
                    Text("حفظ")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving, shape = RectangleShape) { Text("إلغاء") } }
    )
}

@Composable
private fun QuantityEntryDialog(
    materialName: String,
    initialQuantity: Int = 1,
    initialUnit: MaterialUnit = MaterialUnit.KG,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, unit: String, notes: String, important: Boolean) -> Unit
) {
    // FIX: same free-typed-decimal issue as MaterialEditDialog (see its
    // comment) - this is the dialog actually used every time a new
    // shortage is added from the catalog, so it needed the identical fix:
    // a whole-number stepper instead of a text field that could take
    // "1.5" for a كيلو entry, plus the two new نص كيلو / ربع كيلو units.
    var quantity by remember { mutableStateOf(initialQuantity) }
    var unit by remember { mutableStateOf(initialUnit) }
    var notes by remember { mutableStateOf("") }
    var important by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(materialName) },
        confirmLeading = {
            com.shopmanager.app.ui.common.ImportantStarButton(
                checked = important,
                onCheckedChange = { important = it },
                enabled = !isSaving
            )
        },
        text = {
            Column {
                AppSectionTitle("الكمية المطلوبة", Modifier.padding(bottom = 8.dp))
                QuantityStepper(
                    value = quantity,
                    unitLabel = unit.label,
                    enabled = !isSaving,
                    onValueChange = { quantity = it.coerceAtLeast(1) }
                )
                AppSectionTitle("الوحدة", Modifier.padding(top = 18.dp, bottom = 8.dp))
                UnitPicker(selected = unit, enabled = !isSaving, onSelected = { unit = it })
                AppTextField(
                    value = notes, onValueChange = { notes = it }, enabled = !isSaving,
                    label = "ملاحظة (اختياري)",
                    singleLine = false, minLines = 1, maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp, start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                shape = RectangleShape,
                onClick = {
                    if (quantity <= 0) {
                        error = "أدخل كمية صحيحة"
                    } else {
                        error = null
                        onSave(quantity.toDouble(), unit.label, notes.trim(), important)
                    }
                }
            ) {
                if (isSaving) {
                    com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                } else {
                    Text("حفظ")
                }
            }
        },
        dismissButton = { TextButton(enabled = !isSaving, shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}
