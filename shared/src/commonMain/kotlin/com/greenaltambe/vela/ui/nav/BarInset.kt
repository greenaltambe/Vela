package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The room at the bottom of a page that a see-through bottom bar (and the mini player on it) covers. The shell lets the page run underneath such a bar;
 * a scrolling list adds this to its bottom padding so its last item can be scrolled clear of the bar. Zero when the bar is solid (the page then ends
 * above it) and on pages the shell pads itself.
 */
val LocalBarInset = compositionLocalOf<Dp> { 0.dp }

/**
 * The same at the top: the room a see-through top bar (and, on Home, the chip row under it) covers at the top of a page. Zero when the top bar is solid.
 */
val LocalTopBarInset = compositionLocalOf<Dp> { 0.dp }

/**
 * A place in the shell's see-through top area for what a page wants to hang right under the top bar (Home's chip strip), so both are blurred as one surface.
 * [active] says the area is see-through (the page then publishes its strip into [content] instead of drawing it itself); while [held] is true (the strip is slid away
 * on scroll) the room the page keeps for it is not re-measured, so lists do not jump.
 */
@androidx.compose.runtime.Stable
class TopExtras(val active: Boolean) {
    var content by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
    var held by androidx.compose.runtime.mutableStateOf(false)
}

val LocalTopExtras = compositionLocalOf<TopExtras?> { null }

/** The pages whose lists add [LocalBarInset] themselves, so they can show through the bar. Every other page is padded by the shell and simply ends above the bar. */
fun Route.drawsUnderBar(): Boolean = this == Route.Home || this == Route.Explore || this == Route.Subscriptions
