package io.github.barista9409.calendar

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.barista9409.calendar.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 同じ共有カレンダーを 2 つのアカウントで登録していても、名前と色が入れ替わらないこと */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApiCalendarPrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun sameCalendarInTwoAccountsKeepsEachSetting() {
        val p = Prefs(ctx)
        val shared = "family@group.calendar.google.com"
        p.putApiCalendars(mapOf(Prefs.apiKey("me@gmail.com", shared) to Prefs.ApiCalendar("わが家", 0xFFAD1457.toInt(), true)))
        p.putApiCalendars(mapOf(Prefs.apiKey("wife@gmail.com", shared) to Prefs.ApiCalendar("家族", 0xFF7986CB.toInt(), true)))
        // 何度同期しても、それぞれのアカウントの設定のまま
        p.putApiCalendars(mapOf(Prefs.apiKey("me@gmail.com", shared) to Prefs.ApiCalendar("わが家", 0xFFAD1457.toInt(), true)))
        assertEquals("わが家", p.apiCalendars.value[Prefs.apiKey("me@gmail.com", shared)]?.name)
        assertEquals("家族", p.apiCalendars.value[Prefs.apiKey("wife@gmail.com", shared)]?.name)
        // 読み直しても同じ
        val again = Prefs(ctx)
        assertEquals(0xFF7986CB.toInt(), again.apiCalendars.value[Prefs.apiKey("wife@gmail.com", shared)]?.color)
    }

    @Test fun oldFormatKeysAreIgnored() {
        ctx.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE).edit()
            .putString("api_calendars", """{"family@group.calendar.google.com":{"n":"古い","s":true}}""").commit()
        assertTrue(Prefs(ctx).apiCalendars.value.isEmpty())
    }
}
