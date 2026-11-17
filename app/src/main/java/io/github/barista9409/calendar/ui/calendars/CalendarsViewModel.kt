package io.github.barista9409.calendar.ui.calendars

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.model.CalendarInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import io.github.barista9409.calendar.data.SyncStatus
import io.github.barista9409.calendar.data.SyncSummary

/** カレンダー管理画面（表示・非表示、Google 連携） */
class CalendarsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val google = c.google

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()
    val linked: StateFlow<Set<String>> = c.prefs.linkedAccounts

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** スマホに同期していないカレンダー (id, 名前, アカウント) */
    private val _unsynced = MutableStateFlow<List<Triple<Long, String, String>>>(emptyList())
    val unsynced: StateFlow<List<Triple<Long, String, String>>> = _unsynced.asStateFlow()

    /** アカウントごとの同期の状態（画面を開いている間、数秒ごとに更新） */
    private val _sync = MutableStateFlow<Map<String, io.github.barista9409.calendar.data.SyncStatus>>(emptyMap())
    val sync: StateFlow<Map<String, io.github.barista9409.calendar.data.SyncStatus>> = _sync.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            while (true) { readSync(); kotlinx.coroutines.delay(3000) }
        }
    }

    private suspend fun readSync() {
        val uns = c.repository.unsyncedCalendars()
        _unsynced.value = uns
        val accounts = (_calendars.value.filter { it.isGoogle }.map { it.accountName } + uns.map { it.third }).distinct()
        _sync.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            accounts.associateWith { a -> c.sync.status(a, unsynced = uns.count { it.third == a }) }
        }
    }

    fun reload() { viewModelScope.launch { _calendars.value = c.repository.calendars(includeHiddenAccounts = true); readSync() } }

    /** スマホに同期していないカレンダーを同期する */
    fun enableSync(id: Long, name: String, account: String) = run("「$name」を同期します。少しすると予定が表示されます") {
        c.repository.enableSync(id)
        c.sync.request(account, manual = true)
    }

    val hiddenAccounts: StateFlow<Set<String>> = c.prefs.hiddenAccounts
    fun setAccountHidden(account: String, hidden: Boolean) {
        c.prefs.setAccountHidden(account, hidden)
        _messages.tryEmit(if (hidden) "$account をこのアプリで非表示にしました" else "$account を表示しました")
    }

    private fun run(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
                success?.let { _messages.tryEmit(it) }
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "エラーが発生しました")
            } finally {
                _busy.value = false
                reload()
            }
        }
    }

    fun link(account: String) = run("Google と連携しました（添付ファイルが使えます）") { google.link(account) }
    fun unlink(account: String) { google.unlink(account); _messages.tryEmit("連携を解除しました") }
    /** 今すぐ同期（全アカウント。自動同期がオフでも 1 回は同期される） */
    fun syncNow() = run {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.sync.requestAll(upload = true, manual = true) }
    }

    /** 同期の進み具合（上の線）と完了の知らせ */
    val syncRun = c.sync.run

    /** 一番上のまとめ（このアプリで非表示のアカウントは除く） */
    val summary: StateFlow<SyncSummary> = combine(_sync, c.sync.run, c.prefs.hiddenAccounts) { m, run, hidden ->
        val list = m.values.filter { it.account !in hidden }
        val last = SyncStatus("", true, true, false, false,
            lastSyncAt = list.maxOfOrNull { it.lastSyncAt } ?: 0L,
            lastRequestAt = list.maxOfOrNull { it.lastRequestAt } ?: 0L).lastLine()
        SyncSummary.of(list, run.running, last)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncSummary(SyncSummary.Kind.OK, "確認しています…", ""))

    init {
        viewModelScope.launch {
            c.sync.finished.collect { r ->
                readSync()
                if (r.manual) _messages.tryEmit(syncDoneMessage(r))
            }
        }
    }

    // ---------------- ［直す］：まとめて直す ----------------

    /** 画面にしてほしいこと */
    sealed interface FixStep {
        /** スマホ全体の自動同期をオンにしてよいか聞く */
        data object AskMaster : FixStep
        /** このアカウントを Android 標準の画面で選んでもらう（アプリから見えるようにする） */
        data class Choose(val account: String) : FixStep
    }

    private val _fixStep = MutableStateFlow<FixStep?>(null)
    val fixStep: StateFlow<FixStep?> = _fixStep.asStateFlow()
    private var chooseQueue = ArrayDeque<String>()

    /** ［直す］ */
    fun fixAll() {
        viewModelScope.launch {
            readSync()
            val list = _sync.value.values.filter { it.account !in c.prefs.hiddenAccounts.value }
            chooseQueue = ArrayDeque(list.filter { !it.access }.map { it.account })
            if (list.any { !it.masterOn }) _fixStep.value = FixStep.AskMaster else nextFixStep()
        }
    }

    fun answerMaster(ok: Boolean) {
        _fixStep.value = null
        viewModelScope.launch {
            if (ok) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.sync.turnOnMasterSync() }
            nextFixStep()
        }
    }

    /** アカウントを選んだ（またはやめた）後 */
    fun chosen() { _fixStep.value = null; nextFixStep() }

    /** 選んでもらう画面をやめたとき */
    fun cancelFix() { _fixStep.value = null; chooseQueue.clear() }

    private fun nextFixStep() {
        val next = chooseQueue.removeFirstOrNull()
        if (next != null) { _fixStep.value = FixStep.Choose(next); return }
        // 見えるアカウントは、カレンダーの同期をオンにして、すぐ同期する
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val accounts = _sync.value.keys.filter { it !in c.prefs.hiddenAccounts.value }
                accounts.filter { c.sync.hasAccess(it) }.forEach { c.sync.turnOnAccountSync(it) }
                c.sync.requestAll(upload = true, manual = true)
            }
            readSync()
            val left = _sync.value.values.filter { it.account !in c.prefs.hiddenAccounts.value && (it.isError || !it.access) }
            _messages.tryEmit(if (left.isEmpty()) "同期の設定を直して、同期を始めました"
                else "直せなかったアカウントがあります：${left.joinToString("、") { it.account }}（︙ →「困ったとき」から確認できます）")
        }
    }

    fun setVisible(cal: CalendarInfo, visible: Boolean) = c.prefs.setCalendarVisible(cal.id, visible)
}

/** 同期が終わったときの知らせ */
fun syncDoneMessage(r: io.github.barista9409.calendar.data.SyncRun): String {
    val t = java.time.Instant.ofEpochMilli(r.finishedAt).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))
    return if (r.confirmed) "同期が完了しました（$t）"
    else "同期を頼みました。Google 側の都合で時間がかかることがあります。届きしだい自動で表示されます"
}
