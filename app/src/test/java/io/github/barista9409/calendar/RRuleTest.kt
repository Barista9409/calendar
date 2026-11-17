package io.github.barista9409.calendar

import io.github.barista9409.calendar.util.RRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RRuleTest {
    private val start = LocalDate.of(2026, 11, 17) // 第3火曜日
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test fun weeklyWithUntil() {
        val r = RRule(RRule.Freq.WEEKLY, interval = 2, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), until = LocalDate.of(2027, 3, 31))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20270331T145959Z", r.toRule(start, false, tokyo))
        assertEquals("2週間ごと（月・水）、2027年3月31日まで", r.label(start))
    }

    @Test fun monthlyNthWeekdayWithCount() {
        val r = RRule(RRule.Freq.MONTHLY, monthlyByWeekday = true, count = 5)
        assertEquals("FREQ=MONTHLY;BYDAY=3TU;COUNT=5", r.toRule(start, false, tokyo))
        assertEquals("毎月第3火曜日、5回", r.label(start))
    }

    @Test fun allDayUntilUsesDate() {
        val r = RRule(RRule.Freq.DAILY, until = LocalDate.of(2026, 12, 1))
        assertEquals("FREQ=DAILY;UNTIL=20261201", r.toRule(start, true, tokyo))
    }

    @Test fun parseRoundTrip() {
        val p = RRule.parse("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20270101T000000Z")!!
        assertEquals(RRule.WEEKDAYS, p.byDay)
        assertEquals(LocalDate.of(2027, 1, 1), p.until)
        assertEquals("毎週平日（月〜金）、2027年1月1日まで", p.label(start))
    }

    @Test fun parseMonthlyByDay() {
        val p = RRule.parse("FREQ=MONTHLY;BYDAY=3TU")!!
        assertEquals(true, p.monthlyByWeekday)
    }
}
