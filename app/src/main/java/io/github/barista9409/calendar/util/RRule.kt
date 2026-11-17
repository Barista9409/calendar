package io.github.barista9409.calendar.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 繰り返しルール（Google カレンダーと同じ項目：頻度・間隔・曜日・毎月の指定・終了）。
 */
data class RRule(
    val freq: Freq = Freq.WEEKLY,
    val interval: Int = 1,
    /** 毎週のときの曜日 */
    val byDay: Set<DayOfWeek> = emptySet(),
    /** 毎月のとき：true = 第 N ○曜日、false = ○日 */
    val monthlyByWeekday: Boolean = false,
    /** 終了：日付（その日まで） */
    val until: LocalDate? = null,
    /** 終了：回数 */
    val count: Int? = null,
) {
    enum class Freq(val unit: String) { DAILY("日"), WEEKLY("週"), MONTHLY("か月"), YEARLY("年") }

    /** RRULE 文字列（start は開始日。毎月第 N 曜日の計算に使う） */
    fun toRule(start: LocalDate, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        when (freq) {
            Freq.WEEKLY -> parts += "BYDAY=" + (byDay.ifEmpty { setOf(start.dayOfWeek) }).sortedBy { it.value }.joinToString(",") { code(it) }
            Freq.MONTHLY -> parts += if (monthlyByWeekday) "BYDAY=${nth(start)}${code(start.dayOfWeek)}" else "BYMONTHDAY=${start.dayOfMonth}"
            else -> {}
        }
        until?.let { u ->
            parts += "UNTIL=" + if (allDay) u.format(DATE) else u.atTime(23, 59, 59).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(TS)
        }
        if (until == null) count?.let { parts += "COUNT=$it" }
        return parts.joinToString(";")
    }

    /** 日本語の説明（例：2 週間ごと（月・水）、2027年3月31日まで） */
    fun label(start: LocalDate): String {
        val base = when (freq) {
            Freq.DAILY -> if (interval == 1) "毎日" else "${interval}日ごと"
            Freq.WEEKLY -> {
                val days = byDay.ifEmpty { setOf(start.dayOfWeek) }
                val weekdays = days == WEEKDAYS
                when {
                    interval == 1 && weekdays -> "毎週平日（月〜金）"
                    interval == 1 && days.size == 1 -> "毎週${jp(days.first())}曜日"
                    else -> (if (interval == 1) "毎週" else "${interval}週間ごと") + "（" + days.sortedBy { it.value }.joinToString("・") { jp(it) } + "）"
                }
            }
            Freq.MONTHLY -> (if (interval == 1) "毎月" else "${interval}か月ごと") +
                if (monthlyByWeekday) "第${nth(start)}${jp(start.dayOfWeek)}曜日" else "${start.dayOfMonth}日"
            Freq.YEARLY -> (if (interval == 1) "毎年" else "${interval}年ごと") + "${start.monthValue}月${start.dayOfMonth}日"
        }
        val end = when {
            until != null -> "、${until.year}年${until.monthValue}月${until.dayOfMonth}日まで"
            count != null -> "、${count}回"
            else -> ""
        }
        return base + end
    }

    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val CODES = mapOf(DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU")
        private val JP = mapOf(DayOfWeek.MONDAY to "月", DayOfWeek.TUESDAY to "火", DayOfWeek.WEDNESDAY to "水",
            DayOfWeek.THURSDAY to "木", DayOfWeek.FRIDAY to "金", DayOfWeek.SATURDAY to "土", DayOfWeek.SUNDAY to "日")

        fun code(d: DayOfWeek) = CODES.getValue(d)
        fun jp(d: DayOfWeek) = JP.getValue(d)
        /** その月の第何週の曜日か */
        fun nth(d: LocalDate) = (d.dayOfMonth - 1) / 7 + 1

        /** RRULE 文字列を読む（読めない部分は無視） */
        fun parse(rule: String?): RRule? {
            if (rule.isNullOrBlank()) return null
            val m = rule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
            val freq = runCatching { Freq.valueOf(m["FREQ"] ?: return null) }.getOrNull() ?: return null
            val byDayRaw = m["BYDAY"].orEmpty().split(',').filter { it.isNotBlank() }
            val days = byDayRaw.mapNotNull { s -> CODES.entries.firstOrNull { s.endsWith(it.value) }?.key }.toSet()
            val until = m["UNTIL"]?.let { u -> runCatching { LocalDate.parse(u.take(8), DATE) }.getOrNull() }
            return RRule(
                freq = freq,
                interval = m["INTERVAL"]?.toIntOrNull() ?: 1,
                byDay = if (freq == Freq.WEEKLY) days else emptySet(),
                monthlyByWeekday = freq == Freq.MONTHLY && byDayRaw.isNotEmpty(),
                until = until,
                count = m["COUNT"]?.toIntOrNull(),
            )
        }

        /** Google カレンダーと同じ「よく使う繰り返し」の候補 */
        fun presets(start: LocalDate): List<RRule> = listOf(
            RRule(Freq.DAILY),
            RRule(Freq.WEEKLY, byDay = setOf(start.dayOfWeek)),
            RRule(Freq.MONTHLY, monthlyByWeekday = true),
            RRule(Freq.MONTHLY),
            RRule(Freq.YEARLY),
            RRule(Freq.WEEKLY, byDay = WEEKDAYS),
        )
    }
}
