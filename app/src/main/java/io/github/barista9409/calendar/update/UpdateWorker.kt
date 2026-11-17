package io.github.barista9409.calendar.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.barista9409.calendar.CalendarApp
import io.github.barista9409.calendar.MainActivity
import io.github.barista9409.calendar.R
import java.util.concurrent.TimeUnit

/** 1日1回の更新確認 */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CalendarApp).container
        if (!c.prefs.autoUpdateCheck) return Result.success()
        return try {
            val info = c.updater.fetch()
            c.prefs.lastUpdateCheck = System.currentTimeMillis()
            if (c.updater.isNewer(info) && info.versionCode != c.prefs.skippedVersionCode) notify(info)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(info: UpdateInfo) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.channel_update), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_UPDATE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("新しいバージョン ${info.versionName} があります")
            .setContentText(info.releaseNotes.lineSequence().firstOrNull().orEmpty())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 1001
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
