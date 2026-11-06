package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours Material has no role for: the seek-bar markers (one per SponsorBlock category), and what is drawn over video (icons and text on
 * the picture, the dimming behind them, the letterbox). They do not change with light and dark: video is video.
 */
data class ExtraColorTokens(
    val markers: Map<String, Color>,
    val onMedia: Color = Color(0xFFFFFFFF),
    val mediaScrim: Color = Color(0x99000000),
    val mediaBackground: Color = Color(0xFF000000),
) {
    companion object {
        val Default = ExtraColorTokens(
            markers = mapOf(
                "sponsor" to Color(0xFF00D400), "selfpromo" to Color(0xFFFFFF00), "interaction" to Color(0xFFCC00FF),
                "intro" to Color(0xFF00FFFF), "outro" to Color(0xFF0202ED), "preview" to Color(0xFF008FD6),
                "music_offtopic" to Color(0xFFFF9900), "filler" to Color(0xFF7300FF), "poi_highlight" to Color(0xFFFF1684),
                "exclusive_access" to Color(0xFF008A5C),
            ),
        )
    }
}

val LocalExtraColors = staticCompositionLocalOf { ExtraColorTokens.Default }

/** `ExtraColors.onMedia` and friends, read from the active theme. */
object ExtraColors {
    val markers: Map<String, Color> @Composable @ReadOnlyComposable get() = LocalExtraColors.current.markers
    val onMedia: Color @Composable @ReadOnlyComposable get() = LocalExtraColors.current.onMedia
    val mediaScrim: Color @Composable @ReadOnlyComposable get() = LocalExtraColors.current.mediaScrim
    val mediaBackground: Color @Composable @ReadOnlyComposable get() = LocalExtraColors.current.mediaBackground
    /** The tick of a chapter on the seek bar: the page's text colour, so it shows on either theme. */
    val chapterMarker: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground
}
