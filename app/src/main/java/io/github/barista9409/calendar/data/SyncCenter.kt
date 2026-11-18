package io.github.barista9409.calendar.data

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 同期の進み具合（画面の上の線と「同期が完了しました」の表示に使う） */
data class SyncRun(
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    /** ユーザーが押した同期か（押したときだけ完了を知らせる） */
    val manual: Boolean = false,
    /** 時間内に終わったのを確認できたか */
    val confirmed: Boolean = false,
)

/**
 * スマホの Google カレンダー同期を見守り、確実に動かす。
 *
 * 大事な仕組み（Android 13 以降）：
 * - 同期の設定の読み書きや「このアカウントを同期して」という依頼は、アプリから「見える」アカウントにしか効かない
 *   （見えないと、読み取りは常に「オフ」、依頼は黙って無視される。ContentService の hasAccountAccess）。
 * - アカウントを見えるようにするには、Android 標準の「アカウントの選択」画面で選んでもらう（AccountManager.newChooseAccountIntent）。
 *   Google の審査は不要。
 * - アカウントを指定しない依頼（requestSync(null, …)）は見えなくても通り、カレンダーの全アカウントが同期される。
 * - 動いている同期の一覧（getCurrentSyncs）はアカウントが見えなくても読めるので、進み具合はこれで分かる。
 */
class SyncCenter(private val context: Context) {
    private val sp = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
    private val worker = HandlerThread("sync-watch").apply { start() }
    private val h = Handler(worker.looper)
    private val auth = CalendarContract.AUTHORITY

    private val _run = MutableStateFlow(SyncRun())
    val run: StateFlow<SyncRun> = _run.asStateFlow()
    private val _finished = MutableSharedFlow<SyncRun>(extraBufferCapacity = 4)
    val finished: SharedFlow<SyncRun> = _finished.asSharedFlow()

    private fun acc(name: String) = Account(name, GOOGLE)

    /** スマホに入っている Google アカウント（カレンダーがあるもの） */
    fun googleAccounts(): List<String> = runCatching {
        val out = linkedSetOf<String>()
        context.contentResolver.query(Calendars.CONTENT_URI, arrayOf(Calendars.ACCOUNT_NAME),
            "${Calendars.ACCOUNT_TYPE}=?", arrayOf(GOOGLE), null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { out += it }
        }
        out.toList()
    }.getOrDefault(emptyList())

    /** このアプリから見えるアカウントか（見えないと、同期の設定は読めず、アカウント指定の依頼も効かない） */
    fun hasAccess(account: String): Boolean = runCatching {
        AccountManager.get(context).getAccountsByType(GOOGLE).any { it.name == account }
    }.getOrDefault(false)

    private fun extras(upload: Boolean) = Bundle().apply {
        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        if (upload) putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true)
    }

    /** 今すぐ同期を頼む（自動同期がオフでも 1 回は同期される） */
    fun request(account: String, upload: Boolean = false, manual: Boolean = false) {
        if (hasAccess(account)) runCatching { ContentResolver.requestSync(acc(account), auth, extras(upload)) }
        // 見えないアカウントは、アカウントを指定せずに頼む（こちらは必ず届く）
        else runCatching { ContentResolver.requestSync(null, auth, extras(upload)) }
        sp.edit().putLong("req_$account", System.currentTimeMillis()).apply()
        begin(manual)
    }

    /** すべてのアカウントを今すぐ同期 */
    fun requestAll(upload: Boolean = false, manual: Boolean = false) {
        runCatching { ContentResolver.requestSync(null, auth, extras(upload)) }
        val now = System.currentTimeMillis()
        val e = sp.edit()
        googleAccounts().forEach { e.putLong("req_$it", now) }
        e.apply()
        begin(manual)
    }

    /** アプリを開いたときなど。前回頼んでから minGapMs 以上たっていれば同期を頼む */
    fun requestIfStale(minGapMs: Long = 5 * 60_000L) {
        val now = System.currentTimeMillis()
        val accounts = googleAccounts()
        if (accounts.any { now - maxOf(lastRequestAt(it), lastSyncAt(it)) >= minGapMs }) requestAll()
    }

    private val sendLater = Runnable { requestAll(upload = true) }

    /** このアプリで予定を変えた直後に呼ぶ。変えた予定を「送信待ち」として覚え、少し待ってまとめて送信を頼む */
    fun localChanged(vararg eventIds: Long) {
        // このアプリが書き換えた時刻（直後のカレンダーの変化は「Google から届いた」と数えない）
        sp.edit().putLong("last_local_write", System.currentTimeMillis()).apply()
        if (eventIds.isNotEmpty()) synchronized(this) {
            val now = System.currentTimeMillis()
            val m = readEdits().toMutableMap()
            eventIds.filter { it > 0 }.forEach { if (it !in m) m[it] = now }
            writeEdits(m)
        }
        h.removeCallbacks(sendLater)
        h.postDelayed(sendLater, 1500)
    }

    // ---------------- このアプリで変えた予定の送信待ち ----------------

    private fun readEdits(): Map<Long, Long> = sp.getStringSet("pending_edits", emptySet()).orEmpty().mapNotNull { s ->
        val p = s.split(':'); val id = p.getOrNull(0)?.toLongOrNull(); val t = p.getOrNull(1)?.toLongOrNull()
        if (id != null && t != null) id to t else null
    }.toMap()

    private fun writeEdits(m: Map<Long, Long>) {
        sp.edit().putStringSet("pending_edits", m.map { "${it.key}:${it.value}" }.toSet()).apply()
    }

    /**
     * このアプリで変えて、まだ Google に届いていない予定（予定の ID → 変えた時刻）。
     * 同期機能が送ると DIRTY が 0 になる（削除した予定は行ごと消える）ので、そうなったものは外す。
     * 外したものがあれば「同期できた」と記録する。
     */
    @Synchronized fun pendingEdits(): Map<Long, Long> {
        val m = readEdits()
        if (m.isEmpty()) return m
        val still = HashMap<Long, Long>()
        val ok = runCatching {
            context.contentResolver.query(Events.CONTENT_URI, arrayOf(Events._ID, Events.DIRTY, Events.ACCOUNT_TYPE),
                "${Events._ID} IN (${m.keys.joinToString(",")})", null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(1) == 1 && c.getString(2) == GOOGLE) c.getLong(0).let { still[it] = m.getValue(it) }
                }
            } != null
        }.getOrDefault(false)
        if (!ok) return m
        if (still.size < m.size) { writeEdits(still); markSynced() }
        return still
    }

    // ---------------- Google から受け取れたか ----------------

    /**
     * 同期機能はカレンダーごとの同期用の欄（CAL_SYNC1〜10：次に取りにいく位置など）を書き換える。
     * 前に見たときと変わっていれば、その間に Google と同期できたとわかる（アプリを閉じていた間の同期も）。
     */
    @Synchronized fun checkRemoteSync(): Boolean {
        val fp = runCatching {
            val cols = arrayOf(Calendars._ID, Calendars.CAL_SYNC1, Calendars.CAL_SYNC2, Calendars.CAL_SYNC3, Calendars.CAL_SYNC4,
                Calendars.CAL_SYNC5, Calendars.CAL_SYNC6, Calendars.CAL_SYNC7, Calendars.CAL_SYNC8, Calendars.CAL_SYNC9, Calendars.CAL_SYNC10)
            val sb = StringBuilder()
            context.contentResolver.query(Calendars.CONTENT_URI, cols, "${Calendars.ACCOUNT_TYPE}=?", arrayOf(GOOGLE),
                Calendars._ID)?.use { c ->
                while (c.moveToNext()) for (i in 0 until c.columnCount) {
                    sb.append(when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(i).contentHashCode().toString()
                        android.database.Cursor.FIELD_TYPE_NULL -> "-"
                        else -> c.getString(i)
                    }).append('|')
                }
            }
            "${sb.length}:${sb.toString().hashCode()}"
        }.getOrNull() ?: return false
        val old = sp.getString("remote_fp", null)
        if (old == fp) return false
        sp.edit().putString("remote_fp", fp).apply()
        if (old != null) markSynced()
        return old != null
    }

    /**
     * カレンダーが書き換わった（changedAt）。このアプリが直前に書き換えたのでなければ、
     * Google の同期機能（端末によっては Google カレンダーアプリ自身）が届けた変更なので「同期した」と記録する。
     */
    fun noteProviderChange(changedAt: Long = System.currentTimeMillis()) {
        val mine = sp.getLong("last_local_write", 0L)
        if (SyncProgress.isExternalChange(changedAt, mine)) markSynced()
    }

    /** 同期できたのを確認した時刻を記録 */
    private fun markSynced() {
        val now = System.currentTimeMillis()
        val e = sp.edit().putLong("last_sync", now)
        googleAccounts().forEach { e.putLong("done_$it", now) }
        e.apply()
    }

    /** 最後に同期できたのを確認した時刻（全アカウント） */
    fun lastSyncAll(): Long = sp.getLong("last_sync", 0L)
    fun lastRequestAll(): Long = googleAccounts().maxOfOrNull { lastRequestAt(it) } ?: 0L

    /** 画面の判断に使う、実際に起きたこと */
    fun facts(): SyncFacts {
        checkRemoteSync()
        val pend = pendingEdits()
        val now = System.currentTimeMillis()
        return SyncFacts(
            masterOn = runCatching { ContentResolver.getMasterSyncAutomatically() }.getOrDefault(true),
            running = _run.value.running || calendarSyncRunning(),
            pending = pend.size,
            stuck = pend.values.count { now - it >= SyncSummary.STUCK_MS },
            lastSyncAt = lastSyncAll(),
            lastRequestAt = lastRequestAll(),
        )
    }

    /** そのアカウントのカレンダー自動同期をオンにする（アカウントが見えているときだけ効く） */
    fun turnOnAccountSync(account: String): Boolean {
        val a = acc(account)
        runCatching { if (ContentResolver.getIsSyncable(a, auth) == 0) ContentResolver.setIsSyncable(a, auth, 1) }
        runCatching { ContentResolver.setSyncAutomatically(a, auth, true) }
        request(account, manual = true)
        return runCatching { ContentResolver.getSyncAutomatically(a, auth) }.getOrDefault(false)
    }

    /** スマホ全体の自動同期をオンにする */
    fun turnOnMasterSync(): Boolean {
        runCatching { ContentResolver.setMasterSyncAutomatically(true) }
        requestAll(manual = true)
        return runCatching { ContentResolver.getMasterSyncAutomatically() }.getOrDefault(false)
    }

    /** まだ Google に送っていない変更の数（読み取れないときは null） */
    fun dirtyCount(account: String? = null): Int? = runCatching {
        val sel = "${Events.DIRTY}=1 AND ${Events.ACCOUNT_TYPE}=?" + if (account != null) " AND ${Events.ACCOUNT_NAME}=?" else ""
        val args = if (account != null) arrayOf(GOOGLE, account) else arrayOf(GOOGLE)
        context.contentResolver.query(Events.CONTENT_URI, arrayOf(Events._ID), sel, args, null)?.use { it.count }
    }.getOrNull()

    /** カレンダーの同期がいま動いているか（アカウントが見えなくても分かる） */
    fun calendarSyncRunning(): Boolean = runCatching {
        ContentResolver.getCurrentSyncs().any { it.authority == auth }
    }.getOrDefault(false)

    fun lastSyncAt(account: String) = sp.getLong("done_$account", 0L)
    fun lastRequestAt(account: String) = sp.getLong("req_$account", 0L)

    fun status(account: String, unsynced: Int = 0): SyncStatus {
        val a = acc(account)
        val access = hasAccess(account)
        val running = calendarSyncRunning()
        return SyncStatus(
            account = account,
            masterOn = runCatching { ContentResolver.getMasterSyncAutomatically() }.getOrDefault(true),
            accountOn = if (access) runCatching { ContentResolver.getSyncAutomatically(a, auth) }.getOrDefault(true) else true,
            active = running || (access && runCatching { ContentResolver.isSyncActive(a, auth) }.getOrDefault(false)),
            pending = access && runCatching { ContentResolver.isSyncPending(a, auth) }.getOrDefault(false),
            unsynced = unsynced,
            syncable = if (access) runCatching { ContentResolver.getIsSyncable(a, auth) }.getOrDefault(1) else 1,
            dirty = dirtyCount(account) ?: 0,
            lastSyncAt = lastSyncAt(account),
            lastRequestAt = lastRequestAt(account),
            access = access,
        )
    }

    // ---------------- 進み具合の見守り ----------------

    @Volatile private var lastChangeAt = 0L
    @Volatile private var sawRunning = false
    private var started = false

    /** アプリが動いている間、同期の始まり・終わりとカレンダーの書き換えを見守る */
    fun startWatching() {
        if (started) return
        started = true
        runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, object : ContentObserver(h) {
                override fun onChange(selfChange: Boolean) {
                    lastChangeAt = System.currentTimeMillis()
                    // 書き込みが落ち着いたら、同期で届いたかを確かめる
                    h.removeCallbacks(checkLater); h.postDelayed(checkLater, 3000)
                }
            })
        }
        val mask = ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_PENDING
        runCatching { ContentResolver.addStatusChangeListener(mask) { h.post { tick() } } }
    }

    // 書き込みが落ち着いてから判定する（このアプリの書き込みは、その後に「自分で書いた」と記録されるので、少し待つ）
    private val checkLater = Runnable { checkRemoteSync(); pendingEdits(); noteProviderChange(lastChangeAt) }

    private fun begin(manual: Boolean) {
        h.post {
            val cur = _run.value
            if (cur.running) {
                if (manual && !cur.manual) _run.value = cur.copy(manual = true)
                return@post
            }
            sawRunning = false
            _run.value = SyncRun(running = true, startedAt = System.currentTimeMillis(), manual = manual)
            h.removeCallbacks(ticker)
            h.postDelayed(ticker, 1000)
        }
    }

    private val ticker = object : Runnable {
        override fun run() { if (tick()) h.postDelayed(this, 1000) }
    }

    /** 進み具合を判定する。続けるなら true */
    private fun tick(): Boolean {
        val cur = _run.value
        if (!cur.running) return false
        val now = System.currentTimeMillis()
        val running = calendarSyncRunning()
        if (running) sawRunning = true
        val elapsed = now - cur.startedAt
        val quiet = now - lastChangeAt >= 2500
        // 長いあいだ届いていない変更（別の問題）で、同期の完了がいつまでも分からなくならないように、最近の変更だけ見る
        val pend = pendingEdits().values.count { now - it < SyncSummary.STUCK_MS }
        val changed = lastChangeAt > cur.startedAt || checkRemoteSync()
        val done = SyncProgress.isDone(elapsed, running, sawRunning, changed, quiet, pend)
        val timeout = elapsed >= SyncProgress.TIMEOUT_MS
        if (!done && !timeout) return true
        val fin = cur.copy(running = false, finishedAt = now, confirmed = done)
        if (done) markSynced()
        _run.value = fin
        _finished.tryEmit(fin)
        return false
    }

    companion object { const val GOOGLE = "com.google" }
}

/** 同期が終わったかの判定（テストしやすいように分けている） */
object SyncProgress {
    /** これだけ待っても同期の動きが見えなければ、見守りを終える（端末によっては頼んでも動きが見えない） */
    const val TIMEOUT_MS = 20_000L

    /** このアプリが書き換えてから 15 秒以上あとのカレンダーの変化は、外（Google）から届いたもの */
    fun isExternalChange(changedAt: Long, lastLocalWrite: Long): Boolean = changedAt - lastLocalWrite > 15_000

    /**
     * - 同期が動いているのを見て、それが止まった
     * - または、頼んだあとにカレンダーが書き換わり、2.5 秒静かになった
     * のどちらかで、このアプリで変えた予定がすべて届いていれば完了
     */
    fun isDone(elapsedMs: Long, running: Boolean, sawRunning: Boolean, changedAfterStart: Boolean, quiet: Boolean, pending: Int): Boolean {
        if (elapsedMs < 2000 || running || pending > 0) return false
        return sawRunning || (changedAfterStart && quiet)
    }
}
