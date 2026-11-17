package io.github.barista9409.calendar

import androidx.compose.ui.unit.dp
import io.github.barista9409.calendar.ui.month.monthCellCapacity
import io.github.barista9409.calendar.ui.month.monthRowHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** アプリの月表示：行の高さが固定で、空きがあるのに「+N」にならないこと */
class MonthViewSizeTest {
    @Test fun rowHeightDependsOnlyOnFullHeight() {
        assertEquals(100.dp, monthRowHeight(600.dp))
        assertEquals(64.dp, monthRowHeight(200.dp))
    }

    @Test fun tallCellShowsManyEvents() {
        assertEquals(5, monthCellCapacity(100f, 14f))
        assertTrue(monthCellCapacity(64f, 14f) >= 2)
        assertEquals(1, monthCellCapacity(30f, 14f))
    }
}
