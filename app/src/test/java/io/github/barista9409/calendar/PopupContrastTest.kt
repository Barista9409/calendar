package io.github.barista9409.calendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.barista9409.calendar.ui.settings.ACCENT_COLORS
import io.github.barista9409.calendar.ui.settings.DARK_BG_COLORS
import io.github.barista9409.calendar.ui.settings.LIGHT_BG_COLORS
import io.github.barista9409.calendar.ui.theme.Dark
import io.github.barista9409.calendar.ui.theme.Light
import io.github.barista9409.calendar.ui.theme.relativeLuminance
import io.github.barista9409.calendar.ui.theme.schemeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日付・時刻の選択などのダイアログが読めること（暗い下地に暗い文字にならない、下地が透けない）。
 * すべての背景色・明るさ・メインの色の組み合わせで確かめる。
 */
class PopupContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a.copy(alpha = 1f).toArgb()); val lb = relativeLuminance(b.copy(alpha = 1f).toArgb())
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun cases() = buildList {
        for (dark in listOf(false, true)) {
            val base = if (dark) Dark else Light
            // 標準・今の明るさに合う色・合わない色（標準に戻るはず）
            val bgs = listOf(0) + LIGHT_BG_COLORS.map { it.argb } + DARK_BG_COLORS.map { it.argb }
            for (bg in bgs) for (acc in ACCENT_COLORS) add(Triple(dark, bg, schemeFor(base, bg, 60, Color(acc.argb), base.onSurface, dark)))
        }
    }

    @Test fun dialogBackgroundsAreOpaqueAndReadable() {
        for ((dark, bg, s) in cases()) {
            val where = "dark=$dark bg=${Integer.toHexString(bg)}"
            for (container in listOf(s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceContainer, s.primaryContainer, s.secondaryContainer)) {
                assertEquals(where, 1f, container.alpha)
            }
            assertTrue(where, contrast(s.onSurface, s.surfaceContainerHigh) >= 4.5)
            assertTrue(where, contrast(s.onSurface, s.surfaceContainerHighest) >= 4.5)
            assertTrue(where, contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5)
            assertTrue(where, contrast(s.onSecondaryContainer, s.secondaryContainer) >= 4.5)
        }
    }

    @Test fun selectedDayIsReadable() {
        for ((dark, bg, s) in cases()) {
            assertTrue("dark=$dark bg=${Integer.toHexString(bg)}", contrast(s.onPrimary, s.primary) >= 3.0)
        }
    }
}
