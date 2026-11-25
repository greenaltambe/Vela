package com.greenaltambe.vela.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.theme.LocalMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** The moment a bar that is not animating shows. */
private const val STILL_TIME = 0.37f

/**
 * The Lightning style's clock, in seconds. Unlike the wave's phase it does not loop every couple of seconds (the same bolts coming round again would
 * show), and stopping it holds the current picture. It moves in steps of a thirtieth of a second, the cadence of the footage the motion was taken from,
 * whatever the screen's refresh rate.
 * Read it inside a draw block only.
 */
@Composable
fun rememberLightningClock(active: Boolean): State<Float> {
    // Plain memory, not state: where the clock stood when it was last drawn, so a pause holds that picture and a resume carries on from it.
    val held = remember { floatArrayOf(STILL_TIME) }
    val run = active && !LocalMotion.current.reduced && com.greenaltambe.vela.ui.look.LocalAnimationsActive.current
    if (!run) return remember { mutableFloatStateOf(held[0]) }
    val from = remember { held[0] }
    val seconds = rememberInfiniteTransition(label = "lightning").animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing)), label = "lightningSeconds")
    return remember(seconds) { derivedStateOf { (from + floor(seconds.value * 30f) / 30f).also { held[0] = it } } }
}

/*
 * The motion follows filmed Tesla coil arcs (measured at 30 pictures a second): one channel persists and wriggles in place, moving about a hundredth of
 * its length from picture to picture; after a fifth of a second or so (now and then nearly a second) it breaks and the discharge takes a wholly new
 * path; while it looks for one, thin filaments fan out of the source. The channel is a sinuous bundle of strands that part and rejoin.
 */

/** The soft light round the channel, widest first: stroke width in dp to opacity. It stays close to the arc, as the light of a real one does. */
private val GLOW = listOf(13f to 0.05f, 8f to 0.1f, 4.6f to 0.28f)

/** Channels break at about this spacing in seconds; each break is moved about, and some are skipped, so lifetimes run from a tenth to nearly a second. */
private const val CHANNEL_SLOT = 0.22f

/** A repeatable pseudo-random number in 0..1 for the three integers: the same moment always draws the same bolt. */
private fun noise(a: Int, b: Int, c: Int = 0): Float {
    var h = a * 374761393 + b * 668265263 + c * 1274126177
    h = (h xor (h ushr 13)) * 1103515245
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

/** [noise] in -1..1. */
private fun jitter(a: Int, b: Int, c: Int = 0): Float = noise(a, b, c) * 2f - 1f

private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** A random value in -1..1 for [a] that moves smoothly to a new one [rate] times a second. */
private fun drift(a: Int, seed: Int, time: Float, rate: Float): Float {
    val clock = time * rate + noise(a, seed, 1)
    val step = floor(clock).toInt()
    val t = smooth(clock - step)
    return jitter(a, step, seed) * (1f - t) + jitter(a, step + 1, seed) * t
}

/** The path a discharge is following at some moment: which one, how long it has had it, and how long until it breaks. */
internal class Channel(val id: Int, val age: Float, val left: Float)

private fun breakTime(k: Int): Float = (k + 0.7f * noise(k, 201)) * CHANNEL_SLOT

/** A quarter of the breaks are skipped (the channel holds on), but never four in a row. */
private fun breaks(k: Int): Boolean = k.mod(4) == 0 || noise(k, 202) > 0.25f

internal fun channelAt(time: Float): Channel {
    var k = floor(time / CHANNEL_SLOT).toInt() + 1
    while (breakTime(k) > time || !breaks(k)) k--
    var next = k + 1
    while (!breaks(next)) next++
    return Channel(k, time - breakTime(k), breakTime(next) - time)
}

/**
 * A smooth random curve through knots [spacing] apart, sampled at [at]: each knot has a place that belongs to the channel [id] and drifts about it
 * [rate] times a second, so the curve writhes while it stays recognisably the same, and is another altogether on the next channel.
 */
private fun curve(at: Float, spacing: Float, id: Int, seed: Int, time: Float, rate: Float): Float {
    val u = at / spacing
    val i = floor(u).toInt()
    val t = u - i
    fun knot(n: Int) = (jitter(n, id, seed) * 0.75f + drift(n, seed + id * 31, time, rate) * 0.65f) / 1.15f
    val p0 = knot(i - 1); val p1 = knot(i); val p2 = knot(i + 1); val p3 = knot(i + 2)
    // Catmull-Rom: it passes through every knot with no flat spots, which gives the curls a real arc has.
    return 0.5f * (2f * p1 + (p2 - p0) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t + (3f * p1 - p0 - 3f * p2 + p3) * t * t * t)
}

/** The points of the main channel from the start of the track at [from] to the source at [to], pinched to the line at both ends. */
private fun DrawScope.channelPoints(from: Float, to: Float, cy: Float, channel: Channel, time: Float, power: Float): List<Offset> {
    val step = 2.5f.dp.toPx()
    val taper = 20.dp.toPx()
    // Freshly struck it is fairly straight; the kinks grow as the hot channel is pushed about.
    val grown = 0.7f + 0.3f * smooth(channel.age / 0.25f)
    val count = ((to - from) / step).toInt() + 1
    return List(count + 1) { n ->
        val d = minOf(n * step, to - from)
        val ease = smooth(minOf(d, to - from - d) / taper)
        val bends = 18.dp.toPx() * curve(d, 54.dp.toPx(), channel.id, 11, time, 2.6f) +
            8.dp.toPx() * curve(d, 19.dp.toPx(), channel.id, 12, time, 4.5f) +
            2.4f.dp.toPx() * curve(d, 7.dp.toPx(), channel.id, 13, time, 7f)
        // As bold as the footage allows within the bar: large swings are eased in towards a limit, never cut off flat.
        val limit = 18.dp.toPx()
        val across = limit * bends / sqrt(limit * limit + bends * bends)
        // A little give along the bar too, so the curls can lean over on themselves.
        val along = 4.5f.dp.toPx() * curve(d, 15.dp.toPx(), channel.id, 14, time, 4f)
        Offset(from + d + along * ease, cy + across * ease * grown * power)
    }
}

/** A further strand of the bundle: mostly lying on the main channel, parting from it here and there into a loop, most widely near the far end. */
private fun DrawScope.companionPoints(main: List<Offset>, from: Float, to: Float, channel: Channel, time: Float, power: Float, which: Int): List<Offset> {
    val side = if (which == 0) 1f else -1f
    return main.map { p ->
        val d = p.x - from
        val ease = smooth(minOf(d, to - p.x) / 14.dp.toPx())
        val parted = (curve(d, 34.dp.toPx(), channel.id, 31 + which, time, 3.5f) - 0.1f).coerceAtLeast(0f)
        val fanned = 1f - smooth((d - 20.dp.toPx()) / 70.dp.toPx())
        val across = side * (parted * 9f + fanned * 4.5f).dp.toPx() * power +
            0.9f.dp.toPx() * curve(d, 9.dp.toPx(), channel.id, 41 + which, time, 7f)
        Offset(p.x, p.y + across * ease)
    }
}

/** [points] as a path, broken (the pen lifted) within [gapHalf] of each of [gaps]. */
private fun strandPath(points: List<Offset>, gaps: List<Float>, gapHalf: Float): Path {
    val path = Path()
    var pen = false
    for (n in 1 until points.size) {
        val a = points[n - 1]
        val b = points[n]
        if (gaps.any { kotlin.math.abs((a.x + b.x) / 2f - it) < gapHalf }) {
            pen = false
            continue
        }
        if (!pen) path.moveTo(a.x, a.y)
        path.lineTo(b.x, b.y)
        pen = true
    }
    return path
}

/** A line through [points] that is [width] wide where it starts and closes to a point at its end: one filled shape, so joints never show as beads. */
private fun taperedPath(points: List<Offset>, width: Float): Path {
    val left = ArrayList<Offset>(points.size)
    val right = ArrayList<Offset>(points.size)
    for (i in points.indices) {
        val before = points[maxOf(i - 1, 0)]
        val after = points[minOf(i + 1, points.lastIndex)]
        val dx = after.x - before.x
        val dy = after.y - before.y
        val norm = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val half = width / 2f * (1f - i.toFloat() / points.lastIndex)
        val side = Offset(-dy / norm * half, dx / norm * half)
        left += points[i] + side
        right += points[i] - side
    }
    return Path().apply {
        moveTo(left[0].x, left[0].y)
        for (i in 1 until left.size) lineTo(left[i].x, left[i].y)
        for (i in right.indices.reversed()) lineTo(right[i].x, right[i].y)
        close()
    }
}

/**
 * A thin wandering filament from [root], thrown back towards the start of the bar: [length] long, leaving at [angle] to the track on [side] (-1 up,
 * 1 down), with [twig] adding one finer offshoot half way. It writhes with the same smooth noise as the channel.
 */
private fun DrawScope.drawFilament(root: Offset, side: Float, length: Float, angle: Float, alpha: Float, id: Int, time: Float, color: Color, hot: Color, twig: Boolean) {
    if (alpha <= 0.02f || length < 4.dp.toPx()) return
    val count = (length / 3.dp.toPx()).toInt().coerceIn(4, 40)
    // A long one leaves at a shallower angle, so that it stays near the bar without being flattened against an edge.
    val climb = minOf(sin(angle), 17.dp.toPx() / length)
    val points = List(count + 1) { n ->
        val d = length * n / count
        val ease = smooth(d / 10.dp.toPx())
        val wander = ease * (5.dp.toPx() * curve(d, 22.dp.toPx(), id, 51, time, 4.5f) + 2.dp.toPx() * curve(d, 8.dp.toPx(), id, 52, time, 7f))
        // It bends back towards the track's direction the farther it gets, and stays near the bar.
        val rise = climb * d * (1f - 0.35f * d / length)
        Offset(root.x - cos(angle) * d - side * climb * wander * 0.5f, root.y + side * rise + wander)
    }
    drawPath(taperedPath(points, 4.dp.toPx()), color, alpha = alpha * 0.22f)
    drawPath(taperedPath(points, 1.5f.dp.toPx()), lerp(color, hot, 0.35f), alpha = alpha)
    if (twig) drawFilament(points[count * 2 / 5], if (noise(id, 53) < 0.6f) side else -side, length * 0.38f, angle + 0.45f, alpha * 0.7f, id * 7 + 3, time, color, hot, false)
}

/**
 * The played part of the Lightning style: an arc drawn from the handle at [xNow], its source, to the start of the track. [time] is
 * [rememberLightningClock]'s value; [boldScale] above 1 (the bar is being dragged) overcharges it. [played] are the SponsorBlock pieces left of the
 * handle, [gaps] the x positions of the chapter boundaries.
 */
internal fun DrawScope.drawLightning(
    g: ScrubGeometry,
    cy: Float,
    xNow: Float,
    colors: List<Color>,
    color: Color,
    time: Float,
    grow: Float,
    boldScale: Float,
    played: List<MarkerSpan>,
    gaps: List<Float>,
    white: Color,
) {
    val charge = ((boldScale - 1f) / 0.55f).coerceIn(0f, 1.2f)
    val power = 1f + 0.4f * charge
    val hot = lerp(color, white, 0.8f)
    val x0 = g.trackStart
    val length = xNow - x0
    val round = StrokeCap.Round
    val channel = channelAt(time)
    val picture = floor(time * 30f + 0.5f).toInt()
    // About every other break is a search: the channel fades and filaments feel for a new path.
    val searching = noise(channel.id, 203) < 0.5f && channel.left < 0.12f
    val struck = 1f - smooth(channel.age / 0.1f)
    val intensity = (0.86f + 0.14f * noise(picture, 204)) * (if (searching) 0.55f else 1f)

    if (length > 1f) {
        val brush = Brush.horizontalGradient(colors, startX = x0, endX = g.trackEnd.coerceAtLeast(x0 + 1f))
        // A chapter gap is only cut when the chapters are far enough apart for the arc to survive it.
        val gapHalf = 2.75f.dp.toPx()
        val cut = if (gaps.isNotEmpty() && g.trackWidth / (gaps.size + 1) > gapHalf * 6f) gaps else emptyList()
        val points = channelPoints(x0, xNow, cy, channel, time, power)
        val core = strandPath(points, cut, gapHalf)

        for ((width, alpha) in GLOW) drawPath(core, brush, alpha = alpha * intensity * (1f + 0.6f * struck), style = Stroke(width = width.dp.toPx() * power, cap = round, join = StrokeJoin.Round))

        for (which in 0 until 2) {
            val strand = strandPath(companionPoints(points, x0, xNow, channel, time, power, which), cut, gapHalf)
            val alpha = intensity * (if (which == 0) 0.85f else 0.6f)
            drawPath(strand, brush, alpha = alpha * 0.35f, style = Stroke(width = 2.6f.dp.toPx(), cap = round, join = StrokeJoin.Round))
            drawPath(strand, lerp(color, hot, 0.45f), alpha = alpha, style = Stroke(width = 0.8f.dp.toPx(), cap = round, join = StrokeJoin.Round))
        }

        drawPath(core, brush, alpha = minOf(1f, intensity + 0.1f), style = Stroke(width = (2.5f + 0.8f * struck).dp.toPx() * power, cap = round, join = StrokeJoin.Round))
        drawPath(core, lerp(hot, white, 0.5f + 0.5f * struck), alpha = intensity, style = Stroke(width = (1.15f + 0.5f * struck).dp.toPx() * power, cap = round, join = StrokeJoin.Round))

        // SponsorBlock pieces take their colour along the arc, keeping a thin hot thread so they stay lit.
        for (s in played) {
            clipRect(left = s.x0, top = 0f, right = s.x1, bottom = size.height) {
                drawPath(core, s.color, alpha = 0.22f, style = Stroke(width = 7.dp.toPx() * power, cap = round, join = StrokeJoin.Round))
                drawPath(core, s.color, style = Stroke(width = 3.2f.dp.toPx() * power, cap = round, join = StrokeJoin.Round))
                drawPath(core, white, alpha = 0.55f, style = Stroke(width = 0.9f.dp.toPx() * power, cap = round, join = StrokeJoin.Round))
            }
        }

        val room = minOf(length, 130.dp.toPx() * power)
        val head = Offset(xNow, cy)
        if (searching) {
            // The fan: several long filaments leave the source at once.
            for (f in 0 until 3 + (2 * charge).toInt()) {
                val id = channel.id * 97 + f
                val side = if (f % 2 == 0) -1f else 1f
                drawFilament(head, side, room * (0.4f + 0.6f * noise(id, 205)), 0.08f + 0.5f * noise(id, 206), 0.8f, id, time, color, hot, true)
            }
        }
        // Whiskers at the source: short, faint, each gone in a few pictures.
        for (f in 0 until 3 + (3 * charge).toInt()) {
            val clock = time * (5f + 3f * noise(f, 207)) + noise(f, 208)
            val life = floor(clock).toInt()
            val age = clock - life
            val id = 40009 + f * 1013 + life
            val side = if (noise(id, 209) < 0.5f) -1f else 1f
            drawFilament(head, side, minOf(room, (14f + 38f * noise(id, 210) * noise(id, 211)).dp.toPx() * power), 0.2f + 0.8f * noise(id, 212), 0.65f * (1f - age), id, time, color, hot, noise(id, 213) < 0.35f)
        }
        // And now and then the channel itself leaks one, farther back.
        for (f in 0 until 1 + (length / 130.dp.toPx()).toInt().coerceAtMost(4)) {
            val id = channel.id * 89 + 500 + f
            if (noise(id, 214) < 0.45f) continue
            val root = points[((points.size - 1) * noise(id, 215)).toInt()]
            val fade = 1f - smooth(channel.age / (0.12f + 0.2f * noise(id, 216)))
            val side = if (root.y < cy) -1f else 1f
            drawFilament(root, side, minOf(root.x - x0, (18f + 34f * noise(id, 217)).dp.toPx() * power), 0.25f + 0.5f * noise(id, 218), 0.7f * fade, id, time, color, hot, true)
        }
    }

    // The source: a soft halo and a white-hot point that flickers with the arc.
    val head = Offset(xNow, cy)
    val size = grow * (1f + 0.35f * charge) * (0.93f + 0.07f * noise(picture, 41) + 0.12f * struck)
    val halo = 17.dp.toPx() * size
    drawCircle(Brush.radialGradient(0f to hot.copy(alpha = 0.7f), 0.3f to color.copy(alpha = 0.38f), 0.65f to color.copy(alpha = 0.1f), 1f to color.copy(alpha = 0f), center = head, radius = halo), halo, head)
    val ball = 6.5f.dp.toPx() * size
    drawCircle(Brush.radialGradient(0f to white, 0.42f to hot, 0.7f to color, 1f to color.copy(alpha = 0f), center = head, radius = ball), ball, head)
    drawCircle(white, 2.3f.dp.toPx() * size, head)
}
