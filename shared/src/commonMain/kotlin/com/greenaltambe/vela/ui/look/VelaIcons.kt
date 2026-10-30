package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.IconStyle
import com.greenaltambe.vela.core.settings.IconWeight

/**
 * The app's icon sets on a 24 x 24 grid. [IconStyle] picks the PACK: Filled (solid shapes), Line (fine strokes) or Duotone (the line icons
 * with a soft fill) are drawn here; Material Rounded / Outlined / Sharp / Two-tone and Lucide come ready-made from [LibraryIconPacks]. The line packs are drawn as strokes from the start rather than outlining
 * the solid shapes, so thin things (dots, plus, check marks, arrows) stay as elegant as the rest.
 */
class VelaIconSet(private val icons: Map<String, ImageVector>) {
    private operator fun get(name: String): ImageVector = icons.getValue(name)

    val home get() = this["home"]; val subscriptions get() = this["subscriptions"]; val library get() = this["library"]; val settings get() = this["settings"]
    val search get() = this["search"]; val back get() = this["back"]; val close get() = this["close"]; val more get() = this["more"]
    val play get() = this["play"]; val pause get() = this["pause"]; val rewind get() = this["rewind"]; val forward get() = this["forward"]
    val fullscreen get() = this["fullscreen"]; val fullscreenExit get() = this["fullscreenExit"]; val volume get() = this["volume"]; val mute get() = this["mute"]
    val download get() = this["download"]; val downloaded get() = this["downloaded"]; val save get() = this["save"]; val share get() = this["share"]
    val speed get() = this["speed"]; val timer get() = this["timer"]; val quality get() = this["quality"]; val captions get() = this["captions"]
    val like get() = this["like"]; val refresh get() = this["refresh"]; val info get() = this["info"]
    val chevronRight get() = this["chevronRight"]; val chevronLeft get() = this["chevronLeft"]; val chevronDown get() = this["chevronDown"]; val chevronUp get() = this["chevronUp"]
    val check get() = this["check"]; val add get() = this["add"]; val delete get() = this["delete"]; val edit get() = this["edit"]
    val history get() = this["history"]; val playlist get() = this["playlist"]; val notifications get() = this["notifications"]; val account get() = this["account"]
    val palette get() = this["palette"]; val lock get() = this["lock"]; val shield get() = this["shield"]; val folder get() = this["folder"]
    val tune get() = this["tune"]; val arrowUp get() = this["arrowUp"]; val arrowDown get() = this["arrowDown"]; val dislike get() = this["dislike"]
    val watchLater get() = this["watchLater"]; val comment get() = this["comment"]; val audioTrack get() = this["audioTrack"]; val link get() = this["link"]; val cloud get() = this["cloud"]; val visibility get() = this["visibility"]; val bolt get() = this["bolt"]; val explore get() = this["explore"]; val skipNext get() = this["skipNext"]; val skipPrevious get() = this["skipPrevious"]

    /** Every icon with its name, for tests and the pack previews. */
    val all: List<Pair<String, ImageVector>> get() = IconNames.all.map { it to this[it] }
}

/** Every icon the app uses. Each pack, Vela's own and the ready-made ones, must cover all of them (a test checks it). */
object IconNames {
    val all: List<String> = listOf(
        "home", "subscriptions", "library", "settings", "search", "back", "close", "more", "play", "pause", "rewind", "forward", "fullscreen", "fullscreenExit", "volume", "mute",
        "download", "downloaded", "save", "share", "speed", "timer", "quality", "captions", "like", "refresh", "info",
        "chevronRight", "chevronLeft", "chevronDown", "chevronUp", "check", "add", "delete", "edit", "history", "playlist", "notifications", "account", "palette",
        "lock", "shield", "folder", "tune", "arrowUp", "arrowDown", "dislike", "link", "cloud", "visibility", "bolt", "explore", "skipNext", "skipPrevious", "watchLater", "comment", "audioTrack",
    )
}

/** One drawn part of a line icon: [closed] parts get the soft fill in Duotone; [dot] parts are small solid dots in every line pack. */
internal class Seg(val d: String, val closed: Boolean = false, val dot: Boolean = false)

internal object IconPaths {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx} ${cy - r}a$r $r 0 1 0 0 ${2 * r} $r $r 0 0 0 0-${2 * r}z"
    private const val SPEAKER = "M4.5 9.5v5H8l4.5 4V5.5L8 9.5z"
    private const val SCREEN = "M5.5 5.5h13a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17V7a1.5 1.5 0 0 1 1.5-1.5z"

    /** The Filled pack: solid shapes (name -> path, even-odd fill). */
    val filled: Map<String, Pair<String, Boolean>> = mapOf(
        "home" to ("M12 3 2 12h3v8h5v-6h4v6h5v-8h3z" to false),
        "subscriptions" to ("M4 4h16v2H4zM6 8h12v2H6zM3 12h18v8H3zM10 14v4l4-2z" to true),
        "library" to ("M4 8h2v12h12v2H4zM8 4h12v12H8z" to false),
        "settings" to ("M3 7h10v2H3zM15 5h2v6h-2zM17 7h4v2h-4zM3 15h4v2H3zM9 13h2v6H9zM11 15h10v2H11z" to false),
        "search" to ("M10 3a7 7 0 1 0 4.2 12.6l5.1 5.1 1.4-1.4-5.1-5.1A7 7 0 0 0 10 3zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10z" to true),
        "back" to ("M20 11H7.8l5.6-5.6L12 4l-8 8 8 8 1.4-1.4L7.8 13H20z" to false),
        "close" to ("M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12z" to false),
        "more" to ("M6 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM18 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to false),
        "audioTrack" to ("M12 3v10.55a4 4 0 1 0 2 3.45V7h4V3z" to false),
        "comment" to ("M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zM18 14H6v-2h12zm0-3H6V9h12zm0-3H6V6h12z" to true),
        "watchLater" to ("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm4.2 14.2L11 13V7h1.5v5.2l4.5 2.7z" to true),
        "play" to ("M8 5v14l11-7z" to false),
        "pause" to ("M6 5h4v14H6zM14 5h4v14h-4z" to false),
        "rewind" to ("M11 6v12l-8-6zM21 6v12l-8-6z" to false),
        "forward" to ("M3 6v12l8-6zM13 6v12l8-6z" to false),
        "fullscreen" to ("M4 4h6v2H6v4H4zM14 4h6v6h-2V6h-4zM4 14h2v4h4v2H4zM18 14h2v6h-6v-2h4z" to false),
        "fullscreenExit" to ("M8 4h2v6H4V8h4zM14 4h2v4h4v2h-6zM4 14h6v6H8v-4H4zM14 14h6v2h-4v4h-2z" to false),
        "volume" to ("M3 9v6h4l5 5V4L7 9zM15 8.5a5 5 0 0 1 0 7z" to false),
        "mute" to ("M3 9v6h4l5 5V4L7 9zM16.3 9 15 10.4l1.6 1.6-1.6 1.6 1.4 1.4 1.6-1.6 1.6 1.6 1.4-1.4-1.6-1.6 1.6-1.6L19.6 9 18 10.6z" to false),
        "download" to ("M5 20h14v-2H5zM11 4v8.2L7.4 8.6 6 10l6 6 6-6-1.4-1.4L13 12.2V4z" to false),
        "downloaded" to ("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-2 14.4L5.6 12 7 10.6l3 3 7-7L18.4 8z" to false),
        "save" to ("M6 3h12v18l-6-4-6 4z" to false),
        "share" to ("M18 16a3 3 0 0 0-2 .8l-7-4.1a3 3 0 0 0 0-1.4l7-4.1A3 3 0 1 0 15 5a3 3 0 0 0 .1.7L8 9.8a3 3 0 1 0 0 4.4l7.1 4.1A3 3 0 1 0 18 16z" to false),
        "speed" to ("M12 3a9 9 0 1 0 9 9h-2a7 7 0 1 1-7-7zM12 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2zM13 12h7v-2z" to false),
        "timer" to ("M9 1h6v2H9zM12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zm-1 3h2v6h-2z" to true),
        "quality" to ("M4 4h16v16H4zM6 10v4h1.5v-1.5h1V14H10v-4H8.5v1H7.5v-1zM11.5 10v4h2a1.5 1.5 0 0 0 1.5-1.5v-1A1.5 1.5 0 0 0 13.5 10z" to true),
        "captions" to ("M3 5h18v14H3zM6 10v4h3v-1.2H7.5v-1.6H9V10zM12 10v4h3v-1.2h-1.5v-1.6H15V10z" to true),
        "like" to ("M2 10h4v11H2zM8 10l4-7c1.2 0 2 .8 2 2v4h6a2 2 0 0 1 2 2.3l-1.3 7A2 2 0 0 1 18.7 20H8z" to false),
        "refresh" to ("M12 4V1L8 5l4 4V6a6 6 0 1 1-6 6H4a8 8 0 1 0 8-8z" to false),
        "info" to ("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1 5h2v2h-2zm0 4h2v6h-2z" to true),
        "chevronRight" to ("M9.4 5.4 8 6.8 13.2 12 8 17.2l1.4 1.4L16 12z" to false),
        "chevronLeft" to ("M14.6 5.4 16 6.8 10.8 12 16 17.2l-1.4 1.4L8 12z" to false),
        "chevronDown" to ("M5.4 9.4 6.8 8 12 13.2 17.2 8l1.4 1.4L12 16z" to false),
        "chevronUp" to ("M5.4 14.6 6.8 16 12 10.8 17.2 16l1.4-1.4L12 8z" to false),
        "check" to ("M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" to false),
        "add" to ("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z" to false),
        "delete" to ("M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V8H6zM15.5 4l-1-1h-5l-1 1H5v2h14V4z" to false),
        "edit" to ("M3 17.25V21h3.75L17.8 9.94l-3.75-3.75zM20.7 7.05a1 1 0 0 0 0-1.4l-2.35-2.35a1 1 0 0 0-1.4 0l-1.83 1.83 3.75 3.75z" to false),
        "history" to ("M13 3a9 9 0 0 0-9 9H1l3.9 3.9.1.1L9 12H6a7 7 0 1 1 2.05 4.95l-1.42 1.42A9 9 0 1 0 13 3zm-1 5v5l4.3 2.5.7-1.2-3.5-2.1V8z" to false),
        "playlist" to ("M3 6h14v2H3zM3 10h14v2H3zM3 14h7v2H3zM13 14v6l6-3z" to false),
        "notifications" to ("M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2zm6-6v-5c0-3.1-1.6-5.6-4.5-6.3V4a1.5 1.5 0 0 0-3 0v.7C7.6 5.4 6 7.9 6 11v5l-2 2v1h16v-1z" to false),
        "account" to ("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm0 14.2a7.2 7.2 0 0 1-6-3.2c0-2 4-3.1 6-3.1s6 1.1 6 3.1a7.2 7.2 0 0 1-6 3.2z" to false),
        "palette" to ("M12 3a9 9 0 0 0 0 18 1.5 1.5 0 0 0 1.1-2.5 1.5 1.5 0 0 1 1.1-2.5H16a5 5 0 0 0 5-5c0-4.4-4-8-9-8zM6.5 12a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z" to true),
        "lock" to ("M18 8h-1V6a5 5 0 0 0-10 0v2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2zM9 6a3 3 0 0 1 6 0v2H9zm3 11a2 2 0 1 1 0-4 2 2 0 0 1 0 4z" to true),
        "shield" to ("M12 2 4 5v6c0 5.2 3.4 9.7 8 11 4.6-1.3 8-5.8 8-11V5zM10.6 15.6 7.5 12.5l1.1-1.1 2 2 4.8-4.8 1.1 1.1z" to true),
        "folder" to ("M10 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8z" to false),
        "tune" to ("M3 17v2h6v-2zM3 5v2h10V5zM13 21v-2h8v-2h-8v-2h-2v6zM7 9v2H3v2h4v2h2V9zM21 13v-2H11v2zM15 9h2V7h4V5h-4V3h-2z" to false),
        "arrowUp" to ("M11 20V7.8l-5.6 5.6L4 12l8-8 8 8-1.4 1.4L13 7.8V20z" to false),
        "arrowDown" to ("M11 4v12.2l-5.6-5.6L4 12l8 8 8-8-1.4-1.4L13 16.2V4z" to false),
        "dislike" to ("M22 14h-4V3h4zM16 14l-4 7c-1.2 0-2-.8-2-2v-4H4a2 2 0 0 1-2-2.3l1.3-7A2 2 0 0 1 5.3 4H16z" to false),
        "link" to ("M3.9 12a3.1 3.1 0 0 1 3.1-3.1h4V7H7a5 5 0 0 0 0 10h4v-1.9H7A3.1 3.1 0 0 1 3.9 12zM8 13h8v-2H8zm9-6h-4v1.9h4a3.1 3.1 0 0 1 0 6.2h-4V17h4a5 5 0 0 0 0-10z" to false),
        "cloud" to ("M6.5 19a4.5 4.5 0 0 1-.5-8.97A6 6 0 0 1 17.7 10.3 4.4 4.4 0 0 1 17.5 19z" to false),
        "visibility" to ("M12 5C7 5 2.7 8.1 1 12.5 2.7 16.9 7 20 12 20s9.3-3.1 11-7.5C21.3 8.1 17 5 12 5zm0 12.5a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" to true),
        "bolt" to ("M13 2 4 14h6l-1 8 9-12h-6z" to false),
        "skipNext" to ("M6 18l8.5-6L6 6zM16 6h2v12h-2z" to false),
        "skipPrevious" to ("M6 6h2v12H6zM9.5 12l8.5 6V6z" to false),
        "explore" to ("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm4.5 5.5-2.3 6.7-6.7 2.3 2.3-6.7z" to true),
    )

    /** The Line / Duotone packs: the same icons drawn as fine strokes. */
    val line: Map<String, List<Seg>> = mapOf(
        "home" to listOf(Seg("M5 10.5 12 4.5l7 6V20h-4.5v-5.5h-5V20H5z", closed = true)),
        "subscriptions" to listOf(Seg("M6.5 4.5h11M8.5 8h7"), Seg("M5.5 11.5h13a1.5 1.5 0 0 1 1.5 1.5v5.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5V13a1.5 1.5 0 0 1 1.5-1.5z", closed = true), Seg("M10.5 14v3.5l3-1.75z")),
        "library" to listOf(Seg("M4.5 8v11.5H16"), Seg("M8 4.5h11.5V16H8z", closed = true)),
        "settings" to listOf(Seg("M4 7h8M18 7h2M4 17h2M12 17h8"), Seg(c(15f, 7f, 2.2f), closed = true), Seg(c(9f, 17f, 2.2f), closed = true)),
        "search" to listOf(Seg(c(11f, 11f, 6.5f), closed = true), Seg("M16 16l4.5 4.5")),
        "back" to listOf(Seg("M19.5 12h-15M11 5.5 4.5 12 11 18.5")),
        "close" to listOf(Seg("M6 6l12 12M18 6 6 18")),
        "more" to listOf(Seg(c(6f, 12f, 1.3f) + c(12f, 12f, 1.3f) + c(18f, 12f, 1.3f), dot = true)),
        "audioTrack" to listOf(Seg(c(9f, 17f, 2.6f), closed = true), Seg("M11.6 17V5.5l5.4 1.5V10")),
        "comment" to listOf(Seg("M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H9l-4.5 3.5V6A1.5 1.5 0 0 1 5 4.5z", closed = true)),
        "watchLater" to listOf(Seg(c(12f, 12f, 8.5f), closed = true), Seg("M12 7.5V12l3 2")),
        "play" to listOf(Seg("M8 5.5v13L19 12z", closed = true)),
        "pause" to listOf(Seg("M7 5.5h3.2v13H7zM13.8 5.5H17v13h-3.2z", closed = true)),
        "rewind" to listOf(Seg("M11 7.5v9L4.5 12zM19.5 7.5v9L13 12z", closed = true)),
        "forward" to listOf(Seg("M13 7.5v9l6.5-4.5zM4.5 7.5v9L11 12z", closed = true)),
        "fullscreen" to listOf(Seg("M4.5 9V4.5H9M15 4.5h4.5V9M19.5 15v4.5H15M9 19.5H4.5V15")),
        "fullscreenExit" to listOf(Seg("M9 4.5V9H4.5M15 4.5V9h4.5M19.5 15H15v4.5M4.5 15H9v4.5")),
        "volume" to listOf(Seg(SPEAKER, closed = true), Seg("M16 9a4.2 4.2 0 0 1 0 6")),
        "mute" to listOf(Seg(SPEAKER, closed = true), Seg("M16.5 9.5l4 5M20.5 9.5l-4 5")),
        "download" to listOf(Seg("M12 4.5v10M7.5 10.5l4.5 4.5 4.5-4.5M5 19.5h14")),
        "downloaded" to listOf(Seg(c(12f, 12f, 8.5f), closed = true), Seg("M8.3 12.3l2.6 2.6 4.8-5.2")),
        "save" to listOf(Seg("M7 4.5h10v15.5l-5-3.5-5 3.5z", closed = true)),
        "share" to listOf(Seg(c(18f, 6.5f, 2.3f), closed = true), Seg(c(6f, 12f, 2.3f), closed = true), Seg(c(18f, 17.5f, 2.3f), closed = true), Seg("M8 11 16 7.5M8 13l8 3.5")),
        "speed" to listOf(Seg("M4.7 16.5a8 8 0 1 1 14.6 0"), Seg("M12 13.5l3.8-4"), Seg(c(12f, 13.5f, 1.1f), dot = true)),
        "timer" to listOf(Seg("M9.5 3.5h5"), Seg(c(12f, 13.2f, 7f), closed = true), Seg("M12 9v4.4l2.8 1.6")),
        "quality" to listOf(Seg(SCREEN, closed = true), Seg("M7.5 9.5v5M7.5 12h3M10.5 9.5v5"), Seg("M13.8 9.5h1.1a2.5 2.5 0 0 1 0 5h-1.1z")),
        "captions" to listOf(Seg(SCREEN, closed = true), Seg("M10.4 10.6a2 2 0 1 0 0 2.8M16.6 10.6a2 2 0 1 0 0 2.8")),
        "like" to listOf(Seg("M7.5 11v8.5h-3V11z", closed = true), Seg("M7.5 11 11.2 4.6a1.6 1.6 0 0 1 3 .8V9.5h4.3a1.6 1.6 0 0 1 1.6 1.9l-1.2 6.3a2 2 0 0 1-2 1.8H7.5")),
        "refresh" to listOf(Seg("M19.5 5v4.5H15M4.5 19v-4.5H9"), Seg("M5.8 9.2A7.2 7.2 0 0 1 19 9.5M18.2 14.8A7.2 7.2 0 0 1 5 14.5")),
        "info" to listOf(Seg(c(12f, 12f, 8.5f), closed = true), Seg("M12 11v5"), Seg(c(12f, 8f, 1.1f), dot = true)),
        "chevronRight" to listOf(Seg("M9.5 5.5 16 12l-6.5 6.5")),
        "chevronLeft" to listOf(Seg("M14.5 5.5 8 12l6.5 6.5")),
        "chevronDown" to listOf(Seg("M5.5 9.5 12 16l6.5-6.5")),
        "chevronUp" to listOf(Seg("M5.5 14.5 12 8l6.5 6.5")),
        "check" to listOf(Seg("M5 12.5l4.5 4.5L19 7.5")),
        "add" to listOf(Seg("M12 5v14M5 12h14")),
        "delete" to listOf(Seg("M5 7h14M9.5 7V4.5h5V7"), Seg("M6.5 7h11l-.8 12.5H7.3z", closed = true), Seg("M10 11v5M14 11v5")),
        "edit" to listOf(Seg("M5 19l.8-3.6L16.5 4.7a1.6 1.6 0 0 1 2.3 0l.5.5a1.6 1.6 0 0 1 0 2.3L8.6 18.2z", closed = true), Seg("M14.5 6.7l2.8 2.8")),
        "history" to listOf(Seg("M7 6.4A7.5 7.5 0 1 1 4.5 12"), Seg("M4.5 5v4.2h4.2"), Seg("M12 8v4.2l2.8 1.6")),
        "playlist" to listOf(Seg("M4 7h11M4 12h7M4 17h6"), Seg("M14 14.5v5l4.5-2.5z", closed = true)),
        "notifications" to listOf(Seg("M6.5 15.5v-5a5.5 5.5 0 0 1 11 0v5l1.5 1.5H5z", closed = true), Seg("M10 19.5a2 2 0 0 0 4 0")),
        "account" to listOf(Seg(c(12f, 12f, 8.5f), closed = true), Seg(c(12f, 10f, 2.6f)), Seg("M6.8 18.2a6 6 0 0 1 10.4 0")),
        "palette" to listOf(
            Seg("M12 4a8 8 0 0 0 0 16c1.2 0 1.8-.9 1.4-1.9-.4-1 .3-2.1 1.4-2.1H16.5a3.5 3.5 0 0 0 3.5-3.5C20 7.7 16.4 4 12 4z", closed = true),
            Seg(c(7.8f, 11.5f, .9f), dot = true), Seg(c(10f, 8f, .9f), dot = true), Seg(c(14.5f, 8f, .9f), dot = true), Seg(c(17f, 11f, .9f), dot = true),
        ),
        "lock" to listOf(Seg("M6 10.5h12a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7.5a1 1 0 0 1 1-1z", closed = true), Seg("M8 10.5V8a4 4 0 0 1 8 0v2.5"), Seg(c(12f, 15f, 1.1f), dot = true)),
        "shield" to listOf(Seg("M12 3.5 5 6v5.5c0 4.2 2.8 7.4 7 9 4.2-1.6 7-4.8 7-9V6z", closed = true), Seg("M9 12l2.2 2.2L15.2 10")),
        "folder" to listOf(Seg("M4 6.5a1 1 0 0 1 1-1h4.3l2 2H19a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z", closed = true)),
        "tune" to listOf(Seg("M4 7h9M17 7h3M4 17h3M11 17h9"), Seg(c(15f, 7f, 2f), closed = true), Seg(c(9f, 17f, 2f), closed = true)),
        "arrowUp" to listOf(Seg("M12 19.5v-15M5.5 11 12 4.5l6.5 6.5")),
        "arrowDown" to listOf(Seg("M12 4.5v15M5.5 13 12 19.5l6.5-6.5")),
        "dislike" to listOf(Seg("M7.5 13V4.5h-3V13z", closed = true), Seg("M7.5 13l3.7 6.4a1.6 1.6 0 0 0 3-.8V14.5h4.3a1.6 1.6 0 0 0 1.6-1.9l-1.2-6.3a2 2 0 0 0-2-1.8H7.5")),
        "link" to listOf(Seg("M10.5 13.5a3.5 3.5 0 0 0 5 0l3-3a3.5 3.5 0 0 0-5-5l-1 1"), Seg("M13.5 10.5a3.5 3.5 0 0 0-5 0l-3 3a3.5 3.5 0 0 0 5 5l1-1")),
        "cloud" to listOf(Seg("M8 18.5a3.8 3.8 0 0 1-.4-7.5 5.2 5.2 0 0 1 9.8-.8 3.7 3.7 0 0 1-.6 8.3z", closed = true)),
        "visibility" to listOf(Seg("M2.8 12C4.6 8 8 5.8 12 5.8S19.4 8 21.2 12C19.4 16 16 18.2 12 18.2S4.6 16 2.8 12z", closed = true), Seg(c(12f, 12f, 2.8f), closed = true)),
        "bolt" to listOf(Seg("M13 3.5 5.5 13.5H11l-1 7 7.5-10H12z", closed = true)),
        "skipNext" to listOf(Seg("M6.5 6.5v11l8-5.5z", closed = true), Seg("M17.5 6.5v11")),
        "skipPrevious" to listOf(Seg("M17.5 6.5v11l-8-5.5z", closed = true), Seg("M6.5 6.5v11")),
        "explore" to listOf(Seg(c(12f, 12f, 8.5f), closed = true), Seg("M15.6 8.4 13.8 13.8 8.4 15.6 10.2 10.2z", closed = true)),
    )
}

object VelaIcons {
    private val cache = HashMap<IconTokens, VelaIconSet>()

    /** The icon set for the Look's icon tokens, built once per combination. */
    fun forTokens(t: IconTokens): VelaIconSet = cache.getOrPut(t) { build(t) }

    /** Stroke width on the 24 grid for each weight (thin = fine and elegant, bold = sturdy). */
    fun strokeWidth(weight: IconWeight): Float = when (weight) { IconWeight.THIN -> 1.25f; IconWeight.REGULAR -> 1.6f; IconWeight.BOLD -> 2.1f }

    private fun vector(name: String, build: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    private fun filledIcon(name: String, weight: IconWeight): ImageVector {
        val (d, evenOdd) = IconPaths.filled.getValue(name)
        // Bold fattens the solid shapes slightly with a stroke of their own colour; thin and regular are the shapes as drawn.
        val fatten = if (weight == IconWeight.BOLD) 1.2f else 0f
        return vector(name) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(), pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black), stroke = if (fatten > 0f) SolidColor(Color.Black) else null, strokeLineWidth = fatten,
            )
        }
    }

    private fun lineIcon(name: String, t: IconTokens): ImageVector {
        val w = strokeWidth(t.weight)
        val cap = if (t.rounded) StrokeCap.Round else StrokeCap.Butt
        val join = if (t.rounded) StrokeJoin.Round else StrokeJoin.Miter
        val duo = t.style == IconStyle.DUOTONE
        return vector(name) {
            for (seg in IconPaths.line.getValue(name)) {
                val nodes = PathParser().parsePathString(seg.d).toNodes()
                if (seg.dot) { addPath(pathData = nodes, fill = SolidColor(Color.Black)); continue }
                // The soft fill goes under the stroke, only inside closed shapes.
                if (duo && seg.closed) addPath(pathData = nodes, fill = SolidColor(Color.Black), fillAlpha = DUOTONE_FILL_ALPHA)
                addPath(pathData = nodes, fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = w, strokeLineCap = cap, strokeLineJoin = join)
            }
        }
    }

    /** How strong the tint inside a Duotone icon is (the line stays full strength). */
    const val DUOTONE_FILL_ALPHA = 0.24f

    private fun build(t: IconTokens): VelaIconSet {
        val icons = when (t.style) {
            IconStyle.FILLED -> IconNames.all.associateWith { filledIcon(it, t.weight) }
            IconStyle.LINE, IconStyle.DUOTONE -> IconNames.all.associateWith { lineIcon(it, t) }
            else -> LibraryIconPacks.build(t.style)
        }
        return VelaIconSet(icons)
    }

    /** The default (filled, regular, rounded) set. */
    val material: VelaIconSet get() = forTokens(IconTokens())

    fun forSet(@Suppress("UNUSED_PARAMETER") id: String): VelaIconSet = material
}
