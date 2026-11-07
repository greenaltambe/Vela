package com.greenaltambe.vela.ui.look

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.ui.theme.ExtraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** The hull under the logo's sail, in the logo's own coordinates (the mark itself has only a wave line there). */
private const val HULL = "M146,346L388,346C380,376 352,392 322,392L196,392C168,392 152,376 146,346Z"

/**
 * The start-up screen: the sea rises, the logo's ship sails in fast from the left (bow up, settling level) and rides animated waves in the middle
 * while the app loads; once [isReady] says the first page is there (or after [maxMs]) it speeds off to the right and the sea drains away down the
 * screen, uncovering the app. Short on purpose: about two seconds when the app is ready, [maxMs] at most. It covers the app and swallows touches
 * until it is done, then calls [onFinished] (the caller drops it from the tree).
 *
 * The red sea matches the system splash of Android 12+ so the hand-over from the system's static icon is not a flash.
 */
@Composable
fun StartupSplash(isReady: () -> Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier, maxMs: Long = 2_800, minDwellMs: Long = 800) {
    val sea = MaterialTheme.colorScheme.primary
    val foam = MaterialTheme.colorScheme.onPrimary
    val deepWater = ExtraColors.mediaBackground
    val sail = remember { PathParser().parsePathString(Mark.SAIL).toPath() }
    val jib = remember { PathParser().parsePathString(Mark.JIB).toPath() }
    val mast = remember { PathParser().parsePathString(Mark.MAST).toPath() }
    val hull = remember { PathParser().parsePathString(HULL).toPath() }

    // Where the ship is across the screen: -1 = one screen width to the left of the centre, 0 = the centre, +1 = one to the right.
    val x = remember { Animatable(-1.1f) }
    val rise = remember { Animatable(0f) }   // 0..1: the sea coming up from the bottom edge
    val drain = remember { Animatable(0f) }  // 0..1: the sea running down off the bottom, the sky fading to reveal the app
    val dash = remember { Animatable(0f) }   // 0..1: speeding off, the bow lifting

    val wobble = rememberInfiniteTransition(label = "sea")
    val phase1 by wobble.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "w1")
    val phase2 by wobble.animateFloat(0f, 1f, infiniteRepeatable(tween(3700, easing = LinearEasing)), label = "w2")
    val phase3 by wobble.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing)), label = "w3")
    val rock by wobble.animateFloat(-1f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rock")
    val bob by wobble.animateFloat(-1f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        launch { rise.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        x.animateTo(0f, tween(520, easing = CubicBezierEasing(0.1f, 0.7f, 0.2f, 1f))) // in fast, settling at the centre
        // Stay while loading: a beat at least, and never past the cap.
        var waited = 0L
        while (waited < minDwellMs || (!isReady() && (withFrameNanos { it } - start) / 1_000_000 < maxMs)) { delay(40); waited += 40 }
        // Off to the right, bow up as it picks up speed.
        launch { dash.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        x.animateTo(1.3f, tween(440, easing = CubicBezierEasing(0.6f, 0f, 0.9f, 0.45f)))
        drain.animateTo(1f, tween(380, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.4f)))
        onFinished()
    }

    // While the sea drains the sky clears quickly, so the app shows through behind the falling water.
    val skyAlpha = (1f - (drain.value * 1.6f)).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }.testTag("startup.splash")) {
        Box(Modifier.fillMaxSize().alpha(skyAlpha).background(sea))
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val boat = min(w * 0.56f, h * 0.34f)
            val k = boat / Mark.CROP
            val cx = w / 2f + x.value * w
            val cy = h / 2f + bob * boat * 0.025f
            // The waterline: rises from the bottom edge at the start, falls away at the end.
            val rest = h / 2f + boat * 0.40f
            val level = rest + (1f - rise.value) * (h - rest) + drain.value * (h - rest + boat * 0.3f)

            fun wave(baseline: Float, amp: Float, length: Float, phase: Float, deep: Float) {
                val edge = Path()
                var px = 0f
                while (px <= w + 8f) {
                    val py = baseline + amp * sin(((px / length) + phase) * 2f * PI.toFloat())
                    if (px == 0f) edge.moveTo(px, py) else edge.lineTo(px, py)
                    px += 8f
                }
                val area = Path().apply { addPath(edge); lineTo(w + 8f, h + boat); lineTo(0f, h + boat); close() }
                drawPath(area, deepWater.copy(alpha = deep))
                drawPath(edge, foam.copy(alpha = 0.55f), style = Stroke(width = boat * 0.018f, cap = StrokeCap.Round))
            }
            // The bow lifts while the ship is coming in fast and while it leaves; level in between, with the swell rocking it.
            val arriving = (-x.value).coerceIn(0f, 1.1f) / 1.1f
            val lift = -(arriving * 7f + dash.value * 8f)
            // Back wave, then the ship, then the nearer waves in front of its hull.
            wave(level - boat * 0.02f, boat * 0.035f, boat * 1.5f, phase2, 0.06f)
            rotate(degrees = rock * 3.5f + lift, pivot = Offset(cx, level)) {
                withTransform({ translate(cx - boat / 2f - (Mark.CENTER_X - Mark.CROP / 2f) * k, cy - boat / 2f - (Mark.CENTER_Y - Mark.CROP / 2f) * k); scale(k, k, Offset.Zero) }) {
                    drawPath(sail, foam); drawPath(jib, foam.copy(alpha = 0.92f))
                    drawPath(mast, foam, style = Stroke(width = 10f, cap = StrokeCap.Round))
                    drawPath(hull, foam)
                }
            }
            wave(level + boat * 0.035f, boat * 0.05f, boat * 1.1f, -phase1, 0.10f)
            wave(level + boat * 0.11f, boat * 0.06f, boat * 0.8f, phase3, 0.16f)
        }
    }
}
