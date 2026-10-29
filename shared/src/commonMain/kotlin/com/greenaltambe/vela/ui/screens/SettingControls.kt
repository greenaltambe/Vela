package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.components.Scrubber
import com.greenaltambe.vela.ui.look.*

/**
 * One generated control per setting, from the schema alone: switches with their one-line explanation, 2-4 choices as sliding
 * pills, longer lists as chips, sliders with a live word, text fields. Every change raises an undo toast; (i) opens the setting's
 * info text; [highlighted] (a search jump) tints the row until the page is left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingRow(settings: SettingsResolver, setting: Setting<*>, revision: Int, highlighted: Boolean = false) {
    val toasts = LocalUndoToasts.current
    val raw = remember(revision, setting) { settings.rawValue(setting) }
    val overridden = remember(revision, setting) { settings.isOverridden(setting) }
    var infoOpen by remember { mutableStateOf(false) }
    val tag = "setting.${setting.key}"

    fun word(value: String) = setting.choices.firstOrNull { it.raw == value }?.label ?: if (value == "true") "On" else if (value == "false") "Off" else value
    /** Writes [value] and offers Undo, which restores the previous stored value (or "not set"). */
    fun write(value: String) {
        if (value == raw) return
        val before = if (overridden) raw else null
        if (settings.setRaw(setting, value)) toasts.show("${setting.label} → ${word(value)}") { if (before == null) settings.resetToDefault(setting) else settings.setRaw(setting, before) }
    }

    Column(
        Modifier.fillMaxWidth().clip(Vela.mediumShape).then(if (highlighted) Modifier.background(Vela.colors.surfaceVariant.toColor()).padding(Vela.xs) else Modifier).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(Vela.xs),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text(setting.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (setting.info.isNotEmpty()) IconButton(onClick = { infoOpen = !infoOpen }, modifier = Modifier.testTag("$tag.info")) { Icon(Vela.icons.info, "About ${setting.label}") }
            if (setting.control() == SettingControl.TOGGLE) VelaSwitch(raw == "true", { write(it.toString()) }, Modifier.testTag("$tag.switch"))
            else if (overridden) TextButton(onClick = { val before = raw; settings.resetToDefault(setting); toasts.show("${setting.label} reset") { settings.setRaw(setting, before) } }, modifier = Modifier.testTag("$tag.reset")) { Text("Reset", maxLines = 1) }
        }
        if (infoOpen) Text(setting.info, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("$tag.infoText"))
        when (setting.control()) {
            SettingControl.TOGGLE -> {}
            // Sliding pills only when every label fits its share of a phone-width row; longer labels would be cut off, so they become chips.
            SettingControl.CHOICE -> if (setting.choices.size in 2..4 && setting.choices.maxOf { it.label.length } * setting.choices.size <= 40) {
                SegmentedPills(setting.choices.map { it.label }, setting.choices.indexOfFirst { it.raw == raw }.coerceAtLeast(0), { write(setting.choices[it].raw) }, Modifier.fillMaxWidth().widthIn(max = 520.dp), tag = "$tag.pills")
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
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
                            val snapped = if (step != null) kotlin.math.round((v - range.min) / step) * step + range.min else v
                            write(snapped.toString())
                        },
                    )
                    Text(SliderWord.of(setting, raw), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 48.dp).testTag("$tag.value"))
                }
            }
            SettingControl.TEXT -> OutlinedTextField(raw, { write(it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The live word beside a slider ("24 dp", "130 %"): the number with its unit, so a slider is never just a bar. */
object SliderWord {
    fun of(setting: Setting<*>, raw: String): String {
        val n = raw.toDoubleOrNull() ?: return raw
        val text = if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
        return when {
            setting.key == "look.corners" -> if (n == 0.0) "Square" else "$text dp"
            setting.key == "look.textScale" -> "$text %"
            else -> text
        }
    }
}

/** Writes [raw] to [setting] and raises the undo toast; false (nothing written) if the value does not fit. */
fun SettingsResolver.setWithUndo(setting: Setting<*>, raw: String, toasts: UndoToasts, wordFor: (String) -> String = { it }): Boolean {
    val before = if (isOverridden(setting)) rawValue(setting) else null
    if (rawValue(setting) == raw) return true
    if (!setRaw(setting, raw)) return false
    toasts.show("${setting.label} → ${wordFor(raw)}") { if (before == null) resetToDefault(setting) else setRaw(setting, before) }
    return true
}
