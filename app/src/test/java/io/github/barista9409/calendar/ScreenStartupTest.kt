package io.github.barista9409.calendar

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.barista9409.calendar.ui.CalendarViewModel
import io.github.barista9409.calendar.ui.calendars.CalendarsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 画面を開いたときに落ちないことを確かめる（0.7.17 で「カレンダーと同期」を開くと落ちた不具合の再発防止）。
 * ViewModel を実際に作り、起動時の処理を最後まで走らせる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = CalendarApp::class)
class ScreenStartupTest {
    private val app: CalendarApp = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun calendarsScreenStarts() {
        val errors = mutableListOf<Throwable>()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> errors += e }
        try {
            val vm = CalendarsViewModel(app)
            idle(); Thread.sleep(300); idle()
            assertEquals(emptyList<Throwable>(), errors)
            assertNotNull(vm.summary)
            // まとめのボタン（同期を頼む）も落ちない
            vm.summaryAction(); idle(); Thread.sleep(300); idle()
            assertEquals(emptyList<Throwable>(), errors)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun mainScreenStarts() {
        val errors = mutableListOf<Throwable>()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> errors += e }
        try {
            val vm = CalendarViewModel(app)
            idle(); Thread.sleep(300); idle()
            vm.syncNow(); vm.requestCloseDrawer(); idle()
            assertEquals(emptyList<Throwable>(), errors)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun monthWidgetShowsSyncingUntilDone() {
        // 見出しをタップしたら、同期が終わる（印が消える）まで、くるくると「同期しています…」を出し続ける
        val id = 1
        val W = io.github.barista9409.calendar.widget.MonthWidget
        fun draw() = W.frame(app, id).apply(app, android.widget.FrameLayout(app))
        fun spinner(v: android.view.View) = v.findViewById<android.view.View>(R.id.spinner).visibility
        fun title(v: android.view.View) = v.findViewById<android.widget.TextView>(R.id.title).text.toString()

        io.github.barista9409.calendar.widget.WidgetSyncState.startMonth(app, id)
        val v = draw()
        assertEquals(android.view.View.VISIBLE, spinner(v))
        assertEquals("同期しています…", title(v))
        // 何度描き直しても（同期で届いた変更の描き直しなど）出たまま
        assertEquals(android.view.View.VISIBLE, spinner(draw()))

        // 同期が終わってジョブが印を消したら、元の見出しに戻る
        kotlinx.coroutines.runBlocking { io.github.barista9409.calendar.widget.WidgetSyncState.clear(app) }
        val v2 = draw()
        assertEquals(android.view.View.GONE, spinner(v2))
        assertTrue(title(v2).startsWith("本日："))

        // ジョブが止められて印が残っても、90 秒を過ぎたら出さない
        W.store(app).edit().putLong("refresh_$id", System.currentTimeMillis() - 100_000).commit()
        assertEquals(android.view.View.GONE, spinner(draw()))
    }

    @Test fun syncStateExpires() {
        val S = io.github.barista9409.calendar.widget.WidgetSyncState
        val now = 1_000_000L
        assertTrue(S.isActive(now - 1_000, now))
        assertTrue(S.isActive(now - 80_000, now))
        org.junit.Assert.assertFalse(S.isActive(now - 91_000, now))
        org.junit.Assert.assertFalse(S.isActive(0L, now))
    }
}
