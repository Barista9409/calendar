package io.github.barista9409.calendar.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.barista9409.calendar.MainActivity
import io.github.barista9409.calendar.R
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.util.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 予定のリマインダー通知。OS のカレンダーが通知時刻に送る EVENT_REMINDER を受け取って通知を出す。
 * 設定で「このアプリで通知する」をオンにしたときだけ動く（公式アプリとの二重通知を防ぐため）。
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.container.prefs.appReminders) return
        when (intent.action) {
            ACTION_SNOOZED -> {
                show(context, intent.getLongExtra(EXTRA_EVENT_ID, -1), intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    intent.getLongExtra(EXTRA_BEGIN, 0), intent.getLongExtra(EXTRA_END, 0),
                    intent.getStringExtra(EXTRA_LOCATION), intent.getBooleanExtra(EXTRA_ALL_DAY, false))
                return
            }
        }
        val alarmTime = intent.getLongExtra(CalendarContract.CalendarAlerts.ALARM_TIME, -1L)
        val pending = goAsync()
        Thread {
            try { fire(context, alarmTime) } finally { pending.finish() }
        }.start()
    }

    private fun fire(context: Context, alarmTime: Long) {
        val cr = context.contentResolver
        val sel = StringBuilder("${CalendarAlerts.STATE}=${CalendarAlerts.STATE_SCHEDULED}")
        val args = mutableListOf<String>()
        if (alarmTime > 0) { sel.append(" AND ${CalendarAlerts.ALARM_TIME}=?"); args += alarmTime.toString() }
        else { sel.append(" AND ${CalendarAlerts.ALARM_TIME}<=?"); args += System.currentTimeMillis().toString() }
        val ids = mutableListOf<Long>()
        runCatching {
            cr.query(CalendarAlerts.CONTENT_URI, arrayOf(CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.TITLE,
                CalendarAlerts.BEGIN, CalendarAlerts.END, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY),
                sel.toString(), args.toTypedArray(), null)?.use { c ->
                while (c.moveToNext()) {
                    ids += c.getLong(0)
                    show(context, c.getLong(1), c.getString(2).orEmpty(), c.getLong(3), c.getLong(4), c.getString(5), c.getInt(6) == 1)
                }
            }
            ids.forEach { id ->
                cr.update(Uri.withAppendedPath(CalendarAlerts.CONTENT_URI, id.toString()),
                    ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED) }, null, null)
            }
        }
    }

    companion object {
        /** 通知の種類に合ったチャンネルを用意する（以前のチャンネルは消す） */
        fun ensureChannel(context: Context, style: ReminderStyle) {
            val nm = context.getSystemService(NotificationManager::class.java)
            ReminderStyle.OLD_CHANNELS.forEach { runCatching { nm.deleteNotificationChannel(it) } }
            if (nm.getNotificationChannel(style.channelId) != null) return
            val ch = NotificationChannel(style.channelId, "予定の通知（${style.label}）",
                if (style.popUp) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW)
            ch.description = style.description
            if (style.sound) {
                if (style.alarmSound) {
                    ch.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    ch.setBypassDnd(false)
                }
            } else ch.setSound(null, null)
            // バイブはアプリが直接鳴らす（Buzz）。通知の仕組みでは鳴らさない（二重にならないように）
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }

        /** 設定画面の「テスト通知を送る」 */
        fun showTest(context: Context) =
            show(context, -1L, "テスト通知", System.currentTimeMillis() + 10 * 60_000L, System.currentTimeMillis() + 70 * 60_000L,
                null, allDay = false)

        const val ACTION_SNOOZED = "io.github.barista9409.calendar.SNOOZED"
        private const val EXTRA_EVENT_ID = "eid"; private const val EXTRA_TITLE = "title"; private const val EXTRA_BEGIN = "begin"
        private const val EXTRA_END = "end"; private const val EXTRA_LOCATION = "loc"; private const val EXTRA_ALL_DAY = "allday"

        fun show(context: Context, eventId: Long, title: String, begin: Long, end: Long, location: String?, allDay: Boolean) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val style = ReminderStyle.of(context.container.prefs.reminderStyle)
            ensureChannel(context, style)
            val zone = ZoneId.systemDefault()
            val whenText = if (allDay) "終日 ・ " + DateUtils.date(Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate())
            else {
                val s = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime(); val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime()
                "${DateUtils.date(s.toLocalDate())} ${DateUtils.time(s)}〜${DateUtils.time(e)}"
            }
            val req = (eventId * 31 + begin / 60_000).toInt()
            val open = PendingIntent.getActivity(context, req,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_EVENT_ID, eventId).putExtra(MainActivity.EXTRA_EVENT_BEGIN, begin)
                    .setData(Uri.parse("calendar://event/$eventId/$begin")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val snooze = PendingIntent.getBroadcast(context, req, Intent(context, SnoozeReceiver::class.java)
                .putExtra(EXTRA_EVENT_ID, eventId).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BEGIN, begin).putExtra(EXTRA_END, end)
                .putExtra(EXTRA_LOCATION, location).putExtra(EXTRA_ALL_DAY, allDay).putExtra("nid", req),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = NotificationCompat.Builder(context, style.channelId)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title.ifBlank { "(タイトルなし)" })
                .setContentText(whenText + (location?.takeIf { it.isNotBlank() }?.let { " ・ $it" } ?: ""))
                .setCategory(if (style == ReminderStyle.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
                .setPriority(if (style.popUp) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open).setAutoCancel(true)
            val repeat = context.container.prefs.alertRepeat
            b.setDeleteIntent(Buzz.stopIntent(context, req))
            // ボタンの並び：［止める］（鳴る通知はいつも先頭）・［10分後に再通知］・［地図］
            for (action in ReminderStyle.actions(style, repeat, hasLocation = !location.isNullOrBlank())) when (action) {
                ReminderStyle.ACTION_STOP -> b.addAction(0, action, PendingIntent.getBroadcast(context, req + 2,
                    Intent(context, StopAlertReceiver::class.java).putExtra("nid", req).putExtra("dismiss", true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                ReminderStyle.ACTION_SNOOZE -> b.addAction(0, action, snooze)
                ReminderStyle.ACTION_MAP -> b.addAction(0, action, PendingIntent.getActivity(context, req + 1,
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE))
            }
            val n = b.build()
            // 止めるまで鳴らす：音も繰り返す（通知を消すか開くまで）
            if (repeat && style.sound) n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
            NotificationManagerCompat.from(context).notify(req, n)
            Buzz.start(context, style)
        }
    }
}

/** 「10 分後に再通知」 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Buzz.stop(context)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        val again = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZED).putExtras(intent)
        val pi = PendingIntent.getBroadcast(context, intent.getIntExtra("nid", 0), again,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10 * 60_000L, pi)
    }
}
