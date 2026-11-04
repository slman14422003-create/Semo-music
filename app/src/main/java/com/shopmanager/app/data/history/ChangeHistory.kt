package com.shopmanager.app.data.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChangeEntry(val time: Long, val text: String)

object ChangeHistory {

    private const val PREFS = "shop_manager_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 300

    private val lock = Any()

    fun log(context: Context, text: String) {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
            val items = ArrayList<JSONObject>(current.length() + 1)
            for (i in 0 until current.length()) current.optJSONObject(i)?.let { items.add(it) }
            items.add(JSONObject().put("t", System.currentTimeMillis()).put("x", text))
            val trimmed = if (items.size > MAX_ENTRIES) items.subList(items.size - MAX_ENTRIES, items.size) else items
            val out = JSONArray()
            trimmed.forEach { out.put(it) }
            prefs.edit().putString(KEY, out.toString()).apply()
        }
    }

    fun all(context: Context): List<ChangeEntry> = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val list = ArrayList<ChangeEntry>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            list.add(ChangeEntry(o.optLong("t"), o.optString("x")))
        }
        list.reversed()
    }

    fun clear(context: Context) {
        synchronized(lock) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        }
    }
}
