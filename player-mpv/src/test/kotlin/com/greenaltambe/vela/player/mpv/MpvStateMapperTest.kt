package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvStateMapperTest {
    private val loaded = MpvSnapshot(loadRequested = true, coreIdle = false, timePosSeconds = 12.3456, durationSeconds = 600.0)

    private fun status(s: MpvSnapshot) = MpvStateMapper.map(s).status

    @Test fun nothingLoadedIsIdle() = assertEquals(PlaybackStatus.IDLE, status(MpvSnapshot(speed = 1.5)))
    @Test fun idleKeepsSpeed() = assertEquals(1.5f, MpvStateMapper.map(MpvSnapshot(speed = 1.5)).speed)
    @Test fun playing() = assertEquals(PlaybackStatus.PLAYING, status(loaded))
    @Test fun pausedWins() = assertEquals(PlaybackStatus.PAUSED, status(loaded.copy(pause = true, coreIdle = true)))
    @Test fun startingUpIsBufferingWhenPlayIsWanted() = assertEquals(PlaybackStatus.BUFFERING, status(loaded.copy(coreIdle = true)))
    @Test fun cacheStallIsBuffering() = assertEquals(PlaybackStatus.BUFFERING, status(loaded.copy(pausedForCache = true)))
    @Test fun eofIsEnded() = assertEquals(PlaybackStatus.ENDED, status(loaded.copy(eofReached = true, pause = true)))
    @Test fun failureWinsOverEverything() = assertEquals(PlaybackStatus.ERROR, status(loaded.copy(failed = true, eofReached = true)))

    @Test
    fun positionAndDurationAreMilliseconds() {
        val s = MpvStateMapper.map(loaded)
        assertEquals(12_346L, s.positionMs); assertEquals(600_000L, s.durationMs)
    }

    @Test
    fun overrideShowsTheRequestedPositionUntilCleared() {
        assertEquals(90_000L, MpvStateMapper.map(loaded.copy(positionOverrideMs = 90_000)).positionMs)
    }

    @Test
    fun unknownDurationAndNegativePositionAreZero() {
        val s = MpvStateMapper.map(loaded.copy(durationSeconds = null, timePosSeconds = -0.5))
        assertEquals(0L, s.durationMs); assertEquals(0L, s.positionMs)
    }

    @Test
    fun positionIsThrottledButChangesAreNot() {
        val a = PlayerState(PlaybackStatus.PLAYING, 1000, 60_000, 1f)
        assertFalse(MpvStateMapper.worthPublishing(a, a.copy(positionMs = 1100)))
        assertTrue(MpvStateMapper.worthPublishing(a, a.copy(positionMs = 1250)))
        assertTrue(MpvStateMapper.worthPublishing(a, a.copy(status = PlaybackStatus.PAUSED)))
        assertTrue(MpvStateMapper.worthPublishing(a, a.copy(speed = 2f)))
        assertTrue(MpvStateMapper.worthPublishing(a, a.copy(durationMs = 61_000)))
    }
}
