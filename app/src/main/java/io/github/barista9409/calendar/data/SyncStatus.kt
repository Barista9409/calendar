package io.github.barista9409.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** アカウントごとの、スマホのカレンダー同期の状態（読み取りは SyncCenter.status） */
data class SyncStatus(
    val account: String,
    /** スマホ全体の自動同期 */
    val masterOn: Boolean,
    /** このアカウントのカレンダーの自動同期 */
    val accountOn: Boolean,
    val active: Boolean,
    val pending: Boolean,
    /** スマホに同期していないカレンダーの数 */
    val unsynced: Int = 0,
    /** 同期の対象か（0 ＝ 対象外、負 ＝ 未確定） */
    val syncable: Int = 1,
    /** まだ Google に送っていない変更の数 */
    val dirty: Int = 0,
    /** 最後に同期が終わった時刻（このアプリが見ていたときだけ記録。0 ＝ 不明） */
    val lastSyncAt: Long = 0L,
    /** 最後に同期を頼んだ時刻 */
    val lastRequestAt: Long = 0L,
    /** このアプリからアカウントが見えるか（見えないと、アカウントごとの同期の設定は読めない） */
    val access: Boolean = true,
) {
    enum class Fix { NONE, MASTER, ACCESS, ACCOUNT }

    /** アカウントの自動同期がオフと判断するか（見えないアカウントは判断しない） */
    private val accountOff get() = access && (!accountOn || syncable == 0)

    /** アプリ内で直せる問題 */
    val fix: Fix get() = when {
        !masterOn -> Fix.MASTER
        accountOff -> Fix.ACCOUNT
        !access -> Fix.ACCESS
        else -> Fix.NONE
    }

    /** 状態の説明（画面に出す文） */
    val message: String get() = when {
        !masterOn -> "スマホ全体の自動同期がオフです。このままでは Google カレンダーの変更が自動では届きません"
        accountOff -> "このアカウントのカレンダーの自動同期がオフです"
        active -> "同期しています…"
        !access -> "このアプリからは、このアカウントの同期の設定が見えません。［見られるようにする］でアカウントを選ぶと、状態の確認と設定ができます（同期はできます）"
        pending -> "同期の順番を待っています…"
        dirty > 0 -> "まだ Google に送っていない変更が $dirty 件あります"
        unsynced > 0 -> "同期はオンです。ただし、スマホに同期していないカレンダーが $unsynced 個あります（下に表示）"
        else -> "同期はオンです（Google カレンダーと自動でそろいます）"
    }

    /** 赤い警告にするか（見えないだけのときは赤くしない） */
    val isError: Boolean get() = fix == Fix.MASTER || fix == Fix.ACCOUNT

    /** 「最後の同期」の表示 */
    fun lastLine(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        fun fmt(ms: Long): String {
            val t = Instant.ofEpochMilli(ms).atZone(zone)
            val d = t.toLocalDate()
            val hm = t.format(DateTimeFormatter.ofPattern("H:mm"))
            return when (d) {
                today -> "今日 $hm"
                today.minusDays(1) -> "昨日 $hm"
                else -> "${d.monthValue}/${d.dayOfMonth} $hm"
            }
        }
        return when {
            lastSyncAt > 0 -> "最後の同期：${fmt(lastSyncAt)}"
            lastRequestAt > 0 -> "同期を頼んだ時刻：${fmt(lastRequestAt)}"
            else -> "最後の同期：まだ確認していません"
        }
    }

    /** 詳しい状態（画面の「詳しく」に出す。問い合わせのときにこの画面を見れば原因が分かるように） */
    val details: List<Pair<String, String>> get() = listOf(
        "スマホ全体の自動同期" to onOff(masterOn),
        "このアプリからアカウントが見えるか" to yesNo(access),
        "このアカウントのカレンダー自動同期" to (if (access) onOff(accountOn) else "見えないため不明"),
        "同期の対象" to when { !access -> "見えないため不明"; syncable > 0 -> "はい"; syncable == 0 -> "いいえ"; else -> "未確定" },
        "いま同期中" to yesNo(active),
        "同期の順番待ち" to yesNo(pending),
        "Google に未送信の変更" to "$dirty 件",
        "スマホに同期していないカレンダー" to "$unsynced 個",
    )

    private fun onOff(b: Boolean) = if (b) "オン" else "オフ"
    private fun yesNo(b: Boolean) = if (b) "はい" else "いいえ"
}

/** 同期画面の一番上に出す、全アカウントまとめた状態 */
data class SyncSummary(val kind: Kind, val title: String, val sub: String, val pending: Int = 0) {
    enum class Kind { OK, SYNCING, PENDING, STOPPED, CHECK }

    /** ［直す］を出すか */
    val needsFix: Boolean get() = kind == Kind.STOPPED || kind == Kind.CHECK

    companion object {
        fun of(statuses: List<SyncStatus>, running: Boolean, lastLine: String): SyncSummary {
            val dirty = statuses.sumOf { it.dirty }
            return when {
                statuses.any { !it.masterOn } -> SyncSummary(Kind.STOPPED, "同期が止まっています",
                    "スマホ全体の自動同期がオフです。［直す］でオンにできます")
                statuses.any { it.fix == SyncStatus.Fix.ACCOUNT } -> SyncSummary(Kind.STOPPED, "同期が止まっています",
                    "Google カレンダーと同期していないアカウントがあります：" +
                        statuses.filter { it.fix == SyncStatus.Fix.ACCOUNT }.joinToString("、") { it.account })
                running -> SyncSummary(Kind.SYNCING, "同期しています…", lastLine)
                statuses.any { it.fix == SyncStatus.Fix.ACCESS } -> SyncSummary(Kind.CHECK, "同期の状態を確認できません",
                    "［直す］でアカウントを選ぶと、確認と設定ができます（同期はしています）")
                dirty > 0 -> SyncSummary(Kind.PENDING, "送信待ち $dirty 件", "同期すると Google に届きます。$lastLine", dirty)
                else -> SyncSummary(Kind.OK, "最新です", lastLine)
            }
        }
    }
}
