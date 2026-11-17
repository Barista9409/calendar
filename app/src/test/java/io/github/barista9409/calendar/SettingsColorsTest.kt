package io.github.barista9409.calendar

import io.github.barista9409.calendar.data.Prefs
import io.github.barista9409.calendar.ui.settings.ACCENT_COLORS
import io.github.barista9409.calendar.ui.settings.DARK_BG_COLORS
import io.github.barista9409.calendar.ui.settings.LIGHT_BG_COLORS
import io.github.barista9409.calendar.ui.settings.WIDGET_BG_COLORS
import io.github.barista9409.calendar.ui.settings.selectedIndex
import io.github.barista9409.calendar.ui.theme.bgFitsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 設定の色：選んだ色が残ること、おすすめは各 8 色 */
class SettingsColorsTest {
    @Test fun eachPaletteHasEightDistinctColors() {
        for (p in listOf(ACCENT_COLORS, LIGHT_BG_COLORS, DARK_BG_COLORS, WIDGET_BG_COLORS)) {
            assertEquals(8, p.size)
            assertEquals(8, p.map { it.argb }.toSet().size)
            assertTrue(p.none { it.argb == 0 }) // 0 は「標準」なので色として使わない
        }
    }

    @Test fun choosingBlackStaysSelected() {
        // 以前は黒 = 0 = 標準になり、選んでも残らなかった
        val black = WIDGET_BG_COLORS.first { it.name == "ブラック" }.argb
        assertEquals(WIDGET_BG_COLORS.indexOfFirst { it.name == "ブラック" }, selectedIndex(WIDGET_BG_COLORS, black, defaultIndex = 0))
        assertEquals(black, Prefs.normalizeColor(black))
    }

    @Test fun defaultShowsTheDefaultSwatch() {
        assertEquals(0, selectedIndex(LIGHT_BG_COLORS, 0))
        assertEquals(4, selectedIndex(WIDGET_BG_COLORS, 0, defaultIndex = 4))
    }

    @Test fun oldSavedColorsAreReadAsSelected() {
        val oldBlue = 0x3157D5 // 以前の保存形式（不透明の印なし）
        assertEquals(0, selectedIndex(ACCENT_COLORS, Prefs.normalizeColor(oldBlue)))
        assertEquals(0, Prefs.normalizeColor(0))
    }

    @Test fun backgroundMustFitLightOrDark() {
        val white = LIGHT_BG_COLORS[0].argb
        val charcoal = DARK_BG_COLORS[0].argb
        assertTrue(bgFitsMode(white, dark = false))
        assertFalse(bgFitsMode(white, dark = true))
        assertTrue(bgFitsMode(charcoal, dark = true))
        assertFalse(bgFitsMode(0, dark = false))
        LIGHT_BG_COLORS.forEach { assertTrue(it.name, bgFitsMode(it.argb, dark = false)) }
        DARK_BG_COLORS.forEach { assertTrue(it.name, bgFitsMode(it.argb, dark = true)) }
    }
}
