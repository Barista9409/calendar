package io.github.barista9409.calendar

import io.github.barista9409.calendar.google.Attachment
import io.github.barista9409.calendar.google.AttachmentService
import io.github.barista9409.calendar.google.AttachmentText
import io.github.barista9409.calendar.google.GoogleAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 添付ファイル：予定の説明に書き込む形式（審査のいらない仕組み） */
class AttachmentServiceTest {
    private val a = Attachment("FILE_A", "見積書.pdf", null, AttachmentText.fileUrl("FILE_A"))
    private val b = Attachment("FILE_B", "地図.png", null, AttachmentText.fileUrl("FILE_B"))

    @Test fun onlyTheDriveFileScopeIsRequested() {
        // 審査が必要な権限（カレンダー・ドライブ全体）を使わない
        assertEquals(listOf("https://www.googleapis.com/auth/drive.file"), GoogleAuth.SCOPES)
    }

    @Test fun addKeepsTheUsersTextAndAddsLinks() {
        val d1 = AttachmentText.withAdded("持ち物：上履き", a)
        val d2 = AttachmentText.withAdded(d1, b)
        assertEquals("持ち物：上履き", AttachmentText.body(d2))
        assertEquals(listOf(a, b), AttachmentText.parse(d2))
        assertTrue(d2.contains("https://drive.google.com/file/d/FILE_A/view"))
    }

    @Test fun removeOnlyThatFileAndDropBlockWhenEmpty() {
        val d = AttachmentText.withAdded(AttachmentText.withAdded("メモ", a), b)
        val removed = AttachmentText.withRemoved(d, a)
        assertEquals(listOf(b), AttachmentText.parse(removed))
        val none = AttachmentText.withRemoved(removed, b)
        assertEquals("メモ", none)
        assertTrue(AttachmentText.parse(none).isEmpty())
    }

    @Test fun emptyDescription() {
        assertEquals(listOf(a), AttachmentText.parse(AttachmentText.withAdded(null, a)))
        assertEquals("", AttachmentText.body(null))
        assertTrue(AttachmentText.parse("ふつうの説明").isEmpty())
    }

    @Test fun editingKeepsAttachments() {
        // 編集画面では本文だけを出し、保存するときに添付のブロックを付け直す
        val d = AttachmentText.withAdded("前の説明", a)
        val saved = AttachmentText.join("新しい説明", AttachmentText.block(d))
        assertEquals("新しい説明", AttachmentText.body(saved))
        assertEquals(listOf(a), AttachmentText.parse(saved))
        // 入力画面の「添付済み」の一覧は、ブロックだけからも読める
        assertEquals(listOf(a), AttachmentText.parse(AttachmentText.block(d)))
        // 本文を空にしても添付は残る
        assertEquals(listOf(a), AttachmentText.parse(AttachmentText.join("", AttachmentText.block(d))))
    }

    @Test fun titleWithColonOrNewlineIsSafe() {
        val odd = Attachment("X1", "a：b\nc.pdf", null, AttachmentText.fileUrl("X1"))
        val parsed = AttachmentText.parse(AttachmentText.withAdded("", odd)).single()
        assertEquals("X1", parsed.fileId)
        assertEquals(odd.fileUrl, parsed.fileUrl)
    }

    @Test fun safeNameReplacesPathCharacters() {
        assertEquals("a_b_c.pdf", AttachmentService.safeName("a/b:c.pdf"))
    }
}

class AttachmentStoreNameTest {
    @org.junit.Test fun storedNameKeepsExtension() =
        org.junit.Assert.assertEquals("見積書 (1a2b3c4d).pdf", io.github.barista9409.calendar.google.AttachmentStore.storedName("1a2b3c4d5e6f", "見積書.pdf"))
    @org.junit.Test fun storedNameWithoutExtension() =
        org.junit.Assert.assertEquals("memo (abc)", io.github.barista9409.calendar.google.AttachmentStore.storedName("abc", "memo"))

    @Test fun driveFileNameHasDateAndTitle() {
        val f = io.github.barista9409.calendar.google.AttachmentService
        val d = java.time.LocalDate.of(2026, 12, 24)
        org.junit.Assert.assertEquals("2026-12-24_面談_IMG_0001.jpg", f.fileName(d, "面談", "IMG_0001.jpg"))
        // 予定名の空白・使えない文字は「_」、名前が無ければ日付だけ
        org.junit.Assert.assertEquals("2026-12-24_A_B_x.pdf", f.fileName(d, "A / B", "x.pdf"))
        org.junit.Assert.assertEquals("2026-12-24_x.pdf", f.fileName(d, "  ", "x.pdf"))
        // 終日の予定は UTC の 0 時で入っている
        val utcMidnight = java.time.LocalDate.of(2026, 12, 24).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        org.junit.Assert.assertEquals(d, f.eventDate(utcMidnight, true, "UTC"))
    }
}
