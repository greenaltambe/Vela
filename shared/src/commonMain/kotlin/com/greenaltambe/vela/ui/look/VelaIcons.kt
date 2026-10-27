package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's own small icon set, drawn as vector paths on a 24 x 24 grid (no icon dependency). Which set is used is
 * the Look's `iconSetId`; only "material" exists so far, so [forSet] returns it for any id.
 */
class VelaIconSet(
    val home: ImageVector, val subscriptions: ImageVector, val library: ImageVector, val settings: ImageVector,
    val search: ImageVector, val back: ImageVector, val close: ImageVector, val more: ImageVector,
    val play: ImageVector, val pause: ImageVector, val rewind: ImageVector, val forward: ImageVector,
    val fullscreen: ImageVector, val fullscreenExit: ImageVector, val volume: ImageVector, val mute: ImageVector,
    val download: ImageVector, val downloaded: ImageVector, val save: ImageVector, val share: ImageVector,
    val speed: ImageVector, val timer: ImageVector, val quality: ImageVector, val captions: ImageVector,
    val like: ImageVector, val refresh: ImageVector, val info: ImageVector,
)

object VelaIcons {
    private fun icon(name: String, path: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        ).build()

    val material: VelaIconSet by lazy {
        VelaIconSet(
            home = icon("home", "M12 3 2 12h3v8h5v-6h4v6h5v-8h3z"),
            subscriptions = icon("subscriptions", "M4 4h16v2H4zM6 8h12v2H6zM3 12h18v8H3zM10 14v4l4-2z", evenOdd = true),
            library = icon("library", "M4 8h2v12h12v2H4zM8 4h12v12H8z"),
            settings = icon("settings", "M3 7h10v2H3zM15 5h2v6h-2zM17 7h4v2h-4zM3 15h4v2H3zM9 13h2v6H9zM11 15h10v2H11z"),
            search = icon("search", "M10 3a7 7 0 1 0 4.2 12.6l5.1 5.1 1.4-1.4-5.1-5.1A7 7 0 0 0 10 3zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10z", evenOdd = true),
            back = icon("back", "M20 11H7.8l5.6-5.6L12 4l-8 8 8 8 1.4-1.4L7.8 13H20z"),
            close = icon("close", "M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12z"),
            more = icon("more", "M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM12 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"),
            play = icon("play", "M8 5v14l11-7z"),
            pause = icon("pause", "M6 5h4v14H6zM14 5h4v14h-4z"),
            rewind = icon("rewind", "M11 6v12l-8-6zM21 6v12l-8-6z"),
            forward = icon("forward", "M3 6v12l8-6zM13 6v12l8-6z"),
            fullscreen = icon("fullscreen", "M4 4h6v2H6v4H4zM14 4h6v6h-2V6h-4zM4 14h2v4h4v2H4zM18 14h2v6h-6v-2h4z"),
            fullscreenExit = icon("fullscreen_exit", "M8 4h2v6H4V8h4zM14 4h2v4h4v2h-6zM4 14h6v6H8v-4H4zM14 14h6v2h-4v4h-2z"),
            volume = icon("volume", "M3 9v6h4l5 5V4L7 9zM15 8.5a5 5 0 0 1 0 7z"),
            mute = icon("mute", "M3 9v6h4l5 5V4L7 9zM16.3 9 15 10.4l1.6 1.6-1.6 1.6 1.4 1.4 1.6-1.6 1.6 1.6 1.4-1.4-1.6-1.6 1.6-1.6L19.6 9 18 10.6z"),
            download = icon("download", "M5 20h14v-2H5zM11 4v8.2L7.4 8.6 6 10l6 6 6-6-1.4-1.4L13 12.2V4z"),
            downloaded = icon("downloaded", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-2 14.4L5.6 12 7 10.6l3 3 7-7L18.4 8z"),
            save = icon("save", "M6 3h12v18l-6-4-6 4z"),
            share = icon("share", "M18 16a3 3 0 0 0-2 .8l-7-4.1a3 3 0 0 0 0-1.4l7-4.1A3 3 0 1 0 15 5a3 3 0 0 0 .1.7L8 9.8a3 3 0 1 0 0 4.4l7.1 4.1A3 3 0 1 0 18 16z"),
            speed = icon("speed", "M12 3a9 9 0 1 0 9 9h-2a7 7 0 1 1-7-7zM12 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2zM13 12h7v-2z"),
            timer = icon("timer", "M9 1h6v2H9zM12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zm-1 3h2v6h-2z", evenOdd = true),
            quality = icon("quality", "M4 4h16v16H4zM6 10v4h1.5v-1.5h1V14H10v-4H8.5v1H7.5v-1zM11.5 10v4h2a1.5 1.5 0 0 0 1.5-1.5v-1A1.5 1.5 0 0 0 13.5 10z", evenOdd = true),
            captions = icon("captions", "M3 5h18v14H3zM6 10v4h3v-1.2H7.5v-1.6H9V10zM12 10v4h3v-1.2h-1.5v-1.6H15V10z", evenOdd = true),
            like = icon("like", "M2 10h4v11H2zM8 10l4-7c1.2 0 2 .8 2 2v4h6a2 2 0 0 1 2 2.3l-1.3 7A2 2 0 0 1 18.7 20H8z"),
            refresh = icon("refresh", "M12 4V1L8 5l4 4V6a6 6 0 1 1-6 6H4a8 8 0 1 0 8-8z"),
            info = icon("info", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1 5h2v2h-2zm0 4h2v6h-2z", evenOdd = true),
        )
    }

    fun forSet(@Suppress("UNUSED_PARAMETER") id: String): VelaIconSet = material
}
