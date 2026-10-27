package com.greenaltambe.vela.player.mpv

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pixel size mpv should render at for a widget of [widgetWidth] x [widgetHeight] showing a
 * [videoWidth] x [videoHeight] video: the widget size, scaled down (same aspect) so mpv never scales the picture
 * UP. The caller then stretches the result over the widget, which Skia does on the GPU for far less than mpv's CPU
 * scaler costs at full-screen sizes. Unknown video size (0) leaves the widget size alone.
 */
fun cappedRenderSize(widgetWidth: Int, widgetHeight: Int, videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
    if (widgetWidth <= 0 || widgetHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return widgetWidth to widgetHeight
    // mpv fits the video inside the widget at scale s = min(w/vw, h/vh); rendering at f*(w,h) gives scale f*s, and
    // f*s <= 1 means f <= max(vw/w, vh/h).
    val f = minOf(1.0, max(videoWidth.toDouble() / widgetWidth, videoHeight.toDouble() / widgetHeight))
    return max(1, (widgetWidth * f).roundToInt()) to max(1, (widgetHeight * f).roundToInt())
}

/** `1920x1080@60` -> 1920 to 1080; null if [resolution] is not in that form. */
fun parseVideoSize(resolution: String?): Pair<Int, Int>? {
    val m = Regex("""^(\d+)x(\d+)""").find(resolution ?: return null) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2].toInt()
}
