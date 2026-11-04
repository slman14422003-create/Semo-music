package com.shopmanager.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * أدوات رقم هاتف العميل (اختياري): تنظيف الرقم، وتحويله للصيغة الدولية
 * (الافتراضي سوريا +963) لأن واتساب يحتاج الصيغة الدولية، وفتح الاتصال /
 * واتساب / الرسائل بدون أي صلاحية إضافية (كلها Intents تفتح تطبيق الجهاز).
 */
object PhoneUtils {

    private const val DEFAULT_COUNTRY_CODE = "963" // سوريا

    /** أرقام فقط — يحوّل الأرقام العربية/الفارسية (٠١٢…) إلى لاتينية ويحذف أي رمز آخر. */
    fun digitsOnly(raw: String): String = buildString {
        raw.forEach { c ->
            val d = Character.digit(c, 10)
            if (d >= 0) append(d)
        }
    }

    /** الحقل اختياري: فارغ = صحيح. غير الفارغ يلزم أن يحوي 7 إلى 15 رقماً. */
    fun isValidOrBlank(raw: String): Boolean {
        if (raw.isBlank()) return true
        return digitsOnly(raw).length in 7..15
    }

    /** للاتصال/الرسائل: يبقي + في البداية إن وُجد، وإلا أرقام فقط (09xx تبقى محلية). */
    fun forDial(raw: String): String {
        val d = digitsOnly(raw)
        return if (raw.trim().startsWith("+")) "+$d" else d
    }

    /** للواتساب: صيغة دولية بدون + (مثال 0933123456 ← 963933123456). */
    fun forWhatsApp(raw: String): String {
        val d = digitsOnly(raw)
        if (d.isEmpty()) return ""
        return when {
            raw.trim().startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.startsWith(DEFAULT_COUNTRY_CODE) -> d
            d.startsWith("0") -> DEFAULT_COUNTRY_CODE + d.drop(1)
            d.length == 9 && d.startsWith("9") -> DEFAULT_COUNTRY_CODE + d
            else -> d
        }
    }

    fun call(context: Context, raw: String) {
        val number = forDial(raw)
        if (number.isEmpty()) return
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    fun sms(context: Context, raw: String) {
        val number = forDial(raw)
        if (number.isEmpty()) return
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }

    fun whatsApp(context: Context, raw: String, text: String? = null) {
        val number = forWhatsApp(raw)
        if (number.isEmpty()) return
        val suffix = if (text.isNullOrBlank()) "" else "&text=" + Uri.encode(text)
        val webSuffix = if (text.isNullOrBlank()) "" else "?text=" + Uri.encode(text)
        // أولاً مباشرة بتطبيق واتساب، وإن لم يوجد نفتح رابط wa.me (يفتح التطبيق أو المتصفح).
        val opened = launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$number$suffix")), showError = false)
        if (!opened) launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number$webSuffix")))
    }

    private fun launch(context: Context, intent: Intent, showError: Boolean = true): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        if (showError) Toast.makeText(context, "تعذر فتح التطبيق على هذا الجهاز", Toast.LENGTH_SHORT).show()
        false
    }
}
