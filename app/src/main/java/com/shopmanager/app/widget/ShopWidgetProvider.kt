package com.shopmanager.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shopmanager.app.MainActivity
import com.shopmanager.app.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShopWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShopWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { manager.updateAppWidget(it, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_shop)
            val snap = WidgetCache.read(context)
            if (snap == null) {
                views.setTextViewText(R.id.widget_debt_value, context.getString(R.string.widget_empty_value))
                views.setTextViewText(R.id.widget_shortages_value, context.getString(R.string.widget_empty_value))
                views.setTextViewText(R.id.widget_updated, context.getString(R.string.widget_not_synced))
            } else {
                val nf = NumberFormat.getNumberInstance(Locale("ar"))
                val time = SimpleDateFormat("d MMM، h:mm a", Locale("ar")).format(Date(snap.updatedAt))
                views.setTextViewText(R.id.widget_debt_value, "${nf.format(snap.totalDebt)} ${snap.currency}".trim())
                views.setTextViewText(R.id.widget_shortages_value, nf.format(snap.shortages.toLong()))
                views.setTextViewText(R.id.widget_updated, context.getString(R.string.widget_updated_at, time))
            }
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
