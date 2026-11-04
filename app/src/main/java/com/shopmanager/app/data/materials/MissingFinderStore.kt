package com.shopmanager.app.data.materials

import android.content.Context
import com.shopmanager.app.data.learning.DebouncedTask
import com.shopmanager.app.data.learning.LearningHub
import com.shopmanager.app.data.retryOnError
import com.shopmanager.app.ui.common.ArabicText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class PendingMaterial(
    val name: String,
    val quantity: Double,
    val unit: String,
    val notes: String = "",
    val important: Boolean = false
)

/**
 * الذكاء المحلي لقائمة النواقص — نسخة مشتركة بين كل المستخدمين.
 *
 * كيف يعمل:
 *  1. كل ما يحدث (إضافة نقص، صار متوفراً، "موجودة"، تخطّي…) يُسجَّل كـ**حدث** في فايربيس
 *     ([AiEventSync]) ويُحفظ نسخة منه على الجهاز ليعمل دون اتصال.
 *  2. كل جهاز يعيد بناء الإحصائيات من نفس الأحداث بترتيب الزمن بنفس الخوارزمية، فتظهر
 *     نفس الاقتراحات عند الجميع بلا تعارض ولا عدّادات تتضارب.
 *  3. الاستعلامات قراءات من الذاكرة فقط ولا تلمس القرص.
 *
 * الخيوط: كل التعديلات تمر على خيط التعلّم الواحد؛ [events] و[statsMap] محميتان بـ synchronized.
 */
class MissingFinderStore(context: Context) {

    private val appCtx = context.applicationContext
    private val prefs = appCtx.getSharedPreferences("shop_missing_finder", Context.MODE_PRIVATE)
    private val cacheFile = File(appCtx.filesDir, "ai_events_cache.json")

    data class Stat(
        val added: Int = 0,
        val important: Int = 0,
        val exists: Int = 0,
        val skipped: Int = 0,
        val lastAdded: Long = 0L,
        val avgGapDays: Float = 0f,
        val gapDev: Float = 0f,
        val qty: Int = 1,
        val unit: String = MaterialUnit.KG.label,
        val name: String = "",
        /** عدد مرات الإضافة في كل يوم من الأسبوع (0 = الاثنين … 6 = الأحد). */
        val weekdays: IntArray = IntArray(7),
        /** نفس العدّاد لكن بأوزان تتناقص مع الزمن (نصف عمر [WD_HALF_LIFE_DAYS] يوماً). */
        val wdW: FloatArray = FloatArray(7),
        val wdT: Long = 0L,
        /** آخر مرة صارت فيها المادة متوفرة (حُذفت من النواقص أو أُجيب "موجودة"). */
        val lastResolved: Long = 0L,
        /** متوسط المدة (بالأيام) التي تبقى فيها المادة متوفرة قبل أن تنقص من جديد. */
        val avgLife: Float = 0f,
        /** أحدث أوقات الإضافة (حتى [MAX_TS]) لحساب معدل الطلب الأخير. */
        val recent: LongArray = LongArray(0),
        /** آخر كميات ووحدات طُلبت (حتى [MAX_Q]) لاقتراح الكمية "المعتادة" لا الأخيرة فقط. */
        val recentQty: IntArray = IntArray(0),
        val recentUnits: List<String> = emptyList()
    )

    data class Insight(
        val added: Int,
        val exists: Int,
        val daysSinceLast: Int?,
        val qty: Int,
        val unit: MaterialUnit,
        val avgGapDays: Int?,
        val due: Boolean,
        /** وصف نمط التكرار: "عادةً تنقص يوم الأحد (4 من 5 مرات)" أو "بلا يوم ثابت…". */
        val pattern: String? = null,
        /** اليوم هو أحد أيام النمط الثابت لهذه المادة. */
        val patternToday: Boolean = false
    )

    /** نمط أيام الأسبوع المكتشف لمادة: [days] أيام ثابتة، [hits] من [total] مرات. */
    private data class Pattern(val days: List<Int>, val hits: Int, val total: Int, val weekly: Boolean)

    private val events = HashMap<String, AiEvent>()
    private var statsMap = HashMap<String, Stat>()
    private var pairsMap = HashMap<String, MutableMap<String, Int>>()
    private val keyCache = ConcurrentHashMap<String, String>()
    @Volatile private var loaded = false
    private var syncJob: Job? = null

    private val _version = MutableStateFlow(0)

    /** يزيد مع كل إعادة بناء (حدث محلي أو قادم من جهاز آخر) ليحدّث الواجهة اقتراحاتها. */
    val version: StateFlow<Int> = _version

    private val saver = DebouncedTask(1200L) { saveCache() }

    private fun key(name: String): String {
        if (keyCache.size > 3000) keyCache.clear()
        return keyCache.getOrPut(name) { ArabicText.normalize(name) }
    }

    // ───────────────────────── التحميل والمزامنة ─────────────────────────

    fun preload() {
        synchronized(this) { ensureLoaded() }
        startSync()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loadCache()
        rebuildLocked()
    }

    @Synchronized
    private fun startSync() {
        if (syncJob != null) return
        syncJob = LearningHub.scope.launch {
            runCatching { migrateLegacyOnce() }
            runCatching { purgeWeekly() }
            AiEventSync.listen().retryOnError { }.collect { incoming ->
                var changed = false
                synchronized(this@MissingFinderStore) {
                    ensureLoaded()
                    for (e in incoming) if (events.put(e.id, e) == null) changed = true
                    if (changed) rebuildLocked()
                }
                if (changed) saver.trigger()
            }
        }
    }

    private suspend fun purgeWeekly() {
        val last = prefs.getLong(KEY_PURGE_AT, 0L)
        val now = System.currentTimeMillis()
        if (now - last < 7 * DAY_MS) return
        AiEventSync.purgeOld()
        prefs.edit().putLong(KEY_PURGE_AT, now).apply()
    }

    /**
     * ترحيل إحصائيات النسخة القديمة (المحلية فقط) مرة واحدة: تتحول إلى أحداث ذات معرّفات
     * حتمية فتُرفع لفايربيس ويراها الجميع دون تكرار حتى لو رحّلها أكثر من جهاز.
     */
    private fun migrateLegacyOnce() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val raw = prefs.getString(KEY_LEGACY_STATS, null)
        val seeds = ArrayList<AiEvent>()
        if (raw != null) runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { k ->
                val o = obj.getJSONObject(k)
                val name = o.optString("n", "").ifBlank { k }
                val added = o.optInt("a")
                val last = o.optLong("t")
                if (added <= 0 && o.optInt("i") <= 0 && o.optInt("e") <= 0) return@forEach
                val ts = o.optJSONArray("ts")?.let { a -> List(a.length()) { i -> a.optLong(i) } } ?: emptyList()
                val rq = o.optJSONArray("rq")?.let { a -> List(a.length()) { i -> a.optInt(i, 1) } } ?: emptyList()
                val ru = o.optJSONArray("ru")?.let { a -> List(a.length()) { i -> a.optString(i) } } ?: emptyList()
                val stamps = if (ts.isNotEmpty()) ts else if (last > 0L) listOf(last) else emptyList()
                stamps.forEachIndexed { idx, t ->
                    if (t <= 0L) return@forEachIndexed
                    val fromEnd = stamps.size - 1 - idx
                    val q = rq.getOrNull(rq.size - 1 - fromEnd) ?: o.optInt("q", 1)
                    val u = ru.getOrNull(ru.size - 1 - fromEnd) ?: o.optString("u", MaterialUnit.KG.label)
                    seeds += seed(AiEvent.ADDED, name, t, qty = q.coerceAtLeast(1), unit = u)
                }
                val anchor = if (last > 0L) last - 1 else 0L
                if (anchor > 0L) {
                    val ex = o.optInt("e")
                    if (ex > 0) seeds += seed(AiEvent.EXISTS, name, anchor, count = ex)
                    val sk = o.optInt("s")
                    if (sk > 0) seeds += seed(AiEvent.SKIPPED, name, anchor, count = sk)
                    val im = o.optInt("i")
                    if (im > 0) seeds += seed(AiEvent.IMPORTANT, name, anchor, count = im)
                }
                val r = o.optLong("r", 0L)
                if (r > 0L) seeds += seed(AiEvent.RESOLVED, name, r)
            }
        }
        if (seeds.isNotEmpty()) {
            synchronized(this) {
                ensureLoaded()
                seeds.forEach { events[it.id] = it }
                rebuildLocked()
            }
            AiEventSync.writeAll(seeds)
            saver.trigger()
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun seed(type: String, name: String, ts: Long, qty: Int = 1, unit: String = "", count: Int = 1): AiEvent =
        ev(type, name, ts, qty = qty, unit = unit, count = count)

    // ───────────────────────── نسخة الأحداث على الجهاز ─────────────────────────

    private fun loadCache() {
        if (!cacheFile.exists()) return
        runCatching {
            val arr = JSONArray(cacheFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isEmpty()) continue
                events[id] = AiEvent(
                    id = id, key = o.optString("k"), name = o.optString("n"), type = o.optString("t"),
                    ts = o.optLong("ts"), qty = o.optInt("q", 1), unit = o.optString("u"),
                    important = o.optBoolean("i"), batch = o.optString("b"), to = o.optString("to"),
                    count = o.optInt("c", 1).coerceAtLeast(1)
                )
            }
        }
    }

    private fun saveCache() {
        val snapshot = synchronized(this) { events.values.toList() }
        runCatching {
            val arr = JSONArray()
            snapshot.forEach { e ->
                arr.put(
                    JSONObject().put("id", e.id).put("k", e.key).put("n", e.name).put("t", e.type)
                        .put("ts", e.ts).put("q", e.qty).put("u", e.unit).put("i", e.important)
                        .put("b", e.batch).put("to", e.to).put("c", e.count)
                )
            }
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    fun flushNow() = saver.flushNow()

    // ───────────────────────── إعادة بناء الإحصائيات من الأحداث ─────────────────────────

    /** يُستدعى داخل synchronized(this). */
    private fun rebuildLocked() {
        val now = System.currentTimeMillis()
        val from = now - AiEventSync.WINDOW_DAYS * DAY_MS
        if (events.size > MAX_EVENTS) {
            val drop = events.values.sortedBy { it.ts }.take(events.size - MAX_EVENTS + 200).map { it.id }
            drop.forEach { events.remove(it) }
        }
        val list = events.values
            .filter { it.key.isNotEmpty() && it.ts in from..(now + DAY_MS) }
            .sortedWith(compareBy<AiEvent> { it.ts }.thenBy { it.id })
        val st = HashMap<String, Stat>()
        val alias = HashMap<String, String>()
        val batches = HashMap<String, MutableList<String>>()
        for (e in list) applyEvent(st, alias, batches, e)

        fun canon(k: String): String {
            var cur = k
            var guard = 0
            while (guard++ < 6) cur = alias[cur] ?: break
            return cur
        }
        val pairs = HashMap<String, MutableMap<String, Int>>()
        for (members in batches.values) {
            val keys = members.map { canon(it) }.distinct().take(MAX_BATCH)
            if (keys.size < 2) continue
            for (a in keys) {
                val m = pairs.getOrPut(a) { HashMap() }
                for (b in keys) if (a != b) m[b] = (m[b] ?: 0) + 1
            }
        }
        for (m in pairs.values) {
            if (m.size > MAX_PARTNERS) {
                val keep = m.entries.sortedByDescending { it.value }.take(MAX_PARTNERS).associate { it.key to it.value }
                m.clear(); m.putAll(keep)
            }
        }
        statsMap = st
        pairsMap = pairs
        loaded = true
        _version.value = _version.value + 1
    }

    private fun decayed(s: Stat, now: Long): FloatArray {
        val out = s.wdW.copyOf()
        if (s.wdT in 1 until now) {
            val f = 0.5.pow((now - s.wdT).toDouble() / (WD_HALF_LIFE_DAYS * DAY_MS)).toFloat()
            for (i in out.indices) out[i] *= f
        }
        return out
    }

    private fun applyEvent(
        st: HashMap<String, Stat>,
        alias: HashMap<String, String>,
        batches: HashMap<String, MutableList<String>>,
        e: AiEvent
    ) {
        val k = e.key
        val now = e.ts
        when (e.type) {
            AiEvent.ADDED -> {
                val old = st[k] ?: Stat()
                val gap = if (old.lastAdded > 0L) (now - old.lastAdded).toFloat() / DAY_MS else 0f
                var avg = old.avgGapDays
                var dev = old.gapDev
                if (gap >= 0.5f) {
                    if (avg <= 0f) {
                        avg = gap
                    } else {
                        dev = dev * 0.6f + kotlin.math.abs(gap - avg) * 0.4f
                        avg = avg * 0.6f + gap * 0.4f
                    }
                }
                val di = weekdayOf(now)
                val wd = old.weekdays.copyOf()
                wd[di]++
                val w = decayed(old, now)
                w[di] += 1f
                // كم بقيت المادة متوفرة بعد آخر مرة صارت فيها متوفرة؟
                var life = old.avgLife
                if (old.lastResolved > 0L && old.lastResolved >= old.lastAdded) {
                    val l = (now - old.lastResolved).toFloat() / DAY_MS
                    if (l >= 0.5f) life = if (life <= 0f) l else life * 0.6f + l * 0.4f
                }
                val q = e.qty.coerceAtLeast(1)
                st[k] = old.copy(
                    added = old.added + 1,
                    important = old.important + if (e.important) 1 else 0,
                    lastAdded = now,
                    avgGapDays = avg,
                    gapDev = dev,
                    qty = q,
                    unit = e.unit.ifEmpty { old.unit },
                    name = e.name,
                    weekdays = wd,
                    wdW = w,
                    wdT = now,
                    avgLife = life,
                    recent = (old.recent.toList() + now).takeLast(MAX_TS).toLongArray(),
                    recentQty = (old.recentQty.toList() + q).takeLast(MAX_Q).toIntArray(),
                    recentUnits = (old.recentUnits + e.unit).takeLast(MAX_Q)
                )
                if (e.batch.isNotEmpty()) batches.getOrPut(e.batch) { ArrayList() }.add(k)
            }
            AiEvent.RESOLVED -> {
                val old = st[k] ?: return
                if (old.added == 0) return
                // حذف مكرر خلال دقيقة (جهازان، أو حذف + مزامنة) لا يُحسب مرتين.
                if (old.lastResolved >= old.lastAdded && now - old.lastResolved < 60_000L) return
                st[k] = old.copy(lastResolved = now)
            }
            AiEvent.EXISTS -> {
                val old = st[k] ?: Stat(name = e.name)
                st[k] = old.copy(
                    exists = old.exists + e.count,
                    lastResolved = if (old.added > 0) now else old.lastResolved
                )
            }
            AiEvent.SKIPPED -> {
                val old = st[k] ?: Stat(name = e.name)
                st[k] = old.copy(skipped = old.skipped + e.count)
            }
            AiEvent.IMPORTANT -> {
                val old = st[k] ?: Stat(name = e.name)
                st[k] = old.copy(important = old.important + e.count)
            }
            AiEvent.RENAMED -> {
                val to = e.to
                if (to.isEmpty() || to == k) return
                val moved = st.remove(k) ?: run { alias[k] = to; return }
                alias[k] = to
                val ex = st[to]
                st[to] = if (ex == null) moved.copy(name = e.name) else ex.copy(
                    added = ex.added + moved.added,
                    important = ex.important + moved.important,
                    exists = ex.exists + moved.exists,
                    skipped = ex.skipped + moved.skipped,
                    lastAdded = max(ex.lastAdded, moved.lastAdded),
                    lastResolved = max(ex.lastResolved, moved.lastResolved),
                    avgLife = if (ex.avgLife > 0f) ex.avgLife else moved.avgLife,
                    avgGapDays = if (ex.avgGapDays > 0f) ex.avgGapDays else moved.avgGapDays,
                    gapDev = if (ex.avgGapDays > 0f) ex.gapDev else moved.gapDev,
                    recent = (ex.recent.toList() + moved.recent.toList()).sorted().takeLast(MAX_TS).toLongArray(),
                    name = e.name,
                    weekdays = IntArray(7) { ex.weekdays[it] + moved.weekdays[it] },
                    wdW = FloatArray(7) { decayed(ex, e.ts)[it] + decayed(moved, e.ts)[it] },
                    wdT = e.ts
                )
            }
        }
    }

    // ───────────────────────── تسجيل الأحداث ─────────────────────────

    private fun ev(
        type: String,
        name: String,
        ts: Long = System.currentTimeMillis(),
        qty: Int = 1,
        unit: String = "",
        important: Boolean = false,
        batch: String = "",
        to: String = "",
        count: Int = 1
    ): AiEvent {
        val k = key(name)
        return AiEvent(AiEventSync.idFor(type, k, ts), k, name, type, ts, qty, unit, important, batch, to, count)
    }

    /** يضيف الأحداث محلياً فوراً ويرسلها لفايربيس؛ الاستعلامات ترى أثرها مباشرة. */
    private fun post(onDone: () -> Unit = {}, block: () -> List<AiEvent>) {
        LearningHub.scope.launch {
            val created = synchronized(this@MissingFinderStore) {
                ensureLoaded()
                val list = block()
                if (list.isNotEmpty()) {
                    list.forEach { events[it.id] = it }
                    rebuildLocked()
                }
                list
            }
            if (created.isNotEmpty()) {
                if (created.size == 1) AiEventSync.write(created[0]) else AiEventSync.writeAll(created)
                saver.trigger()
            }
            startSync()
            onDone()
        }
    }

    fun recordAdded(
        name: String,
        quantity: Double,
        unit: String,
        important: Boolean,
        batch: String = "",
        onDone: () -> Unit = {}
    ) {
        post(onDone) {
            listOf(ev(AiEvent.ADDED, name, qty = quantity.toInt().coerceAtLeast(1), unit = unit, important = important, batch = batch))
        }
    }

    /**
     * المادة صارت متوفرة (حُذفت من قائمة النواقص): لا تُقترح حتى يحين وقتها الطبيعي.
     * لا ننشئ سجلاً لمادة لم تُسجَّل كنقص قط، ولا نكرر حدثاً سجّله جهاز آخر قبل لحظات.
     */
    fun recordResolved(names: Collection<String>, onDone: () -> Unit = {}) {
        if (names.isEmpty()) return
        val copy = names.toList()
        post(onDone) {
            val now = System.currentTimeMillis()
            copy.mapNotNull { n ->
                val old = statsMap[key(n)] ?: return@mapNotNull null
                if (old.added == 0) return@mapNotNull null
                if (old.lastResolved >= old.lastAdded && now - old.lastResolved < 60_000L) return@mapNotNull null
                ev(AiEvent.RESOLVED, n, now)
            }
        }
    }

    fun recordImportant(name: String, onDone: () -> Unit = {}) {
        post(onDone) { listOf(ev(AiEvent.IMPORTANT, name)) }
    }

    fun recordExists(names: Collection<String>, onDone: () -> Unit = {}) {
        if (names.isEmpty()) return
        val copy = names.toList()
        post(onDone) { copy.map { ev(AiEvent.EXISTS, it) } }
    }

    fun recordSkipped(names: Collection<String>, onDone: () -> Unit = {}) {
        if (names.isEmpty()) return
        val copy = names.toList()
        post(onDone) { copy.map { ev(AiEvent.SKIPPED, it) } }
    }

    fun rename(oldName: String, newName: String, onDone: () -> Unit = {}) {
        post(onDone) {
            val from = key(oldName)
            val to = key(newName)
            if (from == to) return@post emptyList()
            listOf(
                AiEvent(
                    AiEventSync.idFor(AiEvent.RENAMED, from, System.currentTimeMillis()),
                    from, newName, AiEvent.RENAMED, System.currentTimeMillis(), to = to
                )
            )
        }
    }

    @Synchronized
    fun partnerOf(name: String, pendingNames: Collection<String>): String? {
        if (!loaded || pendingNames.isEmpty()) return null
        val m = pairsMap[key(name)] ?: return null
        var best: String? = null
        var bestCount = 1
        for (p in pendingNames) {
            val c = m[key(p)] ?: 0
            if (c > bestCount) { bestCount = c; best = p }
        }
        return best
    }

    // ───────────────────────── المنطق الزمني ─────────────────────────

    /** المادة صارت متوفرة بعد آخر طلب لها (وليست بانتظار الشراء). */
    private fun isResolved(s: Stat) = s.lastResolved > 0L && s.lastResolved >= s.lastAdded

    /** نقطة البداية لعدّ الأيام: من لحظة توفّرها إن حُذفت، وإلا من آخر إضافة. */
    private fun anchor(s: Stat) = if (isResolved(s)) s.lastResolved else s.lastAdded

    /** الفاصل المتوقع (بالأيام) قبل أن تنقص من جديد. */
    private fun expectedInterval(s: Stat): Float = when {
        isResolved(s) && s.avgLife >= 1f -> s.avgLife
        s.avgGapDays >= 1f -> s.avgGapDays
        else -> 0f
    }

    /** فترة "هدوء" بعد توفّر المادة: لا تُقترح خلالها أبداً. */
    private fun cooldownDays(s: Stat): Float {
        val e = expectedInterval(s)
        return if (e >= 1f) max(MIN_COOLDOWN_DAYS, e * 0.6f) else DEFAULT_COOLDOWN_DAYS
    }

    private fun inCooldown(s: Stat, now: Long): Boolean {
        if (!isResolved(s)) return false
        val days = (now - s.lastResolved).toFloat() / DAY_MS
        return days < cooldownDays(s)
    }

    private fun isDue(s: Stat, now: Long): Boolean {
        if (s.added < 2 || s.lastAdded == 0L) return false
        if (inCooldown(s, now)) return false
        val interval = expectedInterval(s)
        if (interval < 1f) return false
        val days = (now - anchor(s)).toFloat() / DAY_MS
        if (days > 120f && days > interval * 6f) return false
        val threshold = max(interval - 0.5f * s.gapDev, interval * 0.6f)
        return days >= threshold
    }

    /**
     * هل تنقص هذه المادة في أيام ثابتة من الأسبوع؟
     *  - يوم واحد إن كان ≥ 50% من الإضافات (بأوزان تُفضّل الأحدث)،
     *  - أو يومان إن جمعا ≥ 80% ولكلٍّ منهما ≥ 25%.
     * يلزم ≥ 3 مرات و≥ 60% من كل المرات على هذه الأيام؛ وإلا فلا يوجد يوم ثابت.
     * [Pattern.weekly] = تتكرر أسبوعياً تقريباً (متوسط الفاصل ≤ 16 يوماً)، وإلا فاليوم مجرد ميل.
     */
    private fun patternOf(s: Stat, now: Long): Pattern? {
        val total = s.weekdays.sum()
        if (total < 3) return null
        val w = decayed(s, now)
        val sum = w.sum()
        if (sum <= 0f) return null
        val order = (0..6).sortedByDescending { w[it] }
        val d0 = order[0]
        val d1 = order[1]
        val s0 = w[d0] / sum
        val s1 = w[d1] / sum
        val days: List<Int> = when {
            s0 >= 0.5f -> listOf(d0)
            s0 + s1 >= 0.8f && s1 >= 0.25f -> listOf(d0, d1).sorted()
            else -> return null
        }
        val hits = days.sumOf { s.weekdays[it] }
        if (hits < 3 || hits.toFloat() / total < 0.6f) return null
        return Pattern(days, hits, total, weekly = s.avgGapDays in 1f..16f)
    }

    /** اليوم أحد أيام نمطها الأسبوعي الثابت، ولم تُطلب بعد في هذا الأسبوع. */
    private fun isWeekdayDue(s: Stat, now: Long): Boolean {
        val p = patternOf(s, now) ?: return false
        if (!p.weekly) return false
        if (weekdayOf(now) !in p.days) return false
        if (inCooldown(s, now) || s.exists > s.added) return false
        if (s.lastAdded == 0L) return false
        val sinceLast = (now - s.lastAdded).toFloat() / DAY_MS
        val minGap = if (p.days.size >= 2) 1.5f else 3.5f
        if (sinceLast < minGap) return false
        if (sinceLast > 90f) return false
        return true
    }

    private fun patternText(s: Stat, now: Long): String? {
        val p = patternOf(s, now)
        if (p != null) {
            val label = if (p.days.size == 1) "يوم ${DAY_NAMES[p.days[0]]}"
            else "يومي ${DAY_NAMES[p.days[0]]} و${DAY_NAMES[p.days[1]]}"
            return "عادةً تنقص $label (${p.hits} من ${p.total} مرات)"
        }
        if (s.added >= 3 && s.avgGapDays >= 1f) {
            return "بلا يوم ثابت — تنقص كل ~${s.avgGapDays.roundToInt()} يوم تقريباً"
        }
        return null
    }

    /** طُلبت كثيراً مؤخراً: عدد مرات الإضافة في آخر [FREQ_WINDOW_DAYS] يوماً. */
    private fun recentCount(s: Stat, now: Long): Int {
        val from = now - FREQ_WINDOW_DAYS * DAY_MS
        return s.recent.count { it >= from }
    }

    /** مادة كانت تُطلب بانتظام ثم توقّفت عن الظهور من مدة أطول بكثير من العادة. */
    private fun isStale(s: Stat, now: Long): Boolean {
        if (s.added < 3 || s.lastAdded == 0L || s.avgGapDays < 1f) return false
        if (inCooldown(s, now)) return false
        val days = (now - anchor(s)).toFloat() / DAY_MS
        return days >= max(s.avgGapDays * 2.2f, 21f) && days <= 200f && s.exists <= s.added
    }

    private fun score(s: Stat?, now: Long): Double {
        if (s == null || (s.added == 0 && s.important == 0)) return 0.0
        // اشتريتها/حذفتها للتو: لا تقترحها.
        if (inCooldown(s, now)) return 0.0
        val resolved = isResolved(s)
        val days = if (anchor(s) > 0L) (now - anchor(s)).toDouble() / DAY_MS else 9999.0
        // التعزيز الزمني فقط لمادة ما زالت بانتظار الطلب؛ بعد توفّرها لا معنى له.
        val recency = if (resolved || s.lastAdded == 0L) 0.0 else 3.0 * exp(-days / 14.0)
        var due = 0.0
        if (isDue(s, now)) {
            val ratio = (days / expectedInterval(s)).coerceAtMost(2.0)
            due = 1.5 + (ratio - 0.6).coerceAtLeast(0.0)
        }
        // يوم أسبوعي ثابت: كلما زاد عدد المرات (الثقة) وكان اليوم هو يومها زادت الأولوية.
        var weekday = 0.0
        val p = patternOf(s, now)
        if (p != null && weekdayOf(now) in p.days) {
            val share = p.hits.toDouble() / p.total
            val conf = p.hits / (p.hits + 1.5)
            weekday = (if (p.weekly) 2.8 else 1.0) * share * conf
        }
        val hot = min(recentCount(s, now), 5) * 0.6
        val stale = if (isStale(s, now)) 1.2 else 0.0
        val conf = s.added / (s.added + 2.0)
        val base = ln(1.0 + s.added) * 3.0 + s.important * 2.5 + recency + due + weekday + hot + stale
        // ردّ "موجودة" يخفض الأولوية بقوة أكبر كلما تكرر.
        val penalty = s.exists.toDouble() / (s.added + s.exists + 1.0) * 3.0 + min(s.skipped, 5) * 0.15
        return (base * (0.6 + 0.4 * conf) - penalty).coerceAtLeast(0.1)
    }

    @Synchronized
    fun ranked(items: List<MaterialCatalogItem>, exclude: Collection<String> = emptyList()): List<MaterialCatalogItem> {
        if (!loaded) return items.sortedBy { it.name }
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val scored = items.filter { key(it.name) !in skip }.map { it to score(statsMap[key(it.name)], now) }
        return scored.sortedWith(compareByDescending<Pair<MaterialCatalogItem, Double>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    /**
     * الاقتراحات: تستثني ما هو على قائمة النواقص الآن ([exclude]) وما صار متوفراً
     * مؤخراً (فترة الهدوء)، وترفع الأكثر طلباً مؤخراً والمستحق والمتأخر منذ مدة وما يوافق
     * يوم اليوم. وإن كانت هناك نواقص حالية، تُضاف المواد التي اعتادت أن تنقص معها.
     */
    private fun isOccasional(s: Stat?, now: Long): Boolean {
        if (s == null) return true
        val from = now - RARE_WINDOW_DAYS * DAY_MS
        return s.recent.count { it >= from } < MIN_ADDS_FOR_FREQUENT
    }

    @Synchronized
    fun suggested(
        items: List<MaterialCatalogItem>,
        limit: Int = 12,
        exclude: Collection<String> = emptyList(),
        frequentOnly: Boolean = false
    ): List<MaterialCatalogItem> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val partnerBoost = HashMap<String, Double>()
        for (p in exclude) {
            val m = pairsMap[key(p)] ?: continue
            for ((other, c) in m) if (c >= 2 && other !in skip) {
                partnerBoost[other] = (partnerBoost[other] ?: 0.0) + min(c, 6) * 0.35
            }
        }
        return items.filter { key(it.name) !in skip }
            .filter { !frequentOnly || !isOccasional(statsMap[key(it.name)], now) }
            .map { it to (score(statsMap[key(it.name)], now).let { sc ->
                if (sc <= 0.0) 0.0 else sc + (partnerBoost[key(it.name)] ?: 0.0)
            }) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun displayName(catalog: List<MaterialCatalogItem>): (Map.Entry<String, Stat>) -> String {
        val catalogNames = HashMap<String, String>(catalog.size)
        catalog.forEach { catalogNames[key(it.name)] = it.name }
        return { e -> catalogNames[e.key] ?: e.value.name }
    }

    /** مواد حان وقتها إحصائياً، دون ما هو على القائمة الآن. */
    @Synchronized
    fun dueNames(exclude: Collection<String>, catalog: List<MaterialCatalogItem>, limit: Int = 3): List<String> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val nameOf = displayName(catalog)
        return statsMap.entries
            .filter { it.key !in skip && isDue(it.value, now) && it.value.exists <= it.value.added }
            .map { nameOf(it) to score(it.value, now) }
            .filter { it.first.isNotBlank() && it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** مواد تأخرت أكثر بكثير من عادتها (ما عادت تُجلب) — تستحق التفقّد. */
    @Synchronized
    fun staleNames(exclude: Collection<String>, catalog: List<MaterialCatalogItem>, limit: Int = 3): List<String> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val nameOf = displayName(catalog)
        val inCatalog = catalog.map { key(it.name) }.toHashSet()
        return statsMap.entries
            .filter { it.key !in skip && (inCatalog.isEmpty() || it.key in inCatalog) && isStale(it.value, now) }
            .sortedByDescending { (now - anchor(it.value)).toFloat() / max(it.value.avgGapDays, 1f) * it.value.added }
            .map { nameOf(it) }
            .filter { it.isNotBlank() }
            .take(limit)
    }

    /** الأكثر طلباً في آخر [FREQ_WINDOW_DAYS] يوماً (بحسب إحصائيات جمع النواقص). */
    @Synchronized
    fun frequentNames(exclude: Collection<String>, catalog: List<MaterialCatalogItem>, limit: Int = 3): List<String> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val nameOf = displayName(catalog)
        return statsMap.entries
            .filter { it.key !in skip && !inCooldown(it.value, now) && recentCount(it.value, now) >= 3 && it.value.exists <= it.value.added }
            .sortedByDescending { recentCount(it.value, now) }
            .map { nameOf(it) }
            .filter { it.isNotBlank() }
            .take(limit)
    }

    /** مواد اعتادت أن تنقص مع ما هو ناقص الآن. */
    @Synchronized
    fun partnerNames(pending: Collection<String>, catalog: List<MaterialCatalogItem>, limit: Int = 3): List<String> {
        if (!loaded || pending.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val skip = pending.map { key(it) }.toHashSet()
        val sums = HashMap<String, Int>()
        for (p in pending) {
            val m = pairsMap[key(p)] ?: continue
            for ((other, c) in m) if (c >= 2 && other !in skip) sums[other] = (sums[other] ?: 0) + c
        }
        val nameOf = displayName(catalog)
        return sums.entries
            .filter { e -> statsMap[e.key]?.let { !inCooldown(it, now) && it.exists <= it.added } ?: false }
            .sortedByDescending { it.value }
            .mapNotNull { e -> statsMap.entries.firstOrNull { it.key == e.key }?.let { nameOf(it) } }
            .filter { it.isNotBlank() }
            .take(limit)
    }

    @Synchronized
    fun frequency(name: String): Int = if (loaded) statsMap[key(name)]?.added ?: 0 else 0

    @Synchronized
    fun suggestion(name: String): Pair<Int, MaterialUnit>? {
        if (!loaded) return null
        val s = statsMap[key(name)] ?: return null
        if (s.added == 0) return null
        // الكمية "المعتادة" = الوسيط لآخر الطلبات، والوحدة الأكثر استعمالاً (بدل آخر قيمة فقط).
        val qty = if (s.recentQty.size >= 3) s.recentQty.sorted()[s.recentQty.size / 2] else s.qty
        val unit = s.recentUnits.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: s.unit
        return qty to MaterialUnit.fromLabel(unit)
    }

    /**
     * مواد لها يوم أسبوعي ثابت يوافق اليوم (مثلاً تنقص كل أحد وهذا يوم الأحد)، لم تُطلب
     * بعد في هذا الأسبوع. الأعلى ثقة (نسبة وعدد المرات) أولاً.
     */
    @Synchronized
    fun weekdayNames(exclude: Collection<String>, catalog: List<MaterialCatalogItem>, limit: Int = 3): List<String> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val nameOf = displayName(catalog)
        return statsMap.entries
            .filter { it.key !in skip && isWeekdayDue(it.value, now) }
            .sortedByDescending { e ->
                val p = patternOf(e.value, now)
                if (p == null) 0.0 else (p.hits.toDouble() / p.total) * (p.hits / (p.hits + 1.5))
            }
            .map { nameOf(it) }
            .filter { it.isNotBlank() }
            .take(limit)
    }

    @Synchronized
    fun insight(name: String): Insight? {
        if (!loaded) return null
        val s = statsMap[key(name)] ?: return null
        if (s.added == 0 && s.exists == 0) return null
        val now = System.currentTimeMillis()
        val days = if (s.lastAdded > 0L) ((now - s.lastAdded) / DAY_MS).toInt() else null
        val p = patternOf(s, now)
        return Insight(
            added = s.added,
            exists = s.exists,
            daysSinceLast = days,
            qty = s.qty,
            unit = MaterialUnit.fromLabel(s.unit),
            avgGapDays = if (s.avgGapDays >= 1f) s.avgGapDays.toInt() else null,
            due = isDue(s, now),
            pattern = patternText(s, now),
            patternToday = p != null && weekdayOf(now) in p.days
        )
    }

    private fun weekdayOf(ts: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    private companion object {
        const val KEY_LEGACY_STATS = "stats_json"
        const val KEY_MIGRATED = "events_migrated_v1"
        const val KEY_PURGE_AT = "events_purge_at"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_PARTNERS = 8
        const val MAX_BATCH = 30
        const val MAX_EVENTS = 6000
        const val MAX_TS = 10
        const val MAX_Q = 7
        const val FREQ_WINDOW_DAYS = 30L
        const val RARE_WINDOW_DAYS = 28L
        const val MIN_ADDS_FOR_FREQUENT = 5
        const val WD_HALF_LIFE_DAYS = 60.0
        const val MIN_COOLDOWN_DAYS = 3f
        const val DEFAULT_COOLDOWN_DAYS = 7f

        /** مرتّبة كما يرجعها weekdayOf: 0 = الاثنين … 6 = الأحد. */
        val DAY_NAMES = arrayOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
    }
}
