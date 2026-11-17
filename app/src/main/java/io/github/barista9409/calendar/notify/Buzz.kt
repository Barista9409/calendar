package io.github.barista9409.calendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import io.github.barista9409.calendar.container

/**
 * 通知のバイブ。通知の仕組みに任せると、サイレントや「通知のクールダウン」で省かれることがあるため、
 * アプリがアラーム扱いで直接鳴らす（長さ・止めるまで繰り返す、を設定どおりに）。
 */
object Buzz {
    /**
     * Android のバイブは「鳴らしたときと同じ呼び出し元」からでないと止められない。
     * 通知のボタンなどは毎回別の呼び出し元になるので、アプリ全体で 1 つのバイブを使い回す。
     */
    @Volatile private var shared: Vibrator? = null

    private fun vibrator(context: Context): Vibrator = shared ?: synchronized(this) {
        shared ?: run {
            val app = context.applicationContext
            if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        }.also { shared = it }
    }

    fun start(context: Context, style: ReminderStyle) {
        val prefs = context.container.prefs
        val silent = context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_SILENT
        if (!ReminderStyle.shouldVibrate(style, silent)) return
        val v = vibrator(context)
        if (!v.hasVibrator()) return
        val repeat = prefs.alertRepeat
        val effect = if (repeat) VibrationEffect.createWaveform(ReminderStyle.repeatingPattern(prefs.vibrateLength), 0)
        else VibrationEffect.createWaveform(ReminderStyle.pattern(prefs.vibrateLength), -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else @Suppress("DEPRECATION") v.vibrate(effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
        if (repeat) {
            // 鳴りっぱなしを防ぐ：最長 5 分で止める
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ReminderStyle.MAX_REPEAT_MS, stopIntent(context, 0))
        }
    }

    fun stop(context: Context) { runCatching { vibrator(context).cancel() } }

    /** 止める（通知を消したとき・［止める］・時間切れ） */
    fun stopIntent(context: Context, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(context, 900_000 + notificationId,
            Intent(context, StopAlertReceiver::class.java).putExtra("nid", notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Buzz.stop(context)
        val nid = intent.getIntExtra("nid", 0)
        if (nid != 0 && intent.getBooleanExtra("dismiss", false)) NotificationManagerCompat.from(context).cancel(nid)
    }
}
