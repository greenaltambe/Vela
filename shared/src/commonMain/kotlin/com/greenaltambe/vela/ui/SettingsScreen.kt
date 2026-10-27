package com.greenaltambe.vela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.Setting
import com.greenaltambe.vela.core.settings.SettingControl
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.control
import com.greenaltambe.vela.core.settings.isOverridden
import com.greenaltambe.vela.core.settings.rawValue
import com.greenaltambe.vela.core.settings.resetToDefault
import com.greenaltambe.vela.core.settings.setRaw
import com.greenaltambe.vela.core.settings.settingsBySection
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.Scrubber
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor

/**
 * Every control here is derived from the schema ([control], choices, range, section); there is no per-setting UI.
 * Edits the global layer only; writes go straight to the store. [query] narrows the list (the search field is in the
 * screen's top bar). Rows keep one shape: the label (up to three lines) with its control beside it for switches and
 * under it for everything wider, so nothing is clipped at 320 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: SettingsResolver, query: String = "", modifier: Modifier = Modifier) {
    val revision by settings.revision.collectAsState()
    val sections = settingsBySection(query)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("settings.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            if (sections.isEmpty()) item { EmptyState("No settings match \"$query\".") }
            sections.forEach { (section, items) ->
                item(key = "section-${section.name}") {
                    Box(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch), contentAlignment = Alignment.BottomStart) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, color = Vela.colors.primary.toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items.forEach { setting -> item(key = setting.key) { SettingRow(settings, setting, revision) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(settings: SettingsResolver, setting: Setting<*>, revision: Int) {
    val raw = remember(revision, setting) { settings.rawValue(setting) }
    val overridden = remember(revision, setting) { settings.isOverridden(setting) }
    fun write(value: String) { settings.setRaw(setting, value) }
    val tag = "setting.${setting.key}"

    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Text(setting.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (setting.control() == SettingControl.TOGGLE) Switch(checked = raw == "true", onCheckedChange = { write(it.toString()) }, modifier = Modifier.testTag("$tag.switch"))
            else if (overridden) TextButton(onClick = { settings.resetToDefault(setting) }) { Text("Reset", maxLines = 1) }
        }
        when (setting.control()) {
            SettingControl.TOGGLE -> if (overridden) TextButton(onClick = { settings.resetToDefault(setting) }) { Text("Reset", maxLines = 1) }
            SettingControl.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                setting.choices.forEach { c -> FilterChip(selected = c.raw == raw, onClick = { write(c.raw) }, label = { Text(c.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
            }
            SettingControl.SLIDER -> {
                val range = setting.range!!
                val step = range.step
                val fraction = (((raw.toDoubleOrNull() ?: range.min) - range.min) / (range.max - range.min)).toFloat().coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    Scrubber(
                        value = fraction, onPreview = {}, modifier = Modifier.weight(1f), description = setting.label, valueDescription = raw,
                        onCommit = { f ->
                            val v = range.min + f * (range.max - range.min)
                            // Snap to the step so the stored value is clean (1.25, not 1.2500001).
                            val snapped = if (step != null) kotlin.math.round((v - range.min) / step) * step + range.min else v
                            write(snapped.toString())
                        },
                    )
                    Text(raw, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 40.dp))
                }
            }
            SettingControl.TEXT -> OutlinedTextField(raw, { write(it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
