@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.Setting
import com.greenaltambe.vela.core.settings.SettingControl
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.control
import com.greenaltambe.vela.core.settings.description
import com.greenaltambe.vela.core.settings.isOverridden
import com.greenaltambe.vela.core.settings.rawValue
import com.greenaltambe.vela.core.settings.resetToDefault
import com.greenaltambe.vela.core.settings.setRaw
import com.greenaltambe.vela.core.settings.title
import com.greenaltambe.vela.core.settings.unit
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * One generated row per setting, from the schema alone (the control comes from [control]): a switch row, two or three short options as segmented
 * buttons, any longer list as a picker row that opens a sheet, a slider with its value and unit, a text value that opens a dialog. The row is a
 * card of [shape] (its place in a segmented stack). Every change raises an undo toast; the title and the one-line description come from the copy table.
 */
@Composable
fun SettingItem(settings: SettingsResolver, setting: Setting<*>, revision: Int, shape: Shape, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val toasts = LocalUndoToasts.current
    val raw = remember(revision, setting) { settings.rawValue(setting) }
    val overridden = remember(revision, setting) { settings.isOverridden(setting) }
    val tag = "setting.${setting.key}"
    val title = setting.title
    val description = setting.description

    fun word(value: String) = setting.choices.firstOrNull { it.raw == value }?.label ?: if (value == "true") "On" else if (value == "false") "Off" else value
    /** Writes [value] and offers Undo, which restores the previous stored value (or "not set"). */
    fun write(value: String) {
        if (value == raw) return
        val before = if (overridden) raw else null
        if (settings.setRaw(setting, value)) toasts.show("$title → ${word(value)}") { if (before == null) settings.resetToDefault(setting) else settings.setRaw(setting, before) }
    }
    fun reset() {
        if (!overridden) return
        val before = raw
        settings.resetToDefault(setting)
        toasts.show("$title reset") { settings.setRaw(setting, before) }
    }

    val container = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    val supporting: (@Composable () -> Unit)? = description.takeIf { it.isNotBlank() }?.let { d -> { Text(d, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.infoText")) } }
    Box(modifier.fillMaxWidth().clip(shape).background(container).testTag(tag)) {
        when (setting.control()) {
            SettingControl.TOGGLE -> {
                val on = raw == "true"
                ListItem(
                    headlineContent = { Text(title, maxLines = 3, overflow = TextOverflow.Ellipsis) }, supportingContent = supporting,
                    trailingContent = { Switch(on, null) }, colors = transparent,
                    modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { write(it.toString()) }).testTag("$tag.switch"),
                )
            }
            SettingControl.CHOICE -> {
                val segmented = setting.choices.size in 2..3 && setting.choices.maxOf { it.label.length } * setting.choices.size <= 36
                if (segmented) Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.infoText"))
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("$tag.pills")) {
                        setting.choices.forEachIndexed { i, c ->
                            SegmentedButton(selected = c.raw == raw, onClick = { write(c.raw) }, shape = SegmentedButtonDefaults.itemShape(i, setting.choices.size), modifier = Modifier.testTag("$tag.pills.$i")) { Text(c.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                } else {
                    var open by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Column { if (description.isNotBlank()) Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.infoText")); Text(word(raw), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("$tag.value")) } },
                        trailingContent = { Icon(AppIcons.chevronDown, null) }, colors = transparent,
                        modifier = Modifier.clickable(role = Role.Button) { open = true },
                    )
                    if (open) SettingChoiceSheet(setting, raw, overridden, onPick = { write(it) }, onReset = { reset() }, onDismiss = { open = false })
                }
            }
            SettingControl.SLIDER -> {
                val range = setting.range!!
                val step = range.step
                val unit = setting.unit
                val fraction = (((raw.toDoubleOrNull() ?: range.min) - range.min) / (range.max - range.min)).toFloat().coerceIn(0f, 1f)
                var dragging by remember(raw) { mutableStateOf<Float?>(null) }
                fun valueAt(f: Float): Double { val v = range.min + f * (range.max - range.min); return if (step != null) kotlin.math.round((v - range.min) / step) * step + range.min else v }
                val shownValue = valueAt(dragging ?: fraction).let { if (dragging == null) (raw.toDoubleOrNull() ?: it) else it }
                Column(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatNumber(shownValue) + unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = Spacing.xs).testTag("$tag.value"))
                        // Always there, only invisible while the value is the default, so the row never changes height.
                        IconButton(onClick = { reset() }, enabled = overridden, modifier = Modifier.size(40.dp).alpha(if (overridden) 1f else 0f).testTag("$tag.reset")) { Icon(AppIcons.refresh, "Reset $title") }
                    }
                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Spacing.md).testTag("$tag.infoText"))
                    // Tick marks only when there are few steps; a long range snaps to its step on release without drawing a dot for each.
                    val steps = step?.let { (((range.max - range.min) / it).toInt() - 1).coerceAtLeast(0) }?.takeIf { it in 1..9 } ?: 0
                    Slider(
                        value = dragging ?: fraction, onValueChange = { dragging = it }, steps = steps,
                        onValueChangeFinished = { dragging?.let { write(valueAt(it).toString()) }; dragging = null },
                        modifier = Modifier.padding(end = Spacing.md).semantics { contentDescription = title; stateDescription = formatNumber(shownValue) + unit }.testTag("$tag.slider"),
                    )
                }
            }
            SettingControl.TEXT -> {
                var open by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Column { if (description.isNotBlank()) Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.infoText")); Text(raw.ifBlank { "Not set" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag.value")) } },
                    trailingContent = { Icon(AppIcons.edit, null) }, colors = transparent,
                    modifier = Modifier.clickable(role = Role.Button) { open = true },
                )
                if (open) SettingTextDialog(setting, raw, overridden, onSave = { write(it) }, onReset = { reset() }, onDismiss = { open = false })
            }
        }
    }
}

/** 12 or 0.25, not 12.0 or 0.2500. */
internal fun formatNumber(v: Double): String = if (v == kotlin.math.round(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

/** The options of a picker setting in a bottom sheet: a radio-style list (searchable when it is long), and "Reset to default" while changed. */
@Composable
private fun SettingChoiceSheet(setting: Setting<*>, raw: String, overridden: Boolean, onPick: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val tag = "setting.${setting.key}"
    var query by remember { mutableStateOf("") }
    val shown = remember(query, setting) { if (query.isBlank()) setting.choices else setting.choices.filter { it.label.contains(query, ignoreCase = true) } }
    com.greenaltambe.vela.ui.components.VelaSheet(onDismiss, Modifier.testTag("$tag.sheet")) {
        Column(Modifier.navigationBarsPadding()) {
            Text(setting.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
            if (setting.choices.size > 12) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).testTag("$tag.search"))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(shown, key = { it.raw }) { c ->
                    ListItem(
                        headlineContent = { Text(c.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { if (c.raw == raw) Icon(AppIcons.check, "Selected", tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onPick(c.raw); onDismiss() }.testTag("$tag.option.${c.raw}"),
                    )
                }
            }
            if (overridden) TextButton(onClick = { onReset(); onDismiss() }, modifier = Modifier.padding(horizontal = Spacing.sm).testTag("$tag.default")) { Text("Reset to default") }
            Spacer(Modifier.size(Spacing.md))
        }
    }
}

/** A text setting's value in a dialog: one write when saved (not one per keystroke); "Default" clears it. */
@Composable
private fun SettingTextDialog(setting: Setting<*>, raw: String, overridden: Boolean, onSave: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val tag = "setting.${setting.key}"
    var draft by remember { mutableStateOf(raw) }
    val uri = setting.key.endsWith("server", ignoreCase = true) || setting.key.endsWith("url", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.widthIn(max = 520.dp),
        title = { Text(setting.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (setting.description.isNotBlank()) Text(setting.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(draft, { draft = it }, singleLine = false, maxLines = 4, keyboardOptions = KeyboardOptions(keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text), modifier = Modifier.fillMaxWidth().testTag("$tag.field"))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft.trim()); onDismiss() }, modifier = Modifier.testTag("$tag.save")) { Text("Save") } },
        dismissButton = {
            Row {
                if (overridden) TextButton(onClick = { onReset(); onDismiss() }, modifier = Modifier.testTag("$tag.default")) { Text("Default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A switch row for a choice that is not a stored setting (backup options, rules): the whole row toggles, [tag] names the row. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit, shape: Shape, tag: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = description?.takeIf { it.isNotBlank() }?.let { d -> { Text(d, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
            trailingContent = { Switch(checked, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange).testTag(tag),
        )
    }
}
