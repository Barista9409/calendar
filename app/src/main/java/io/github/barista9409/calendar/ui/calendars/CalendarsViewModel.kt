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
import io.github.barista9409.calendar.data.SyncFacts
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

    /** 実際に起きたこと（画面の判断はこれだけで行う） */
    private val _facts = MutableStateFlow<SyncFacts?>(null)

    private suspend fun readSync() {
        _facts.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.sync.facts() }
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

    /** 一番上のまとめ（設定の値ではなく、実際に同期できたかで判断） */
    val summary: StateFlow<SyncSummary?> = combine(_facts, c.sync.run) { f, run ->
        f?.let { SyncSummary.of(it.copy(running = it.running || run.running)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            c.sync.finished.collect { r ->
                readSync()
                if (r.manual) _messages.tryEmit(syncDoneMessage(r))
            }
        }
    }

    // ---------------- まとめの 1 枚のボタン ----------------

    /** スマホ全体の自動同期をオンにしてよいか聞いている */
    private val _askMaster = MutableStateFlow(false)
    val askMaster: StateFlow<Boolean> = _askMaster.asStateFlow()

    /** まとめのボタン（オンにする／もう一度送る／今すぐ同期） */
    fun summaryAction() {
        when (summary.value?.kind) {
            SyncSummary.Kind.MASTER_OFF -> _askMaster.value = true
            SyncSummary.Kind.STUCK -> resend()
            else -> syncNow()
        }
    }

    fun answerMaster(ok: Boolean) {
        _askMaster.value = false
        if (!ok) return
        run("自動同期をオンにして、同期を始めました") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.sync.turnOnMasterSync() }
        }
    }

    /** 送れていない変更を、もう一度送る（念のため、アプリから見えるアカウントは自動同期もオンにする） */
    private fun resend() = run("もう一度送っています。届くと「送信待ち」が消えます") {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            c.sync.googleAccounts().filter { c.sync.hasAccess(it) }.forEach { runCatching { c.sync.turnOnAccountSync(it) } }
            c.sync.requestAll(upload = true, manual = true)
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
