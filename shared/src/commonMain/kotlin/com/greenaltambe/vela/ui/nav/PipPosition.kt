package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

/** Where the picture-in-picture mini player's top-left corner is (px in the app window), or null before it has been moved (bottom-right, above the bar). */
class PipPosition {
    var pos by mutableStateOf<Offset?>(null)
}

val LocalPipPosition = staticCompositionLocalOf<PipPosition?> { null }
