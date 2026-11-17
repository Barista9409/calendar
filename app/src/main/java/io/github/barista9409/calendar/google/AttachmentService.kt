package io.github.barista9409.calendar.google

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract.Events
import android.provider.OpenableColumns
import io.github.barista9409.calendar.data.Prefs
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OpenInBrowserException(val url: String) : Exception("ブラウザで開きます")

/**
 * 添付ファイル。ファイルは自分の Google ドライブの「カレンダー添付ファイル」フォルダ（アプリが作る）に保存し、
 * 「リンクを知っている人は閲覧可」にして、予定の説明にリンクを書き込む。
 * 予定の説明は端末のカレンダー経由で書き換えるので Google カレンダーにも同期され、家族も Google カレンダーアプリから開ける。
 * 使う Google の権限は「このアプリが作ったファイルだけ」（drive.file）なので、審査もテストユーザーの登録もいらない。
 */
class AttachmentService(
    private val context: Context,
    private val drive: DriveApi,
    val store: AttachmentStore,
    private val prefs: Prefs,
) {
    // ---------------- 予定の説明の読み書き（端末のカレンダー） ----------------

    private suspend fun description(eventId: Long): String? = withContext(Dispatchers.IO) {
        context.contentResolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.DESCRIPTION),
            null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private suspend fun setDescription(eventId: Long, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            ContentValues().apply { put(Events.DESCRIPTION, text) }, null, null)
        // 添付のリンクを Google に送る
        runCatching { (context.applicationContext as io.github.barista9409.calendar.CalendarApp).container.sync.localChanged() }
        Unit
    }

    /** 予定の添付ファイル一覧（説明から読む。ネットワーク不要） */
    suspend fun list(e: EventInstance): List<Attachment> = AttachmentText.parse(description(e.eventId) ?: e.description)

    /** ドライブに保存して、予定の説明にリンクを書き込む（繰り返し予定はすべての回に付く） */
    suspend fun attach(cal: CalendarInfo, e: EventInstance, uri: Uri, onProgress: (Float) -> Unit): Attachment =
        attach(cal, e.eventId, uri, eventDate(e.begin, e.allDay, e.timeZone) to e.title, onProgress)

    /** 予定の ID を指定して添付（入力画面で選んだファイルを、保存した直後に付けるとき） */
    suspend fun attach(cal: CalendarInfo, eventId: Long, uri: Uri,
                       label: Pair<java.time.LocalDate, String>? = null, onProgress: (Float) -> Unit): Attachment {
        val account = cal.accountName
        val (name, size) = describe(uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val folder = rootFolder(account)
        // ファイル名は「予定の日付_予定の名前_元のファイル名」（ドライブで日付順に並び、どの予定のものか分かる）
        val (date, title) = label ?: eventLabel(eventId) ?: (java.time.LocalDate.now() to "")
        val file = drive.upload(account, fileName(date, title, name), mime, folder, mapOf(KEY_FILE to "1"),
            size, { requireNotNull(context.contentResolver.openInputStream(uri)) { "ファイルを読み込めません" } }, onProgress)
        // リンクを知っている人は閲覧可（家族が Google カレンダーアプリやこのアプリから開けるように）
        runCatching { drive.shareAnyoneWithLink(account, file.id) }
        runCatching { store.save(file.id, file.name) { requireNotNull(context.contentResolver.openInputStream(uri)) } }
        val att = Attachment(file.id, file.name, file.mimeType, AttachmentText.fileUrl(file.id))
        setDescription(eventId, AttachmentText.withAdded(description(eventId), att))
        return att
    }

    /** 予定の日付と名前（端末のカレンダーから読む） */
    private suspend fun eventLabel(eventId: Long): Pair<java.time.LocalDate, String>? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                arrayOf(Events.DTSTART, Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.TITLE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) null
                else eventDate(c.getLong(0), c.getInt(1) == 1, c.getString(2)) to (c.getString(3) ?: "")
            }
        }.getOrNull()
    }

    /** 予定を消すときに、添付したファイルもドライブのゴミ箱へ移す（30 日間は復元可能） */
    suspend fun trashFiles(account: String, items: List<Attachment>) {
        if (!prefs.linkedAccounts.value.contains(account)) return
        items.forEach { a -> a.fileId?.let { id -> runCatching { drive.trash(account, id) }; store.delete(id, a.title) } }
    }

    /** 予定から外す。自分が保存したファイルならドライブのゴミ箱へ（30 日間は復元可能） */
    suspend fun remove(cal: CalendarInfo, e: EventInstance, att: Attachment) {
        setDescription(e.eventId, AttachmentText.withRemoved(description(e.eventId), att))
        att.fileId?.let { id ->
            if (prefs.linkedAccounts.value.contains(cal.accountName)) runCatching { drive.trash(cal.accountName, id) }
            store.delete(id, att.title)
        }
    }

    /**
     * 開くための端末内ファイル。キャッシュ → 連携済みならドライブから → 公開リンクから、の順に試し、
     * どれも駄目なら OpenInBrowserException（ブラウザ・ドライブアプリで開く）。
     */
    suspend fun localFile(account: String, att: Attachment): File {
        val id = att.fileId ?: throw OpenInBrowserException(att.fileUrl)
        store.find(id, att.title)?.let { return it }
        val f = store.file(id, att.title)
        if (prefs.linkedAccounts.value.contains(account)) {
            if (runCatching { drive.download(account, id, f) }.isSuccess && f.length() > 0) return f
        }
        if (runCatching { drive.downloadPublic(id, f) }.isSuccess && f.length() > 0) return f
        f.delete()
        throw OpenInBrowserException(att.fileUrl)
    }

    fun isCached(att: Attachment): Boolean = att.fileId?.let { store.find(it, att.title) } != null

    // ---------------- 保存先（アプリが作るフォルダ） ----------------

    /** マイドライブの「カレンダー添付ファイル」フォルダ（なければ作る）。ドライブ上で移動・名前変更しても使い続ける */
    suspend fun rootFolder(account: String): String {
        prefs.attachRoots.value[account]?.id?.let { known ->
            if (runCatching { drive.findFolder(account, KEY_ROOT, "1") == known }.getOrDefault(false)) return known
        }
        val id = drive.findFolder(account, KEY_ROOT, "1")
            ?: drive.createFolder(account, ROOT_NAME, null, mapOf(KEY_ROOT to "1"))
        // フォルダごと「リンクを知っている人は閲覧可」にしておく（中のファイルにも引き継がれる）
        runCatching { drive.shareAnyoneWithLink(account, id) }
        prefs.setAttachRoot(account, Prefs.DriveFolder(id, ROOT_NAME))
        return id
    }

    fun folderUrl(account: String): String? = prefs.attachRoots.value[account]?.id?.let { "https://drive.google.com/drive/folders/$it" }

    private suspend fun describe(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        var name = "file"; var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        name to size
    }

    companion object {
        const val ROOT_NAME = "カレンダー添付ファイル"
        private const val KEY_ROOT = "calendarAppRootV2"
        private const val KEY_FILE = "calendarAppAttachment"

        /** 予定の日付（終日の予定は UTC の 0 時で入っているので UTC で読む） */
        fun eventDate(millis: Long, allDay: Boolean, tz: String?): java.time.LocalDate {
            val zone = if (allDay) java.time.ZoneOffset.UTC
                else runCatching { java.time.ZoneId.systemDefault() }.getOrDefault(java.time.ZoneOffset.UTC)
            return java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        }

        /** ドライブに保存するファイル名：「2026-12-24_面談_IMG_0001.jpg」 */
        fun fileName(date: java.time.LocalDate, title: String, original: String): String {
            val t = title.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(40).trim('_')
            val head = if (t.isBlank()) "$date" else "${date}_$t"
            return safeName("${head}_$original")
        }

        fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "file" }
    }
}
