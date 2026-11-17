package io.github.barista9409.calendar.ui.settings

import io.github.barista9409.calendar.data.Prefs

/** おすすめのテーマ（色の組み合わせ）。選ぶとまとめて設定され、そこから好みに直せる */
data class ThemePreset(val name: String, val mood: String, val theme: Prefs.AppTheme)

private fun argb(rgb: Long) = (0xFF000000L or rgb).toInt()

private fun preset(index: Int, name: String, mood: String, dark: Boolean, accent: Long, cal: Long, pop: Long, menu: Long, transparency: Int = 0) =
    ThemePreset(name, mood, Prefs.AppTheme(
        accent = argb(accent),
        calendarBg = argb(cal), calendarAlpha = 100 - transparency,
        popupBg = argb(pop), popupAlpha = 100 - minOf(transparency, 20),
        mode = if (dark) 2 else 1,
        menuBg = argb(menu), menuAlpha = 100 - minOf(transparency, 20),
        preset = index,
    ))

val THEME_PRESETS = listOf(
    preset(0, "スタンダード", "迷ったらこれ", false, 0x3157D5, 0xFFFFFF, 0xFFFFFF, 0xF6F7F9),
    preset(1, "北欧ミント", "すっきり爽やか", false, 0x00897B, 0xFFFFFF, 0xE8F5EC, 0xDDF0E4),
    preset(2, "さくら", "やわらかい", false, 0xC2185B, 0xFFFFFF, 0xFDEEE8, 0xFCE4EC),
    preset(3, "カフェラテ", "木の温かみ", false, 0x8D6E63, 0xF5F1E8, 0xFFF8E1, 0xEFE6D8),
    preset(4, "ラベンダー", "上品で静か", false, 0x7B1FA2, 0xFFFFFF, 0xF1ECFB, 0xEDE7F6),
    preset(5, "ミッドナイト", "夜に目が楽", true, 0x8AB4F8, 0x15171C, 0x1A2340, 0x121A33),
    preset(6, "フォレスト", "森の静けさ", true, 0xA5D6A7, 0x17302A, 0x1E3A33, 0x122520),
    preset(7, "ガラス", "壁紙を生かす", true, 0x9DB4FF, 0x000000, 0x15171C, 0x15171C, transparency = 45),
)

/** マイテーマの番号（preset に入れる） */
const val MY_THEME = 100

/** 選んだテーマから何か所変えたか（明るさ・メインの色・カレンダー・詳細・メニュー） */
fun changedAreas(base: Prefs.AppTheme, now: Prefs.AppTheme): Int = listOf(
    base.mode != now.mode,
    base.accent != now.accent,
    base.calendarBg != now.calendarBg || base.calendarAlpha != now.calendarAlpha,
    base.popupBg != now.popupBg || base.popupAlpha != now.popupAlpha,
    base.menuBg != now.menuBg || base.menuAlpha != now.menuAlpha,
).count { it }
