package com.shopmanager.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shopmanager.app.MainActivity
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.settings.SettingsRepository
import java.io.File
import com.shopmanager.app.ui.common.avatarColorFor

/**
 * Local (on-device) notifications - no server/FCM setup required. Two kinds:
 * - "shopping list": which materials are currently on the shortage list and
 *   need buying from the market. Every material added to the list is, by
 *   definition, something the shop is short on (re-shown, not re-created,
 *   whenever the set of names actually changes - not on every unrelated
 *   Firestore update).
 * - "new debt": a new customer/debt appeared (useful if a second device or
 *   employee adds one).
 */
object NotificationHelper {

    private const val CHANNEL_REALTIME = "realtime_sync_silent_channel"
    private const val CHANNEL_REALTIME_PREVIOUS = "realtime_sync_hidden_channel"
    private const val CHANNEL_REALTIME_LEGACY = "realtime_sync_channel"
    private const val CHANNEL_UPDATE_DOWNLOAD = "update_download_channel"
    const val NOTIF_ID_REALTIME = 1800
    const val NOTIF_ID_UPDATE_DOWNLOAD = 1801
    private const val NOTIF_ID_SHOPPING_LIST = 1001
    private const val NOTIF_ID_DEBT = 1002
    // BUG FIXED (إشعارات تمسح بعضها): every id below is `BASE + (hash & 0xFFF)`,
    // i.e. a window of 4096 ids - but the bases were only 1000 apart, so the
    // windows overlapped. A "debt paid" notification could land on the exact
    // same id as a "new debt" or a note reminder and silently REPLACE it
    // before it was ever seen. Bases are now 10000 apart, so each kind owns
    // its own non-overlapping window (and none touches the fixed ids above).
    const val NOTIF_ID_UPDATE_READY = 1802
    private const val NOTIF_ID_PAID_BASE = 20000
    private const val NOTIF_ID_NEW_DEBT_BASE = 30000
    private const val NOTIF_ID_NOTE_BASE = 40000
    private const val NOTIF_ID_NEW_NOTE_BASE = 50000
    private const val NOTIF_ID_NOTE_DONE_BASE = 60000
    private const val NOTIF_ID_MATERIAL_BASE = 70000
    private const val NOTIF_ID_SHOPPING_SUMMARY = 1901
    private const val GROUP_SHOPPING = "com.shopmanager.app.GROUP_SHOPPING"

    // "مجموعات الإشعارات المتقدمة": each channel gets its own notification
    // *group*, with a silent summary notification posted alongside the
    // individual ones. Without a group, several debt-paid notifications
    // fired back-to-back (e.g. paying off 3 debts in a row, or the
    // background worker catching up after being closed for a while) just
    // stack as separate unrelated entries; with a group, Android (API 24+)
    // automatically visually clusters them under the shop name instead, and
    // the summary line gives an at-a-glance count without opening the
    // shade — the same "grouped alerts" behavior iOS notification
    // grouping and modern Android apps both use.
    private const val GROUP_DEBTS = "com.shopmanager.app.GROUP_DEBTS"
    private const val NOTIF_ID_DEBTS_SUMMARY = 1900
    private const val NOTIF_ID_OVERDUE = 1903
    private const val NOTIF_ID_DUE_BASE = 80000

    // نفس لون Claude الأساسي في ثيم التطبيق (ClaudeOrangeLight في ui/theme/Color.kt) —
    // يُطبَّق عبر setColor() ليتطابق تلوين الأيقونة الصغيرة وبانر الإشعار مع بقية
    // التطبيق بدل الرمادي الافتراضي للنظام (الأيقونة نفسها: ic_stat_notify.xml).
    private val BRAND_COLOR = android.graphics.Color.parseColor("#C96442")

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // القنوات تُنشأ بمعرّف يحمل مفتاح الصوت المختار (انظر NotificationSounds.channelId)،
            // وتُحذف القنوات القديمة (v1/v2) وقنوات الأصوات غير المختارة حالياً.
            val settingsRepo = SettingsRepository(context.applicationContext)
            val activeIds = NotifKind.values().map { NotificationSounds.channelId(context, it) }.toSet()
            val legacy = setOf(
                "low_stock_channel", "debts_channel", "notes_channel",
                "low_stock_channel_v2", "debts_channel_v2", "notes_channel_v2"
            )
            manager.notificationChannels.orEmpty().forEach { ch ->
                val stale = ch.id in legacy ||
                    (NotifKind.values().any { ch.id.startsWith(it.idPrefix) } && ch.id !in activeIds)
                if (stale) try { manager.deleteNotificationChannel(ch.id) } catch (_: Exception) {}
            }

            val shoppingChannel = NotificationChannel(
                NotificationSounds.channelId(context, NotifKind.SHOPPING), NotifKind.SHOPPING.label, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه عند تغيّر قائمة المواد الناقصة"
                enableLights(true)
                setSound(NotificationSounds.soundUri(context, settingsRepo.soundFor(NotifKind.SHOPPING), settingsRepo.customSoundUri(NotifKind.SHOPPING)), NotificationSounds.AUDIO_ATTRS)
            }

            // Debt alerts are money-related and time-sensitive (a new debt,
            // a payment coming in) — bumped to HIGH importance so they post
            // as a heads-up/banner notification with sound instead of
            // silently landing in the shade, plus a short distinct
            // vibration pattern so it's recognizable by feel alone.
            val debtsChannel = NotificationChannel(
                NotificationSounds.channelId(context, NotifKind.DEBTS), NotifKind.DEBTS.label, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه فوري عند إضافة دين جديد أو تسديده"
                enableLights(true)
                setSound(NotificationSounds.soundUri(context, settingsRepo.soundFor(NotifKind.DEBTS), settingsRepo.customSoundUri(NotifKind.DEBTS)), NotificationSounds.AUDIO_ATTRS)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 90, 180)
            }

            manager.createNotificationChannel(shoppingChannel)
            manager.createNotificationChannel(debtsChannel)

            // "الملاحظات الهامة" reminders - their own channel (not the
            // generic shopping-list one) since a person may want reminders
            // to make sound/vibrate distinctly from a shortage-list update.
            val notesChannel = NotificationChannel(
                NotificationSounds.channelId(context, NotifKind.NOTES), NotifKind.NOTES.label, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه عند حلول موعد تذكير لملاحظة هامة"
                enableLights(true)
                setSound(NotificationSounds.soundUri(context, settingsRepo.soundFor(NotifKind.NOTES), settingsRepo.customSoundUri(NotifKind.NOTES)), NotificationSounds.AUDIO_ATTRS)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 90, 180)
            }
            manager.createNotificationChannel(notesChannel)

            // قناة إشعار الخدمة الأمامية الدائم (المزامنة الفورية): أدنى أهمية —
            // بلا صوت ولا اهتزاز ولا ظهور على شاشة القفل، فقط سطر صغير مطوي في
            // الشريط. لازم يوجد إشعار للخدمة الأمامية، وهذا أقل شكل ممكن له.
            // Foreground-service notification is created blocked (IMPORTANCE_NONE):
            // the service keeps syncing but nothing shows in the shade or status bar.
            // The old visible channel is removed so it can't keep being used.
            runCatching { manager.deleteNotificationChannel(CHANNEL_REALTIME_LEGACY) }
            if (!RealtimeSyncService.isRunning) {
                runCatching { manager.deleteNotificationChannel(CHANNEL_REALTIME_PREVIOUS) }
            }
            val realtimeChannel = NotificationChannel(
                CHANNEL_REALTIME, "المزامنة الفورية", NotificationManager.IMPORTANCE_NONE
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(realtimeChannel)

            // قناة إشعار خدمة تحميل التحديث ([UpdateDownloadService]): أهمية
            // منخفضة (يظهر بالشريط لكن بلا صوت/اهتزاز/ظهور فوقي) — شريط تقدم
            // بسيط يثبت أن التحميل مستمر بالخلفية، وليس تنبيهاً يستدعي الانتباه.
            val updateChannel = NotificationChannel(
                CHANNEL_UPDATE_DOWNLOAD, "تحميل تحديثات التطبيق", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "شريط تقدم تحميل تحديث التطبيق الجاري في الخلفية"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            manager.createNotificationChannel(updateChannel)
        }
    }

    /**
     * PRIVACY (ترابط مع قفل التطبيق): debt names/amounts and note text used to
     * show in full on the lock screen even when the app itself is PIN-locked.
     * When a PIN is set, the lock screen now shows only a generic title and
     * the full text appears once the phone is unlocked.
     */
    private fun lockScreenPrivacy(context: Context, channelId: String, publicTitle: String) =
        NotificationCompat.Extender { builder ->
            val hasPin = try {
                SettingsRepository(context.applicationContext).hasPin
            } catch (_: Exception) {
                false
            }
            if (hasPin) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                builder.setPublicVersion(
                    NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
                        .setColor(BRAND_COLOR)
                        .setContentTitle(publicTitle)
                        .build()
                )
            }
            builder
        }

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * BUG FIXED ("اصلح ميزات الاشعارات" — a single channel switched off
     * from system Settings looked identical to "everything's fine"):
     * Settings' own "systemNotificationsAllowed" check only ever called
     * `NotificationManagerCompat.areNotificationsEnabled()`, which is the
     * *app-level* toggle alone. Android also lets a person disable one
     * specific channel (e.g. just "تنبيهات الديون" from the notification
     * long-press menu) while leaving the app-level toggle and every other
     * channel untouched — every `notify()` call under that one channel then
     * silently no-ops forever, with nothing in the UI ever hinting at it,
     * since the app-level check alone still reported "allowed". Exposes
     * which channels (by their human label) are actually blocked so
     * Settings can warn about exactly that, instead of only ever detecting
     * the all-or-nothing case.
     */
    fun blockedChannelLabels(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return emptyList()
        return NotifKind.values().map { NotificationSounds.channelId(context, it) to it.label }.filter { (id, _) -> manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE }
            .map { it.second }
    }

    /**
     * BUG FIXED (tapping a notification did nothing): none of the three
     * notifications below ever set a `contentIntent`, so tapping them had
     * no effect at all - not an OEM (Xiaomi/Samsung) quirk, just a missing
     * PendingIntent. Every notification now opens [MainActivity] carrying
     * its own [NotificationAction] (via [NotificationAction.applyExtras]),
     * which the Activity reads in `onCreate`/`onNewIntent` and turns into
     * the matching confirmation dialog ("تم تسديد الدين"، إلخ) or
     * navigation. `requestCode` must be unique per *distinct* notification
     * (not shared across all of them) or Android reuses/overwrites a
     * previous PendingIntent's extras instead of building a fresh one -
     * the same `id` each notification is posted under is reused here for
     * exactly that reason.
     */
    private fun buildContentIntent(context: Context, requestCode: Int, action: NotificationAction): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action.applyExtras(this)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * "إشعارات أكثر تطورًا وكفاءة": a broadcast-backed action button that
     * does its work (snooze a reminder, dismiss the shopping-list card)
     * directly from the notification shade via [NotificationActionReceiver]
     * — no need to open [MainActivity] first the way every `contentIntent`
     * tap above does. Distinct `requestCode`s per action/id (same rotating-
     * id convention as [buildContentIntent]) so several action buttons
     * across different notifications never collide and silently overwrite
     * one another's extras.
     */
    private fun buildActionIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * A small circular "avatar" bitmap — the customer's own brand color
     * (via [avatarColorFor], the exact same deterministic palette every
     * in-app debt/customer avatar already uses) with their first letter
     * centered on it — used as the notification's `setLargeIcon`. Purely
     * decorative/drawn on-device (no network image), so this never adds a
     * loading state or a failure path; the small brand-colored status
     * icon (`ic_stat_notify`) stays as-is alongside it, exactly like a
     * messaging app pairs a contact photo with its own small app glyph.
     */
    private fun buildAvatarBitmap(name: String, sizePx: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val composeColor = avatarColorFor(name)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(
                (composeColor.alpha * 255).toInt(),
                (composeColor.red * 255).toInt(),
                (composeColor.green * 255).toInt(),
                (composeColor.blue * 255).toInt()
            )
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, backgroundPaint)

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.46f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, radius, textY, textPaint)
        return bitmap
    }

    /**
     * Fires a note's reminder - see [com.shopmanager.app.data.notifications.NoteReminderWorker].
     * Id is derived from the note id (same rotating-id pattern as the debt
     * notifications above) so several reminders firing close together each
     * stay visible instead of overwriting one another.
     */
    fun showNoteReminderNotification(context: Context, noteId: String, title: String, content: String) {
        // BUG FIXED (Lint error, MissingPermission, NotificationHelper.kt:272):
        // this was the one notify() call in the whole file missing the
        // `hasPermission(context)` guard every sibling function here already
        // has (showNoteDoneNotification right below, showNewNoteNotification,
        // showShoppingListNotification, ...) — a plain oversight, not a
        // different case that needed different handling. Without it, calling
        // notify() when the person denied (or never granted) the Android 13+
        // POST_NOTIFICATIONS runtime permission can throw a SecurityException
        // and crash the app, instead of just silently skipping the reminder
        // the way every other notification type in this file already does.
        if (!hasPermission(context)) return
        val id = NOTIF_ID_NOTE_BASE + (noteId.hashCode() and 0xFFF)
        val body = content.ifBlank { "تذكير بملاحظة هامة" }
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_NOTE
            putExtra(NotificationActionReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(NotificationActionReceiver.EXTRA_NOTE_TITLE, title)
            putExtra(NotificationActionReceiver.EXTRA_NOTE_CONTENT, content)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, id)
        }
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.NOTES))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("📌 $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.NoteReminder(noteId, title)))
            .extend(lockScreenPrivacy(context, NotificationSounds.channelId(context, NotifKind.NOTES), "ملاحظات هامة"))
            // "كفاءة أعلى": تأجيل التذكير ساعة كاملة دون فتح التطبيق —
            // يُنفَّذ مباشرة عبر NotificationActionReceiver.
            .addAction(0, "تأجيل ساعة", buildActionIntent(context, id, snoozeIntent))
            .build()

        // LINT FIX (MissingPermission, 7 identical errors across this
        // file): every one of these calls already sits behind a
        // `hasPermission(context)` early-return guard above it, but
        // Android Lint's permission checker only recognizes the
        // POST_NOTIFICATIONS check when it's written inline in the same
        // method as the call — it doesn't trace through a private
        // helper function to see that the check already happened,
        // so it flags the call as "unchecked" regardless. Wrapping the
        // call itself in try/catch(SecurityException) is the pattern
        // Lint's checker DOES special-case as a satisfied permission
        // check, and it's genuinely correct defense-in-depth here too:
        // the permission can still be revoked in the moment between
        // hasPermission()'s check and this call (e.g. the person
        // backgrounds the app and revokes it from system Settings),
        // so catching it means a lost-permission race can never crash
        // the app — it just silently skips that one notification.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
    }

    fun cancelNoteReminderNotification(context: Context, noteId: String) {
        val id = NOTIF_ID_NOTE_BASE + (noteId.hashCode() and 0xFFF)
        NotificationManagerCompat.from(context).cancel(id)
    }

    /**
     * "إنجاز الملاحظة لازم يكون له إشعار": marking a note done previously
     * produced no notification at all — the note's *lifecycle* had an
     * "added" notification ([showNewNoteNotification]) but nothing for the
     * matching "finished" event, unlike debts, which post one for both
     * "new" ([showNewDebtNotification]) and "paid" ([showDebtPaidNotification]).
     * Fired the same way [showDebtPaidNotification] is: directly, on the
     * device that ticked it off, right after the write succeeds (see
     * NotesViewModel.setDone) — a local confirmation, not a cross-device
     * diff, exactly like every other "I just did X" notification in this
     * app. Uses its own id range ([NOTIF_ID_NOTE_DONE_BASE]) so completing
     * a note can never collide with/overwrite that same note's still-open
     * "new note" or reminder notification if either happens to still be
     * showing.
     */
    fun showNoteDoneNotification(context: Context, title: String, noteId: String) {
        if (!hasPermission(context)) return
        val id = NOTIF_ID_NOTE_DONE_BASE + (noteId.hashCode() and 0xFFF)
        val displayTitle = title.ifBlank { "ملاحظة" }
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.NOTES))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("✅ تم إنجاز ملاحظة")
            .setContentText(displayTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayTitle))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.NoteReminder(noteId, displayTitle)))
            .extend(lockScreenPrivacy(context, NotificationSounds.channelId(context, NotifKind.NOTES), "ملاحظات هامة"))
            .build()

        // LINT FIX (MissingPermission) — see the first occurrence of this
        // pattern above (showNoteReminderNotification) for why.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
    }

    /**
     * BUG FIXED ("ما ترسل إشعار لبقية الأجهزة إني ضفت ملاحظة"): unlike
     * debts ([showNewDebtNotification]) and the shortage list
     * ([showShoppingListNotification]), important notes never had a
     * "someone added one" notification at all — only the *reminder* this
     * same note's own creator scheduled locally
     * ([showNoteReminderNotification], via [NoteReminderWorker]), which
     * fires on the device that made it, not the others sharing this shop.
     * Fired the same way [showNewDebtNotification] is: from
     * [com.shopmanager.app.ui.notes.NotesViewModel]'s live-listener diff
     * (while the app's open on another device) and from
     * [BackgroundSyncWorker]'s periodic check (while it's fully closed) —
     * both skip the device that actually created the note. Reuses
     * [NotificationAction.NoteReminder] for the tap target since it already
     * does exactly what's needed here too: open the app straight to that
     * note. A rotating id derived from the note id keeps several new notes
     * (e.g. after reconnecting) each visible instead of overwriting one another.
     */
    fun showNewNoteNotification(context: Context, title: String, content: String, noteId: String) {
        if (!hasPermission(context)) return
        val id = NOTIF_ID_NEW_NOTE_BASE + (noteId.hashCode() and 0xFFF)
        val displayTitle = title.ifBlank { "ملاحظة جديدة" }
        val body = content.ifBlank { "أُضيفت ملاحظة جديدة" }
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.NOTES))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("📝 ملاحظة جديدة: $displayTitle")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.NoteReminder(noteId, displayTitle)))
            .extend(lockScreenPrivacy(context, NotificationSounds.channelId(context, NotifKind.NOTES), "ملاحظات هامة"))
            .build()

        // LINT FIX (MissingPermission) — see the first occurrence of this
        // pattern above (showNoteReminderNotification) for why.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
    }

    fun showShoppingListNotification(context: Context, shortageNames: List<String>) {
        if (!hasPermission(context) || shortageNames.isEmpty()) return
        val body = if (shortageNames.size <= 4) shortageNames.joinToString("، ")
        else shortageNames.take(4).joinToString("، ") + " و${shortageNames.size - 4} أخرى"
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_SHOPPING
        }

        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.SHOPPING))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("🛒 قائمة مشتريات: ${shortageNames.size} مادة ناقصة")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            // عدد المواد الناقصة كـ"شارة" رقمية على أيقونة الإشعار
            // (المكان الذي تدعمه واجهة المستخدم) — لمحة سريعة دون فتح الشريط.
            .setNumber(shortageNames.size)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, NOTIF_ID_SHOPPING_LIST, NotificationAction.ShoppingList(shortageNames)))
            .addAction(0, "تم الشراء", buildActionIntent(context, NOTIF_ID_SHOPPING_LIST, dismissIntent))
            .build()

        // LINT FIX (MissingPermission) — see the first occurrence of this
        // pattern above (showNoteReminderNotification) for why.
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_SHOPPING_LIST, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
    }

    private fun materialNotifId(materialId: String): Int =
        NOTIF_ID_MATERIAL_BASE + (materialId.hashCode() and 0xFFFF)

    fun showMaterialNotification(context: Context, material: MaterialRecord, isNew: Boolean) {
        if (!hasPermission(context) || material.name.isBlank()) return
        val id = materialNotifId(material.id)
        val label = Material(name = material.name, quantity = material.quantity, unit = material.unit).quantityLabel()
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_SHOPPING
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, id)
        }
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.SHOPPING))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle(if (isNew) "🛒 مادة ناقصة جديدة" else "🛒 تعديل مادة ناقصة")
            .setContentText("${material.name} • $label")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${material.name} • $label"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setGroup(GROUP_SHOPPING)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.ShoppingList(listOf(material.name))))
            .addAction(0, "تم الشراء", buildActionIntent(context, id, dismissIntent))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            postShoppingGroupSummary(context)
        } catch (e: SecurityException) {
        }
    }

    fun cancelMaterialNotification(context: Context, materialId: String) {
        NotificationManagerCompat.from(context).cancel(materialNotifId(materialId))
    }

    private fun postShoppingGroupSummary(context: Context) {
        val summary = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.SHOPPING))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("المواد الناقصة")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("إدارة المحل"))
            .setGroup(GROUP_SHOPPING)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, NOTIF_ID_SHOPPING_SUMMARY,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_SHOPPING_SUMMARY, summary)
        } catch (e: SecurityException) {
        }
    }

    fun cancelShoppingListNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_SHOPPING_LIST)
    }

    /**
     * BUG FIXED (اشعارات دين جديد تختفي): this used to post every "new
     * debt" notification under the same fixed [NOTIF_ID_DEBT] id. Both
     * callers (DebtsViewModel's live-listener diff and
     * BackgroundSyncWorker's periodic diff) can detect *several* new debts
     * at once — e.g. reconnecting after being offline, or another device
     * adding two debts in a row — and loop over them calling this once per
     * debt. Since `NotificationManagerCompat.notify(id, ...)` replaces any
     * existing notification already posted under that same id, only the
     * *last* debt in the loop ever stayed visible; every earlier one in
     * the same batch was silently overwritten before the person ever saw
     * it. [showDebtPaidNotification] already avoided exactly this by
     * deriving its id from the debt id - this now does the same (falling
     * back to the shared [NOTIF_ID_DEBT] only when no id is available, so
     * existing behavior for a single new debt is unchanged).
     */
    fun showNewDebtNotification(context: Context, personName: String, amount: String, currencySymbol: String = "ل.س", debtId: String = "") {
        if (!hasPermission(context)) return
        val id = if (debtId.isEmpty()) NOTIF_ID_DEBT else NOTIF_ID_NEW_DEBT_BASE + (debtId.hashCode() and 0xFFF)
        val builder = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.DEBTS))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            // "صورة رمزية": نفس لون ولون الحرف الأول اللذين يستخدمهما تطبيق
            // العملاء داخليًا (avatarColorFor) — تُظهر هوية العميل مباشرة
            // من شريط الإشعارات بدل الاعتماد على الأيقونة العامة فقط.
            .setLargeIcon(buildAvatarBitmap(personName))
            .setContentTitle("💰 عميل جديد بالديون")
            .setContentText("$personName — $amount $currencySymbol")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$personName — $amount $currencySymbol"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_DEBTS)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.NewDebt(personName, amount, currencySymbol)))
            .extend(lockScreenPrivacy(context, NotificationSounds.channelId(context, NotifKind.DEBTS), "تنبيهات الديون"))

        // FEATURE ADDED ("تحسينات بالإشعارات"): زر "تسديد" مباشر من شريط
        // الإشعار — نفس فكرة "تم الشراء" على قائمة النواقص و"تأجيل ساعة"
        // على تذكيرات الملاحظات: إنجاز الإجراء بلمسة وحدة بدون فتح
        // التطبيق. يحتاج debtId حقيقي (متوفر بكل نداءات هالدالة الفعلية —
        // راجع DebtsViewModel/BackgroundSyncWorker) عشان نقدر نحدد ونسدد
        // نفس الدين تحديدًا من NotificationActionReceiver.
        if (debtId.isNotEmpty()) {
            val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_DEBT_PAID
                putExtra(NotificationActionReceiver.EXTRA_DEBT_ID, debtId)
                putExtra(NotificationActionReceiver.EXTRA_PERSON_NAME, personName)
                putExtra(NotificationActionReceiver.EXTRA_AMOUNT, amount)
                putExtra(NotificationActionReceiver.EXTRA_CURRENCY, currencySymbol)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, id)
            }
            // SECURITY: settling a debt is a money action - on a locked phone
            // it now asks the person to unlock first instead of running
            // straight from the lock-screen notification.
            builder.addAction(
                NotificationCompat.Action.Builder(0, "تسديد", buildActionIntent(context, id, payIntent))
                    .setAuthenticationRequired(true)
                    .build()
            )
        }

        // LINT FIX (MissingPermission) — see the first occurrence of this
        // pattern above (showNoteReminderNotification) for why.
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
        postDebtsGroupSummary(context)
    }

    fun showOverdueDebtsNotification(context: Context, names: List<String>, days: Int) {
        if (!hasPermission(context) || names.isEmpty()) return
        val shown = names.take(3).joinToString("، ")
        val more = if (names.size > 3) " و${names.size - 3} آخرين" else ""
        val text = "${names.size} عميل لديهم ديون متأخرة: $shown$more"
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, NOTIF_ID_OVERDUE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.DEBTS))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("⏰ ديون متأخرة")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_OVERDUE, notification)
        } catch (e: SecurityException) {
        }
    }

    fun showDueReminderNotification(context: Context, key: String, title: String, body: String) {
        if (!hasPermission(context)) return
        val id = NOTIF_ID_DUE_BASE + (key.hashCode() and 0xFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.DEBTS))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
        }
    }

    /**
     * Fired when a debt is marked paid (the checkmark button next to each
     * debt in Person Detail). Uses a rotating id derived from the debt id so
     * paying off several debts in a row shows several notifications instead
     * of each one silently replacing the last.
     */
    fun showDebtPaidNotification(context: Context, personName: String, amount: String, currencySymbol: String = "ل.س", debtId: String = "") {
        if (!hasPermission(context)) return
        val id = NOTIF_ID_PAID_BASE + (debtId.hashCode() and 0xFFF)
        val notification = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.DEBTS))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setLargeIcon(buildAvatarBitmap(personName))
            .setContentTitle("✅ تم سداد دين")
            .setContentText("$personName وفى $amount $currencySymbol")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$personName وفى $amount $currencySymbol"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_DEBTS)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.DebtPaid(personName, amount, currencySymbol)))
            .extend(lockScreenPrivacy(context, NotificationSounds.channelId(context, NotifKind.DEBTS), "تنبيهات الديون"))
            .build()

        // LINT FIX (MissingPermission) — see the first occurrence of this
        // pattern above (showNoteReminderNotification) for why.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
        postDebtsGroupSummary(context)
    }

    /**
     * The silent group-summary notification required (API 24+) for
     * multiple grouped debt notifications to actually cluster visually
     * instead of just sharing an invisible group tag. `setGroupSummary`
     * marks it as the "stack cover" rather than a notification in its own
     * right, and it deliberately carries no sound/vibration of its own
     * (the individual notification that triggered it already made noise) —
     * it exists purely so the shade shows "٣ إشعارات" collapsed instead of
     * three separate cards.
     */
    private fun postDebtsGroupSummary(context: Context) {
        if (!hasPermission(context)) return
        val summary = NotificationCompat.Builder(context, NotificationSounds.channelId(context, NotifKind.DEBTS))
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("تنبيهات الديون")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("إدارة المحل"))
            .setGroup(GROUP_DEBTS)
            .setGroupSummary(true)
            // BUG FIXED: the summary lives on the HIGH-importance debts channel and
            // is re-posted with every debt notification, so it used to beep/pop up
            // a second time for each one. Only the child notifications may alert.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, NOTIF_ID_DEBTS_SUMMARY,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // LINT FIX (MissingPermission) — see the first occurrence of this
        // pattern above (showNoteReminderNotification) for why.
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_DEBTS_SUMMARY, summary)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
    }

    /**
     * ترابط التحديثات بالإشعارات: التحميل يتم بالخلفية ([UpdateDownloadService])،
     * لكن لو خرج الشخص من التطبيق أثناءه لم يكن يصله أي شيء عند انتهائه ولا
     * يبدأ التثبيت (مربع التحديث كان قد اختفى). الآن يصل إشعار "تم تحميل
     * التحديث" ولمسه يفتح المثبّت مباشرة (أو شاشة "السماح من هذا المصدر" إن
     * لم تكن مفعّلة). يُلغى تلقائياً عند بدء التثبيت من داخل التطبيق
     * ([com.shopmanager.app.data.updates.ApkDownloader.install]).
     */
    fun showUpdateReadyNotification(context: Context, file: File, versionName: String = "") {
        if (!hasPermission(context)) return
        val tapIntent: Intent = try {
            if (com.shopmanager.app.data.updates.ApkDownloader.canInstallPackages(context)) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            } else {
                com.shopmanager.app.data.updates.ApkDownloader.unknownSourcesSettingsIntent(context)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        val pending = PendingIntent.getActivity(
            context, NOTIF_ID_UPDATE_READY, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (versionName.isNotBlank()) "اضغط لتثبيت الإصدار $versionName" else "اضغط لتثبيت التحديث"
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_DOWNLOAD)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("تم تحميل التحديث")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_UPDATE_READY, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call — skip.
        }
    }

    /**
     * الإشعار الدائم المطلوب للخدمة الأمامية [RealtimeSyncService]. صامت وبأدنى
     * أولوية. الضغط عليه يفتح التطبيق.
     */
    fun buildRealtimeServiceNotification(context: Context): android.app.Notification {
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, NOTIF_ID_REALTIME, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_REALTIME)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("المزامنة")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()
    }

    /**
     * الإشعار الدائم المطلوب لخدمة [com.shopmanager.app.data.updates.UpdateDownloadService]
     * أثناء تحميل تحديث التطبيق — شريط تقدم حقيقي (setProgress) بدل نص ثابت،
     * يتحدّث كل ما تغيّرت النسبة. الضغط عليه يفتح التطبيق.
     */
    fun buildUpdateDownloadNotification(
        context: Context,
        percent: Int,
        versionName: String = ""
    ): android.app.Notification {
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, NOTIF_ID_UPDATE_DOWNLOAD, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (versionName.isNotBlank()) "جارٍ تحميل التحديث $versionName" else "جارٍ تحميل التحديث"
        return NotificationCompat.Builder(context, CHANNEL_UPDATE_DOWNLOAD)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()
    }
}
