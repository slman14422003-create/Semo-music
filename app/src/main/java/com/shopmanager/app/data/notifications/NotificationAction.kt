package com.shopmanager.app.data.notifications

import android.content.Intent

/**
 * What should happen inside the app when a person taps one of our
 * notifications (see [NotificationHelper]). Each notification's
 * `PendingIntent` carries one of these, packed into plain Intent extras
 * (parcelable custom classes are a common source of "nothing happens on
 * tap" bugs on some OEM skins, so this deliberately sticks to primitive
 * String/StringArray extras instead).
 *
 * [from] is also used on a *cold* launch (app fully closed, Activity
 * created fresh from the notification's Intent) and on a *warm* one
 * (app already running - see MainActivity.onNewIntent, which needs
 * `android:launchMode="singleTop"` in the manifest so tapping again
 * reuses the same Activity instead of the tap silently doing nothing).
 */
sealed class NotificationAction {
    abstract fun applyExtras(intent: Intent)

    data class DebtPaid(val personName: String, val amount: String, val currency: String) : NotificationAction() {
        override fun applyExtras(intent: Intent) {
            intent.putExtra(EXTRA_ACTION_TYPE, TYPE_DEBT_PAID)
            intent.putExtra(EXTRA_PERSON_NAME, personName)
            intent.putExtra(EXTRA_AMOUNT, amount)
            intent.putExtra(EXTRA_CURRENCY, currency)
        }
    }

    data class NewDebt(val personName: String, val amount: String, val currency: String) : NotificationAction() {
        override fun applyExtras(intent: Intent) {
            intent.putExtra(EXTRA_ACTION_TYPE, TYPE_NEW_DEBT)
            intent.putExtra(EXTRA_PERSON_NAME, personName)
            intent.putExtra(EXTRA_AMOUNT, amount)
            intent.putExtra(EXTRA_CURRENCY, currency)
        }
    }

    data class ShoppingList(val materialNames: List<String>) : NotificationAction() {
        override fun applyExtras(intent: Intent) {
            intent.putExtra(EXTRA_ACTION_TYPE, TYPE_SHOPPING_LIST)
            intent.putStringArrayListExtra(EXTRA_MATERIALS, ArrayList(materialNames))
        }
    }

    data class NoteReminder(val noteId: String, val title: String) : NotificationAction() {
        override fun applyExtras(intent: Intent) {
            intent.putExtra(EXTRA_ACTION_TYPE, TYPE_NOTE_REMINDER)
            intent.putExtra(EXTRA_NOTE_ID, noteId)
            intent.putExtra(EXTRA_NOTE_TITLE, title)
        }
    }

    companion object {
        private const val EXTRA_ACTION_TYPE = "notif_action_type"
        private const val EXTRA_PERSON_NAME = "notif_person_name"
        private const val EXTRA_AMOUNT = "notif_amount"
        private const val EXTRA_CURRENCY = "notif_currency"
        private const val EXTRA_MATERIALS = "notif_materials"
        private const val EXTRA_NOTE_ID = "notif_note_id"
        private const val EXTRA_NOTE_TITLE = "notif_note_title"

        private const val TYPE_DEBT_PAID = "debt_paid"
        private const val TYPE_NEW_DEBT = "new_debt"
        private const val TYPE_SHOPPING_LIST = "shopping_list"
        private const val TYPE_NOTE_REMINDER = "note_reminder"

        /** Reads back whichever [NotificationAction] (if any) the Intent that
         * launched/resumed the Activity was carrying. Returns null for an
         * ordinary launch (tapping the app icon, not a notification). */
        fun from(intent: Intent?): NotificationAction? {
            val type = intent?.getStringExtra(EXTRA_ACTION_TYPE) ?: return null
            return when (type) {
                TYPE_DEBT_PAID -> {
                    val name = intent.getStringExtra(EXTRA_PERSON_NAME) ?: return null
                    DebtPaid(name, intent.getStringExtra(EXTRA_AMOUNT) ?: "", intent.getStringExtra(EXTRA_CURRENCY) ?: "ل.س")
                }
                TYPE_NEW_DEBT -> {
                    val name = intent.getStringExtra(EXTRA_PERSON_NAME) ?: return null
                    NewDebt(name, intent.getStringExtra(EXTRA_AMOUNT) ?: "", intent.getStringExtra(EXTRA_CURRENCY) ?: "ل.س")
                }
                TYPE_SHOPPING_LIST -> ShoppingList(intent.getStringArrayListExtra(EXTRA_MATERIALS) ?: emptyList())
                TYPE_NOTE_REMINDER -> {
                    val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return null
                    NoteReminder(noteId, intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "")
                }
                else -> null
            }
        }
    }
}
