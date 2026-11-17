package io.github.barista9409.calendar

import io.github.barista9409.calendar.data.SyncStatus
import io.github.barista9409.calendar.widget.MonthWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 同期の状態の表示と、ウィジェットの見出し */
class SyncStatusTest {
    private fun st(master: Boolean = true, acc: Boolean = true, active: Boolean = false, pending: Boolean = false,
                   unsynced: Int = 0, syncable: Int = 1, dirty: Int = 0, access: Boolean = true,
                   last: Long = 0L, req: Long = 0L) =
        SyncStatus("a@gmail.com", master, acc, active, pending, unsynced, syncable, dirty, last, req, access)

    @Test fun problemsAreFixableInApp() {
        assertTrue(st(master = false).message.contains("スマホ全体の自動同期がオフ"))
        assertEquals(SyncStatus.Fix.MASTER, st(master = false).fix)
        assertEquals(SyncStatus.Fix.ACCOUNT, st(acc = false).fix)
        assertEquals(SyncStatus.Fix.ACCOUNT, st(syncable = 0).fix)
        assertTrue(st(acc = false).isError && st(acc = false).fixLabel.isNotEmpty())
        assertTrue(st(unsynced = 2).message.contains("2 個"))
        assertTrue(st(master = false).hasProblem && st(unsynced = 1).hasProblem)
    }

    @Test fun invisibleAccountIsNotReportedAsOff() {
        // Android 13 以降、アプリから見えないアカウントは読み取りが常に「オフ」になる。誤って「オフ」と警告しない
        val s = st(acc = false, syncable = 0, access = false)
        assertEquals(SyncStatus.Fix.ACCESS, s.fix)
        assertFalse(s.isError)
        assertFalse(s.hasProblem)
        assertTrue(s.message.contains("見えません"))
        assertEquals("見られるようにする", s.fixLabel)
        assertTrue(s.details.any { it.second.contains("見えないため不明") })
    }

    @Test fun syncProgressDone() {
        // 動いているのを見て止まった → 完了
        assertTrue(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, running = false, sawRunning = true, changedAfterStart = false, quiet = true, dirty = 0))
        // 頼んだ後に書き換わって静かになった → 完了
        assertTrue(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, false, false, changedAfterStart = true, quiet = true, dirty = 0))
        // 動いている最中・未送信が残る・何も起きていない・直後 → まだ
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, running = true, sawRunning = true, changedAfterStart = true, quiet = true, dirty = 0))
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, false, true, true, true, dirty = 2))
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(5000, false, false, false, true, 0))
        assertFalse(io.github.barista9409.calendar.data.SyncProgress.isDone(500, false, true, true, true, 0))
    }

    @Test fun creatorHidesCalendarIds() {
        val f = { o: String?, a: String?, n: String?, w: String? -> io.github.barista9409.calendar.model.creatorLabel(o, a, n, w) }
        // 共有カレンダーの ID（画面で「作成者：8c6c…@group.calendar.google.com」と出ていた不具合）
        assertEquals(null, f("abc123@group.calendar.google.com", "me@example.com", "プライベート", null))
        assertEquals(null, f("ja.japanese#holiday@group.v.calendar.google.com", "a@gmail.com", "日本の祝日", null))
        assertEquals(null, f("a@gmail.com", "a@gmail.com", "a@gmail.com", null))
        assertEquals(null, f("cal@example.com", "a@gmail.com", "x", "cal@example.com"))
        assertEquals(null, f("", "a@gmail.com", "x", null))
        assertEquals("b@gmail.com", f("b@gmail.com", "a@gmail.com", "x", null))
    }

    @Test fun normalStates() {
        assertTrue(st(active = true).message.contains("同期しています"))
        assertTrue(st(pending = true).message.contains("待って"))
        assertTrue(st(dirty = 3).message.contains("送っていない変更が 3 件"))
        assertTrue(st().message.contains("同期はオン"))
        assertFalse(st().hasProblem)
        assertEquals(SyncStatus.Fix.NONE, st().fix)
    }

    @Test fun lastSyncLine() {
        val zone = java.time.ZoneId.of("Asia/Tokyo")
        val today = LocalDate.of(2026, 11, 17)
        val t = java.time.ZonedDateTime.of(2026, 11, 17, 14, 3, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("最後の同期：今日 14:03", st(last = t).lastLine(today, zone))
        assertEquals("最後の同期：昨日 14:03", st(last = t).lastLine(today.plusDays(1), zone))
        assertEquals("同期を頼んだ時刻：今日 14:03", st(req = t).lastLine(today, zone))
        assertTrue(st().lastLine(today, zone).contains("まだ"))
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

    @Test fun summaryShowsOnlyOneState() {
        val S = io.github.barista9409.calendar.data.SyncSummary
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.OK, S.of(listOf(st(), st()), running = false, lastLine = "最後の同期：今日 9:00").kind)
        assertEquals("最新です", S.of(listOf(st()), false, "").title)
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.SYNCING, S.of(listOf(st()), running = true, lastLine = "").kind)
        val pend = S.of(listOf(st(dirty = 2), st(dirty = 1)), false, "")
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.PENDING, pend.kind); assertEquals("送信待ち 3 件", pend.title)
        // 止まっているときは同期中より優先して［直す］を出す
        val stop = S.of(listOf(st(), st(acc = false)), running = true, lastLine = "")
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.STOPPED, stop.kind); assertTrue(stop.needsFix)
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.STOPPED, S.of(listOf(st(master = false)), false, "").kind)
        // 見えないだけのアカウントは赤くせず、確認を促す
        val check = S.of(listOf(st(access = false)), false, "")
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.CHECK, check.kind); assertTrue(check.needsFix)
        // 同期していないカレンダーはエラーにしない（「ほかのカレンダーを追加」に出す）
        assertEquals(io.github.barista9409.calendar.data.SyncSummary.Kind.OK, S.of(listOf(st(unsynced = 2)), false, "").kind)
    }

    @Test fun pendingUploadMark() {
        val e = io.github.barista9409.calendar.model.EventInstance(1, 1, 1, "習い事", 0, 0, false, 0, null, null, null, null, null)
        assertEquals("習い事", e.displayTitle)
        assertEquals("↑ 習い事", e.copy(pendingUpload = true).displayTitle)
    }
}
