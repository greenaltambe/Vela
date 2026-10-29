package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far the video page has been pulled down towards the mini player (0..1). The video page writes it while the finger drags;
 * the app shell reads it to fade in the screen the video was opened from (a picture of it), so the page behind is revealed instead of black.
 */
val LocalPageReveal = staticCompositionLocalOf<MutableFloatState?> { null }
