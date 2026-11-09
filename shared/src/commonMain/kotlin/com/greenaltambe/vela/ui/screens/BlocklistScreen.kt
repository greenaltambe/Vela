package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.components.Avatar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.SecondaryButton
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import com.greenaltambe.vela.ui.settings.segmentedItemColors
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * Settings > Your feed > Blocked channels & words. Words are matched against titles; channels by id. Everything here is hidden in Home, Search, Explore,
 * Subscriptions and Related; a channel's own page and your library (history, playlists, downloads) are not touched. The list can be copied to share and pasted back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlocklistScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val list = services.blocklist
    val revision by list.revision.collectAsState()
    val channels = remember(revision) { list.channels() }
    val words = remember(revision) { list.keywords() }
    var draft by remember { mutableStateOf("") }
    val toasts = LocalUndoToasts.current
    val clipboard = LocalClipboardManager.current
    fun add() { val w = draft; if (list.addKeyword(w)) draft = "" else if (w.isBlank()) Unit else toasts.show("\"${w.trim()}\" is already blocked") }

    SettingsScaffold("Blocked", modifier, onBack = { navigator.back() }, listTag = "blocklist.list") {
        // What blocking does, in the same tonal card the other settings pages open with.
        settingsSection("about", "", listOf("about"), { it }) { _, shape ->
            InfoRow("Blocked channels and words are hidden in Home, Search, Explore, Subscriptions, Related, the Shorts player and notifications. A channel's own page, your history, playlists and downloads are not changed.", shape)
        }
        item(key = "add") {
            // The same pill field as Search, with the add button inside it.
            TextField(
                value = draft, onValueChange = { draft = it }, singleLine = true, shape = CircleShape,
                placeholder = { Text("Block a word in titles") },
                trailingIcon = {
                    FilledIconButton(onClick = { add() }, enabled = draft.isNotBlank(), shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(end = Spacing.xs).testTag("blocklist.add")) { Icon(AppIcons.add, "Block this word") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm).testTag("blocklist.input"),
            )
        }
        if (words.isEmpty() && channels.isEmpty()) item(key = "empty") {
            EmptyState("Nothing is blocked yet. Add a word above, or choose \"Block channel\" in a video's ⋮ menu or a channel's menu.")
        }
        if (words.isNotEmpty()) {
            item(key = "words-title") { Text("Words in titles", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.xs)) }
            // Removable words are input chips: the Material component for entries the viewer typed in.
            item(key = "words") {
                FlowRow(Modifier.fillMaxWidth().testTag("blocklist.words"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    words.forEach { w ->
                        val remove = { val before = list.keywords(); list.removeKeyword(w); toasts.show("\"$w\" is unblocked") { list.restore(list.channels(), before) } }
                        InputChip(
                            selected = false, onClick = remove, label = { Text(w, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(AppIcons.close, "Unblock the word $w", Modifier.size(InputChipDefaults.IconSize)) },
                            modifier = Modifier.testTag("blocklist.word"),
                        )
                    }
                }
            }
        }
        if (channels.isNotEmpty()) settingsSection("channels", "Channels", channels, { it.id }) { c, shape ->
            SegmentedListItem(
                onClick = { navigator.push(com.greenaltambe.vela.ui.nav.Route.Channel(c.id, c.name)) }, shapes = ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
                leadingContent = { Avatar(c.name, size = 40.dp) },
                supportingContent = { Text("Tap to open its page", maxLines = 1) },
                trailingContent = {
                    IconButton(onClick = { val before = list.channels(); list.unblockChannel(c.id); toasts.show("${c.name} is unblocked") { list.restore(before, list.keywords()) } }, modifier = Modifier.testTag("blocklist.unblock")) { Icon(AppIcons.close, "Unblock ${c.name}") }
                },
                modifier = Modifier.fillMaxWidth().testTag("blocklist.channel"),
            ) { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        item(key = "share") {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton("Copy list", { clipboard.setText(AnnotatedString(list.export())); toasts.show("Blocklist copied") }, enabled = words.isNotEmpty() || channels.isNotEmpty(), modifier = Modifier.weight(1f).testTag("blocklist.copy"))
                SecondaryButton("Paste list", {
                    val text = clipboard.getText()?.text.orEmpty()
                    val n = list.import(text)
                    toasts.show(if (n == 0) "Nothing new to add from the clipboard" else "Added $n to the blocklist")
                }, modifier = Modifier.weight(1f).testTag("blocklist.paste"))
            }
        }
    }
}
