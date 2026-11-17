package io.github.barista9409.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.github.barista9409.calendar.container

val Brand = Color(0xFF3157D5)
val SundayRed = Color(0xFFC0392B)
val SaturdayBlue = Color(0xFF2B5FB8)
val NowRed = Color(0xFFD93025)

data class CalendarColors(
    val sunday: Color, val saturday: Color, val gridLine: Color, val altMonth: Color, val selectedBg: Color,
)

val LocalCalendarColors = staticCompositionLocalOf {
    CalendarColors(SundayRed, SaturdayBlue, Color(0xFFE6E8EE), Color(0x0A000000), Color(0xFFEAF0FF))
}

/** ポップアップ（シート・入力画面など）用の配色 */
val LocalPopupScheme = staticCompositionLocalOf<ColorScheme?> { null }
val LocalMenuScheme = staticCompositionLocalOf<ColorScheme?> { null }

internal val Light = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEFC), onPrimaryContainer = Color(0xFF1C3A99),
    surface = Color.White, onSurface = Color(0xFF1B1D22),
    surfaceVariant = Color(0xFFF0F2F7), onSurfaceVariant = Color(0xFF4A4F5A),
    outline = Color(0xFFC9CDD6), outlineVariant = Color(0xFFE6E8EE),
    background = Color.White, error = Color(0xFFA93226),
)

internal val Dark = darkColorScheme(
    primary = Color(0xFF9DB4FF), onPrimary = Color(0xFF0B2161),
    primaryContainer = Color(0xFF233C8F), onPrimaryContainer = Color(0xFFDCE4FF),
    surface = Color(0xFF15171C), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF242832), onSurfaceVariant = Color(0xFFB5BBC6),
    outline = Color(0xFF5A606B), outlineVariant = Color(0xFF2E333D),
    background = Color(0xFF15171C), error = Color(0xFFFFB4AB),
)

private fun opaque(rgb: Int) = Color(rgb or (0xFF shl 24))
/** 下地に乗せる文字色：濃い色と白っぽい色のうち、下地との濃さの差（コントラスト）が大きいほう */
private fun on(c: Color): Color {
    val dark = Color(0xFF1B1D22); val light = Color(0xFFF1F3F7)
    val l = relativeLuminance(c.copy(alpha = 1f).toArgb())
    fun ratio(x: Color): Double { val lx = relativeLuminance(x.toArgb()); return (maxOf(l, lx) + 0.05) / (minOf(l, lx) + 0.05) }
    return if (ratio(dark) >= ratio(light)) dark else light
}

/**
 * 背景色が今の明るさ（ライト／ダーク）に合うか。
 * 合わない色（ダークで白い背景など）は使わず標準にする（明るさを切り替えても文字が読めるように）。
 */
internal fun bgFitsMode(argb: Int, dark: Boolean): Boolean =
    argb != 0 && (relativeLuminance(argb) < 0.4) == dark

/** 色の明るさ（相対輝度 0〜1）。端末に依存しない計算なので、単体テストでも同じ結果になる */
internal fun relativeLuminance(argb: Int): Double {
    fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
    return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
}

/**
 * 背景色（と透明度）に合わせて、文字色や区切り線を作り直した配色。
 * 画面の背景（surface）だけは透け具合を反映し、ダイアログ・選択中の印などの下地（container 系）は
 * 必ず不透明で、文字色と同じ明るさの系統にそろえる（暗い下地に暗い文字、半透明で後ろが透ける、を防ぐ）。
 */
internal fun schemeFor(base: ColorScheme, bgRgb: Int, alpha: Int, accent: Color, defaultOn: Color, dark: Boolean): ColorScheme {
    val use = bgFitsMode(bgRgb, dark)
    val solid = if (use) opaque(bgRgb) else base.surface.copy(alpha = 1f)
    val fg = if (use) on(solid) else defaultOn
    val c = solid.copy(alpha = alpha.coerceIn(0, 100) / 100f)
    fun tone(t: Float) = androidx.compose.ui.graphics.lerp(solid, fg, t)
    val accentSoft = androidx.compose.ui.graphics.lerp(solid, accent, 0.28f)
    return base.copy(
        primary = accent, onPrimary = on(accent),
        primaryContainer = accentSoft, onPrimaryContainer = fg,
        secondaryContainer = accentSoft, onSecondaryContainer = fg,
        tertiaryContainer = accentSoft, onTertiaryContainer = fg,
        surface = c, background = c, onSurface = fg, onBackground = fg,
        onSurfaceVariant = fg.copy(alpha = 0.72f), surfaceVariant = fg.copy(alpha = 0.07f),
        surfaceContainerLowest = solid, surfaceContainerLow = tone(0.03f), surfaceContainer = tone(0.05f),
        surfaceContainerHigh = tone(0.08f), surfaceContainerHighest = tone(0.12f),
        surfaceBright = tone(0.02f), surfaceDim = tone(0.10f), surfaceTint = accent,
        inverseSurface = fg, inverseOnSurface = solid, inversePrimary = accentSoft,
        outline = fg.copy(alpha = 0.35f), outlineVariant = fg.copy(alpha = 0.14f),
    )
}

@Composable
fun CalendarTheme(content: @Composable () -> Unit) {
    val t by LocalContext.current.container.prefs.appTheme.collectAsState()
    val dark = when (t.mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val base = if (dark) Dark else Light
    val accent = if (t.accent != 0) opaque(t.accent) else base.primary
    val calendar = schemeFor(base, t.calendarBg, t.calendarAlpha, accent, base.onSurface, dark)
    val popup = schemeFor(base, t.popupBg, t.popupAlpha, accent, base.onSurface, dark)
    // メニューの色を決めていなければ、予定の詳細と同じ
    val menu = if (t.menuBg == 0) popup else schemeFor(base, t.menuBg, t.menuAlpha, accent, base.onSurface, dark)
    val fg = calendar.onSurface
    val cc = CalendarColors(
        sunday = if (fg.luminance() > 0.5f) Color(0xFFFF8A80) else SundayRed,
        saturday = if (fg.luminance() > 0.5f) Color(0xFF8AB4F8) else SaturdayBlue,
        gridLine = fg.copy(alpha = 0.12f), altMonth = fg.copy(alpha = 0.035f), selectedBg = accent.copy(alpha = 0.15f),
    )
    CompositionLocalProvider(LocalCalendarColors provides cc, LocalPopupScheme provides popup, LocalMenuScheme provides menu) {
        MaterialTheme(colorScheme = calendar, content = content)
    }
}

/** 左から出るメニュー用の配色で中身を表示 */
@Composable
fun MenuTheme(content: @Composable () -> Unit) {
    val p = LocalMenuScheme.current ?: LocalPopupScheme.current ?: MaterialTheme.colorScheme
    MaterialTheme(colorScheme = p) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.onSurface, content = content)
    }
}

/** シート・入力画面などのポップアップ用の配色で中身を表示 */
@Composable
fun PopupTheme(content: @Composable () -> Unit) {
    val p = LocalPopupScheme.current ?: MaterialTheme.colorScheme
    MaterialTheme(colorScheme = p) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.onSurface, content = content)
    }
}
