package io.github.barista9409.calendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.barista9409.calendar.ui.ShareApp
import io.github.barista9409.calendar.ui.settings.THEME_PRESETS
import io.github.barista9409.calendar.ui.settings.changedAreas
import io.github.barista9409.calendar.ui.theme.Dark
import io.github.barista9409.calendar.ui.theme.Light
import io.github.barista9409.calendar.ui.theme.bgFitsMode
import io.github.barista9409.calendar.ui.theme.relativeLuminance
import io.github.barista9409.calendar.ui.theme.schemeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** おすすめテーマ：8 種類、どれも読みやすいこと／共有の文面 */
class ThemePresetTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a.copy(alpha = 1f).toArgb()); val lb = relativeLuminance(b.copy(alpha = 1f).toArgb())
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test fun eightDistinctPresets() {
        assertEquals(8, THEME_PRESETS.size)
        assertEquals(8, THEME_PRESETS.map { it.name }.toSet().size)
        THEME_PRESETS.forEachIndexed { i, p -> assertEquals(i, p.theme.preset) }
    }

    @Test fun presetColorsFitTheirBrightnessAndAreReadable() {
        for (p in THEME_PRESETS) {
            val t = p.theme
            val dark = t.mode == 2
            val base = if (dark) Dark else Light
            for (bg in listOf(t.calendarBg, t.popupBg, t.menuBg)) {
                // 明るさに合わない色は無視されて標準になってしまうので、必ず合っていること
                assertTrue("${p.name} ${Integer.toHexString(bg)}", bgFitsMode(bg, dark))
                val s = schemeFor(base, bg, 100, Color(t.accent), base.onSurface, dark)
                assertTrue("${p.name} 文字", contrast(s.onSurface, s.surface) >= 4.5)
                assertTrue("${p.name} ダイアログ", contrast(s.onSurface, s.surfaceContainerHigh) >= 4.5)
                assertTrue("${p.name} 選んだ日", contrast(s.onPrimary, s.primary) >= 3.0)
                assertTrue("${p.name} 選択中の印", contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5)
            }
        }
    }

    @Test fun countsChangedAreas() {
        val t = THEME_PRESETS[1].theme
        assertEquals(0, changedAreas(t, t))
        assertEquals(1, changedAreas(t, t.copy(accent = 0xFF000001.toInt())))
        assertEquals(2, changedAreas(t, t.copy(menuBg = 0xFFFFFFFF.toInt(), calendarAlpha = 50)))
    }

    @Test fun shareTextHasDownloadLinkAndSteps() {
        val text = ShareApp.text("1.2.3")
        assertTrue(text.contains(ShareApp.DOWNLOAD_URL))
        assertTrue(text.contains("1.2.3"))
        assertTrue(text.contains("インストール"))
        // APK の直リンク（常に最新版）
        assertTrue(ShareApp.DOWNLOAD_URL.startsWith("https://github.com/"))
        assertTrue(ShareApp.DOWNLOAD_URL.endsWith("/releases/latest/download/calendar.apk"))
    }
}
