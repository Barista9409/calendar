package io.github.barista9409.calendar.data

import android.content.Context
import io.github.barista9409.calendar.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** アプリ内の設定（端末内に保存） */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)

    private val _hidden = MutableStateFlow(readHidden())
    val hiddenCalendars: StateFlow<Set<Long>> = _hidden.asStateFlow()

    private val _accountFilter = MutableStateFlow(sp.getString(KEY_ACCOUNT, null))
    val accountFilter: StateFlow<String?> = _accountFilter.asStateFlow()

    private val _linked = MutableStateFlow(sp.getStringSet(KEY_LINKED, emptySet()).orEmpty().toSet())
    /** Google と連携済みのアカウント */
    val linkedAccounts: StateFlow<Set<String>> = _linked.asStateFlow()

    fun setLinked(account: String, linked: Boolean) {
        val next = if (linked) _linked.value + account else _linked.value - account
        sp.edit().putStringSet(KEY_LINKED, next).apply()
        _linked.value = next
    }

    private fun readHidden(): Set<Long> =
        sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun setCalendarVisible(id: Long, visible: Boolean) {
        val next = if (visible) _hidden.value - id else _hidden.value + id
        sp.edit().putStringSet(KEY_HIDDEN, next.map { it.toString() }.toSet()).apply()
        _hidden.value = next
    }

    fun setAccountFilter(account: String?) {
        sp.edit().putString(KEY_ACCOUNT, account).apply()
        _accountFilter.value = account
    }

    var viewMode: ViewMode
        get() = runCatching { ViewMode.valueOf(sp.getString(KEY_VIEW, ViewMode.MONTH.name)!!) }.getOrDefault(ViewMode.MONTH)
        set(v) = sp.edit().putString(KEY_VIEW, v.name).apply()

    var lastCalendarId: Long
        get() = sp.getLong(KEY_LAST_CAL, -1L)
        set(v) = sp.edit().putLong(KEY_LAST_CAL, v).apply()

    /** ウィジェットの背景色（0 = 端末のテーマに合わせる）と不透明度（%） */
    /** ウィジェットの背景色（0 = 標準＝端末の明るさに合わせる。色は不透明の argb で保存） */
    var widgetColor: Int
        get() = normalizeColor(sp.getInt("widget_color", 0))
        set(v) = sp.edit().putInt("widget_color", v).apply()

    /** 月ウィジェットで 1 日に表示する最大件数（0 = 自動：ウィジェットの大きさに合わせる） */
    var widgetMaxEvents: Int
        get() = sp.getInt("widget_max_events", 0)
        set(v) = sp.edit().putInt("widget_max_events", v).apply()

    /** ウィジェットの予定の文字の大きさ（sp）。小さいほど 1 日に多く表示できる */
    var widgetFontSize: Int
        get() = sp.getInt("widget_font_size", 10)
        set(v) = sp.edit().putInt("widget_font_size", v).apply()

    var widgetOpacity: Int
        get() = sp.getInt("widget_opacity", 100)
        set(v) = sp.edit().putInt("widget_opacity", v).apply()

    private val _hiddenAccounts = MutableStateFlow(sp.getStringSet("hidden_accounts", emptySet()).orEmpty().toSet())
    /** このアプリで使わない（非表示にした）アカウント */
    val hiddenAccounts: StateFlow<Set<String>> = _hiddenAccounts.asStateFlow()

    fun setAccountHidden(account: String, hidden: Boolean) {
        val next = if (hidden) _hiddenAccounts.value + account else _hiddenAccounts.value - account
        sp.edit().putStringSet("hidden_accounts", next).apply()
        _hiddenAccounts.value = next
    }

    /** 初回の「同期の準備」を案内したか */
    var syncSetupAsked: Boolean
        get() = sp.getBoolean("sync_setup_asked", false)
        set(v) = sp.edit().putBoolean("sync_setup_asked", v).apply()

    /** 初回の「通知をどちらで受け取るか」を聞いたか */
    var notifyChoiceAsked: Boolean
        get() = sp.getBoolean("notify_choice_asked", false)
        set(v) = sp.edit().putBoolean("notify_choice_asked", v).apply()

    /** 通知のバイブの長さ（0 = 短い、1 = 普通、2 = 長い） */
    var vibrateLength: Int
        get() = sp.getInt("vibrate_length", 1)
        set(v) = sp.edit().putInt("vibrate_length", v).apply()

    /** 通知を止めるまで鳴らし続ける（最長 5 分） */
    var alertRepeat: Boolean
        get() = sp.getBoolean("alert_repeat", false)
        set(v) = sp.edit().putBoolean("alert_repeat", v).apply()

    /** 通知の種類（ReminderStyle の番号）。0 = スマホの設定に合わせる */
    var reminderStyle: Int
        get() = sp.getInt("reminder_style", 0)
        set(v) = sp.edit().putInt("reminder_style", v).apply()

    /** このアプリで予定のリマインダーを通知する（公式アプリとの二重通知を避けるため初期値はオフ） */
    var appReminders: Boolean
        get() = sp.getBoolean("app_reminders", false)
        set(v) = sp.edit().putBoolean("app_reminders", v).apply()

    /** 添付ファイルの画像・PDF を予定の詳細で大きく表示する */
    private val _attachPreview = MutableStateFlow(sp.getBoolean("attach_preview", true))
    val attachPreview: StateFlow<Boolean> = _attachPreview.asStateFlow()
    fun setAttachPreview(v: Boolean) { sp.edit().putBoolean("attach_preview", v).apply(); _attachPreview.value = v }

    /** 添付ファイルの保存先（アカウントごとのドライブのフォルダ ID と表示用のパス） */
    data class DriveFolder(val id: String, val path: String)

    private val _attachRoots = MutableStateFlow(readRoots())
    val attachRoots: StateFlow<Map<String, DriveFolder>> = _attachRoots.asStateFlow()

    private fun readRoots(): Map<String, DriveFolder> = runCatching {
        val o = org.json.JSONObject(sp.getString("attach_roots", "{}")!!)
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { DriveFolder(it.getString("id"), it.getString("path")) } }
    }.getOrDefault(emptyMap())

    fun setAttachRoot(account: String, folder: DriveFolder?) {
        val next = if (folder == null) _attachRoots.value - account else _attachRoots.value + (account to folder)
        val o = org.json.JSONObject()
        next.forEach { (k, v) -> o.put(k, org.json.JSONObject().put("id", v.id).put("path", v.path)) }
        sp.edit().putString("attach_roots", o.toString()).apply()
        _attachRoots.value = next
    }

    /**
     * アプリの色。色は不透明の argb（0 = 標準）。透明度は不透明度の %。
     * mode：明るさ（0 = 端末に合わせる、1 = ライト、2 = ダーク）
     * menuBg：左から出るメニューの背景（0 = 予定の詳細と同じ）
     * preset：最後に選んだおすすめテーマの番号（-1 = なし、MY_THEME = マイテーマ）
     */
    data class AppTheme(
        val accent: Int = 0,
        val calendarBg: Int = 0, val calendarAlpha: Int = 100,
        val popupBg: Int = 0, val popupAlpha: Int = 100,
        val mode: Int = 0,
        val menuBg: Int = 0, val menuAlpha: Int = 100,
        val preset: Int = -1,
    )

    private fun readTheme(prefix: String) = AppTheme(
        normalizeColor(sp.getInt("${prefix}accent", 0)), normalizeColor(sp.getInt("${prefix}cal_bg", 0)), sp.getInt("${prefix}cal_a", 100),
        normalizeColor(sp.getInt("${prefix}pop_bg", 0)), sp.getInt("${prefix}pop_a", 100), sp.getInt("${prefix}mode", 0),
        normalizeColor(sp.getInt("${prefix}menu_bg", 0)), sp.getInt("${prefix}menu_a", 100), sp.getInt("${prefix}preset", -1),
    )

    private fun writeTheme(prefix: String, t: AppTheme) {
        sp.edit().putInt("${prefix}accent", t.accent).putInt("${prefix}cal_bg", t.calendarBg).putInt("${prefix}cal_a", t.calendarAlpha)
            .putInt("${prefix}pop_bg", t.popupBg).putInt("${prefix}pop_a", t.popupAlpha).putInt("${prefix}mode", t.mode)
            .putInt("${prefix}menu_bg", t.menuBg).putInt("${prefix}menu_a", t.menuAlpha).putInt("${prefix}preset", t.preset).apply()
    }

    private val _theme = MutableStateFlow(readTheme("t_"))

    /** マイテーマ（保存した色の組み合わせ。未保存なら null） */
    private val _myTheme = MutableStateFlow(if (sp.getBoolean("my_saved", false)) readTheme("my_") else null)
    val myTheme: StateFlow<AppTheme?> = _myTheme.asStateFlow()
    fun saveMyTheme(t: AppTheme) { writeTheme("my_", t); sp.edit().putBoolean("my_saved", true).apply(); _myTheme.value = t }
    val appTheme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setAppTheme(t: AppTheme) {
        writeTheme("t_", t)
        _theme.value = t
    }

    private val _weekAutoWidth = MutableStateFlow(sp.getBoolean("week_auto_width", false))
    /** 週表示：予定の重なりが多い日の列を広げる */
    val weekAutoWidth: StateFlow<Boolean> = _weekAutoWidth.asStateFlow()
    fun setWeekAutoWidth(v: Boolean) { sp.edit().putBoolean("week_auto_width", v).apply(); _weekAutoWidth.value = v }

    var sheetFraction: Float
        get() = sp.getFloat("sheet_fraction", 0.5f)
        set(v) = sp.edit().putFloat("sheet_fraction", v).apply()

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    var skippedVersionCode: Int
        get() = sp.getInt(KEY_SKIP, 0)
        set(v) = sp.edit().putInt(KEY_SKIP, v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong(KEY_LAST_CHECK, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_CHECK, v).apply()

    companion object {
        private const val KEY_HIDDEN = "hidden_calendars"
        private const val KEY_LINKED = "linked_accounts"
        /**
         * 以前の保存値（不透明の印なしの rgb）を不透明の argb に直す。0 は「標準」のまま。
         * （以前は黒 = 0 = 標準と区別できず、黒を選んでも選択が残らなかった）
         */
        fun normalizeColor(v: Int): Int = if (v == 0 || (v ushr 24) != 0) v else v or (0xFF shl 24)

        private const val KEY_ACCOUNT = "account_filter"
        private const val KEY_VIEW = "view_mode"
        private const val KEY_LAST_CAL = "last_calendar"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_SKIP = "skip_version"
        private const val KEY_LAST_CHECK = "last_update_check"
    }
}
