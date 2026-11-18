package io.github.barista9409.calendar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.barista9409.calendar.ui.CalendarScreen
import io.github.barista9409.calendar.ui.CalendarViewModel
import io.github.barista9409.calendar.ui.calendars.CalendarsScreen
import io.github.barista9409.calendar.ui.settings.SettingsScreen
import io.github.barista9409.calendar.ui.theme.CalendarTheme
import io.github.barista9409.calendar.ui.theme.MenuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * メニューから開く画面と、メインの画面を実際に描いて、落ちないことを確かめる。
 * （0.7.17 で「カレンダーと同期」を開くと落ちた。画面を開く操作そのものをテストする）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = CalendarApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun calendarsAndSyncScreenOpens() {
        rule.setContent { CalendarTheme { MenuTheme { CalendarsScreen(onBack = {}) } } }
        rule.waitForIdle()
        rule.onNodeWithText("カレンダーと同期").assertExists()
    }

    @Test fun settingsScreenOpens() {
        rule.setContent { CalendarTheme { MenuTheme { SettingsScreen(onBack = {}, onOpenUpdate = {}) } } }
        rule.waitForIdle()
        rule.onNodeWithText("設定").assertExists()
    }

    @Test fun mainScreenOpens() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CalendarApp>()
        val vm = CalendarViewModel(app)
        rule.setContent { CalendarTheme { CalendarScreen(vm, onOpenUpdate = {}, onOpenCalendars = {}, onOpenSettings = {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("今日").assertExists()
    }

    @Test fun everySettingsPageOpens() {
        // 設定の各ページを順に開いて戻る
        rule.setContent { CalendarTheme { MenuTheme { SettingsScreen(onBack = {}, onOpenUpdate = {}) } } }
        for (page in listOf("色とテーマ", "カレンダーの表示", "通知", "添付ファイル", "アカウント")) {
            rule.onNodeWithText(page).performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("戻る", useUnmergedTree = true).performClick()
            rule.waitForIdle()
        }
    }

    @Test fun updateScreenOpens() {
        rule.setContent { CalendarTheme { MenuTheme { io.github.barista9409.calendar.ui.settings.UpdateScreen(onBack = {}) } } }
        rule.waitForIdle()
    }
}
