package com.shopmanager.app.data.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.shopmanager.app.R
import com.shopmanager.app.data.settings.SettingsRepository

/** أنواع الإشعارات التي يمكن اختيار صوت مستقل لكل منها من الإعدادات. */
enum class NotifKind(
    val idPrefix: String,
    val prefKey: String,
    val label: String,
    val defaultSound: NotifSound
) {
    SHOPPING("low_stock_channel_s_", "shopping", "قائمة النواقص والمشتريات", NotifSound.NOTE),
    DEBTS("debts_channel_s_", "debts", "تنبيهات الديون", NotifSound.TRITONE),
    NOTES("notes_channel_s_", "notes", "تذكيرات الملاحظات الهامة", NotifSound.CHIME)
}

/** نغمات بأسلوب الآيفون (مولَّدة أصلياً داخل التطبيق) + نغمات الجهاز + صامت. */
enum class NotifSound(val key: String, val label: String, val hint: String, val rawRes: Int?) {
    NOTE("note", "نوتة", "نقرة ماريمبا ناعمة", R.raw.tone_note),
    TRITONE("tritone", "ثلاثي النغمات", "ثلاث نوتات صاعدة سريعة", R.raw.tone_tritone),
    CHIME("chime", "رنين", "نغمتان زجاجيتان", R.raw.tone_chime),
    GLASS("glass", "زجاج", "رنة زجاج صافية", R.raw.tone_glass),
    BELL("bell", "جرس", "جرس هادئ طويل الصدى", R.raw.tone_bell),
    COMPLETE("complete", "اكتمال", "أربع نوتات صاعدة", R.raw.tone_complete),
    AURORA("aurora", "أورورا", "أربيجيو حالم", R.raw.tone_aurora),
    DEVICE("device", "من نغمات الجهاز", "اختر أي نغمة موجودة على هاتفك", null),
    SYSTEM("system", "صوت النظام الافتراضي", "نغمة الإشعارات الافتراضية للجهاز", null),
    SILENT("silent", "بدون صوت", "يظهر الإشعار بصمت (مع الاهتزاز إن وُجد)", null);

    companion object {
        fun fromKey(key: String?): NotifSound? = values().firstOrNull { it.key == key }
    }
}

object NotificationSounds {

    val AUDIO_ATTRS: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * صوت قناة الإشعار لا يتغيّر بعد إنشائها (قيد من أندرويد)، لذلك يُضمَّن مفتاح الصوت
     * في معرّف القناة نفسه: تغيير الصوت من الإعدادات = قناة جديدة بالصوت الجديد.
     */
    fun channelId(context: Context, kind: NotifKind): String {
        val repo = SettingsRepository(context.applicationContext)
        val sound = repo.soundFor(kind)
        val suffix = if (sound == NotifSound.DEVICE)
            "_" + Integer.toHexString((repo.customSoundUri(kind) ?: "").hashCode()) else ""
        return kind.idPrefix + sound.key + suffix
    }

    fun soundUri(context: Context, sound: NotifSound, customUri: String? = null): Uri? = when {
        sound == NotifSound.DEVICE ->
            customUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        sound.rawRes != null -> Uri.parse("android.resource://${context.packageName}/${sound.rawRes}")
        sound == NotifSound.SYSTEM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> null
    }

    private var player: MediaPlayer? = null

    /** تشغيل معاينة الصوت داخل الإعدادات. */
    fun preview(context: Context, sound: NotifSound, customUri: String? = null) {
        stopPreview()
        try {
            when {
                sound.rawRes != null -> {
                    player = MediaPlayer().apply {
                        setAudioAttributes(AUDIO_ATTRS)
                        context.resources.openRawResourceFd(sound.rawRes).use {
                            setDataSource(it.fileDescriptor, it.startOffset, it.length)
                        }
                        setOnCompletionListener { stopPreview() }
                        prepare()
                        start()
                    }
                }
                sound == NotifSound.SYSTEM || sound == NotifSound.DEVICE -> {
                    val r = RingtoneManager.getRingtone(
                        context.applicationContext,
                        soundUri(context, sound, customUri)
                    )
                    r?.setAudioAttributes(AUDIO_ATTRS)
                    r?.play()
                }
                else -> Unit
            }
        } catch (_: Exception) {
            stopPreview()
        }
    }

    fun stopPreview() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
