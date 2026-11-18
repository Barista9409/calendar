package io.github.barista9409.calendar

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.barista9409.calendar.ui.CalendarViewModel
import io.github.barista9409.calendar.ui.calendars.CalendarsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test fun monthWidgetKeepsSpinnerWhileRefreshing() {
        // 見出しタップの直後に描き直しても、くるくるが消えない（0.7.16 では描き直しで消えて見えなかった）
        val id = 1
        val store = io.github.barista9409.calendar.widget.MonthWidget.store(app)
        store.edit().putLong("refresh_$id", System.currentTimeMillis()).commit()
        val v = io.github.barista9409.calendar.widget.MonthWidget.frame(app, id).apply(app, android.widget.FrameLayout(app))
        assertEquals(android.view.View.VISIBLE, v.findViewById<android.view.View>(R.id.spinner).visibility)
        // 読み込みが終わったら（または 6 秒たったら）出さない
        store.edit().remove("refresh_$id").commit()
        val v2 = io.github.barista9409.calendar.widget.MonthWidget.frame(app, id).apply(app, android.widget.FrameLayout(app))
        assertEquals(android.view.View.GONE, v2.findViewById<android.view.View>(R.id.spinner).visibility)
        store.edit().putLong("refresh_$id", System.currentTimeMillis() - 10_000).commit()
        val v3 = io.github.barista9409.calendar.widget.MonthWidget.frame(app, id).apply(app, android.widget.FrameLayout(app))
        assertEquals(android.view.View.GONE, v3.findViewById<android.view.View>(R.id.spinner).visibility)
    }
}
