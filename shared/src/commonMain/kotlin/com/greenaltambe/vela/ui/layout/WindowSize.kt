package com.greenaltambe.vela.ui.layout

import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.ui.look.SpacingTokens

/**
 * How much room the window has, from its real width. Every screen branches on this, never on ad-hoc dp checks:
 * COMPACT is a phone in portrait, MEDIUM a small tablet or a phone in landscape, EXPANDED a large tablet or the desktop.
 */
enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

fun windowSizeClassFor(widthDp: Float, spacing: SpacingTokens = SpacingTokens()): WindowSizeClass = when {
    widthDp < spacing.mediumMinWidthDp -> WindowSizeClass.COMPACT
    widthDp < spacing.wideLayoutMinWidthDp -> WindowSizeClass.MEDIUM
    else -> WindowSizeClass.EXPANDED
}

/** The standard content margin for a class, from the Look's spacing tokens. */
fun SpacingTokens.gutterFor(size: WindowSizeClass): Float = when (size) {
    WindowSizeClass.COMPACT -> gutterCompactDp
    WindowSizeClass.MEDIUM -> gutterMediumDp
    WindowSizeClass.EXPANDED -> gutterExpandedDp
}

/** How many columns of at least [minCardDp] fit in [availableDp] (never fewer than one). */
fun gridColumns(availableDp: Float, minCardDp: Float, gutterDp: Float): Int =
    ((availableDp + gutterDp) / (minCardDp + gutterDp)).toInt().coerceAtLeast(1)

val LocalWindowSize = staticCompositionLocalOf { WindowSizeClass.COMPACT }

/** The window's width in dp, for layouts that need the number (grids). */
val LocalWindowWidthDp = staticCompositionLocalOf { 360f }
