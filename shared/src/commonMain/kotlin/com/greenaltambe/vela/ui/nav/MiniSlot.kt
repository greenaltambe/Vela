package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/** The height of the mini player: a standard list-item-sized bar. */
val MiniPlayerHeight = 64.dp

/**
 * Where the shell has set aside room for the mini player, in window pixels: the strip between the page and the navigation bar (NaN until it has been
 * laid out, or while no video is playing). The shell writes it from a spacer of [MiniPlayerHeight]; the player sheet reads it in its layout, so the
 * mini player sits exactly on the bar, whatever the window (phone, with a rail on a wide window) and the sheet needs no hard-coded sizes.
 */
@Stable
class MiniSlot {
    var top by mutableFloatStateOf(Float.NaN)
    var left by mutableFloatStateOf(Float.NaN)
    var width by mutableFloatStateOf(Float.NaN)
    val isSet: Boolean get() = !top.isNaN() && !left.isNaN() && !width.isNaN()
}
