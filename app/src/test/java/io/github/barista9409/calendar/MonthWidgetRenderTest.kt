package io.github.barista9409.calendar

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.widget.MonthWidget
import io.github.barista9409.calendar.widget.WColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/** 月ウィジェットの 1 週分の行を実際にレイアウトして確かめる（過去の不具合の再発防止） */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MonthWidgetRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sunday = 0xFFC0392B.toInt()
    private val colors = WColors(0xFF000000.toInt(), 0xFF111111.toInt(), -1, -1, -1, -1, -1, -1, sunday, -1, -1)
    private fun spec(capacity: Int) = MonthWidget.Spec(rowDp = 120f, laneDp = 16f, capacity = capacity, fontSp = 10f, c = colors)
    private val week = LocalDate.of(2026, 11, 15) // 日曜始まり
    private val today = LocalDate.of(2026, 11, 16)

    private var nextId = 1L
    private fun allDay(title: String, start: LocalDate, days: Int) = EventInstance(
        nextId++, nextId, 1, title,
        start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        start.plusDays(days.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, 0xFFAD1457.toInt(), null, null, null, null, null,
    )

    private fun byDay(vararg events: EventInstance): Map<LocalDate, List<EventInstance>> {
        val m = HashMap<LocalDate, MutableList<EventInstance>>()
        for (e in events) {
            var d = e.startDate(ZoneOffset.UTC)
            while (!d.isAfter(e.lastDate(ZoneOffset.UTC))) { m.getOrPut(d) { mutableListOf() }.add(e); d = d.plusDays(1) }
        }
        return m
    }

    private fun lanesOf(v: android.view.View) = v.findViewById<LinearLayout>(R.id.lanes)

    @Test fun recycledRowIsClearedBeforeDrawing() {
        val data = byDay(allDay("習い事", week, 1), allDay("買い物", week.plusDays(6), 1))
        val view = MonthWidget.weekViews(ctx, spec(5), week, today, data).apply(ctx, FrameLayout(ctx))
        val first = lanesOf(view).childCount
        assertEquals(1, first)
        // 同じ行を別の週・同じ週に使い回しても、段が増えない
        MonthWidget.weekViews(ctx, spec(5), week, today, data).reapply(ctx, view)
        assertEquals(first, lanesOf(view).childCount)
        MonthWidget.weekViews(ctx, spec(5), week.plusWeeks(1), today, emptyMap()).reapply(ctx, view)
        assertEquals(0, lanesOf(view).childCount)
    }

    @Test fun overflowShowsDotsOnlyWhereHidden() {
        val events = (1..6).map { allDay("予定$it", week, 1) }.toTypedArray()
        val view = MonthWidget.weekViews(ctx, spec(3), week, today, byDay(*events)).apply(ctx, FrameLayout(ctx))
        val lanes = lanesOf(view)
        assertEquals(3, lanes.childCount) // 2 段 + 「•••」
        val more = lanes.getChildAt(2) as LinearLayout
        assertEquals("•••", (more.getChildAt(0) as TextView).text.toString())
        assertEquals("", (more.getChildAt(1) as TextView).text.toString())
    }

    @Test fun noDotsWithoutHiddenEvents() {
        val view = MonthWidget.weekViews(ctx, spec(3), week, today, byDay(allDay("A", week, 1), allDay("B", week, 1)))
            .apply(ctx, FrameLayout(ctx))
        assertEquals(2, lanesOf(view).childCount)
    }

    @Test fun multiDayEventIsOneChipSpanningDays() {
        val view = MonthWidget.weekViews(ctx, spec(5), week, today, byDay(allDay("文化祭の準備期間", week.plusDays(2), 5)))
            .apply(ctx, FrameLayout(ctx))
        val lane = lanesOf(view).getChildAt(0) as LinearLayout
        assertEquals(2, lane.childCount) // 2 日分の空き + 5 日分の帯
        val chip = lane.getChildAt(1) as TextView
        assertEquals("文化祭の準備期間", chip.text.toString())
        assertEquals(5f, (chip.layoutParams as LinearLayout.LayoutParams).weight)
    }

    @Test fun firstOfMonthShowsMonth() {
        val view = MonthWidget.weekViews(ctx, spec(5), LocalDate.of(2026, 11, 29), today, emptyMap()).apply(ctx, FrameLayout(ctx))
        assertEquals("12/1", view.findViewById<TextView>(R.id.num2).text.toString())
        // 左の月の列：月が変わる週はその月
        assertEquals("12月", view.findViewById<TextView>(R.id.rail_text).text.toString())
        assertTrue(view.findViewById<TextView>(R.id.num0).text.toString() == "29")
    }

    @Test fun holidayIsRed() {
        val holiday = LocalDate.of(2026, 11, 23) // 勤労感謝の日（月曜）
        val view = MonthWidget.weekViews(ctx, spec(5), LocalDate.of(2026, 11, 22), today, emptyMap(), setOf(holiday))
            .apply(ctx, FrameLayout(ctx))
        assertEquals(sunday, view.findViewById<TextView>(R.id.num1).currentTextColor)
        assertEquals(0xFF111111.toInt(), view.findViewById<TextView>(R.id.num2).currentTextColor)
    }

    @Test fun railShowsMonthOfTheWeek() {
        val view = MonthWidget.weekViews(ctx, spec(5), LocalDate.of(2026, 11, 22), today, emptyMap()).apply(ctx, FrameLayout(ctx))
        assertEquals("11月", view.findViewById<TextView>(R.id.rail_text).text.toString())
        assertEquals(MonthWidget.railMonth(LocalDate.of(2026, 11, 29)), 12 to true)
        assertEquals(MonthWidget.railMonth(LocalDate.of(2026, 12, 6)), 12 to false)
    }
}
