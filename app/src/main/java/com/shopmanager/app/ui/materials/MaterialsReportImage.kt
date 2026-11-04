package com.shopmanager.app.ui.materials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceBreakdown
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the materials shortage list as one formatted PNG "report card" -
 * a branded header, one row per material (name, quantity, price), sized to
 * fit the list - and hands it back as a content Uri ready to drop into a
 * share Intent (see MaterialsScreen's "مشاركة" button).
 *
 * FIX (was plain-text share): sharing raw text ("• اسم: كمية — سعر") pastes
 * badly into WhatsApp/Telegram (no alignment, no branding, wraps
 * unpredictably per-recipient). Rendering it once as an image means every
 * recipient sees the exact same clean, aligned layout no matter which app
 * they open it in.
 *
 * Built with plain android.graphics/Canvas rather than capturing a
 * Composable to a bitmap - this only ever needs to run once, from a plain
 * onClick, with no Compose frame/recomposition timing to line up for an
 * accurate capture.
 */
object MaterialsReportImage {

    private const val WIDTH = 1080
    private const val PADDING = 56f
    private const val ROW_HEIGHT = 104f
    private const val HEADER_HEIGHT = 220f
    private const val FOOTER_HEIGHT = 90f
    private const val FOOTER_LINE = 56f

    fun generate(
        context: Context,
        materials: List<Material>,
        prices: Map<String, Double>,
        brandColor: Int
    ): Uri {
        val sorted = materials.sortedBy { it.name }
        val breakdown = sorted.priceBreakdown(prices)
        val hasTotal = breakdown.all.total > 0.0
        val footerHeight = if (hasTotal) FOOTER_HEIGHT + 2 * FOOTER_LINE else FOOTER_HEIGHT
        val height = (HEADER_HEIGHT + sorted.size.coerceAtLeast(1) * ROW_HEIGHT + footerHeight + PADDING)
            .toInt()

        val reportScale = com.shopmanager.app.ui.common.ReportScale.factor(context)
        val bitmap = Bitmap.createBitmap(
            (WIDTH * reportScale).toInt(),
            (height * reportScale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(reportScale, reportScale)
        canvas.drawColor(Color.WHITE)

        // Header band, using the app's own current brand color so the
        // image always matches whichever palette the person picked in
        // الإعدادات → المظهر instead of a hardcoded color.
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brandColor }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEADER_HEIGHT, headerPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("قائمة المواد والأسعار", WIDTH - PADDING, 100f, titlePaint)

        val datePaint = TextPaint(titlePaint).apply {
            textSize = 30f
            isFakeBoldText = false
            alpha = 210
        }
        // 12-hour clock (was HH:mm/24h) — "a" renders as ص/م in Arabic locale.
        val df = SimpleDateFormat("d MMMM yyyy، h:mm a", Locale("ar"))
        canvas.drawText(df.format(Date()), WIDTH - PADDING, 152f, datePaint)

        val countPaint = TextPaint(datePaint).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("${sorted.size} مادة", PADDING, 152f, countPaint)

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val qtyPaint = TextPaint(namePaint).apply {
            color = Color.parseColor("#6B6B6B")
            isFakeBoldText = false
            textSize = 32f
        }
        val pricePaint = TextPaint(namePaint).apply {
            color = brandColor
            textSize = 38f
            textAlign = Paint.Align.LEFT
        }
        val dividerPaint = Paint().apply { color = Color.parseColor("#EDEDED"); strokeWidth = 2f }

        var y = HEADER_HEIGHT + PADDING
        if (sorted.isEmpty()) {
            val emptyPaint = TextPaint(namePaint).apply {
                textAlign = Paint.Align.CENTER
                color = Color.parseColor("#999999")
                isFakeBoldText = false
                textSize = 34f
            }
            canvas.drawText("لا توجد نواقص حالياً", WIDTH / 2f, y + 50f, emptyPaint)
        } else {
            sorted.forEachIndexed { index, m ->
                val rowTop = y
                canvas.drawText(m.name, WIDTH - PADDING, rowTop + 44f, namePaint)
                canvas.drawText(m.quantityLabel(), WIDTH - PADDING, rowTop + 82f, qtyPaint)
                // سعر الصف = سعر الكيلو × الوزن
                val price = m.linePrice(prices)
                if (price != null) {
                    canvas.drawText(
                        "${Formatters.number(price)} ${AppSettingsState.currencySymbol}",
                        PADDING, rowTop + 60f, pricePaint
                    )
                }
                y += ROW_HEIGHT
                if (index != sorted.lastIndex) {
                    canvas.drawLine(PADDING, y - 14f, WIDTH - PADDING, y - 14f, dividerPaint)
                }
            }
        }

        // إجمالي السعر في أسفل الصورة (فقط إن وُجد سعر)
        if (hasTotal) {
            val currency = AppSettingsState.currencySymbol
            val linePaint = TextPaint(namePaint).apply { textSize = 34f; isFakeBoldText = false; color = Color.parseColor("#444444") }
            val totalPaint = TextPaint(namePaint).apply { textSize = 40f; color = brandColor }
            val base = height - PADDING - 8f
            canvas.drawText("إجمالي السعر: ${Formatters.number(breakdown.all.total)} $currency", WIDTH - PADDING, base, totalPaint)
            canvas.drawText("بقية المواد: ${Formatters.number(breakdown.regular.total)} $currency", WIDTH - PADDING, base - FOOTER_LINE, linePaint)
            canvas.drawText("★ المواد الهامة: ${Formatters.number(breakdown.important.total)} $currency", WIDTH - PADDING, base - 2 * FOOTER_LINE, linePaint)
        }

        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        // Clear any previously shared images from this session's cache
        // folder before writing the new one, so this doesn't quietly grow
        // forever - the person only ever needs the most recent report.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "materials_report_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
