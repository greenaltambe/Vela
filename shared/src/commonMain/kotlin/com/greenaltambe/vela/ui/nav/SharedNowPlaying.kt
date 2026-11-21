package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.extractor.VideoSummary

/**
 * One player serves every desktop tab and window, so what it shows in the mini player is shared too: the tab that starts or closes a video publishes it
 * here, and a tab that comes to the front takes over what is playing instead of showing nothing (or a stale video). Null on Android (one page stack).
 */
class SharedNowPlaying {
    var video: VideoSummary? by mutableStateOf(null)
    /** The last session's mini player is restored once per run, by whichever page stack gets there first. */
    var restoreClaimed: Boolean = false
}

val LocalSharedNowPlaying = staticCompositionLocalOf<SharedNowPlaying?> { null }
