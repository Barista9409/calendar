package io.github.barista9409.calendar.google

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Base64
import io.github.barista9409.calendar.data.Prefs
import io.github.barista9409.calendar.model.CalendarInfo

/**
 * Google との連携（添付ファイル用のドライブの権限だけ）。
 * 予定・カレンダーの名前・色は端末のカレンダー同期（OS）がそのまま Google と同期する。
 * カレンダーの名前・色・共有の変更は、Google カレンダーの設定画面を開いて行う（審査が必要な権限を使わないため）。
 */
class GoogleSync(
    private val prefs: Prefs,
    private val auth: GoogleAuth,
) {
    fun isLinked(account: String) = account in prefs.linkedAccounts.value

    /** 連携（必要なら同意画面を表示） */
    suspend fun link(account: String) {
        auth.accessToken(account, interactive = true)
        prefs.setLinked(account, true)
    }

    fun unlink(account: String) = prefs.setLinked(account, false)

    companion object {
        /** Google カレンダー（ウェブ）の、そのカレンダーの設定画面（名前・色・共有・削除） */
        fun calendarSettingsUrl(c: CalendarInfo): String {
            val id = c.apiId ?: c.ownerAccount ?: c.accountName
            val encoded = Base64.encodeToString(id.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return "https://calendar.google.com/calendar/u/0/r/settings/calendar/$encoded?authuser=${Uri.encode(c.accountName)}"
        }

        /** 新しいカレンダーを作る画面 */
        fun createCalendarUrl(account: String) =
            "https://calendar.google.com/calendar/u/0/r/settings/createcalendar?authuser=${Uri.encode(account)}"

        private object Uri { fun encode(s: String): String = android.net.Uri.encode(s) }
    }
}
