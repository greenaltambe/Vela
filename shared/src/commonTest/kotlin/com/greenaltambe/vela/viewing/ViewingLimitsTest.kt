package com.greenaltambe.vela.viewing

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewingLimitsTest {
    private val min = 60_000L

    /** Plays for [minutes] with a tick each second; returns the minute at which it first fired, or null. */
    private fun watch(t: BreakTimer, from: Long, minutes: Int, threshold: Long, playing: Boolean = true): Long? {
        var now = from
        repeat(minutes * 60) { now += 1000; if (t.tick(now, playing, threshold)) return now }
        return null
    }

    @Test fun itFiresOnceWhenTheThresholdOfPlayingTimeIsReached() {
        val t = BreakTimer()
        t.tick(0, true, 30 * min)
        val at = watch(t, 0, 40, 30 * min)
        assertEquals(30 * min, at)
        assertEquals(null, watch(t, at!!, 20, 30 * min), "it does not fire again until snoozed")
    }

    @Test fun pausedTimeDoesNotCount() {
        val t = BreakTimer()
        t.tick(0, true, 10 * min)
        watch(t, 0, 4, 10 * min)               // 4 min playing
        watch(t, 4 * min, 3, 10 * min, playing = false)   // 3 min paused: under the reset time
        assertEquals(null, watch(t, 7 * min, 5, 10 * min), "4 + 5 = 9 minutes is not yet 10")
        assertTrue(watch(t, 12 * min, 2, 10 * min) != null)
    }

    @Test fun aLongPauseIsABreakAndTheCountStartsOver() {
        val t = BreakTimer(resetAfterMs = 5 * min)
        t.tick(0, true, 10 * min)
        watch(t, 0, 8, 10 * min)
        watch(t, 8 * min, 6, 10 * min, playing = false)  // 6 min away
        assertTrue(t.watchedMs < min)
        assertEquals(null, watch(t, 14 * min, 8, 10 * min), "8 more minutes after the break: still under 10")
    }

    @Test fun snoozeRemindsAgainAfterTheExtraTime() {
        val t = BreakTimer()
        t.tick(0, true, 5 * min)
        val first = watch(t, 0, 6, 5 * min)!!
        t.snooze(2 * min)
        val second = watch(t, first, 5, 5 * min)
        assertEquals(first + 2 * min, second)
    }

    @Test fun aSuspendedAppIsNotCountedAsWatching() {
        val t = BreakTimer()
        t.tick(0, true, 10 * min)
        t.tick(2 * 60 * min, true, 10 * min)    // two hours later in one tick (the phone slept)
        assertTrue(t.watchedMs <= 5_000L)
    }

    @Test fun offMeansNever() {
        val t = BreakTimer()
        t.tick(0, true, 0)
        assertEquals(null, watch(t, 0, 200, 0))
    }

    @Test fun dailyUsageAddsUpAndForgetsYesterday() {
        var now = 10 * 3_600_000L          // day 0, 10:00 UTC
        val u = DailyUsage(InMemorySettingsStore(), "k", { now }, { 0 })
        u.add(5 * min); u.add(7 * min)
        assertEquals(12 * min, u.usedMs())
        now += 24 * 3_600_000L            // the next day
        assertEquals(0L, u.usedMs())
        u.add(min); assertEquals(min, u.usedMs())
    }

    @Test fun theDayChangesAtLocalMidnightNotUtcMidnight() {
        var now = 23 * 3_600_000L          // 23:00 UTC on day 0
        val u = DailyUsage(InMemorySettingsStore(), "k", { now }, { 120 })   // UTC+2: already 01:00 on day 1
        u.add(10 * min)
        now += 90 * min                    // 00:30 UTC day 1 = 02:30 local, still the same local day
        assertEquals(10 * min, u.usedMs())
    }

    @Test fun forgivingGivesTimeBackButNeverGoesNegative() {
        val u = DailyUsage(InMemorySettingsStore(), "k", { 0L }, { 0 })
        u.add(10 * min); u.forgive(4 * min); assertEquals(6 * min, u.usedMs())
        u.forgive(60 * min); assertEquals(0L, u.usedMs())
        assertFalse(u.usedMs() < 0)
    }
}
