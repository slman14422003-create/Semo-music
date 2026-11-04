package com.shopmanager.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A tiny in-memory mirror of the currency symbol stored in
 * [com.shopmanager.app.data.settings.SettingsRepository]. Every place that
 * used to hardcode "ل.س" now reads [currencySymbol] instead, so changing the
 * currency once in الإعدادات (Settings) - e.g. to "$" or "SAR" - updates the
 * dashboard, debts, materials, share text, and notifications immediately,
 * with no need to pass SettingsRepository down through every screen/ViewModel.
 *
 * [currencySymbol] is loaded from SettingsRepository once at app startup
 * (MainActivity) and written back to SettingsRepository whenever it changes
 * (SettingsScreen), so it stays a single source of truth backed by disk.
 */
object AppSettingsState {
    var currencySymbol by mutableStateOf("ل.س")
        private set

    fun setCurrency(symbol: String) {
        currencySymbol = symbol.ifBlank { "ل.س" }
    }
}
