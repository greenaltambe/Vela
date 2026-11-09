package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.CaptionStyle
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.plainDescription
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.LoadingSpinner
import com.greenaltambe.vela.ui.components.VelaSheet
import com.greenaltambe.vela.ui.components.formatAge
import com.greenaltambe.vela.ui.components.formatViews
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.player.CaptionStyleContent
import com.greenaltambe.vela.ui.player.PlayerMenuRow
import com.greenaltambe.vela.ui.player.PlayerMenuSheet
import com.greenaltambe.vela.ui.player.SpeedMenuContent
import com.greenaltambe.vela.ui.player.formatSpeed
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** The sheets of the Shorts player's options menu. [OPTIONS] is the list; the others open from it. */
enum class ShortsMenu { OPTIONS, CAPTIONS, CAPTION_STYLE, SPEED, SAVE }

/**
 * The options of the Short on screen, opened from the three-dot button or by holding the picture: Description, Captions (with their style), Playback speed,
 * Save to a playlist, Copy link, Open the channel, Open as a normal video. Each is one tap away; a sub-sheet's Back returns to the list.
 */
@Composable
internal fun ShortsMenus(
    services: AppServices, navigator: Navigator, session: PlaybackSession, video: VideoSummary, menu: ShortsMenu, speed: Float,
    onMenu: (ShortsMenu?) -> Unit,
    captionStyle: CaptionStyle, onCaptionStyle: (CaptionStyle) -> Unit,
    onCaptionsOn: (Boolean) -> Unit,
    onNote: (String) -> Unit,
    /** Opens the video page's own description panel under the picture (the same one the long videos use). */
    onDescription: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val toasts = LocalUndoToasts.current
    val tracks by session.captions.collectAsState()
    val active by session.activeCaption.collectAsState()
    val close = { onMenu(null) }
    when (menu) {
        ShortsMenu.OPTIONS -> PlayerMenuSheet("Short", close, subtitle = video.title.ifBlank { null }) {
            PlayerMenuRow("Description", false, { close(); onDescription() }, 0, leading = AppIcons.info, supporting = "Numbers, description, tags and the channel")
            PlayerMenuRow(
                "Captions", false, { onMenu(ShortsMenu.CAPTIONS) }, 1, leading = AppIcons.captions, enabled = tracks.isNotEmpty(),
                supporting = if (tracks.isEmpty()) "Not available for this Short" else active?.label ?: "Off",
            )
            PlayerMenuRow("Playback speed", false, { onMenu(ShortsMenu.SPEED) }, 2, leading = AppIcons.speed, supporting = formatSpeed(speed))
            PlayerMenuRow("Save to…", false, { onMenu(ShortsMenu.SAVE) }, 3, leading = AppIcons.save, supporting = "Watch later or a playlist")
            PlayerMenuRow("Copy link", false, { clipboard.setText(AnnotatedString("https://youtube.com/shorts/${video.id}")); toasts.show("Link copied"); close() }, 4, leading = AppIcons.link)
            if (video.channelId.isNotBlank()) PlayerMenuRow("Go to ${video.channelName.ifBlank { "channel" }}", false, { close(); navigator.push(Route.Channel(video.channelId, video.channelName)) }, 5, leading = AppIcons.account)
            PlayerMenuRow("Open as a normal video", false, { close(); session.stop(); navigator.push(Route.Video(video, null, asVideoPage = true)) }, 6, leading = AppIcons.fullscreen)
        }
        ShortsMenu.CAPTIONS -> PlayerMenuSheet("Captions", { onMenu(ShortsMenu.OPTIONS) }, subtitle = active?.label ?: "Off") {
            PlayerMenuRow("Off", active == null, { session.selectCaption(null); onCaptionsOn(false); close() }, 0)
            tracks.forEachIndexed { i, t -> PlayerMenuRow(t.label, active == t, { session.selectCaption(t); onCaptionsOn(true); close() }, i + 1) }
            PlayerMenuRow("Caption style", false, { onMenu(ShortsMenu.CAPTION_STYLE) }, tracks.size + 1, leading = AppIcons.captions, supporting = "${captionStyle.size.label} · ${captionStyle.background.label}")
        }
        ShortsMenu.CAPTION_STYLE -> PlayerMenuSheet("Caption style", { onMenu(ShortsMenu.CAPTIONS) }) { CaptionStyleContent(captionStyle, onCaptionStyle) }
        ShortsMenu.SPEED -> PlayerMenuSheet("Playback speed", { onMenu(ShortsMenu.OPTIONS) }) { SpeedMenuContent(speed) { session.setSpeed(it) } }
        ShortsMenu.SAVE -> SaveSheet(services, video, onMessage = { note, error -> (note ?: error)?.let(onNote) }, onChanged = {}, onDismiss = { onMenu(ShortsMenu.OPTIONS) })
    }
}
