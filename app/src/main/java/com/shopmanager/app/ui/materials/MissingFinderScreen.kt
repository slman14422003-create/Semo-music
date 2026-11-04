package com.shopmanager.app.ui.materials

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.data.materials.MissingFinderStore
import com.shopmanager.app.data.materials.PendingMaterial
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.AppSpinner
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.ArabicText
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.PillBadge
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay

@Composable
fun MissingFinderScreen(viewModel: MaterialsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var waited by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.recordUsage(com.shopmanager.app.data.learning.UsageArea.MISSING_FINDER)
        delay(2500)
        waited = true
    }
    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    val ready = catalog.isNotEmpty() && !state.isLoading
    var onlySuggested by remember { mutableStateOf(false) }
    val lowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    val listedKeys = remember(ready) {
        if (!ready) emptySet() else state.materials.map { ArabicText.normalize(it.name) }.toSet()
    }
    val suggestedAll = remember(ready) { if (ready) viewModel.suggestedNow(catalog) else emptyList() }
    val rankedAll = remember(ready) { if (ready) viewModel.rankedCatalog(catalog) else emptyList() }
    val hasSuggestions = suggestedAll.isNotEmpty()
    val queue: List<MaterialCatalogItem> = remember(ready, onlySuggested) {
        if (!ready) emptyList()
        else (if (onlySuggested) suggestedAll else rankedAll).filter { ArabicText.normalize(it.name) !in listedKeys }
    }
    val alreadyListed = remember(ready) {
        if (!ready) 0 else catalog.count { ArabicText.normalize(it.name) in listedKeys }
    }

    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Answer>() }
    var isSaving by remember { mutableStateOf(false) }
    var finishedEarly by remember { mutableStateOf(false) }

    val done = ready && queue.isNotEmpty() && (index >= queue.size || finishedEarly)
    val fadeInSpec = MotionSpecs.contentTween<Float>()
    val fadeOutSpec = MotionSpecs.listItemFadeOut()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            ScreenTopBar(
                title = "إيجاد المواد الناقصة",
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ready && hasSuggestions && answers.isEmpty() && index == 0 && !finishedEarly) {
                ModeRow(
                    onlySuggested = onlySuggested,
                    onChange = { onlySuggested = it }
                )
            }
            when {
                !ready && catalog.isEmpty() && waited && !state.isLoading -> AppEmptyState(
                    icon = Icons.Default.Spa,
                    text = "لا توجد مواد في القائمة الثابتة بعد"
                )
                !ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppSpinner(size = 28.dp, color = MaterialTheme.colorScheme.onSurface)
                }
                queue.isEmpty() -> AppEmptyState(
                    icon = Icons.Default.TaskAlt,
                    text = "كل مواد القائمة موجودة أصلاً في قائمة النواقص"
                )
                done -> SummaryStep(
                    pending = answers.mapNotNull { it.pending },
                    isSaving = isSaving,
                    onRemove = { name -> answers.removeAll { it.pending?.name == name } },
                    onConfirm = {
                        val pendingList = answers.mapNotNull { it.pending }
                        viewModel.recordExists(answers.filter { it.pending == null && !it.skipped }.map { it.name })
                        viewModel.recordSkipped(answers.filter { it.skipped }.map { it.name })
                        if (pendingList.isEmpty()) {
                            onBack()
                        } else {
                            isSaving = true
                            viewModel.addMaterialsBatch(pendingList) { onBack() }
                        }
                    },
                    onBackToQuestions = if (finishedEarly || index > 0) ({
                        if (finishedEarly) {
                            finishedEarly = false
                        } else {
                            index = queue.size - 1
                            answers.removeLastOrNull()
                        }
                    }) else null
                )
                else -> {
                    val total = queue.size
                    val missingCount = answers.count { it.pending != null }
                    val skippedCount = answers.count { it.skipped }
                    val existsCount = answers.size - missingCount - skippedCount
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = AppScreenPadding, vertical = 6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    " / $total",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            if (existsCount > 0) {
                                CounterChip(Icons.Default.Check, existsCount, LocalSemanticColors.current.success)
                            }
                            if (skippedCount > 0) {
                                CounterChip(Icons.Default.SkipNext, skippedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (missingCount > 0) {
                                CounterChip(Icons.Default.Close, missingCount, MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { index.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.onSurface,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                    val step: @Composable (Int) -> Unit = { i ->
                        val item = queue[i.coerceIn(0, queue.lastIndex)]
                        val previous = answers.firstOrNull { it.name == item.name }
                        val pendingNames = answers.mapNotNull { it.pending?.name }
                        ItemStep(
                            item = item,
                            insight = remember(item.name) { viewModel.insightFor(item.name) },
                            suggestion = remember(item.name) { viewModel.suggestionFor(item.name) },
                            partner = remember(item.name, pendingNames.size) { viewModel.partnerFor(item.name, pendingNames) },
                            initial = previous?.pending,
                            canGoBack = i > 0,
                            onGoBack = {
                                if (index > 0) {
                                    answers.removeLastOrNull()
                                    index -= 1
                                }
                            },
                            onFinishNow = { finishedEarly = true },
                            hasPending = answers.any { it.pending != null },
                            onExists = {
                                answers.removeAll { it.name == item.name }
                                answers.add(Answer(item.name, null))
                                index += 1
                            },
                            onSkip = {
                                answers.removeAll { it.name == item.name }
                                answers.add(Answer(item.name, null, skipped = true))
                                index += 1
                            },
                            onMissing = { pending ->
                                answers.removeAll { it.name == item.name }
                                answers.add(Answer(item.name, pending))
                                index += 1
                            }
                        )
                    }
                    if (lowTier) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            key(index) { step(index) }
                        }
                    } else {
                        AnimatedContent(
                            targetState = index,
                            transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "finderStep"
                        ) { i -> step(i) }
                    }
                    if (alreadyListed > 0) {
                        AppFootnote(
                            "تم تخطي $alreadyListed مادة موجودة أصلاً في قائمة النواقص",
                            modifier = Modifier.padding(horizontal = AppScreenPadding, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Answer(val name: String, val pending: PendingMaterial?, val skipped: Boolean = false)

@Composable
private fun CounterChip(icon: ImageVector, count: Int, color: Color) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ModeRow(onlySuggested: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppScreenPadding, vertical = 6.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeSegment("كل المواد", !onlySuggested, false, { onChange(false) }, Modifier.weight(1f))
        ModeSegment("المقترحة فقط", onlySuggested, true, { onChange(true) }, Modifier.weight(1f))
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, icon: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) cs.onSurface else Color.Transparent,
        contentColor = if (selected) cs.surface else cs.onSurfaceVariant,
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemStep(
    item: MaterialCatalogItem,
    insight: MissingFinderStore.Insight?,
    suggestion: Pair<Int, MaterialUnit>?,
    partner: String?,
    initial: PendingMaterial?,
    canGoBack: Boolean,
    hasPending: Boolean,
    onGoBack: () -> Unit,
    onFinishNow: () -> Unit,
    onExists: () -> Unit,
    onSkip: () -> Unit,
    onMissing: (PendingMaterial) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val color = remember(item.name) { avatarColorFor(item.name) }
    var asking by remember { mutableStateOf(initial == null) }
    var qty by remember { mutableIntStateOf(initial?.quantity?.toInt() ?: suggestion?.first ?: 1) }
    var unit by remember { mutableStateOf(initial?.let { MaterialUnit.fromLabel(it.unit) } ?: suggestion?.second ?: MaterialUnit.KG) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showNote by remember { mutableStateOf(!initial?.notes.isNullOrBlank()) }
    var important by remember { mutableStateOf(initial?.important ?: false) }
    LaunchedEffect(Unit) { if (initial != null) asking = false }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppScreenPadding, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!asking) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { showNote = !showNote }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "ملاحظة",
                                        tint = if (showNote || notes.isNotBlank()) cs.onSurface else cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.matchParentSize().clip(CircleShape).background(color.copy(alpha = 0.18f)))
                            AppIconCircle(color = color, size = 84.dp) {
                                Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        val hasBadges = insight != null || partner != null
                        if (hasBadges) {
                            Spacer(Modifier.height(14.dp))
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (insight != null) {
                                    if (insight.exists >= 2 && insight.exists > insight.added) {
                                        PillBadge(text = "غالباً موجودة", color = semantic.success)
                                    } else if (insight.added >= 2) {
                                        PillBadge(text = "تنقص كثيراً (${insight.added} مرات)", color = cs.error)
                                    } else if (insight.added == 1) {
                                        PillBadge(text = "أضفتها مرة", color = semantic.warning)
                                    }
                                    if (insight.due) {
                                        PillBadge(text = "حان وقت نقصها", color = semantic.warning)
                                    }
                                    insight.daysSinceLast?.let { days ->
                                        if (insight.added > 0) {
                                            PillBadge(
                                                text = if (days == 0) "آخر مرة اليوم" else "آخر مرة منذ $days يوم",
                                                color = semantic.info
                                            )
                                        }
                                    }
                                    insight.pattern?.let { pattern ->
                                        PillBadge(
                                            text = if (insight.patternToday) "$pattern — اليوم يومها" else pattern,
                                            color = if (insight.patternToday) semantic.success else semantic.info
                                        )
                                    }
                                }
                                if (partner != null) {
                                    PillBadge(text = "غالباً تنقص مع $partner", color = semantic.info)
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            if (asking) "هل المادة موجودة؟" else "كم ينقص منها؟",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                    }
                }
            }

            if (!asking) {
                Spacer(Modifier.height(16.dp))
                QuantityStepper(
                    value = qty,
                    unitLabel = unit.label,
                    onValueChange = { qty = it.coerceAtLeast(1) }
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 5, 10).forEach { n ->
                        Surface(
                            onClick = { qty = n },
                            shape = CircleShape,
                            color = if (qty == n) cs.onSurface else cs.surfaceContainerHigh,
                            contentColor = if (qty == n) cs.surface else cs.onSurface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "$n",
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                AppSectionTitle("الوحدة", Modifier.padding(top = 16.dp, bottom = 8.dp))
                UnitPicker(selected = unit, onSelected = { unit = it })
                if (showNote) {
                    AppTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "ملاحظة (اختياري)",
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = AppScreenPadding, end = AppScreenPadding, top = 6.dp, bottom = 4.dp)
        ) {
            if (asking) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppPillButton(
                        label = "لا، ناقصة",
                        icon = Icons.Default.Close,
                        onClick = { asking = false },
                        height = 64.dp,
                        modifier = Modifier.weight(1f)
                    )
                    AppPillButton(
                        label = "نعم، موجودة",
                        icon = Icons.Default.Check,
                        onClick = onExists,
                        tonal = true,
                        height = 64.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppPillButton(
                        label = "تراجع",
                        onClick = { asking = true },
                        tonal = true,
                        modifier = Modifier.weight(1f)
                    )
                    com.shopmanager.app.ui.common.ImportantStarButton(
                        checked = important,
                        onCheckedChange = { important = it }
                    )
                    AppPillButton(
                        label = "تأكيد",
                        icon = Icons.Default.Check,
                        onClick = {
                            onMissing(PendingMaterial(item.name, qty.toDouble(), unit.label, notes.trim(), important))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) TextButton(onClick = onGoBack) { Text("السابق") }
                if (asking) {
                    TextButton(onClick = onSkip) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تخطي")
                    }
                }
                if (hasPending) TextButton(onClick = onFinishNow) { Text("إنهاء ومراجعة") }
            }
        }
    }
}

@Composable
private fun SummaryStep(
    pending: List<PendingMaterial>,
    isSaving: Boolean,
    onRemove: (String) -> Unit,
    onConfirm: () -> Unit,
    onBackToQuestions: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    if (pending.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppEmptyState(icon = Icons.Default.TaskAlt, text = "لا توجد نواقص، كل شيء موجود")
            }
            AppPillButton(
                label = "إنهاء",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().padding(AppScreenPadding)
            )
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(AppGroupGap)
        ) {
            item(key = "title") {
                AppSectionTitle("المواد الناقصة (${pending.size})", Modifier.padding(bottom = 4.dp))
            }
            itemsIndexed(pending, key = { _, p -> p.name }) { index, p ->
                AppRowSurface(shape = groupedRowShape(index, pending.lastIndex)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconCircle(color = avatarColorFor(p.name), size = 40.dp) {
                            Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (p.notes.isNotBlank()) {
                                Text(
                                    p.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                        PillBadge(text = "${p.quantity.toInt()} ${p.unit}".trim(), color = cs.onSurface)
                        if (p.important) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5B301), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onRemove(p.name) }) {
                            Icon(Icons.Default.Close, contentDescription = "إزالة", tint = cs.error)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(AppScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBackToQuestions != null) {
                AppPillButton(
                    label = "السابق",
                    onClick = onBackToQuestions,
                    tonal = true,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
            AppPillButton(
                label = if (isSaving) "جارٍ الإضافة..." else "إضافة (${pending.size})",
                icon = Icons.Default.AutoAwesome,
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.weight(if (onBackToQuestions != null) 2f else 1f)
            )
        }
    }
}
