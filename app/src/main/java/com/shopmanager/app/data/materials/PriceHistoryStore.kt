package com.shopmanager.app.data.materials

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class PriceChange(val at: Long, val oldPrice: Double?, val newPrice: Double) {
    val delta: Double? get() = oldPrice?.let { newPrice - it }
}

object PriceHistoryStore {

    const val MAX_PER_MATERIAL = 10
    const val RECENT_WINDOW_MS = 7L * 86_400_000L

    private const val PREFS = "shop_price_history"
    private const val KEY = "history_v1"
    private const val NO_OLD = -1.0

    private val state = MutableStateFlow<Map<String, List<PriceChange>>>(emptyMap())
    private var loaded = false

    fun flow(context: Context): StateFlow<Map<String, List<PriceChange>>> {
        ensureLoaded(context)
        return state
    }

    fun isRecent(change: PriceChange?, now: Long): Boolean =
        change != null && change.oldPrice != null && change.oldPrice != change.newPrice &&
            now - change.at in 0..RECENT_WINDOW_MS

    @Synchronized
    fun record(context: Context, name: String, oldPrice: Double?, newPrice: Double, at: Long = System.currentTimeMillis()) {
        ensureLoaded(context)
        val next = HashMap(state.value)
        if (apply(next, name, oldPrice, newPrice, at)) commit(context, next)
    }

    @Synchronized
    fun recordAll(context: Context, before: Map<String, Double>, after: Map<String, Double>, at: Long = System.currentTimeMillis()) {
        ensureLoaded(context)
        val next = HashMap(state.value)
        var changed = false
        for ((name, price) in after) {
            if (apply(next, name, before[name], price, at)) changed = true
        }
        if (changed) commit(context, next)
    }

    @Synchronized
    fun recordDiff(context: Context, previous: Map<String, Double>, current: Map<String, Double>, at: Long = System.currentTimeMillis()) {
        ensureLoaded(context)
        val next = HashMap(state.value)
        var changed = false
        for ((name, price) in current) {
            val old = previous[name] ?: continue
            if (apply(next, name, old, price, at)) changed = true
        }
        if (changed) commit(context, next)
    }

    private fun apply(target: MutableMap<String, List<PriceChange>>, name: String, oldPrice: Double?, newPrice: Double, at: Long): Boolean {
        val key = name.trim()
        if (key.isEmpty() || oldPrice == newPrice) return false
        val existing = target[key].orEmpty()
        if (existing.firstOrNull()?.newPrice == newPrice) return false
        target[key] = (listOf(PriceChange(at, oldPrice, newPrice)) + existing).take(MAX_PER_MATERIAL)
        return true
    }

    private fun commit(context: Context, next: Map<String, List<PriceChange>>) {
        state.value = next
        val root = JSONObject()
        for ((name, list) in next) {
            val arr = JSONArray()
            list.forEach { c -> arr.put(JSONArray().put(c.at).put(c.oldPrice ?: NO_OLD).put(c.newPrice)) }
            root.put(name, arr)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, root.toString()).apply()
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        val parsed = HashMap<String, List<PriceChange>>()
        runCatching {
            val root = JSONObject(raw)
            val names = root.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = root.optJSONArray(name) ?: continue
                val list = (0 until arr.length()).mapNotNull { i ->
                    val e = arr.optJSONArray(i) ?: return@mapNotNull null
                    val old = e.optDouble(1, NO_OLD)
                    PriceChange(e.optLong(0), if (old == NO_OLD) null else old, e.optDouble(2))
                }
                if (list.isNotEmpty()) parsed[name] = list
            }
        }
        state.value = parsed
    }
}
