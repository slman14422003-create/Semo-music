package com.shopmanager.app.data.notes

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import com.shopmanager.app.data.awaitQueued

private const val WRITE_TIMEOUT_MS = 15_000L

/**
 * Same shared Firebase project/collection pattern as [com.shopmanager.app.data.debts.DebtsRepository]
 * and [com.shopmanager.app.data.materials.MaterialsRepository] - its own
 * collection, same hard write timeout so a stuck write can't hang the UI.
 */
class NotesRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.notesDb
    private val notesCollection = "important_notes"

    fun listenNotes(): Flow<List<ImportantNote>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(notesCollection)
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val notes = snapshot?.documents
                    ?.map { it.toNote() }
                    // Pinned first, then newest first - matches how a
                    // person naturally scans a sticky-note list: the ones
                    // they starred stay glued to the top regardless of age.
                    ?.sortedWith(compareByDescending<ImportantNote> { it.isPinned }.thenByDescending { it.createdAt })
                    ?: emptyList()
                trySend(notes)
            }
        awaitClose { registration.remove() }
    }

    /**
     * BUG FIXED (إشعار ملاحظة جديدة لسا يوصل أحياناً لنفس الجهاز رغم
     * selfCreatedNoteIds): same race as DebtsRepository.addDebt /
     * MaterialsRepository.addMaterial. The id is generated client-side via
     * `document()` and handed to [onIdAssigned] before the write is sent,
     * so NotesViewModel can register it as self-created synchronously —
     * before this coroutine ever suspends on the network write — instead
     * of only after the write was fully durable.
     */
    suspend fun addNote(note: ImportantNote, onIdAssigned: (String) -> Unit = {}): String = withTimeout(WRITE_TIMEOUT_MS) {
        val ref = db.collection(notesCollection).document()
        onIdAssigned(ref.id)
        val data = mapOf(
            "title" to note.title,
            "content" to note.content,
            "linkType" to note.linkType.toStorage(),
            "linkedId" to note.linkedId,
            "linkedName" to note.linkedName,
            "reminderAt" to note.reminderAt,
            "isPinned" to note.isPinned,
            "isDone" to note.isDone,
            "createdAt" to System.currentTimeMillis()
        )
        ref.set(data).awaitQueued()
        ref.id
    }

    suspend fun updateNote(note: ImportantNote) = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf(
            "title" to note.title,
            "content" to note.content,
            "linkType" to note.linkType.toStorage(),
            "linkedId" to note.linkedId,
            "linkedName" to note.linkedName,
            "reminderAt" to note.reminderAt,
            "isPinned" to note.isPinned,
            "isDone" to note.isDone
        )
        db.collection(notesCollection).document(note.id).update(data).awaitQueued()
        Unit
    }

    suspend fun setDone(id: String, done: Boolean) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).update("isDone", done).awaitQueued()
        Unit
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).update("isPinned", pinned).awaitQueued()
        Unit
    }

    /**
     * BUG FIXED ("تأجيل ساعة" ما كان يحدّث وقت التذكير المخزّن بالملاحظة):
     * تأجيل تذكير من زر الإشعار (راجع NotificationActionReceiver.handleSnoozeNote)
     * كان يعيد جدولة WorkManager محليًا بس، من دون ما يلمس حقل reminderAt
     * بمستند الملاحظة نفسها. فتح تلك الملاحظة للتعديل بعدها كان يعرض وقت
     * التذكير الأصلي (اللي فات فعلاً بما إنو الشخص أجّله)، وأي حفظ لاحق -
     * حتى لو غير متعلق بالتذكير إطلاقًا - كان يرسل هالوقت الفائت لـ
     * updateNote، وهاي بدورها كانت تلغي التأجيل بصمت لأن
     * NoteReminderWorker.schedule() يتجاهل أي وقت بالماضي. تحديث الحقل
     * مباشرة هون (بدل استدعاء updateNote الكامل، اللي بده كل حقول الملاحظة
     * وما نملكها بسياق الإشعار) يخلي المستند يعكس وقت التأجيل الحقيقي.
     */
    suspend fun updateReminderAt(id: String, reminderAtMillis: Long) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).update("reminderAt", reminderAtMillis).awaitQueued()
        Unit
    }

    /**
     * "ترابط بين الديون والملاحظات": يُستدعى لما يُحذف عميل بالكامل (راجع
     * DebtsViewModel.deletePerson) عشان أي ملاحظة كانت مرتبطة فيه ما تضل
     * مؤشّرة على id عميل ما عاد موجود. النص/العنوان اللي كتبه الشخص محتوى
     * حقيقي وبيضل - بس الربط الميت نفسه ينمسح (نفس اختيار "بدون ربط" يدويًا
     * بمحرر الملاحظة)، فالملاحظة تصير عامة بدل ما تشاور على عميل محذوف.
     * فلترة بحقلين equality بس (بدون orderBy) هيك ما بتحتاج composite index
     * بـ Firestore - نفس أسلوب DebtsRepository.listenDebtsForPerson.
     */
    suspend fun unlinkNotesForPerson(personId: String) = withTimeout(WRITE_TIMEOUT_MS) {
        if (personId.isNotBlank()) {
            val orphaned = db.collection(notesCollection)
                .whereEqualTo("linkType", NoteLinkType.PERSON.toStorage())
                .whereEqualTo("linkedId", personId)
                .get().await()
            if (!orphaned.isEmpty) {
                val batch = db.batch()
                orphaned.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "linkType" to NoteLinkType.NONE.toStorage(),
                            "linkedId" to "",
                            "linkedName" to ""
                        )
                    )
                }
                batch.commit().awaitQueued()
            }
        }
        Unit
    }

    suspend fun deleteNote(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).delete().awaitQueued()
        Unit
    }

    /** One-off, all-at-once read - used only for the daily local backup
     * snapshot (see BackupManager); the rest of the app uses [listenNotes]. */
    suspend fun fetchAllForBackup(): List<ImportantNote> = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).get().await().documents.map { it.toNote() }
    }

    suspend fun restoreFromBackup(notes: List<ImportantNote>) = withTimeout(120_000L) {
        val existing = db.collection(notesCollection).get().await()
        val writes = notes.filter { it.id.isNotBlank() }.map {
            db.collection(notesCollection).document(it.id) to mapOf(
                "title" to it.title,
                "content" to it.content,
                "linkType" to it.linkType.toStorage(),
                "linkedId" to it.linkedId,
                "linkedName" to it.linkedName,
                "reminderAt" to it.reminderAt,
                "isPinned" to it.isPinned,
                "isDone" to it.isDone,
                "createdAt" to it.createdAt
            )
        }
        writes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().awaitQueued(30_000L)
        }
        val keep = writes.map { it.first.id }.toSet()
        existing.documents.filter { it.id !in keep }
            .map { db.collection(notesCollection).document(it.id) }
            .chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().awaitQueued(30_000L)
            }
        Unit
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toNote() = ImportantNote(
        id = id,
        title = getString("title") ?: "",
        content = getString("content") ?: "",
        linkType = noteLinkTypeFromStorage(getString("linkType") ?: "none"),
        linkedId = getString("linkedId") ?: "",
        linkedName = getString("linkedName") ?: "",
        reminderAt = getLong("reminderAt") ?: 0L,
        isPinned = getBoolean("isPinned") ?: false,
        isDone = getBoolean("isDone") ?: false,
        createdAt = getLong("createdAt") ?: 0L
    )
}
