package com.shopmanager.app.ui.debts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the customer debts list as one formatted PNG "report card" —
 * branded header, one row per customer (name, amount), a total footer —
 * and hands it back as a content Uri ready to drop into a share Intent
 * (see DebtsScreen's "مشاركة" button).
 *
 * Added alongside the existing plain-text share (see
 * `buildDebtsShareText`): text still pastes fine into a quick note or
 * SMS, but an image gives the exact same clean, aligned layout no matter
 * which app the recipient opens it in — same reasoning as
 * [com.shopmanager.app.ui.materials.MaterialsReportImage], now offered as
 * a choice instead of the text-only path this screen used to have.
 *
 * Built with plain android.graphics/Canvas rather than capturing a
 * Composable to a bitmap — this only ever runs once, from a plain
 * onClick, with no Compose frame/recomposition timing to line up.
 */
object DebtsReportImage {

    private const val WIDTH = 1080
    private const val PADDING = 56f
    private const val ROW_HEIGHT = 104f
    private const val HEADER_HEIGHT = 220f
    private const val FOOTER_HEIGHT = 130f

    fun generate(
        context: Context,
        persons: List<Person>,
        totalAmount: Double,
        brandColor: Int
    ): Uri {
        val sorted = persons.sortedByDescending { it.amount }
        val height = (HEADER_HEIGHT + sorted.size.coerceAtLeast(1) * ROW_HEIGHT + FOOTER_HEIGHT + PADDING)
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

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brandColor }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEADER_HEIGHT, headerPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("كشف الديون", WIDTH - PADDING, 100f, titlePaint)

        val datePaint = TextPaint(titlePaint).apply {
            textSize = 30f
            isFakeBoldText = false
            alpha = 210
        }
        // 12-hour clock ("a" renders as ص/م in the Arabic locale).
        val df = SimpleDateFormat("d MMMM yyyy، h:mm a", Locale("ar"))
        canvas.drawText(df.format(Date()), WIDTH - PADDING, 152f, datePaint)

        val countPaint = TextPaint(datePaint).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("${sorted.size} عميل", PADDING, 152f, countPaint)

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val amountPaint = TextPaint(namePaint).apply {
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
            canvas.drawText("لا توجد ديون حالياً", WIDTH / 2f, y + 50f, emptyPaint)
        } else {
            sorted.forEachIndexed { index, p ->
                val rowTop = y
                canvas.drawText(p.name, WIDTH - PADDING, rowTop + 60f, namePaint)
                canvas.drawText(
                    "${Formatters.number(p.amount)} ${AppSettingsState.currencySymbol}",
                    PADDING, rowTop + 60f, amountPaint
                )
                y += ROW_HEIGHT
                if (index != sorted.lastIndex) {
                    canvas.drawLine(PADDING, y - 14f, WIDTH - PADDING, y - 14f, dividerPaint)
                }
            }
        }

        // Total footer band, same brand color as the header so it reads
        // as one cohesive card rather than an appended afterthought.
        val footerTop = y + PADDING / 2
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brandColor; alpha = 24 }
        canvas.drawRect(0f, footerTop, WIDTH.toFloat(), footerTop + FOOTER_HEIGHT, footerPaint)
        val totalLabelPaint = TextPaint(namePaint).apply { textSize = 34f }
        val totalValuePaint = TextPaint(amountPaint).apply { textSize = 44f }
        canvas.drawText("الإجمالي", WIDTH - PADDING, footerTop + 76f, totalLabelPaint)
        canvas.drawText(
            "${Formatters.number(totalAmount)} ${AppSettingsState.currencySymbol}",
            PADDING, footerTop + 76f, totalValuePaint
        )

        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        // Clear any previously shared images from this session's cache
        // folder before writing the new one — same reasoning as
        // MaterialsReportImage: the person only ever needs the most
        // recent report, this must never quietly grow forever.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "debts_report_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
