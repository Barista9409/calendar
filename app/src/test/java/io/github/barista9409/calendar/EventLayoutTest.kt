package io.github.barista9409.calendar

import io.github.barista9409.calendar.ui.timeline.EventLayout
import io.github.barista9409.calendar.ui.timeline.EventLayout.Item
import io.github.barista9409.calendar.ui.timeline.EventLayout.Placement
import org.junit.Assert.assertEquals
import org.junit.Test

class EventLayoutTest {
    @Test fun nonOverlappingUseSingleColumn() {
        val r = EventLayout.layout(listOf(Item(540, 600), Item(600, 660)))
        assertEquals(listOf(Placement(0, 1), Placement(0, 1)), r)
    }

    @Test fun overlappingSplitIntoColumns() {
        val r = EventLayout.layout(listOf(Item(540, 660), Item(570, 630), Item(600, 720)))
        assertEquals(3, r[0].columns)
        assertEquals(setOf(0, 1, 2), r.map { it.column }.toSet())
    }

    @Test fun columnIsReusedWhenFree() {
        // A 9-10, B 9:30-11, C 10-10:30 → C は A の列を再利用
        val r = EventLayout.layout(listOf(Item(540, 600), Item(570, 660), Item(600, 630)))
        assertEquals(r[0].column, r[2].column)
        assertEquals(2, r[2].columns)
    }

    @Test fun keepsInputOrder() {
        val r = EventLayout.layout(listOf(Item(700, 760), Item(540, 600)))
        assertEquals(2, r.size)
        assertEquals(Placement(0, 1), r[1])
    }
}
