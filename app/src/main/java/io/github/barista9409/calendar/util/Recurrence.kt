package io.github.barista9409.calendar.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 繰り返しルール（RRULE）の操作 */
object Recurrence {
    private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    /** UNTIL と COUNT を取り除く */
    fun stripEnd(rule: String): String =
        rule.split(';').filter { p -> !p.startsWith("UNTIL=") && !p.startsWith("COUNT=") && p.isNotBlank() }.joinToString(";")

    /** この回（instanceBegin）の直前で終わるようにした RRULE */
    fun endBefore(rule: String, instanceBegin: Long, allDay: Boolean): String {
        val until = if (allDay) DATE.format(Instant.ofEpochMilli(instanceBegin - 86_400_000L))
        else TS.format(Instant.ofEpochMilli(instanceBegin - 1000L))
        return stripEnd(rule) + ";UNTIL=" + until
    }

    /** 予定の長さ（RFC 5545 の DURATION） */
    fun duration(startMillis: Long, endMillis: Long, allDay: Boolean): String =
        if (allDay) "P${((endMillis - startMillis) / 86_400_000L).coerceAtLeast(1)}D"
        else "P${((endMillis - startMillis) / 1000L).coerceAtLeast(60)}S"
}
