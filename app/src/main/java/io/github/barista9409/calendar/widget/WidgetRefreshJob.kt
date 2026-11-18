package io.github.barista9409.calendar.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * カレンダーのデータが変わったら（Google からの同期・ほかのアプリでの変更も含む）ウィジェットを描き直す。
 * Android 標準の「データの変更で動くジョブ」（JobInfo.TriggerContentUri）。アプリが動いていなくても動く。
 * このジョブは 1 回動くと終わるので、動くたびに次を予約し直す。
 */
class WidgetRefreshJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // アプリが閉じていても、同期で届いたのを記録する（「最後の同期」の時刻）
                runCatching {
                    val sync = (app as io.github.barista9409.calendar.CalendarApp).container.sync
                    sync.checkRemoteSync()
                    // このアプリが書き換えたのでなければ、Google から届いた変更なので「同期した」と記録
                    sync.noteProviderChange()
                }
                runCatching { WidgetUpdater.updateAll(app) }
            } finally {
                schedule(app)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4242

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WidgetRefreshJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                // 同期は短い間に何度も書き込むので、落ち着いてからまとめて 1 回描き直す
                .setTriggerContentUpdateDelay(2_000)
                .setTriggerContentMaxDelay(10_000)
                .build()
            runCatching { js.schedule(job) }
        }
    }
}
