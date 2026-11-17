package io.github.barista9409.calendar

import io.github.barista9409.calendar.notify.ReminderStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 通知の種類とバイブ：マナーモードやサイレントでも気づけること、長さの設定 */
class ReminderStyleTest {
    @Test fun eachStyleHasItsOwnNewChannel() {
        val ids = ReminderStyle.entries.map { it.channelId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it in ReminderStyle.OLD_CHANNELS })
    }

    @Test fun vibrationDecision() {
        // スマホの設定に合わせる：サイレントでは鳴らさない、それ以外は鳴らす
        assertTrue(ReminderStyle.shouldVibrate(ReminderStyle.PHONE, ringerSilent = false))
        assertFalse(ReminderStyle.shouldVibrate(ReminderStyle.PHONE, ringerSilent = true))
        // バイブのみ・アラーム：サイレントでも鳴らす
        assertTrue(ReminderStyle.shouldVibrate(ReminderStyle.VIBRATE, ringerSilent = true))
        assertTrue(ReminderStyle.shouldVibrate(ReminderStyle.ALARM, ringerSilent = true))
        // 表示のみ：鳴らさない
        assertFalse(ReminderStyle.shouldVibrate(ReminderStyle.SILENT, ringerSilent = false))
    }

    @Test fun longerSettingVibratesLonger() {
        val total = (0..2).map { ReminderStyle.pattern(it).sum() }
        assertTrue(total[0] < total[1] && total[1] < total[2])
        assertTrue(total[1] >= 3000)
        assertTrue(total[2] >= 6000)
        // 振動の並びは「待ち・振動」の組（最初は待ち 0）
        (0..2).forEach { assertEquals(0L, ReminderStyle.pattern(it)[0]) }
    }

    @Test fun repeatingPatternRestsBetweenRounds() {
        val p = ReminderStyle.repeatingPattern(1)
        assertEquals(ReminderStyle.pattern(1).size + 1, p.size)
        assertEquals(1000L, p.last())
        assertEquals(5 * 60_000L, ReminderStyle.MAX_REPEAT_MS)
    }

    @Test fun defaultIsPhoneSetting() {
        assertEquals(ReminderStyle.PHONE, ReminderStyle.of(0))
        assertEquals(ReminderStyle.PHONE, ReminderStyle.of(99))
    }

    @Test fun stopButtonIsAlwaysFirstWhenItBuzzes() {
        for (repeat in listOf(false, true)) for (loc in listOf(false, true)) {
            for (st in listOf(ReminderStyle.PHONE, ReminderStyle.VIBRATE, ReminderStyle.ALARM)) {
                val a = ReminderStyle.actions(st, repeat, loc)
                assertEquals("$st repeat=$repeat", ReminderStyle.ACTION_STOP, a.first())
                assertTrue(a.size <= 3) // Android の上限
            }
        }
        // 表示のみ（鳴らない）には［止める］は不要
        assertFalse(ReminderStyle.actions(ReminderStyle.SILENT, repeat = false, hasLocation = true).contains(ReminderStyle.ACTION_STOP))
        assertEquals(listOf(ReminderStyle.ACTION_STOP, ReminderStyle.ACTION_SNOOZE, ReminderStyle.ACTION_MAP),
            ReminderStyle.actions(ReminderStyle.PHONE, repeat = true, hasLocation = true))
    }
}
