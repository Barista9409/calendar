package io.github.barista9409.calendar

import io.github.barista9409.calendar.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class DateUtilsTest {
    @Test fun weekIndexRoundTrip() {
        val d = LocalDate.of(2026, 11, 18) // 水曜
        val start = DateUtils.weekStartOf(DateUtils.weekIndex(d))
        assertEquals(LocalDate.of(2026, 11, 15), start)
        assertEquals(DayOfWeek.SUNDAY, start.dayOfWeek)
    }

    @Test fun indexesCoverFarFuture() {
        assert(DateUtils.weekIndex(LocalDate.of(2300, 1, 1)) < DateUtils.WEEK_COUNT)
    }

    @Test fun defaultStartIsNextHourToday() {
        val now = LocalDateTime.of(2026, 11, 15, 14, 20)
        assertEquals(LocalDateTime.of(2026, 11, 15, 15, 0), DateUtils.defaultStart(now.toLocalDate(), now))
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), DateUtils.defaultStart(LocalDate.of(2026, 11, 20), now))
    }
}

class WeekTitleTest {
    @org.junit.Test fun sameMonth() = org.junit.Assert.assertEquals("2026年11月", DateUtils.weekTitle(java.time.LocalDate.of(2026, 11, 15)))
    @org.junit.Test fun crossMonth() = org.junit.Assert.assertEquals("2026年11月〜12月", DateUtils.weekTitle(java.time.LocalDate.of(2026, 11, 29)))
    @org.junit.Test fun crossYear() = org.junit.Assert.assertEquals("2026年12月〜2027年1月", DateUtils.weekTitle(java.time.LocalDate.of(2026, 12, 27)))
}

class RecurrenceTest {
    private val R = io.github.barista9409.calendar.util.Recurrence
    @org.junit.Test fun stripEnd() =
        org.junit.Assert.assertEquals("FREQ=WEEKLY;BYDAY=TU", R.stripEnd("FREQ=WEEKLY;UNTIL=20270101T000000Z;BYDAY=TU"))
    @org.junit.Test fun endBeforeTimed() {
        val begin = java.time.ZonedDateTime.of(2026, 11, 17, 1, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        org.junit.Assert.assertEquals("FREQ=WEEKLY;BYDAY=TU;UNTIL=20261117T005959Z", R.endBefore("FREQ=WEEKLY;BYDAY=TU;COUNT=5", begin, false))
    }
    @org.junit.Test fun endBeforeAllDay() {
        val begin = java.time.ZonedDateTime.of(2026, 11, 21, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        org.junit.Assert.assertEquals("FREQ=DAILY;UNTIL=20261120", R.endBefore("FREQ=DAILY", begin, true))
    }
    @org.junit.Test fun durationTimed() = org.junit.Assert.assertEquals("P3600S", R.duration(0, 3_600_000, false))
}
