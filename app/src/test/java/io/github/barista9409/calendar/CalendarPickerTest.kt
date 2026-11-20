package io.github.barista9409.calendar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.EventDraft
import io.github.barista9409.calendar.model.pickableCalendars
import io.github.barista9409.calendar.ui.edit.EventEditScreen
import io.github.barista9409.calendar.ui.theme.CalendarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 予定の入力画面で選べるカレンダー（メニューで非表示にしたものは出さない） */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = CalendarApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarPickerTest {
    @get:Rule val rule = createComposeRule()

    private fun cal(id: Long, name: String) = CalendarInfo(id, name, "me@example.com", "com.google", 0xFF3157D5.toInt(),
        android.provider.CalendarContract.Calendars.CAL_ACCESS_OWNER, id == 1L, "me@example.com", "c$id")

    private val all = listOf(cal(1, "わが家"), cal(2, "習い事"), cal(3, "会議"))

    @Test fun hiddenCalendarsAreNotPickable() {
        assertEquals(listOf("わが家", "会議"), pickableCalendars(all, hidden = setOf(2L), currentId = 1L).map { it.name })
        // いまの予定が入っているカレンダーは、非表示でも残す
        assertEquals(listOf("わが家", "習い事", "会議"), pickableCalendars(all, hidden = setOf(2L), currentId = 2L).map { it.name })
    }

    @Test fun pickerListShowsOnlyVisible() {
        val draft = EventDraft(calendarId = 1L, start = java.time.LocalDateTime.of(2026, 11, 20, 10, 0), end = java.time.LocalDateTime.of(2026, 11, 20, 11, 0))
        rule.setContent {
            CalendarTheme {
                EventEditScreen(draft, pickableCalendars(all, setOf(2L), draft.calendarId), loadColors = { emptyList() },
                    onChange = {}, onSave = {}, onClose = {})
            }
        }
        rule.onNodeWithText("わが家").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("会議").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("習い事").fetchSemanticsNodes().isEmpty())
    }
}
