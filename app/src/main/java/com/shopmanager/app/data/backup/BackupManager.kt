package com.shopmanager.app.data.backup

import android.content.Context
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.debts.Installment
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fully local, on-device backup. No server involved, no notification ever
 * posted for it, no storage permission needed — the snapshot lives in the
 * app's own private storage (`filesDir/backups`), the same place Android
 * already isolates for this app alone, so it's invisible in any file
 * manager and is wiped automatically only if the app itself is
 * uninstalled (same lifetime guarantee as everything else the app owns).
 *
 * Recent snapshots are kept per kind (see KEEP_DAILY / KEEP_INSTANT) so a
 * bad or empty state can never overwrite the only good copy. An empty
 * snapshot is never written while older ones exist.
 *
 * Restoring is intentionally NEVER silent/automatic in the sense of
 * quietly overwriting Firestore on its own: it always requires either an
 * explicit tap in Settings, or a tap on the one-time prompt shown when the
 * live data fails to load from the server (see DebtsViewModel /
 * MaterialsViewModel `hasSyncError`). That keeps a real, temporary network
 * hiccup from ever being "fixed" by silently discarding whatever the
 * server actually has.
 */
/**
 * Which trigger produced a given local snapshot. Kept as a filename
 * prefix (not a JSON field) so pruning can tell the two apart with a
 * plain directory listing — no need to open/parse every file just to
 * decide what to delete.
 */
enum class BackupKind(val filePrefix: String) {
    /** Once a day, via [com.shopmanager.app.data.backup.DailyBackupWorker]. */
    DAILY("backup_daily_"),
    /**
     * Right after a real data change, via [InstantBackupWorker]. Requested
     * both from a local add/edit/delete (see DebtsViewModel/
     * MaterialsViewModel's action functions) AND from those same
     * ViewModels' live Firestore listeners (personsFlow/debtsFlow/
     * materialsFlow/pricesFlow) — so a change made on *another* device
     * signed into the same shop also produces a fresh local snapshot on
     * *this* device the moment it's seen, not only on the device that
     * made the change.
     */
    INSTANT("backup_instant_")
}

object BackupManager {

    private const val DIR_NAME = "backups"
    private const val LEGACY_PREFIX = "backup_"
    private const val SUFFIX = ".json"
    private const val KEEP_DAILY = 7
    private const val KEEP_INSTANT = 5
    private const val KEEP_LEGACY = 2

    private fun stampFormat() = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)

    private fun backupDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    data class BackupInfo(
        val file: File,
        val createdAt: Long,
        val personsCount: Int,
        val materialsCount: Int
    )

    /**
     * Reads current data straight from Firestore — which itself already
     * falls back to its local offline cache automatically when there's no
     * network (see FirebaseModule's persistent cache setup) — and writes
     * one timestamped JSON snapshot, then prunes every other snapshot (see
     * [pruneOldBackups] - only one is ever kept). Throws only if there's
     * truly nothing synced yet
     * (e.g. very first run, no network, empty cache); the daily worker
     * treats that as "try again next scheduled run", never as something
     * worth surfacing to the person.
     */
    suspend fun performBackup(
        context: Context,
        debtsRepo: DebtsRepository,
        materialsRepo: MaterialsRepository,
        kind: BackupKind = BackupKind.DAILY
    ) {
        val json = buildSnapshotJson(debtsRepo, materialsRepo)

        val isEmptySnapshot = (json.optJSONArray("persons")?.length() ?: 0) == 0 &&
            (json.optJSONArray("materials")?.length() ?: 0) == 0
        if (isEmptySnapshot && backupFiles(context).isNotEmpty()) return

        // Millisecond-resolution timestamp in the filename (not just
        // seconds) so two backups triggered a moment apart — e.g. an
        // instant backup plus a WorkManager retry — can never collide on
        // the exact same filename and silently overwrite one another.
        val file = File(backupDir(context), "${kind.filePrefix}${stampFormat().format(System.currentTimeMillis())}$SUFFIX")
        file.writeText(json.toString())

        pruneOldBackups(context)
    }

    /**
     * FEATURE ADDED ("استعادة نسخة تم تصديرها"): the on-device snapshot
     * above (performBackup/restore) never leaves the app's own private
     * storage, so it protects against Firestore being unreachable but not
     * against losing the phone itself, or moving to a new one. This builds
     * the exact same JSON shape as [performBackup] but just returns the
     * text — used by Settings' "تصدير نسخة كملف" to hand the person a real
     * file (via the system's own "Save to..." picker) they can keep in
     * Drive/WhatsApp/a USB drive/another phone, and load back later through
     * [restoreFromJsonText].
     */
    suspend fun buildSnapshotJson(debtsRepo: DebtsRepository, materialsRepo: MaterialsRepository): JSONObject {
        val (persons, debts) = debtsRepo.fetchAllForBackup()
        val (materials, prices, catalog) = materialsRepo.fetchAllForBackup()

        val pricesJson = JSONObject()
        prices.forEach { (name, price) -> pricesJson.put(name, price) }

        return JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("persons", JSONArray(persons.map { it.toJson() }))
            put("debts", JSONArray(debts.map { it.toJson() }))
            put("materials", JSONArray(materials.map { it.toJson() }))
            put("prices", pricesJson)
            put("catalog", JSONArray(catalog.map { it.toJson() }))
        }
    }

    fun listBackups(context: Context): List<BackupInfo> =
        backupDir(context)
            .listFiles { f -> f.name.startsWith(LEGACY_PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f ->
                runCatching {
                    val json = JSONObject(f.readText())
                    BackupInfo(
                        file = f,
                        createdAt = json.optLong("createdAt", f.lastModified()),
                        personsCount = json.optJSONArray("persons")?.length() ?: 0,
                        materialsCount = json.optJSONArray("materials")?.length() ?: 0
                    )
                }.getOrNull()
            } ?: emptyList()

    /** Time of the newest local snapshot (file timestamp only, no parsing), or null if none exist. */
    fun lastBackupAt(context: Context): Long? =
        backupFiles(context).maxOfOrNull { it.lastModified() }?.takeIf { it > 0L }

    private fun backupFiles(context: Context): List<File> =
        backupDir(context)
            .listFiles { f -> f.name.startsWith(LEGACY_PREFIX) && f.name.endsWith(SUFFIX) }
            ?.toList() ?: emptyList()

    private fun pruneOldBackups(context: Context) {
        val files = backupFiles(context)
        val groups = listOf(
            files.filter { it.name.startsWith(BackupKind.DAILY.filePrefix) } to KEEP_DAILY,
            files.filter { it.name.startsWith(BackupKind.INSTANT.filePrefix) } to KEEP_INSTANT,
            files.filter {
                !it.name.startsWith(BackupKind.DAILY.filePrefix) &&
                    !it.name.startsWith(BackupKind.INSTANT.filePrefix)
            } to KEEP_LEGACY
        )
        groups.forEach { (group, keep) ->
            group.sortedByDescending { it.name }.drop(keep).forEach { it.delete() }
        }
    }

    /** Writes a chosen local snapshot back into Firestore, replacing what's
     * there now. Only ever called after explicit user confirmation. */
    suspend fun restore(
        backup: BackupInfo,
        debtsRepo: DebtsRepository,
        materialsRepo: MaterialsRepository
    ) = restoreFromJsonText(backup.file.readText(), debtsRepo, materialsRepo)

    /**
     * FEATURE ADDED ("استعادة نسخة تم تصديرها"): shared by [restore] (the
     * on-device snapshot) and Settings' "استعادة من ملف" (a file the person
     * picks via the system file browser — could be an export from THIS
     * phone or one copied over from another). Same tolerant parsing as the
     * on-device path below: a missing/renamed/corrupt section never takes
     * down the sections that did parse.
     */
    suspend fun restoreFromJsonText(
        jsonText: String,
        debtsRepo: DebtsRepository,
        materialsRepo: MaterialsRepository
    ) {
        val json = JSONObject(jsonText)

        if ((json.optJSONArray("persons")?.length() ?: 0) == 0 &&
            (json.optJSONArray("materials")?.length() ?: 0) == 0
        ) {
            throw IllegalArgumentException("النسخة المحددة فارغة ولن تتم استعادتها")
        }

        // BUG FIXED (استعادة تفشل بالكامل): only ONE snapshot is ever kept
        // on disk (see the class doc above) - there is no older backup to
        // fall back on if this single file's shape doesn't exactly match
        // what performBackup currently writes. `getJSONArray`/
        // `getJSONObject` throw the moment a key is missing, so a snapshot
        // written before a field existed (e.g. "catalog" was added after
        // "persons"/"debts"), or a JSON file that lost one field to disk
        // corruption, used to fail the *entire* restore - every section,
        // even the ones that parsed fine - and the person's only local
        // safety net was gone. Switched to the `opt*` variants (empty
        // array/object when the key is absent) so each section restores
        // independently: a missing/corrupt "catalog" no longer takes
        // "persons"/"debts"/"materials"/"prices" down with it.
        val persons = json.optJSONArray("persons")?.toObjectList { it.toPerson() } ?: emptyList()
        val debts = json.optJSONArray("debts")?.toObjectList { it.toDebt() } ?: emptyList()
        val materials = json.optJSONArray("materials")?.toObjectList { it.toMaterial() } ?: emptyList()
        val catalog = json.optJSONArray("catalog")?.toObjectList { it.toCatalogItem() } ?: emptyList()
        val pricesJson = json.optJSONObject("prices")
        val prices = pricesJson?.keys()?.asSequence()?.associateWith { pricesJson.optDouble(it, 0.0) } ?: emptyMap()

        debtsRepo.restoreFromBackup(persons, debts)
        materialsRepo.restoreFromBackup(materials, prices, catalog)
    }

    // --- (de)serialization helpers -----------------------------------

    private inline fun <T> JSONArray.toObjectList(map: (JSONObject) -> T): List<T> =
        (0 until length()).map { map(getJSONObject(it)) }

    private fun Person.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("amount", amount); put("date", date); put("createdAt", createdAt)
        put("phone", phone)
        put("dueDate", dueDate)
        put("installments", JSONArray().apply {
            installments.forEach { i ->
                put(JSONObject().put("id", i.id).put("amount", i.amount).put("date", i.date).put("paid", i.paid))
            }
        })
    }

    private fun Debt.toJson() = JSONObject().apply {
        put("id", id); put("personId", personId); put("amount", amount)
        put("date", date); put("note", note); put("createdAt", createdAt)
    }

    private fun Material.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("quantity", quantity)
        put("unit", unit); put("section", section); put("notes", notes); put("updatedAt", updatedAt)
        put("important", important); put("order", order)
    }

    private fun MaterialCatalogItem.toJson() = JSONObject().apply { put("id", id); put("name", name); put("group", group) }

    private fun JSONObject.toPerson() = Person(
        id = optString("id"), name = optString("name"), amount = optDouble("amount", 0.0),
        date = optString("date"), createdAt = optLong("createdAt", 0L),
        phone = optString("phone"),
        dueDate = optLong("dueDate", 0L),
        installments = optJSONArray("installments")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { o ->
                    Installment(
                        id = o.optString("id"),
                        amount = o.optDouble("amount", 0.0),
                        date = o.optLong("date", 0L),
                        paid = o.optBoolean("paid", false)
                    )
                }
            }
        } ?: emptyList()
    )

    private fun JSONObject.toDebt() = Debt(
        id = optString("id"), personId = optString("personId"), amount = optDouble("amount", 0.0),
        date = optString("date"), note = optString("note"), createdAt = optLong("createdAt", 0L)
    )

    private fun JSONObject.toMaterial() = Material(
        id = optString("id"), name = optString("name"), quantity = optDouble("quantity", 0.0),
        unit = optString("unit"), section = optString("section", "main"),
        notes = optString("notes"), updatedAt = optLong("updatedAt", 0L),
        important = optBoolean("important", false),
        order = optLong("order", optLong("updatedAt", 0L))
    )

    private fun JSONObject.toCatalogItem() = MaterialCatalogItem(id = optString("id"), name = optString("name"), group = optString("group"))
}
