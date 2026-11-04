package com.shopmanager.app.data.notes

/** What (if anything) an [ImportantNote] is linked to - lets a note point
 * straight at a customer's debt record or a shortage-list item instead of
 * living as a totally disconnected scrap of text. */
enum class NoteLinkType { NONE, PERSON, MATERIAL }

fun NoteLinkType.toStorage(): String = when (this) {
    NoteLinkType.PERSON -> "person"
    NoteLinkType.MATERIAL -> "material"
    NoteLinkType.NONE -> "none"
}

fun noteLinkTypeFromStorage(value: String): NoteLinkType = when (value) {
    "person" -> NoteLinkType.PERSON
    "material" -> NoteLinkType.MATERIAL
    else -> NoteLinkType.NONE
}

/**
 * "ملاحظات هامة" (Important Notes) - a general-purpose sticky-note list,
 * separate from a per-debt/per-material note (see Debt.note / Material.notes
 * for those), meant for things that don't belong to one single record: a
 * reminder to call a customer back, a supplier's phone number, a task for
 * tomorrow morning, etc. Every note can optionally:
 *  - carry a reminder time, which schedules a local notification (see
 *    NoteReminderScheduler) exactly like an alarm - useful for "تذكير
 *    بتحصيل دين فلان يوم الخميس".
 *  - link itself to an existing customer or shortage-list item, so tapping
 *    the note can jump straight to that person's page (or filter/highlight
 *    that material) instead of the note being an island with no connection
 *    to the data it's actually about.
 */
data class ImportantNote(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val linkType: NoteLinkType = NoteLinkType.NONE,
    val linkedId: String = "",
    // Denormalized display name for the linked person/material, refreshed
    // every time the note is saved - avoids a join/lookup just to show
    // "مرتبطة بـ: أحمد" on the notes list itself.
    val linkedName: String = "",
    // 0L = no reminder set.
    val reminderAt: Long = 0L,
    val isPinned: Boolean = false,
    val isDone: Boolean = false,
    val createdAt: Long = 0L
)
