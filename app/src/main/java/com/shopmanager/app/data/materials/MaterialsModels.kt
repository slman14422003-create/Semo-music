package com.shopmanager.app.data.materials

/**
 * Fixed unit choices for a spice shop (بزورية) - weight is always picked from
 * these options, never free-typed. "unit" is still stored in Firestore as a
 * plain string (the Arabic label) so it stays backward-compatible with any
 * existing documents.
 *
 * FIX: previously only كيلو/لوقية/نص لوقية/ربع لوقية existed, so a half-kilo
 * or quarter-kilo shortage had to be typed as a decimal quantity (e.g. "0.5"
 * with unit كيلو). نص كيلو and ربع كيلو are now their own units, same as the
 * لوقية fractions, so quantity can always stay a whole number (see
 * MaterialEditDialog's stepper) - you pick the size, then just count how
 * many of it.
 *
 * FIX: not everything on the shortage list is sold by weight (e.g. "بيض"
 * counted by the piece, or a shortage that's simply "2" of something with no
 * size at all). NONE covers that: its label is the empty string, so the
 * quantity is shown and stored on its own with nothing appended (see
 * formatQuantity / Material.quantityLabel) instead of being forced into one
 * of the weight units.
 */
enum class MaterialUnit(val label: String) {
    KG("كيلو"),
    HALF_KG("نص كيلو"),
    QUARTER_KG("ربع كيلو"),
    OKE("لوقية"),
    HALF_OKE("نص لوقية"),
    QUARTER_OKE("ربع لوقية"),
    NONE("");

    companion object {
        fun fromLabel(label: String): MaterialUnit = when (label) {
            "كغ", "كيلو" -> KG
            "" -> NONE
            else -> entries.find { it.label == label } ?: KG
        }
    }
}

/**
 * A "material" here is a shortage entry, not a stock count: every row in
 * this list is by definition something the shop is out of and needs to buy
 * (a live shopping list), like adding "فلفل اسود، الكمية 2" when the shop is
 * out of black pepper. There is deliberately no "current stock" or
 * "low-stock threshold" concept - an item simply stays on the list until
 * it's bought and removed.
 */
data class Material(
    val id: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = MaterialUnit.KG.label,
    val section: String = "main",
    // FEATURE ADDED: optional free-text note per shortage row (e.g. "خذها
    // من محل أبو خالد" أو "أولوية - خلصت تماماً") - same "ملاحظة (اختياري)"
    // concept already used for a debt (see data.debts.Debt.note), now
    // available on a material shortage too.
    val notes: String = "",
    val updatedAt: Long = 0L,
    // FEATURE ADDED: "نجمة الأهمية" - marking a shortage as very important
    // pins it to the top of the list (see [order] below); this flag only
    // drives the star's filled/outlined look, the actual position is
    // [order]'s job.
    val important: Boolean = false,
    // FEATURE ADDED: manual drag-to-reorder position (see MaterialsList's
    // long-press drag). Ascending sort key - lower comes first. New
    // materials default to `System.currentTimeMillis()` (see
    // MaterialsRepository.addMaterial) so they land at the bottom of
    // whatever manual order already exists, exactly like being appended to
    // a list. Marking a material important reassigns this to one less than
    // the current minimum, which is what actually moves it to the top.
    val order: Long = 0L
)

/** A fixed catalog entry (the shop's own standing list of spice names). */
data class MaterialCatalogItem(
    val id: String = "",
    val name: String = "",
    val group: String = ""
)

/**
 * Quantity is always entered as a whole number now (see MaterialEditDialog's
 * stepper), but is still stored/typed as Double for backward compatibility
 * with existing Firestore documents. Plain `.toString()` on a Double prints
 * a trailing ".0" (e.g. "1.0 كيلو"), which never made sense for a whole-unit
 * count - this trims it to "1 كيلو" while still showing real decimals for
 * any older document that predates the stepper.
 */
fun Double.formatQuantity(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

/**
 * Quantity + unit as shown to a person ("2 نص كيلو"), used everywhere a
 * material's amount is displayed (list rows, share text, notifications).
 * When the unit is NONE (empty label - a plain count like "بيض: 6") there is
 * no unit word to append, so this returns just the number instead of
 * leaving a trailing space.
 *
 * FIX: for a weight unit, quantity 1 used to print as "1 كيلو" / "1 نص
 * كيلو" - a redundant "1" in front of a size that's already exactly one of
 * itself once picked from the unit picker. Tapping "كيلو" should just read
 * "كيلو"; tapping "نص كيلو" should just read "نص كيلو" - no number next to
 * it. The count only needs to show once there's actually more than one of
 * that size (e.g. "2 كيلو"). NONE is unaffected: a plain count like "بيض: 1"
 * still needs its number since there's no unit word to imply it.
 */
fun Material.quantityLabel(): String = when {
    unit.isBlank() -> quantity.formatQuantity()
    quantity == 1.0 -> unit
    else -> "${quantity.formatQuantity()} $unit"
}

/**
 * عدّاد الأوزان لقائمة النواقص (يظهر بطاقةً أعلى قائمة المواد): مجموع ما يلزم
 * شراؤه مقسّماً حسب نوع الوحدة بلا أي تحويل مُخمَّن بين العائلتين —
 *  - [kilos]: عائلة الكيلو (كيلو = 1، نص كيلو = 0.5، ربع كيلو = 0.25) × الكمية.
 *  - [okes]: عائلة اللوقية (لوقية = 1، نص لوقية = 0.5، ربع لوقية = 0.25) × الكمية.
 *  - [pieces]: المواد بلا وحدة (مثل "بيض: 6") تُجمع كعدد.
 * [count] عدد المواد (الصفوف) المحسوبة.
 */
data class MaterialsWeightSummary(
    val kilos: Double = 0.0,
    val okes: Double = 0.0,
    val pieces: Double = 0.0,
    val count: Int = 0
) {
    val hasAnything: Boolean get() = kilos > 0.0 || okes > 0.0 || pieces > 0.0
}

fun List<Material>.weightSummary(): MaterialsWeightSummary {
    var kilos = 0.0
    var okes = 0.0
    var pieces = 0.0
    for (m in this) {
        val q = m.quantity
        when (MaterialUnit.fromLabel(m.unit)) {
            MaterialUnit.KG -> kilos += q
            MaterialUnit.HALF_KG -> kilos += q * 0.5
            MaterialUnit.QUARTER_KG -> kilos += q * 0.25
            MaterialUnit.OKE -> okes += q
            MaterialUnit.HALF_OKE -> okes += q * 0.5
            MaterialUnit.QUARTER_OKE -> okes += q * 0.25
            MaterialUnit.NONE -> pieces += q
        }
    }
    return MaterialsWeightSummary(kilos = kilos, okes = okes, pieces = pieces, count = size)
}


// ============================================================================
// حساب أسعار النواقص
//
// القاعدة (بدون أي استثناء):
//   سعر المادة       = سعر الكيلو المُسجَّل لها × وزنها بالكيلو
//   وزنها بالكيلو    = الكمية × معامل الوحدة (انظر [kgFactor])
//   إجمالي القائمة   = مجموع (سعر كل مادة × وزنها)
//
// المعاملات: كيلو 1 | نص كيلو 0.5 | ربع كيلو 0.25
//            لوقية = [OKE_IN_KG] | نص لوقية = نصفها | ربع لوقية = ربعها
// المواد "بالعدد" (بلا وحدة، مثل بيض) تُضرب بالكمية مباشرة (السعر = سعر القطعة).
// ============================================================================

/**
 * وزن اللوقية الواحدة بالكيلو. القيمة الحالية 0.05 (أي 50 غرام، الكيلو = 20 لوقية).
 * إذا كانت اللوقية عندك بوزن مختلف غيّر هذا الرقم فقط — كل الحسابات في التطبيق
 * (القائمة، التبويب، المشاركة) تقرأ منه.
 */
const val OKE_IN_KG: Double = 0.05

/** معامل تحويل وحدة واحدة من هذا النوع إلى كيلو (أو إلى قطعة للمواد بلا وحدة). */
fun MaterialUnit.kgFactor(): Double = when (this) {
    MaterialUnit.KG -> 1.0
    MaterialUnit.HALF_KG -> 0.5
    MaterialUnit.QUARTER_KG -> 0.25
    MaterialUnit.OKE -> OKE_IN_KG
    MaterialUnit.HALF_OKE -> OKE_IN_KG * 0.5
    MaterialUnit.QUARTER_OKE -> OKE_IN_KG * 0.25
    MaterialUnit.NONE -> 1.0
}

/** التقريب لأقرب جزء من مئة حتى لا تتراكم أخطاء الفاصلة العائمة في المجاميع. */
private fun roundMoney(v: Double): Double = Math.round(v * 100.0) / 100.0

/** الوزن الفعلي لهذا الصف بالكيلو (أو عدد القطع للمواد بلا وحدة). */
fun Material.weightInKg(): Double = quantity * MaterialUnit.fromLabel(unit).kgFactor()

/** سعر الصف = سعر الكيلو × الوزن. null إذا لم تُسعَّر المادة بعد. */
fun Material.linePrice(pricePerKg: Double?): Double? =
    if (pricePerKg == null) null else roundMoney(pricePerKg * weightInKg())

/** سعر المادة من خريطة الأسعار (البحث بالاسم كما هو، ثم بعد قصّ الفراغات). */
fun Map<String, Double>.priceOf(name: String): Double? = this[name] ?: this[name.trim()]

fun Material.linePrice(prices: Map<String, Double>): Double? = linePrice(prices.priceOf(name))

/** نتيجة تجميع أسعار القائمة: المجموع + عدد المواد التي لا سعر لها (غير داخلة في المجموع). */
data class MaterialsPriceSummary(val total: Double = 0.0, val unpriced: Int = 0)

fun List<Material>.priceSummary(prices: Map<String, Double>): MaterialsPriceSummary {
    var total = 0.0
    var unpriced = 0
    for (m in this) {
        val line = m.linePrice(prices)
        if (line == null) unpriced++ else total += line
    }
    return MaterialsPriceSummary(total = roundMoney(total), unpriced = unpriced)
}

data class MaterialsPriceBreakdown(
    val important: MaterialsPriceSummary = MaterialsPriceSummary(),
    val regular: MaterialsPriceSummary = MaterialsPriceSummary(),
    val all: MaterialsPriceSummary = MaterialsPriceSummary()
)

fun List<Material>.priceBreakdown(prices: Map<String, Double>): MaterialsPriceBreakdown =
    MaterialsPriceBreakdown(
        important = filter { it.important }.priceSummary(prices),
        regular = filter { !it.important }.priceSummary(prices),
        all = priceSummary(prices)
    )
