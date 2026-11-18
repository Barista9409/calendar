package io.github.barista9409.calendar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test fun accountsAndCalendarsScreenOpens() {
        rule.setContent { CalendarTheme { MenuTheme { CalendarsScreen(onBack = {}) } } }
        rule.waitForIdle()
        rule.onNodeWithText("アカウントとカレンダー").assertExists()
        rule.onNodeWithText("アカウントを追加").assertExists()
        // 同期の状態はここには出さない（「同期」の画面だけ）
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("最後の同期", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun syncScreenOpens() {
        rule.setContent { CalendarTheme { MenuTheme { io.github.barista9409.calendar.ui.calendars.SyncScreen(onBack = {}) } } }
        rule.waitForIdle()
        // まとめの 1 枚：ボタンの右に最後の同期の日時（テスト環境ではスマホ全体の自動同期がオフ扱いなので［オンにする］）
        rule.waitUntil(5_000) { rule.onAllNodesWithText("最後の同期", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("困ったとき（詳しい状態）").performClick()
        rule.waitForIdle()
    }

    @Test fun settingsScreenOpens() {
        rule.setContent { CalendarTheme { MenuTheme { SettingsScreen(onBack = {}, onOpenUpdate = {}) } } }
        rule.waitForIdle()
        rule.onNodeWithText("設定").assertExists()
        // 入口は 1 か所ずつ
        for (t in listOf("アカウントとカレンダー", "同期", "アップデート", "このアプリを共有")) rule.onNodeWithText(t).assertExists()
    }

    @Test fun mainScreenOpens() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CalendarApp>()
        val vm = CalendarViewModel(app)
        rule.setContent { CalendarTheme { CalendarScreen(vm, onOpenUpdate = {}, onOpenSync = {}, onOpenSettings = {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("今日").assertExists()
    }

    @Test fun everySettingsPageOpens() {
        // 設定の各ページを順に開いて戻る
        rule.setContent { CalendarTheme { MenuTheme { SettingsScreen(onBack = {}, onOpenUpdate = {}) } } }
        for (page in listOf("色とテーマ", "カレンダーの表示", "通知", "添付ファイル")) {
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

    @Test fun menuShowsSyncingWhileRunning() {
        // 同期中にメニューを開き直しても、終わるまで「同期しています…」を出し続ける
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CalendarApp>()
        val vm = CalendarViewModel(app)
        rule.setContent { CalendarTheme { CalendarScreen(vm, onOpenUpdate = {}, onOpenSync = {}, onOpenSettings = {}) } }
        app.container.sync.requestAll(manual = true)
        rule.waitUntil(5_000) { app.container.sync.run.value.running }
        repeat(2) {
            rule.onNodeWithContentDescription("メニュー").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("同期しています…").fetchSemanticsNodes().isNotEmpty() }
            // 「同期しています…」はメニューの 1 か所だけ（重ねて出さない）
            org.junit.Assert.assertEquals(1, rule.onAllNodesWithText("同期しています…").fetchSemanticsNodes().size)
            org.junit.Assert.assertTrue(rule.onAllNodesWithText("今すぐ同期").fetchSemanticsNodes().isEmpty())
            vm.requestCloseDrawer()
            rule.waitForIdle()
        }
    }

    @Test fun menuHasOnlyViewItems() {
        // メニューは「何を見るか」と同期・設定だけ。アカウントの丸ボタンや、重なっていた入口は出さない
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CalendarApp>()
        val vm = CalendarViewModel(app)
        rule.setContent { CalendarTheme { CalendarScreen(vm, onOpenUpdate = {}, onOpenSync = {}, onOpenSettings = {}) } }
        rule.onNodeWithContentDescription("メニュー").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("設定").assertExists()
        for (gone in listOf("すべて", "追加", "カレンダーと同期", "今すぐ同期", "このアプリを共有", "表示するアカウント"))
            org.junit.Assert.assertTrue(gone, rule.onAllNodesWithText(gone).fetchSemanticsNodes().isEmpty())
    }
}
