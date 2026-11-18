package io.github.barista9409.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * アカウントごとに、スマホが返す同期の設定の値（「困ったとき」に参考として出すだけ）。
 * 機種によっては実際と違う値を返す（同期できているのに「オフ」など）ので、画面の判断には使わない。
 */
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
    /** まだ Google に送っていない変更の数（このアカウントの全部。ほかのアプリでの変更も含む） */
    val dirty: Int = 0,
    val lastSyncAt: Long = 0L,
    val lastRequestAt: Long = 0L,
    /** このアプリからアカウントが見えるか */
    val access: Boolean = true,
) {
    /** 詳しい状態（「困ったとき」に出す） */
    val details: List<Pair<String, String>> get() = listOf(
        "スマホ全体の自動同期" to onOff(masterOn),
        "このアプリからアカウントが見えるか" to yesNo(access),
        "このアカウントのカレンダー自動同期" to (if (access) onOff(accountOn) else "不明"),
        "同期の対象" to when { !access -> "不明"; syncable > 0 -> "はい"; syncable == 0 -> "いいえ"; else -> "未確定" },
        "いま同期中" to yesNo(active),
        "同期の順番待ち" to yesNo(pending),
        "Google に未送信の変更（ほかのアプリの分も含む）" to "$dirty 件",
        "スマホに同期していないカレンダー" to "$unsynced 個",
    )

    private fun onOff(b: Boolean) = if (b) "オン" else "オフ"
    private fun yesNo(b: Boolean) = if (b) "はい" else "いいえ"
}

/** 画面の判断に使う、実際に起きたこと（設定の値ではなく、結果で判断する） */
data class SyncFacts(
    /** スマホ全体の自動同期（これは正しく読める） */
    val masterOn: Boolean,
    /** いま同期が動いているか */
    val running: Boolean,
    /** このアプリで変えて、まだ Google に届いていない予定の数 */
    val pending: Int,
    /** そのうち、長いあいだ届いていないもの */
    val stuck: Int,
    /** 最後に同期できたのを確認した時刻（0 ＝ まだ） */
    val lastSyncAt: Long,
    /** 最後に同期を頼んだ時刻 */
    val lastRequestAt: Long,
)

/** 「最後の同期：今日 18:47」 */
fun lastSyncLine(lastSyncAt: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (lastSyncAt <= 0) return "最後の同期：まだ確認していません"
    val t = Instant.ofEpochMilli(lastSyncAt).atZone(zone)
    val d = t.toLocalDate()
    val hm = t.format(DateTimeFormatter.ofPattern("H:mm"))
    val day = when (d) {
        today -> "今日"
        today.minusDays(1) -> "昨日"
        else -> "${d.monthValue}/${d.dayOfMonth}"
    }
    return "最後の同期：$day $hm"
}

/** 同期画面の一番上に出す 1 枚 */
data class SyncSummary(val kind: Kind, val title: String, val sub: String, val action: String) {
    enum class Kind { OK, SYNCING, PENDING, STUCK, MASTER_OFF }

    /** 赤い警告にするか */
    val isError: Boolean get() = kind == Kind.STUCK || kind == Kind.MASTER_OFF

    companion object {
        /** これより長く届いていない変更は「送れていない」とする */
        const val STUCK_MS = 15 * 60_000L

        fun of(f: SyncFacts, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): SyncSummary {
            val last = lastSyncLine(f.lastSyncAt, today, zone)
            return when {
                !f.masterOn -> SyncSummary(Kind.MASTER_OFF, "自動同期がオフです",
                    "スマホ全体の自動同期がオフのため、Google カレンダーの変更が自動では届きません", "オンにする")
                f.running -> SyncSummary(Kind.SYNCING, "同期しています…", last, "")
                f.stuck > 0 -> SyncSummary(Kind.STUCK, "送れていない変更があります",
                    "${f.stuck} 件が 15 分以上 Google に届いていません", "もう一度送る")
                f.pending > 0 -> SyncSummary(Kind.PENDING, "送信待ち ${f.pending} 件", "まもなく Google に届きます。$last", "今すぐ同期")
                else -> SyncSummary(Kind.OK, "同期できています", last, "今すぐ同期")
            }
        }
    }
}
