package com.greenaltambe.vela.library

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the sleep timer waits for: a span of time from now, or the end of the current video. */
sealed interface SleepTimerMode {
    data class After(val duration: Duration) : SleepTimerMode
    data object EndOfVideo : SleepTimerMode
}

/** Presets offered on the player. */
val SleepTimerPresets: List<SleepTimerMode> =
    listOf(15, 30, 45, 60, 90).map { SleepTimerMode.After(it.minutes) } + SleepTimerMode.EndOfVideo

/** What a running timer reports to the UI. */
data class SleepTimerState(val mode: SleepTimerMode, val deadline: Instant? = null)
