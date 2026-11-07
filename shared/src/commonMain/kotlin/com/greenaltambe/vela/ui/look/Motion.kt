package com.greenaltambe.vela.ui.look

import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import com.greenaltambe.vela.ui.theme.MotionTokens
import com.greenaltambe.vela.ui.theme.Accent
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.LocalMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Stable
import kotlinx.coroutines.delay

/**
 * The single source of animation numbers (brief section 6): durations 90/160/240/360 ms, three spring feels, two easings, all from
 * [MotionTokens]. Nothing else in the app spells a duration or a spring. With [MotionTokens.reduced] (Reduce motion or Performance
 * mode) durations shrink and the springs lose their bounce.
 */
object VelaMotion {
    enum class Level { PRESS, QUICK, STANDARD, SLOW }

    fun durationMs(m: MotionTokens, level: Level): Int =
        (m.durationsMs[level.ordinal] * if (m.reduced) m.reducedFactor else 1f).toInt().coerceAtLeast(1)

    fun <T> tween(m: MotionTokens, level: Level = Level.STANDARD, emphasized: Boolean = false): FiniteAnimationSpec<T> =
        tween(durationMs(m, level), easing = if (emphasized) FastOutSlowInEasing else androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))

    private fun <T> springOf(p: List<Float>, m: MotionTokens): FiniteAnimationSpec<T> =
        if (m.reduced) tween(durationMs(m, Level.QUICK)) else spring(dampingRatio = p[0], stiffness = p[1])

    fun <T> snappy(m: MotionTokens) = springOf<T>(m.snappy, m)
    fun <T> standard(m: MotionTokens) = springOf<T>(m.standard, m)
    fun <T> bouncy(m: MotionTokens) = springOf<T>(m.bouncy, m)

    /** Delay before list item [index] fades in on first load; 0 when motion is reduced, and capped so a long list never waits. */
    fun staggerDelayMs(m: MotionTokens, index: Int): Int = if (m.reduced) 0 else (index.coerceAtMost(MAX_STAGGERED) * m.staggerMs)

    const val MAX_STAGGERED = 8
    const val PRESS_SCALE = 0.96f
    const val PRESS_DIM = 0.88f
}

/** Looping animations currently running anywhere; tests read it to prove Reduce motion and Performance mode leave none. */
object ActiveLoops {
    var count = 0
        private set
    fun started() { count++ }
    fun stopped() { count-- }
}

/** False while the app is in the background: loops stop so nothing animates (and draws frames) out of sight. */
val LocalAnimationsActive = compositionLocalOf { true }

/** Keys whose entrance already played in this session, so scrolling back never replays it ("first load only"). */
@Stable
class AppearedKeys { val seen = HashSet<Any>() }
val LocalAppeared = compositionLocalOf { AppearedKeys() }

/** A click target with Material's own press indication (the ripple of the theme). */
fun Modifier.pressable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, role = role, onClick = onClick)

/**
 * A short fade + rise the first time an item with [key] appears (staggered by [index]); never again for that key. Instant under
 * Reduce motion / Performance mode (no animation object is created at all).
 */
@Composable
fun Modifier.appearOnce(key: Any, index: Int = 0): Modifier {
    val m = LocalMotion.current
    val seen = LocalAppeared.current.seen
    // Only the first screenful enters with a stagger; rows that scroll into view later just appear.
    val first = remember(key) { index < VelaMotion.MAX_STAGGERED && seen.add(key) }
    if (m.reduced || !first) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(VelaMotion.staggerDelayMs(m, index).toLong())
        progress.animateTo(1f, VelaMotion.tween(m, VelaMotion.Level.STANDARD))
    }
    return this.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 24f }
}

/**
 * A shimmer sweep for skeletons. A looping animation, so it exists only while the app is visible and motion is not reduced;
 * otherwise a flat block. [ActiveLoops] counts it.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerLow
    val m = LocalMotion.current
    if (m.reduced || !LocalAnimationsActive.current) return this.background(base)
    androidx.compose.runtime.DisposableEffect(Unit) { ActiveLoops.started(); onDispose { ActiveLoops.stopped() } }
    val t = rememberInfiniteTransition(label = "shimmer")
    val x = t.animateFloat(-1f, 2f, infiniteRepeatable(tween(VelaMotion.durationMs(m, VelaMotion.Level.SLOW) * 4, easing = LinearEasing), RepeatMode.Restart), label = "shimmerX")
    // The sweep is read while drawing: 60 or 120 frames a second redraw the block and recompose nothing.
    return this.background(base).drawBehind {
        val at = x.value * 600f
        drawRect(Brush.linearGradient(listOf(base, hi, base), start = androidx.compose.ui.geometry.Offset(at, 0f), end = androidx.compose.ui.geometry.Offset(at + 400f, 200f)))
    }
}

/** One toast: [message] and an optional Undo. */
class UndoToast(val message: String, val onUndo: (() -> Unit)?)

/** Where undo toasts are raised from: `LocalUndoToasts.current.show("Corners → Round", undo)`. */
@Stable
class UndoToasts {
    var current by mutableStateOf<UndoToast?>(null)
        private set
    fun show(message: String, onUndo: (() -> Unit)? = null) { current = UndoToast(message, onUndo) }
    fun dismiss() { current = null }
}
val LocalUndoToasts = compositionLocalOf { UndoToasts() }

/**
 * Draws the current toast as a Material snackbar (message and an Undo action) and dismisses it after [seconds]; Undo runs once and dismisses.
 * [showHost] false when the screen draws it itself with [UndoSnackbarHost] where it should sit (the app shell: above the mini player and the bar).
 */
@Composable
fun UndoHost(toasts: UndoToasts, modifier: Modifier = Modifier, seconds: Int = 5, showHost: Boolean = true, content: @Composable () -> Unit) {
    Box(modifier) {
        CompositionLocalProvider(LocalUndoToasts provides toasts) { content() }
        if (showHost) UndoSnackbarHost(toasts, Modifier.align(Alignment.BottomCenter), seconds)
    }
}

@Composable
fun UndoSnackbarHost(toasts: UndoToasts, modifier: Modifier = Modifier, seconds: Int = 5) {
    val host = remember { SnackbarHostState() }
    val toast = toasts.current
    LaunchedEffect(toast) {
        if (toast == null) { host.currentSnackbarData?.dismiss(); return@LaunchedEffect }
        val result = withTimeoutOrNull(seconds * 1000L) { host.showSnackbar(toast.message, actionLabel = if (toast.onUndo != null) "Undo" else null, duration = SnackbarDuration.Indefinite) }
        if (result == SnackbarResult.ActionPerformed) toast.onUndo?.invoke()
        if (toasts.current === toast) toasts.dismiss()
    }
    SnackbarHost(host, modifier) { data ->
        Snackbar(
            Modifier.testTag("undo.toast"),
            action = data.visuals.actionLabel?.let { label -> { TextButton(onClick = { data.performAction() }, modifier = Modifier.testTag("undo.action")) { Text(label) } } },
        ) { Text(data.visuals.message, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
