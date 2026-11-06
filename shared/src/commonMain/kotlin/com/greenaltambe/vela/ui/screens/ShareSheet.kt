package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.player.PlayerMenuRow
import com.greenaltambe.vela.ui.player.PlayerMenuSection
import com.greenaltambe.vela.ui.player.PlayerMenuSheet
import com.greenaltambe.vela.ui.platform.LocalShare

/**
 * Everything you can do with a video's address, in one sheet: send it (the system share sheet, where there is one), copy it, either as it is or
 * at the moment you are watching, or open it in the browser. [onDone] gets a short message for the page to show ("Link copied: ...").
 */
@Composable
fun ShareSheet(videoId: String, title: String, positionMs: Long, onDone: (String?) -> Unit, onDismiss: () -> Unit) {
    val share = LocalShare.current
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val link = "https://youtu.be/$videoId"
    val seconds = (positionMs / 1000).coerceAtLeast(0)
    val atTime = seconds >= 2
    val timed = "$link?t=$seconds"
    fun copy(text: String) { clipboard.setText(AnnotatedString(text)); onDone("Link copied: $text") }

    PlayerMenuSheet("Share", onDismiss, subtitle = title) {
        var i = 0
        PlayerMenuSection("Send")
        PlayerMenuRow(if (share != null) "Share link" else "Copy link", selected = false, index = i++, leading = Vela.icons.share, supporting = "youtu.be/$videoId", onClick = { if (share != null) { share(title, link); onDone(null) } else copy(link) })
        if (atTime) PlayerMenuRow("Share at ${formatTimestamp(seconds)}", selected = false, index = i++, leading = Vela.icons.timer, supporting = "Starts where you are now", onClick = { if (share != null) { share(title, timed); onDone(null) } else copy(timed) })
        PlayerMenuSection("Copy")
        if (share != null) PlayerMenuRow("Copy link", selected = false, index = i++, leading = Vela.icons.link, supporting = "youtu.be/$videoId", onClick = { copy(link) })
        if (atTime) PlayerMenuRow("Copy link at ${formatTimestamp(seconds)}", selected = false, index = i++, leading = Vela.icons.link, supporting = "…?t=$seconds", onClick = { copy(timed) })
        PlayerMenuSection("Elsewhere")
        PlayerMenuRow("Open in browser", selected = false, index = i, leading = Vela.icons.explore, supporting = "youtube.com", onClick = { runCatching { uri.openUri("https://www.youtube.com/watch?v=$videoId" + if (atTime) "&t=${seconds}s" else "") }; onDone(null) })
    }
}
