package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.greenaltambe.vela.ui.components.Avatar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.feed.SubscriptionGroup
import com.greenaltambe.vela.ui.components.ActionChip
import com.greenaltambe.vela.ui.components.ChipRow
import com.greenaltambe.vela.ui.components.ExpressiveChip
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SecondaryButton
import com.greenaltambe.vela.ui.components.VelaSheet
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** The Subscriptions page's group chips: All, each group, and "New group". With a group chosen, "Edit" opens its editor. [selected] null means All. */
@Composable
internal fun GroupChips(groups: List<SubscriptionGroup>, selected: String?, onSelect: (String?) -> Unit, onNew: () -> Unit, onEdit: (SubscriptionGroup) -> Unit) {
    ChipRow(Modifier.testTag("subs.groups")) {
        item(key = "all") { ExpressiveChip(selected == null, "All", { onSelect(null) }, Modifier.testTag("subs.group.all")) }
        items(groups, key = { it.id }) { g -> ExpressiveChip(selected == g.id, g.name, { onSelect(g.id) }, Modifier.testTag("subs.group.${g.id}")) }
        groups.firstOrNull { it.id == selected }?.let { g -> item(key = "edit") { ActionChip(AppIcons.edit, "Edit", { onEdit(g) }, Modifier.testTag("subs.group.edit")) } }
        item(key = "new") { ActionChip(AppIcons.add, "New group", onNew, Modifier.testTag("subs.group.new")) }
    }
}

/** One channel as a selectable row: its picture, name and a checkbox; a ticked row is tinted so the choice reads at a glance. */
@Composable
private fun ChannelCheckRow(name: String, avatarUrl: String?, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val container by androidx.compose.animation.animateColorAsState(if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "groupRow")
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Avatar(name, size = 40.dp, url = avatarUrl) },
        trailingContent = { Checkbox(checked, null) },
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}

/**
 * Create a group (when [group] is null) or edit one: its name and which of the subscribed channels are in it ([initialMembers] pre-ticks channels for a
 * new group). Saving with a name that is blank or taken says so instead of closing; Delete removes the group (the channels stay subscribed) and offers Undo.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupEditorSheet(
    services: AppServices, group: SubscriptionGroup?, onDismiss: () -> Unit, onSaved: (SubscriptionGroup) -> Unit = {}, onDeleted: () -> Unit = {},
    initialMembers: Set<String> = emptySet(),
) {
    val store = services.subscriptionGroups
    val channels = remember { services.subscriptions.all().sortedBy { it.name.lowercase() } }
    var name by remember(group) { mutableStateOf(group?.name.orEmpty()) }
    var members by remember(group) { mutableStateOf(group?.channelIds ?: initialMembers) }
    var problem by remember { mutableStateOf<String?>(null) }
    val toasts = LocalUndoToasts.current
    VelaSheet(onDismiss, Modifier.testTag("group.sheet")) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(if (group == null) "New group" else "Edit group", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                name, { name = it; problem = null }, singleLine = true, label = { Text("Name") }, isError = problem != null, shape = MaterialTheme.shapes.large,
                supportingText = problem?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("group.name"),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (members.isEmpty()) "Channels in this group" else "${members.size} channel${if (members.size == 1) "" else "s"} in this group",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                )
                if (channels.isNotEmpty()) TextButton(onClick = { members = if (members.size == channels.size) emptySet() else channels.map { it.channelId }.toSet() }, modifier = Modifier.testTag("group.all")) {
                    Text(if (members.size == channels.size) "Select none" else "Select all")
                }
            }
            if (channels.isEmpty()) Text("You are not subscribed to any channel yet.", style = MaterialTheme.typography.bodyMedium)
            else LazyColumn(Modifier.heightIn(max = 320.dp).testTag("group.channels"), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                items(channels, key = { it.channelId }) { c ->
                    ChannelCheckRow(c.name, c.avatarUrl, c.channelId in members, { members = if (c.channelId in members) members - c.channelId else members + c.channelId })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (group != null) TextButton(
                    onClick = { val before = store.groups(); store.delete(group.id); onDeleted(); onDismiss(); toasts.show("${group.name} deleted") { store.restore(before) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("group.delete"),
                ) { Icon(AppIcons.delete, null, Modifier.size(18.dp)); Text("  Delete") }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                    SecondaryButton("Cancel", onDismiss)
                    PrimaryButton("Save", {
                        val saved = if (group == null) store.create(name, members)
                        else if (store.rename(group.id, name)) { store.setMembers(group.id, members); store.groups().firstOrNull { it.id == group.id } } else null
                        if (saved == null) problem = if (name.isBlank()) "Give the group a name" else "There is already a group with this name" else { onSaved(saved); onDismiss() }
                    }, modifier = Modifier.testTag("group.save"))
                }
            }
        }
    }
}

/** From a channel's menu: tick the groups this channel belongs to, or start a new group that already has it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupPickerSheet(services: AppServices, channelId: String, channelName: String, onDismiss: () -> Unit) {
    val store = services.subscriptionGroups
    var revision by remember { mutableStateOf(0) }
    val groups = remember(revision) { store.groups() }
    var creating by remember { mutableStateOf(false) }
    if (creating) { GroupEditorSheet(services, null, { creating = false; revision++ }, initialMembers = setOf(channelId)); return }
    VelaSheet(onDismiss, Modifier.testTag("group.picker")) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            Text("Add $channelName to…", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = Spacing.xs))
            if (groups.isEmpty()) Text("You have no groups yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.sm))
            groups.forEach { g ->
                val on = channelId in g.channelIds
                val container by androidx.compose.animation.animateColorAsState(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "pickRow")
                ListItem(
                    headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${g.channelIds.size} channel${if (g.channelIds.size == 1) "" else "s"}", maxLines = 1) },
                    trailingContent = { Checkbox(on, null) },
                    colors = ListItemDefaults.colors(containerColor = container),
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).toggleable(on, role = Role.Checkbox, onValueChange = { store.toggle(g.id, channelId); revision++ }).testTag("group.pick.${g.id}"),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton("New group", { creating = true }, modifier = Modifier.weight(1f).testTag("group.pick.new"))
                PrimaryButton("Done", onDismiss, modifier = Modifier.weight(1f).testTag("group.pick.done"))
            }
        }
    }
}
