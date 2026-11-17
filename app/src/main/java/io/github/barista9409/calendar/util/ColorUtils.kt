package io.github.barista9409.calendar.util

import androidx.compose.ui.graphics.Color

object ColorUtils {
    private val DARK = Color(0xFF1B1D22)

    /**
     * 端末のカレンダープロバイダには Google の旧パレット（淡い色）が保存されている。
     * 公式アプリと同じく、現在のパレット（濃い色）に置き換えて表示する。
     */
    private val MODERN: Map<Int, Int> = mapOf(
        // カレンダーの色（24 色）
        0xac725e to 0x795548, 0xd06b64 to 0xE67C73, 0xf83a22 to 0xD50000, 0xfa573c to 0xF4511E,
        0xff7537 to 0xEF6C00, 0xffad46 to 0xF09300, 0x42d692 to 0x009688, 0x16a765 to 0x0B8043,
        0x7bd148 to 0x7CB342, 0xb3dc6c to 0xC0CA33, 0xfbe983 to 0xE4C441, 0xfad165 to 0xF6BF26,
        0x92e1c0 to 0x33B679, 0x9fe1e7 to 0x039BE5, 0x9fc6e7 to 0x4285F4, 0x4986e7 to 0x3F51B5,
        0x9a9cff to 0x7986CB, 0xb99aff to 0xB39DDB, 0xc2c2c2 to 0x616161, 0xcabdbf to 0xA79B8E,
        0xcca6ac to 0xAD1457, 0xf691b2 to 0xD81B60, 0xcd74e6 to 0x8E24AA, 0xa47ae2 to 0x9E69AF,
        // 予定ごとの色（11 色）
        0xa4bdfc to 0x7986CB, 0x7ae7bf to 0x33B679, 0xdbadff to 0x8E24AA, 0xff887c to 0xE67C73,
        0xfbd75b to 0xF6BF26, 0xffb878 to 0xF4511E, 0x46d6db to 0x039BE5, 0xe1e1e1 to 0x616161,
        0x5484ed to 0x3F51B5, 0x51b749 to 0x0B8043, 0xdc2127 to 0xD50000,
    )

    /** 旧パレットの色なら現在のパレットに変換（それ以外はそのまま） */
    fun toModern(argb: Int): Int = MODERN[argb and 0xFFFFFF]?.let { it or (0xFF shl 24) } ?: (argb or (0xFF shl 24))

    /** 背景色の明るさに応じて、読みやすい文字色（濃い色または白）を返す */
    fun onColor(argb: Int): Color =
        if (androidx.core.graphics.ColorUtils.calculateLuminance(argb or (0xFF shl 24)) > 0.45) DARK else Color.White
}
