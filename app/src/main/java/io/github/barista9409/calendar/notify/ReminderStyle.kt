package io.github.barista9409.calendar.notify

/**
 * 予定の通知の種類。Android では通知の音・バイブは「通知チャンネル」ごとに決まり、作った後はアプリから変えられないため、
 * 種類ごとに別のチャンネルを使う。
 */
enum class ReminderStyle(
    val channelId: String,
    val label: String,
    val description: String,
    val sound: Boolean,
    val alarmSound: Boolean,
    val vibrate: Boolean,
    val popUp: Boolean,
) {
    PHONE("reminder2_phone", "スマホの設定に合わせる", "通常は音とバイブ、マナーモードではバイブだけ、サイレント（消音）では鳴りません",
        sound = true, alarmSound = false, vibrate = true, popUp = true),
    VIBRATE("reminder2_vibrate", "バイブのみ", "音は鳴らさず、サイレントのときもバイブで知らせます",
        sound = false, alarmSound = false, vibrate = true, popUp = true),
    ALARM("reminder2_alarm", "アラーム", "マナーモードやサイレントでも、アラームの音量で音とバイブで知らせます",
        sound = true, alarmSound = true, vibrate = true, popUp = true),
    SILENT("reminder2_silent", "表示のみ", "音もバイブもなく、通知欄に表示するだけです",
        sound = false, alarmSound = false, vibrate = false, popUp = false);

    companion object {
        fun of(index: Int): ReminderStyle = entries.getOrElse(index) { PHONE }
        /** 以前のチャンネル（消して作り直す） */
        val OLD_CHANNELS = listOf("reminders", "reminder_phone", "reminder_vibrate", "reminder_alarm", "reminder_silent")

        /** バイブの長さごとの振動の並び（待ち, 振動, 待ち, 振動, …）[ミリ秒] */
        fun pattern(length: Int): LongArray = when (length) {
            0 -> longArrayOf(0, 500)
            2 -> longArrayOf(0, 1000, 400, 1000, 400, 1000, 400, 1000, 400, 1000)
            else -> longArrayOf(0, 800, 300, 800, 300, 800)
        }

        /** 止めるまで繰り返すときの並び（最後に 1 秒休む） */
        fun repeatingPattern(length: Int): LongArray = pattern(length) + longArrayOf(1000)

        /** 振動するか。「スマホの設定に合わせる」はサイレントでは鳴らさない。バイブのみ・アラームはいつも鳴らす */
        fun shouldVibrate(style: ReminderStyle, ringerSilent: Boolean): Boolean =
            style.vibrate && !(style == PHONE && ringerSilent)

        const val ACTION_STOP = "止める"
        const val ACTION_SNOOZE = "10分後に再通知"
        const val ACTION_MAP = "地図"

        /**
         * 通知に付けるボタン（左から）。バイブか音が鳴る通知には、いつも先頭に［止める］を付ける
         * （折りたたまれた通知でも最初のボタンは見えやすい）。Android の上限は 3 つ。
         */
        fun actions(style: ReminderStyle, repeat: Boolean, hasLocation: Boolean): List<String> = buildList {
            if (style.vibrate || (repeat && style.sound)) add(ACTION_STOP)
            add(ACTION_SNOOZE)
            if (hasLocation) add(ACTION_MAP)
        }

        /** 止めるまで鳴らすときの最長時間 */
        const val MAX_REPEAT_MS = 5 * 60_000L
    }
}
