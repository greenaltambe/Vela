package com.greenaltambe.vela.library

import com.greenaltambe.vela.data.WatchRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HistoryGroupingTest {
    // 2026-10-27 12:00 UTC
    private val now = Instant.fromEpochSeconds(1_793_102_400)

    private fun record(id: Long, video: String, secondsAgo: Long) =
        WatchRecord(id, "local", video, "c", "t$video", Instant.fromEpochSeconds(now.epochSeconds - secondsAgo), 0, 0, 0)

    @Test
    fun groupsByDayWithTodayAndYesterday() {
        val days = groupHistoryByDay(listOf(record(1, "a", 60), record(2, "b", 3600 * 20), record(3, "c", 86_400 * 3)), now, offsetMinutes = 0)
        assertEquals(listOf("Today", "Yesterday", "2026-10-24"), days.map { it.label })
    }

    @Test
    fun localOffsetMovesTheDayBoundary() {
        // 23:00 UTC the previous day is already "today" for someone at UTC+2 (now = 14:00 local).
        val lateLastNight = record(1, "a", 13 * 3600)
        assertEquals("Yesterday", groupHistoryByDay(listOf(lateLastNight), now, offsetMinutes = 0).single().label)
        assertEquals("Today", groupHistoryByDay(listOf(lateLastNight), now, offsetMinutes = 120).single().label)
    }

    @Test
    fun aVideoWatchedTwiceInADayShowsOnceAtItsLatest() {
        val day = groupHistoryByDay(listOf(record(1, "a", 7200), record(2, "a", 60), record(3, "b", 120)), now, 0).single()
        assertEquals(listOf(2L, 3L), day.records.map { it.id })
    }

    @Test
    fun keepsEveryWatchWhenAskedTo() {
        val day = groupHistoryByDay(listOf(record(1, "a", 7200), record(2, "a", 60)), now, 0, distinctPerDay = false).single()
        assertEquals(2, day.records.size)
    }

    @Test
    fun civilDates() {
        assertEquals("1970-01-01", isoDate(0))
        assertEquals("2000-02-29", isoDate(11016))
        assertEquals("2026-10-27", isoDate(now.epochSeconds / 86_400))
    }
}
