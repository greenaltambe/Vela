package com.greenaltambe.vela.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Set by the seek bar from the first touch on it until the finger lifts or is cancelled, plus a short tail ([TAIL_MS]) so the lift cannot become
 * a fling. While it is locked the player's other gestures stay out of the way: the sheet's drag (minimise / swipe up to fullscreen), the
 * fullscreen swipe down, double-tap, hold for speed. Times are the pointer events' own `uptimeMillis`, so a test's virtual clock works.
 * [bar] is where the seek bar is, in root coordinates; a swipe may not start on it or within [BAND_DP] around it.
 */
@Stable
class PlayerGestureLock {
    var active by mutableStateOf(false)
        private set
    private var releasedAtMs = Long.MIN_VALUE / 2
    var bar: Rect? = null

    fun begin() { active = true }
    fun end(uptimeMs: Long) { active = false; releasedAtMs = uptimeMs }
    fun isLocked(uptimeMs: Long): Boolean = active || uptimeMs - releasedAtMs < TAIL_MS

    /** Whether [rootPosition] lies on the seek bar or within [marginPx] of it. */
    fun onBar(rootPosition: Offset, marginPx: Float): Boolean = bar?.inflate(marginPx)?.contains(rootPosition) == true

    companion object {
        const val TAIL_MS = 150L
        const val BAND_DP = 12
    }
}

val LocalPlayerGestureLock = staticCompositionLocalOf<PlayerGestureLock?> { null }
