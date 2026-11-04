package com.shopmanager.app.data.materials

import com.shopmanager.app.ui.common.sortedArabicBy
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
 * BUG FIXED (material-manager web version): every read/write in that app's
 * `materials.js` and `prices.js` called three globals - `isFirebaseReady()`,
 * `getDB()`, and `COLLECTION` / `PRICES_COLLECTION` - that were never defined
 * anywhere in the project. Every Firestore call threw before it could run,
 * so the app silently fell back to local-only storage. This repository talks
 * to Firestore directly, and every write below has a hard timeout so it can
 * never hang the UI indefinitely (e.g. before Firestore rules are published).
 */
class MaterialsRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.materialsDb
    private val materialsCollection = "spices_final_v12"
    private val pricesCollection = "material_prices"
    private val catalogCollection = "materials_catalog"

    fun listenMaterials(section: String): Flow<List<Material>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(materialsCollection)
            .whereEqualTo("section", section)
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.map { doc ->
                        Material(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            quantity = doc.getDouble("quantity") ?: 0.0,
                            unit = doc.getString("unit") ?: MaterialUnit.KG.label,
                            section = doc.getString("section") ?: "main",
                            notes = doc.getString("notes") ?: "",
                            updatedAt = doc.getLong("timestamp") ?: 0L,
                            important = doc.getBoolean("important") ?: false,
                            // Falls back to the write timestamp for any
                            // document written before "order" existed, so
                            // older shortages keep sorting in the
                            // chronological order they always displayed in
                            // (this used to be a name sort - see below).
                            order = doc.getLong("order") ?: (doc.getLong("timestamp") ?: 0L)
                        )
                    }
                    // Alphabetical (Arabic) order is the single source of truth for every list.
                    ?.sortedArabicBy { it.name }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    /** price is keyed by material name, matching the original web schema. */
    fun listenPrices(): Flow<Map<String, Double>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(pricesCollection)
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val prices = snapshot?.documents
                    ?.associate { (it.getString("name") ?: it.id) to (it.getDouble("price") ?: 0.0) }
                    ?: emptyMap()
                trySend(prices)
            }
        awaitClose { registration.remove() }
    }

    fun listenCatalog(): Flow<List<MaterialCatalogItem>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(catalogCollection)
            .addSnapshotListener(FirebaseModule.snapshotExecutor) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.map { MaterialCatalogItem(id = it.id, name = it.getString("name") ?: "", group = it.getString("group") ?: "") }
                    ?.sortedArabicBy { it.name }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    /**
     * BUG FIXED (self-notification / "إشعار عند إضافتي أنا للمادة"): this
     * used to discard the id Firestore assigns on `add()`. Without it,
     * MaterialsViewModel had no way to tell "this material just changed
     * because *I* added/edited it on this exact device" apart from "someone
     * (any device) changed the shared list" — so every add fired a shopping-
     * list notification straight back at whoever just made it, even though
     * they obviously already know what they just typed. Returning the new
     * document id lets the caller mark it as a local/self change and skip
     * notifying for it (see MaterialsViewModel.selfTouchedMaterialIds).
     */
    /**
     * BUG FIXED (إشعار قائمة المشتريات لسا يوصل أحياناً لنفس الجهاز رغم
     * selfTouchedMaterialIds): same race as DebtsRepository.addDebt —
     * `.add(data).await().id` only returns the id once the write is fully
     * durable, but Firestore's local cache (and this collection's live
     * listener) reacts to the write the instant it's queued, which can beat
     * the id ever reaching `selfTouchedMaterialIds`. Generating the
     * document reference client-side first and handing its id to
     * [onIdAssigned] before the actual write closes that window entirely.
     */
    suspend fun addMaterial(
        name: String,
        quantity: Double,
        unit: String,
        section: String,
        notes: String = "",
        onIdAssigned: (String) -> Unit = {}
    ): String =
        withTimeout(WRITE_TIMEOUT_MS) {
            val ref = db.collection(materialsCollection).document()
            onIdAssigned(ref.id)
            val now = System.currentTimeMillis()
            val data = mapOf(
                "name" to name,
                "quantity" to quantity,
                "unit" to unit,
                "section" to section,
                "notes" to notes,
                "timestamp" to now,
                "important" to false,
                // Starts as "now", same as the old add-time sort, so a
                // freshly added shortage lands at the bottom of the manual
                // order rather than jumping in wherever a millisecond
                // timestamp used for prior reorders happened to be.
                "order" to now
            )
            ref.set(data).awaitQueued()
            ref.id
        }

    suspend fun updateMaterial(id: String, name: String, quantity: Double, unit: String, section: String, notes: String = "") =
        withTimeout(WRITE_TIMEOUT_MS) {
            val data = mapOf(
                "name" to name,
                "quantity" to quantity,
                "unit" to unit,
                "section" to section,
                "notes" to notes,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(materialsCollection).document(id).update(data).awaitQueued()
            Unit
        }

    suspend fun deleteMaterial(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(materialsCollection).document(id).delete().awaitQueued()
        Unit
    }

    /**
     * FEATURE ADDED ("نجمة الأهمية"): toggles the star. Marking a shortage
     * important also pins it to the top by giving it `topOrder` (one less
     * than the current minimum `order` in the list - see
     * MaterialsViewModel.setImportant); un-marking it only flips the flag
     * back off and leaves its position exactly where it already was.
     */
    suspend fun setImportant(id: String, important: Boolean, topOrder: Long? = null) =
        withTimeout(WRITE_TIMEOUT_MS) {
            val data = if (topOrder != null) mapOf("important" to important, "order" to topOrder)
                else mapOf("important" to important)
            db.collection(materialsCollection).document(id).update(data).awaitQueued()
            Unit
        }

    /**
     * FEATURE ADDED ("ترتيب المواد بالضغط المطول وتحريكها"): persists a full
     * drag-reorder in one batch - each id gets a fresh sequential `order`
     * matching its new position in the list, so re-opening the app (or
     * another device's listener) shows the exact order just dragged into
     * place.
     */
    suspend fun updateMaterialsOrder(orderedIds: List<String>) = withTimeout(WRITE_TIMEOUT_MS) {
        orderedIds.withIndex().chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (position, id) ->
                batch.update(db.collection(materialsCollection).document(id), "order", position.toLong())
            }
            batch.commit().awaitQueued()
        }
        Unit
    }

    /**
     * Bulk delete for the "مسح الكل" (clear all) button on the materials
     * list — deletes every id given in as few batched commits as possible
     * instead of one `deleteMaterial` round trip per item. Chunked at 400
     * to stay under Firestore's 500-operation batch limit, same pattern as
     * [restoreFromBackup].
     */
    suspend fun deleteMaterials(ids: List<String>) = withTimeout(WRITE_TIMEOUT_MS) {
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(db.collection(materialsCollection).document(it)) }
            batch.commit().awaitQueued()
        }
        Unit
    }

    /**
     * BUG FIXED ("Document references must have an even number of segments"):
     * سعر المادة كان يُحفظ بوثيقة اسمها = اسم المادة، وأسماء مثل
     * "بذور القرع / اليقطين" فيها "/" فيفهمها Firestore كمسار فرعي ويرفض الحفظ.
     * الآن معرّف الوثيقة آمن ([priceDocId]) واسم المادة الحقيقي محفوظ داخل
     * الوثيقة في الحقل "name" (والقراءة تستخدمه، وتعود للمعرّف للوثائق القديمة).
     */
    private fun priceDocId(materialName: String): String {
        val cleaned = materialName.trim()
            .replace("/", "\u2215")   // ∕ بدل /
            .replace("\\", "\u2216")   // ∖ بدل \
            .ifBlank { "_" }
        val safe = if (cleaned == "." || cleaned == ".." || (cleaned.startsWith("__") && cleaned.endsWith("__"))) "_$cleaned" else cleaned
        return if (safe.toByteArray().size > 1400) safe.take(400) else safe
    }

    private fun priceData(materialName: String, price: Double) =
        mapOf("name" to materialName.trim(), "price" to price)

    suspend fun setPrice(materialName: String, price: Double) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(pricesCollection).document(priceDocId(materialName)).set(priceData(materialName, price)).awaitQueued()
        Unit
    }

    /** يحفظ كل الأسعار المعدّلة دفعة واحدة (عملية ذرية على السحابة: إما كلها أو لا شيء). */
    suspend fun setPrices(prices: Map<String, Double>) = withTimeout(WRITE_TIMEOUT_MS) {
        prices.entries.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (name, price) ->
                batch.set(db.collection(pricesCollection).document(priceDocId(name)), priceData(name, price))
            }
            batch.commit().awaitQueued()
        }
        Unit
    }

    suspend fun catalogNameExists(name: String): Boolean = withTimeout(WRITE_TIMEOUT_MS) {
        val snapshot = db.collection(catalogCollection).whereEqualTo("name", name).get().await()
        !snapshot.isEmpty
    }

    suspend fun addCatalogItem(name: String, group: String = "") = withTimeout(WRITE_TIMEOUT_MS) {
        val data = HashMap<String, Any>()
        data["name"] = name
        if (group.isNotBlank()) data["group"] = group
        db.collection(catalogCollection).document().set(data).awaitQueued()
        Unit
    }

    suspend fun setCatalogGroup(ids: List<String>, group: String) = withTimeout(60_000L) {
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.update(db.collection(catalogCollection).document(id), "group", group)
            }
            batch.commit().awaitQueued(30_000L)
        }
        Unit
    }

    suspend fun deleteCatalogItem(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(catalogCollection).document(id).delete().awaitQueued()
        Unit
    }

    /**
     * FEATURE ADDED ("تعديل المواد الثابتة بعد إضافتها"): renames a fixed
     * catalog entry in place instead of deleting and re-adding it (which
     * would also silently drop its saved price in [pricesCollection], since
     * prices are keyed by name).
     */
    suspend fun updateCatalogItem(id: String, name: String, group: String = "") = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(catalogCollection).document(id).update("name", name, "group", group).awaitQueued()
        Unit
    }

    /** One-off, server-sourced re-fetch used by pull-to-refresh — see
     * [com.shopmanager.app.data.debts.DebtsRepository.refreshFromServer]. */
    suspend fun refreshFromServer() = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(materialsCollection).get(com.google.firebase.firestore.Source.SERVER).await()
        db.collection(pricesCollection).get(com.google.firebase.firestore.Source.SERVER).await()
        Unit
    }

    /**
     * One-off, all-at-once read of materials + prices + catalog — used
     * only for the daily local backup snapshot (see BackupManager); the
     * rest of the app always uses the live listeners above.
     */
    suspend fun fetchAllForBackup(): Triple<List<Material>, Map<String, Double>, List<MaterialCatalogItem>> =
        withTimeout(WRITE_TIMEOUT_MS) {
            val materialsSnap = db.collection(materialsCollection).get().await()
            val pricesSnap = db.collection(pricesCollection).get().await()
            val catalogSnap = db.collection(catalogCollection).get().await()

            val materials = materialsSnap.documents.map { doc ->
                Material(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    quantity = doc.getDouble("quantity") ?: 0.0,
                    unit = doc.getString("unit") ?: MaterialUnit.KG.label,
                    section = doc.getString("section") ?: "main",
                    notes = doc.getString("notes") ?: "",
                    updatedAt = doc.getLong("timestamp") ?: 0L,
                    important = doc.getBoolean("important") ?: false,
                    order = doc.getLong("order") ?: (doc.getLong("timestamp") ?: 0L)
                )
            }
            val prices = pricesSnap.documents.associate { (it.getString("name") ?: it.id) to (it.getDouble("price") ?: 0.0) }
            val catalog = catalogSnap.documents.map { MaterialCatalogItem(id = it.id, name = it.getString("name") ?: "", group = it.getString("group") ?: "") }
            Triple(materials, prices, catalog)
        }

    /**
     * Restores a full local backup snapshot back into Firestore, wiping
     * every existing document in the three collections first and
     * rewriting the backed-up ones with their original ids. Batched in
     * chunks of 400 to stay under Firestore's 500-operation batch limit.
     * Only ever called after explicit confirmation — see
     * [com.shopmanager.app.data.debts.DebtsRepository.restoreFromBackup].
     */
    suspend fun restoreFromBackup(
        materials: List<Material>,
        prices: Map<String, Double>,
        catalog: List<MaterialCatalogItem>
    ) = withTimeout(120_000L) {
        val existingMaterials = db.collection(materialsCollection).get().await()
        val existingPrices = db.collection(pricesCollection).get().await()
        val existingCatalog = db.collection(catalogCollection).get().await()

        val materialWrites = materials.filter { it.id.isNotBlank() }.map {
            db.collection(materialsCollection).document(it.id) to mapOf(
                "name" to it.name, "quantity" to it.quantity, "unit" to it.unit,
                "section" to it.section, "notes" to it.notes, "timestamp" to it.updatedAt,
                "important" to it.important, "order" to it.order
            )
        }
        val priceWrites = prices.map { (name, price) ->
            db.collection(pricesCollection).document(priceDocId(name)) to priceData(name, price)
        }
        val catalogWrites = catalog.filter { it.id.isNotBlank() }.map {
            db.collection(catalogCollection).document(it.id) to (
                if (it.group.isBlank()) mapOf("name" to it.name) else mapOf("name" to it.name, "group" to it.group)
            )
        }
        (materialWrites + priceWrites + catalogWrites).chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().awaitQueued(30_000L)
        }

        val keepMaterials = materialWrites.map { it.first.id }.toSet()
        val keepPrices = priceWrites.map { it.first.id }.toSet()
        val keepCatalog = catalogWrites.map { it.first.id }.toSet()
        val deletes = existingMaterials.documents.filter { it.id !in keepMaterials }
            .map { db.collection(materialsCollection).document(it.id) } +
            existingPrices.documents.filter { it.id !in keepPrices }
                .map { db.collection(pricesCollection).document(it.id) } +
            existingCatalog.documents.filter { it.id !in keepCatalog }
                .map { db.collection(catalogCollection).document(it.id) }
        deletes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().awaitQueued(30_000L)
        }
        Unit
    }
}
