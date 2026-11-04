package com.shopmanager.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * "طبقة مساعدة للمزامنة" (sync helper layer): before this, the only signal
 * anyone had about sync health was [hasSyncError] on DebtsViewModel /
 * MaterialsViewModel — which only flips on *after* a live Firestore
 * listener has already failed. There was nothing that told the person
 * *why* (no internet vs. a real server problem) or *when* things last
 * synced successfully. This file is the small, self-contained layer other
 * screens can build on instead of each reinventing connectivity checks:
 *
 * - [SyncConnectivityObserver] — a live "متصل / غير متصل" signal, independent
 *   of Firestore ever actually trying to talk to the network.
 * - [SyncStatusStore] — remembers the last time *any* repository listener
 *   (debts or materials) received real data, on this device, across app
 *   restarts.
 *
 * Both are read by the new "المزامنة" section in Settings; [SyncStatusStore]
 * is also written to from DebtsViewModel/MaterialsViewModel's existing
 * listener `onEach` (see the BUG FIXED note there for why it already reset
 * `hasSyncError` on every real update — recording the timestamp piggybacks
 * on that exact same, already-correct signal instead of adding a new one).
 */
object SyncConnectivityObserver {

    /**
     * Emits true/false as the device's validated internet connectivity
     * changes (has actual internet access, not just a network interface —
     * e.g. a WiFi hotspot with no upstream internet correctly reports
     * false here). Starts with the current state instead of waiting for
     * the first change, so a UI collecting this never shows a stale
     * "جاري التحقق" before the first real callback fires.
     */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (connectivityManager == null) {
            trySend(true) // fail open — never block the UI on a missing system service
            awaitClose { }
            return@callbackFlow
        }

        fun currentlyOnline(): Boolean {
            val active = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        trySend(currentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentlyOnline()) }
            override fun onLost(network: Network) { trySend(currentlyOnline()) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentlyOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { connectivityManager.registerNetworkCallback(request, callback) }

        awaitClose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}

object SyncStatusStore {
    private const val PREFS = "shop_manager_sync"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"

    /** Called from a listener's `onEach` the moment it receives a real
     * (non-error) update — i.e. proof this device actually has current
     * data, not just that it tried to fetch some. */
    // PERF: recordSuccess يُستدعى مع كل لقطة من كل مستمع (قد تصل عدة لقطات في
    // الثانية عند فتح التطبيق)، وكل استدعاء كان يكتب SharedPreferences على القرص.
    // الشاشة تعرض "آخر مزامنة" بدقة الدقيقة فقط، فكتابة واحدة كل 30 ثانية تكفي.
    private const val MIN_WRITE_INTERVAL_MS = 30_000L

    @Volatile
    private var lastWriteElapsed = -MIN_WRITE_INTERVAL_MS

    fun recordSuccess(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWriteElapsed < MIN_WRITE_INTERVAL_MS) return
        lastWriteElapsed = now
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Epoch millis of the last confirmed successful sync on this device,
     * or null if this device has never synced yet (fresh install, first
     * launch still offline). */
    fun lastSyncedAt(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNCED_AT, -1L)
        return value.takeIf { it > 0 }
    }
}

/**
 * "مزامنة الآن" (Settings → المزامنة): Firestore's own listeners already
 * reconnect on their own once connectivity returns — there is normally
 * nothing to manually trigger. But a listener that's stuck in a long
 * backoff after several failures, or a network that flapped in a way the
 * OS didn't clearly signal, can leave the app looking "stuck offline" for
 * longer than necessary with no way for the person to say "try now". A
 * full app restart already fixes that (a fresh listener always attempts
 * immediately) — this gives the same fresh-attempt effect without asking
 * anyone to close and reopen the app.
 */
object SyncRetry {
    /** Drops and immediately re-opens the shared Firestore connection, so
     * every active listener (debts + materials, on this or any other
     * screen) makes a fresh connection attempt right away instead of
     * waiting out its current backoff delay. Safe to call at any time —
     * a no-op from the listeners' point of view if they were already
     * connected, since they just resubscribe against the same cache. */
    suspend fun forceReconnect() {
        val db = com.shopmanager.app.data.FirebaseModule.debtsDb
        runCatching { db.disableNetwork().await() }
        runCatching { db.enableNetwork().await() }
    }
}
