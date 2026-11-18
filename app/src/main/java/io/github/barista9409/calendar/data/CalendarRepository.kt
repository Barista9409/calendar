package io.github.barista9409.calendar.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.EventColor
import io.github.barista9409.calendar.model.EventDraft
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Colors
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.util.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 端末のカレンダープロバイダ（Google アカウントと OS が同期）への読み書き。
 */
class CalendarRepository(private val context: Context, private val prefs: Prefs, private val sync: SyncCenter? = null) {
    /** 予定に色が個別指定されていないときに使うカレンダーの色（Google から取得した色を優先） */
    @Volatile private var calendarColors: Map<Long, Int> = emptyMap()

    private val cr get() = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** カレンダーの変更通知（同期・他アプリでの編集を含む） */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        cr.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        awaitClose { cr.unregisterContentObserver(observer) }
    }

    /** includeHiddenAccounts=false のとき、「このアプリで使わない」アカウントのカレンダーは除く */
    /** スマホに同期していないカレンダー（Google カレンダーでは見えるのに、このアプリに出ない原因）。(id, 名前, アカウント) */
    suspend fun unsyncedCalendars(): List<Triple<Long, String, String>> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val list = mutableListOf<Triple<Long, String, String>>()
        cr.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE),
            "${Calendars.SYNC_EVENTS}=0", null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(3) == "com.google") list += Triple(c.getLong(0), c.getString(1) ?: "(名前なし)", c.getString(2).orEmpty())
        }
        list
    }

    /** そのカレンダーをスマホに同期する（Android のカレンダー設定と同じ操作） */
    suspend fun enableSync(calendarId: Long) = withContext(Dispatchers.IO) {
        cr.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId),
            ContentValues().apply { put(Calendars.SYNC_EVENTS, 1); put(Calendars.VISIBLE, 1) }, null, null)
        Unit
    }

    suspend fun calendars(includeHiddenAccounts: Boolean = false): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val hiddenAccounts = if (includeHiddenAccounts) emptySet() else prefs.hiddenAccounts.value
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.IS_PRIMARY, Calendars.OWNER_ACCOUNT,
            Calendars.NAME,
        )
        val list = mutableListOf<CalendarInfo>()
        cr.query(Calendars.CONTENT_URI, projection, "${Calendars.SYNC_EVENTS}=1", null, null)?.use { c ->
            while (c.moveToNext()) {
                val account = c.getString(2).orEmpty()
                val owner = c.getString(7)
                if (account in hiddenAccounts) continue
                val primaryCol = if (c.isNull(6)) null else c.getInt(6) == 1
                val type = c.getString(3).orEmpty()
                val apiId = if (type == "com.google") (owner ?: c.getString(8)) else null
                list += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "(名前なし)",
                    accountName = account,
                    accountType = type,
                    color = ColorUtils.toModern(c.getInt(4)),
                    accessLevel = c.getInt(5),
                    isPrimary = primaryCol ?: (owner != null && owner == account),
                    ownerAccount = owner,
                    apiId = apiId,
                )
            }
        }
        calendarColors = list.associate { it.id to it.color }
        list.sortedWith(compareBy<CalendarInfo> { it.accountName }.thenByDescending { it.isPrimary }
            .thenByDescending { it.isOwner }.thenBy { it.name })
    }

    suspend fun instances(startMillis: Long, endMillis: Long): List<EventInstance> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startMillis)
            ContentUris.appendId(it, endMillis)
        }.build()
        val projection = arrayOf(
            Instances._ID, Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.DISPLAY_COLOR, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
            Instances.RRULE, Instances.ORGANIZER, Instances.EVENT_TIMEZONE, Instances.STATUS, Instances.EVENT_COLOR,
        )
        if (calendarColors.isEmpty()) calendars()
        val calColors = calendarColors
        val list = mutableListOf<EventInstance>()
        // このアプリで変えて、まだ Google に届いていない予定（届くと外れる）
        val dirty = sync?.let { runCatching { it.pendingEdits().keys }.getOrNull() } ?: emptySet()
        cr.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                if (!c.isNull(13) && c.getInt(13) == Events.STATUS_CANCELED) continue
                list += EventInstance(
                    instanceId = c.getLong(0),
                    eventId = c.getLong(1),
                    calendarId = c.getLong(2),
                    title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "(タイトルなし)",
                    begin = c.getLong(4),
                    end = c.getLong(5),
                    allDay = c.getInt(6) == 1,
                    color = if (!c.isNull(14) && c.getInt(14) != 0) ColorUtils.toModern(c.getInt(14))
                        else calColors[c.getLong(2)] ?: ColorUtils.toModern(c.getInt(7)),
                    location = c.getString(8),
                    description = c.getString(9),
                    rrule = c.getString(10),
                    organizer = c.getString(11),
                    timeZone = c.getString(12),
                    pendingUpload = c.getLong(1) in dirty,
                )
            }
        }
        list
    }

    suspend fun reminderMinutes(eventId: Long): Int? = withContext(Dispatchers.IO) {
        cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID}=?",
            arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC")?.use { c ->
            if (c.moveToFirst()) c.getInt(0) else null
        }
    }

    private fun values(d: EventDraft, zone: ZoneId): ContentValues = ContentValues().apply {
        put(Events.TITLE, d.title.ifBlank { "(タイトルなし)" })
        put(Events.EVENT_LOCATION, d.location)
        put(Events.DESCRIPTION, io.github.barista9409.calendar.google.AttachmentText.join(d.description, d.attachmentBlock))
        if (d.colorKey != null) put(Events.EVENT_COLOR_KEY, d.colorKey)
        else if (d.eventId != null) { putNull(Events.EVENT_COLOR_KEY); putNull(Events.EVENT_COLOR) }
        if (d.guests.isNotEmpty()) put(Events.HAS_ATTENDEE_DATA, 1)
        if (!d.isRecurring) {
            if (d.allDay) {
                val s = d.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                var endDate = d.end.toLocalDate().plusDays(1)
                if (!endDate.isAfter(d.start.toLocalDate())) endDate = d.start.toLocalDate().plusDays(1)
                put(Events.DTSTART, s)
                put(Events.DTEND, endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.ALL_DAY, 1)
            } else {
                val s = d.start.atZone(zone).toInstant().toEpochMilli()
                val e = maxOf(d.end.atZone(zone).toInstant().toEpochMilli(), s)
                put(Events.DTSTART, s)
                put(Events.DTEND, e)
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.ALL_DAY, 0)
            }
        }
    }

    suspend fun insert(d: EventDraft, zone: ZoneId = ZoneId.systemDefault()): Long = withContext(Dispatchers.IO) {
        val v = values(d, zone).apply {
            put(Events.CALENDAR_ID, requireNotNull(d.calendarId))
            // 繰り返し予定は DTEND ではなく DURATION で長さを指定する
            d.rrule?.let { rule ->
                val s = getAsLong(Events.DTSTART); val e = getAsLong(Events.DTEND)
                remove(Events.DTEND)
                put(Events.RRULE, rule)
                put(Events.DURATION, if (d.allDay) "P${((e - s) / 86_400_000L).coerceAtLeast(1)}D" else "P${((e - s) / 1000L).coerceAtLeast(60)}S")
            }
        }
        val uri = requireNotNull(cr.insert(Events.CONTENT_URI, v)) { "予定を保存できませんでした" }
        val id = ContentUris.parseId(uri)
        d.reminderMinutes?.let { setReminder(id, it) }
        d.guests.forEach { addAttendee(id, it) }
        sync?.localChanged(id)
        id
    }

    private fun addAttendee(eventId: Long, email: String) {
        cr.insert(Attendees.CONTENT_URI, ContentValues().apply {
            put(Attendees.EVENT_ID, eventId)
            put(Attendees.ATTENDEE_EMAIL, email)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_INVITED)
        })
    }

    /** ゲスト（主催者以外）のメールアドレスと出欠 */
    data class Guest(val email: String, val status: Int)

    suspend fun guests(eventId: Long): List<Guest> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Guest>()
        cr.query(Attendees.CONTENT_URI, arrayOf(Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_RELATIONSHIP, Attendees.ATTENDEE_STATUS),
            "${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val email = c.getString(0) ?: continue
                if (c.getInt(1) == Attendees.RELATIONSHIP_ORGANIZER) continue
                out += Guest(email, c.getInt(2))
            }
        }
        out
    }

    suspend fun colorKey(eventId: Long): String? = withContext(Dispatchers.IO) {
        cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.EVENT_COLOR_KEY), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    /** アカウントで使える予定の色（Google は 11 色） */
    suspend fun eventColors(accountName: String, accountType: String): List<EventColor> = withContext(Dispatchers.IO) {
        val out = mutableListOf<EventColor>()
        cr.query(Colors.CONTENT_URI, arrayOf(Colors.COLOR_KEY, Colors.COLOR),
            "${Colors.ACCOUNT_NAME}=? AND ${Colors.ACCOUNT_TYPE}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}",
            arrayOf(accountName, accountType), null)?.use { c ->
            while (c.moveToNext()) out += EventColor(c.getString(0), ColorUtils.toModern(c.getInt(1)))
        }
        out.sortedBy { it.key.toIntOrNull() ?: 99 }
    }

    suspend fun update(d: EventDraft, zone: ZoneId = ZoneId.systemDefault()) = withContext(Dispatchers.IO) {
        val id = requireNotNull(d.eventId)
        val v = values(d, zone)
        d.calendarId?.let { if (!d.isRecurring) v.put(Events.CALENDAR_ID, it) }
        // 単発の予定に繰り返しを設定した場合
        if (!d.isRecurring && d.rrule != null) {
            val s = v.getAsLong(Events.DTSTART); val e = v.getAsLong(Events.DTEND)
            v.remove(Events.DTEND); v.putNull(Events.DTEND)
            v.put(Events.RRULE, d.rrule)
            v.put(Events.DURATION, io.github.barista9409.calendar.util.Recurrence.duration(s, e, d.allDay))
        }
        cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null)
        cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(id.toString()))
        d.reminderMinutes?.let { setReminder(id, it) }
        // ゲストの差分を反映
        val current = guests(id).map { it.email.lowercase() }.toSet()
        val wanted = d.guests.map { it.lowercase() }.toSet()
        (current - wanted).forEach { email ->
            cr.delete(Attendees.CONTENT_URI, "${Attendees.EVENT_ID}=? AND lower(${Attendees.ATTENDEE_EMAIL})=?", arrayOf(id.toString(), email))
        }
        d.guests.filter { it.lowercase() !in current }.forEach { addAttendee(id, it) }
        sync?.localChanged(id)
    }

    private fun setReminder(eventId: Long, minutes: Int) {
        cr.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, eventId)
            put(Reminders.MINUTES, minutes)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
        cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            ContentValues().apply { put(Events.HAS_ALARM, 1) }, null, null)
    }

    // ---------------- 繰り返し予定 ----------------

    data class SeriesInfo(val dtstart: Long, val rrule: String?, val allDay: Boolean)

    suspend fun seriesInfo(eventId: Long): SeriesInfo? = withContext(Dispatchers.IO) {
        cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.DTSTART, Events.RRULE, Events.ALL_DAY),
            null, null, null)?.use { c -> if (c.moveToFirst()) SeriesInfo(c.getLong(0), c.getString(1), c.getInt(2) == 1) else null }
    }

    /** 下書きの開始・終了（ミリ秒） */
    private fun times(d: EventDraft, zone: ZoneId): Pair<Long, Long> =
        if (d.allDay) {
            val s = d.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            var endDate = d.end.toLocalDate().plusDays(1)
            if (!endDate.isAfter(d.start.toLocalDate())) endDate = d.start.toLocalDate().plusDays(1)
            s to endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            val s = d.start.atZone(zone).toInstant().toEpochMilli()
            s to maxOf(d.end.atZone(zone).toInstant().toEpochMilli(), s)
        }

    /** 単発の予定の時刻だけを変更（ドラッグ移動・長さ変更） */
    suspend fun updateTimes(eventId: Long, start: Long, end: Long) = withContext(Dispatchers.IO) {
        cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            ContentValues().apply { put(Events.DTSTART, start); put(Events.DTEND, end) }, null, null)
        sync?.localChanged(eventId)
    }

    /** 繰り返しの「この予定のみ」を変更（例外として登録） */
    suspend fun saveOccurrence(d: EventDraft, eventId: Long, instanceBegin: Long, zone: ZoneId) = withContext(Dispatchers.IO) {
        val (s, e) = times(d, zone)
        val exId = cr.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eventId), ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
            put(Events.TITLE, d.title.ifBlank { "(タイトルなし)" })
            put(Events.EVENT_LOCATION, d.location)
            put(Events.DESCRIPTION, io.github.barista9409.calendar.google.AttachmentText.join(d.description, d.attachmentBlock))
            put(Events.DTSTART, s); put(Events.DTEND, e)
            put(Events.ALL_DAY, if (d.allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else zone.id)
            d.colorKey?.let { put(Events.EVENT_COLOR_KEY, it) }
        })?.let { ContentUris.parseId(it) }
        sync?.localChanged(eventId, *listOfNotNull(exId).toLongArray())
    }

    /** 繰り返しの「すべての予定」を変更（シリーズの開始をずらす） */
    suspend fun saveAll(d: EventDraft, eventId: Long, instanceBegin: Long, zone: ZoneId) {
        update(d.copy(eventId = eventId, isRecurring = true), zone)  // タイトル・通知・ゲストなど
        val info = seriesInfo(eventId) ?: return
        val (s, e) = times(d, zone)
        val newStart = info.dtstart + (s - instanceBegin)
        withContext(Dispatchers.IO) {
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), ContentValues().apply {
                put(Events.DTSTART, newStart)
                putNull(Events.DTEND)
                put(Events.DURATION, io.github.barista9409.calendar.util.Recurrence.duration(s, e, d.allDay))
                put(Events.ALL_DAY, if (d.allDay) 1 else 0)
                put(Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else zone.id)
                put(Events.RRULE, d.rrule ?: info.rrule)
            }, null, null)
        }
        sync?.localChanged(eventId)
    }

    /** 繰り返しの「これ以降のすべての予定」を変更（元のシリーズをこの回の前で終え、新しいシリーズを作る） */
    suspend fun saveFollowing(d: EventDraft, eventId: Long, instanceBegin: Long, zone: ZoneId) {
        val info = seriesInfo(eventId) ?: return
        if (instanceBegin <= info.dtstart || info.rrule == null) { saveAll(d, eventId, instanceBegin, zone); return }
        withContext(Dispatchers.IO) {
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), ContentValues().apply {
                put(Events.RRULE, io.github.barista9409.calendar.util.Recurrence.endBefore(info.rrule, instanceBegin, info.allDay))
            }, null, null)
        }
        // 新しいシリーズは元の終了（UNTIL / COUNT）を引き継ぐ（Google カレンダーと同じ）
        val rule = d.rrule ?: info.rrule
        val calId = d.calendarId ?: return
        insert(d.copy(eventId = null, isRecurring = false, rrule = rule, calendarId = calId), zone)
    }

    /** 繰り返しの「これ以降」を削除 */
    suspend fun deleteFollowing(instance: EventInstance) {
        val info = seriesInfo(instance.eventId) ?: return
        if (instance.begin <= info.dtstart || info.rrule == null) { deleteEvent(instance.eventId); return }
        withContext(Dispatchers.IO) {
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, instance.eventId), ContentValues().apply {
                put(Events.RRULE, io.github.barista9409.calendar.util.Recurrence.endBefore(info.rrule, instance.begin, info.allDay))
            }, null, null)
        }
        sync?.localChanged(instance.eventId)
    }

    /** 予定全体（繰り返しならシリーズ全体）を削除 */
    suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        sync?.localChanged(eventId)
    }

    /** 繰り返し予定の1回分だけを削除（例外としてキャンセル扱いを登録） */
    suspend fun deleteOccurrence(instance: EventInstance) = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, instance.eventId)
        val exId = cr.insert(uri, ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, instance.begin)
            put(Events.STATUS, Events.STATUS_CANCELED)
        })?.let { ContentUris.parseId(it) }
        sync?.localChanged(instance.eventId, *listOfNotNull(exId).toLongArray())
    }
}
