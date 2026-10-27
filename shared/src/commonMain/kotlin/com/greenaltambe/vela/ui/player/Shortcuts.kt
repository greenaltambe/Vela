package com.greenaltambe.vela.ui.player

import androidx.compose.ui.input.key.Key
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlin.math.abs

enum class ShortcutAction { TogglePlay, SeekBack5, SeekForward5, SeekBack10, SeekForward10, ToggleFullscreen, ToggleMute, VolumeUp, VolumeDown, SpeedDown, SpeedUp }

/** Desktop shortcuts: space, arrows (5 s; with shift 10 s), f, m, up / down, < and >. Null for any other key. */
fun shortcutFor(key: Key, shift: Boolean): ShortcutAction? = when (key) {
    Key.Spacebar -> ShortcutAction.TogglePlay
    Key.DirectionLeft -> if (shift) ShortcutAction.SeekBack10 else ShortcutAction.SeekBack5
    Key.DirectionRight -> if (shift) ShortcutAction.SeekForward10 else ShortcutAction.SeekForward5
    Key.F -> ShortcutAction.ToggleFullscreen
    Key.M -> ShortcutAction.ToggleMute
    Key.DirectionUp -> ShortcutAction.VolumeUp
    Key.DirectionDown -> ShortcutAction.VolumeDown
    Key.Comma -> ShortcutAction.SpeedDown
    Key.Period -> ShortcutAction.SpeedUp
    else -> null
}

val SpeedSteps = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** The next speed up / down the ladder from [current], clamped at the ends. */
fun steppedSpeed(current: Float, up: Boolean): Float {
    val index = SpeedSteps.indices.minByOrNull { abs(SpeedSteps[it] - current) } ?: 3
    return SpeedSteps[(if (up) index + 1 else index - 1).coerceIn(SpeedSteps.indices)]
}

private const val VOLUME_STEP = 0.05f

/** Runs [action] against the session. Seeks are clamped to the video; fullscreen is the caller's, via [toggleFullscreen]. */
fun applyShortcut(action: ShortcutAction, session: PlaybackSession, player: PlayerState, toggleFullscreen: () -> Unit) {
    fun seekBy(deltaMs: Long) {
        val target = (player.positionMs + deltaMs).coerceAtLeast(0)
        session.seekTo(if (player.durationMs > 0) target.coerceAtMost(player.durationMs) else target)
    }
    when (action) {
        ShortcutAction.TogglePlay -> if (player.status == PlaybackStatus.PLAYING) session.pause() else session.play()
        ShortcutAction.SeekBack5 -> seekBy(-5_000)
        ShortcutAction.SeekForward5 -> seekBy(5_000)
        ShortcutAction.SeekBack10 -> seekBy(-10_000)
        ShortcutAction.SeekForward10 -> seekBy(10_000)
        ShortcutAction.ToggleFullscreen -> toggleFullscreen()
        ShortcutAction.ToggleMute -> session.toggleMute()
        ShortcutAction.VolumeUp -> session.setVolume(session.volume.value + VOLUME_STEP)
        ShortcutAction.VolumeDown -> session.setVolume(session.volume.value - VOLUME_STEP)
        ShortcutAction.SpeedDown -> session.setSpeed(steppedSpeed(player.speed, up = false))
        ShortcutAction.SpeedUp -> session.setSpeed(steppedSpeed(player.speed, up = true))
    }
}
