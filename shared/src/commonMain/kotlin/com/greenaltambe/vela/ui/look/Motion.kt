package com.greenaltambe.vela.ui.look

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

/** Press feedback on every tappable: ~0.96 scale and a slight dim while a finger is down. */
@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource): Modifier {
    val m = Vela.motion
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) VelaMotion.PRESS_SCALE else 1f, VelaMotion.snappy(m), label = "pressScale")
    val dim by androidx.compose.animation.core.animateFloatAsState(if (pressed) VelaMotion.PRESS_DIM else 1f, VelaMotion.tween(m, VelaMotion.Level.PRESS), label = "pressDim")
    return this.graphicsLayer { scaleX = scale; scaleY = scale; alpha = dim }
}

/** A visible ring while an item has keyboard focus (indication is off on pressable items, so without this focus would be invisible). */
@Composable
fun Modifier.focusRing(source: MutableInteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    return if (focused) this.border(2.dp, Vela.colors.primary.toColor(), Vela.mediumShape) else this
}

/** [clickable] with [pressFeedback]. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.pressFeedback(source).focusRing(source).clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/**
 * A short fade + rise the first time an item with [key] appears (staggered by [index]); never again for that key. Instant under
 * Reduce motion / Performance mode (no animation object is created at all).
 */
@Composable
fun Modifier.appearOnce(key: Any, index: Int = 0): Modifier {
    val m = Vela.motion
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
    val base = Vela.colors.surfaceVariant.toColor()
    val hi = Vela.colors.surface.toColor()
    val m = Vela.motion
    if (m.reduced || !LocalAnimationsActive.current) return this.background(base)
    androidx.compose.runtime.DisposableEffect(Unit) { ActiveLoops.started(); onDispose { ActiveLoops.stopped() } }
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(VelaMotion.durationMs(m, VelaMotion.Level.SLOW) * 4, easing = LinearEasing), RepeatMode.Restart), label = "shimmerX")
    return this.background(Brush.linearGradient(listOf(base, hi, base), start = androidx.compose.ui.geometry.Offset(x * 600f, 0f), end = androidx.compose.ui.geometry.Offset(x * 600f + 400f, 200f)))
}

/**
 * Segmented pills (2-4 options) with the selection outline SLIDING between them; the outline is the wake gradient stroke, the one
 * place the signature accent marks "selected" besides toggles and the active tab.
 */
@Composable
fun SegmentedPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tag: String = "pills") {
    val m = Vela.motion
    val index by androidx.compose.animation.core.animateFloatAsState(selected.toFloat(), VelaMotion.standard(m), label = "pillIndex")
    val wake = Brush.horizontalGradient(Vela.look.accent.wake.map { it.toColor() })
    BoxWithConstraints(modifier.height(Vela.minTouch).clip(Vela.mediumShape).background(Vela.colors.surfaceVariant.toColor()).testTag(tag)) {
        val each = maxWidth / options.size
        Box(Modifier.offset { IntOffset((index * each.toPx()).toInt(), 0) }.width(each).fillMaxHeight().padding(2.dp).border(2.dp, wake, Vela.mediumShape).testTag("$tag.outline"))
        Row(Modifier.fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.width(each).fillMaxHeight().clip(Vela.mediumShape).semantics { role = Role.RadioButton; this.selected = i == selected }.pressable { onSelect(i) }.testTag("$tag.$i"),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i == selected) Vela.colors.onSurface.toColor() else Vela.colors.onSurfaceVariant.toColor()) }
            }
        }
    }
}

/** A switch whose thumb slides and stretches while pressed; "on" is marked by the wake gradient track. */
@Composable
fun VelaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val m = Vela.motion
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pos by androidx.compose.animation.core.animateFloatAsState(if (checked) 1f else 0f, VelaMotion.bouncy(m), label = "switchPos")
    val thumbW by androidx.compose.animation.core.animateFloatAsState(if (pressed) 28f else 20f, VelaMotion.snappy(m), label = "switchThumb")
    val track = if (checked) Brush.horizontalGradient(Vela.look.accent.wake.map { it.toColor() }) else Brush.horizontalGradient(listOf(Vela.colors.outline.toColor(), Vela.colors.outline.toColor()))
    Box(
        modifier.size(52.dp, 32.dp).alpha(if (enabled) 1f else 0.5f).clip(Vela.pillShape).background(track)
            .semantics { role = Role.Switch; this.selected = checked }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset { IntOffset(((4f + pos * (52f - 8f - thumbW)) * density).toInt(), 0) }.size(thumbW.dp, 24.dp).clip(Vela.pillShape).background(Vela.colors.background.toColor()).testTag("switch.thumb"))
    }
}

/** A little pop when [selected] turns on (like, subscribe, watch later): scale up on a bouncy spring and back. */
@Composable
fun Modifier.popOnSelect(selected: Boolean): Modifier {
    val m = Vela.motion
    val s = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first) { first = false; return@LaunchedEffect }
        if (selected && !m.reduced) { s.snapTo(0.8f); s.animateTo(1f, VelaMotion.bouncy(m)) }
    }
    return this.scale(s.value)
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

/** Draws the current toast at the bottom with a spring in/out and dismisses it after [seconds]; Undo runs once and dismisses. */
@Composable
fun UndoHost(toasts: UndoToasts, modifier: Modifier = Modifier, seconds: Int = 5, content: @Composable () -> Unit) {
    val m = Vela.motion
    Box(modifier) {
        CompositionLocalProvider(LocalUndoToasts provides toasts) { content() }
        val toast = toasts.current
        LaunchedEffect(toast) { if (toast != null) { delay(seconds * 1000L); if (toasts.current === toast) toasts.dismiss() } }
        androidx.compose.animation.AnimatedVisibility(
            toast != null, Modifier.align(Alignment.BottomCenter).padding(Vela.md),
            enter = androidx.compose.animation.slideInVertically(VelaMotion.bouncy(m)) { it } + androidx.compose.animation.fadeIn(VelaMotion.tween(m, VelaMotion.Level.QUICK)),
            exit = androidx.compose.animation.slideOutVertically(VelaMotion.snappy(m)) { it } + androidx.compose.animation.fadeOut(VelaMotion.tween(m, VelaMotion.Level.QUICK)),
        ) {
            val shown = remember(toast) { toast }   // keep the text while it animates out
            Row(
                Modifier.clip(Vela.largeShape).background(Vela.colors.onSurface.toColor()).padding(horizontal = Vela.md, vertical = Vela.sm).testTag("undo.toast"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Vela.md),
            ) {
                Text(shown?.message.orEmpty(), color = Vela.colors.surface.toColor(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                shown?.onUndo?.let { undo -> Text("Undo", color = Vela.colors.primary.toColor(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.pressable { undo(); toasts.dismiss() }.padding(Vela.xs).testTag("undo.action")) }
            }
        }
    }
}
