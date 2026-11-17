package io.github.barista9409.calendar

import io.github.barista9409.calendar.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateInfoTest {
    private val sha = "a".repeat(64)

    @Test fun parsesManifest() {
        val json = """{"latest":{"versionCode":10300,"versionName":"1.3.0","minSdk":26,
            "apkUrl":"https://github.com/barista9409/calendar-releases/releases/download/v1.3.0/calendar-1.3.0.apk",
            "sha256":"$sha","releaseNotes":"・改善","mandatory":false,"publishedAt":"2026-12-01T10:00:00+09:00"}}"""
        val info = UpdateInfo.parse(json)
        assertEquals(10300, info.versionCode)
        assertEquals("1.3.0", info.versionName)
        assertEquals(sha, info.sha256)
    }

    @Test fun rejectsForeignHost() {
        val json = """{"latest":{"versionCode":2,"versionName":"0.0.2","apkUrl":"https://evil.example.com/a.apk","sha256":"$sha"}}"""
        assertThrows(IllegalArgumentException::class.java) { UpdateInfo.parse(json) }
    }

    @Test fun rejectsBadHash() {
        val json = """{"latest":{"versionCode":2,"versionName":"0.0.2",
            "apkUrl":"https://github.com/barista9409/calendar-releases/releases/download/v0.0.2/a.apk","sha256":"xyz"}}"""
        assertThrows(IllegalArgumentException::class.java) { UpdateInfo.parse(json) }
    }

}
