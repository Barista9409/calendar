package io.github.barista9409.calendar

import io.github.barista9409.calendar.widget.MonthLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthLayoutTest {
    @Test fun multiDayEventsShareLanes() {
        val lanes = MonthLayout.lanes(listOf(Triple("A", 0, 2), Triple("B", 1, 1), Triple("C", 3, 6), Triple("D", 0, 0)))
        assertEquals(listOf("A", "C"), lanes[0].map { it.item })
        assertEquals(listOf("D", "B"), lanes[1].map { it.item })
    }

    @Test fun overflowShowsPlusCount() {
        val lanes = MonthLayout.lanes(listOf(Triple("A", 0, 0), Triple("B", 0, 0), Triple("C", 0, 0), Triple("D", 1, 1)))
        val (shown, more) = MonthLayout.fit(lanes, 2)
        assertEquals(1, shown)
        assertEquals(2, more[0])
        assertEquals(0, more[1])
        val (all, none) = MonthLayout.fit(lanes, 3)
        assertEquals(3, all); assertEquals(0, none.sum())
    }

    @Test fun fixedMaxShowsUpToMaxAndDotsForTheRest() {
        val lanes = MonthLayout.lanes((1..5).map { Triple("予定$it", 0, 0) } + Triple("X", 1, 1))
        val (shown, more) = MonthLayout.fitMax(lanes, 3)
        assertEquals(3, shown)
        assertEquals(2, more[0])  // 5 件中 3 件表示 → 2 件は「•••」
        assertEquals(0, more[1])
        val (all, none) = MonthLayout.fitMax(lanes, 6)
        assertEquals(5, all); assertEquals(0, none.sum())
    }

    @Test fun rowHeightGrowsWithMax() {
        val h3 = MonthLayout.rowHeightFor(3, 16f)
        val h4 = MonthLayout.rowHeightFor(4, 16f)
        assertEquals(16f, h4 - h3)
        // 日付の行 + 3 件 + 「•••」の行がすべて入る
        assertEquals(24f + 2f + 16f * 4, h3)
    }
}
