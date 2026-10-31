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
