package com.shopmanager.app.widget

import android.content.Context

object WidgetCache {

    private const val PREFS = "shop_widget_cache"
    private const val KEY_DEBT = "total_debt"
    private const val KEY_SHORTAGES = "shortages"
    private const val KEY_CURRENCY = "currency"
    private const val KEY_UPDATED = "updated_at"

    data class Snapshot(val totalDebt: Double, val shortages: Int, val currency: String, val updatedAt: Long)

    fun read(context: Context): Snapshot? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_UPDATED)) return null
        return Snapshot(
            totalDebt = java.lang.Double.longBitsToDouble(p.getLong(KEY_DEBT, 0L)),
            shortages = p.getInt(KEY_SHORTAGES, 0),
            currency = p.getString(KEY_CURRENCY, "") ?: "",
            updatedAt = p.getLong(KEY_UPDATED, 0L)
        )
    }

    fun save(context: Context, totalDebt: Double, shortages: Int, currency: String) {
        val app = context.applicationContext
        val old = read(app)
        if (old != null && old.totalDebt == totalDebt && old.shortages == shortages && old.currency == currency) return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DEBT, java.lang.Double.doubleToLongBits(totalDebt))
            .putInt(KEY_SHORTAGES, shortages)
            .putString(KEY_CURRENCY, currency)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
        ShopWidgetProvider.refreshAll(app)
    }
}
