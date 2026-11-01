package com.greenaltambe.vela.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class SheetValue {
    COLLAPSED,
    EXPANDED,
    DISMISSED,
}

/**
 * Single source of truth for the persistent video player sheet.
 * [progress]: 0f = fully collapsed (miniplayer), 1f = fully expanded (video page).
 */
@Stable
class PlayerSheetState(
    private val scope: CoroutineScope,
    private val services: AppServices,
    private val navigator: Navigator,
) {
    val animatable = Animatable(0f)

    /** 0f (collapsed / miniplayer) to 1f (expanded / full video page). */
    val progress: Float get() = animatable.value

    var currentVideo by mutableStateOf<VideoSummary?>(null)
        private set

    var startSeconds by mutableStateOf<Long?>(null)
        private set

    val isExpanded: Boolean get() = progress > 0.85f && currentVideo != null
    val isCollapsed: Boolean get() = progress < 0.15f && currentVideo != null
    val isVisible: Boolean get() = currentVideo != null

    /** Restores a video into the collapsed miniplayer without auto-playing. */
    fun restore(video: VideoSummary) {
        if (currentVideo == null) {
            currentVideo = video
            navigator.setNowPlayingDirect(video)
        }
    }

    /** Open a video and expand the sheet to full screen. */
    fun open(video: VideoSummary, start: Long? = null) {
        val sameVideo = currentVideo?.id == video.id
        currentVideo = video
        startSeconds = start
        navigator.setNowPlayingDirect(video)
        scope.launch {
            if (!sameVideo) {
                // If opening a different video, ensure we animate smoothly up to 1f
                animatable.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            } else if (animatable.value < 0.99f) {
                animatable.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
        }
    }

    /** Expand the currently active video to full screen. */
    fun expand() {
        if (currentVideo == null) return
        scope.launch {
            animatable.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    /** Collapse the currently active video down to miniplayer. */
    fun collapse() {
        if (currentVideo == null) return
        scope.launch {
            animatable.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    /** Directly snap progress during gesture dragging. */
    suspend fun snapProgress(p: Float) {
        animatable.snapTo(p.coerceIn(0f, 1f))
    }

    /** Finish a drag gesture based on release progress and velocity. */
    fun settle(velocityY: Float) {
        scope.launch {
            val target = when {
                velocityY > 350f -> 0f   // Flick down -> collapse immediately
                velocityY < -350f -> 1f  // Flick up -> expand immediately
                progress > 0.60f -> 1f   // Spring back to expanded if released in upper 40%
                else -> 0f               // Pulled down past 40% -> collapse!
            }
            val spec: AnimationSpec<Float> = if (target == 1f) {
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            } else {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            }
            animatable.animateTo(target, spec)
        }
    }

    /** Dismiss the miniplayer, stopping playback. */
    fun dismiss() {
        scope.launch {
            animatable.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            services.player?.session?.stop()
            currentVideo = null
            startSeconds = null
            navigator.clearNowPlaying()
        }
    }
}

@Composable
fun rememberPlayerSheetState(
    services: AppServices,
    navigator: Navigator,
    scope: CoroutineScope = rememberCoroutineScope(),
): PlayerSheetState {
    val state = remember(services, navigator) { PlayerSheetState(scope, services, navigator) }
    return state
}
