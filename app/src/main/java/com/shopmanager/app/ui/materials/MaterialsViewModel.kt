package com.shopmanager.app.ui.materials

import com.shopmanager.app.data.retryOnError
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.data.learning.LearningHub
import com.shopmanager.app.data.learning.UsageArea
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.data.materials.MissingFinderStore
import com.shopmanager.app.data.materials.PendingMaterial
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.notifications.RemoteChangeProcessor
import com.shopmanager.app.data.notifications.SelfChangeLedger
import com.shopmanager.app.data.sync.SyncStatusStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

data class MaterialsUiState(
    val materials: List<Material> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true
)

class MaterialsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MaterialsRepository()

    private val section = MutableStateFlow("main")

    // PERF: same fix as DebtsViewModel — Firestore's listener can re-fire
    // with a list that's identical to the last one (e.g. a local write
    // getting server-acknowledged). distinctUntilChanged stops that from
    // recomputing/recomposing the whole screen for no visible change.
    // See DebtsViewModel.hasSyncError — same idea: a real listener error
    // (not just a legitimately empty collection) flips this, so Settings
    // can offer restoring the last local daily backup.
    private val _hasSyncError = MutableStateFlow(false)
    val hasSyncError: StateFlow<Boolean> = _hasSyncError

    // BUG FIXED (النسخ الاحتياطي التلقائي لا يعمل إلا على الجهاز الذي أضاف
    // المادة): InstantBackupWorker used to be requested only from this
    // ViewModel's own add/edit/delete functions further below — i.e. only
    // on the device where the person physically made the change. Every
    // other device signed into the same shop only ever learns about that
    // change through this same Firestore listener (materialsFlow/
    // pricesFlow), so it never took its own local snapshot until *its
    // own* user happened to add something. materialsFlow/pricesFlow below
    // now request a local instant backup on every real update they see —
    // regardless of which device produced it — so every signed-in device
    // stays backed up together instead of only the one that made the
    // edit. That onEach also resets [_hasSyncError] back to false: it used
    // to only ever be set true (on a listener error) and never cleared, so
    // a single network blip left Settings offering to restore from the
    // local backup forever afterward, even long after the connection and
    // live data had fully recovered.

    private val materialsFlow = channelFlow {
        section.collect { s ->
            repo.listenMaterials(s).collect { send(it) }
        }
    }
        .distinctUntilChanged()
        .onEach {
            _hasSyncError.value = false
            // PERF: طلب مؤجَّل ومُخفَّف (لا كتابة في قاعدة WorkManager مع كل لقطة).
            InstantBackupWorker.requestDeferred(getApplication())
            // "طبقة مساعدة للمزامنة" — see SyncStatus.kt.
            SyncStatusStore.recordSuccess(getApplication())
        }
        .retryOnError { _hasSyncError.value = true }

    private val pricesFlow = repo.listenPrices()
        .distinctUntilChanged()
        .onEach {
            _hasSyncError.value = false
            // PERF: طلب مؤجَّل ومُخفَّف (لا كتابة في قاعدة WorkManager مع كل لقطة).
            InstantBackupWorker.requestDeferred(getApplication())
            SyncStatusStore.recordSuccess(getApplication())
        }
        .retryOnError { _hasSyncError.value = true }

    // Same fix as DebtsViewModel.uiState: debounce the cache→server settle
    // burst on cold start so the dashboard's "قائمة النواقص" counter counts
    // up once to the real total instead of jittering through partial ones.
    // PERF: التجميع (groupBy/sum) والـdebounce وآثار onEach تجري على Dispatchers.Default
    // لا على الخيط الرئيسي (مجال viewModelScope)، فلا تأكل وقت الرسم على الهواتف الضعيفة.
    val uiState: StateFlow<MaterialsUiState> = combine(materialsFlow, pricesFlow) { materials, prices ->
        MaterialsUiState(materials = materials, prices = prices, isLoading = false)
    }.debounce(200).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaterialsUiState())

    val catalog: StateFlow<List<MaterialCatalogItem>> = repo.listenCatalog()
        .distinctUntilChanged()
        .retryOnError { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val finderStore = LearningHub.store(application)
    private val usage = LearningHub.usage(application)
    private val learnVersion = MutableStateFlow(0)

    val priceHistory: StateFlow<Map<String, List<PriceChange>>> = PriceHistoryStore.flow(application)

    init {
        viewModelScope.launch(LearningHub.io) {
            finderStore.preload()
            usage.preload()
            learnVersion.update { it + 1 }
        }
        observeResolved()
        observePriceChanges()
        observeSharedLearning()
    }

    /**
     * الذكاء مشترك عبر فايربيس: أي حدث جديد (من هذا الجهاز أو من جهاز آخر) يغيّر الإحصائيات،
     * فنحدّث الاقتراحات وبطاقة الصفحة الرئيسية تلقائياً بدل انتظار الدخول القادم للتطبيق.
     */
    private fun observeSharedLearning() {
        viewModelScope.launch {
            finderStore.version.debounce(800).collect {
                learnVersion.update { v -> v + 1 }
                if (lastEnterAt != 0L) refreshHomeSuggestions()
            }
        }
    }

    private fun observePriceChanges() {
        viewModelScope.launch(Dispatchers.Default) {
            var prev: Map<String, Double>? = null
            uiState.collect { st ->
                if (st.isLoading) return@collect
                val before = prev
                if (before != null && before.isNotEmpty() && before != st.prices) {
                    PriceHistoryStore.recordDiff(getApplication(), before, st.prices)
                }
                prev = st.prices
            }
        }
    }

    /**
     * أي مادة تختفي من قائمة النواقص (حذفتها هنا أو من جهاز آخر، أو "مسح الكل")
     * تعني أنها صارت متوفرة: نسجّلها كذلك فلا تُقترح من جديد قبل وقتها الطبيعي.
     * التعديل (إعادة تسمية) لا يُحسب حذفاً لأن المقارنة بالـ id، وتغيير القسم يعيد البداية.
     */
    private fun observeResolved() {
        viewModelScope.launch(Dispatchers.Default) {
            var prevSection: String? = null
            var prev: Map<String, String> = emptyMap()
            uiState.collect { st ->
                if (st.isLoading) return@collect
                val sec = section.value
                val now = st.materials.associate { it.id to it.name }
                if (prevSection == sec && prev.isNotEmpty()) {
                    val gone = prev.filterKeys { it !in now }.values.filter { n -> n !in now.values }
                    if (gone.isNotEmpty()) finderStore.recordResolved(gone) { learnVersion.update { it + 1 } }
                }
                prevSection = sec
                prev = now
            }
        }
    }

    /** أسماء ما هو ناقص الآن — لا يُقترح أبداً لأنه موجود على القائمة أصلاً. */
    private fun pendingNames(): List<String> = uiState.value.materials.map { it.name }

    val suggestions: StateFlow<List<MaterialCatalogItem>> = combine(
        catalog, learnVersion, uiState.map { st -> st.materials.map { it.name } }.distinctUntilChanged()
    ) { items, _, pending ->
        finderStore.suggested(items, 12, pending, frequentOnly = true)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rankedCatalog(items: List<MaterialCatalogItem>): List<MaterialCatalogItem> =
        finderStore.ranked(items, pendingNames())

    fun suggestedNow(items: List<MaterialCatalogItem>): List<MaterialCatalogItem> =
        finderStore.suggested(items, 60, pendingNames())

    fun suggestionFor(name: String): Pair<Int, MaterialUnit>? = finderStore.suggestion(name)

    fun insightFor(name: String): MissingFinderStore.Insight? = finderStore.insight(name)

    fun recordImportant(name: String) {
        finderStore.recordImportant(name) { learnVersion.update { it + 1 } }
    }

    fun recordExists(names: Collection<String>) {
        finderStore.recordExists(names) { learnVersion.update { it + 1 } }
    }

    fun recordSkipped(names: Collection<String>) {
        finderStore.recordSkipped(names)
    }

    fun partnerFor(name: String, pendingNames: Collection<String>): String? = finderStore.partnerOf(name, pendingNames)

    private fun learn(name: String, quantity: Double, unit: String, important: Boolean, batch: String = "") {
        finderStore.recordAdded(name, quantity, unit, important, batch) { learnVersion.update { it + 1 } }
    }

    fun recordUsage(area: UsageArea) {
        LearningHub.scope.launch { usage.record(area) }
    }

    private val _homeSuggestions = MutableStateFlow<List<HomeSuggestion>>(emptyList())
    val homeSuggestions: StateFlow<List<HomeSuggestion>> = _homeSuggestions

    private var enterJob: Job? = null
    private var lastEnterAt = 0L

    /** أنواع البطاقات التي قبلها/رفضها المستخدم في هذه الجلسة: لا تعود حتى لو تغيّرت الإحصائيات. */
    private val sessionHidden: MutableSet<com.shopmanager.app.data.learning.SuggestionKind> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun onAppEntered() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastEnterAt != 0L && now - lastEnterAt < 10_000L) return
        lastEnterAt = now
        sessionHidden.clear()
        LearningHub.scope.launch { usage.record(UsageArea.APP_OPEN) }
        refreshHomeSuggestions()
    }

    private fun refreshHomeSuggestions() {
        enterJob?.cancel()
        enterJob = viewModelScope.launch(Dispatchers.Default) {
            val items = withTimeoutOrNull(2500L) { catalog.first { it.isNotEmpty() } } ?: catalog.value
            val current = (withTimeoutOrNull(2500L) { uiState.first { !it.isLoading } } ?: uiState.value)
                .materials.map { it.name }
            val picks = withContext(LearningHub.io) {
                finderStore.preload()
                usage.pickSuggestions(finderStore, items, current, 4)
            }
            _homeSuggestions.value = picks.filter { it.kind !in sessionHidden }
        }
    }

    fun onSuggestionAccepted(s: HomeSuggestion) {
        sessionHidden += s.kind
        _homeSuggestions.value = _homeSuggestions.value.filter { it.kind != s.kind }
        LearningHub.scope.launch { usage.recordFeedback(s.kind, true) }
    }

    fun onSuggestionDismissed(s: HomeSuggestion) {
        sessionHidden += s.kind
        _homeSuggestions.value = _homeSuggestions.value.filter { it.kind != s.kind }
        LearningHub.scope.launch { usage.recordFeedback(s.kind, false) }
        if (s.names.isNotEmpty()) finderStore.recordSkipped(s.names)
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Pull-to-refresh: forces a real server round trip and keeps the
     * spinner up for a minimum, tactile duration either way. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val start = System.currentTimeMillis()
            try {
                repo.refreshFromServer()
            } catch (_: Exception) {
                // Live listeners remain the source of truth.
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 400) kotlinx.coroutines.delay(400 - elapsed)
            _isRefreshing.value = false
        }
    }

    /**
     * "التفريق بين هاتف وهاتف آخر" (المواد): اكتشاف تغيّر قائمة النواقص القادم
     * من الأجهزة الأخرى صار في مكان واحد (RemoteChangeWatcher +
     * RemoteChangeProcessor) يراقب **كل الأقسام** ويعمل حتى والتطبيق مغلق.
     *
     * الخطأ القديم هنا وأُصلح: كان هذا الـ ViewModel يحتفظ بمجموعة ids
     * "لمستُها أنا" في الذاكرة، ولا يحذف الـ id منها إلا إذا حدث تغيير فعلي.
     * فلو ضغطتَ "حفظ" على تعديل دون تغيير حقيقي بقي الـ id مُعلَّماً، ثم إذا
     * عدّل هاتف آخر نفس المادة لاحقاً اعتُبر تعديله "منّي" ولم يصلك إشعار.
     * الآن نسجّل في [SelfChangeLedger] **بصمة الحالة التي كتبها هذا الجهاز**
     * (اسم|كمية|وحدة)، ويُعتبر التغيير منّي فقط إذا طابقت الحالة الحالية هذه
     * البصمة؛ أي تعديل من هاتف آخر يعطي بصمة مختلفة فيُعامَل كتغيير خارجي.
     */
    private fun markWrittenHere(id: String, name: String, quantity: Double, unit: String) {
        SelfChangeLedger.markMaterialWritten(
            getApplication(), id, RemoteChangeProcessor.materialSignature(name, quantity, unit)
        )
    }

    private fun markDeletedHere(ids: Collection<String>) {
        SelfChangeLedger.markMaterialsDeleted(getApplication(), ids)
    }

    // BUG FIXED (لا يوجد مؤشر تحميل بواجهة إضافة/تعديل مادة): مثل نفس
    // مشكلة PersonEditDialog قديماً - onDone هون تسمح للواجهة (MaterialEditDialog/
    // QuantityEntryDialog) توقف زر الحفظ وتعرض دائرة تحميل لحد ما الكتابة
    // بفايرستور تخلص فعلياً، بدل ما توهم إنها علقت أو تسمح بضغطات متكررة.
    fun addMaterial(name: String, quantity: Double, unit: String, notes: String = "", important: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val topOrder = (uiState.value.materials.minOfOrNull { it.order } ?: 0L) - 1
                // BUG FIXED (see MaterialsRepository.addMaterial): register
                // the id via onIdAssigned the instant it's generated
                // (client-side, before the write is even sent) instead of
                // after the whole suspend call returns — closes the race
                // where the live listener could fire first and notify this
                // same device about the material it just added.
                val newId = repo.addMaterial(name, quantity, unit, section.value, notes) { id -> markWrittenHere(id, name, quantity, unit) }
                if (important) repo.setImportant(newId, true, topOrder)
                learn(name, quantity, unit, important)
                _message.value = "تمت إضافة النقص بنجاح"
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun addMaterialsBatch(items: List<PendingMaterial>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var ok = 0
            val batchId = java.util.UUID.randomUUID().toString().take(12)
            val baseOrder = (uiState.value.materials.minOfOrNull { it.order } ?: 0L) - 1
            for (p in items) {
                try {
                    val id = repo.addMaterial(p.name, p.quantity, p.unit, section.value, p.notes) { newId ->
                        markWrittenHere(newId, p.name, p.quantity, p.unit)
                    }
                    if (p.important) repo.setImportant(id, true, baseOrder - ok)
                    learn(p.name, p.quantity, p.unit, p.important, batchId)
                    ok++
                } catch (e: Exception) {
                    _message.value = "تعذرت إضافة \"${p.name}\": ${e.message ?: "تحقق من الاتصال"}"
                }
            }
            if (ok > 0) {
                _message.value = "تمت إضافة $ok مادة إلى النواقص"
                InstantBackupWorker.requestNow(getApplication())
            }
            onDone(ok)
        }
    }

    fun updateMaterial(id: String, name: String, quantity: Double, unit: String, notes: String = "", onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                markWrittenHere(id, name, quantity, unit)
                repo.updateMaterial(id, name, quantity, unit, section.value, notes)
                _message.value = "تم تعديل النقص بنجاح"
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    private val _undo = MutableStateFlow<(() -> Unit)?>(null)
    val undo: StateFlow<(() -> Unit)?> = _undo
    fun clearUndo() { _undo.value = null }
    fun performUndo() {
        val action = _undo.value ?: return
        _undo.value = null
        action()
    }

    fun deleteMaterial(id: String) {
        val snapshot = uiState.value.materials.firstOrNull { it.id == id }
        viewModelScope.launch {
            try {
                markDeletedHere(listOf(id))
                repo.deleteMaterial(id)
                if (snapshot != null) {
                    _undo.value = {
                        addMaterial(snapshot.name, snapshot.quantity, snapshot.unit, snapshot.notes, snapshot.important)
                    }
                }
                _message.value = "تم حذف المادة بنجاح"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "خطأ أثناء الحذف: ${e.message}"
            }
        }
    }

    /**
     * FEATURE ADDED ("نجمة الأهمية"): toggling on pins the material to the
     * top of the list by giving it an `order` one less than the current
     * minimum among today's materials (so it sorts before literally
     * everything else); toggling off just flips the flag back and leaves
     * its position as-is.
     */
    fun setImportant(material: Material, important: Boolean) {
        viewModelScope.launch {
            try {
                val topOrder = if (important) {
                    (uiState.value.materials.minOfOrNull { it.order } ?: 0L) - 1
                } else null
                repo.setImportant(material.id, important, topOrder)
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر التحديث: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    /**
     * FEATURE ADDED ("ترتيب المواد بالضغط المطول"): called once a drag
     * finishes with the full list in its new order; persists a fresh
     * sequential `order` per id in one batch (see
     * MaterialsRepository.updateMaterialsOrder).
     */
    fun reorderMaterials(orderedIds: List<String>) {
        viewModelScope.launch {
            try {
                repo.updateMaterialsOrder(orderedIds)
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حفظ الترتيب: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    /**
     * "مسح الكل" (clear all): deletes every material currently on the list
     * for the active section in one batched call, same effect as
     * select-all-then-delete but without needing a whole multi-select mode
     * on the list UI.
     */
    fun deleteAllMaterials() {
        val ids = uiState.value.materials.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                markDeletedHere(ids)
                repo.deleteMaterials(ids)
                _message.value = "تم حذف كل المواد (${ids.size})"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حذف المواد: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun setPrice(materialName: String, price: Double) {
        val before = uiState.value.prices[materialName]
        viewModelScope.launch {
            try {
                repo.setPrice(materialName, price)
                PriceHistoryStore.record(getApplication(), materialName, before, price)
                _message.value = "تم حفظ السعر"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حفظ السعر: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    /** حفظ كل الأسعار المعدّلة دفعة واحدة في السحابة مع رسالة واحدة. */
    fun setPrices(prices: Map<String, Double>) {
        if (prices.isEmpty()) return
        val before = uiState.value.prices
        viewModelScope.launch {
            try {
                repo.setPrices(prices)
                PriceHistoryStore.recordAll(getApplication(), before, prices)
                _message.value = if (prices.size == 1) "تم حفظ السعر" else "تم حفظ ${prices.size} أسعار"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حفظ الأسعار: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun addCatalogItem(name: String, group: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (repo.catalogNameExists(name)) {
                    _message.value = "\"$name\" موجودة بالقائمة مسبقاً"
                    onDone(false)
                    return@launch
                }
                repo.addCatalogItem(name, group)
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    /**
     * FEATURE ADDED ("تعديل المواد الثابتة بعد إضافتها"): renames a fixed
     * catalog entry. Blocks renaming to a name that already exists
     * elsewhere in the catalog, same duplicate check as adding a new one -
     * but allows saving with no change (renaming "X" to "X" again).
     */
    fun updateCatalogItem(id: String, currentName: String, newName: String, group: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (newName != currentName && repo.catalogNameExists(newName)) {
                    _message.value = "\"$newName\" موجودة بالقائمة مسبقاً"
                    onDone(false)
                    return@launch
                }
                repo.updateCatalogItem(id, newName, group)
                if (newName != currentName) {
                    finderStore.rename(currentName, newName) { learnVersion.update { it + 1 } }
                }
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun renameCatalogGroup(oldName: String, newName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val target = newName.trim()
                val ids = catalog.value.filter { it.group == oldName }.map { it.id }
                if (target.isNotEmpty() && ids.isNotEmpty()) repo.setCatalogGroup(ids, target)
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun deleteCatalogGroup(name: String) {
        viewModelScope.launch {
            try {
                val ids = catalog.value.filter { it.group == name }.map { it.id }
                if (ids.isNotEmpty()) repo.setCatalogGroup(ids, "")
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر الحذف: ${e.message}"
            }
        }
    }

    fun deleteCatalogItem(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteCatalogItem(id)
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر الحذف: ${e.message}"
            }
        }
    }
}
