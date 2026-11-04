package com.shopmanager.app.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.Formatters
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders one combined PNG "report card" covering the whole app — every
 * customer debt, then every material/price — for the "تصدير نسخة
 * احتياطية الآن" button in Settings. Sits next to the existing plain-text
 * export (`buildBackupText`) as a second format option, same reasoning as
 * [com.shopmanager.app.ui.materials.MaterialsReportImage] and
 * [com.shopmanager.app.ui.debts.DebtsReportImage]: a text message pastes
 * fine into a note, an image keeps its exact layout everywhere else.
 *
 * This is a share/export snapshot only — it has no bearing on the actual
 * local backup file written by [com.shopmanager.app.data.backup.BackupManager].
 */
object WholeAppReportImage {

    private const val WIDTH = 1080
    private const val PADDING = 56f
    private const val ROW_HEIGHT = 96f
    private const val HEADER_HEIGHT = 220f
    private const val SECTION_HEIGHT = 84f

    fun generate(
        context: Context,
        persons: List<Person>,
        totalDebt: Double,
        materials: List<Material>,
        prices: Map<String, Double>,
        currency: String,
        brandColor: Int
    ): Uri {
        val sortedPersons = persons.sortedByDescending { it.amount }
        val sortedMaterials = materials.sortedBy { it.name }

        val height = (
            HEADER_HEIGHT +
                SECTION_HEIGHT + sortedPersons.size.coerceAtLeast(1) * ROW_HEIGHT +
                SECTION_HEIGHT + sortedMaterials.size.coerceAtLeast(1) * ROW_HEIGHT +
                PADDING * 2
            ).toInt()

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
            textSize = 52f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تقرير شامل — إدارة المحل", WIDTH - PADDING, 100f, titlePaint)

        val datePaint = TextPaint(titlePaint).apply {
            textSize = 30f
            isFakeBoldText = false
            alpha = 210
        }
        // 12-hour clock ("a" renders as ص/م in the Arabic locale).
        val df = SimpleDateFormat("d MMMM yyyy، h:mm a", Locale("ar"))
        canvas.drawText(df.format(Date()), WIDTH - PADDING, 152f, datePaint)

        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brandColor
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 36f
            textAlign = Paint.Align.RIGHT
        }
        val subPaint = TextPaint(namePaint).apply {
            color = Color.parseColor("#6B6B6B")
            textSize = 28f
        }
        val valuePaint = TextPaint(namePaint).apply {
            color = brandColor
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val dividerPaint = Paint().apply { color = Color.parseColor("#EDEDED"); strokeWidth = 2f }

        var y = HEADER_HEIGHT + PADDING

        // --- الديون ---
        canvas.drawText(
            "الديون (${sortedPersons.size} عميل — الإجمالي ${Formatters.number(totalDebt)} $currency)",
            WIDTH - PADDING, y + 46f, sectionPaint
        )
        y += SECTION_HEIGHT
        if (sortedPersons.isEmpty()) {
            canvas.drawText("لا يوجد عملاء حالياً", WIDTH - PADDING, y + 44f, subPaint)
            y += ROW_HEIGHT
        } else {
            sortedPersons.forEach { p ->
                canvas.drawText(p.name, WIDTH - PADDING, y + 44f, namePaint)
                canvas.drawText("${Formatters.number(p.amount)} $currency", PADDING, y + 44f, valuePaint)
                y += ROW_HEIGHT
                canvas.drawLine(PADDING, y - 12f, WIDTH - PADDING, y - 12f, dividerPaint)
            }
        }

        y += PADDING / 2

        // --- المواد ---
        canvas.drawText("المواد والأسعار (${sortedMaterials.size})", WIDTH - PADDING, y + 46f, sectionPaint)
        y += SECTION_HEIGHT
        if (sortedMaterials.isEmpty()) {
            canvas.drawText("لا توجد نواقص حالياً", WIDTH - PADDING, y + 44f, subPaint)
        } else {
            sortedMaterials.forEach { m ->
                canvas.drawText(m.name, WIDTH - PADDING, y + 40f, namePaint)
                canvas.drawText(m.quantityLabel(), WIDTH - PADDING, y + 72f, subPaint)
                val price = prices[m.name]
                if (price != null) {
                    canvas.drawText("${Formatters.number(price)} $currency", PADDING, y + 56f, valuePaint)
                }
                y += ROW_HEIGHT
                canvas.drawLine(PADDING, y - 12f, WIDTH - PADDING, y - 12f, dividerPaint)
            }
        }

        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "full_report_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
