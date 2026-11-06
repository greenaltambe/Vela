package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Where the bar has set aside room for the mini player, in window pixels (NaN until it has been laid out, or when the bar is not showing, as with a rail).
 * The bar writes it; the player sheet reads it in its layout, so the mini player sits on the bar and moves with it when the bar shrinks.
 */
@Stable
class MiniSlot {
    var top by mutableFloatStateOf(Float.NaN)
}
