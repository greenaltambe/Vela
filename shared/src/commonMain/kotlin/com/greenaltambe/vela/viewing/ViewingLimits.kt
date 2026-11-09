package com.greenaltambe.vela.viewing

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore

/**
 * Counts how long the viewer has been watching and says when it is time for a break. Only time spent actually playing counts; a pause of [resetAfterMs] or
 * more is a break in itself and starts the count again. Call [tick] about once a second.
 */
class BreakTimer(private val resetAfterMs: Long = 5 * 60_000L) {
    private var accumulated = 0L
    private var last: Long? = null
    private var lastPlayingAt: Long? = null
    private var nextAt = 0L
    private var fired = false

    /** Playing time since the last break, in milliseconds. */
    val watchedMs: Long get() = accumulated

    /** True once, when the viewer has watched [thresholdMs] since the last break or snooze. A threshold of 0 or less is "off". */
    fun tick(nowMs: Long, playing: Boolean, thresholdMs: Long): Boolean {
        if (playing) {
            last?.let { accumulated += (nowMs - it).coerceIn(0L, MAX_STEP_MS) }   // a long gap (the app was asleep) never counts as watching
            last = nowMs; lastPlayingAt = nowMs
        } else {
            last = null
            val at = lastPlayingAt
            if (at != null && nowMs - at >= resetAfterMs) startOver()
        }
        if (thresholdMs <= 0L) return false
        if (!fired && accumulated >= maxOf(thresholdMs, nextAt)) { fired = true; return true }
        return false
    }

    /** "Keep watching": remind again after [extraMs] more. */
    fun snooze(extraMs: Long) { nextAt = accumulated + extraMs; fired = false }

    /** The viewer took the break: count from nothing. */
    fun startOver() { accumulated = 0L; nextAt = 0L; fired = false; lastPlayingAt = null; last = null }

    private companion object { const val MAX_STEP_MS = 5_000L }
}

/**
 * Time spent in one place (Shorts) today, kept in a settings row so it survives the app closing. The day is the viewer's local day: [offsetMinutes] is the
 * clock's offset from UTC. Yesterday's time never counts today.
 */
class DailyUsage(private val store: SettingsStore, private val key: String, private val nowMs: () -> Long, private val offsetMinutes: () -> Int) {
    private fun day() = kotlin.math.floor((nowMs() + offsetMinutes() * 60_000L) / 86_400_000.0).toLong()

    fun usedMs(): Long {
        val raw = store.get(key, SettingLayer.Global) ?: return 0L
        val (d, ms) = raw.split(':').let { (it.getOrNull(0)?.toLongOrNull() ?: return 0L) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
        return if (d == day()) ms else 0L
    }

    fun add(ms: Long) { if (ms > 0) store.put(key, SettingLayer.Global, "${day()}:${usedMs() + ms}") }

    /** Gives back time (a few extra minutes the viewer asked for): never below zero. */
    fun forgive(ms: Long) { store.put(key, SettingLayer.Global, "${day()}:${(usedMs() - ms).coerceAtLeast(0L)}") }
}
