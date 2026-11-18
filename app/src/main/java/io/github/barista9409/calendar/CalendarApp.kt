package io.github.barista9409.calendar

import android.app.Application
import android.content.Context
import io.github.barista9409.calendar.data.CalendarRepository
import io.github.barista9409.calendar.data.Prefs
import io.github.barista9409.calendar.update.UpdateWorker
import io.github.barista9409.calendar.update.Updater

class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val sync = io.github.barista9409.calendar.data.SyncCenter(context)
    val repository = CalendarRepository(context, prefs, sync)
    val updater = Updater(context)
    val googleAuth = io.github.barista9409.calendar.google.GoogleAuth(context)
    val google = io.github.barista9409.calendar.google.GoogleSync(prefs, googleAuth)
    val driveApi = io.github.barista9409.calendar.google.DriveApi(googleAuth)
    val attachments = io.github.barista9409.calendar.google.AttachmentService(context, driveApi,
        io.github.barista9409.calendar.google.AttachmentStore(context, prefs), prefs)
}

class CalendarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateWorker.schedule(this)
        // 同期の開始・終了を見守る（最後に同期した時刻の表示に使う）
        container.sync.startWatching()
        // カレンダーが変わったら（同期で届いた変更も）ウィジェットを描き直す
        io.github.barista9409.calendar.widget.WidgetRefreshJob.schedule(this)
        // 前に開いたときから同期があったかを確かめる
        Thread { runCatching { container.sync.checkRemoteSync() } }.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as CalendarApp).container
