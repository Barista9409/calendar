package io.github.barista9409.calendar.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object DateUtils {
    /** 週の開始曜日（日本の一般的な表示に合わせて日曜始まり） */
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY

    /** 週インデックスの基準（1970-01-04 は日曜） */
    private val BASE: LocalDate = LocalDate.of(1970, 1, 4)
    const val WEEK_COUNT = 52 * 400

    fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    fun weekIndex(date: LocalDate): Int = ChronoUnit.WEEKS.between(BASE, startOfWeek(date)).toInt()
    fun weekStartOf(index: Int): LocalDate = BASE.plusWeeks(index.toLong())

    private val JP = Locale.JAPAN
    private val weekdayFmt = DateTimeFormatter.ofPattern("E", JP)
    private val dateFmt = DateTimeFormatter.ofPattern("M月d日（E）", JP)
    private val ymFmt = DateTimeFormatter.ofPattern("yyyy年M月", JP)
    private val timeFmt = DateTimeFormatter.ofPattern("H:mm", JP)

    fun weekday(date: LocalDate): String = date.format(weekdayFmt)
    fun date(date: LocalDate): String = date.format(dateFmt)
    fun yearMonth(date: LocalDate): String = date.format(ymFmt)

    /** 週のタイトル。月をまたぐ週は「2026年11月〜12月」、年をまたぐ週は「2026年12月〜2027年1月」 */
    fun weekTitle(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        return when {
            weekStart.monthValue == end.monthValue -> yearMonth(weekStart)
            weekStart.year == end.year -> "${yearMonth(weekStart)}〜${end.monthValue}月"
            else -> "${yearMonth(weekStart)}〜${yearMonth(end)}"
        }
    }
    fun time(t: LocalTime): String = t.format(timeFmt)
    fun time(t: LocalDateTime): String = t.format(timeFmt)

    /** 新規作成の既定開始時刻：今日なら次の正時、それ以外は 10:00 */
    fun defaultStart(date: LocalDate, now: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        if (date == now.toLocalDate()) now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        else date.atTime(10, 0)
}
