package io.github.barista9409.calendar.model

import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class ViewMode(val label: String) { DAY("日"), WEEK("週"), MONTH("月"), AGENDA("予定") }

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val accessLevel: Int,
    val isPrimary: Boolean,
    val ownerAccount: String?,
    /** Google カレンダー上の ID（Google アカウントのカレンダーのみ） */
    val apiId: String? = null,
) {
    val isGoogle: Boolean get() = accountType == "com.google" && apiId != null
    val writable: Boolean get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
    val isOwner: Boolean get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_OWNER
}

/** 1回分の予定（繰り返し予定は回ごとに1件） */
data class EventInstance(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?,
    val description: String?,
    val rrule: String?,
    val organizer: String?,
    val timeZone: String?,
    /** このスマホで変えて、まだ Google に送っていない */
    val pendingUpload: Boolean = false,
) {
    val isRecurring: Boolean get() = !rrule.isNullOrBlank()

    /** 一覧に出す名前（送信待ちは先頭に「↑」） */
    val displayTitle: String get() = if (pendingUpload) "↑ $title" else title

    /** 終日予定、または 24 時間以上の予定（時間軸ではなく終日の帯に表示する） */
    val isLong: Boolean get() = allDay || end - begin >= 24L * 60 * 60 * 1000

    /** 開始日（終日予定は UTC 基準で保存されている） */
    fun startDate(zone: ZoneId): LocalDate =
        if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()

    /** 最終日（その日を含む） */
    fun lastDate(zone: ZoneId): LocalDate {
        return if (allDay) {
            val endEx = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
            val s = startDate(zone)
            if (endEx.isAfter(s)) endEx.minusDays(1) else s
        } else {
            val e = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(zone).toLocalDate()
            maxOf(e, startDate(zone))
        }
    }

    fun occursOn(date: LocalDate, zone: ZoneId): Boolean =
        !date.isBefore(startDate(zone)) && !date.isAfter(lastDate(zone))

    fun startLocal(zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
    fun endLocal(zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime()
}

/** 作成・編集用の入力内容 */
data class EventDraft(
    val eventId: Long? = null,
    val title: String = "",
    val calendarId: Long? = null,
    val allDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String = "",
    val description: String = "",
    /** 説明の最後にある添付ファイルのリンク（編集画面には出さず、保存時に付け直す） */
    val attachmentBlock: String = "",
    /** 入力画面で選んだ、保存後にアップロードするファイル */
    val pendingFiles: List<PendingFile> = emptyList(),
    val reminderMinutes: Int? = 30,
    val isRecurring: Boolean = false,
    /** 新規作成時の繰り返し（RRULE。null = 繰り返さない） */
    val rrule: String? = null,
    /** ゲストのメールアドレス */
    val guests: List<String> = emptyList(),
    /** 予定ごとの色（Colors テーブルのキー。null = カレンダーの色） */
    val colorKey: String? = null,
    /** 繰り返し予定を編集するときの、元の回の開始時刻 */
    val instanceBegin: Long? = null,
)

/** 繰り返し予定の変更範囲 */
enum class RecurScope(val label: String) { THIS("この予定"), FOLLOWING("これ以降のすべての予定"), ALL("すべての予定") }

/** 予定に設定できる色（Google の予定の色） */
data class EventColor(val key: String, val color: Int)

/** 入力画面で選んだ添付ファイル（保存したあとにアップロードする） */
data class PendingFile(val uri: String, val name: String)

/**
 * 予定の詳細に出す「作成者」。自分（カレンダーのアカウント）やカレンダー自身のときは出さない（Google と同じ）。
 * 共有カレンダーの予定は作成者がカレンダーの ID（…@group.calendar.google.com）になるので、これも出さない。
 */
fun creatorLabel(organizer: String?, accountName: String?, calendarName: String?, ownerAccount: String?): String? {
    val o = organizer?.trim().orEmpty()
    if (o.isEmpty()) return null
    if (o.equals(accountName, true) || o.equals(calendarName, true) || o.equals(ownerAccount, true)) return null
    if (o.endsWith("calendar.google.com", ignoreCase = true)) return null
    return o
}
