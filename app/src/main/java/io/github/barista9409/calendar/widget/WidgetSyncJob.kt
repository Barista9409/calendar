package io.github.barista9409.calendar.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.barista9409.calendar.CalendarApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ウィジェットの見出しをタップしたときの同期。
 * 同期を頼み、終わるまで待ってから「同期中」の印を消して描き直す。
 * ジョブにしておくと、アプリが閉じていても同期が終わるまで動ける（受信だけだと 10 秒ほどで止められる）。
 */
class WidgetSyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext as CalendarApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val sync = app.container.sync
                sync.requestAll(upload = true)
                // 始まるのを待ち（最大 5 秒）、終わるのを待つ（最大 30 秒。同期の見守りは 20 秒で打ち切る）
                withTimeoutOrNull(5_000) { sync.run.first { it.running } }
                withTimeoutOrNull(30_000) { sync.run.first { !it.running } }
            } finally {
                WidgetSyncState.clear(app)
                runCatching { WidgetUpdater.updateAll(app) }
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // 止められたら印だけ消しておく（くるくるが出たままにならないように）
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            WidgetSyncState.clear(applicationContext)
            runCatching { WidgetUpdater.updateAll(applicationContext) }
        }
        return false
    }

    companion object {
        private const val JOB_ID = 4243

        fun start(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val b = JobInfo.Builder(JOB_ID, ComponentName(context, WidgetSyncJob::class.java))
            if (Build.VERSION.SDK_INT >= 31) b.setExpedited(true) else b.setOverrideDeadline(0)
            val ok = runCatching { js.schedule(b.build()) == JobScheduler.RESULT_SUCCESS }.getOrDefault(false)
            if (!ok && Build.VERSION.SDK_INT >= 31) {
                // 急ぎのジョブの枠を使い切っていたら、普通のジョブで
                runCatching { js.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, WidgetSyncJob::class.java)).setOverrideDeadline(0).build()) }
            }
        }
    }
}

/** ウィジェットの「同期中」の印（見出しをタップした時刻）。描くたびにこれを見て、くるくると「同期しています…」を出す */
object WidgetSyncState {
    /** これより古い印は無視する（ジョブが止められても出たままにならないように） */
    const val MAX_MS = 45_000L

    fun isActive(startedAt: Long, now: Long = System.currentTimeMillis()) = startedAt > 0 && now - startedAt in 0 until MAX_MS

    /** 月ウィジェット */
    fun startMonth(context: Context, id: Int) {
        MonthWidget.store(context).edit().putLong("refresh_$id", System.currentTimeMillis()).commit()
    }

    fun monthStartedAt(context: Context, id: Int) = MonthWidget.store(context).getLong("refresh_$id", 0L)

    /** すべてのウィジェットの印を消す */
    suspend fun clear(context: Context) {
        val store = MonthWidget.store(context)
        val e = store.edit()
        store.all.keys.filter { it.startsWith("refresh_") }.forEach { e.remove(it) }
        e.commit()
        runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(DayWidget::class.java).forEach { gid ->
                updateAppWidgetState(context, gid) { it.remove(REFRESHING_KEY) }
            }
        }
    }
}
