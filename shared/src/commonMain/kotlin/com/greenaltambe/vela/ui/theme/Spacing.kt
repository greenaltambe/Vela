package com.greenaltambe.vela.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.layout.LocalWindowSize
import com.greenaltambe.vela.ui.layout.gutterFor

/** The numbers the window classes and the page margins come from (dp). Fixed: Material has no spacing tokens, so this is the one place they live. */
data class SpacingTokens(
    val xsDp: Float = 4f,
    val smDp: Float = 8f,
    val mdDp: Float = 16f,
    val lgDp: Float = 24f,
    /** Window width where COMPACT ends (phones) and MEDIUM (small tablets, phones in landscape) begins. */
    val mediumMinWidthDp: Float = 600f,
    /** Window width where EXPANDED (large tablets, desktop) begins. */
    val wideLayoutMinWidthDp: Float = 840f,
    /** The standard content margin per window class: one gutter for every screen, so left edges line up. */
    val gutterCompactDp: Float = 16f,
    val gutterMediumDp: Float = 24f,
    val gutterExpandedDp: Float = 32f,
    /** Nothing tappable is smaller than this in either direction. */
    val minTouchDp: Float = 48f,
    /** Nothing readable is smaller than this. */
    val minTextSp: Float = 12f,
    /** Where the grid layouts start a new column: cards are never narrower than this. */
    val gridMinCardDp: Float = 280f,
)

/** `Spacing.sm`, `Spacing.gutter`: the one spacing scale of the app. */
object Spacing {
    private val t = SpacingTokens()
    val xs: Dp = t.xsDp.dp
    val sm: Dp = t.smDp.dp
    val md: Dp = t.mdDp.dp
    val lg: Dp = t.lgDp.dp

    /** Minimum size of anything tappable. */
    val minTouch: Dp = t.minTouchDp.dp

    val pagePadding: PaddingValues = PaddingValues(md)

    /** The content margin for the current window class: use this for every screen's horizontal edge. */
    val gutter: Dp @Composable @ReadOnlyComposable get() = t.gutterFor(LocalWindowSize.current).dp

    /** Padding for every scrolling screen: the window class's gutter at the sides, a little air above and below . */
    val contentPadding: PaddingValues
        @Composable @ReadOnlyComposable get() = PaddingValues(start = gutter, end = gutter, top = sm, bottom = sm)
}
