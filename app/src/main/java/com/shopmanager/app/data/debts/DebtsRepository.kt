package com.shopmanager.app.data.debts

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import com.shopmanager.app.data.awaitQueued

private const val WRITE_TIMEOUT_MS = 15_000L

/**
 * BUG FIXED (debt-app web version): the header stats (عدد الديون / إجمالي المبلغ)
 * were computed with a ONE-TIME `getDocs()` per customer, run only inside the
 * "persons" real-time listener. Adding/editing/deleting a debt never
 * re-triggered that listener, so the totals silently went stale until you
 * added or edited a *customer*. Here, persons and debts are each their own
 * live listener, and the UI layer combines both flows - so totals recompute
 * the instant any debt changes, on any device.
 *
 * BUG FIXED (this app): every write used to wait on Firestore with no upper
 * bound. If a write is rejected by security rules (e.g. before the rules
 * from firebase-rules/ are published) or the network stalls, the call could
 * hang indefinitely and the "جارِ الحفظ..." button would spin forever with
 * no way out. Every write below now has a hard 15s timeout, after which it
 * fails loudly (caught by the ViewModel, which resets isSaving and shows an
 * error) instead of hanging silently.
 */
class DebtsRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.debtsDb

    fun listenPersons(): Flow<List<Person>> = callbackFlow {
        // BUG FIXED: كان هنا orderBy("createdAt") — فايرستور يستبعد أي مستند لا يحتوي
        // هذا الحقل (عملاء قدامى/مستوردون)، فكانوا يختفون من القائمة كلياً. الترتيب
        // صار يتم على الجهاز بعد الجلب فلا يسقط أي عميل.
        val registration: ListenerRegistration = db.collection("persons")
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val persons = snapshot?.documents?.map { it.toPerson() } ?: emptyList()
                trySend(persons.sortedByDescending { it.createdAt })
            }
        awaitClose { registration.remove() }
    }

    /** Listens to the WHOLE debts collection so totals stay live everywhere. */
    fun listenAllDebts(): Flow<List<Debt>> = callbackFlow {
        val registration: ListenerRegistration = db.collection("debts")
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val debts = snapshot?.documents?.map { it.toDebt() } ?: emptyList()
                trySend(debts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Filtered by personId only (no orderBy in the query itself) so this never
     * needs a Firestore composite index - the small per-customer debt list is
     * sorted client-side instead.
     */
    fun listenDebtsForPerson(personId: String): Flow<List<Debt>> = callbackFlow {
        val registration: ListenerRegistration = db.collection("debts")
            .whereEqualTo("personId", personId)
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val debts = snapshot?.documents
                    ?.map { it.toDebt() }
                    // ترتيب حسب وقت الإضافة (الأحدث أولاً) ثم التاريخ المكتوب.
                    ?.sortedWith(compareByDescending<Debt> { it.createdAt }.thenByDescending { it.date })
                    ?: emptyList()
                trySend(debts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * BUG FIXED: marking a person as paid (the checkmark on the Debts list)
     * only ever deleted their debts — it never deleted the person, by
     * design, so their balance can go back up later. But `savePerson()`
     * used to treat *any* existing name as a hard duplicate and simply
     * refuse to save, with no way forward. So paying a customer off, then
     * trying to give them a new debt the normal way (typing their name
     * again in "عميل جديد"), hit "\"$name\" موجود مسبقاً!" every time and
     * went nowhere — the person had to already know to scroll down and
     * open the existing (zero-balance) customer instead. This now returns
     * the existing person's id (or null) so the caller can route a
     * same-name save into "add a debt to them" instead of a dead-end error.
     */
    suspend fun findPersonIdByName(name: String): String? = withTimeout(WRITE_TIMEOUT_MS) {
        val snapshot = db.collection("persons").whereEqualTo("name", name).get().await()
        snapshot.documents.firstOrNull()?.id
    }

    /**
     * One-off, server-sourced re-fetch used by pull-to-refresh. The screens
     * already stay live via [listenPersons]/[listenAllDebts], so this isn't
     * needed to see new data — but forcing a real round trip to the server
     * (bypassing Firestore's local cache) is a cheap way to detect and
     * recover a snapshot listener that silently stalled after a network
     * blip, instead of leaving the person pulling down with nothing to show
     * for it.
     */
    suspend fun refreshFromServer() = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("persons").get(com.google.firebase.firestore.Source.SERVER).await()
        db.collection("debts").get(com.google.firebase.firestore.Source.SERVER).await()
        Unit
    }

    /**
     * BUG FIXED: adding a new person used to only write the "persons" doc
     * with an `amount` field — it never created a matching row in the
     * "debts" collection. Since Person Detail's debt history is driven
     * entirely by the debts collection, that initial amount silently never
     * showed up there; the person had to open the new client and re-enter
     * the exact same amount as a debt for it to actually appear. Now both
     * documents are written together, atomically, in one batch.
     *
     * BUG FIXED (PERMISSION_DENIED on "إضافة الدين" after paying a customer
     * off): this initial debt document used to be written WITHOUT a "note"
     * field, while every debt created afterward through [addDebt]/
     * [updateDebt] always writes one (even as ""). So two different field
     * shapes existed for documents in the same "debts" collection. If the
     * Firestore rules deployed on the console validate the write schema
     * (e.g. `request.resource.data.keys().hasOnly([...])`) against the
     * narrower shape written here, any *later* debt add — which is exactly
     * what happens right after settling someone and giving them a new
     * debt — carries a key the rules never saw permitted for that customer
     * and gets rejected as PERMISSION_DENIED. Every debt document now has
     * an identical field set no matter which code path created it.
     */
    /**
     * BUG FIXED (self-notification / "إشعار عميل جديد عند إضافتي أنا له"):
     * this used to return Unit, discarding the ids generated for the new
     * person/debt documents. DebtsViewModel's "new debt" notification is
     * driven by diffing the live debts listener (so it also catches debts
     * added from *other* devices) — without the id coming back from here,
     * it had no way to tell that particular apart from "I just added this
     * myself, seconds ago, on this exact screen" and notified the same
     * device that just typed it in. Returning the new debt id (when one was
     * created) lets the caller mark it as a local/self change so the diff
     * can skip notifying for it — see DebtsViewModel.selfCreatedDebtIds.
     */
    suspend fun addPerson(
        name: String,
        amount: Double,
        date: String,
        note: String = "",
        phone: String = "",
        onIdAssigned: (String) -> Unit = {}
    ): String? = withTimeout(WRITE_TIMEOUT_MS) {
        val personRef = db.collection("persons").document()
        val batch = db.batch()
        batch.set(
            personRef,
            mapOf(
                "name" to name,
                "amount" to amount,
                "date" to date,
                "phone" to phone,
                "createdAt" to System.currentTimeMillis()
            )
        )
        var debtId: String? = null
        if (amount > 0) {
            val debtRef = db.collection("debts").document()
            debtId = debtRef.id
            // Same race fix as [addDebt]: the id is known client-side the
            // moment document() is called, so it's handed to the caller
            // right away (still before batch.commit().await() below) instead
            // of only after the whole batch is durably committed.
            onIdAssigned(debtRef.id)
            batch.set(
                debtRef,
                mapOf(
                    "personId" to personRef.id,
                    "amount" to amount,
                    "date" to date,
                    // BUG FIXED: this initial debt used to always be written
                    // with a hardcoded empty note, even though the "عميل
                    // جديد" dialog now collects one (see PersonEditDialog) -
                    // every debt added afterward through addDebt()/
                    // updateDebt() already supported a note, so the very
                    // first debt was the only one that couldn't have one.
                    "note" to note,
                    "createdAt" to System.currentTimeMillis()
                )
            )
        }
        batch.commit().awaitQueued()
        debtId
    }

    /** [phone] = null يترك الرقم المحفوظ كما هو (لا يمسحه)؛ نص فارغ يمسحه فعلاً. */
    suspend fun updatePerson(id: String, name: String, amount: Double, date: String, phone: String? = null) = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mutableMapOf<String, Any>("name" to name, "amount" to amount, "date" to date)
        if (phone != null) data["phone"] = phone
        db.collection("persons").document(id).update(data).awaitQueued()
        Unit
    }

    suspend fun deletePersonWithDebts(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val debts = db.collection("debts").whereEqualTo("personId", id).get().await()
        val refs = debts.documents.map { db.collection("debts").document(it.id) } +
            db.collection("persons").document(id)
        refs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().awaitQueued()
        }
        Unit
    }

    suspend fun markAllDebtsAsPaid(personId: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val debts = db.collection("debts").whereEqualTo("personId", personId).get().await()
        debts.documents.map { db.collection("debts").document(it.id) }.chunked(400).forEach { chunk ->
            val chunkBatch = db.batch()
            chunk.forEach { chunkBatch.delete(it) }
            chunkBatch.commit().awaitQueued()
        }
        Unit
    }

    /**
     * BUG FIXED (إشعار "عميل جديد بالديون" لسا يوصل أحياناً لنفس الجهاز
     * رغم selfCreatedDebtIds): `.add(data).await().id` only reveals the new
     * document's id once the write is fully durable — but Firestore's local
     * cache applies the write (and re-fires every listener on it, with
     * `hasPendingWrites`) the instant it's queued, well before that network
     * round trip finishes. Since the ViewModel only added the id to
     * `selfCreatedDebtIds` AFTER this whole suspend function returned, the
     * live listener's diff (running on the same dispatcher) could — and
     * sometimes did, depending on how the coroutine happened to interleave
     * with the snapshot callback — see the new debt and fire the "عميل
     * جديد" notification before the id was ever registered as self-made.
     * `db.collection("debts").document()` generates the id purely
     * client-side (no network call), so calling [onIdAssigned] with it
     * BEFORE the actual `.set(data).await()` guarantees the id lands in
     * `selfCreatedDebtIds` synchronously, on this same thread, before this
     * coroutine ever suspends — there is no longer a window for the
     * listener to see the new id first.
     */
    suspend fun addDebt(
        personId: String,
        amount: Double,
        date: String,
        note: String = "",
        onIdAssigned: (String) -> Unit = {}
    ): String = withTimeout(WRITE_TIMEOUT_MS) {
        val ref = db.collection("debts").document()
        onIdAssigned(ref.id)
        val data = mapOf(
            "personId" to personId,
            "amount" to amount,
            "date" to date,
            "note" to note,
            "createdAt" to System.currentTimeMillis()
        )
        ref.set(data).awaitQueued()
        ref.id
    }

    suspend fun updateDebt(id: String, amount: Double, date: String, note: String = "") = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf("amount" to amount, "date" to date, "note" to note)
        db.collection("debts").document(id).update(data).awaitQueued()
        Unit
    }

    suspend fun deleteDebt(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("debts").document(id).delete().awaitQueued()
        Unit
    }

    /**
     * Marks a single debt as paid: removes the debt entry. The person's
     * displayed total is derived live from the sum of their remaining debts
     * (see DebtsViewModel), so no separate "update the person's amount"
     * write is needed here — one less place for the two to drift apart.
     */
    suspend fun markDebtAsPaid(debtId: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("debts").document(debtId).delete().awaitQueued()
        Unit
    }

    /**
     * One-off, all-at-once read of every person and debt — used only for
     * the daily local backup snapshot (see BackupManager); the rest of the
     * app always uses the live listeners above.
     */
    suspend fun restoreDebts(debts: List<Debt>) = withTimeout(WRITE_TIMEOUT_MS) {
        debts.filter { it.id.isNotBlank() }.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { d ->
                batch.set(
                    db.collection("debts").document(d.id),
                    mapOf(
                        "personId" to d.personId, "amount" to d.amount, "date" to d.date,
                        "note" to d.note, "createdAt" to d.createdAt
                    )
                )
            }
            batch.commit().awaitQueued()
        }
        Unit
    }

    suspend fun restorePerson(person: Person, debts: List<Debt>) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("persons").document(person.id).set(
            person.toFields()
        ).awaitQueued()
        restoreDebtsNoTimeout(debts)
    }

    private suspend fun restoreDebtsNoTimeout(debts: List<Debt>) {
        debts.filter { it.id.isNotBlank() }.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { d ->
                batch.set(
                    db.collection("debts").document(d.id),
                    mapOf(
                        "personId" to d.personId, "amount" to d.amount, "date" to d.date,
                        "note" to d.note, "createdAt" to d.createdAt
                    )
                )
            }
            batch.commit().awaitQueued()
        }
    }

    suspend fun fetchAllForBackup(): Pair<List<Person>, List<Debt>> = withTimeout(WRITE_TIMEOUT_MS) {
        val personsSnap = db.collection("persons").get().await()
        val debtsSnap = db.collection("debts").get().await()
        val persons = personsSnap.documents.map { it.toPerson() }
        val debts = debtsSnap.documents.map { it.toDebt() }
        persons to debts
    }

    /**
     * Restores a full local backup snapshot back into Firestore: wipes
     * every existing "persons"/"debts" document and rewrites the
     * backed-up ones with their original document ids (so person <-> debt
     * links by id keep working and nothing re-orders). Batched in chunks
     * of 400 to stay under Firestore's 500-operation batch limit. Only
     * ever called after explicit confirmation from Settings, or from the
     * one-tap "server unavailable, restore local backup" prompt — never
     * silently.
     */
    suspend fun restoreFromBackup(persons: List<Person>, debts: List<Debt>) = withTimeout(120_000L) {
        val existingPersons = db.collection("persons").get().await()
        val existingDebts = db.collection("debts").get().await()

        val personWrites = persons.filter { it.id.isNotBlank() }.map {
            db.collection("persons").document(it.id) to it.toFields()
        }
        val debtWrites = debts.filter { it.id.isNotBlank() }.map {
            db.collection("debts").document(it.id) to mapOf(
                "personId" to it.personId, "amount" to it.amount, "date" to it.date,
                "note" to it.note, "createdAt" to it.createdAt
            )
        }
        (personWrites + debtWrites).chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().awaitQueued(30_000L)
        }

        val keepPersons = personWrites.map { it.first.id }.toSet()
        val keepDebts = debtWrites.map { it.first.id }.toSet()
        val deletes = existingPersons.documents.filter { it.id !in keepPersons }
            .map { db.collection("persons").document(it.id) } +
            existingDebts.documents.filter { it.id !in keepDebts }
                .map { db.collection("debts").document(it.id) }
        deletes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().awaitQueued(30_000L)
        }
        Unit
    }

    suspend fun updateSchedule(personId: String, dueDate: Long, installments: List<Installment>) =
        withTimeout(WRITE_TIMEOUT_MS) {
            db.collection("persons").document(personId).update(
                mapOf(
                    "dueDate" to dueDate,
                    "installments" to installments.map { it.toMap() }
                )
            ).awaitQueued()
            Unit
        }

    private fun Installment.toMap(): Map<String, Any> =
        mapOf("id" to id, "amount" to amount, "date" to date, "paid" to paid)

    private fun Person.toFields(): Map<String, Any> = mapOf(
        "name" to name, "amount" to amount, "date" to date, "createdAt" to createdAt,
        "phone" to phone, "dueDate" to dueDate, "installments" to installments.map { it.toMap() }
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toPerson() = Person(
        id = id,
        name = getString("name") ?: "",
        amount = getDouble("amount") ?: 0.0,
        date = getString("date") ?: "",
        createdAt = getLong("createdAt") ?: 0L,
        phone = getString("phone") ?: "",
        dueDate = getLong("dueDate") ?: 0L,
        installments = (get("installments") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            Installment(
                id = m["id"] as? String ?: "",
                amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
                date = (m["date"] as? Number)?.toLong() ?: 0L,
                paid = m["paid"] as? Boolean ?: false
            )
        } ?: emptyList()
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toDebt() = Debt(
        id = id,
        personId = getString("personId") ?: "",
        amount = getDouble("amount") ?: 0.0,
        date = getString("date") ?: "",
        note = getString("note") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )
}
