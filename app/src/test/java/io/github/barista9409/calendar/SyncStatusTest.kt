package io.github.barista9409.calendar

import io.github.barista9409.calendar.data.SyncStatus
import io.github.barista9409.calendar.data.SyncSummary
import io.github.barista9409.calendar.data.lastSyncLine
import io.github.barista9409.calendar.widget.MonthWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 同期の状態の表示と、ウィジェットの見出し */
class SyncStatusTest {
    private fun f(master: Boolean = true, running: Boolean = false, pending: Int = 0, stuck: Int = 0, last: Long = 0L) =
        io.github.barista9409.calendar.data.SyncFacts(master, running, pending, stuck, last, 0L)

    @Test fun summaryJudgesByResultsNotSettings() {
        // スマホが返すアカウントごとの設定（オフ・対象外など）は判断に使わない。結果だけで判断する
        assertEquals(SyncSummary.Kind.OK, SyncSummary.of(f()).kind)
        assertEquals("同期できています", SyncSummary.of(f()).title)
        assertEquals(SyncSummary.Kind.SYNCING, SyncSummary.of(f(running = true)).kind)
        val pend = SyncSummary.of(f(pending = 2))
        assertEquals(SyncSummary.Kind.PENDING, pend.kind); assertEquals("送信待ち 2 件", pend.title); assertFalse(pend.isError)
        val stuck = SyncSummary.of(f(pending = 2, stuck = 1))
        assertEquals(SyncSummary.Kind.STUCK, stuck.kind); assertTrue(stuck.isError); assertEquals("もう一度送る", stuck.action)
        // スマホ全体の自動同期オフ（これは正しく読める）は最優先
        val off = SyncSummary.of(f(master = false, running = true, stuck = 1))
        assertEquals(SyncSummary.Kind.MASTER_OFF, off.kind); assertEquals("オンにする", off.action)
    }

    @Test fun lastSyncLineFormat() {
        val zone = java.time.ZoneId.of("Asia/Tokyo")
        val today = LocalDate.of(2026, 11, 17)
        val t = java.time.ZonedDateTime.of(2026, 11, 17, 14, 3, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("最後の同期：今日 14:03", lastSyncLine(t, today, zone))
        assertEquals("最後の同期：昨日 14:03", lastSyncLine(t, today.plusDays(1), zone))
        assertEquals("最後の同期：11/17 14:03", lastSyncLine(t, today.plusDays(3), zone))
        assertEquals("最後の同期：まだ記録がありません", lastSyncLine(0L, today, zone))
        assertEquals("最後の同期：今日 14:03", SyncSummary.of(f(last = t), today, zone).last)
        // 状態が何でも、ボタンの右に最後の同期を出す
        assertEquals("最後の同期：今日 14:03", SyncSummary.of(f(last = t, pending = 1), today, zone).last)
        assertEquals("最後の同期：今日 14:03", SyncSummary.of(f(last = t, running = true), today, zone).last)
    }

    @Test fun externalChangeCountsAsSync() {
        // このアプリが書き換えた直後の変化は数えない。それ以外（Google から届いた変更）は「同期した」と数える
        val P = io.github.barista9409.calendar.data.SyncProgress
        assertFalse(P.isExternalChange(changedAt = 10_000, lastLocalWrite = 8_000))
        assertTrue(P.isExternalChange(changedAt = 100_000, lastLocalWrite = 8_000))
        assertTrue(P.isExternalChange(changedAt = 100_000, lastLocalWrite = 0))
    }

    @Test fun detailsAreForReferenceOnly() {
        val st = SyncStatus("a@example.com", true, false, false, false, syncable = 0, access = true)
        assertTrue(st.details.any { it.first.contains("カレンダー自動同期") && it.second == "オフ" })
    }

    @Test fun syncProgressDone() {
        // 動いているのを見て止まった → 完了
        assertTrue(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, running = false, sawRunning = true, changedAfterStart = false, quiet = true, pending = 0))
        // 頼んだ後に書き換わって静かになった → 完了
        assertTrue(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, false, false, changedAfterStart = true, quiet = true, pending = 0))
        // 動いている最中・未送信が残る・何も起きていない・直後 → まだ
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, running = true, sawRunning = true, changedAfterStart = true, quiet = true, pending = 0))
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, false, true, true, true, pending = 2))
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, false, false, false, true, 0))
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(500, false, true, true, true, 0))
    }

    @Test fun widgetTitleIsTodayNotTheScrolledMonth() {
        // 見出しはスクロール位置に関係なく今日の日付（ずれない）
        assertEquals("本日：2026年11月17日（火）", MonthWidget.title(LocalDate.of(2026, 11, 17)))
        assertEquals("本日：11月17日（火）", MonthWidget.title(LocalDate.of(2026, 11, 17), narrow = true))
    }

    @Test fun monthWidgetShowsThisMonthAndTwoMore() {
        // 重さ対策：過去は出さず、今月から 3 か月だけ（2026/11/1 は日曜、2027/1/31 は日曜）
        val today = LocalDate.of(2026, 11, 17)
        assertEquals(LocalDate.of(2026, 11, 1), MonthWidget.listStart(today))
        val weeks = MonthWidget.listWeeks(today)
        val lastWeek = MonthWidget.listStart(today).plusWeeks(weeks - 1L)
        assertTrue(!lastWeek.isAfter(LocalDate.of(2027, 1, 31)) && lastWeek.plusDays(6) >= LocalDate.of(2027, 1, 31))
        assertEquals(14, weeks)
    }

    @Test fun pendingUploadMark() {
        val e = io.github.barista9409.calendar.model.EventInstance(1, 1, 1, "習い事", 0, 0, false, 0, null, null, null, null, null)
        assertEquals("習い事", e.displayTitle)
        assertEquals("↑ 習い事", e.copy(pendingUpload = true).displayTitle)
    }
}
