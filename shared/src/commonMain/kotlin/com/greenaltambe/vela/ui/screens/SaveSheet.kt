package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.player.PlayerMenuRow
import com.greenaltambe.vela.ui.player.PlayerMenuSheet
import com.greenaltambe.vela.auth.ActionResult
import kotlinx.coroutines.launch

/**
 * "Save to...": the account's Watch later (when signed in) and every local playlist as a row you tap to add the video or take it out (a check marks the
 * ones that hold it). The sheet stays open so one visit can change several lists; [onChanged] tells the page to refresh its Save button.
 */
@Composable
fun SaveSheet(services: AppServices, video: VideoSummary, onMessage: (note: String?, error: String?) -> Unit, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    val lists = remember(revision) { services.playlists.all() }
    val signedIn = services.actions != null && services.accounts.active.value.isSignedIn

    PlayerMenuSheet("Save to", onDismiss, subtitle = video.title) {
        var i = 0
        if (signedIn) PlayerMenuRow(
            "Watch later", selected = false, index = i++, leading = Vela.icons.watchLater, supporting = "On your YouTube account",
            onClick = {
                scope.launch {
                    when (val r = services.actions!!.addToPlaylist("WL", video.id)) {
                        is ActionResult.Failed -> onMessage(null, "Could not add to Watch later: ${r.message}")
                        else -> onMessage("Added to Watch later", null)
                    }
                }
                onDismiss()
            },
            modifier = Modifier.testTag("video.saveWatchLater"),
        )
        if (lists.isEmpty() && !signedIn) Text("No playlists yet. Create one in Library.", Modifier.padding(horizontal = Vela.gutter), style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
        lists.forEach { list ->
            val has = remember(revision, list.id) { services.playlists.contains(list.id, video.id) }
            PlayerMenuRow(
                list.name, selected = has, index = i++, leading = if (list.isWatchLater) Vela.icons.watchLater else Vela.icons.playlist,
                supporting = (if (list.isWatchLater) "On this device · " else "") + "${list.itemCount} video${if (list.itemCount == 1) "" else "s"}",
                onClick = {
                    if (has) services.playlists.remove(list.id, video.id) else services.playlists.add(list.id, listOf(video), services.clock.now())
                    revision++; onChanged()
                },
            )
        }
    }
}
