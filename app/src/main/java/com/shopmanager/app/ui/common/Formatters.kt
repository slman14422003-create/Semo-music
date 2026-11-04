package com.shopmanager.app.ui.common

import java.text.NumberFormat
import java.util.Locale

/**
 * PERF: every screen that shows a money amount used to create its own
 * `NumberFormat.getNumberInstance(Locale("ar"))` — most were `remember`ed
 * per-composable, but each item in a LazyColumn (a person row, a material
 * row) is its own composable, so a list of 100 customers meant 100 separate
 * NumberFormat instances being built (a real, non-trivial constructor —
 * it parses locale data) purely to format a number that any one of them
 * could have shared. On a low-end device that's avoidable allocation and
 * GC pressure during scrolling, exactly where jank is most visible.
 *
 * One shared instance, built once for the process. `NumberFormat` is not
 * thread-safe for concurrent use, but every call site here runs on the
 * main thread (Compose UI, or a ViewModel's `viewModelScope`, which
 * defaults to `Dispatchers.Main.immediate`) — never from a background
 * worker — so sharing it is safe.
 */
object Formatters {
    private val arabicNumberFormat = NumberFormat.getNumberInstance(Locale("ar"))

    fun number(value: Double): String = arabicNumberFormat.format(value)
}
