package com.shopmanager.app.data.debts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class OverdueItem(
    val personId: String,
    val name: String,
    val phone: String,
    val amount: Double,
    val days: Int
)

object OverdueRule {

    const val DAY_MS = 86_400_000L

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun compute(persons: List<Person>, debts: List<Debt>, overdueDays: Int, now: Long): List<OverdueItem> {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val limit = now - overdueDays.toLong() * DAY_MS
        val balance = HashMap<String, Double>()
        val oldest = HashMap<String, Long>()
        for (d in debts) {
            balance[d.personId] = (balance[d.personId] ?: 0.0) + d.amount
            if (d.amount <= 0) continue
            val t = runCatching { format.parse(d.date.trim())?.time }.getOrNull() ?: continue
            if (t >= limit) continue
            val current = oldest[d.personId]
            if (current == null || t < current) oldest[d.personId] = t
        }
        val today = startOfDay(now)
        val result = ArrayList<OverdueItem>()
        for (p in persons) {
            val owed = balance[p.id] ?: 0.0
            if (owed <= 0) continue
            val days: Int = if (p.dueDate > 0L) {
                if (p.dueDate >= today) continue
                ((today - startOfDay(p.dueDate)) / DAY_MS).toInt().coerceAtLeast(1)
            } else {
                val t = oldest[p.id] ?: continue
                ((now - t) / DAY_MS).toInt()
            }
            result += OverdueItem(p.id, p.name, p.phone, owed, days)
        }
        return result.sortedByDescending { it.days }
    }
}
