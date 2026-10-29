package com.greenaltambe.vela.ui.look

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.layout.gutterFor

/** The active Look. VelaTheme provides it; the default only matters for previews and tests that skip the theme. */
val LocalLook: ProvidableCompositionLocal<LookTokens> = compositionLocalOf { Looks.Light }

/** Mirrors the `performanceMode` setting for [Effects]; provided by VelaTheme. */
val LocalPerformanceMode: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

fun Long.toColor(): Color = Color(this.toInt())

fun Float.dpScaled(density: Density): Dp = (this * density.scale).dp

/** Shorthand accessors so components say `Vela.look.radii...` instead of re-reading the local everywhere. */
object Vela {
    val look: LookTokens
        @Composable @ReadOnlyComposable get() = LocalLook.current

    val colors: ColorTokens
        @Composable @ReadOnlyComposable get() = LocalLook.current.colors

    val smallShape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalLook.current.radii.smallDp.dp)
    val mediumShape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalLook.current.radii.mediumDp.dp)
    val largeShape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalLook.current.radii.largeDp.dp)
    val pillShape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalLook.current.radii.fullDp.dp)

    /** Thumbnails and buttons use the same medium radius as every other element. */
    val thumbnailShape @Composable @ReadOnlyComposable get() = mediumShape
    val buttonShape @Composable @ReadOnlyComposable get() = mediumShape

    /** The palette's gradient as a horizontal brush: for the few places the app wears it (selected marks, buttons, switches, the profile ring). */
    val wake: androidx.compose.ui.graphics.Brush @Composable @ReadOnlyComposable get() = androidx.compose.ui.graphics.Brush.horizontalGradient(LocalLook.current.accent.wake.map { it.toColor() })

    val motion: MotionTokens @Composable @ReadOnlyComposable get() = LocalLook.current.motion

    val xs: Dp @Composable @ReadOnlyComposable get() = LocalLook.current.spacing.xsDp.dpScaled(LocalLook.current.density)
    val sm: Dp @Composable @ReadOnlyComposable get() = LocalLook.current.spacing.smDp.dpScaled(LocalLook.current.density)
    val md: Dp @Composable @ReadOnlyComposable get() = LocalLook.current.spacing.mdDp.dpScaled(LocalLook.current.density)
    val lg: Dp @Composable @ReadOnlyComposable get() = LocalLook.current.spacing.lgDp.dpScaled(LocalLook.current.density)

    val pagePadding: PaddingValues @Composable @ReadOnlyComposable get() = PaddingValues(md)

    /** Padding for every scrolling screen: the window class's gutter at the sides, a little air above and below (and, below, room for a bar that floats over the page). */
    val contentPadding: PaddingValues @Composable @ReadOnlyComposable get() = PaddingValues(start = gutter, end = gutter, top = sm, bottom = sm + com.greenaltambe.vela.ui.nav.LocalChromeInset.current)

    /** The content margin for the current window class: use this for every screen's horizontal edge. */
    val gutter: Dp @Composable @ReadOnlyComposable get() = LocalLook.current.spacing.gutterFor(com.greenaltambe.vela.ui.layout.LocalWindowSize.current).dpScaled(LocalLook.current.density)

    /** The icon set the active Look names. */
    val icons: VelaIconSet @Composable @ReadOnlyComposable get() = VelaIcons.forTokens(LocalLook.current.icons)

    /** Minimum size of anything tappable. */
    val minTouch: Dp @Composable @ReadOnlyComposable get() = LocalLook.current.spacing.minTouchDp.dp
}
