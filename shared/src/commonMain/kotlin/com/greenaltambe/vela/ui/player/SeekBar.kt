package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.Vela

/** A coloured span of the timeline (a SponsorBlock segment, once those exist). */
data class SeekMarker(val startMs: Long, val endMs: Long, val color: Color)

private val trackInset = 10.dp // where Material's slider track starts and ends inside its bounds

/**
 * The seek slider with chapter ticks and coloured [markers] drawn over its track. Dragging only previews; the seek
 * happens once, on release.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    markers: List<SeekMarker>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (Long?) -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1).toFloat()
    val tick = Vela.colors.chapterMarker.let { Color(it.toInt()) }
    Box(modifier.fillMaxWidth().height(32.dp).testTag(ComponentIds.SEEK_BAR), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal = trackInset).height(32.dp)) {
            if (durationMs <= 0) return@Canvas
            val y = size.height / 2
            markers.forEach { m ->
                val x0 = (m.startMs / duration).coerceIn(0f, 1f) * size.width
                val x1 = (m.endMs / duration).coerceIn(0f, 1f) * size.width
                drawRect(m.color, Offset(x0, y - 3.dp.toPx()), Size((x1 - x0).coerceAtLeast(2.dp.toPx()), 6.dp.toPx()))
            }
            chapters.drop(1).forEach { c ->
                val x = (c.startSeconds * 1000 / duration).coerceIn(0f, 1f) * size.width
                drawRect(tick, Offset(x - 1.dp.toPx(), y - 5.dp.toPx()), Size(2.dp.toPx(), 10.dp.toPx()))
            }
        }
        Slider(
            value = (dragging ?: positionMs.toFloat()).coerceIn(0f, duration),
            onValueChange = { dragging = it; onDrag(it.toLong()) },
            onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null; onDrag(null) },
            valueRange = 0f..duration,
            enabled = durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
