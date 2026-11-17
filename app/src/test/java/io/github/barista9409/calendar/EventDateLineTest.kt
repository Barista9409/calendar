package io.github.barista9409.calendar

import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.sheet.eventDateLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 予定詳細で日付が二重に出ないこと */
class EventDateLineTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private fun ev(s: LocalDateTime, e: LocalDateTime) = EventInstance(1, 1, 1, "t",
        s.atZone(zone).toInstant().toEpochMilli(), e.atZone(zone).toInstant().toEpochMilli(), false, 0, null, null, null, null, null)

    @Test fun multiDayDoesNotRepeatDate() {
        val line = eventDateLine(ev(LocalDateTime.of(2026, 11, 17, 9, 0), LocalDateTime.of(2026, 11, 23, 10, 0)), zone)
        assertEquals(1, Regex("11月17日").findAll(line).count())
    }

    @Test fun sameDayShowsDateOnce() {
        val line = eventDateLine(ev(LocalDateTime.of(2026, 11, 17, 15, 30), LocalDateTime.of(2026, 11, 17, 16, 30)), zone)
        assertEquals(1, Regex("11月17日").findAll(line).count())
        assert(line.contains("15:30"))
    }
}
