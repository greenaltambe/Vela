package com.greenaltambe.vela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/**
 * Every control here is derived from the schema ([control], choices, range, section); there is no per-setting UI.
 * Edits the global layer only. Writes go straight to the store, so they persist as soon as they are made.
 */
@Composable
fun SettingsScreen(settings: SettingsResolver, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    // Bumped after every write so rows re-read the store (the resolver is not observable).
    var revision by remember { mutableStateOf(0) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(query, { query = it }, label = { Text("Search settings") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        val sections = settingsBySection(query)
        if (sections.isEmpty()) Text("No settings match.")
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sections.forEach { (section, items) ->
                item(key = "section-${section.name}") { Text(section.title, style = MaterialTheme.typography.titleMedium) }
                items.forEach { setting ->
                    item(key = setting.key) { SettingRow(settings, setting, revision) { revision++ } }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(settings: SettingsResolver, setting: Setting<*>, revision: Int, changed: () -> Unit) {
    val raw = remember(revision, setting) { settings.rawValue(setting) }
    val overridden = remember(revision, setting) { settings.isOverridden(setting) }
    fun write(value: String) { if (settings.setRaw(setting, value)) changed() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(setting.label, Modifier.weight(1f))
            Button(onClick = { settings.resetToDefault(setting); changed() }, enabled = overridden) { Text("Reset") }
        }
        when (setting.control()) {
            SettingControl.TOGGLE -> Switch(checked = raw == "true", onCheckedChange = { write(it.toString()) })
            SettingControl.CHOICE -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                setting.choices.forEach { c -> Button(onClick = { write(c.raw) }, enabled = c.raw != raw) { Text(c.label) } }
            }
            SettingControl.SLIDER -> {
                val range = setting.range!!
                val step = range.step
                Slider(
                    value = (raw.toFloatOrNull() ?: range.min.toFloat()).coerceIn(range.min.toFloat(), range.max.toFloat()),
                    onValueChange = { v ->
                        // Snap to the step so the stored value is clean (1.25, not 1.2500001).
                        val snapped = if (step != null) (kotlin.math.round((v - range.min) / step) * step + range.min) else v.toDouble()
                        write(snapped.toString())
                    },
                    valueRange = range.min.toFloat()..range.max.toFloat(),
                    steps = step?.let { ((range.max - range.min) / it).toInt() - 1 }?.coerceAtLeast(0) ?: 0,
                )
                Text(raw)
            }
            SettingControl.TEXT -> OutlinedTextField(raw, { write(it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
