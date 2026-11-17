package io.github.barista9409.calendar

import io.github.barista9409.calendar.util.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorUtilsTest {
    @Test fun mapsLegacyCalendarColor() {
        assertEquals(0xFF8E24AA.toInt(), ColorUtils.toModern(0xFFCD74E6.toInt()))
    }

    @Test fun mapsLegacyEventColor() {
        assertEquals(0xFF7986CB.toInt(), ColorUtils.toModern(0xFFA4BDFC.toInt()))
    }

    @Test fun keepsUnknownColorOpaque() {
        assertEquals(0xFF123456.toInt(), ColorUtils.toModern(0x00123456))
    }
}
