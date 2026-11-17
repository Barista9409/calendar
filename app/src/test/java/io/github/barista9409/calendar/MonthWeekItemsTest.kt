package io.github.barista9409.calendar

import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.widget.MonthLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** アプリの月表示と月ウィジェットで共通の並べ方（MonthLayout.weekItems） */
class MonthWeekItemsTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val week = LocalDate.of(2027, 1, 10) // 日曜

    private fun allDay(id: Long, title: String, start: LocalDate, days: Int) = EventInstance(id, id, 1, title,
        start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        start.plusDays(days.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, 0, null, null, null, null, null)

    private fun timed(id: Long, title: String, at: LocalDateTime) = EventInstance(id, id, 1, title,
        at.atZone(zone).toInstant().toEpochMilli(), at.plusHours(1).atZone(zone).toInstant().toEpochMilli(),
        false, 0, null, null, null, null, null)

    private fun byDay(vararg es: EventInstance): Map<LocalDate, List<EventInstance>> {
        val m = HashMap<LocalDate, MutableList<EventInstance>>()
        for (e in es) { var d = e.startDate(zone); while (!d.isAfter(e.lastDate(zone))) { m.getOrPut(d) { mutableListOf() }.add(e); d = d.plusDays(1) } }
        return m
    }

    @Test fun multiDayEventIsOneItemSpanningColumns() {
        val items = MonthLayout.weekItems(week, byDay(allDay(1, "出願期間", week.plusDays(2), 4)), zone)
        assertEquals(1, items.size)
        assertEquals(2, items[0].second)
        assertEquals(5, items[0].third)
    }

    @Test fun longEventsComeBeforeTimedOnes() {
        val items = MonthLayout.weekItems(week, byDay(
            timed(1, "習い事", week.atTime(10, 0)),
            allDay(2, "成人の日", week.plusDays(1), 1),
            allDay(3, "出願期間", week.plusDays(2), 4),
        ), zone)
        assertEquals(listOf("出願期間", "成人の日", "習い事").sortedBy { t -> items.indexOfFirst { it.first.title == t } },
            items.map { it.first.title })
        assertEquals(true, items.first().first.isLong)
        assertEquals("習い事", items.last().first.title)
    }

    @Test fun eventContinuingFromPreviousWeekStartsAtColumnZero() {
        val items = MonthLayout.weekItems(week, byDay(allDay(1, "旅行", week.minusDays(2), 4)), zone)
        assertEquals(0, items[0].second)
        assertEquals(1, items[0].third)
    }
}
