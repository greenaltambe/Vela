package com.greenaltambe.vela.ui.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Frame times as the app draws them, for Android and Linux alike (on Android `dumpsys gfxinfo` is the reference; this is the same
 * idea inside the process so later tasks can measure on both). [budgetMs] is the frame time that counts as jank (16.7 for 60 Hz,
 * 11.1 for 90 Hz). Pure maths, no clock of its own: feed it frame-to-frame deltas.
 */
class FrameStats(val budgetMs: Double = 16.7, private val capacity: Int = 3000) {
    private val deltas = ArrayDeque<Double>()

    fun record(deltaMs: Double) {
        // Gaps over a second are idle time (nothing was animating), not frames.
        if (deltaMs <= 0.0 || deltaMs > 1000.0) return
        deltas.addLast(deltaMs); if (deltas.size > capacity) deltas.removeFirst()
    }

    fun clear() = deltas.clear()

    data class Summary(val frames: Int, val jankyPercent: Double, val p50: Double, val p90: Double, val p95: Double, val p99: Double, val worst: Double, val over100ms: Int) {
        fun line() = "frames=$frames janky=${"%.1f".format(jankyPercent)}% p50=${"%.1f".format(p50)} p90=${"%.1f".format(p90)} p95=${"%.1f".format(p95)} p99=${"%.1f".format(p99)} worst=${"%.0f".format(worst)} ms; >100ms: $over100ms"
    }

    fun summary(): Summary {
        if (deltas.isEmpty()) return Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        val sorted = deltas.sorted()
        fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
        return Summary(sorted.size, deltas.count { it > budgetMs } * 100.0 / sorted.size, pct(0.5), pct(0.9), pct(0.95), pct(0.99), sorted.last(), deltas.count { it > 100.0 })
    }
}

/** A small corner readout of [FrameStats] for debug sessions; recording happens only while [enabled], so it costs nothing otherwise. */
@Composable
fun FrameStatsOverlay(enabled: Boolean, budgetMs: Double = 16.7, onReport: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    if (!enabled) return
    val stats = remember(budgetMs) { FrameStats(budgetMs) }
    var text by remember { mutableStateOf("frames…") }
    LaunchedEffect(stats) {
        var last = 0L; var sinceReport = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) stats.record((now - last) / 1_000_000.0)
                if (now - sinceReport > 2_000_000_000L) { sinceReport = now; val line = stats.summary().line(); text = line; onReport(line); stats.clear() }
                last = now
            }
        }
    }
    Box(modifier.padding(4.dp).background(Color(0xCC000000)).padding(4.dp).testTag("debug.frameStats")) { Text(text, color = Color.White, fontSize = 10.sp) }
}
