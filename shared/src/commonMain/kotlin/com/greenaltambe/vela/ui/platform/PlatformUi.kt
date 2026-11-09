package com.greenaltambe.vela.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Handles the system Back gesture / button while [enabled]. Desktop has none (its Back is Escape / Alt+Left, handled by the app). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Returns a function that opens the system file chooser for a text file (CSV / JSON) and calls [onPicked] with its
 * contents, or with null if the user cancelled or the file could not be read.
 */
@Composable
expect fun rememberTextFilePicker(onPicked: (String?) -> Unit): () -> Unit

/** What only the window can do. Desktop provides it; on Android it stays null. */
interface WindowControls {
    val isFullscreen: Boolean
    fun toggleFullscreen()
}

val LocalWindowControls = staticCompositionLocalOf<WindowControls?> { null }

/**
 * The screen's brightness and the system media volume, each 0..1: what the player's edge swipes in fullscreen change (left half brightness, right half volume).
 * Android provides it; desktop has its own volume slider and the OS controls, so it stays null there and the swipes are off.
 */
interface PlayerLevels {
    /** 0..1. Setting it takes the screen off the system's (auto) brightness for this window. */
    var brightness: Float
    var volume: Float
    /** Gives the screen back to the system's brightness setting (auto-brightness included), as before the swipe. */
    fun useSystemBrightness()
}

val LocalPlayerLevels = staticCompositionLocalOf<PlayerLevels?> { null }

/** The level a vertical swipe has reached: [start] moved by [dy] pixels (finger up is negative, which raises it) over a surface [height] tall. A swipe across [travel] of the height covers the whole range. */
fun swipedLevel(start: Float, dy: Float, height: Float, travel: Float = 0.8f): Float = swipedRaw(start, dy, height, travel).coerceIn(0f, 1f)

/** Like [swipedLevel] but not clamped: below 0 means the finger went past the bottom end. */
fun swipedRaw(start: Float, dy: Float, height: Float, travel: Float = 0.8f): Float =
    if (height <= 0f) start else start - dy / (height * travel)

/** The brightness a swipe asks for: null (the system's own brightness, "Auto") once the finger has gone a little past the bottom end, else the level 0..1. */
fun brightnessTarget(raw: Float, autoBelow: Float = -0.08f): Float? = if (raw < autoBelow) null else raw.coerceIn(0f, 1f)

/** Opens the platform's share sheet for a title and link (Android: the system chooser); null where there is none, and the page then copies the link. */
val LocalShare = staticCompositionLocalOf<((title: String, url: String) -> Unit)?> { null }

/** Fetches the text at an address (a caption file), or null when it could not be read. Provided by the app shell. */
val LocalTextFetcher = staticCompositionLocalOf<(suspend (String) -> String?)?> { null }

/** An invisible mouse pointer (desktop), shown over the picture in fullscreen once the controls have hidden; null where there is no pointer. */
val LocalBlankPointer = staticCompositionLocalOf<androidx.compose.ui.input.pointer.PointerIcon?> { null }

/** True where the app has its own volume control (desktop); Android leaves volume to the system buttons and shows only mute. */
val LocalVolumeSlider = staticCompositionLocalOf { false }

/** True while the app is shown in a Picture-in-Picture window, where only the video should be drawn. */
val LocalInPictureInPicture = staticCompositionLocalOf { false }

/**
 * Returns a function that lets the viewer choose a download folder and calls [onPicked] with where it is (a path, or on
 * Android a persistable content URI), or null if cancelled.
 */
@Composable
expect fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit

/** Subtitle text kept with downloads, found by a `vela-offline:` address, so offline playback shows captions without a network. */
object OfflineSubtitles {
    private val texts = HashMap<String, String>()
    fun put(address: String, vtt: String) { synchronized(texts) { texts[address] = vtt } }
    fun get(address: String): String? = synchronized(texts) { texts[address] }
    fun address(videoId: String, language: String) = "vela-offline:$videoId:$language"
}
