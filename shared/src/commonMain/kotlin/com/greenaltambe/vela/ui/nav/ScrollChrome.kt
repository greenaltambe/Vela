package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Whether the page's chrome (Home's filter chips, the bottom bar) is tucked away because the user is scrolling down. Fed by [connection], which the shell
 * puts around the page: any vertical list under it reports its scroll here. Scrolling down hides, scrolling up (or reaching a new page) shows again.
 */
@Stable
class ScrollChrome {
    var hidden by mutableStateOf(false)
        private set
    private var acc = 0f

    fun show() { hidden = false; acc = 0f }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f || abs(dy) < abs(available.x)) return Offset.Zero
            // Fingers moving up (dy < 0) means the content scrolls down: tuck the chrome away once that has gone on for a little; the opposite brings it back.
            if ((dy < 0f) != (acc < 0f)) acc = 0f
            acc += dy
            if (acc < -HIDE_AFTER_PX) hidden = true else if (acc > SHOW_AFTER_PX) hidden = false
            return Offset.Zero
        }
    }

    private companion object {
        const val HIDE_AFTER_PX = 48f
        const val SHOW_AFTER_PX = 24f
    }
}

/** Provided by the shell; null where nothing tracks scrolling (tests, previews). */
val LocalScrollChrome = staticCompositionLocalOf<ScrollChrome?> { null }
